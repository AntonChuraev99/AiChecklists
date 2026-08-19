package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.DateInputMethod
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The three producers of `inbox_quick_added`'s due-date dimensions, plus the value both capture
 * hosts actually write.
 *
 * These four declarations are the entire measuring apparatus for the R1 due rail: `has_due_date`,
 * `date_input_method` and `due_date_offset_days` are what the stage will be judged by, and a rail
 * that works while its numbers are wrong is indistinguishable from a rail that does not work.
 *
 * The clock and the zone are parameters everywhere below on purpose. `Europe/Moscow` rather than
 * `UTC` is doing real work: it is UTC+3 all year, so every case where an instant falls on a
 * different CALENDAR DAY in the two zones is a case where reading the wrong one becomes visible.
 * Under `TimeZone.UTC` those tests would pass against a function that ignores its zone argument.
 */
class DraftDueTest {

    private val tz = TimeZone.of("Europe/Moscow")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(LocalDate(year, month, day), LocalTime(hour, minute)).toInstant(tz)

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        at(year, month, day, hour, minute).toEpochMilliseconds()

    // ── dueDateOffsetDays ───────────────────────────────────────────────────────

    @Test
    fun offset_laterTodayIsZero() {
        assertEquals(
            0,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 19, 23, 0),
                nowMillis = ms(2026, 8, 19, 14, 0),
                timeZone = tz,
            ),
        )
    }

    @Test
    fun offset_tomorrowIsOne() {
        assertEquals(
            1,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 20, 9, 0),
                nowMillis = ms(2026, 8, 19, 8, 0),
                timeZone = tz,
            ),
        )
    }

    /**
     * The case the KDoc is written about, and the one that separates a calendar answer from a
     * duration answer: 22:00 today to 09:00 tomorrow is ELEVEN hours, so `(due - now) / 86_400_000`
     * reports 0 — "captured for today" — for a task the user deliberately pushed to the next day.
     */
    @Test
    fun offset_tomorrowMorningCapturedLateTonight_isOneNotZero() {
        assertEquals(
            1,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 20, 9, 0),
                nowMillis = ms(2026, 8, 19, 22, 0),
                timeZone = tz,
            ),
        )
    }

    /** The same defect at its narrowest: one hour apart, but across midnight. */
    @Test
    fun offset_oneHourApartAcrossMidnight_isOneNotZero() {
        assertEquals(
            1,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 20, 0, 30),
                nowMillis = ms(2026, 8, 19, 23, 30),
                timeZone = tz,
            ),
        )
    }

    /**
     * Counted in the USER's zone, not in UTC.
     *
     * 2026-08-20 01:00 in Moscow is 2026-08-19 22:00 UTC — still "today" to a function that resolves
     * either instant with `TimeZone.UTC`, and "tomorrow" to the user who set it. Every reader east of
     * Greenwich spends part of each day inside this window.
     */
    @Test
    fun offset_isCountedInTheGivenZoneNotUtc() {
        assertEquals(
            1,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 20, 1, 0),
                nowMillis = ms(2026, 8, 19, 10, 0),
                timeZone = tz,
            ),
        )
    }

    @Test
    fun offset_nextWeekIsSeven() {
        assertEquals(
            7,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 26, 9, 0),
                nowMillis = ms(2026, 8, 19, 14, 0),
                timeZone = tz,
            ),
        )
    }

    /**
     * A date already gone reports a NEGATIVE offset rather than clamping to 0.
     *
     * Reachable: a capture can carry a picked time that passed while the dock stayed open, and
     * folding it into 0 would file it in the same bucket as "due today", quietly inflating the one
     * number the rail is meant to move.
     */
    @Test
    fun offset_yesterdayIsMinusOne() {
        assertEquals(
            -1,
            dueDateOffsetDays(
                dueAtMillis = ms(2026, 8, 18, 23, 0),
                nowMillis = ms(2026, 8, 19, 10, 0),
                timeZone = tz,
            ),
        )
    }

    // ── dateInputMethod ─────────────────────────────────────────────────────────

    @Test
    fun method_noDateAtAllIsNone() {
        assertEquals(DateInputMethod.NONE, TaskDraft().dateInputMethod(dueAtMillis = null))
    }

    /**
     * A chip is selected but the send path resolved NO date — still `NONE`.
     *
     * The distinction the whole dimension exists for: `dateInputMethod` reports how a date that was
     * WRITTEN came about, not which control happens to be lit. Reading the draft's chip instead of
     * the resolved value would count dateless captures as `PRESET` and make the rail look like it is
     * working on exactly the tasks it failed to date.
     */
    @Test
    fun method_aStagedChipWithNoResolvedDateIsStillNone() {
        val draft = TaskDraft(
            reminderPreset = ItemCreateReminderPreset.TONIGHT,
            reminderAt = ms(2026, 8, 19, 18, 0),
        )
        assertEquals(DateInputMethod.NONE, draft.dateInputMethod(dueAtMillis = null))
    }

    @Test
    fun method_aPresetChipIsPreset() {
        val due = ms(2026, 8, 19, 18, 0)
        val draft = TaskDraft(reminderPreset = ItemCreateReminderPreset.TONIGHT, reminderAt = due)
        assertEquals(DateInputMethod.PRESET, draft.dateInputMethod(due))
    }

    @Test
    fun method_thePickersOwnResultIsPicker() {
        val due = ms(2026, 8, 21, 15, 45)
        val draft = TaskDraft(reminderPreset = ItemCreateReminderPreset.CUSTOM, reminderAt = due)
        assertEquals(DateInputMethod.PICKER, draft.dateInputMethod(due))
    }

    /** A repeat is configured in the v1 sheet, i.e. down the detour the rail exists to replace. */
    @Test
    fun method_aStagedRepeatIsPicker() {
        val draft = TaskDraft(repeat = PendingRepeatConfig())
        assertEquals(DateInputMethod.PICKER, draft.dateInputMethod(ms(2026, 8, 20, 9, 0)))
    }

    /**
     * Order matters, and this pins it: a repeat outranks a preset chip.
     *
     * Not hypothetical bookkeeping — `withRepeat` clears the preset today, so the pair can only
     * coexist if some future path writes both. Deciding the answer here means such a path reports one
     * method rather than whichever branch happened to be listed first.
     */
    @Test
    fun method_aRepeatOutranksAPresetChip() {
        val draft = TaskDraft(
            reminderPreset = ItemCreateReminderPreset.TONIGHT,
            repeat = PendingRepeatConfig(),
        )
        assertEquals(DateInputMethod.PICKER, draft.dateInputMethod(ms(2026, 8, 20, 9, 0)))
    }

    /**
     * A date with no control behind it came from Smart-Add reading the typed text.
     *
     * Its own value rather than folding into [DateInputMethod.PRESET]: a rise here argues for
     * teaching the parser more phrases, a rise in `PRESET` argues for more chips — opposite backlogs
     * off one number.
     */
    @Test
    fun method_aDateWithNoControlBehindItIsParsedFromText() {
        assertEquals(
            DateInputMethod.PARSED_FROM_TEXT,
            TaskDraft(text = "call mom tomorrow").dateInputMethod(ms(2026, 8, 20, 9, 0)),
        )
    }

    // ── resolveDueOutcome ───────────────────────────────────────────────────────

    @Test
    fun outcome_aDatelessDraftResolvesToNothing() {
        val outcome = TaskDraft(text = "milk").resolveDueOutcome(at(2026, 8, 19, 14, 0), tz)

        assertNull(outcome.reminderAt)
        assertNull(outcome.repeatRule)
        assertNull(outcome.repeatTimeOfDayMinutes)
        assertNull(outcome.repeatFirstTriggerAt)
        assertNull(outcome.dueAtMillis, "No date must stay absent — never a zero the funnel can count")
    }

    /**
     * The one-shot branch is resolved AT SEND, not read off the draft.
     *
     * "Tonight" tapped at 17:50 stored 18:00 today; twenty minutes of typing later that moment has
     * gone, and writing it would hand AlarmManager a past trigger — a reminder that fires the instant
     * the task is created and never again. The stale value must roll to tomorrow evening.
     */
    @Test
    fun outcome_aTonightChipThatWentStaleWhileTypingRollsForward() {
        val draft = TaskDraft(
            text = "call mom",
            reminderPreset = ItemCreateReminderPreset.TONIGHT,
            reminderAt = ms(2026, 8, 19, 18, 0),
        )

        val outcome = draft.resolveDueOutcome(at(2026, 8, 19, 18, 10), tz)

        assertEquals(ms(2026, 8, 20, 18, 0), outcome.reminderAt)
        assertEquals(outcome.reminderAt, outcome.dueAtMillis)
        assertNull(outcome.repeatRule)
    }

    /**
     * A staged repeat produces a rule, a time of day and a FIRST TRIGGER — and no one-shot.
     *
     * 09:30 has already gone at 14:00, so the first occurrence is tomorrow's: arming today's would
     * fire immediately, i.e. a daily reminder that rings once the moment it is created and then goes
     * quiet for the rest of the day.
     */
    @Test
    fun outcome_aStagedRepeatProducesARuleATimeAndTomorrowsFirstTrigger() {
        val draft = TaskDraft(
            text = "vitamins",
            repeat = PendingRepeatConfig(type = RepeatType.DAILY, timeHour = 9, timeMinute = 30),
        )

        val outcome = draft.resolveDueOutcome(at(2026, 8, 19, 14, 0), tz)

        assertNull(outcome.reminderAt, "A repeat and a one-shot are two answers to 'when'")
        assertEquals(9 * 60 + 30, outcome.repeatTimeOfDayMinutes)
        assertEquals(ms(2026, 8, 20, 9, 30), outcome.repeatFirstTriggerAt)
        val rule = assertNotNull(outcome.repeatRule)
        assertEquals(RepeatType.DAILY, rule.type)
        assertEquals(1, rule.interval)
    }

    /** The same rule staged before its time of day: the first occurrence is still today. */
    @Test
    fun outcome_aStagedRepeatWhoseTimeIsStillAheadFiresToday() {
        val draft = TaskDraft(
            text = "vitamins",
            repeat = PendingRepeatConfig(type = RepeatType.DAILY, timeHour = 9, timeMinute = 30),
        )

        val outcome = draft.resolveDueOutcome(at(2026, 8, 19, 7, 0), tz)

        assertEquals(ms(2026, 8, 19, 9, 30), outcome.repeatFirstTriggerAt)
    }

    /**
     * A repeating task HAS a due date — its first occurrence.
     *
     * `dueAtMillis` is what `has_due_date` and `due_date_offset_days` are read from, so reporting
     * only `reminderAt` would file every recurring capture as undated and understate the single
     * metric this whole rail is judged by.
     */
    @Test
    fun outcome_aRepeatingTaskCountsAsDated() {
        val draft = TaskDraft(
            text = "vitamins",
            repeat = PendingRepeatConfig(type = RepeatType.DAILY, timeHour = 9, timeMinute = 30),
        )

        val outcome = draft.resolveDueOutcome(at(2026, 8, 19, 14, 0), tz)

        assertEquals(outcome.repeatFirstTriggerAt, outcome.dueAtMillis)
        assertEquals(
            1,
            dueDateOffsetDays(
                dueAtMillis = assertNotNull(outcome.dueAtMillis),
                nowMillis = ms(2026, 8, 19, 14, 0),
                timeZone = tz,
            ),
        )
    }
}
