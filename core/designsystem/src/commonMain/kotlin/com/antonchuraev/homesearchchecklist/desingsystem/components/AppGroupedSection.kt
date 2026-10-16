package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface

/**
 * Where one row sits inside a grouped section container (redesign B "Soft & tactile").
 *
 * A section is ONE visual container — 24dp corners, cream fill, 1dp hairline — but its rows are
 * separate `LazyColumn` items (reorder, swipe, `animateItem`), so a wrapping `Column` / `Card` is
 * impossible. Each item paints its own SEGMENT of the container instead; see [groupSegment].
 */
enum class GroupPosition { Single, First, Middle, Last }

/** Position of the row at [index] in a run of [size] rows. */
fun groupPositionOf(index: Int, size: Int): GroupPosition = when {
    size <= 1 -> GroupPosition.Single
    index <= 0 -> GroupPosition.First
    index >= size - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

/** True when the segment draws the inset divider at its bottom edge (a row follows it). */
fun GroupPosition.hasDividerBelow(): Boolean =
    this == GroupPosition.First || this == GroupPosition.Middle

/**
 * The clip shape of one segment: outer corners rounded by [corner] on the first / last / single
 * segment, square everywhere else. Exposed so a row's own overlays (swipe slab, arrival highlight)
 * can clip to exactly the same outline.
 */
fun groupSegmentShape(
    position: GroupPosition,
    corner: Dp = AppShapeTokens.GroupCorner,
): Shape = when (position) {
    GroupPosition.Single -> RoundedCornerShape(corner)
    GroupPosition.First -> RoundedCornerShape(topStart = corner, topEnd = corner)
    GroupPosition.Last -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
    GroupPosition.Middle -> RectangleShape
}

/**
 * Paints this item as one segment of a grouped section container.
 *
 * Extends the app's single card style ([AppCardDefaults]): same fill ([AppSurface.card]) and the same
 * 1dp `outlineVariant` hairline, only SPLIT across lazy items:
 *  - hairline on the left and right edges always, on the top edge of [GroupPosition.First] /
 *    [GroupPosition.Single], on the bottom edge of [GroupPosition.Last] / [GroupPosition.Single];
 *  - a 1dp divider at the bottom of [GroupPosition.First] / [GroupPosition.Middle], inset from the
 *    start by [dividerInsetStart] so it starts under the row's TEXT, not its checkbox;
 *  - content is clipped to the segment shape, so ripples and highlights stay inside.
 *
 * Apply it on the STATIC layer of a row (the one that does not translate while the row swipes): the
 * divider and the hairline must stay put under swiping content. The hairline is drawn behind the
 * content, so a row's own background never covers the divider — rows inside a group are transparent.
 *
 * No elevation, no shadow, both themes.
 */
@Composable
fun Modifier.groupSegment(
    position: GroupPosition,
    color: Color = AppSurface.card(),
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    dividerInsetStart: Dp = AppDensity.GroupDividerInsetStart,
): Modifier {
    val shape = groupSegmentShape(position)
    return this
        .clip(shape)
        .drawBehind {
            drawRect(color)
            val stroke = 1.dp.toPx()
            val half = stroke / 2f
            // The edges a segment must NOT draw are pushed outside the clip rather than skipped: the
            // outline of a taller box whose hidden edge lies beyond the bounds traces exactly the
            // visible edges, rounded corners included, with one code path for all four positions.
            val (top, height) = when (position) {
                GroupPosition.Single -> half to size.height - stroke
                GroupPosition.First -> half to size.height + stroke * 2f
                GroupPosition.Last -> -stroke * 2f to size.height + stroke * 2f - half
                GroupPosition.Middle -> -stroke * 2f to size.height + stroke * 4f
            }
            val outline = shape.createOutline(
                size = Size(size.width - stroke, height),
                layoutDirection = layoutDirection,
                density = this,
            )
            translate(left = half, top = top) {
                drawOutline(outline = outline, color = border, style = Stroke(width = stroke))
            }
            if (position.hasDividerBelow()) {
                val inset = dividerInsetStart.toPx()
                val y = size.height - half
                val (startX, endX) = if (layoutDirection == LayoutDirection.Rtl) {
                    0f to size.width - inset
                } else {
                    inset to size.width
                }
                drawLine(
                    color = border,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = stroke,
                )
            }
        }
}
