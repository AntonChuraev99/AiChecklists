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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * The distinct quick-action a home-screen prompt chip triggers.
 *
 * The chip itself is pure UI; it only carries this enum so the host screen
 * (@android-expert wiring) can map each action to its own intent/flow:
 *  - [CREATE_WITH_AI] → open the chat and prefill the create-checklist trigger; the user
 *    describes the checklist in words and the AI builds it (no picker — pure chat).
 *  - [PHOTO]    → create a checklist from a photo (image picker → AI).
 *  - [REMIND]   → open the reminder flow.
 *  - [LINK]     → create a checklist from a URL.
 *  - [PLAN_DAY] → open the "plan my day" flow.
 *  - [PDF]      → create a checklist from a PDF document.
 *
 * NOTE: [PDF] is intentionally kept in the enum (the document picker still
 * references it), but it is NO LONGER part of [gistiDefaultPromptChips] — the
 * home prompt row was trimmed to 4 chips for optimal scan-ability (verb-led,
 * 3-4 visible at once is the recommended density).
 *
 * The leading "➕ New list" chip is NOT part of this enum — it is a separate
 * parameter ([GistiPromptChips.onNewListClick]) with its own distinct destination
 * (Templates, not chat).
 */
enum class GistiQuickAction {
    CREATE_WITH_AI,
    PHOTO,
    REMIND,
    LINK,
    PLAN_DAY,
    PDF,
}

/**
 * The distinct contextual quick-action a checklist-detail prompt chip triggers.
 *
 * These chips live above the chat input on the **full checklist screen** and are
 * scoped to the currently-open checklist (the host supplies the checklist id when
 * dispatching). The chip itself is pure UI; the host screen (@android-expert
 * wiring) maps each action to a chat prefill / send for that checklist:
 *  - [WHATS_MISSING] → ask the AI what items are typically forgotten / missing.
 *  - [GENERATE_IDEAS]→ ask the AI to brainstorm fresh item ideas for this checklist.
 *  - [ADD_ITEMS]     → ask the AI to suggest / add more items to this checklist.
 *  - [SUMMARY]       → ask the AI for a short progress summary of this checklist.
 *  - [REMIND]        → set a reminder for this checklist (user picks the time).
 *
 * Unlike [GistiQuickAction] these never open pickers — every action is a chat
 * interaction contextual to the open checklist, so there is no leading "New list"
 * chip on the detail screen.
 */
enum class GistiChecklistAction {
    WHATS_MISSING,
    GENERATE_IDEAS,
    ADD_ITEMS,
    SUMMARY,
    REMIND,
}

/**
 * Data class for a single prompt-starter chip displayed in [GistiPromptChips].
 *
 * Generic over the action type [T] so ONE UI component serves both sets:
 *  - the home dock set ([GistiQuickAction] via [gistiDefaultPromptChips]), and
 *  - the checklist-detail set ([GistiChecklistAction] via [gistiChecklistPromptChips]).
 *
 * [T] is constrained to [Enum] so the LazyRow item key can stay stable and unique
 * via `action.name` (see [GistiPromptChips]).
 *
 * @param emoji Lead emoji rendered at 15sp (slightly larger than label for visual hierarchy).
 * @param label Short, verb-led action text shown next to the emoji (e.g. "Photo → list").
 * @param action The action [T] this chip triggers. Surfaced to the caller via
 *               [GistiPromptChips.onChipClick] so each chip drives a DIFFERENT flow.
 */
data class GistiPromptChip<T>(
    val emoji: String,
    val label: String,
    val action: T,
)

/**
 * Horizontal scrollable row of prompt-starter chips for the Gisti AI docks.
 *
 * One generic component, two call-sites:
 *  - **Home** (`MainScreen`) — [GistiQuickAction] chips under the `AskGistiBar`,
 *    optionally prefixed with a "➕ New list" chip via [onNewListClick].
 *  - **Checklist detail** (`ChecklistDetailScreen`) — [GistiChecklistAction] chips
 *    above the chat input, contextual to the open checklist (no leading chip).
 *
 * Visual spec (from gisti-extra.jsx PromptChips) — identical for both sets:
 *  - Chip height: 38dp, full-pill (radius = height/2 = 19dp)
 *  - Background: `surfaceContainerLowest` (white card), border: 1dp `outlineVariant`
 *  - Content: emoji (15sp) + label (labelLarge ~13.5sp, weight 600, `onSurface`)
 *  - Gap between chips: 8dp (`AppDimens.SpacingSm`)
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
 * @param onChipClick  Called with the action [T] of the tapped chip. The host maps
 *                     each action to its own intent (NOT affected by the optional leading
 *                     "New list" chip, which has its own callback).
 * @param onNewListClick When non-null, a leading "➕ New list" chip is prepended before
 *                     [chips]. It has a DISTINCT destination (Templates, not chat) and its
 *                     own callback, so the action chips stay independent. When null, no leading
 *                     chip is shown. Typically null on the checklist-detail screen.
 * @param newListLabel Label for the leading "New list" chip (localized by the caller).
 * @param contentPadding Inner padding applied to the scroll content. Defaults to the screen
 *                     horizontal padding so the row is edge-to-edge with inset chips.
 */
@Composable
fun <T : Enum<T>> GistiPromptChips(
    chips: List<GistiPromptChip<T>>,
    onChipClick: (T) -> Unit,
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
        // Center each glyph WITHIN its own line-box. Emoji (15sp) and label (13.5sp) have
        // different font metrics; the label inherits labelLarge's 20sp lineHeight, so its
        // extra leading is distributed top+bottom and pushes small mid-height glyphs (the
        // "→" in "Photo → list") off the optical centre of the Row. trim=None keeps the full
        // line-box, alignment=Center re-centres the glyph inside it so both Texts share a
        // common visual centre under Row's CenterVertically.
        val centeredLineHeight = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    lineHeightStyle = centeredLineHeight,
                ),
            )
            // The "→" glyph (U+2192) sits low on the text baseline by font design, so in
            // "Photo → list" / "Link → list" it reads as bottom-anchored next to the lowercase
            // letters. LineHeightStyle can't fix this — it moves the whole line-box, not a single
            // glyph relative to its same-baseline neighbours. Nudge ONLY the arrow up via
            // baselineShift so it lands on the optical centre of the row.
            val displayLabel = buildAnnotatedString {
                label.forEach { ch ->
                    if (ch == '→') {
                        withStyle(SpanStyle(baselineShift = BaselineShift(0.22f))) { append(ch) }
                    } else {
                        append(ch)
                    }
                }
            }
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeightStyle = centeredLineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Factory for the standard **home-screen** prompt chips.
 *
 * Order: Create with AI (flagship — describe a checklist to the AI in chat), then by
 * popularity Photo, Remind, Link, Plan day. PDF is not in this set (kept for the document
 * picker). The leading "➕ New list" chip (→ Templates, manual) is rendered separately via
 * [GistiPromptChips.onNewListClick] and is NOT part of this list.
 *
 * @param createAiLabel Label for the create-with-AI chip (e.g. "Create with AI").
 * @param photoLabel   Label for the photo chip (e.g. "Photo → list").
 * @param remindLabel  Label for the reminder chip (e.g. "Remind me…").
 * @param linkLabel    Label for the link chip (e.g. "Link → list").
 * @param planDayLabel Label for the plan-day chip (e.g. "Plan day").
 */
fun gistiDefaultPromptChips(
    createAiLabel: String = "Create with AI",
    photoLabel: String = "Photo → list",
    remindLabel: String = "Remind me…",
    linkLabel: String = "Link → list",
    planDayLabel: String = "Plan day",
): List<GistiPromptChip<GistiQuickAction>> = listOf(
    GistiPromptChip(emoji = "✨", label = createAiLabel, action = GistiQuickAction.CREATE_WITH_AI),
    GistiPromptChip(emoji = "📷", label = photoLabel, action = GistiQuickAction.PHOTO),
    GistiPromptChip(emoji = "🔔", label = remindLabel, action = GistiQuickAction.REMIND),
    GistiPromptChip(emoji = "🔗", label = linkLabel, action = GistiQuickAction.LINK),
    GistiPromptChip(emoji = "📅", label = planDayLabel, action = GistiQuickAction.PLAN_DAY),
)

/**
 * Factory for the contextual **checklist-detail** prompt chips.
 *
 * These sit above the chat input on the full checklist screen and act on the
 * currently-open checklist. Order (product intent): "What's missing?", "Generate ideas",
 * "Add items", "Summary", "Remind me" — AI-insight chips first, then add, then reminder.
 *
 * @param whatsMissingLabel  Label for the "what's missing" chip (e.g. "What's missing?").
 * @param generateIdeasLabel Label for the "generate ideas" chip (e.g. "Generate ideas").
 * @param addItemsLabel      Label for the "add items" chip (e.g. "Add items").
 * @param summaryLabel       Label for the "summary" chip (e.g. "Summary").
 * @param remindLabel        Label for the "remind me" chip (e.g. "Remind me").
 */
fun gistiChecklistPromptChips(
    whatsMissingLabel: String = "What's missing?",
    generateIdeasLabel: String = "Generate ideas",
    addItemsLabel: String = "Add items",
    summaryLabel: String = "Summary",
    remindLabel: String = "Remind me",
): List<GistiPromptChip<GistiChecklistAction>> = listOf(
    GistiPromptChip(emoji = "🧩", label = whatsMissingLabel, action = GistiChecklistAction.WHATS_MISSING),
    GistiPromptChip(emoji = "💡", label = generateIdeasLabel, action = GistiChecklistAction.GENERATE_IDEAS),
    GistiPromptChip(emoji = "➕", label = addItemsLabel, action = GistiChecklistAction.ADD_ITEMS),
    GistiPromptChip(emoji = "📊", label = summaryLabel, action = GistiChecklistAction.SUMMARY),
    GistiPromptChip(emoji = "🔔", label = remindLabel, action = GistiChecklistAction.REMIND),
)
