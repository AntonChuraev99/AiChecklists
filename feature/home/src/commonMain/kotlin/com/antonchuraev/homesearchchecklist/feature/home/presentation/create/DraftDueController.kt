package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_reminder_time_in_past
import aichecklists.core.designsystem.generated.resources.inbox_task_update_failed
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetId
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource

/**
 * The rules behind the capture dock's due rail, in ONE place, for both hosts.
 *
 * ## Why a delegate and not two `when` branches
 * The Inbox tab and the Calendar tab mount the same dock and must answer "when" identically. Written
 * twice they would not stay identical: the two v2 capture surfaces already drifted once (only one of
 * them pre-selected a reminder), and the drift is invisible until someone opens both by hand. So each
 * ViewModel owns its draft and forwards one intent case here; this object owns every rule that acts
 * on it.
 *
 * ## What it deliberately does NOT do
 * It never persists and never schedules. Everything it produces lands on the [TaskDraft] the host
 * holds, and the host's own send path writes it — the draft is the single staging area for a task
 * that does not exist yet, and a second write path here would be a second source of truth for the
 * same fields.
 *
 * @param draft the host's draft flow. Mutated through the `with*` extensions rather than `copy`, so
 *   the chip rules (single-select presets, re-tap clears, a picked time cancels a repeat) stay in the
 *   one place that already states them.
 * @param limits the host's live [UserLimits], read synchronously at gate time — a suspend read here
 *   would open the sheet unlocked for a frame and then lock it under the user's finger. The whole
 *   object rather than a premium flag on purpose: the free ceiling is a Remote Config value, and a
 *   caller that only receives `isPremium` has no choice but to hardcode the number beside it.
 *   Null (limits not loaded yet, or the read failed) means the gate opens UNLOCKED.
 * @param countActiveReminders how many recurring reminders are already armed. Suspend and fallible;
 *   a failure opens the sheet UNLOCKED rather than locking paid behaviour behind a read error.
 * @param onUpgradeClick the locked banner's CTA. Navigation belongs to the host's navigator.
 * @param onMessage user-visible failure text. A [StringResource], never a literal — the host resolves
 *   it, because only the host knows whether its channel is a snackbar or a side effect.
 */
class DraftDueController(
    private val scope: CoroutineScope,
    private val logger: AppLogger,
    private val tag: String,
    private val draft: MutableStateFlow<TaskDraft>,
    private val limits: () -> UserLimits?,
    private val countActiveReminders: suspend () -> Int,
    private val onUpgradeClick: () -> Unit,
    private val onMessage: (StringResource) -> Unit,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    private val _state = MutableStateFlow(DraftDueUiState())
    val state: StateFlow<DraftDueUiState> = _state.asStateFlow()

    @Suppress("CyclomaticComplexMethod")
    fun onIntent(intent: DraftDueIntent) {
        when (intent) {
            // ── Rail ─────────────────────────────────────────────────────────────────────────
            DraftDueIntent.OnLeadClick ->
                _state.update { it.copy(plannerExpanded = !it.plannerExpanded) }

            DraftDueIntent.OnPlannerCollapse -> _state.update { it.copy(plannerExpanded = false) }

            // Through `withCustomReminder(null)`, which is already "no reminder, no preset, no
            // repeat" — the one place that spells out what clearing an answer means. The parsed
            // token goes with it and has to: it is the SECOND thing the lead chip can be showing, so
            // leaving it would make Clear look like it did nothing.
            DraftDueIntent.OnClearDate ->
                draft.update { it.withCustomReminder(null).copy(parsedToken = null) }

            is DraftDueIntent.OnPresetClick -> applyPreset(intent.id)

            // ── Planner exits into the v1 sheet ──────────────────────────────────────────────
            // Pick date and Time are the SAME door on purpose: both are "I want to name the moment
            // myself", and the v1 sheet's ONCE tab is where that is done — its own "Pick date & time"
            // row raises the picker. Two labels for one destination is what the panel's layout asks
            // for; two DIFFERENT surfaces for it would be the thinner v2 twin this project bans.
            DraftDueIntent.OnPickDateClick, DraftDueIntent.OnTimeClick -> openSheet(ReminderTab.ONCE)
            DraftDueIntent.OnRepeatClick -> openRepeatSheet()

            // ── The v1 sheet ─────────────────────────────────────────────────────────────────
            is DraftDueIntent.OnSheetTabSelected -> selectTab(intent.tab)

            is DraftDueIntent.OnSheetPresetSelected -> {
                // Guarded exactly like both existing hosts: a preset resolved when the sheet opened
                // can be in the past by the time it is tapped, and AlarmManager fires a past trigger
                // immediately — a reminder that "rings" the instant you set it.
                if (intent.triggerAtMillis <= now().toEpochMilliseconds()) {
                    logger.warning(tag, "due preset ${intent.triggerAtMillis} is in the past — ignored")
                    onMessage(Res.string.inbox_reminder_time_in_past)
                } else {
                    draft.update { it.withCustomReminder(intent.triggerAtMillis) }
                    closeSheet()
                }
            }

            DraftDueIntent.OnSheetCustomDateRequested -> openPicker()

            DraftDueIntent.OnSheetRemoveReminder -> {
                draft.update { it.withCustomReminder(null) }
                closeSheet()
            }

            DraftDueIntent.OnSheetDismiss -> closeSheet()

            DraftDueIntent.OnSheetUpgradeClick -> {
                closeSheet()
                onUpgradeClick()
            }

            is DraftDueIntent.OnRepeatTypeSelected -> updateRepeatConfig {
                // Same reset as every other host: a type switch drops the interval and weekday
                // selection that belonged to the previous type, which would otherwise survive as an
                // invisible part of the rule.
                it.copy(type = intent.type, isCustom = false, interval = 1, weekDays = emptySet())
            }

            is DraftDueIntent.OnSmartPresetSelected -> updateRepeatConfig { intent.config }

            is DraftDueIntent.OnRepeatIntervalChanged -> updateRepeatConfig {
                it.copy(interval = intent.interval.coerceIn(1, MAX_REPEAT_INTERVAL), isCustom = true)
            }

            is DraftDueIntent.OnWeekDayToggled -> updateRepeatConfig { config ->
                val days = config.weekDays.toMutableSet()
                if (!days.add(intent.dayNumber)) days.remove(intent.dayNumber)
                config.copy(weekDays = days, isCustom = true)
            }

            is DraftDueIntent.OnResetChecksToggled ->
                updateRepeatConfig { it.copy(resetChecks = intent.enabled) }

            is DraftDueIntent.OnRepeatTimeChanged ->
                updateRepeatConfig { it.copy(timeHour = intent.hour, timeMinute = intent.minute) }

            DraftDueIntent.OnEndConditionClick ->
                _state.update { it.copy(sheet = it.sheet?.copy(endConditionPickerOpen = true)) }

            is DraftDueIntent.OnEndConditionSelected -> {
                updateRepeatConfig { it.copy(endCondition = intent.condition) }
                _state.update { it.copy(sheet = it.sheet?.copy(endConditionPickerOpen = false)) }
            }

            DraftDueIntent.OnEndConditionDismiss ->
                _state.update { it.copy(sheet = it.sheet?.copy(endConditionPickerOpen = false)) }

            DraftDueIntent.OnRepeatSave -> {
                val config = _state.value.sheet?.repeatConfig
                if (config == null) {
                    // Not reachable through the UI (the REPEAT tab seeds a config on open), so a
                    // quiet return would hide a contract break rather than a user mistake.
                    logger.warning(tag, "repeat save skipped: no staged config")
                    onMessage(Res.string.inbox_task_update_failed)
                } else {
                    draft.update { it.withRepeat(config) }
                    closeSheet()
                }
            }

            DraftDueIntent.OnRepeatRemove -> {
                draft.update { it.withRepeat(null) }
                closeSheet()
            }

            // ── The v1 date+time picker ──────────────────────────────────────────────────────
            is DraftDueIntent.OnPickerDateSelected -> selectPickerDate(intent.dateMillis)
            is DraftDueIntent.OnPickerTimeChanged -> updatePickerTimeInPast(intent.hour, intent.minute)
            is DraftDueIntent.OnPickerTimeSelected -> commitPickerDateTime(intent.hour, intent.minute)
            DraftDueIntent.OnPickerDismiss -> _state.update { it.copy(picker = null) }
        }
    }

    /**
     * Everything the dock has open goes away, and the planner with it.
     *
     * Called by the hosts after a successful send: the dock stays open for the NEXT capture, so a
     * planner left expanded would sit over the fresh draft claiming a date the new task does not have
     * — and would keep the AI source row hidden while it did.
     */
    fun reset() {
        _state.value = DraftDueUiState()
    }

    // ── Rail ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Applies (or, on a re-tap of the active one, removes) a preset.
     *
     * Clearing a staged repeat alongside it is the same rule `withCustomReminder` already states in
     * the other direction: a one-shot preset and a recurring rule are two answers to "when", the lead
     * chip shows exactly one, and keeping both would leave the rail displaying a fact the send path
     * would then contradict.
     */
    private fun applyPreset(id: DuePresetId) {
        val preset = id.toReminderPreset()
        draft.update { current ->
            val next = current.withPreset(preset, now(), timeZone())
            if (next.reminderPreset != null) next.copy(repeat = null) else next
        }
    }

    // ── Sheet ────────────────────────────────────────────────────────────────────────────────

    private fun openSheet(tab: ReminderTab) {
        _state.update {
            it.copy(sheet = DraftDueSheetUi(tab = tab, repeatConfig = draft.value.repeat))
        }
    }

    /**
     * The REPEAT tab, behind the free-tier gate the checklist detail screen already runs for the same
     * chip.
     *
     * This is the ONLY place in the rail that can reach a paywall, and it is the pre-existing one: a
     * due DATE is free (the owner's decision — otherwise every other tap hits a wall), the recurring
     * NOTIFICATION is what the free tier caps. A staged repeat re-opens unlocked, so editing the rule
     * the user already configured never turns into an upsell.
     */
    private fun openRepeatSheet() {
        scope.launch {
            val staged = draft.value.repeat
            val atLimit = runCatching {
                // The ceiling comes from Remote Config through `UserLimits`, never from a constant
                // here. A local number is the drift this project has already paid for: the served
                // value is an order of magnitude looser than the `1` a hand-written gate would
                // guess, so a hardcoded copy silently makes a NEW surface the strictest one in the
                // app. `canCreateRecurringReminder` is the same helper the detail screen and the
                // create screen already call — one rule, three call sites, no fourth spelling.
                //
                // Null limits (not loaded yet) fall through to `true` = allowed, matching those
                // call sites: never lock paid-for behaviour on a value that has not arrived.
                val current = limits()
                val allowed = current?.canCreateRecurringReminder(countActiveReminders()) ?: true
                staged == null && !allowed
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                // Open UNLOCKED rather than lock paid behaviour behind a read error — and say why the
                // gate could not be evaluated, because a wrongly-locked sheet is otherwise silent.
                logger.error(tag, "repeat gate: countActiveReminders failed: ${e.message}", e)
                false
            }
            _state.update {
                it.copy(
                    sheet = DraftDueSheetUi(
                        tab = ReminderTab.REPEAT,
                        locked = atLimit,
                        repeatConfig = if (atLimit) null else staged ?: PendingRepeatConfig(),
                    ),
                )
            }
        }
    }

    private fun selectTab(tab: ReminderTab) {
        _state.update { state ->
            val sheet = state.sheet ?: return@update state
            state.copy(
                sheet = sheet.copy(
                    tab = tab,
                    // Seed the repeat editor the first time the tab is opened; an already-edited
                    // config is left alone, so switching tabs never loses input.
                    repeatConfig = if (tab == ReminderTab.REPEAT) {
                        sheet.repeatConfig ?: draft.value.repeat ?: PendingRepeatConfig()
                    } else {
                        sheet.repeatConfig
                    },
                ),
            )
        }
    }

    private inline fun updateRepeatConfig(update: (PendingRepeatConfig) -> PendingRepeatConfig) {
        _state.update { state ->
            val sheet = state.sheet ?: return@update state
            state.copy(sheet = sheet.copy(repeatConfig = update(sheet.repeatConfig ?: PendingRepeatConfig())))
        }
    }

    private fun closeSheet() = _state.update { it.copy(sheet = null) }

    // ── Picker ───────────────────────────────────────────────────────────────────────────────

    /**
     * Raises the date+time picker and puts the sheet away, mirroring the detail screen: the two are
     * both modal, and two live `ModalBottomSheet`s fight over the scrim and over predictive back.
     */
    private fun openPicker() {
        val tz = timeZone()
        val today = now().toLocalDateTime(tz).date
        // The Material date picker works in UTC millis, so "today" has to be expressed as UTC
        // midnight or the day before becomes selectable east of Greenwich.
        val todayUtcMidnight = LocalDateTime(today, LocalTime(0, 0))
            .toInstant(TimeZone.UTC).toEpochMilliseconds()
        _state.update {
            it.copy(sheet = null, picker = DraftDuePickerUi(minDateMillis = todayUtcMidnight))
        }
    }

    private fun selectPickerDate(dateMillis: Long) {
        val tz = timeZone()
        val nowLocal = now().toLocalDateTime(tz)
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        // Picking today pre-selects the next full hour; any other day starts at 09:00.
        val initialHour = if (selectedDate == nowLocal.date) {
            (nowLocal.hour + 1).coerceAtMost(LAST_HOUR)
        } else {
            DEFAULT_PICKER_HOUR
        }
        _state.update {
            it.copy(
                picker = it.picker?.copy(
                    dateMillis = dateMillis,
                    initialHour = initialHour,
                    timeInPast = false,
                ),
            )
        }
    }

    private fun updatePickerTimeInPast(hour: Int, minute: Int) {
        val dateMillis = _state.value.picker?.dateMillis
        if (dateMillis == null) {
            // Unreachable through the UI (the picker asks for a day before a time), so this is a
            // contract violation worth a line rather than a quiet return.
            logger.warning(tag, "picker time changed with no date chosen — in-past hint skipped")
            return
        }
        val tz = timeZone()
        val nowLocal = now().toLocalDateTime(tz)
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val inPast = selectedDate == nowLocal.date && LocalTime(hour, minute) <= nowLocal.time
        _state.update { it.copy(picker = it.picker?.copy(timeInPast = inPast)) }
    }

    private fun commitPickerDateTime(hour: Int, minute: Int) {
        val dateMillis = _state.value.picker?.dateMillis
        if (dateMillis == null) {
            logger.warning(tag, "custom due date skipped: no day chosen")
            onMessage(Res.string.inbox_task_update_failed)
            return
        }
        val date = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val triggerAt = LocalDateTime(date, LocalTime(hour, minute))
            .toInstant(timeZone())
            .toEpochMilliseconds()
        if (triggerAt <= now().toEpochMilliseconds()) {
            // The picker already renders an in-past warning, so this is the last line of defence:
            // AlarmManager fires a past trigger immediately, which reads as a broken reminder.
            logger.warning(tag, "custom due date $triggerAt is in the past — not applied")
            onMessage(Res.string.inbox_reminder_time_in_past)
            return
        }
        draft.update { it.withCustomReminder(triggerAt) }
        _state.update { it.copy(picker = null) }
    }


    private companion object {
        /** Highest repeat interval the config sheet accepts, matching every other host. */
        const val MAX_REPEAT_INTERVAL = 99

        /** Hour the picker opens on when the draft has no time of its own. */
        const val DEFAULT_PICKER_HOUR = 9

        /** Last hour of a day, for clamping a picked time. */
        const val LAST_HOUR = 23
    }
}
