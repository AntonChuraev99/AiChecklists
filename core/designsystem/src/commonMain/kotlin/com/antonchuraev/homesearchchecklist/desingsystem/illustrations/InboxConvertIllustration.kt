package com.antonchuraev.homesearchchecklist.desingsystem.illustrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.SparkleTile
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors

/**
 * "A note becomes a checklist" — the wordless picture at the top of the empty-Inbox card (concept C,
 * owner's pick 2026-11-16): a tilted paper note with a camera badge → the AI sparkle → a small
 * checklist card with the first item done.
 *
 * Built from layout primitives and theme tokens rather than a raster, so it follows light/dark, the
 * palette and RTL without a second asset. Entirely decorative: [clearAndSetSemantics] removes the whole
 * subtree from the accessibility tree — the heading and hint under it say the same thing in words.
 *
 * Fixed 116dp tall and NOT scaled with the font: the host hides it on short windows and large font
 * scales instead (the action rows below it are what must stay above the fold).
 */
@Composable
fun InboxConvertIllustration(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(IllustrationHeight)
            .clip(AppShapes.medium)
            .background(AppSurface.recessed())
            .clearAndSetSemantics {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            PaperNote()
            SparkleTile(size = SparkleSize, cornerRadius = SparkleSize / 2)
            MiniChecklist()
        }
    }
}

/** The "before": a tilted amber note with ruled lines and a camera badge on its corner. */
@Composable
private fun PaperNote() {
    Box(modifier = Modifier.size(width = NoteWidth + BadgeOverhang, height = NoteHeight + BadgeOverhang)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NoteLineGap),
            modifier = Modifier
                .size(width = NoteWidth, height = NoteHeight)
                .rotate(NoteTilt)
                .clip(AppShapes.extraSmall)
                .background(GistiColors.paletteTint(NoteSlot))
                .padding(horizontal = AppDimens.SpacingSm, vertical = AppDimens.SpacingMd),
        ) {
            NoteLineFractions.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(LineThickness)
                        .clip(CircleShape)
                        .background(GistiColors.paletteContent(NoteSlot).copy(alpha = NoteLineAlpha)),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(BadgeSize)
                // A ring of the well's own tone cuts the badge out of the note's edge.
                .border(BadgeRing, AppSurface.recessed(), CircleShape)
                .padding(BadgeRing)
                .background(GistiColors.paletteTint(BadgeSlot), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = GistiColors.paletteContent(BadgeSlot),
                modifier = Modifier.size(BadgeGlyph),
            )
        }
    }
}

/** The "after": a cream card with four rows, the first one checked. */
@Composable
private fun MiniChecklist() {
    Column(
        verticalArrangement = Arrangement.spacedBy(MiniRowGap),
        modifier = Modifier
            .width(MiniCardWidth)
            .clip(AppShapes.small)
            .background(AppSurface.card())
            .border(AppDimens.DividerThickness, MaterialTheme.colorScheme.outlineVariant, AppShapes.small)
            .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingMd),
    ) {
        MiniBarFractions.forEachIndexed { index, fraction ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                val dot = Modifier.size(MiniDot)
                Box(
                    modifier = if (index == 0) {
                        dot.background(MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        dot.border(MiniDotStroke, MaterialTheme.colorScheme.outline, CircleShape)
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(LineThickness)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = MiniBarAlpha)),
                )
            }
        }
    }
}

private val IllustrationHeight = 116.dp
private val SparkleSize = 28.dp

private val NoteWidth = 62.dp
private val NoteHeight = 80.dp
private const val NoteTilt = -6f
private val NoteLineGap = 7.dp
private val NoteLineFractions = listOf(0.9f, 1f, 0.75f, 0.95f, 0.6f)
private const val NoteLineAlpha = 0.45f
private const val NoteSlot = 5 // amber

private val BadgeSize = 26.dp
private val BadgeRing = 2.dp
private val BadgeGlyph = 13.dp
private val BadgeOverhang = 8.dp
private const val BadgeSlot = 2 // peach — the Photo door's own colour

private val MiniCardWidth = 112.dp
private val MiniRowGap = 8.dp
private val MiniDot = 12.dp
private val MiniDotStroke = 1.5.dp
private val MiniBarFractions = listOf(0.7f, 0.95f, 0.6f, 0.85f)
private const val MiniBarAlpha = 0.22f

private val LineThickness = 3.dp
