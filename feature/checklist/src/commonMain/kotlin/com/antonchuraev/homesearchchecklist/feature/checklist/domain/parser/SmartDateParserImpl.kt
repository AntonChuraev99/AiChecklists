package com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.lexicon.EnDateLexicon
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.lexicon.RuDateLexicon
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ChipDisplay
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.DayKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.OffsetUnit
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.RepeatKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.WeekdayKey
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure-Kotlin, KMP-compatible parser for natural-language date/time/repeat phrases.
 *
 * Supports Russian and English. No LLM, no network, no AI credits.
 *
 * Detection priority (highest → lowest):
 * 1. Repeat phrases ("каждый день", "every monday at 7")
 * 2. Weekday + optional time ("в понедельник", "monday 9am")
 * 3. Relative day + optional time ("завтра 7 утра", "tomorrow 3pm")
 * 4. Time-of-day word only ("утром", "morning")
 * 5. Relative offset ("через 2 часа", "in 30 minutes")
 * 6. Bare clock time ("9:00", "в 15:30", "at 9am")
 *
 * Correctness rules enforced:
 * - Locale-explicit lowercase (EN: Locale.ENGLISH, RU: Locale("ru"))
 * - Word-boundary matching — "завтрак" does NOT match "завтра"
 * - Double-space tolerance
 * - AM/PM heuristic from time-of-day words
 * - Past-time-today → schedule tomorrow
 */
class SmartDateParserImpl(private val logger: AppLogger) : SmartDateParser {

    private companion object {
        const val TAG = "SmartDateParser"

        /** Default reminder time when user gave a day but no explicit time: 09:00 */
        const val DEFAULT_TIME_MINUTES = 9 * 60

        /** Regex for a clock time: 9:00, 09:30, 9, 15 — captured in named groups. */
        val CLOCK_REGEX = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")

        /** Regex for bare hour with optional am/pm: "9am", "9 am", "9pm", "7 вечера" — handled via lexicon. */
        val BARE_HOUR_AMPM_REGEX = Regex("""(?<!\d)(\d{1,2})\s*(am|pm)(?!\w)""", RegexOption.IGNORE_CASE)

        /** Normalizes multiple internal spaces to single space and trims. */
        fun normalize(input: String): String =
            input.trim().replace(Regex("""\s+"""), " ")

        /** Returns true if [word] appears at a word boundary in [text] (lowercased by caller). */
        fun containsWordBoundary(text: String, word: String): Boolean {
            if (word.isEmpty()) return false
            val escaped = Regex.escape(word)
            // \b doesn't work well for Cyrillic in all regex engines; use explicit boundary check
            return Regex("""(?<![а-яёa-z\d])${escaped}(?![а-яёa-z\d])""")
                .containsMatchIn(text)
        }

        /** Find the first match range of [phrase] in [text] respecting word boundaries. */
        fun findPhraseRange(text: String, phrase: String): IntRange? {
            if (phrase.isEmpty()) return null
            val escaped = Regex.escape(phrase)
            return Regex("""(?<![а-яёa-z\d])${escaped}(?![а-яёa-z\d])""")
                .find(text)?.range
        }

        /** Returns WeekdayKey ISO day number (1=Mon..7=Sun). */
        fun WeekdayKey.isoDayNumber(): Int = when (this) {
            WeekdayKey.MONDAY -> 1
            WeekdayKey.TUESDAY -> 2
            WeekdayKey.WEDNESDAY -> 3
            WeekdayKey.THURSDAY -> 4
            WeekdayKey.FRIDAY -> 5
            WeekdayKey.SATURDAY -> 6
            WeekdayKey.SUNDAY -> 7
        }

        /** Returns the ISO day number set for WEEKDAYS repeat (Mon–Fri). */
        fun weekdaysIsoSet(): Set<Int> = setOf(1, 2, 3, 4, 5)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    override fun parse(input: String, now: Long, timeZone: TimeZone): ParsedDateToken? {
        if (input.isBlank()) return null
        return runCatching { parseInternal(input, now, timeZone) }
            .onFailure { e ->
                logger.error(
                    TAG,
                    "Parse failed: inputLen=${input.length} tz=$timeZone exception=${e::class.simpleName}",
                    e,
                )
            }
            .getOrNull()
    }

    // ─── Internal parsing ─────────────────────────────────────────────────────

    private fun parseInternal(input: String, now: Long, timeZone: TimeZone): ParsedDateToken? {
        val normalized = normalize(input)
        // Locale-explicit lowercase per language region:
        // We try both without knowing the language upfront.
        val lowerRu = normalizeToLowerRu(normalized)
        val lowerEn = normalizeToLowerEn(normalized)

        val nowInstant = Instant.fromEpochMilliseconds(now)
        val nowLocal = nowInstant.toLocalDateTime(timeZone)

        // Priority 1: Repeat phrases
        tryParseRepeat(normalized, lowerRu, lowerEn, nowLocal, timeZone)?.let { return it }

        // Priority 2: Weekday + optional time
        tryParseWeekday(normalized, lowerRu, lowerEn, nowLocal, timeZone)?.let { return it }

        // Priority 3: Relative day + optional time
        tryParseRelativeDay(normalized, lowerRu, lowerEn, nowLocal, timeZone)?.let { return it }

        // Priority 4: Time-of-day word only
        tryParseTimeOfDayWord(normalized, lowerRu, lowerEn, nowLocal, timeZone)?.let { return it }

        // Priority 5: Relative offset
        tryParseRelativeOffset(normalized, lowerRu, lowerEn, now)?.let { return it }

        // Priority 6: Bare clock time
        tryParseBareTime(normalized, lowerRu, lowerEn, nowLocal, timeZone)?.let { return it }

        return null
    }

    // ─── Priority 1: Repeat ───────────────────────────────────────────────────

    private fun tryParseRepeat(
        original: String,
        lowerRu: String,
        lowerEn: String,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        // Fixed repeat keys (non-weekday-specific)
        data class RepeatEntry(val key: RepeatKey, val phrases: Set<String>, val isRu: Boolean)

        val candidates = listOf(
            // RU
            RepeatEntry(RepeatKey.WEEKDAYS, RuDateLexicon.repeatWeekdays, true),
            RepeatEntry(RepeatKey.DAILY, RuDateLexicon.repeatDaily, true),
            RepeatEntry(RepeatKey.WEEKLY, RuDateLexicon.repeatWeekly, true),
            RepeatEntry(RepeatKey.MONTHLY, RuDateLexicon.repeatMonthly, true),
            RepeatEntry(RepeatKey.YEARLY, RuDateLexicon.repeatYearly, true),
            // EN
            RepeatEntry(RepeatKey.WEEKDAYS, EnDateLexicon.repeatWeekdays, false),
            RepeatEntry(RepeatKey.DAILY, EnDateLexicon.repeatDaily, false),
            RepeatEntry(RepeatKey.WEEKLY, EnDateLexicon.repeatWeekly, false),
            RepeatEntry(RepeatKey.MONTHLY, EnDateLexicon.repeatMonthly, false),
            RepeatEntry(RepeatKey.YEARLY, EnDateLexicon.repeatYearly, false),
        )

        for (entry in candidates) {
            val lower = if (entry.isRu) lowerRu else lowerEn
            for (phrase in entry.phrases.sortedByDescending { it.length }) {
                val range = findPhraseRange(lower, phrase) ?: continue
                val substring = original.substring(range)
                val repeatRule = toRepeatRule(entry.key)
                // Compute first trigger: next occurrence from now using the repeat time
                val timeMinutes = extractTimeMinutes(original, lower, entry.isRu)
                val reminderAt = computeFirstRepeatTrigger(nowLocal, timeZone, timeMinutes)
                val chipDisplay = buildRepeatChipDisplay(entry.key, timeMinutes)
                return ParsedDateToken(
                    originalSubstring = substring,
                    startIndex = range.first,
                    endIndex = range.last + 1,
                    display = chipDisplay,
                    reminderAt = reminderAt,
                    repeatRule = repeatRule,
                    timeOfDayMinutes = timeMinutes,
                )
            }
        }

        // "каждый <weekday>" / "every <weekday>" — weekly repeat on a specific day
        tryParseRepeatWeekday(original, lowerRu, isRu = true, nowLocal, timeZone)?.let { return it }
        tryParseRepeatWeekday(original, lowerEn, isRu = false, nowLocal, timeZone)?.let { return it }

        return null
    }

    private fun tryParseRepeatWeekday(
        original: String,
        lower: String,
        isRu: Boolean,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        val prefixes = if (isRu) RuDateLexicon.repeatWeekdayPrefix else EnDateLexicon.repeatWeekdayPrefix
        val weekdays = if (isRu) RuDateLexicon.weekdays else EnDateLexicon.weekdays

        for (prefix in prefixes) {
            val prefixRange = findPhraseRange(lower, prefix) ?: continue
            val afterPrefix = lower.substring(prefixRange.last + 1).trimStart()
            // Try to match a weekday name at the start of afterPrefix
            val matchedEntry = weekdays.entries
                .filter { afterPrefix.startsWith(it.key) }
                .maxByOrNull { it.key.length } ?: continue

            // Ensure the weekday is followed by a word boundary or end
            val dayEnd = (lower.length - afterPrefix.length) + matchedEntry.key.length
            val afterDay = lower.getOrNull(dayEnd) ?: ' '
            if (afterDay.isLetterOrDigit() && afterDay != ' ') continue

            val timeMinutes = extractTimeMinutes(original, lower, isRu)
            val weekdayKey = matchedEntry.value
            val repeatRule = ReminderRepeatRule(
                type = RepeatType.WEEKLY,
                interval = 1,
                weekDays = setOf(weekdayKey.isoDayNumber()),
            )
            val reminderAt = computeNextWeekdayMillis(nowLocal, timeZone, weekdayKey, timeMinutes)
            val repeatDisplay = ChipDisplay.Repeat(RepeatKey.WEEKLY)
            val weekdayDisplay = ChipDisplay.Weekday(weekdayKey, timeMinutes)
            return ParsedDateToken(
                originalSubstring = original.substring(prefixRange.first, minOf(dayEnd, original.length)),
                startIndex = prefixRange.first,
                endIndex = minOf(dayEnd, original.length),
                display = ChipDisplay.Combined(repeatDisplay, weekdayDisplay),
                reminderAt = reminderAt,
                repeatRule = repeatRule,
                timeOfDayMinutes = timeMinutes,
            )
        }
        return null
    }

    // ─── Priority 2: Weekday ─────────────────────────────────────────────────

    private fun tryParseWeekday(
        original: String,
        lowerRu: String,
        lowerEn: String,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        // Try RU weekdays
        tryMatchWeekday(original, lowerRu, RuDateLexicon.weekdays, isRu = true, nowLocal, timeZone)
            ?.let { return it }
        // Try EN weekdays
        tryMatchWeekday(original, lowerEn, EnDateLexicon.weekdays, isRu = false, nowLocal, timeZone)
            ?.let { return it }
        return null
    }

    private fun tryMatchWeekday(
        original: String,
        lower: String,
        weekdays: Map<String, WeekdayKey>,
        isRu: Boolean,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        // Match longest phrase first (e.g. "понедельник" before "пн")
        for ((phrase, weekdayKey) in weekdays.entries.sortedByDescending { it.key.length }) {
            val range = findPhraseRange(lower, phrase) ?: continue
            val timeMinutes = extractTimeMinutes(original, lower, isRu)
            val reminderMillis = computeNextWeekdayMillis(nowLocal, timeZone, weekdayKey, timeMinutes)
            return ParsedDateToken(
                originalSubstring = original.substring(range),
                startIndex = range.first,
                endIndex = range.last + 1,
                display = ChipDisplay.Weekday(weekdayKey, timeMinutes),
                reminderAt = reminderMillis,
                repeatRule = null,
                timeOfDayMinutes = timeMinutes,
            )
        }
        return null
    }

    // ─── Priority 3: Relative day ─────────────────────────────────────────────

    private fun tryParseRelativeDay(
        original: String,
        lowerRu: String,
        lowerEn: String,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        // dayAfterTomorrow first (longer, avoids "завтра" shadow)
        data class RelativeEntry(val key: DayKey, val phrases: Set<String>, val isRu: Boolean)

        val entries = listOf(
            RelativeEntry(DayKey.DAY_AFTER_TOMORROW, RuDateLexicon.dayAfterTomorrow, true),
            RelativeEntry(DayKey.TOMORROW, RuDateLexicon.tomorrow, true),
            RelativeEntry(DayKey.TODAY, RuDateLexicon.today, true),
            RelativeEntry(DayKey.DAY_AFTER_TOMORROW, EnDateLexicon.dayAfterTomorrow, false),
            RelativeEntry(DayKey.TOMORROW, EnDateLexicon.tomorrow, false),
            RelativeEntry(DayKey.TODAY, EnDateLexicon.today, false),
        )

        for (entry in entries) {
            val lower = if (entry.isRu) lowerRu else lowerEn
            for (phrase in entry.phrases.sortedByDescending { it.length }) {
                val range = findPhraseRange(lower, phrase) ?: continue
                val timeMinutes = extractTimeMinutes(original, lower, entry.isRu)
                val effectiveTime = timeMinutes ?: DEFAULT_TIME_MINUTES
                val baseDateOffset = entry.key.daysOffset()
                val reminderMillis = if (baseDateOffset == 0) {
                    // TODAY: respect past-time → tomorrow rule
                    resolveTimeToday(nowLocal, timeZone, effectiveTime)
                } else {
                    val targetDate = nowLocal.date.plus(baseDateOffset, DateTimeUnit.DAY)
                    dateTimeToMillis(targetDate, effectiveTime, timeZone)
                }
                return ParsedDateToken(
                    originalSubstring = original.substring(range),
                    startIndex = range.first,
                    endIndex = range.last + 1,
                    display = ChipDisplay.RelativeDay(entry.key, timeMinutes),
                    reminderAt = reminderMillis,
                    repeatRule = null,
                    timeOfDayMinutes = timeMinutes,
                )
            }
        }
        return null
    }

    private fun DayKey.daysOffset() = when (this) {
        DayKey.TODAY -> 0
        DayKey.TOMORROW -> 1
        DayKey.DAY_AFTER_TOMORROW -> 2
    }

    // ─── Priority 4: Time-of-day word only ───────────────────────────────────

    private fun tryParseTimeOfDayWord(
        original: String,
        lowerRu: String,
        lowerEn: String,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        data class TodEntry(val words: Set<String>, val minutes: Int, val isRu: Boolean)

        val entries = listOf(
            TodEntry(RuDateLexicon.morningWords, RuDateLexicon.morningTime, true),
            TodEntry(RuDateLexicon.noonWords, RuDateLexicon.noonTime, true),
            TodEntry(RuDateLexicon.eveningWords, RuDateLexicon.eveningTime, true),
            TodEntry(RuDateLexicon.nightWords, RuDateLexicon.nightTime, true),
            TodEntry(EnDateLexicon.morningWords, EnDateLexicon.morningTime, false),
            TodEntry(EnDateLexicon.noonWords, EnDateLexicon.noonTime, false),
            TodEntry(EnDateLexicon.eveningWords, EnDateLexicon.eveningTime, false),
            TodEntry(EnDateLexicon.nightWords, EnDateLexicon.nightTime, false),
        )

        for (entry in entries) {
            val lower = if (entry.isRu) lowerRu else lowerEn
            for (word in entry.words.sortedByDescending { it.length }) {
                val range = findPhraseRange(lower, word) ?: continue
                val reminderMillis = resolveTimeToday(nowLocal, timeZone, entry.minutes)
                return ParsedDateToken(
                    originalSubstring = original.substring(range),
                    startIndex = range.first,
                    endIndex = range.last + 1,
                    display = ChipDisplay.AbsoluteTime(entry.minutes / 60, entry.minutes % 60),
                    reminderAt = reminderMillis,
                    repeatRule = null,
                    timeOfDayMinutes = entry.minutes,
                )
            }
        }
        return null
    }

    // ─── Priority 5: Relative offset ─────────────────────────────────────────

    private fun tryParseRelativeOffset(
        original: String,
        lowerRu: String,
        lowerEn: String,
        now: Long,
    ): ParsedDateToken? {
        tryMatchOffset(original, lowerRu, RuDateLexicon.offsetPrepositions, RuDateLexicon.offsetUnits, now)
            ?.let { return it }
        tryMatchOffset(original, lowerEn, EnDateLexicon.offsetPrepositions, EnDateLexicon.offsetUnits, now)
            ?.let { return it }
        return null
    }

    private fun tryMatchOffset(
        original: String,
        lower: String,
        prepositions: Set<String>,
        units: Map<String, OffsetUnit>,
        now: Long,
    ): ParsedDateToken? {
        for (prep in prepositions) {
            val prepRange = findPhraseRange(lower, prep) ?: continue
            // Cursor after preposition, skipping whitespace
            var cursor = prepRange.last + 1
            while (cursor < lower.length && lower[cursor] == ' ') cursor++

            // Match a number at cursor
            val numberMatch = Regex("""^(\d+)""").find(lower.substring(cursor)) ?: continue
            val amount = numberMatch.groupValues[1].toIntOrNull() ?: continue
            cursor += numberMatch.value.length

            // Skip whitespace between number and unit
            while (cursor < lower.length && lower[cursor] == ' ') cursor++

            // Match a unit word at cursor (longest first)
            val matchedUnit = units.entries
                .sortedByDescending { it.key.length }
                .firstOrNull { (unitWord, _) ->
                    lower.startsWith(unitWord, cursor) &&
                        (cursor + unitWord.length >= lower.length || !lower[cursor + unitWord.length].isLetter())
                } ?: continue

            val (unitWord, offsetUnit) = matchedUnit
            val endIndex = cursor + unitWord.length
            val reminderMillis = now + offsetToMillis(amount, offsetUnit)

            return ParsedDateToken(
                originalSubstring = original.substring(prepRange.first, minOf(endIndex, original.length)),
                startIndex = prepRange.first,
                endIndex = minOf(endIndex, original.length),
                display = ChipDisplay.RelativeOffset(amount, offsetUnit),
                reminderAt = reminderMillis,
                repeatRule = null,
                timeOfDayMinutes = null,
            )
        }
        return null
    }

    private fun offsetToMillis(amount: Int, unit: OffsetUnit): Long = when (unit) {
        OffsetUnit.MINUTES -> amount * 60_000L
        OffsetUnit.HOURS -> amount * 3_600_000L
        OffsetUnit.DAYS -> amount * 86_400_000L
        OffsetUnit.WEEKS -> amount * 7 * 86_400_000L
    }

    // ─── Priority 6: Bare clock time ─────────────────────────────────────────

    private fun tryParseBareTime(
        original: String,
        lowerRu: String,
        lowerEn: String,
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
    ): ParsedDateToken? {
        // "HH:MM" format (most unambiguous)
        val clockMatch = CLOCK_REGEX.find(lowerRu) ?: CLOCK_REGEX.find(lowerEn)
        if (clockMatch != null) {
            val hour = clockMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = clockMatch.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            val totalMinutes = hour * 60 + minute
            val reminderMillis = resolveTimeToday(nowLocal, timeZone, totalMinutes)
            return ParsedDateToken(
                originalSubstring = original.substring(clockMatch.range),
                startIndex = clockMatch.range.first,
                endIndex = clockMatch.range.last + 1,
                display = ChipDisplay.AbsoluteTime(hour, minute),
                reminderAt = reminderMillis,
                repeatRule = null,
                timeOfDayMinutes = totalMinutes,
            )
        }

        // "9am" / "9pm" / "9 am" / "9 pm" format
        val amPmMatch = BARE_HOUR_AMPM_REGEX.find(lowerEn) ?: BARE_HOUR_AMPM_REGEX.find(lowerRu)
        if (amPmMatch != null) {
            val hour12 = amPmMatch.groupValues[1].toIntOrNull() ?: return null
            val amPm = amPmMatch.groupValues[2].lowercase()
            if (hour12 !in 1..12) return null
            val hour24 = when {
                amPm == "am" && hour12 == 12 -> 0
                amPm == "am" -> hour12
                amPm == "pm" && hour12 == 12 -> 12
                else -> hour12 + 12
            }
            val totalMinutes = hour24 * 60
            val reminderMillis = resolveTimeToday(nowLocal, timeZone, totalMinutes)
            return ParsedDateToken(
                originalSubstring = original.substring(amPmMatch.range),
                startIndex = amPmMatch.range.first,
                endIndex = amPmMatch.range.last + 1,
                display = ChipDisplay.AbsoluteTime(hour24, 0),
                reminderAt = reminderMillis,
                repeatRule = null,
                timeOfDayMinutes = totalMinutes,
            )
        }

        return null
    }

    // ─── Time extraction helper ───────────────────────────────────────────────

    /**
     * Extracts explicit time from a phrase that also contains day/weekday words.
     * Returns minutes-since-midnight or null if no explicit time found.
     *
     * Handles: "HH:MM", "Xam"/"Xpm", "в X", "X утра/вечера", time-of-day words.
     */
    private fun extractTimeMinutes(original: String, lower: String, isRu: Boolean): Int? {
        // "HH:MM"
        CLOCK_REGEX.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            if (h in 0..23 && min in 0..59) return h * 60 + min
        }

        // "Xam" / "Xpm"
        BARE_HOUR_AMPM_REGEX.find(lower)?.let { m ->
            val h12 = m.groupValues[1].toIntOrNull() ?: return@let
            val amPm = m.groupValues[2].lowercase()
            if (h12 in 1..12) {
                val h24 = when {
                    amPm == "am" && h12 == 12 -> 0
                    amPm == "am" -> h12
                    amPm == "pm" && h12 == 12 -> 12
                    else -> h12 + 12
                }
                return h24 * 60
            }
        }

        if (isRu) {
            // RU: bare hour with qualifier ("9 утра"), or after time preposition ("в 12" → 12:00 literal)
            val words = lower.split(Regex("""\s+"""))
            for (i in words.indices) {
                val word = words[i]
                val hourCandidate = word.toIntOrNull()
                if (hourCandidate != null && hourCandidate in 0..23) {
                    val next = words.getOrNull(i + 1) ?: ""
                    val prev = words.getOrNull(i - 1) ?: ""
                    val resolved = when {
                        next in RuDateLexicon.morningWords -> hourCandidate * 60
                        next in RuDateLexicon.eveningWords -> (hourCandidate + 12).coerceAtMost(23) * 60
                        next in RuDateLexicon.nightWords -> (hourCandidate + 12).coerceAtMost(23) * 60
                        next in RuDateLexicon.noonWords -> 12 * 60
                        prev in RuDateLexicon.timePrepositions -> hourCandidate * 60
                        else -> null
                    }
                    if (resolved != null) return resolved
                }
            }
        } else {
            // EN: "at 12" → 12:00 literal (0..23) | "9 morning" → 09:00 (1..12 with AM/PM heuristic)
            val words = lower.split(Regex("""\s+"""))
            for (i in words.indices) {
                val hourCandidate = words[i].toIntOrNull() ?: continue
                val next = words.getOrNull(i + 1) ?: ""
                val prev = words.getOrNull(i - 1) ?: ""
                if (prev in EnDateLexicon.timePrepositions && hourCandidate in 0..23) {
                    val resolved = when {
                        next in EnDateLexicon.morningWords -> hourCandidate * 60
                        next in EnDateLexicon.eveningWords -> (hourCandidate + 12).coerceAtMost(23) * 60
                        next in EnDateLexicon.nightWords -> (hourCandidate + 12).coerceAtMost(23) * 60
                        next in EnDateLexicon.noonWords -> 12 * 60
                        else -> hourCandidate * 60
                    }
                    return resolved
                }
                if (hourCandidate in 1..12) {
                    val resolved = when {
                        next in EnDateLexicon.morningWords -> hourCandidate * 60
                        next in EnDateLexicon.eveningWords -> (hourCandidate + 12) * 60
                        next in EnDateLexicon.nightWords -> (hourCandidate + 12) * 60
                        next in EnDateLexicon.noonWords -> 12 * 60
                        else -> null
                    }
                    if (resolved != null) return resolved
                }
            }
        }

        // Bare time-of-day words (no explicit hour)
        if (isRu) {
            if (RuDateLexicon.morningWords.any { containsWordBoundary(lower, it) }) return RuDateLexicon.morningTime
            if (RuDateLexicon.eveningWords.any { containsWordBoundary(lower, it) }) return RuDateLexicon.eveningTime
            if (RuDateLexicon.nightWords.any { containsWordBoundary(lower, it) }) return RuDateLexicon.nightTime
            if (RuDateLexicon.noonWords.any { containsWordBoundary(lower, it) }) return RuDateLexicon.noonTime
        } else {
            if (EnDateLexicon.morningWords.any { containsWordBoundary(lower, it) }) return EnDateLexicon.morningTime
            if (EnDateLexicon.eveningWords.any { containsWordBoundary(lower, it) }) return EnDateLexicon.eveningTime
            if (EnDateLexicon.nightWords.any { containsWordBoundary(lower, it) }) return EnDateLexicon.nightTime
            if (EnDateLexicon.noonWords.any { containsWordBoundary(lower, it) }) return EnDateLexicon.noonTime
        }

        return null
    }

    // ─── Date arithmetic helpers ──────────────────────────────────────────────

    /**
     * Compute the next occurrence of [weekdayKey] from [nowLocal].
     * If today is that weekday and [timeMinutes] is still in the future → today.
     * Otherwise → next occurrence (minimum 1 day ahead if time already passed).
     */
    private fun computeNextWeekdayMillis(
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
        weekdayKey: WeekdayKey,
        timeMinutes: Int?,
    ): Long {
        val effectiveTime = timeMinutes ?: DEFAULT_TIME_MINUTES
        val targetIso = weekdayKey.isoDayNumber()
        val todayIso = nowLocal.dayOfWeek.isoDayNumber
        var daysAhead = (targetIso - todayIso + 7) % 7

        // Same weekday: if time is still in the future today → 0 days; else 7 days
        if (daysAhead == 0) {
            val nowMinutes = nowLocal.hour * 60 + nowLocal.minute
            daysAhead = if (effectiveTime > nowMinutes) 0 else 7
        }

        val targetDate = nowLocal.date.plus(daysAhead, DateTimeUnit.DAY)
        return dateTimeToMillis(targetDate, effectiveTime, timeZone)
    }

    /**
     * Resolves a time-of-day (minutes since midnight) to epoch millis.
     * If that time today is in the past → schedule for tomorrow.
     */
    private fun resolveTimeToday(
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
        timeMinutes: Int,
    ): Long {
        val nowMinutes = nowLocal.hour * 60 + nowLocal.minute
        val date = if (timeMinutes > nowMinutes) nowLocal.date
        else nowLocal.date.plus(1, DateTimeUnit.DAY)
        return dateTimeToMillis(date, timeMinutes, timeZone)
    }

    /**
     * Computes the first trigger for a repeat rule starting from [nowLocal].
     * Uses [timeMinutes] if given, otherwise [DEFAULT_TIME_MINUTES].
     * The trigger is always in the future (>= now + 1 minute).
     */
    private fun computeFirstRepeatTrigger(
        nowLocal: LocalDateTime,
        timeZone: TimeZone,
        timeMinutes: Int?,
    ): Long {
        val effectiveTime = timeMinutes ?: DEFAULT_TIME_MINUTES
        return resolveTimeToday(nowLocal, timeZone, effectiveTime)
    }

    private fun dateTimeToMillis(date: LocalDate, totalMinutes: Int, timeZone: TimeZone): Long {
        val time = LocalTime(totalMinutes / 60, totalMinutes % 60)
        return LocalDateTime(date, time).toInstant(timeZone).toEpochMilliseconds()
    }

    // ─── Repeat rule builder ──────────────────────────────────────────────────

    private fun toRepeatRule(key: RepeatKey): ReminderRepeatRule = when (key) {
        RepeatKey.DAILY -> ReminderRepeatRule(type = RepeatType.DAILY)
        RepeatKey.WEEKLY -> ReminderRepeatRule(type = RepeatType.WEEKLY)
        RepeatKey.MONTHLY -> ReminderRepeatRule(type = RepeatType.MONTHLY)
        RepeatKey.YEARLY -> ReminderRepeatRule(type = RepeatType.YEARLY)
        RepeatKey.WEEKDAYS -> ReminderRepeatRule(
            type = RepeatType.WEEKLY,
            weekDays = weekdaysIsoSet(),
        )
    }

    private fun buildRepeatChipDisplay(key: RepeatKey, timeMinutes: Int?): ChipDisplay {
        val repeatDisplay = ChipDisplay.Repeat(key)
        return if (timeMinutes != null) {
            ChipDisplay.Combined(repeatDisplay, ChipDisplay.AbsoluteTime(timeMinutes / 60, timeMinutes % 60))
        } else {
            repeatDisplay
        }
    }

    // ─── Locale-safe lowercase ────────────────────────────────────────────────

    /**
     * Lowercase using RU locale rules.
     * KMP does not expose java.util.Locale directly; we use the standard
     * Kotlin lowercase() which is Unicode-correct for Cyrillic on all platforms.
     * For EN/TR safety, the EN branch uses explicit ASCII fold.
     */
    private fun normalizeToLowerRu(input: String): String =
        input.lowercase() // Cyrillic lowercase is unambiguous across all Unicode locales

    /**
     * Lowercase using EN locale rules (ASCII fold — safe even in TR locale).
     */
    private fun normalizeToLowerEn(input: String): String =
        buildString(input.length) {
            for (ch in input) {
                append(
                    when (ch) {
                        in 'A'..'Z' -> ch + 32 // ASCII-only fold, avoids Turkish İ→i issue
                        else -> ch
                    }
                )
            }
        }
}
