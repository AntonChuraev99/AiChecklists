package com.antonchuraev.homesearchchecklist.activation

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Day a reminder option falls on — picks `activation_sheet_today` vs `activation_sheet_tomorrow`. */
enum class ReminderOptionDay { Today, Tomorrow }

/** One radio row of the activation reminder sheet. */
data class ActivationReminderOption(
    val day: ReminderOptionDay,
    val hour: Int,
    val minute: Int,
    /** Epoch millis the reminder is scheduled at when this option is confirmed. */
    val triggerAtMs: Long,
)

private const val EVENING_HOUR = 20
private const val MORNING_HOUR = 9

/**
 * The two reminder times the sheet offers; the FIRST is the default selection.
 *  - before 20:00 → [Today 20:00, Tomorrow 09:00]
 *  - from 20:00   → [Tomorrow 09:00, Tomorrow 20:00]
 * Pure (epoch millis + zone in, values out) so it is unit-testable.
 */
fun activationReminderOptions(
    nowEpochMs: Long,
    timeZone: TimeZone,
): List<ActivationReminderOption> {
    val nowLocal = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone)
    val today = nowLocal.date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    fun option(day: ReminderOptionDay, date: LocalDate, hour: Int) = ActivationReminderOption(
        day = day,
        hour = hour,
        minute = 0,
        triggerAtMs = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, 0)
            .toInstant(timeZone)
            .toEpochMilliseconds(),
    )
    return if (nowLocal.hour < EVENING_HOUR) {
        listOf(
            option(ReminderOptionDay.Today, today, EVENING_HOUR),
            option(ReminderOptionDay.Tomorrow, tomorrow, MORNING_HOUR),
        )
    } else {
        listOf(
            option(ReminderOptionDay.Tomorrow, tomorrow, MORNING_HOUR),
            option(ReminderOptionDay.Tomorrow, tomorrow, EVENING_HOUR),
        )
    }
}

/**
 * "HH:mm", zero-padded 24h — the same shape `DueLabelFormatter.timeOfDay` gives the labels
 * [AppDueChip][com.antonchuraev.homesearchchecklist.desingsystem.components.AppDueChip] shows.
 * The app has no 12h mode by convention, so the sheet and its snackbar match every other time
 * the user sees.
 */
fun formatReminderClock(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
