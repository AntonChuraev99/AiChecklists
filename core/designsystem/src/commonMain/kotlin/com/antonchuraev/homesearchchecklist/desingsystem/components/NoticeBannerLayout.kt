package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * When a [NoticeBannerLayout] keeps its action in the row and when it drops it under the text.
 *
 * Asked in the measure phase with the width the text would get if the action stayed inline; `true`
 * keeps the action inline, `false` stacks it under the text, end-aligned.
 */
fun interface NoticeBannerActionFit {
    fun fitsInline(text: IntrinsicMeasurable, inlineTextWidth: Int): Boolean

    companion object {
        /**
         * Inline while the text's longest word still fits beside the action (its min intrinsic
         * width). RU at 320dp × 1.5 otherwise broke «бесплатн/ая» mid-word.
         */
        val LongestWordFits = NoticeBannerActionFit { text, inlineTextWidth ->
            text.minIntrinsicWidth(Constraints.Infinity) <= inlineTextWidth
        }
    }
}

/**
 * [NoticeBannerActionFit] that keeps the action inline only while [headline] sits on ONE line beside
 * it — a two-line headline squeezed next to a button reads as a cramped column, so the button goes
 * under the text instead. [style] must be the style the headline is drawn with.
 */
@Composable
fun rememberHeadlineOnOneLineFit(headline: String, style: TextStyle): NoticeBannerActionFit {
    val measurer = rememberTextMeasurer()
    return remember(headline, style, measurer) { HeadlineOnOneLineFit(headline, style, measurer) }
}

private class HeadlineOnOneLineFit(
    private val headline: String,
    private val style: TextStyle,
    private val measurer: TextMeasurer,
) : NoticeBannerActionFit {
    override fun fitsInline(text: IntrinsicMeasurable, inlineTextWidth: Int): Boolean =
        measurer.measure(headline, style, softWrap = false).size.width <= inlineTextWidth
}

/**
 * icon · text · action in one row; when [actionFit] says the action does not fit, the action drops
 * under the text, end-aligned. Decided in the measure phase — no extra composition pass.
 *
 * Inline: icon, text and action are centred on the row (at least [Modifier]'s min height). Stacked:
 * the icon centres on the whole text block, or on its first line when [stackedIconLineHeight] is
 * given; the action takes its own line under the text.
 *
 * @param icon Optional leading icon; null → the text starts at the edge (e.g. hidden at large font).
 * @param action Optional trailing action (an [AppButtonText]); null → the text takes the full width.
 * @param stackedIconLineHeight Line height of the text's first line (its style's `lineHeight`);
 *   when stacked, the icon centres on that line instead of on a multi-line block.
 */
@Composable
fun NoticeBannerLayout(
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    actionFit: NoticeBannerActionFit = NoticeBannerActionFit.LongestWordFits,
    stackedIconLineHeight: TextUnit = TextUnit.Unspecified,
) {
    Layout(
        modifier = modifier,
        content = {
            if (icon != null) Box(Modifier.layoutId(NoticeBannerPart.Icon)) { icon() }
            Box(Modifier.layoutId(NoticeBannerPart.Text)) { text() }
            if (action != null) Box(Modifier.layoutId(NoticeBannerPart.Action)) { action() }
        },
    ) { measurables, constraints ->
        val gap = AppDimens.SpacingMd.roundToPx()
        val w = constraints.maxWidth
        val iconP = measurables.firstOrNull { it.layoutId == NoticeBannerPart.Icon }?.measure(Constraints())
        val textM = measurables.first { it.layoutId == NoticeBannerPart.Text }
        val actionP = measurables.firstOrNull { it.layoutId == NoticeBannerPart.Action }
            ?.measure(Constraints(maxWidth = w))
        val textStart = iconP?.let { it.width + gap } ?: 0
        val inlineTextW = w - textStart - (actionP?.let { it.width + gap } ?: 0)
        val stacked = actionP != null && !actionFit.fitsInline(textM, inlineTextW.coerceAtLeast(0))
        val textW = (if (stacked || actionP == null) w - textStart else inlineTextW).coerceAtLeast(0)
        val textP = textM.measure(Constraints(maxWidth = textW))
        val iconH = iconP?.height ?: 0
        val rowH = maxOf(iconH, textP.height, if (stacked) 0 else actionP?.height ?: 0, constraints.minHeight)
        val h = if (stacked) maxOf(rowH, textP.height) + (actionP?.height ?: 0) else rowH
        layout(w, h) {
            val lineH = if (stacked) textP.height else rowH
            val iconBandH = if (stacked && stackedIconLineHeight.isSpecified) {
                stackedIconLineHeight.roundToPx()
            } else {
                lineH
            }
            iconP?.place(0, ((iconBandH - iconH) / 2).coerceAtLeast(0))
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

private enum class NoticeBannerPart { Icon, Text, Action }
