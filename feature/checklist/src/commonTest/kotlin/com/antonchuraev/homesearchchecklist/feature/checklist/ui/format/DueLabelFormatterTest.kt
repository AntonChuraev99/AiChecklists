package com.antonchuraev.homesearchchecklist.feature.checklist.ui.format

import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiScheduleState
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Branch coverage for [resolveDueLabel] — the pure half of the due-label formatter.
 *
 * The formatter is split in two on purpose: this function decides **which** label a task gets and
 * **which colour state** goes with it, using nothing but the domain fields and a clock, while the
 * `@Composable` layer above it only turns the resulting [DueLabelForm] into a localized string. That
 * split is what makes every branch below testable without a Compose runtime, and it is also the
 * answer to "`stringResource` only works in a `@Composable`": no string is produced here at all.
 *
 * Fixture clock: **Tuesday 2026-05-05 12:00 UTC**. The timezone is passed explicitly so the test
 * does not silently change meaning on a machine in another zone.
 */
class DueLabelFormatterTest {

    private val zone = TimeZone.UTC

    /** Tuesday 2026-05-05, 12:00. */
    private val now = utc(2026, 5, 5, 12, 0)

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.UTC).toEpochMilliseconds()

    private fun oneShot(
        at: Long?,
        style: DueLabelStyle = DueLabelStyle.Relative,
    ): DueLabelSpec? = resolveDueLabel(
        reminderAt = at,
        repeatRule = null,
        nowMillis = now,
        style = style,
        timeZone = zone,
    )

    private fun repeating(
        rule: ReminderRepeatRule,
        timeOfDayMinutes: Int? = 540,
        nextAt: Long? = null,
        style: DueLabelStyle = DueLabelStyle.Relative,
    ): DueLabelSpec? = resolveDueLabel(
        reminderAt = null,
        repeatRule = rule,
        repeatTimeOfDayMinutes = timeOfDayMinutes,
        repeatNextAt = nextAt,
        nowMillis = now,
        style = style,
        timeZone = zone,
    )

    // ── No date at all: no chip ──

    @Test
    fun noReminderAndNoRepeat_resolvesToNull() {
        // Not a "Someday" chip and not an empty label: the spec's cheapest neutral state is zero
        // pixels, so the call site renders nothing at all.
        assertNull(oneShot(at = null))
    }

    // ── Someday: the parked state ──

    @Test
    fun somedaySpec_carriesTheSomedayStateAndForm() {
        // No domain field produces this yet (parking a task arrives with the sources model), so the
        // spec is exposed as a constant rather than invented out of an unrelated field. The chip and
        // its dashed outline are already wired for it.
        assertEquals(GistiScheduleState.Someday, DueLabelSpec.Someday.state)
        assertEquals(DueLabelForm.Someday, DueLabelSpec.Someday.form)
    }

    // ── One-shot, relative vocabulary ──

    @Test
    fun laterToday_saysTodayWithTheTime() {
        val spec = oneShot(utc(2026, 5, 5, 18, 0))

        assertEquals(DueLabelForm.Today("18:00"), spec?.form)
        assertEquals(GistiScheduleState.Active, spec?.state)
    }

    @Test
    fun earlierToday_saysOverdueWithTheTime() {
        val spec = oneShot(utc(2026, 5, 5, 9, 5))

        assertEquals(DueLabelForm.OverdueTime("09:05"), spec?.form)
        assertEquals(GistiScheduleState.Overdue, spec?.state)
    }

    @Test
    fun tomorrow_saysTomorrowWithTheTime() {
        val spec = oneShot(utc(2026, 5, 6, 9, 0))

        assertEquals(DueLabelForm.Tomorrow("09:00"), spec?.form)
        assertEquals(
            GistiScheduleState.Active,
            spec?.state,
            "today AND tomorrow are the accent state — that is what 'Active' means",
        )
    }

    @Test
    fun laterThisWeek_saysWeekdayAndTime() {
        // Friday 2026-05-08 — three days out, still nameable by weekday.
        val spec = oneShot(utc(2026, 5, 8, 18, 0))

        assertEquals(DueLabelForm.ThisWeek(isoDayOfWeek = 5, time = "18:00"), spec?.form)
        assertEquals(GistiScheduleState.Later, spec?.state)
    }

    @Test
    fun sixDaysOut_isStillAWeekday() {
        // Monday 2026-05-11 — the last day a weekday name is unambiguous.
        assertEquals(
            DueLabelForm.ThisWeek(isoDayOfWeek = 1, time = "08:30"),
            oneShot(utc(2026, 5, 11, 8, 30))?.form,
        )
    }

    @Test
    fun sevenDaysOut_switchesToADate() {
        // Tuesday 2026-05-12 — a weekday name here would collide with today's own name.
        assertEquals(
            DueLabelForm.Date(monthNumber = 5, dayOfMonth = 12),
            oneShot(utc(2026, 5, 12, 8, 30))?.form,
        )
    }

    @Test
    fun farFuture_saysMonthAndDayWithoutATime() {
        val spec = oneShot(utc(2026, 9, 14, 18, 0))

        assertEquals(DueLabelForm.Date(monthNumber = 9, dayOfMonth = 14), spec?.form)
        assertEquals(GistiScheduleState.Later, spec?.state)
    }

    @Test
    fun overdueOnAnEarlierDay_saysOverdueWithTheDate() {
        val spec = oneShot(utc(2026, 5, 1, 9, 0))

        assertEquals(DueLabelForm.OverdueDate(monthNumber = 5, dayOfMonth = 1), spec?.form)
        assertEquals(GistiScheduleState.Overdue, spec?.state)
    }

    @Test
    fun midnightDueTime_keepsTheLeadingZeroes() {
        // Zero padding is not cosmetic here: the chip renders with tabular figures so that a
        // ticking clock does not resize it, and "0:00" would be one glyph narrower than "09:00".
        assertEquals(DueLabelForm.OverdueTime("00:00"), oneShot(utc(2026, 5, 5, 0, 0))?.form)
        assertEquals(DueLabelForm.Tomorrow("00:05"), oneShot(utc(2026, 5, 6, 0, 5))?.form)
    }

    // ── Repeats ──

    @Test
    fun dailyRepeat_saysDailyAndTheTimeOfDay() {
        val spec = repeating(ReminderRepeatRule(type = RepeatType.DAILY))

        assertEquals(DueLabelForm.RepeatDaily("09:00"), spec?.form)
    }

    @Test
    fun weeklyRepeatWithDays_listsTheDaysInIsoOrder() {
        val spec = repeating(
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = setOf(3, 1)),
            timeOfDayMinutes = 18 * 60,
        )

        assertEquals(DueLabelForm.RepeatWeekly(listOf(1, 3), "18:00"), spec?.form)
    }

    @Test
    fun weeklyRepeatWithoutDays_fallsBackToTheRuleName() {
        // "Weekly 18:00" — with no explicit day selection there is nothing to list, and an empty
        // day slot would render as a stray leading space.
        val spec = repeating(
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = null),
            timeOfDayMinutes = 18 * 60,
        )

        assertEquals(DueLabelForm.RepeatWeeklyAny("18:00"), spec?.form)
    }

    @Test
    fun monthlyAndYearlyRepeats_keepTheirOwnWords() {
        assertEquals(
            DueLabelForm.RepeatMonthly("09:00"),
            repeating(ReminderRepeatRule(type = RepeatType.MONTHLY))?.form,
        )
        assertEquals(
            DueLabelForm.RepeatYearly("09:00"),
            repeating(ReminderRepeatRule(type = RepeatType.YEARLY))?.form,
        )
    }

    @Test
    fun repeatWithoutTimeOfDay_readsAsMidnight() {
        assertEquals(
            DueLabelForm.RepeatDaily("00:00"),
            repeating(ReminderRepeatRule(type = RepeatType.DAILY), timeOfDayMinutes = null)?.form,
        )
    }

    @Test
    fun repeatState_comesFromTheNextOccurrence() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY)

        assertEquals(
            GistiScheduleState.Active,
            repeating(rule, nextAt = utc(2026, 5, 5, 18, 0))?.state,
            "a repeat firing today is as urgent as a one-shot firing today",
        )
        assertEquals(
            GistiScheduleState.Later,
            repeating(rule, nextAt = utc(2026, 6, 1, 9, 0))?.state,
        )
        assertEquals(
            GistiScheduleState.Overdue,
            repeating(rule, nextAt = utc(2026, 5, 4, 9, 0))?.state,
        )
    }

    @Test
    fun repeatWithoutANextOccurrence_staysNeutral() {
        // An exhausted or not-yet-computed schedule must not be painted as overdue: nothing has
        // been missed, the series simply has no next date.
        assertEquals(
            GistiScheduleState.Later,
            repeating(ReminderRepeatRule(type = RepeatType.DAILY), nextAt = null)?.state,
        )
    }

    @Test
    fun repeatOutranksAOneShotTimestamp() {
        // Mirrors the label the checklist detail screen has always shown: when both fields are set,
        // the recurring rule is the one that describes the task.
        val spec = resolveDueLabel(
            reminderAt = utc(2026, 5, 5, 18, 0),
            repeatRule = ReminderRepeatRule(type = RepeatType.DAILY),
            repeatTimeOfDayMinutes = 540,
            repeatNextAt = null,
            nowMillis = now,
            timeZone = zone,
        )

        assertEquals(DueLabelForm.RepeatDaily("09:00"), spec?.form)
    }

    // ── Absolute style: the vocabulary the checklist detail screen already shows ──

    @Test
    fun absoluteStyle_futureOneShot_saysMonthDayAndTime() {
        val spec = oneShot(utc(2026, 5, 5, 18, 0), style = DueLabelStyle.Absolute)

        assertEquals(
            DueLabelForm.DateTime(monthNumber = 5, dayOfMonth = 5, time = "18:00"),
            spec?.form,
            "the detail screen has always shown an absolute date here; only its localization changes",
        )
        assertEquals(GistiScheduleState.Active, spec?.state)
    }

    @Test
    fun absoluteStyle_pastOneShot_saysMissedWithTheDateOnly() {
        val spec = oneShot(utc(2026, 5, 1, 9, 0), style = DueLabelStyle.Absolute)

        assertEquals(DueLabelForm.Missed(monthNumber = 5, dayOfMonth = 1), spec?.form)
        assertEquals(GistiScheduleState.Overdue, spec?.state)
    }

    @Test
    fun absoluteStyle_missedEarlierToday_alsoDropsTheTime() {
        // Matches today's behaviour exactly: a missed reminder shows the date without the time.
        assertEquals(
            DueLabelForm.Missed(monthNumber = 5, dayOfMonth = 5),
            oneShot(utc(2026, 5, 5, 9, 0), style = DueLabelStyle.Absolute)?.form,
        )
    }

    @Test
    fun absoluteStyle_weeklyRepeat_keepsTheRuleNameInFront() {
        val spec = repeating(
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = setOf(1)),
            timeOfDayMinutes = 18 * 60,
            style = DueLabelStyle.Absolute,
        )

        assertEquals(DueLabelForm.RepeatWeeklyNamed(listOf(1), "18:00"), spec?.form)
    }

    @Test
    fun absoluteStyle_dailyRepeat_isIdenticalToTheChip() {
        // "Daily 09:00" reads the same in both places, so there is one form and one string key.
        assertEquals(
            DueLabelForm.RepeatDaily("09:00"),
            repeating(ReminderRepeatRule(type = RepeatType.DAILY), style = DueLabelStyle.Absolute)
                ?.form,
        )
    }

    // ── The domain-item convenience wrapper ──

    @Test
    fun fillItemExtension_readsTheItemsOwnFields() {
        val item = ChecklistFillItem(text = "Call dentist", checked = false)
            .withReminderAt(utc(2026, 5, 6, 9, 0))

        assertEquals(
            DueLabelForm.Tomorrow("09:00"),
            item.dueLabelSpec(nowMillis = now, timeZone = zone)?.form,
        )
    }

    @Test
    fun fillItemExtension_withoutAReminder_isNull() {
        val item = ChecklistFillItem(text = "Someday maybe", checked = false)

        assertNull(item.dueLabelSpec(nowMillis = now, timeZone = zone))
    }
}
