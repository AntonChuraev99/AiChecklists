package com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Green coverage for [ReminderRepeatRule.toRRule] — the pure mapping of a recurring
 * reminder rule to an iCalendar RRULE body used when exporting to a calendar event.
 */
class RRuleMapperTest {

    @Test
    fun toRRule_dailyIntervalOne_freqDailyOnly() {
        assertEquals("FREQ=DAILY", ReminderRepeatRule(type = RepeatType.DAILY).toRRule())
    }

    @Test
    fun toRRule_dailyIntervalThree_includesInterval() {
        assertEquals(
            "FREQ=DAILY;INTERVAL=3",
            ReminderRepeatRule(type = RepeatType.DAILY, interval = 3).toRRule()
        )
    }

    @Test
    fun toRRule_weeklyWithWeekDays_mapsByDay() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = setOf(1, 3, 5)).toRRule()
        )
    }

    @Test
    fun toRRule_weeklyUnsortedWeekDays_sortsByDay() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = setOf(5, 1, 3)).toRRule()
        )
    }

    @Test
    fun toRRule_weekdaysPreset_mapsMondayToFriday() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5)).toRRule()
        )
    }

    @Test
    fun toRRule_weeklyIntervalTwoNoWeekDays_includesInterval() {
        assertEquals(
            "FREQ=WEEKLY;INTERVAL=2",
            ReminderRepeatRule(type = RepeatType.WEEKLY, interval = 2).toRRule()
        )
    }

    @Test
    fun toRRule_weeklyEmptyWeekDays_omitsByDay() {
        assertEquals(
            "FREQ=WEEKLY",
            ReminderRepeatRule(type = RepeatType.WEEKLY, weekDays = emptySet()).toRRule()
        )
    }

    @Test
    fun toRRule_monthlyIntervalThree_includesInterval() {
        assertEquals(
            "FREQ=MONTHLY;INTERVAL=3",
            ReminderRepeatRule(type = RepeatType.MONTHLY, interval = 3).toRRule()
        )
    }

    @Test
    fun toRRule_yearly_freqYearlyOnly() {
        assertEquals("FREQ=YEARLY", ReminderRepeatRule(type = RepeatType.YEARLY).toRRule())
    }

    @Test
    fun toRRule_afterCount_appendsCount() {
        assertEquals(
            "FREQ=DAILY;COUNT=10",
            ReminderRepeatRule(
                type = RepeatType.DAILY,
                endCondition = RepeatEndCondition.AfterCount(10)
            ).toRRule()
        )
    }

    @Test
    fun toRRule_untilDate_appendsUtcUntilInBasicFormat() {
        val until = Instant.parse("2026-12-31T23:59:00Z").toEpochMilliseconds()
        assertEquals(
            "FREQ=DAILY;UNTIL=20261231T235900Z",
            ReminderRepeatRule(
                type = RepeatType.DAILY,
                endCondition = RepeatEndCondition.UntilDate(until)
            ).toRRule()
        )
    }
}
