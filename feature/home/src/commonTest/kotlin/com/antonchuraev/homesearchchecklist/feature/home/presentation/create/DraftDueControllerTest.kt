package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_reminder_time_in_past
import aichecklists.core.designsystem.generated.resources.inbox_task_update_failed
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetId
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.StringResource

/**
 * The rules behind the capture dock's due rail — the delegate BOTH v2 capture hosts forward to.
 *
 * Everything here is host-independent by construction, which is the point of the delegate existing:
 * the Inbox tab and the Calendar tab already drifted once over "what can I attach to a task", and a
 * rule proved on one host says nothing about the other. That the hosts are actually wired to this
 * object is a separate claim, proved separately in `TodayViewModelTest`.
 *
 * ## Clock and zone
 * Both are injected. `Europe/Moscow` (UTC+3, no DST) rather than `UTC`: the picker floor is computed
 * as UTC midnight of the LOCAL date, and under `TimeZone.UTC` a mix-up between the two is invisible.
 * The fixed "now" is 2026-08-19 14:00 local unless a test says otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftDueControllerTest {

    private val tz = TimeZone.of("Europe/Moscow")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(LocalDate(year, month, day), LocalTime(hour, minute)).toInstant(tz)

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        at(year, month, day, hour, minute).toEpochMilliseconds()

    /** UTC midnight of a calendar day — what the Material date picker speaks in. */
    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        LocalDateTime(LocalDate(year, month, day), LocalTime(0, 0))
            .toInstant(TimeZone.UTC).toEpochMilliseconds()

    private val defaultNow = at(2026, 8, 19, 14, 0)

    // ── Fixture ─────────────────────────────────────────────────────────────────

    /**
     * Free-tier limits with an explicitly chosen recurring ceiling.
     *
     * The ceiling is a required argument rather than a default: every gate test below has to name the
     * number it is standing against, because the only thing that distinguishes an honest read of
     * `UserLimits` from a constant beside the gate is that the boundary MOVES when this value does.
     */
    private fun freeLimits(maxRecurringReminders: Int) = UserLimits(
        maxChecklists = 5,
        maxFillsPerChecklist = 5,
        currentChecklistCount = 0,
        isPremium = false,
        maxRecurringReminders = maxRecurringReminders,
    )

    private fun premiumLimits(maxRecurringReminders: Int = 1) = UserLimits(
        maxChecklists = 5,
        maxFillsPerChecklist = 5,
        currentChecklistCount = 0,
        isPremium = true,
        maxRecurringReminders = maxRecurringReminders,
    )

    private class RecordingLogger : AppLogger {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<Pair<String, Throwable?>>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) { warnings += message }
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors += message to throwable
        }
    }

    private class Fixture(
        scope: CoroutineScope,
        initialDraft: TaskDraft,
        limits: () -> UserLimits?,
        countActiveReminders: suspend () -> Int,
        now: () -> Instant,
        timeZone: () -> TimeZone,
    ) {
        val draft = MutableStateFlow(initialDraft)
        val logger = RecordingLogger()
        val messages = mutableListOf<StringResource>()
        var upgradeClicks = 0

        val controller = DraftDueController(
            scope = scope,
            logger = logger,
            tag = "DraftDueControllerTest",
            draft = draft,
            limits = limits,
            countActiveReminders = countActiveReminders,
            onUpgradeClick = { upgradeClicks++ },
            onMessage = { messages += it },
            now = now,
            timeZone = timeZone,
        )

        val state get() = controller.state.value
        fun send(intent: DraftDueIntent) = controller.onIntent(intent)
    }

    /**
     * Builds the delegate over the test's own scope.
     *
     * The scope is the `TestScope`, and the one suspending path ([DraftDueIntent.OnRepeatClick])
     * therefore does not run until the test drains the scheduler — deliberately explicit rather than
     * unconfined, so a test that forgets to drain fails loudly instead of passing on a state that
     * happened to be produced eagerly.
     */
    private fun TestScope.fixture(
        draft: TaskDraft = TaskDraft(text = "milk"),
        limits: () -> UserLimits? = { freeLimits(maxRecurringReminders = 2) },
        countActiveReminders: suspend () -> Int = { 0 },
        now: Instant = defaultNow,
        timeZone: TimeZone = tz,
    ) = Fixture(
        scope = this,
        initialDraft = draft,
        limits = limits,
        countActiveReminders = countActiveReminders,
        now = { now },
        timeZone = { timeZone },
    )

    // ── 1. The repeat paywall gate ──────────────────────────────────────────────

    /**
     * At the served ceiling the REPEAT tab opens LOCKED, and with no repeat editor behind it.
     *
     * This is the only paywall the rail can reach at all: a due DATE is free, so presets, the grid and
     * the picker never gate. Only the recurring NOTIFICATION is capped.
     */
    @Test
    fun repeatGate_atTheCeiling_opensLocked() = runTest {
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 2) },
            countActiveReminders = { 2 },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        val sheet = assertNotNull(f.state.sheet, "Repeat must open the sheet, locked or not")
        assertEquals(ReminderTab.REPEAT, sheet.tab)
        assertTrue(sheet.locked, "2 armed reminders against a ceiling of 2 is AT the limit")
        assertNull(sheet.repeatConfig, "A locked sheet shows the banner, not an editor")
    }

    @Test
    fun repeatGate_oneBelowTheCeiling_opensUnlockedWithAnEditor() = runTest {
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 2) },
            countActiveReminders = { 1 },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        val sheet = assertNotNull(f.state.sheet)
        assertFalse(sheet.locked, "1 armed reminder against a ceiling of 2 is BELOW the limit")
        assertNotNull(sheet.repeatConfig, "An unlocked repeat tab must arrive with an editable rule")
    }

    /**
     * The same user with the same two armed reminders, under a LOOSER ceiling — now unlocked.
     *
     * Paired with [repeatGate_atTheCeiling_opensLocked] this is the discriminator that makes the whole
     * group worth having: a gate reading a constant `2` cannot pass both, and neither can one reading
     * a constant `3`. The number has to come from `UserLimits`, which is where Remote Config puts it.
     * The project has already shipped this exact drift once — `ToolCallDispatcherImpl`'s
     * `FREE_ATTACH_LIMIT_PER_ITEM` still hardcodes what Remote Config serves.
     */
    @Test
    fun repeatGate_sameCountLooserCeiling_opensUnlocked() = runTest {
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 3) },
            countActiveReminders = { 2 },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        assertFalse(assertNotNull(f.state.sheet).locked)
    }

    /** …and the mirror image: one armed reminder locks once the served ceiling is 1. */
    @Test
    fun repeatGate_sameCountStricterCeiling_opensLocked() = runTest {
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 1) },
            countActiveReminders = { 1 },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        assertTrue(assertNotNull(f.state.sheet).locked)
    }

    /** Premium is never gated, however many reminders are armed and whatever the free ceiling is. */
    @Test
    fun repeatGate_premiumFarPastTheCeiling_opensUnlocked() = runTest {
        val f = fixture(
            limits = { premiumLimits(maxRecurringReminders = 1) },
            countActiveReminders = { 99 },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        val sheet = assertNotNull(f.state.sheet)
        assertFalse(sheet.locked, "A paying user must never see the upgrade banner")
        assertNotNull(sheet.repeatConfig)
    }

    /**
     * Re-opening a rule the user already configured is EDITING, not a new reminder — never an upsell.
     *
     * Without this branch a free user at the ceiling could stage a repeat and then be unable to change
     * or remove it: every re-open would be a paywall, on a rule they set a moment earlier.
     */
    @Test
    fun repeatGate_aStagedRuleReopensUnlockedEvenAtTheCeiling() = runTest {
        val staged = PendingRepeatConfig(type = RepeatType.WEEKLY, interval = 2, timeHour = 8)
        val f = fixture(
            draft = TaskDraft(text = "gym", repeat = staged),
            limits = { freeLimits(maxRecurringReminders = 1) },
            countActiveReminders = { 5 },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        val sheet = assertNotNull(f.state.sheet)
        assertFalse(sheet.locked, "Editing an already-staged rule must not turn into an upsell")
        assertEquals(staged, sheet.repeatConfig, "The editor must open on the rule the user set")
    }

    /**
     * Limits not loaded yet — open UNLOCKED.
     *
     * `null` is the state of the flow for the first frames after the ViewModel is built, and the dock
     * is reachable in exactly that window. Treating "not yet known" as "free and at the limit" would
     * show the upgrade banner to a paying user who simply opened the app quickly.
     */
    @Test
    fun repeatGate_limitsNotLoadedYet_opensUnlocked() = runTest {
        val f = fixture(limits = { null }, countActiveReminders = { 99 })

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        val sheet = assertNotNull(f.state.sheet)
        assertFalse(sheet.locked, "An unknown entitlement must never lock paid-for behaviour")
        assertNotNull(sheet.repeatConfig)
    }

    /** The locked banner's CTA closes the sheet and hands navigation back to the host. */
    @Test
    fun repeatGate_upgradeFromTheLockedBanner_closesTheSheetAndCallsTheHost() = runTest {
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 1) },
            countActiveReminders = { 1 },
        )
        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()
        assertTrue(assertNotNull(f.state.sheet).locked)

        f.send(DraftDueIntent.OnSheetUpgradeClick)

        assertNull(f.state.sheet)
        assertEquals(1, f.upgradeClicks)
    }

    // ── 2. Fail-open when the reminder count cannot be read ─────────────────────

    /**
     * A read failure opens the sheet UNLOCKED and says so in the log.
     *
     * Revenue-relevant and, until now, executed by nothing: the alternative — locking on an error —
     * shows a paying user an upgrade banner because a database call failed, and does it silently. The
     * logged throwable is the other half; a wrongly-locked sheet with no line in the log is a report
     * nobody can act on.
     */
    @Test
    fun repeatGate_whenTheReminderCountThrows_opensUnlockedAndLogsTheFailure() = runTest {
        val boom = IllegalStateException("database unavailable")
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 1) },
            countActiveReminders = { throw boom },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        val sheet = assertNotNull(f.state.sheet, "A read error must not swallow the sheet")
        assertFalse(sheet.locked, "Fail OPEN: never lock paid behaviour behind a read error")
        assertNotNull(sheet.repeatConfig)
        assertEquals(1, f.logger.errors.size, "The reason the gate could not be evaluated must be logged")
        assertSame(boom, f.logger.errors.single().second)
    }

    /**
     * Cancellation is not a read failure and must not be dressed up as one.
     *
     * `runCatching` catches `CancellationException` too, so without the explicit re-throw a screen
     * torn down mid-gate would log a spurious error and push a sheet onto a dead state.
     */
    @Test
    fun repeatGate_whenTheReminderCountIsCancelled_opensNothingAndLogsNothing() = runTest {
        val f = fixture(
            limits = { freeLimits(maxRecurringReminders = 1) },
            countActiveReminders = { throw CancellationException("screen left") },
        )

        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        assertNull(f.state.sheet, "A cancelled gate must not push a sheet")
        assertTrue(f.logger.errors.isEmpty(), "Cancellation is not a failure to report")
    }

    // ── 3. "In the past" guards ─────────────────────────────────────────────────

    /**
     * A sheet preset that has gone stale is refused, with a message, and the draft is left alone.
     *
     * Reachable by standing still: the presets are resolved when the sheet OPENS, and AlarmManager
     * fires a past trigger immediately — a reminder that "rings" the instant it is set.
     */
    @Test
    fun pastGuard_aSheetPresetInThePastIsRefused() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        val before = f.draft.value

        f.send(DraftDueIntent.OnSheetPresetSelected(ms(2026, 8, 19, 13, 59)))

        assertEquals(before, f.draft.value, "A refused preset must not touch the draft")
        assertEquals(listOf(Res.string.inbox_reminder_time_in_past), f.messages)
        assertNotNull(f.state.sheet, "A refusal keeps the sheet up so the user can pick again")
    }

    /**
     * The boundary is strict: EXACTLY now is already past.
     *
     * Paired with [pastGuard_aSheetPresetOneMillisecondAheadIsApplied] this pins `<=` rather than `<`.
     * A trigger equal to now is one the alarm fires on delivery, which is the very defect the guard
     * exists to stop, so the inclusive side is the correct one and worth nailing down.
     */
    @Test
    fun pastGuard_aSheetPresetAtExactlyNowCountsAsPast() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        val before = f.draft.value

        f.send(DraftDueIntent.OnSheetPresetSelected(defaultNow.toEpochMilliseconds()))

        assertEquals(before, f.draft.value)
        assertEquals(listOf(Res.string.inbox_reminder_time_in_past), f.messages)
    }

    /** One millisecond the other side of the same boundary is accepted and closes the sheet. */
    @Test
    fun pastGuard_aSheetPresetOneMillisecondAheadIsApplied() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        val accepted = defaultNow.toEpochMilliseconds() + 1

        f.send(DraftDueIntent.OnSheetPresetSelected(accepted))

        assertEquals(accepted, f.draft.value.reminderAt)
        assertEquals(ItemCreateReminderPreset.CUSTOM, f.draft.value.reminderPreset)
        assertTrue(f.messages.isEmpty(), "An accepted preset must not warn about the past")
        assertNull(f.state.sheet, "Accepting a preset puts the sheet away")
    }

    /**
     * The picker's own result gets the same guard — it is the last line of defence.
     *
     * The picker renders an in-past hint, but the hint is advisory: the user can ignore it, and the
     * time can also go stale between the hint and the confirm.
     */
    @Test
    fun pastGuard_aPickedTimeEarlierTodayIsRefused() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        f.send(DraftDueIntent.OnPickerDateSelected(utcMidnight(2026, 8, 19)))
        val before = f.draft.value

        f.send(DraftDueIntent.OnPickerTimeSelected(hour = 9, minute = 0))

        assertEquals(before, f.draft.value, "A refused time must not touch the draft")
        assertEquals(listOf(Res.string.inbox_reminder_time_in_past), f.messages)
        assertNotNull(f.state.picker, "The picker stays up so the user can choose again")
    }

    /** Same strict boundary on the picker: today at exactly the current wall-clock minute is past. */
    @Test
    fun pastGuard_aPickedTimeAtExactlyNowCountsAsPast() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        f.send(DraftDueIntent.OnPickerDateSelected(utcMidnight(2026, 8, 19)))
        val before = f.draft.value

        // defaultNow is 14:00:00.000 local, so this resolves to exactly `now`.
        f.send(DraftDueIntent.OnPickerTimeSelected(hour = 14, minute = 0))

        assertEquals(before, f.draft.value)
        assertEquals(listOf(Res.string.inbox_reminder_time_in_past), f.messages)
        assertNotNull(f.state.picker)
    }

    /** One minute later is accepted, written in the LOCAL zone, and closes the picker. */
    @Test
    fun pastGuard_aPickedTimeOneMinuteAheadIsApplied() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        f.send(DraftDueIntent.OnPickerDateSelected(utcMidnight(2026, 8, 19)))

        f.send(DraftDueIntent.OnPickerTimeSelected(hour = 14, minute = 1))

        assertEquals(ms(2026, 8, 19, 14, 1), f.draft.value.reminderAt)
        assertEquals(ItemCreateReminderPreset.CUSTOM, f.draft.value.reminderPreset)
        assertTrue(f.messages.isEmpty())
        assertNull(f.state.picker)
    }

    /**
     * The picker floor is UTC midnight of the LOCAL date — 01:00 in Moscow is still the previous day
     * in UTC, and taking the UTC date would put yesterday back on the calendar.
     */
    @Test
    fun picker_theFloorIsUtcMidnightOfTheLocalDate() = runTest {
        val f = fixture(now = at(2026, 8, 19, 1, 0))
        f.send(DraftDueIntent.OnPickDateClick)

        f.send(DraftDueIntent.OnSheetCustomDateRequested)

        val picker = assertNotNull(f.state.picker)
        assertEquals(utcMidnight(2026, 8, 19), picker.minDateMillis)
        assertNull(f.state.sheet, "Two live modal sheets fight over the scrim — the sheet goes away")
    }

    /** The in-past hint tracks the time as it is scrolled, without committing anything. */
    @Test
    fun picker_theInPastHintFollowsTheScrolledTime() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        f.send(DraftDueIntent.OnPickerDateSelected(utcMidnight(2026, 8, 19)))

        f.send(DraftDueIntent.OnPickerTimeChanged(hour = 9, minute = 0))
        assertTrue(assertNotNull(f.state.picker).timeInPast)

        f.send(DraftDueIntent.OnPickerTimeChanged(hour = 20, minute = 0))
        assertFalse(assertNotNull(f.state.picker).timeInPast)

        assertNull(f.draft.value.reminderAt, "Scrolling a time commits nothing")
    }

    /** A LATER day is never in the past, whatever hour is scrolled to. */
    @Test
    fun picker_anEarlyHourOnALaterDayIsNotInThePast() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        f.send(DraftDueIntent.OnPickerDateSelected(utcMidnight(2026, 8, 20)))

        f.send(DraftDueIntent.OnPickerTimeChanged(hour = 1, minute = 0))

        assertFalse(assertNotNull(f.state.picker).timeInPast)
    }

    // ── 4. A repeat and a one-shot are mutually exclusive ───────────────────────

    /** Saving a repeat supersedes the one-shot the rail was showing. */
    @Test
    fun exclusion_savingARepeatClearsAStagedOneShot() = runTest {
        val f = fixture(
            draft = TaskDraft(
                text = "gym",
                reminderPreset = ItemCreateReminderPreset.TONIGHT,
                reminderAt = ms(2026, 8, 19, 18, 0),
            ),
            countActiveReminders = { 0 },
        )
        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()
        f.send(DraftDueIntent.OnRepeatTypeSelected(RepeatType.WEEKLY))

        f.send(DraftDueIntent.OnRepeatSave)

        val draft = f.draft.value
        assertNotNull(draft.repeat, "The saved rule must land on the draft")
        assertEquals(RepeatType.WEEKLY, draft.repeat?.type)
        assertNull(draft.reminderAt, "The lead chip shows ONE answer to 'when'")
        assertNull(draft.reminderPreset)
        assertNull(f.state.sheet, "Saving puts the sheet away")
    }

    /** …and a preset tapped afterwards supersedes the repeat, in the other direction. */
    @Test
    fun exclusion_tappingAPresetClearsAStagedRepeat() = runTest {
        val f = fixture(
            draft = TaskDraft(text = "gym", repeat = PendingRepeatConfig(type = RepeatType.DAILY)),
        )

        f.send(DraftDueIntent.OnPresetClick(DuePresetId.TOMORROW))

        val draft = f.draft.value
        assertNull(draft.repeat, "A one-shot preset and a recurring rule cannot both be staged")
        assertEquals(ItemCreateReminderPreset.TOMORROW_MORNING, draft.reminderPreset)
        assertEquals(ms(2026, 8, 20, 9, 0), draft.reminderAt)
    }

    /** The picker's result clears a staged repeat too — same rule, third entrance. */
    @Test
    fun exclusion_aPickedTimeClearsAStagedRepeat() = runTest {
        val f = fixture(
            draft = TaskDraft(text = "gym", repeat = PendingRepeatConfig(type = RepeatType.DAILY)),
        )
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        f.send(DraftDueIntent.OnPickerDateSelected(utcMidnight(2026, 8, 21)))

        f.send(DraftDueIntent.OnPickerTimeSelected(hour = 10, minute = 0))

        assertNull(f.draft.value.repeat)
        assertEquals(ms(2026, 8, 21, 10, 0), f.draft.value.reminderAt)
    }

    /** Removing the repeat leaves the draft with no date at all rather than resurrecting the old one. */
    @Test
    fun exclusion_removingTheRepeatLeavesNoDate() = runTest {
        val f = fixture(draft = TaskDraft(text = "gym", repeat = PendingRepeatConfig()))
        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()

        f.send(DraftDueIntent.OnRepeatRemove)

        assertNull(f.draft.value.repeat)
        assertNull(f.draft.value.reminderAt)
        assertNull(f.state.sheet)
    }

    /**
     * Save with nothing staged is a contract break, not a user mistake: the REPEAT tab seeds a config
     * on open, so this cannot be reached through the UI. It is reported rather than quietly dropped.
     */
    @Test
    fun exclusion_savingWithNoStagedConfigReportsFailureAndLeavesTheDraftAlone() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick) // ONCE tab: no repeat config is seeded
        val before = f.draft.value

        f.send(DraftDueIntent.OnRepeatSave)

        assertEquals(before, f.draft.value)
        assertEquals(listOf(Res.string.inbox_task_update_failed), f.messages)
        assertEquals(1, f.logger.warnings.size)
    }

    // ── 5. reset() ──────────────────────────────────────────────────────────────

    /**
     * After a send, everything the dock had open goes away.
     *
     * The dock stays mounted for the NEXT capture, so a planner left expanded would sit over a fresh
     * draft claiming a date the new task does not have — and would keep the AI source row hidden
     * behind it the whole time.
     */
    @Test
    fun reset_putsTheWholeRailBackToItsInitialState() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnLeadClick)
        f.send(DraftDueIntent.OnPickDateClick)
        f.send(DraftDueIntent.OnSheetCustomDateRequested)
        // Everything is open before the reset — otherwise the assertions below are vacuous.
        assertTrue(f.state.plannerExpanded)
        assertNotNull(f.state.picker)

        f.controller.reset()

        assertEquals(DraftDueUiState(), f.state)
    }

    /**
     * A reset with a sheet up clears that too — the sheet is a separate field, so a reset that only
     * collapsed the planner would leave a modal floating over the next capture.
     */
    @Test
    fun reset_alsoDropsAnOpenSheet() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnLeadClick)
        f.send(DraftDueIntent.OnPickDateClick)
        assertNotNull(f.state.sheet)

        f.controller.reset()

        assertNull(f.state.sheet)
        assertFalse(f.state.plannerExpanded)
    }

    // ── 6. The rail itself ──────────────────────────────────────────────────────

    @Test
    fun rail_theLeadChipTogglesThePlanner() = runTest {
        val f = fixture()

        f.send(DraftDueIntent.OnLeadClick)
        assertTrue(f.state.plannerExpanded)

        f.send(DraftDueIntent.OnLeadClick)
        assertFalse(f.state.plannerExpanded, "The lead chip toggles; it does not only open")
    }

    @Test
    fun rail_doneCollapsesThePlanner() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnLeadClick)

        f.send(DraftDueIntent.OnPlannerCollapse)

        assertFalse(f.state.plannerExpanded)
    }

    /**
     * Clear drops the parsed token as well as the reminder.
     *
     * The token is the SECOND thing the lead chip can be showing (Smart-Add read a date out of the
     * typed text), so clearing only the reminder would leave the chip lit and make the `x` look like
     * it did nothing.
     */
    @Test
    fun rail_clearDropsTheReminderAndTheParsedTokenAndKeepsTheText() = runTest {
        val f = fixture(
            draft = TaskDraft(
                text = "call mom tomorrow",
                reminderPreset = ItemCreateReminderPreset.TOMORROW_MORNING,
                reminderAt = ms(2026, 8, 20, 9, 0),
            ),
        )

        f.send(DraftDueIntent.OnClearDate)

        val draft = f.draft.value
        assertNull(draft.reminderAt)
        assertNull(draft.reminderPreset)
        assertNull(draft.parsedToken)
        assertEquals("call mom tomorrow", draft.text, "Clearing a date must not clear the text")
    }

    /** Re-tapping the active preset clears it — otherwise a mis-tap could only be undone after send. */
    @Test
    fun rail_reTappingTheActivePresetClearsTheDate() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPresetClick(DuePresetId.TOMORROW))
        assertEquals(ItemCreateReminderPreset.TOMORROW_MORNING, f.draft.value.reminderPreset)

        f.send(DraftDueIntent.OnPresetClick(DuePresetId.TOMORROW))

        assertNull(f.draft.value.reminderPreset)
        assertNull(f.draft.value.reminderAt)
    }

    /** Tapping a different preset replaces rather than accumulates — the presets are single-select. */
    @Test
    fun rail_tappingAnotherPresetReplacesTheFirst() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPresetClick(DuePresetId.TOMORROW))

        f.send(DraftDueIntent.OnPresetClick(DuePresetId.TONIGHT))

        assertEquals(ItemCreateReminderPreset.TONIGHT, f.draft.value.reminderPreset)
        assertEquals(ms(2026, 8, 19, 18, 0), f.draft.value.reminderAt)
    }

    /** "Pick date" and "Time" are two labels for one destination: the v1 sheet's ONCE tab. */
    @Test
    fun rail_pickDateAndTimeBothOpenTheOnceTab() = runTest {
        val f = fixture()

        f.send(DraftDueIntent.OnPickDateClick)
        assertEquals(ReminderTab.ONCE, assertNotNull(f.state.sheet).tab)

        f.send(DraftDueIntent.OnSheetDismiss)
        f.send(DraftDueIntent.OnTimeClick)
        assertEquals(ReminderTab.ONCE, assertNotNull(f.state.sheet).tab)
    }

    /** Removing the reminder from the ONCE tab clears the date and closes the sheet. */
    @Test
    fun rail_removingTheReminderFromTheSheetClearsTheDate() = runTest {
        val f = fixture(
            draft = TaskDraft(
                text = "milk",
                reminderPreset = ItemCreateReminderPreset.TONIGHT,
                reminderAt = ms(2026, 8, 19, 18, 0),
            ),
        )
        f.send(DraftDueIntent.OnPickDateClick)

        f.send(DraftDueIntent.OnSheetRemoveReminder)

        assertNull(f.draft.value.reminderAt)
        assertNull(f.draft.value.reminderPreset)
        assertNull(f.state.sheet)
    }

    /**
     * Switching to the REPEAT tab from inside an already-open sheet seeds an editor without going
     * through the gate — the gate is evaluated once, when Repeat is entered from the planner.
     */
    @Test
    fun rail_switchingToTheRepeatTabSeedsAnEditorAndKeepsEditsAcrossTabs() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnPickDateClick)

        f.send(DraftDueIntent.OnSheetTabSelected(ReminderTab.REPEAT))
        f.send(DraftDueIntent.OnRepeatIntervalChanged(4))
        f.send(DraftDueIntent.OnSheetTabSelected(ReminderTab.ONCE))
        f.send(DraftDueIntent.OnSheetTabSelected(ReminderTab.REPEAT))

        assertEquals(4, assertNotNull(assertNotNull(f.state.sheet).repeatConfig).interval)
    }

    /** A type switch drops the interval and weekdays that belonged to the previous type. */
    @Test
    fun rail_switchingRepeatTypeResetsTheFieldsThatBelongedToTheOldOne() = runTest {
        val f = fixture()
        f.send(DraftDueIntent.OnRepeatClick)
        testScheduler.advanceUntilIdle()
        f.send(DraftDueIntent.OnSheetTabSelected(ReminderTab.REPEAT))
        f.send(DraftDueIntent.OnRepeatIntervalChanged(4))
        f.send(DraftDueIntent.OnWeekDayToggled(2))

        f.send(DraftDueIntent.OnRepeatTypeSelected(RepeatType.MONTHLY))

        val config = assertNotNull(assertNotNull(f.state.sheet).repeatConfig)
        assertEquals(RepeatType.MONTHLY, config.type)
        assertEquals(1, config.interval)
        assertTrue(config.weekDays.isEmpty())
    }
}
