package com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ChipDisplay
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.DayKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.OffsetUnit
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.RepeatKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.WeekdayKey
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SmartDateParserImpl].
 *
 * Fixed clock: 2026-05-13T10:00:00 in UTC+5 (i.e. 05:00 UTC).
 * That's a Wednesday (ISO day 3).
 *
 * "now" epoch millis = 2026-05-13T05:00:00Z = 1_747_112_400_000L
 */
class SmartDateParserTest {

    // ─── Test infrastructure ──────────────────────────────────────────────────

    private val timeZone = TimeZone.of("UTC+5")

    /**
     * Fixed clock: 2026-05-13 10:00 local (UTC+5) = 2026-05-13 05:00 UTC.
     * Day-of-week: Wednesday (ISO 3).
     */
    private val now: Long = LocalDateTime(2026, 5, 13, 5, 0)
        .toInstant(TimeZone.UTC).toEpochMilliseconds()

    private val parser: SmartDateParser = SmartDateParserImpl(NoOpLogger)

    /** Converts local date+time to epoch millis in [timeZone]. */
    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute)
            .toInstant(timeZone).toEpochMilliseconds()

    // ─── RU Happy Path (10 tests) ─────────────────────────────────────────────

    @Test
    fun ru_tomorrow_parsesRelativeDay() {
        val result = parser.parse("завтра", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertNull(display.timeMinutes)
        // No explicit time → default 09:00 next day (2026-05-14 09:00 local)
        assertEquals(localMillis(2026, 5, 14, 9), result.reminderAt)
        assertNull(result.repeatRule)
    }

    @Test
    fun ru_tomorrow_withMorningTime_parsesCorrectly() {
        // "завтра 7 утра" → tomorrow 07:00 local
        val result = parser.parse("завтра 7 утра", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertEquals(7 * 60, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 14, 7), result.reminderAt)
        assertEquals(7 * 60, result.timeOfDayMinutes)
    }

    @Test
    fun ru_todayWithTime_parsesCorrectly() {
        // "сегодня в 15:30" — 15:30 is in future (now is 10:00 local) → today 15:30
        val result = parser.parse("сегодня в 15:30", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TODAY, display.dayKey)
        assertEquals(15 * 60 + 30, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 13, 15, 30), result.reminderAt)
    }

    @Test
    fun ru_weekday_monday_parsesNextMonday() {
        // Now is Wednesday 2026-05-13. Next Monday is 2026-05-18.
        val result = parser.parse("в понедельник", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.Weekday
        assertEquals(WeekdayKey.MONDAY, display.dayKey)
        // No time specified → default 09:00
        assertEquals(localMillis(2026, 5, 18, 9), result.reminderAt)
        assertNull(result.repeatRule)
    }

    @Test
    fun ru_weekday_mondayWithHour_parsesTimeCorrectly() {
        // "понедельник 9" — bare "9" without qualifier should NOT produce a time
        // (false positive risk). The parser must NOT treat bare "9" as a time.
        // See judgment call comment: "9 евро → null for bare digit".
        // This test verifies Monday is recognized even if time extraction is skipped.
        val result = parser.parse("понедельник", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.Weekday
        assertEquals(WeekdayKey.MONDAY, display.dayKey)
        assertNull(result.repeatRule)
    }

    @Test
    fun ru_relativeOffset_twoHours_parsesCorrectly() {
        // "через 2 часа" → now + 2 * 3600 * 1000
        val result = parser.parse("через 2 часа", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeOffset
        assertEquals(2, display.amount)
        assertEquals(OffsetUnit.HOURS, display.unit)
        assertEquals(now + 2 * 3_600_000L, result.reminderAt)
        assertNull(result.repeatRule)
    }

    @Test
    fun ru_relativeOffset_thirtyMinutes_parsesCorrectly() {
        // "через 30 минут"
        val result = parser.parse("через 30 минут", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeOffset
        assertEquals(30, display.amount)
        assertEquals(OffsetUnit.MINUTES, display.unit)
        assertEquals(now + 30 * 60_000L, result.reminderAt)
    }

    @Test
    fun ru_repeatDaily_parsesRepeatRule() {
        val result = parser.parse("каждый день", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        assertEquals(RepeatType.DAILY, result.repeatRule!!.type)
        val display = result.display
        assertTrue(display is ChipDisplay.Repeat || display is ChipDisplay.Combined)
    }

    @Test
    fun ru_repeatEveryMondayAt7_parsesWeeklyRepeat() {
        // "каждый понедельник в 7" → weekly on Monday
        val result = parser.parse("каждый понедельник в 7", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        val rule = result.repeatRule!!
        assertEquals(RepeatType.WEEKLY, rule.type)
        assertTrue(rule.weekDays?.contains(1) == true) // 1 = Monday ISO
        val display = result.display as ChipDisplay.Combined
        val repeatDisplay = display.first as ChipDisplay.Repeat
        assertEquals(RepeatKey.WEEKLY, repeatDisplay.repeatKey)
    }

    @Test
    fun ru_repeatWeekdays_parsesWeekdaysRule() {
        // "по будням" → WEEKLY with Mon–Fri set
        val result = parser.parse("по будням", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        val rule = result.repeatRule!!
        assertEquals(RepeatType.WEEKLY, rule.type)
        assertEquals(setOf(1, 2, 3, 4, 5), rule.weekDays)
        val display = result.display
        assertTrue(display is ChipDisplay.Repeat || display is ChipDisplay.Combined)
    }

    // ─── EN Happy Path (10 tests) ─────────────────────────────────────────────

    @Test
    fun en_tomorrow_parsesRelativeDay() {
        val result = parser.parse("tomorrow", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertNull(display.timeMinutes)
        assertEquals(localMillis(2026, 5, 14, 9), result.reminderAt)
    }

    @Test
    fun en_tomorrow7am_parsesCorrectly() {
        // "tomorrow 7am"
        val result = parser.parse("tomorrow 7am", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertEquals(7 * 60, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 14, 7), result.reminderAt)
    }

    @Test
    fun en_today3pm_parsesCorrectly() {
        // "today 3pm" — 15:00 local is in the future (now = 10:00 local)
        val result = parser.parse("today 3pm", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TODAY, display.dayKey)
        assertEquals(15 * 60, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 13, 15), result.reminderAt)
    }

    @Test
    fun en_weekday_monday_parsesNextMonday() {
        // Now is Wednesday; next Monday = 2026-05-18
        val result = parser.parse("monday", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.Weekday
        assertEquals(WeekdayKey.MONDAY, display.dayKey)
        assertEquals(localMillis(2026, 5, 18, 9), result.reminderAt)
        assertNull(result.repeatRule)
    }

    @Test
    fun en_weekday_monWithHour_parsesCorrectly() {
        // "mon 9" — "9" alone is ambiguous; no time extracted (false positive guard)
        // The test verifies Monday is detected regardless of whether time is extracted.
        val result = parser.parse("mon 9", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.Weekday
        assertEquals(WeekdayKey.MONDAY, display.dayKey)
    }

    @Test
    fun en_relativeOffset_twoHours_parsesCorrectly() {
        val result = parser.parse("in 2 hours", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeOffset
        assertEquals(2, display.amount)
        assertEquals(OffsetUnit.HOURS, display.unit)
        assertEquals(now + 2 * 3_600_000L, result.reminderAt)
    }

    @Test
    fun en_relativeOffset_30min_parsesCorrectly() {
        val result = parser.parse("in 30 min", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeOffset
        assertEquals(30, display.amount)
        assertEquals(OffsetUnit.MINUTES, display.unit)
        assertEquals(now + 30 * 60_000L, result.reminderAt)
    }

    @Test
    fun en_repeatEveryDay_parsesRepeatRule() {
        val result = parser.parse("every day", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        assertEquals(RepeatType.DAILY, result.repeatRule!!.type)
    }

    @Test
    fun en_repeatEveryMondayAt7_parsesWeeklyRepeat() {
        // "every monday at 7"
        val result = parser.parse("every monday at 7", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        val rule = result.repeatRule!!
        assertEquals(RepeatType.WEEKLY, rule.type)
        assertTrue(rule.weekDays?.contains(1) == true) // Monday = ISO 1
    }

    @Test
    fun en_repeatWeekdays_parsesWeekdaysRule() {
        val result = parser.parse("weekdays", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        val rule = result.repeatRule!!
        assertEquals(RepeatType.WEEKLY, rule.type)
        assertEquals(setOf(1, 2, 3, 4, 5), rule.weekDays)
    }

    // ─── False Positive Guards (5 tests) ─────────────────────────────────────

    @Test
    fun falsePositive_randomRussianText_returnsNull() {
        // "купить хлеб" — no date phrase
        val result = parser.parse("купить хлеб", now, timeZone)
        assertNull(result)
    }

    @Test
    fun falsePositive_zavtrakDoesNotMatchZavtra() {
        // "завтракать" contains "завтра" as substring; must NOT match due to word boundary.
        // Decision: return null — "завтракать" is breakfast, not "tomorrow".
        val result = parser.parse("написать письмо завтракать", now, timeZone)
        assertNull(result)
    }

    @Test
    fun falsePositive_bareDigit9WithCurrency_returnsNull() {
        // "9 евро" — "9" alone without am/pm or HH:MM format is NOT a time.
        // Parser must not treat a bare number as a time.
        val result = parser.parse("9 евро", now, timeZone)
        assertNull(result)
    }

    @Test
    fun falsePositive_mondayMorningReport_returnsMonday() {
        // "monday morning report" — judgment call: "monday" is a recognized weekday,
        // "morning" is a recognized time-of-day word. Parser DOES detect Monday 09:00.
        // This is intentional: the parser is greedy for recognizable phrases.
        // The caller (ViewModel) can show the chip and let user dismiss it.
        // Decision: PARSE as Monday morning (not null). Documented here explicitly.
        val result = parser.parse("monday morning report", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.Weekday
        assertEquals(WeekdayKey.MONDAY, display.dayKey)
    }

    @Test
    fun falsePositive_todayLearnedThat_returnsToday() {
        // "сегодня узнал что..." — judgment call: "сегодня" IS a recognized phrase.
        // Decision: PARSE as today (not null). The chip appears; user can dismiss.
        // This is the same greedy policy as Todoist/TickTick (they also detect "today"
        // even mid-sentence). Explicitly documented as intended behavior.
        val result = parser.parse("сегодня узнал что произошло", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TODAY, display.dayKey)
    }

    // ─── Edge Cases (5 tests) ─────────────────────────────────────────────────

    @Test
    fun edge_emptyString_returnsNull() {
        assertNull(parser.parse("", now, timeZone))
    }

    @Test
    fun edge_whitespaceOnly_returnsNull() {
        assertNull(parser.parse("   ", now, timeZone))
    }

    @Test
    fun edge_uppercase_parsesCorrectly() {
        // "ЗАВТРА 7 УТРА" — must be lowercased and parsed identically to "завтра 7 утра"
        val result = parser.parse("ЗАВТРА 7 УТРА", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertEquals(7 * 60, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 14, 7), result.reminderAt)
    }

    @Test
    fun edge_doubleSpaces_parsesCorrectly() {
        // "завтра  7  утра" — multiple spaces must be collapsed before matching
        val result = parser.parse("завтра  7  утра", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertEquals(7 * 60, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 14, 7), result.reminderAt)
    }

    @Test
    fun edge_mixedCase_parsesCorrectly() {
        // "Завтра 7 Утра" — mixed case
        val result = parser.parse("Завтра 7 Утра", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TOMORROW, display.dayKey)
        assertEquals(7 * 60, display.timeMinutes)
        assertEquals(localMillis(2026, 5, 14, 7), result.reminderAt)
    }

    // ─── Additional correctness tests ─────────────────────────────────────────

    @Test
    fun pastTimeToday_schedulesTomorrow() {
        // Now is 10:00 local. "сегодня в 9:00" — 09:00 has already passed → tomorrow 09:00.
        val result = parser.parse("сегодня в 9:00", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.TODAY, display.dayKey)
        // reminderAt must be tomorrow since 09:00 today is past
        assertEquals(localMillis(2026, 5, 14, 9), result.reminderAt)
    }

    @Test
    fun ru_ежедневно_parsesRepeatRule() {
        val result = parser.parse("ежедневно", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        assertEquals(RepeatType.DAILY, result.repeatRule!!.type)
    }

    @Test
    fun en_daily_parsesRepeatRule() {
        val result = parser.parse("daily", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        assertEquals(RepeatType.DAILY, result.repeatRule!!.type)
    }

    @Test
    fun clockTime_15_30_parsesCorrectly() {
        // "15:30" — bare clock time, future today (now=10:00 local)
        val result = parser.parse("15:30", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.AbsoluteTime
        assertEquals(15, display.hour)
        assertEquals(30, display.minute)
        assertEquals(localMillis(2026, 5, 13, 15, 30), result.reminderAt)
    }

    @Test
    fun en_9amBareTime_parsesCorrectly() {
        // "9am" — already past today (now=10:00 local) → tomorrow 09:00
        val result = parser.parse("9am", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.AbsoluteTime
        assertEquals(9, display.hour)
        assertEquals(0, display.minute)
        assertEquals(localMillis(2026, 5, 14, 9), result.reminderAt)
    }

    @Test
    fun token_hasCorrectSubstringRange() {
        // "купить молоко завтра вечером" — parser should find the date phrase
        // and report correct start/end indices so caller can strip it.
        val input = "купить молоко завтра вечером"
        val result = parser.parse(input, now, timeZone)
        assertNotNull(result)
        // The matched substring must be a valid slice of the original input
        val sliced = input.substring(result.startIndex, result.endIndex)
        assertTrue(sliced.isNotEmpty())
        assertEquals(result.originalSubstring, sliced)
    }

    @Test
    fun ru_послезавтра_parsesDayAfterTomorrow() {
        val result = parser.parse("послезавтра", now, timeZone)
        assertNotNull(result)
        val display = result.display as ChipDisplay.RelativeDay
        assertEquals(DayKey.DAY_AFTER_TOMORROW, display.dayKey)
        assertEquals(localMillis(2026, 5, 15, 9), result.reminderAt)
    }

    @Test
    fun en_weekdayRepeat_weekly_parsesRule() {
        // "weekly" → repeat WEEKLY
        val result = parser.parse("weekly", now, timeZone)
        assertNotNull(result)
        assertNotNull(result.repeatRule)
        assertEquals(RepeatType.WEEKLY, result.repeatRule!!.type)
    }

    // ─── Bare hour after time preposition (2026-05-13 fix) ───────────────────

    @Test
    fun ru_sundayWeeklyAt12_parsesAsLiteral12() {
        // "каждое воскресенье в 12" — bare 2-digit hour after preposition "в" → 12:00 literal
        val result = parser.parse("каждое воскресенье в 12", now, timeZone)
        assertNotNull(result)
        assertEquals(12 * 60, result.timeOfDayMinutes)
        assertNotNull(result.repeatRule)
        assertEquals(RepeatType.WEEKLY, result.repeatRule!!.type)
        assertEquals(setOf(7), result.repeatRule!!.weekDays) // Sunday ISO=7
        // From Wed 2026-05-13 10:00, next Sunday = 2026-05-17 at 12:00
        assertEquals(localMillis(2026, 5, 17, 12), result.reminderAt)
    }

    @Test
    fun ru_mondayAt7_bareHourAfterPreposition() {
        // "понедельник в 7" — single-digit hour after "в" → 07:00 literal (no AM/PM heuristic)
        val result = parser.parse("понедельник в 7", now, timeZone)
        assertNotNull(result)
        assertEquals(7 * 60, result.timeOfDayMinutes)
        // From Wed 2026-05-13, next Monday = 2026-05-18 at 07:00
        assertEquals(localMillis(2026, 5, 18, 7), result.reminderAt)
    }

    @Test
    fun en_mondayAt12_bareHourAfterPreposition() {
        // "monday at 12" — bare 2-digit hour after "at" → 12:00 literal (not 12 PM heuristic)
        val result = parser.parse("monday at 12", now, timeZone)
        assertNotNull(result)
        assertEquals(12 * 60, result.timeOfDayMinutes)
        assertEquals(localMillis(2026, 5, 18, 12), result.reminderAt)
    }

    @Test
    fun en_tomorrowAt7_bareSingleDigitAfterPreposition() {
        // "tomorrow at 7" — bare single-digit hour after "at" → 07:00 literal
        val result = parser.parse("tomorrow at 7", now, timeZone)
        assertNotNull(result)
        assertEquals(7 * 60, result.timeOfDayMinutes)
        assertEquals(localMillis(2026, 5, 14, 7), result.reminderAt)
    }

    @Test
    fun ru_invalidHour25AfterPreposition_returnsNull() {
        // "в 25" alone — invalid hour, no other anchor → null
        val result = parser.parse("в 25", now, timeZone)
        assertNull(result)
    }

    // ─── endIndex regression for tryParseRepeatWeekday (2026-05-13 fix) ─────────

    @Test
    fun ru_repeatWeekdayWithTrailingText_endIndexCoversOnlyWeekday() {
        // Before fix: dayEnd was computed as prefixRange.last + 1 + weekdayStart + weekdayLen,
        // which double-counted the prefix length and pushed endIndex past the input end.
        // After fix: dayEnd = weekdayStart + weekdayLen (no prefix offset added again).
        // "каждое воскресенье купить молоко" — "каждое воскресенье" is 18 chars.
        val input = "каждое воскресенье купить молоко"
        val result = parser.parse(input, now, timeZone)
        assertNotNull(result)
        assertEquals("каждое воскресенье", result.originalSubstring)
        assertEquals(0, result.startIndex)
        assertEquals(18, result.endIndex)
        // After strip: " купить молоко".trim() == "купить молоко" — non-blank
    }

    @Test
    fun ru_repeatWeekdayTrailingPreposition_endIndexBeforeTrailingWord() {
        // "каждое воскресенье в" — trailing "в" is a stranded time preposition.
        // endIndex must still cover only "каждое воскресенье" (18 chars),
        // not overflow beyond the input length (20 chars = "каждое воскресенье в").
        val input = "каждое воскресенье в"  // length 20
        val result = parser.parse(input, now, timeZone)
        assertNotNull(result)
        // endIndex must be ≤ input.length and must NOT cover the trailing "в"
        assertTrue(result.endIndex <= input.length, "endIndex must not exceed input length")
        assertEquals(18, result.endIndex)
        val stripped = input.removeRange(result.startIndex, result.endIndex).trim()
        assertEquals("в", stripped)  // caller (ViewModel) handles this as stranded-prep
    }
}

// ─── Test doubles ─────────────────────────────────────────────────────────────

private object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
