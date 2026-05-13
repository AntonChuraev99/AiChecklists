package com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.lexicon

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.OffsetUnit
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.RepeatKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.WeekdayKey

/**
 * Russian lexicon for the smart-date parser.
 *
 * All string values are lowercase. Matching code must also lowercase its input
 * using Locale("ru") before comparing to avoid the Turkish-İ bug.
 */
internal object RuDateLexicon {

    // ─── Relative day phrases ─────────────────────────────────────────────────

    val today = setOf("сегодня")
    val tomorrow = setOf("завтра")
    val dayAfterTomorrow = setOf("послезавтра")

    // ─── Weekday names (full + abbreviations) ────────────────────────────────

    val weekdays: Map<String, WeekdayKey> = mapOf(
        "понедельник" to WeekdayKey.MONDAY,
        "пн" to WeekdayKey.MONDAY,
        "вторник" to WeekdayKey.TUESDAY,
        "вт" to WeekdayKey.TUESDAY,
        "среда" to WeekdayKey.WEDNESDAY,
        "среду" to WeekdayKey.WEDNESDAY,
        "ср" to WeekdayKey.WEDNESDAY,
        "четверг" to WeekdayKey.THURSDAY,
        "чт" to WeekdayKey.THURSDAY,
        "пятница" to WeekdayKey.FRIDAY,
        "пятницу" to WeekdayKey.FRIDAY,
        "пт" to WeekdayKey.FRIDAY,
        "суббота" to WeekdayKey.SATURDAY,
        "субботу" to WeekdayKey.SATURDAY,
        "сб" to WeekdayKey.SATURDAY,
        "воскресенье" to WeekdayKey.SUNDAY,
        "вс" to WeekdayKey.SUNDAY,
    )

    // ─── Time-of-day words (minutes since midnight) ───────────────────────────

    /** "утром" → 09:00 */
    val morningTime = 9 * 60

    /** "днём" / "в обед" → 12:00 */
    val noonTime = 12 * 60

    /** "вечером" → 19:00 */
    val eveningTime = 19 * 60

    /** "ночью" → 22:00 */
    val nightTime = 22 * 60

    val morningWords = setOf("утром", "утра")
    val noonWords = setOf("днём", "днем", "в обед", "обед")
    val eveningWords = setOf("вечером", "вечера")
    val nightWords = setOf("ночью", "ночи")

    // ─── Repeat phrases (ordered longest-first to avoid substring shadowing) ──

    val repeatDaily: Set<String> = setOf("каждый день", "ежедневно")
    val repeatWeekly: Set<String> = setOf("каждую неделю", "еженедельно")
    val repeatMonthly: Set<String> = setOf("каждый месяц", "ежемесячно")
    val repeatYearly: Set<String> = setOf("каждый год", "ежегодно")
    val repeatWeekdays: Set<String> = setOf("по будням", "каждый рабочий день", "по рабочим дням")

    /**
     * "каждый <weekday>" prefix — signals a weekly repeat on a specific day.
     * Parse the weekday from the word following this prefix.
     */
    val repeatWeekdayPrefix = setOf("каждый", "каждую", "каждое")

    /** Maps [RepeatKey] to its canonical phrases for display (not used for parsing). */
    val repeatKeyPhrases: Map<RepeatKey, Set<String>> = mapOf(
        RepeatKey.DAILY to repeatDaily,
        RepeatKey.WEEKLY to repeatWeekly,
        RepeatKey.MONTHLY to repeatMonthly,
        RepeatKey.YEARLY to repeatYearly,
        RepeatKey.WEEKDAYS to repeatWeekdays,
    )

    // ─── Relative offset ──────────────────────────────────────────────────────

    /** Preposition introducing a relative offset, e.g. "через 2 часа" */
    val offsetPrepositions = setOf("через")

    /**
     * Offset unit words mapped to [OffsetUnit].
     * Order matters for matching: longer forms first within the same unit to
     * avoid "минут" matching before "минуту" or "минуты".
     */
    val offsetUnits: Map<String, OffsetUnit> = mapOf(
        // minutes
        "минуту" to OffsetUnit.MINUTES,
        "минуты" to OffsetUnit.MINUTES,
        "минут" to OffsetUnit.MINUTES,
        "мин" to OffsetUnit.MINUTES,
        // hours
        "часа" to OffsetUnit.HOURS,
        "часов" to OffsetUnit.HOURS,
        "час" to OffsetUnit.HOURS,
        "ч" to OffsetUnit.HOURS,
        // days
        "дня" to OffsetUnit.DAYS,
        "дней" to OffsetUnit.DAYS,
        "день" to OffsetUnit.DAYS,
        "д" to OffsetUnit.DAYS,
        // weeks
        "недели" to OffsetUnit.WEEKS,
        "недель" to OffsetUnit.WEEKS,
        "неделю" to OffsetUnit.WEEKS,
        "неделя" to OffsetUnit.WEEKS,
    )

    // ─── Prepositions that introduce a weekday or time ────────────────────────

    /** "в понедельник", "в 9:00" */
    val timePrepositions = setOf("в", "во")
}
