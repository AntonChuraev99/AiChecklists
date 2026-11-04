package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_sample_badge
import aichecklists.core.designsystem.generated.resources.onboarding_sample_image_a11y
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_1
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_2
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_3
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_4
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_5
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_6
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_struck
import aichecklists.core.designsystem.generated.resources.onboarding_sample_note_title
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import org.jetbrains.compose.resources.stringResource

/**
 * The sample "photo": a handwritten brain-dump note drawn in Compose (DESIGN_SPEC §3). Localised like
 * any other string, sharp at every size, follows the theme; no bitmap.
 *
 * Geometry follows the size the caller gives it: seven ruled lines share the height, the text size is
 * derived from the line pitch. Decorative — the caller owns the semantics (see [SampleNoteImage]).
 *
 * @param scan position of the scan band's centre, `0..1` of the height. Negative (the default) draws
 *   nothing; above 1 draws no band but keeps every line highlighted (the scan is over). Read in the
 *   draw phase only. Lines above the band centre get the highlighter fill.
 */
@Composable
internal fun SampleNote(
    modifier: Modifier = Modifier,
    scan: () -> Float = { NO_SCAN },
) {
    val paper = GistiColors.paletteTint(NOTE_PALETTE)
    val ink = GistiColors.paletteContent(NOTE_PALETTE)
    val band = MaterialTheme.colorScheme.primary
    val lines = listOf(
        stringResource(Res.string.onboarding_sample_note_1),
        stringResource(Res.string.onboarding_sample_note_2),
        stringResource(Res.string.onboarding_sample_note_3),
        stringResource(Res.string.onboarding_sample_note_4),
        stringResource(Res.string.onboarding_sample_note_5),
    )
    val title = stringResource(Res.string.onboarding_sample_note_title)
    val struck = stringResource(Res.string.onboarding_sample_note_struck)
    val last = stringResource(Res.string.onboarding_sample_note_6)

    // Illustration pixels, not UI text: the note never grows with fontScale (DESIGN_SPEC §3).
    FontScaleLocked {
        BoxWithConstraints(
            modifier = modifier
                .clip(AppShapes.extraSmall)
                .background(paper)
                .border(1.dp, ink.copy(alpha = PAPER_RING_ALPHA), AppShapes.extraSmall),
        ) {
            val pitch = maxHeight / LINE_SLOTS
            val top = pitch * TOP_SLOTS
            val side = maxWidth * SIDE_FRACTION
            val fontSize = (pitch.value * FONT_TO_PITCH).sp
            val lineHeight = pitch.value.sp
            val stroke = maxOf(pitch * STROKE_TO_PITCH, 0.5.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val p = pitch.toPx()
                        val t = top.toPx()
                        repeat(TEXT_LINES) { i ->
                            val y = t + p * (i + 1)
                            drawLine(ink.copy(alpha = RULE_ALPHA), Offset(0f, y), Offset(size.width, y), 1f)
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val s = scan()
                        if (s < 0f) return@drawWithContent
                        val p = pitch.toPx()
                        val t = top.toPx()
                        val centre = s * size.height
                        // Highlighter: every line whose centre the band has passed, filled left → right
                        // over roughly one line pitch of band travel.
                        repeat(TEXT_LINES) { i ->
                            val lineCentre = t + p * i + p / 2f
                            val fill = ((centre - lineCentre) / p).coerceIn(0f, 1f)
                            if (fill > 0f) {
                                drawRect(
                                    color = band.copy(alpha = HIGHLIGHT_ALPHA),
                                    topLeft = Offset(0f, t + p * i + p * 0.15f),
                                    size = androidx.compose.ui.geometry.Size(size.width * fill, p * 0.75f),
                                )
                            }
                        }
                        if (s > 1f) return@drawWithContent
                        val h = size.height * BAND_FRACTION
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to band.copy(alpha = BAND_ALPHA),
                                1f to Color.Transparent,
                                startY = centre - h / 2f,
                                endY = centre + h / 2f,
                            ),
                            topLeft = Offset(0f, centre - h / 2f),
                            size = androidx.compose.ui.geometry.Size(size.width, h),
                        )
                    }
                    .padding(start = side, end = side / 2, top = top),
            ) {
                Column {
                    val textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontStyle = FontStyle.Italic,
                        color = ink,
                    )
                    NoteLine(
                        text = title,
                        style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                        pitch = pitch,
                        modifier = Modifier.drawBehind {
                            val y = size.height * UNDERLINE_AT
                            drawLine(ink, Offset(0f, y), Offset(size.width, y), stroke.toPx())
                        },
                    )
                    lines.forEach { NoteLine(text = it, style = textStyle, pitch = pitch) }
                    Row(Modifier.height(pitch), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = struck,
                            style = textStyle,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                val y = size.height / 2f
                                drawLine(ink, Offset(0f, y), Offset(size.width, y), stroke.toPx())
                            },
                        )
                        Text(text = " $last", style = textStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteLine(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    pitch: Dp,
    modifier: Modifier = Modifier,
) {
    Box(Modifier.height(pitch), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = modifier,
        )
    }
}

/**
 * [SampleNote] in its recessed well with the "Sample" badge — one image node for TalkBack
 * (`onboarding_sample_image_a11y`, [Role.Image]).
 *
 * @param noteWidth / [noteHeight] the paper; [rotation] in degrees (negative = counter-clockwise).
 */
@Composable
internal fun SampleNoteImage(
    noteWidth: Dp,
    noteHeight: Dp,
    rotation: Float,
    modifier: Modifier = Modifier,
    well: Boolean = true,
    scan: () -> Float = { NO_SCAN },
    noteModifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.onboarding_sample_image_a11y)
    Box(
        modifier = modifier
            .then(if (well) Modifier.clip(AppShapes.medium).background(AppSurface.recessed()) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
        contentAlignment = Alignment.Center,
    ) {
        SampleNote(
            modifier = noteModifier.size(noteWidth, noteHeight).rotate(rotation),
            scan = scan,
        )
        SampleBadge(Modifier.align(Alignment.BottomStart).padding(AppDimens.SpacingSm))
    }
}

/** Read-only data badge "Sample": fontScale-locked like the note it labels (DESIGN_SPEC §3). */
@Composable
internal fun SampleBadge(modifier: Modifier = Modifier) {
    FontScaleLocked {
        Text(
            text = stringResource(Res.string.onboarding_sample_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = modifier
                .clip(AppShapes.extraSmall)
                .background(AppSurface.card())
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Decorative illustration content: text inside never scales with the user's fontScale. */
@Composable
internal fun FontScaleLocked(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f), content = content)
}

internal const val NO_SCAN = -1f

/** Amber: `paletteTint(5)` paper, `paletteContent(5)` ink — ≥ 4.5:1 in both themes. */
private const val NOTE_PALETTE = 5
private const val TEXT_LINES = 7
private const val TOP_SLOTS = 0.55f
private const val LINE_SLOTS = TEXT_LINES + TOP_SLOTS + 0.45f
private const val SIDE_FRACTION = 0.08f
private const val FONT_TO_PITCH = 0.62f
private const val STROKE_TO_PITCH = 0.1f
private const val UNDERLINE_AT = 0.86f
private const val PAPER_RING_ALPHA = 0.18f
private const val RULE_ALPHA = 0.16f
private const val HIGHLIGHT_ALPHA = 0.22f
private const val BAND_ALPHA = 0.4f
private const val BAND_FRACTION = 0.15f
