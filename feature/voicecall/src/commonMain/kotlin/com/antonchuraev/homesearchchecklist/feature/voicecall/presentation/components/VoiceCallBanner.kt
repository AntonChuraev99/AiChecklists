package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * The single banner under the top row (connection lost / minutes running out).
 *
 * Its semantics are STATIC ([a11yText], a polite live region announced once when it appears); the
 * visible [text] may tick ("…in 14 s") and is hidden from TalkBack so seconds are not read out.
 */
@Composable
fun VoiceCallBanner(
    icon: ImageVector,
    text: String,
    a11yText: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = AppDimens.SpacingMd,
            end = if (actionLabel != null) AppDimens.SpacingXs else AppDimens.SpacingMd,
            top = AppDimens.SpacingSm,
            bottom = AppDimens.SpacingSm,
        ),
    ) {
        BannerLayout(
            modifier = Modifier.heightIn(min = AppDimens.MinTouchTarget),
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = a11yText
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            },
            action = if (actionLabel != null && onAction != null) {
                { AppButtonText(text = actionLabel, onClick = onAction) }
            } else {
                null
            },
        )
    }
}

/**
 * icon · text · action in one row; when the action would leave the text narrower than its longest
 * word (RU at 320dp × 1.5 broke «бесплатн/ая»), the action drops under the text, end-aligned.
 * Decided in the measure phase from the text's min intrinsic width (= its longest word).
 */
@Composable
private fun BannerLayout(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    action: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(BannerPart.Icon)) { icon() }
            Box(Modifier.layoutId(BannerPart.Text)) { text() }
            if (action != null) Box(Modifier.layoutId(BannerPart.Action)) { action() }
        },
    ) { measurables, constraints ->
        val gap = AppDimens.SpacingMd.roundToPx()
        val w = constraints.maxWidth
        val iconP = measurables.first { it.layoutId == BannerPart.Icon }.measure(Constraints())
        val textM = measurables.first { it.layoutId == BannerPart.Text }
        val actionP = measurables.firstOrNull { it.layoutId == BannerPart.Action }?.measure(Constraints(maxWidth = w))
        val textStart = iconP.width + gap
        val inlineTextW = w - textStart - (actionP?.let { it.width + gap } ?: 0)
        val stacked = actionP != null && textM.minIntrinsicWidth(Constraints.Infinity) > inlineTextW
        val textW = (if (stacked || actionP == null) w - textStart else inlineTextW).coerceAtLeast(0)
        val textP = textM.measure(Constraints(maxWidth = textW))
        val rowH = maxOf(iconP.height, textP.height, if (stacked) 0 else actionP?.height ?: 0, constraints.minHeight)
        val h = if (stacked) maxOf(rowH, textP.height) + (actionP?.height ?: 0) else rowH
        layout(w, h) {
            val lineH = if (stacked) textP.height else rowH
            iconP.place(0, (lineH - iconP.height) / 2)
            textP.place(textStart, (lineH - textP.height) / 2)
            if (actionP != null) {
                if (stacked) {
                    actionP.place(w - actionP.width, lineH)
                } else {
                    actionP.place(w - actionP.width, (rowH - actionP.height) / 2)
                }
            }
        }
    }
}

private enum class BannerPart { Icon, Text, Action }
