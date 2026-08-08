package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab

data class CreateChecklistState(
    val name: String = "",
    val items: List<ChecklistItem> = emptyList(),
    val nameError: String? = null,
    val isEditMode: Boolean = false,
    val editChecklistId: Long? = null,
    val newItemText: String = "",
    // Inline item editing
    val editingItemId: String? = null,
    val editingItemText: String = "",
    // Gate: false when free user is at the checklist limit (edit mode always passes)
    val canCreateChecklist: Boolean = true,

    // ── v2 "create project" screen ──────────────────────────────────────────────────────────────
    //
    // Everything below this line is read ONLY by the v2 layout (`useProjectForm = true`). The v1
    // "Classic view" arm renders the pre-redesign screen, which never touches these fields — see
    // [CreateChecklistScreen].

    /**
     * Free-plan project ceiling the limit banner quotes, straight from `GetUserLimitsUseCase`.
     *
     * `null` until the limits Flow emits, and the banner does not render while it is null. Seeding it
     * from a constant (`RemoteConfigDefaults`) would put a SECOND copy of an RC value in the client —
     * exactly the shape behind the shipped `FREE_CHECKLIST_LIMIT = 4` vs RC 5 bug. Nothing is lost by
     * waiting: the banner is gated on [canCreateChecklist] being false, which cannot happen before
     * the same emission.
     */
    val maxChecklists: Int? = null,
    /** `limits.canCreateWeeklyChecklist` — false swaps the Weekly switch for a paywall lock. */
    val canCreateWeekly: Boolean = true,
    /**
     * `null` = the user has not touched the disclosure yet, so the screen picks the default by
     * window size (collapsed on Compact, expanded on Medium/Expanded — vertical space is not scarce
     * there). Once toggled it holds the user's choice across rotation, which a `remember` would not.
     *
     * The screen resolves the effective value and hands it back in
     * [CreateChecklistIntent.OnToggleMoreOptions]; the ViewModel must NOT re-derive a default of its
     * own, or the first tap on a tablet writes the value that was already showing and looks dead.
     */
    val moreOptionsExpanded: Boolean? = null,
    /** True between "Create" and the navigation away: the CTA spins and swallows repeat taps. */
    val isSubmitting: Boolean = false,
    /**
     * One-shot latch for the name autofocus, in STATE rather than `remember`: the screen is
     * recreated when it comes back from the templates list and on rotation, and a `remember`-based
     * guard re-fires the request, dropping the keyboard over content the user just went to fetch.
     */
    val nameFocusConsumed: Boolean = false,
    /**
     * Set synchronously when a submit is rejected for a blank name; cleared on the next keystroke.
     *
     * Separate from [nameError] on purpose: the error TEXT is resolved asynchronously (`getString`
     * suspends) and can, in a resource-less environment, fail to resolve at all. Driving the field's
     * error highlight off the text alone means a failed lookup shows an un-highlighted field with no
     * message — a rejected action with zero feedback. This flag keeps the highlight regardless.
     */
    val nameInvalid: Boolean = false,
    /**
     * Monotonic counter the screen consumes to scroll the name field back into view and refocus it.
     * A counter, not a Boolean, so two rejected submits in a row are two distinct signals (same
     * shape as `inlineAddFocusSignal` / `homeSignal` elsewhere in the app).
     */
    val nameErrorFocusSignal: Int = 0,
    /** `ChecklistViewMode.Weekly` — the list is planned per weekday and created EMPTY. */
    val weeklyMode: Boolean = false,
    /** Confirmation for the destructive Weekly switch (it drops every task already typed). */
    val weeklySwitchConfirmOpen: Boolean = false,
    val foldersEnabled: Boolean = false,
    val separateCompleted: Boolean = false,
    val autoDeleteCompleted: Boolean = false,

    // ── Reminder (DESIGN_SPEC §2.1 / §2.5) ──────────────────────────────────────────────────────
    //
    // The project does not exist yet, so nothing here is persisted or scheduled while the form is
    // open: the whole configuration is STAGED in state and applied against the returned id right
    // after `addChecklist()` (spec §11 pitfall 9 — `ReminderScheduler` needs a checklist id).

    /** Staged one-shot reminder, epoch millis. Mutually exclusive with [repeatRule] in the UI. */
    val reminderAt: Long? = null,
    /** Staged recurring schedule. */
    val repeatRule: ReminderRepeatRule? = null,
    /** Minutes past midnight the staged [repeatRule] should fire at. */
    val repeatTimeOfDayMinutes: Int? = null,
    /** The shared `ReminderSheet` is on screen. */
    val reminderSheetOpen: Boolean = false,
    /** Renders the sheet's locked-paywall banner instead of its tabs (free reminder quota spent). */
    val reminderSheetLocked: Boolean = false,
    val activeReminderTab: ReminderTab = ReminderTab.ONCE,
    /** Non-null only while the REPEAT tab is being edited; `null` collapses it back to the presets. */
    val pendingRepeatConfig: PendingRepeatConfig? = null,
    val showEndConditionPicker: Boolean = false,
    /** The date+time picker the sheet's "Pick a date" row opens. */
    val showCustomPicker: Boolean = false,
    val customPickerDateMillis: Long? = null,
    val customPickerMinDateMillis: Long = 0L,
    val customPickerInitialHour: Int = 9,
    val isCustomTimeInPast: Boolean = false,
) : State

sealed interface CreateChecklistIntent : Intent {
    data object OnBackClick : CreateChecklistIntent
    data object OnSaveClick : CreateChecklistIntent
    data object OnChooseTemplateClick : CreateChecklistIntent
    data class OnNameChange(val name: String) : CreateChecklistIntent
    data class OnNewItemTextChange(val text: String) : CreateChecklistIntent
    data object OnAddItemFromInput : CreateChecklistIntent
    data class OnDeleteItem(val item: ChecklistItem) : CreateChecklistIntent
    // Inline item editing
    data class OnStartItemEdit(val itemId: String) : CreateChecklistIntent
    data class OnItemEditTextChange(val text: String) : CreateChecklistIntent
    data object OnConfirmItemEdit : CreateChecklistIntent
    data object OnCancelItemEdit : CreateChecklistIntent

    // ── v2 "create project" screen ──────────────────────────────────────────────────────────────

    /** Marks the one-shot name autofocus as spent; see [CreateChecklistState.nameFocusConsumed]. */
    data object OnNameFocusConsumed : CreateChecklistIntent

    /**
     * @param currentlyExpanded the value the user is looking at RIGHT NOW, resolved by the screen
     *   (`state.moreOptionsExpanded ?: !isCompact`). Carried in the intent because the default
     *   depends on window size, which the ViewModel cannot see: deriving `!(stored ?: false)` here
     *   makes the first tap on a tablet write `true` over an already-expanded section — a visible
     *   no-op that only reacts on the second tap.
     */
    data class OnToggleMoreOptions(val currentlyExpanded: Boolean) : CreateChecklistIntent

    /**
     * Weekly is destructive when tasks are already typed (a Weekly list is created empty), so this
     * only OPENS the confirmation in that case — [OnWeeklySwitchConfirm] is what applies it.
     */
    data class OnWeeklyToggled(val enabled: Boolean) : CreateChecklistIntent
    data object OnWeeklySwitchConfirm : CreateChecklistIntent
    data object OnWeeklySwitchDismiss : CreateChecklistIntent
    /** Tap on the padlock that replaces the Weekly switch once the free weekly quota is used. */
    data object OnWeeklyLockClick : CreateChecklistIntent

    data class OnFoldersToggled(val enabled: Boolean) : CreateChecklistIntent
    data class OnSeparateCompletedToggled(val enabled: Boolean) : CreateChecklistIntent
    data class OnAutoDeleteToggled(val enabled: Boolean) : CreateChecklistIntent

    /** "Become Pro" inside the free-limit banner. */
    data object OnLimitBannerUpgradeClick : CreateChecklistIntent

    // ── Reminder row + shared ReminderSheet ─────────────────────────────────────────────────────

    /** Tap on the "Reminder" settings row. */
    data object OnReminderClick : CreateChecklistIntent
    data class OnReminderTabSelected(val tab: ReminderTab) : CreateChecklistIntent
    /** One of the ONCE-tab presets ("This evening", "Tomorrow morning", …), epoch millis. */
    data class OnReminderPresetSelected(val triggerAtMillis: Long) : CreateChecklistIntent
    data object OnCustomDateRequested : CreateChecklistIntent
    data class OnCustomDateSelected(val dateMillis: Long) : CreateChecklistIntent
    data class OnCustomTimeChanged(val hour: Int, val minute: Int) : CreateChecklistIntent
    data class OnCustomTimeSelected(val hour: Int, val minute: Int) : CreateChecklistIntent
    data object OnRemoveReminder : CreateChecklistIntent
    /** Closes the sheet AND the date/time picker, dropping any half-edited repeat config. */
    data object OnDismissReminderUI : CreateChecklistIntent

    data class OnRepeatTypeSelected(val type: RepeatType) : CreateChecklistIntent
    data class OnSmartPresetSelected(val config: PendingRepeatConfig) : CreateChecklistIntent
    data class OnRepeatIntervalChanged(val interval: Int) : CreateChecklistIntent
    data class OnWeekDayToggled(val dayNumber: Int) : CreateChecklistIntent
    data class OnResetChecksToggled(val enabled: Boolean) : CreateChecklistIntent
    data class OnRepeatTimeChanged(val hour: Int, val minute: Int) : CreateChecklistIntent
    data object OnSaveRepeat : CreateChecklistIntent
    data object OnRemoveRepeat : CreateChecklistIntent

    data object OnEndConditionClick : CreateChecklistIntent
    data class OnEndConditionSelected(val condition: RepeatEndCondition) : CreateChecklistIntent
    data object OnDismissEndConditionPicker : CreateChecklistIntent

    /** "Become Pro" inside the reminder sheet's locked banner. */
    data object OnReminderUpgradeClick : CreateChecklistIntent
}
