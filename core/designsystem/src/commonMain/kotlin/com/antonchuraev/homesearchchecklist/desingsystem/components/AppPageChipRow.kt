package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens

/**
 * One chip of [AppPageChipRow]: a page title with either a [dotColor] (a project's palette colour)
 * or a [leadingIcon] (the system Inbox). Primitives only — the feature maps its pages onto it.
 */
@Immutable
data class PageChip(
    val title: String,
    val dotColor: Color? = null,
    val leadingIcon: ImageVector? = null,
)

/** Sizing for [AppPageChipRow]. */
object AppPageChipRowDefaults {
    /** Visual chip height; the touch target is 48dp ([minimumInteractiveComponentSize]). */
    val ChipMinHeight: Dp = 36.dp

    /** A long project name ellipsises here — the full title is in the page headline above. */
    val ChipMaxWidth: Dp = 180.dp

    val DotSize: Dp = 10.dp
    val IconSize: Dp = 18.dp
    val RowMinHeight: Dp = 48.dp
}

/**
 * Horizontally scrolling page chips — the redesign-B replacement for the Inbox pager dots, one for
 * one, keeping every behaviour the dots had:
 *  - hidden when there is one page or none (a lone chip implies a second page exists);
 *  - a LAZY row, so 12+ projects scroll instead of collapsing to zero width (a plain `Row` measures
 *    overflowing children against 0 and the selected chip can vanish);
 *  - centred while the run fits; the selected chip is auto-centred on every [selectedIndex] change,
 *    so its neighbours on both sides stay one tap away;
 *  - every chip is a `Role.Tab` with `selected`, inside a `selectableGroup()`;
 *  - tap → [onSelect] (the host animates its pager). Swipe stays the primary way to change page.
 *
 * Selected chip: ink fill (`onSurface`), label/icon `surface`, no border (14.0:1 light / 15.1:1
 * dark). Unselected: transparent, 1dp `outlineVariant`, label `onSurface`. Colour cross-fades on the
 * effects spec; the size never changes with selection.
 */
@Composable
fun AppPageChipRow(
    pages: List<PageChip>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    if (pages.size <= 1) return

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, pages.size) {
        // Coerced, not trusted: a pager index read against a list another device can shorten.
        val target = selectedIndex.coerceIn(0, pages.lastIndex)
        // On first composition the effect runs BEFORE the first layout pass, so the layout info is
        // still empty and centring against it would leave the chip at the start (measured on the
        // 13-page golden). Wait for a measured viewport first.
        var layout = snapshotFlow { listState.layoutInfo }.first { it.viewportEndOffset > 0 }
        if (layout.visibleItemsInfo.none { it.index == target }) {
            // requestScrollToItem, NOT scrollToItem: `first {}` above can resume this coroutine
            // INSIDE the layout pass that produced the layout info, and `scrollToItem` forces a
            // remeasure — "performMeasureAndLayout called during measure layout" (crashed the
            // 13-page Inbox frame with the last page selected). The request is applied on the next
            // measure instead, which is exactly what the wait below observes.
            listState.requestScrollToItem(target)
            layout = snapshotFlow { listState.layoutInfo }
                .first { info -> info.visibleItemsInfo.any { it.index == target } }
        }
        val item = layout.visibleItemsInfo.first { it.index == target }
        // Chips have different widths, so centre on the measured item rather than an average.
        // A lazy list clamps at both ends, so a run that fits is left untouched.
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        val delta = item.offset + item.size / 2 - viewportCenter
        if (delta != 0) listState.animateScrollBy(delta.toFloat())
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppPageChipRowDefaults.RowMinHeight)
            .selectableGroup(),
        contentPadding = contentPadding,
        // A lazy list applies its arrangement only while the run fits: centred when short, packed
        // from the start and scrolling when long.
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pages) { index, page ->
            PageChipItem(
                chip = page,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun PageChipItem(
    chip: PageChip,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        AppMotion.effectsDefaultAs(),
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        AppMotion.effectsDefaultAs(),
    )
    Surface(
        onClick = onClick,
        shape = AppShapeTokens.Pill,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = AppPageChipRowDefaults.ChipMinHeight)
            .widthIn(max = AppPageChipRowDefaults.ChipMaxWidth)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                chip.leadingIcon != null -> Icon(
                    imageVector = chip.leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(AppPageChipRowDefaults.IconSize),
                )
                chip.dotColor != null -> Box(
                    Modifier
                        .size(AppPageChipRowDefaults.DotSize)
                        .background(chip.dotColor, CircleShape),
                )
            }
            Text(
                text = chip.title,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
