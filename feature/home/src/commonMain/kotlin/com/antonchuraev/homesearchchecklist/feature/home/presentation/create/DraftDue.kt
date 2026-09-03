package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.DateInputMethod
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetId
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.firstRepeatTriggerAt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Everything the capture dock's due rail currently has OPEN, as one value on the host's state.
 *
 * Deliberately not `remember`ed inside the dock, even though [plannerExpanded] is pure layout: the
 * dock lives in `AppScaffold`'s `bottomBar` slot, its planner is rendered by one slot lambda and the
 * source row it hides by another, and a screenshot test has to be able to mount the expanded frame
 * without driving a tap through a focused text field (which never reaches idle — the caret blinks
 * forever, so `waitForIdle` hangs). State on the contract makes both trivial.
 *
 * Shared by both capture hosts rather than duplicated per screen: the Inbox tab and the Calendar tab
 * ship the SAME dock, and the last time these two grew a behaviour each they drifted into two
 * different answers to "what can I attach to a task".
 */
data class DraftDueUiState(
    /** The 2x3 planner grid under the rail. Collapsing it brings the AI source row back. */
    val plannerExpanded: Boolean = false,
    /** Non-null while the v1 [ReminderSheet] is up over the dock. */
    val sheet: DraftDueSheetUi? = null,
    /** Non-null while the v1 date+time picker is up (raised FROM the sheet). */
    val picker: DraftDuePickerUi? = null,
)

/**
 * The v1 reminder/repeat sheet's working state while it edits the DRAFT rather than a stored item.
 *
 * Same shape as the Inbox's per-item `InboxItemReminderUi` and deliberately a separate type: this one
 * is scoped to a task that does not exist yet, so it carries no full-screen-delivery toggle (there is
 * no row to write it on) and its Save stages onto [TaskDraft] instead of persisting.
 */
data class DraftDueSheetUi(
    val tab: ReminderTab = ReminderTab.ONCE,
    /**
     * Free-tier gate: renders the locked upgrade banner instead of the tab content.
     *
     * Evaluated when the REPEAT tab is opened, exactly where `ChecklistDetailViewModel` evaluates it
     * for the same chip — the rail, its presets and expanding the grid never reach a paywall, because
     * a due date is free and only the recurring NOTIFICATION is not.
     */
    val locked: Boolean = false,
    val repeatConfig: PendingRepeatConfig? = null,
    val endConditionPickerOpen: Boolean = false,
)

/**
 * Non-null while the custom date+time picker is up.
 *
 * [minDateMillis] has no default: it is UTC midnight of today, which only the ViewModel can compute,
 * and a wrong floor makes yesterday selectable — i.e. a reminder that fires the moment it is saved.
 */
data class DraftDuePickerUi(
    val minDateMillis: Long,
    val dateMillis: Long? = null,
    val initialHour: Int = 9,
    val timeInPast: Boolean = false,
)

/**
 * Everything the due rail, its planner and the sheet behind them can report — ONE vocabulary for
 * both capture hosts.
 *
 * The two hosts have their own `Intent` hierarchies (`InboxIntent`, `TodayIntent`) and each wraps
 * this one in a single case. That is what lets [DraftDueController] be the only implementation of
 * these rules: a second hand-rolled copy per screen is precisely how the two docks would drift, and
 * the drift would be invisible until someone compared them by hand.
 */
sealed interface DraftDueIntent {

    // ── Rail ─────────────────────────────────────────────────────────────────────────────────
    /** The leading chip was tapped — toggles the planner. */
    data object OnLeadClick : DraftDueIntent

    /** The `x` inside the leading chip — drops the date, leaves the text alone. */
    data object OnClearDate : DraftDueIntent

    /** A preset was tapped, in the rail or in the grid. Re-tapping the active one clears it. */
    data class OnPresetClick(val id: DuePresetId) : DraftDueIntent

    /** "Done" under the grid, or anything else that should put the planner away. */
    data object OnPlannerCollapse : DraftDueIntent

    // ── Planner exits into the v1 sheet ──────────────────────────────────────────────────────
    /** "Pick date" cell and the "Time" control both open the ONCE tab; "Repeat" opens REPEAT. */
    data object OnPickDateClick : DraftDueIntent
    data object OnTimeClick : DraftDueIntent
    data object OnRepeatClick : DraftDueIntent

    // ── The v1 sheet ─────────────────────────────────────────────────────────────────────────
    data class OnSheetTabSelected(val tab: ReminderTab) : DraftDueIntent
    data class OnSheetPresetSelected(val triggerAtMillis: Long) : DraftDueIntent
    data object OnSheetCustomDateRequested : DraftDueIntent
    data object OnSheetRemoveReminder : DraftDueIntent
    data object OnSheetDismiss : DraftDueIntent
    data object OnSheetUpgradeClick : DraftDueIntent

    data class OnRepeatTypeSelected(val type: RepeatType) : DraftDueIntent
    data class OnSmartPresetSelected(val config: PendingRepeatConfig) : DraftDueIntent
    data class OnRepeatIntervalChanged(val interval: Int) : DraftDueIntent
    data class OnWeekDayToggled(val dayNumber: Int) : DraftDueIntent
    data class OnResetChecksToggled(val enabled: Boolean) : DraftDueIntent
    data class OnRepeatTimeChanged(val hour: Int, val minute: Int) : DraftDueIntent
    data object OnEndConditionClick : DraftDueIntent
    data class OnEndConditionSelected(val condition: RepeatEndCondition) : DraftDueIntent
    data object OnEndConditionDismiss : DraftDueIntent
    data object OnRepeatSave : DraftDueIntent
    data object OnRepeatRemove : DraftDueIntent

    // ── The v1 date+time picker, raised from the sheet ───────────────────────────────────────
    data class OnPickerDateSelected(val dateMillis: Long) : DraftDueIntent
    data class OnPickerTimeChanged(val hour: Int, val minute: Int) : DraftDueIntent
    data class OnPickerTimeSelected(val hour: Int, val minute: Int) : DraftDueIntent
    data object OnPickerDismiss : DraftDueIntent
}

/**
 * The domain preset a design-system offer stands for.
 *
 * A total mapping in this direction and a partial one in the other ([toDuePresetId]) — that asymmetry
 * is the type system carrying a product rule: every rail offer resolves to a value, but
 * [ItemCreateReminderPreset.CUSTOM] is the picker's RESULT and has no chip to be drawn on.
 */
fun DuePresetId.toReminderPreset(): ItemCreateReminderPreset = when (this) {
    DuePresetId.TONIGHT -> ItemCreateReminderPreset.TONIGHT
    DuePresetId.TOMORROW -> ItemCreateReminderPreset.TOMORROW_MORNING
    DuePresetId.WEEKEND -> ItemCreateReminderPreset.WEEKEND
    DuePresetId.IN_1_HOUR -> ItemCreateReminderPreset.ONE_HOUR
    DuePresetId.NEXT_WEEK -> ItemCreateReminderPreset.NEXT_WEEK
}

/** The rail offer a domain preset is drawn as, or `null` for the one that is not an offer. */
fun ItemCreateReminderPreset.toDuePresetId(): DuePresetId? = when (this) {
    ItemCreateReminderPreset.TONIGHT -> DuePresetId.TONIGHT
    ItemCreateReminderPreset.TOMORROW_MORNING -> DuePresetId.TOMORROW
    ItemCreateReminderPreset.WEEKEND -> DuePresetId.WEEKEND
    ItemCreateReminderPreset.ONE_HOUR -> DuePresetId.IN_1_HOUR
    ItemCreateReminderPreset.NEXT_WEEK -> DuePresetId.NEXT_WEEK
    ItemCreateReminderPreset.CUSTOM -> null
}

/**
 * Whole days from today to [dueAtMillis] in [timeZone] — the `due_date_offset_days` dimension.
 *
 * Compared as CALENDAR DAYS rather than as a millisecond delta divided by 86_400_000, because the
 * question is "which day did the user aim at": a task set for 09:00 tomorrow is 1 when captured at
 * 08:00 and would be 0 under a duration-based answer when captured at 22:00 the evening before.
 */
fun dueDateOffsetDays(dueAtMillis: Long, nowMillis: Long, timeZone: TimeZone): Int {
    val due = Instant.fromEpochMilliseconds(dueAtMillis).toLocalDateTime(timeZone).date
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    return (due.toEpochDays() - today.toEpochDays()).toInt()
}

/**
 * How the date on [this] draft was arrived at — the `date_input_method` dimension.
 *
 * [dueAtMillis] is the value the send path actually resolved, passed in rather than recomputed: it
 * is the difference between "a chip is selected" and "a date was written", and recomputing it here
 * would be a second answer to a question already answered one line earlier at the call site.
 *
 * Order matters. A staged repeat and an explicit picker result both come out of the v1 sheet, so
 * both report [DateInputMethod.PICKER]; a preset chip beats the parser, mirroring the send path,
 * where an explicit chip overrides a phrase the parser merely recognised.
 */
fun TaskDraft.dateInputMethod(dueAtMillis: Long?): DateInputMethod = when {
    dueAtMillis == null -> DateInputMethod.NONE
    repeat != null -> DateInputMethod.PICKER
    reminderPreset == ItemCreateReminderPreset.CUSTOM -> DateInputMethod.PICKER
    reminderPreset != null -> DateInputMethod.PRESET
    else -> DateInputMethod.PARSED_FROM_TEXT
}

/**
 * Everything a draft contributes to the item about to be created — resolved ONCE, at send.
 *
 * The four fields travel together because they are written together and are meaningless apart: a
 * repeat rule with no [repeatFirstTriggerAt] is a schedule with no alarm, and a trigger with no rule
 * is a one-shot wearing a repeat's clothes. Building them at two call sites (the Inbox pager and the
 * Calendar tab both capture into the system Inbox) is how one of them would end up persisting a rule
 * it never scheduled.
 */
data class DraftDueOutcome(
    val reminderAt: Long?,
    val repeatRule: ReminderRepeatRule?,
    val repeatTimeOfDayMinutes: Int?,
    val repeatFirstTriggerAt: Long?,
    /**
     * A moment the user named by hand has gone stale between naming it and pressing Send, so it was
     * dropped instead of written.
     *
     * The host MUST surface this — a due date that silently does not appear is the failure mode the
     * whole rail exists to remove, and unlike a preset there is nothing to roll forward TO: only the
     * user knows which later moment they meant.
     */
    val pickedTimeExpired: Boolean = false,
) {
    /**
     * The moment the task is due, by whichever mechanism produced it — the value `has_due_date` and
     * `due_date_offset_days` are read from.
     *
     * A repeating task HAS a due date: its first occurrence. Reporting only [reminderAt] would count
     * every recurring capture as undated and understate the metric this whole rail is judged by.
     */
    val dueAtMillis: Long? get() = reminderAt ?: repeatFirstTriggerAt
}

/**
 * What to write on the new item for the due date staged on this draft.
 *
 * [resolveReminderAtNow] is re-run here rather than trusting [TaskDraft.reminderAt]: the dock stays
 * open while the user types, so a "Tonight" tapped at 17:50 and sent at 18:10 has to roll forward
 * instead of being filed into the past.
 *
 * A staged repeat and a one-shot are mutually exclusive on the draft (`withRepeat` and
 * `withCustomReminder` each clear the other), so the branch below is a real either/or rather than a
 * precedence rule that could silently drop half of what the user set.
 */
fun TaskDraft.resolveDueOutcome(
    now: Instant,
    timeZone: TimeZone,
): DraftDueOutcome {
    val config = repeat
        ?: run {
            val resolved = resolveReminderAtNow(now, timeZone)
            // A PICKED moment is the one anchor `resolveReminderAtNow` deliberately never recomputes
            // — the user named it, so rolling it forward would file the task at a time they did not
            // choose. That is right up until Send: the dock stays open while they type, and a time
            // picked at 18:05 and sent at 18:15 is a trigger in the past. AlarmManager fires those
            // immediately, i.e. a reminder that "rings" the instant the task is created.
            //
            // A preset cannot reach this branch — every WALL_CLOCK anchor rolls itself forward and
            // OFFSET_FROM_SEND is measured from now — so this drops nothing that could have survived.
            val expired = resolved != null && resolved <= now.toEpochMilliseconds()
            return DraftDueOutcome(
                reminderAt = resolved.takeUnless { expired },
                repeatRule = null,
                repeatTimeOfDayMinutes = null,
                repeatFirstTriggerAt = null,
                pickedTimeExpired = expired,
            )
        }

    val minutes = config.timeHour * MINUTES_PER_HOUR + config.timeMinute
    return DraftDueOutcome(
        reminderAt = null,
        repeatRule = config.toRule(),
        repeatTimeOfDayMinutes = minutes,
        // Shared with the item-scoped path rather than re-derived: handing AlarmManager a trigger in
        // the past makes it fire immediately, i.e. a daily reminder that rings the instant it is
        // created and then not again that day. The rule lives beside its tests in the Inbox package.
        repeatFirstTriggerAt = firstRepeatTriggerAt(minutes, now, timeZone),
    )
}

private const val MINUTES_PER_HOUR = 60
