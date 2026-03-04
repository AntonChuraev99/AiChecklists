package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderRepeatRuleTest {

    private val tz = TimeZone.UTC
    private val json = Json { ignoreUnknownKeys = true }

    private fun dateTimeToMillis(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long {
        return LocalDateTime(year, month, day, hour, minute)
            .toInstant(tz).toEpochMilliseconds()
    }

    // ─── DAILY ───────────────────────────────────────────

    @Test
    fun daily_nextOccurrence_addsOneDay() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000 // 1 second after trigger

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 5, 9, 0), next)
    }

    @Test
    fun daily_everyThreeDays() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 3)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 7, 9, 0), next)
    }

    @Test
    fun daily_preservesTimeOfDay() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY)
        val trigger = dateTimeToMillis(2026, 3, 4, 14, 30)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 5, 14, 30), next)
    }

    // ─── WEEKLY ──────────────────────────────────────────

    @Test
    fun weekly_simpleNoWeekdays_addsOneWeek() {
        val rule = ReminderRepeatRule(type = RepeatType.WEEKLY)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0) // Wednesday
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 11, 9, 0), next) // Next Wednesday
    }

    @Test
    fun weekly_everyTwoWeeks() {
        val rule = ReminderRepeatRule(type = RepeatType.WEEKLY, interval = 2)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0) // Wednesday
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 18, 9, 0), next)
    }

    @Test
    fun weekly_withWeekdays_nextDayInSameWeek() {
        // March 4 2026 = Wednesday (3 in ISO)
        // weekDays = {3 (Wed), 5 (Fri)}
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            weekDays = setOf(3, 5)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0) // Wednesday
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Next should be Friday March 6
        assertEquals(dateTimeToMillis(2026, 3, 6, 9, 0), next)
    }

    @Test
    fun weekly_withWeekdays_jumpsToNextWeek() {
        // March 6 2026 = Friday (5 in ISO)
        // weekDays = {1 (Mon), 5 (Fri)}
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            weekDays = setOf(1, 5)
        )
        val trigger = dateTimeToMillis(2026, 3, 6, 9, 0) // Friday
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Next should be Monday March 9
        assertEquals(dateTimeToMillis(2026, 3, 9, 9, 0), next)
    }

    @Test
    fun weekly_withWeekdays_everyTwoWeeks_jumpsCorrectly() {
        // March 6 2026 = Friday (5 in ISO)
        // weekDays = {1 (Mon), 5 (Fri)}, interval = 2
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            interval = 2,
            weekDays = setOf(1, 5)
        )
        val trigger = dateTimeToMillis(2026, 3, 6, 9, 0) // Friday
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Skip to the Monday of the week 2 weeks later: March 16
        assertEquals(dateTimeToMillis(2026, 3, 16, 9, 0), next)
    }

    @Test
    fun weekly_monWedFri_fromMonday() {
        // March 2 2026 = Monday (1 in ISO)
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            weekDays = setOf(1, 3, 5)
        )
        val trigger = dateTimeToMillis(2026, 3, 2, 9, 0) // Monday
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Next should be Wednesday March 4
        assertEquals(dateTimeToMillis(2026, 3, 4, 9, 0), next)
    }

    // ─── MONTHLY ─────────────────────────────────────────

    @Test
    fun monthly_addsOneMonth() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY)
        val trigger = dateTimeToMillis(2026, 3, 15, 9, 0)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 4, 15, 9, 0), next)
    }

    @Test
    fun monthly_everyThreeMonths() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY, interval = 3)
        val trigger = dateTimeToMillis(2026, 1, 15, 9, 0)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 4, 15, 9, 0), next)
    }

    @Test
    fun monthly_monthEndClamping_jan31ToFeb28() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY)
        val trigger = dateTimeToMillis(2026, 1, 31, 9, 0)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // 2026 is not a leap year, so Feb has 28 days
        assertEquals(dateTimeToMillis(2026, 2, 28, 9, 0), next)
    }

    @Test
    fun monthly_monthEndClamping_jan31ToFeb29LeapYear() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY)
        val trigger = dateTimeToMillis(2028, 1, 31, 9, 0) // 2028 is leap year
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2028, 2, 29, 9, 0), next)
    }

    @Test
    fun monthly_yearBoundary() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY)
        val trigger = dateTimeToMillis(2026, 12, 15, 9, 0)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2027, 1, 15, 9, 0), next)
    }

    // ─── END CONDITIONS ──────────────────────────────────

    @Test
    fun endCondition_never_alwaysContinues() {
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.Never
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        val next = rule.computeNextOccurrence(trigger, 100, now)

        assertNotNull(next, "Should continue indefinitely with Never end condition")
    }

    @Test
    fun endCondition_afterCount_stopsAtMax() {
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.AfterCount(maxCount = 3)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        // count=2, maxCount=3 → 2+1 >= 3 → should return null
        val next = rule.computeNextOccurrence(trigger, 2, now)

        assertNull(next, "Should stop after 3 occurrences")
    }

    @Test
    fun endCondition_afterCount_continuesBelowMax() {
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.AfterCount(maxCount = 5)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        // count=2, maxCount=5 → 2+1=3 < 5 → should continue
        val next = rule.computeNextOccurrence(trigger, 2, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 5, 9, 0), next)
    }

    @Test
    fun endCondition_afterCount_oneOccurrence() {
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.AfterCount(maxCount = 1)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        // count=0, maxCount=1 → 0+1 >= 1 → should return null
        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNull(next, "After 1 occurrence, should stop")
    }

    @Test
    fun endCondition_untilDate_stopsWhenPastEndDate() {
        val endDate = dateTimeToMillis(2026, 3, 6, 23, 59)
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            interval = 5,
            endCondition = RepeatEndCondition.UntilDate(dateMillis = endDate)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        // Next would be March 9, which is past end date March 6
        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNull(next, "Should stop when next occurrence exceeds until date")
    }

    @Test
    fun endCondition_untilDate_continuesBeforeEndDate() {
        val endDate = dateTimeToMillis(2026, 3, 10, 23, 59)
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.UntilDate(dateMillis = endDate)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = trigger + 1000

        // Next would be March 5, well before end date March 10
        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 5, 9, 0), next)
    }

    // ─── O(1) FAST-FORWARD ───────────────────────────────

    @Test
    fun fastForward_daily_deviceOffForThreeDays() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        // Device off for 3 days — now is March 7 at 10:00
        val now = dateTimeToMillis(2026, 3, 7, 10, 0)

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Should skip to March 8 (next future daily)
        assertEquals(dateTimeToMillis(2026, 3, 8, 9, 0), next)
    }

    @Test
    fun fastForward_daily_deviceOffForOneYear() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        val now = dateTimeToMillis(2027, 3, 4, 10, 0) // 1 year later

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Should compute in O(1) — not 365 loop iterations
        assertEquals(dateTimeToMillis(2027, 3, 5, 9, 0), next)
    }

    @Test
    fun fastForward_weekly_deviceOffForFourWeeks() {
        val rule = ReminderRepeatRule(type = RepeatType.WEEKLY)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0) // Wednesday
        // 4 weeks later
        val now = dateTimeToMillis(2026, 4, 1, 10, 0) // Wednesday

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Should be next Wednesday after April 1
        assertEquals(dateTimeToMillis(2026, 4, 8, 9, 0), next)
    }

    @Test
    fun fastForward_monthly_deviceOffForSixMonths() {
        val rule = ReminderRepeatRule(type = RepeatType.MONTHLY)
        val trigger = dateTimeToMillis(2026, 1, 15, 9, 0)
        val now = dateTimeToMillis(2026, 7, 20, 10, 0)

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Should skip to August 15
        assertEquals(dateTimeToMillis(2026, 8, 15, 9, 0), next)
    }

    @Test
    fun fastForward_everyThreeDays_deviceOff() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, interval = 3)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        // 10 days later
        val now = dateTimeToMillis(2026, 3, 14, 10, 0)

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // March 4 + 3 = March 7, + 3 = March 10, + 3 = March 13, + 3 = March 16
        assertEquals(dateTimeToMillis(2026, 3, 16, 9, 0), next)
    }

    @Test
    fun fastForward_weeklyWithWeekdays_deviceOff() {
        // Mon, Fri weekly
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            weekDays = setOf(1, 5)
        )
        val trigger = dateTimeToMillis(2026, 3, 2, 9, 0) // Monday
        // 2 weeks later, Thursday
        val now = dateTimeToMillis(2026, 3, 19, 10, 0)

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        // Should be Friday March 20
        assertEquals(dateTimeToMillis(2026, 3, 20, 9, 0), next)
    }

    // ─── SERIALIZATION ───────────────────────────────────

    @Test
    fun serialization_roundTrip_dailyRule() {
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            interval = 2,
            endCondition = RepeatEndCondition.Never,
            resetChecks = true
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<ReminderRepeatRule>(encoded)

        assertEquals(rule, decoded)
    }

    @Test
    fun serialization_roundTrip_weeklyWithWeekdays() {
        val rule = ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            interval = 1,
            weekDays = setOf(1, 3, 5),
            endCondition = RepeatEndCondition.AfterCount(maxCount = 10)
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<ReminderRepeatRule>(encoded)

        assertEquals(rule, decoded)
    }

    @Test
    fun serialization_roundTrip_monthlyUntilDate() {
        val rule = ReminderRepeatRule(
            type = RepeatType.MONTHLY,
            endCondition = RepeatEndCondition.UntilDate(
                dateMillis = dateTimeToMillis(2027, 1, 1, 0, 0)
            )
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<ReminderRepeatRule>(encoded)

        assertEquals(rule, decoded)
    }

    @Test
    fun serialization_endConditionDiscriminator_stableSerialNames() {
        // Use encodeDefaults=true so default endCondition (Never) is explicitly serialized
        val verboseJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        val neverRule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val untilRule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.UntilDate(123L))
        val afterRule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.AfterCount(5))

        val neverJson = verboseJson.encodeToString(neverRule)
        val untilJson = verboseJson.encodeToString(untilRule)
        val afterJson = verboseJson.encodeToString(afterRule)

        // Verify discriminator values in JSON match @SerialName annotations
        assertTrue(neverJson.contains("\"never\""), "Should contain 'never' discriminator: $neverJson")
        assertTrue(untilJson.contains("\"until_date\""), "Should contain 'until_date' discriminator: $untilJson")
        assertTrue(afterJson.contains("\"after_count\""), "Should contain 'after_count' discriminator: $afterJson")
    }

    @Test
    fun serialization_corruptedJson_doesNotCrash() {
        val corrupted = """{"type": "unknown_type"}"""
        val result = runCatching {
            json.decodeFromString<ReminderRepeatRule>(corrupted)
        }
        // Should fail gracefully (exception, not crash)
        assertTrue(result.isFailure, "Corrupted JSON should cause an exception")
    }

    @Test
    fun serialization_repeatType_serialNames() {
        val dailyRule = ReminderRepeatRule(type = RepeatType.DAILY)
        val encoded = json.encodeToString(dailyRule)
        assertTrue(encoded.contains("\"daily\""), "RepeatType.DAILY should serialize as 'daily': $encoded")
    }

    @Test
    fun serialization_ignoreUnknownKeys() {
        // Simulate a future version adding a new field
        val futureJson = """{"type":"daily","interval":1,"endCondition":{"type":"never"},"resetChecks":false,"newFutureField":"value"}"""
        val jsonParser = Json { ignoreUnknownKeys = true }
        val decoded = jsonParser.decodeFromString<ReminderRepeatRule>(futureJson)

        assertEquals(RepeatType.DAILY, decoded.type)
        assertEquals(1, decoded.interval)
    }

    // ─── findNextWeekday ─────────────────────────────────

    @Test
    fun findNextWeekday_sameWeek_nextDay() {
        // Tuesday March 3 2026 = day 2 (ISO)
        val from = LocalDate(2026, 3, 3) // Tuesday
        val weekDays = setOf(4) // Thursday

        val result = findNextWeekday(from, weekDays, 1)

        assertEquals(LocalDate(2026, 3, 5), result) // Thursday
    }

    @Test
    fun findNextWeekday_wrapsToNextWeek() {
        // Friday March 6 2026 = day 5 (ISO)
        val from = LocalDate(2026, 3, 6) // Friday
        val weekDays = setOf(1) // Monday

        val result = findNextWeekday(from, weekDays, 1)

        assertEquals(LocalDate(2026, 3, 9), result) // Next Monday
    }

    @Test
    fun findNextWeekday_sundayWraps() {
        val from = LocalDate(2026, 3, 8) // Sunday = 7
        val weekDays = setOf(1, 3) // Mon, Wed

        val result = findNextWeekday(from, weekDays, 1)

        assertEquals(LocalDate(2026, 3, 9), result) // Next Monday
    }

    @Test
    fun findNextWeekday_withIntervalWeeks() {
        val from = LocalDate(2026, 3, 6) // Friday = 5
        val weekDays = setOf(1) // Monday
        val intervalWeeks = 2

        val result = findNextWeekday(from, weekDays, intervalWeeks)

        // Jump 2 weeks from next Monday: next Monday is March 9, +1 week = March 16
        assertEquals(LocalDate(2026, 3, 16), result)
    }

    // ─── EDGE CASES ──────────────────────────────────────

    @Test
    fun nextOccurrence_alreadyInFuture_noSkip() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY)
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        // now is BEFORE next occurrence
        val now = trigger - 60_000 // 1 minute before trigger

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNotNull(next)
        assertEquals(dateTimeToMillis(2026, 3, 5, 9, 0), next)
    }

    @Test
    fun nextOccurrence_fastForwardWithEndCondition_untilDateReached() {
        val endDate = dateTimeToMillis(2026, 3, 10, 23, 59)
        val rule = ReminderRepeatRule(
            type = RepeatType.DAILY,
            endCondition = RepeatEndCondition.UntilDate(dateMillis = endDate)
        )
        val trigger = dateTimeToMillis(2026, 3, 4, 9, 0)
        // Device off for 10 days — fast forward past end date
        val now = dateTimeToMillis(2026, 3, 14, 10, 0)

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNull(next, "Should return null when fast-forwarded past end date")
    }

    @Test
    fun nextOccurrence_fastForwardMonthly_withEndDate() {
        val endDate = dateTimeToMillis(2026, 6, 1, 0, 0)
        val rule = ReminderRepeatRule(
            type = RepeatType.MONTHLY,
            endCondition = RepeatEndCondition.UntilDate(dateMillis = endDate)
        )
        val trigger = dateTimeToMillis(2026, 1, 15, 9, 0)
        // Device off for 6 months — past end date
        val now = dateTimeToMillis(2026, 7, 20, 10, 0)

        val next = rule.computeNextOccurrence(trigger, 0, now)

        assertNull(next, "Should return null when monthly fast-forward passes end date")
    }
}
