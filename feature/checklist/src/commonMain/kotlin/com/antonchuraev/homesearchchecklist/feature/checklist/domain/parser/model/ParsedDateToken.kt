package com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule

/**
 * Result of parsing a natural-language date/time/repeat phrase from user input.
 *
 * The parser returns a single best-match token. The caller can:
 * - Strip [originalSubstring] from the input text
 * - Show [display] as a chip label (resolved to localized string in the UI layer)
 * - Apply [reminderAt] / [repeatRule] / [timeOfDayMinutes] to the reminder system
 */
data class ParsedDateToken(
    /** The substring of the original input that was matched (for stripping). */
    val originalSubstring: String,
    val startIndex: Int,
    val endIndex: Int,

    /**
     * Structured display key for the UI chip. The UI layer maps this to a localized
     * string via stringResource() — no raw RU/EN strings are stored in domain.
     */
    val display: ChipDisplay,

    /**
     * Epoch millis (UTC) for the one-shot reminder trigger, or null if this token
     * describes only a repeat rule (no concrete reminder time).
     */
    val reminderAt: Long?,

    /**
     * The repeat schedule, or null for one-shot / time-only tokens.
     */
    val repeatRule: ReminderRepeatRule?,

    /**
     * Minutes since midnight (0..1439) only when the user explicitly specified a time.
     * Null when a default time was inferred (e.g. "завтра" without a time phrase).
     */
    val timeOfDayMinutes: Int?,
)

// ─── Chip display hierarchy ───────────────────────────────────────────────────

/**
 * Structured key that describes the chip label. The UI layer renders this via
 * stringResource() using the enum values as message keys — no locale-specific text
 * lives in this domain model.
 */
sealed class ChipDisplay {

    /** e.g. "Завтра", "Сегодня 07:00" */
    data class RelativeDay(val dayKey: DayKey, val timeMinutes: Int?) : ChipDisplay()

    /** e.g. "Пн 09:00" */
    data class Weekday(val dayKey: WeekdayKey, val timeMinutes: Int?) : ChipDisplay()

    /** e.g. "Через 2 ч" */
    data class RelativeOffset(val amount: Int, val unit: OffsetUnit) : ChipDisplay()

    /** e.g. "15:30" */
    data class AbsoluteTime(val hour: Int, val minute: Int) : ChipDisplay()

    /** e.g. "Ежедневно" */
    data class Repeat(val repeatKey: RepeatKey) : ChipDisplay()

    /** A repeat combined with a time, e.g. "Каждый Пн 07:00" */
    data class Combined(val first: ChipDisplay, val second: ChipDisplay) : ChipDisplay()
}

enum class DayKey { TODAY, TOMORROW, DAY_AFTER_TOMORROW }

enum class WeekdayKey { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

enum class OffsetUnit { MINUTES, HOURS, DAYS, WEEKS }

enum class RepeatKey { DAILY, WEEKLY, MONTHLY, YEARLY, WEEKDAYS }
