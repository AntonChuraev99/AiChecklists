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
import androidx.compose.foundation.lazy.items
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
 * The distinct quick-action a prompt chip triggers.
 *
 * The chip itself is pure UI; it only carries this enum so the host screen
 * (@android-expert wiring) can map each action to its own intent/flow:
 *  - [PHOTO]    → create a checklist from a photo (image picker → AI).
 *  - [REMIND]   → open the reminder flow.
 *  - [LINK]     → create a checklist from a URL.
 *  - [PLAN_DAY] → open the "plan my day" flow.
 *  - [PDF]      → create a checklist from a PDF document.
 *
 * The leading "➕ New list" chip is NOT part of this enum — it is a separate
 * parameter ([GistiPromptChips.onNewListClick]) with its own distinct destination
 * (Templates, not chat).
 */
enum class GistiQuickAction {
    PHOTO,
    REMIND,
    LINK,
    PLAN_DAY,
    PDF,
}

/**
 * Data class for a single prompt-starter chip displayed in [GistiPromptChips].
 *
 * @param emoji Lead emoji rendered at 15sp (slightly larger than label for visual hierarchy).
 * @param label Short action text shown next to the emoji (e.g. "Photo → list").
 * @param action The [GistiQuickAction] this chip triggers. Surfaced to the caller via
 *               [GistiPromptChips.onChipClick] so each chip drives a DIFFERENT flow.
 */
data class GistiPromptChip(
    val emoji: String,
    val label: String,
    val action: GistiQuickAction,
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
 * @param onChipClick  Called with the [GistiQuickAction] of the tapped chip. The host maps
 *                     each action to its own intent (NOT affected by the optional leading
 *                     "New list" chip, which has its own callback).
 * @param onNewListClick When non-null, a leading "➕ New list" chip is prepended before
 *                     [chips]. It has a DISTINCT destination (Templates, not chat) and its
 *                     own callback, so the action chips stay independent. When null, no leading
 *                     chip is shown.
 * @param newListLabel Label for the leading "New list" chip (localized by the caller).
 * @param contentPadding Inner padding applied to the scroll content. Defaults to the screen
 *                     horizontal padding so the row is edge-to-edge with inset chips.
 */
@Composable
fun GistiPromptChips(
    chips: List<GistiPromptChip>,
    onChipClick: (GistiQuickAction) -> Unit,
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
                    emoji = "➕",
                    label = newListLabel,
                    onClick = onNewListClick,
                )
            }
        }
        items(
            items = chips,
            key = { chip -> "gisti_prompt_chip_${chip.action.name}" },
        ) { chip ->
            PromptChipItem(
                emoji = chip.emoji,
                label = chip.label,
                onClick = { onChipClick(chip.action) },
            )
        }
    }
}

@Composable
private fun PromptChipItem(
    emoji: String,
    label: String,
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
                text = emoji,
                fontSize = 15.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = label,
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
 * Order is by popularity (product-confirmed): Photo, Remind, Link, Plan day, PDF.
 * The leading "➕ New list" chip is rendered separately via
 * [GistiPromptChips.onNewListClick] and is NOT part of this list.
 *
 * @param photoLabel   Label for the photo chip (e.g. "Photo → list").
 * @param remindLabel  Label for the reminder chip (e.g. "Remind me…").
 * @param linkLabel    Label for the link chip (e.g. "Link → list").
 * @param planDayLabel Label for the plan-day chip (e.g. "Plan day").
 * @param pdfLabel     Label for the PDF chip (e.g. "PDF → list").
 */
fun gistiDefaultPromptChips(
    photoLabel: String = "Photo → list",
    remindLabel: String = "Remind me…",
    linkLabel: String = "Link → list",
    planDayLabel: String = "Plan day",
    pdfLabel: String = "PDF → list",
): List<GistiPromptChip> = listOf(
    GistiPromptChip(emoji = "📷", label = photoLabel, action = GistiQuickAction.PHOTO),
    GistiPromptChip(emoji = "🔔", label = remindLabel, action = GistiQuickAction.REMIND),
    GistiPromptChip(emoji = "🔗", label = linkLabel, action = GistiQuickAction.LINK),
    GistiPromptChip(emoji = "📅", label = planDayLabel, action = GistiQuickAction.PLAN_DAY),
    GistiPromptChip(emoji = "📄", label = pdfLabel, action = GistiQuickAction.PDF),
)
