package com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Convert a [ReminderRepeatRule] to an iCalendar (RFC 5545) RRULE body — without the
 * leading "RRULE:" prefix, e.g. "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=10".
 *
 * Used to pre-fill recurrence when exporting a recurring reminder to a calendar event.
 * Consumers add the prefix as the target requires: Android's `Events.RRULE` extra takes
 * the bare body; the Google Calendar template URL uses `recur=RRULE:<body>`.
 *
 * Mapping (weekDays are ISO day numbers 1=Mon..7=Sun — see [ReminderRepeatRule.weekDays]):
 *  - type      -> FREQ=DAILY|WEEKLY|MONTHLY|YEARLY
 *  - interval  -> INTERVAL=n (omitted when n == 1)
 *  - weekDays  -> BYDAY=MO,TU,... (WEEKLY only, when non-empty)
 *  - AfterCount-> COUNT=n
 *  - UntilDate -> UNTIL=yyyyMMddTHHmmssZ (UTC)
 */
fun ReminderRepeatRule.toRRule(): String {
    val parts = mutableListOf<String>()
    parts += "FREQ=${type.toFreq()}"
    if (interval > 1) parts += "INTERVAL=$interval"

    if (type == RepeatType.WEEKLY) {
        weekDays?.takeIf { it.isNotEmpty() }?.let { days ->
            parts += "BYDAY=" + days.sorted().joinToString(",") { it.toByDay() }
        }
    }

    when (val end = endCondition) {
        is RepeatEndCondition.AfterCount -> parts += "COUNT=${end.maxCount}"
        is RepeatEndCondition.UntilDate -> parts += "UNTIL=${formatUtcBasic(end.dateMillis)}"
        RepeatEndCondition.Never -> Unit
    }

    return parts.joinToString(";")
}

private fun RepeatType.toFreq(): String = when (this) {
    RepeatType.DAILY -> "DAILY"
    RepeatType.WEEKLY -> "WEEKLY"
    RepeatType.MONTHLY -> "MONTHLY"
    RepeatType.YEARLY -> "YEARLY"
}

/** ISO day number (1=Mon..7=Sun) -> iCalendar BYDAY code. */
private fun Int.toByDay(): String = when (this) {
    1 -> "MO"
    2 -> "TU"
    3 -> "WE"
    4 -> "TH"
    5 -> "FR"
    6 -> "SA"
    7 -> "SU"
    else -> "MO"
}

/**
 * Format epoch millis as iCalendar UTC basic format `yyyyMMddTHHmmssZ`.
 * Shared by RRULE `UNTIL` and the Google Calendar template URL `dates=` parameter.
 */
internal fun formatUtcBasic(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
    val year = dt.year.toString().padStart(4, '0')
    val month = dt.month.number.toString().padStart(2, '0')
    val day = dt.day.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    val second = dt.second.toString().padStart(2, '0')
    return "$year$month${day}T$hour$minute${second}Z"
}
