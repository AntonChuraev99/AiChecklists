package com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.lexicon

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.OffsetUnit
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.WeekdayKey

/**
 * English lexicon for the smart-date parser.
 *
 * All string values are lowercase. Matching code must also lowercase its input
 * using Locale.ENGLISH before comparing to avoid the Turkish-İ bug.
 */
internal object EnDateLexicon {

    // ─── Relative day phrases ─────────────────────────────────────────────────

    val today = setOf("today")
    val tomorrow = setOf("tomorrow", "tmrw", "tmr")
    val dayAfterTomorrow = setOf("day after tomorrow", "the day after tomorrow")

    // ─── Weekday names (full + abbreviations) ────────────────────────────────

    val weekdays: Map<String, WeekdayKey> = mapOf(
        "monday" to WeekdayKey.MONDAY,
        "mon" to WeekdayKey.MONDAY,
        "tuesday" to WeekdayKey.TUESDAY,
        "tue" to WeekdayKey.TUESDAY,
        "tues" to WeekdayKey.TUESDAY,
        "wednesday" to WeekdayKey.WEDNESDAY,
        "wed" to WeekdayKey.WEDNESDAY,
        "thursday" to WeekdayKey.THURSDAY,
        "thu" to WeekdayKey.THURSDAY,
        "thur" to WeekdayKey.THURSDAY,
        "thurs" to WeekdayKey.THURSDAY,
        "friday" to WeekdayKey.FRIDAY,
        "fri" to WeekdayKey.FRIDAY,
        "saturday" to WeekdayKey.SATURDAY,
        "sat" to WeekdayKey.SATURDAY,
        "sunday" to WeekdayKey.SUNDAY,
        "sun" to WeekdayKey.SUNDAY,
    )

    // ─── Time-of-day words ────────────────────────────────────────────────────

    /** "morning" → 09:00 */
    val morningTime = 9 * 60

    /** "noon" / "midday" → 12:00 */
    val noonTime = 12 * 60

    /** "evening" / "afternoon" → 19:00 */
    val eveningTime = 19 * 60

    /** "night" → 22:00 */
    val nightTime = 22 * 60

    val morningWords = setOf("morning")
    val noonWords = setOf("noon", "midday", "lunch")
    val eveningWords = setOf("evening", "afternoon")
    val nightWords = setOf("night", "midnight")

    // ─── Repeat phrases ───────────────────────────────────────────────────────

    val repeatDaily: Set<String> = setOf("every day", "daily", "each day")
    val repeatWeekly: Set<String> = setOf("every week", "weekly", "each week")
    val repeatMonthly: Set<String> = setOf("every month", "monthly", "each month")
    val repeatYearly: Set<String> = setOf("every year", "yearly", "annually", "each year")
    val repeatWeekdays: Set<String> = setOf("weekdays", "every weekday", "on weekdays", "every working day")

    /**
     * "every <weekday>" prefix — signals a weekly repeat on a specific day.
     */
    val repeatWeekdayPrefix = setOf("every", "each")

    // ─── Relative offset ──────────────────────────────────────────────────────

    /** "in 2 hours", "in 30 minutes" */
    val offsetPrepositions = setOf("in")

    val offsetUnits: Map<String, OffsetUnit> = mapOf(
        // minutes
        "minute" to OffsetUnit.MINUTES,
        "minutes" to OffsetUnit.MINUTES,
        "min" to OffsetUnit.MINUTES,
        "mins" to OffsetUnit.MINUTES,
        // hours
        "hour" to OffsetUnit.HOURS,
        "hours" to OffsetUnit.HOURS,
        "hr" to OffsetUnit.HOURS,
        "hrs" to OffsetUnit.HOURS,
        // days
        "day" to OffsetUnit.DAYS,
        "days" to OffsetUnit.DAYS,
        // weeks
        "week" to OffsetUnit.WEEKS,
        "weeks" to OffsetUnit.WEEKS,
    )

    // ─── Prepositions for time introduction ──────────────────────────────────

    /** "at 9", "at 9am" */
    val timePrepositions = setOf("at")
}
