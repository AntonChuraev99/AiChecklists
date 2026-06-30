package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.LocalEmojiFont
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.rememberEmojiAwareText
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
 * @param label Short, verb-led action text shown next to the emoji (e.g. "Photo ➡️ list").
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

    // Surface(onClick=…) applies .clip(shape) BEFORE its own clickable, so the ripple is
    // clipped to the pill shape (a bare Modifier.clickable on the outer modifier would draw a
    // rectangular ripple bleeding past the rounded corners). minimumInteractiveComponentSize
    // centres the 38dp pill inside a 48dp touch target without changing the visible height.
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .height(38.dp)
            .minimumInteractiveComponentSize(),
    ) {
        // Center each glyph WITHIN its own line-box. Emoji (15sp) and label (13.5sp) have
        // different font metrics; the label inherits labelLarge's 20sp lineHeight, so its
        // extra leading is distributed top+bottom and pushes small mid-height glyphs off the
        // optical centre of the Row. trim=None keeps the full line-box, alignment=Center
        // re-centres the glyph inside it so both Texts share a common visual centre under
        // Row's CenterVertically.
        val centeredLineHeight = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            // Pure-emoji Text → emoji font directly (wasmJs/Skiko has no system emoji fallback;
            // on Android/iOS LocalEmojiFont is FontFamily.Default, a no-op).
            Text(
                text = emoji,
                fontFamily = LocalEmojiFont.current,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    lineHeightStyle = centeredLineHeight,
                ),
            )
            // Label may mix Latin with an emoji arrow ("Photo ➡️ list") — rememberEmojiAwareText
            // keeps the emoji font on the Text and overrides Latin runs with FontFamily.Default so
            // both resolve on wasmJs. The old per-glyph baselineShift for the plain "→" (U+2192,
            // not an emoji, absent from Twemoji) is gone: the arrow is now the emoji ➡️ which the
            // emoji font renders at the correct optical height.
            val labelEmojiAware = rememberEmojiAwareText(label)
            Text(
                text = labelEmojiAware.text,
                fontFamily = labelEmojiAware.fontFamily,
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
 * @param photoLabel   Label for the photo chip (e.g. "Photo ➡️ list").
 * @param remindLabel  Label for the reminder chip (e.g. "Remind me…").
 * @param linkLabel    Label for the link chip (e.g. "Link ➡️ list").
 * @param planDayLabel Label for the plan-day chip (e.g. "Plan day").
 */
fun gistiDefaultPromptChips(
    createAiLabel: String = "Create with AI",
    photoLabel: String = "Photo ➡️ list",
    remindLabel: String = "Remind me…",
    linkLabel: String = "Link ➡️ list",
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

// ─────────────────────────────────────────────────────────────────────────────
// Item-create dock chips (selectable, blue-fill when active)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The distinct quick-action a checklist **item-create** dock chip triggers.
 *
 * These chips render in the shared chat dock when it is switched into item-create mode
 * (the checklist-detail "+" button). They are SELECTABLE (blue fill when active), unlike the
 * one-shot [GistiQuickAction] / [GistiChecklistAction] chips:
 *
 * Group A — reminder presets (single-select among themselves; tapping the active one again
 * clears the reminder):
 *  - [REMIND_1H]               → remind in 1 hour.
 *  - [REMIND_TOMORROW_MORNING] → remind tomorrow 09:00.
 *  - [REMIND_TONIGHT]          → remind today 18:00 (or tomorrow 18:00 if already past).
 *  - [REMIND_PICK]             → open the date&time picker; the chip then shows the resolved time.
 *
 * Group B — independent property toggles (each its own on/off state):
 *  - [IMPORTANT] → mark the new item important (priority = 1).
 *  - [REPEAT]    → open the repeat config; the chip then shows the repeat summary.
 *
 * The chip itself is pure UI; the host screen maps each action to its own intent/flow.
 */
enum class GistiItemCreateAction {
    REMIND_1H,
    REMIND_TOMORROW_MORNING,
    REMIND_TONIGHT,
    REMIND_PICK,
    IMPORTANT,
    REPEAT,
}

/**
 * A single selectable dock chip. [selected] drives the blue-fill active styling.
 *
 * @param icon     Lead outline icon (hollow Material vector: bell for reminders, star-border /
 *                 repeat for properties). Vector — not emoji — so it renders on wasmJs without
 *                 emoji-font tofu.
 * @param label    Short label; for [GistiItemCreateAction.REMIND_PICK] the host swaps this to the
 *                 resolved absolute datetime once a custom time is chosen, and for
 *                 [GistiItemCreateAction.REPEAT] to the repeat summary once configured.
 * @param action   The action [T] this chip triggers (surfaced via [GistiSelectableChipRow.onChipClick]).
 * @param selected Whether the chip is currently active (blue fill).
 */
data class GistiSelectableChip<T>(
    val icon: ImageVector,
    val label: String,
    val action: T,
    val selected: Boolean,
)

/**
 * A single horizontally-scrolling row of SELECTABLE chips for the item-create dock — same
 * [LazyRow] scroll pattern as [GistiPromptChips], but the chips carry a selected state. The reminder
 * presets + property toggles stay on ONE line and scroll sideways (they no longer wrap) inside the
 * always-visible expanded dock frame (the dock's peek chip slot fades out on expand, so item-create
 * chips live in the answer frame instead). Selected chips fill solid `primary`/`onPrimary` (the blue
 * active state); unselected chips are transparent with a hairline `outlineVariant` outline + hollow icon.
 *
 * Edge padding is the LazyRow [contentPadding] (the caller passes no outer horizontal padding),
 * matching the [GistiPromptChips] convention.
 */
@Composable
fun <T> GistiSelectableChipRow(
    chips: List<GistiSelectableChip<T>>,
    onChipClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = AppDimens.ScreenPaddingHorizontal),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        items(chips) { chip ->
            SelectablePromptChipItem(
                icon = chip.icon,
                label = chip.label,
                selected = chip.selected,
                onClick = { onChipClick(chip.action) },
            )
        }
    }
}

@Composable
private fun SelectablePromptChipItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp) // full pill for height=38
    // Outline aesthetic: an unselected chip is transparent with a hairline outline + a neutral
    // hollow icon; a selected chip fills solid `primary` (the blue active state the user asked for).
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        border = border,
        modifier = Modifier
            .height(38.dp)
            .minimumInteractiveComponentSize(),
    ) {
        val centeredLineHeight = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeightStyle = centeredLineHeight,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Factory for the **item-create** dock chips (reminder presets + property toggles) in display order:
 * the four reminder presets first (single-select), then Important and Repeat (independent toggles).
 *
 * Labels are passed in (localized by the caller). [pickTimeLabel] / [repeatLabel] are the dynamic
 * labels: pass the resolved datetime / repeat summary when the respective chip is active, otherwise
 * the default "Pick time…" / "Repeat" text. The `selected*` params drive the blue-fill state.
 *
 * @param selectedReminder Which reminder preset is active (one of the `REMIND_*` actions), or null.
 * @param importantSelected Whether the Important toggle is on.
 * @param repeatSelected    Whether a repeat is configured.
 */
fun gistiItemCreatePromptChips(
    in1HourLabel: String,
    tomorrowMorningLabel: String,
    tonightLabel: String,
    pickTimeLabel: String,
    importantLabel: String,
    repeatLabel: String,
    selectedReminder: GistiItemCreateAction?,
    importantSelected: Boolean,
    repeatSelected: Boolean,
): List<GistiSelectableChip<GistiItemCreateAction>> = listOf(
    GistiSelectableChip(
        icon = Icons.Outlined.Notifications,
        label = in1HourLabel,
        action = GistiItemCreateAction.REMIND_1H,
        selected = selectedReminder == GistiItemCreateAction.REMIND_1H,
    ),
    GistiSelectableChip(
        icon = Icons.Outlined.Notifications,
        label = tomorrowMorningLabel,
        action = GistiItemCreateAction.REMIND_TOMORROW_MORNING,
        selected = selectedReminder == GistiItemCreateAction.REMIND_TOMORROW_MORNING,
    ),
    GistiSelectableChip(
        icon = Icons.Outlined.Notifications,
        label = tonightLabel,
        action = GistiItemCreateAction.REMIND_TONIGHT,
        selected = selectedReminder == GistiItemCreateAction.REMIND_TONIGHT,
    ),
    GistiSelectableChip(
        icon = Icons.Outlined.Schedule,
        label = pickTimeLabel,
        action = GistiItemCreateAction.REMIND_PICK,
        selected = selectedReminder == GistiItemCreateAction.REMIND_PICK,
    ),
    GistiSelectableChip(
        icon = Icons.Outlined.StarBorder,
        label = importantLabel,
        action = GistiItemCreateAction.IMPORTANT,
        selected = importantSelected,
    ),
    GistiSelectableChip(
        icon = Icons.Outlined.Repeat,
        label = repeatLabel,
        action = GistiItemCreateAction.REPEAT,
        selected = repeatSelected,
    ),
)
