package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Data class for a single prompt-starter chip displayed in [GistiPromptChips].
 *
 * @param emoji Lead emoji rendered at 15sp (slightly larger than label for visual hierarchy).
 * @param label Short action text shown next to the emoji (e.g. "Photo → list").
 */
data class GistiPromptChip(
    val emoji: String,
    val label: String,
)

/**
 * Horizontal scrollable row of prompt-starter chips for the home screen AI dock.
 *
 * Visual spec (from gisti-extra.jsx PromptChips):
 *  - Chip height: 38dp, full-pill (radius = height/2 = 19dp)
 *  - Background: `surfaceContainerLowest` (white card), border: 1dp `outlineVariant`
 *  - Content: emoji (15sp) + label (labelLarge ~13.5sp, weight 600, `onSurface`)
 *  - Gap between chips: 8dp
 *  - Row scrolls horizontally when chips overflow
 *
 * **Edge-to-edge scroll (MD3 horizontal-list pattern):** this is a [LazyRow] with the
 * screen horizontal padding applied as [contentPadding] — NOT as an outer `Modifier.padding`.
 * The row itself spans `fillMaxWidth` so the first/last chip can scroll out from under the
 * screen edge, giving the user a clear visual signal that the row is scrollable. Callers
 * MUST place this component WITHOUT their own horizontal padding (otherwise the inner
 * content padding stacks on top of the parent padding and the edge-bleed is lost).
 *
 * Token mapping:
 * - Container: `colorScheme.surfaceContainerLowest`
 * - Border: `colorScheme.outlineVariant`
 * - Label: `colorScheme.onSurface`, `labelLarge`
 *
 * @param chips        List of [GistiPromptChip] items to display.
 * @param onChipClick  Called with the 0-based index of the tapped chip (index into [chips],
 *                     NOT affected by the optional leading "New list" chip).
 * @param onNewListClick When non-null, a leading "➕ New list" chip is prepended before
 *                     [chips]. It has a DISTINCT destination (Templates, not chat) and its
 *                     own callback, so the [chips] indices stay stable. When null, no leading
 *                     chip is shown.
 * @param newListLabel Label for the leading "New list" chip (localized by the caller).
 * @param contentPadding Inner padding applied to the scroll content. Defaults to the screen
 *                     horizontal padding so the row is edge-to-edge with inset chips.
 */
@Composable
fun GistiPromptChips(
    chips: List<GistiPromptChip>,
    onChipClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onNewListClick: (() -> Unit)? = null,
    newListLabel: String = "New list",
    contentPadding: PaddingValues = PaddingValues(horizontal = AppDimens.ScreenPaddingHorizontal),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Optional leading "New list" chip — routes to Templates, not chat.
        if (onNewListClick != null) {
            item(key = "gisti_prompt_chip_new_list") {
                PromptChipItem(
                    chip = GistiPromptChip(emoji = "➕", label = newListLabel),
                    onClick = onNewListClick,
                )
            }
        }
        itemsIndexed(chips) { index, chip ->
            PromptChipItem(
                chip = chip,
                onClick = { onChipClick(index) },
            )
        }
    }
}

@Composable
private fun PromptChipItem(
    chip: GistiPromptChip,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp) // full pill for height=38

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .height(38.dp)
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Text(
                text = chip.emoji,
                fontSize = 15.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = chip.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Factory for the standard home-screen prompt chips.
 *
 * @param photoLabel Label for the photo chip (e.g. "Photo → list").
 * @param addLabel Label for the add-tasks chip (e.g. "Add tasks").
 * @param remindLabel Label for the reminder chip (e.g. "Remind me…").
 */
fun gistiDefaultPromptChips(
    photoLabel: String = "Photo → list",
    addLabel: String = "Add tasks",
    remindLabel: String = "Remind me…",
): List<GistiPromptChip> = listOf(
    GistiPromptChip(emoji = "📷", label = photoLabel),
    GistiPromptChip(emoji = "➕", label = addLabel),
    GistiPromptChip(emoji = "🔔", label = remindLabel),
)
