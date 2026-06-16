package com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule

/** Default exported-event duration (60 minutes) — reminders have no inherent length. */
private const val DEFAULT_DURATION_MILLIS = 60L * 60L * 1000L

/**
 * Build a [CalendarEvent] from a reminder's title, start time and optional repeat rule.
 *
 * With a non-null [startMillis] the event spans [DEFAULT_DURATION_MILLIS]; a null [startMillis]
 * produces an event with no pre-filled time (the user picks it in the calendar app). A non-null
 * [rule] is mapped to an RRULE body via [toRRule]. Pure and synchronous — safe to call inside a
 * Web user-gesture stack (see [CalendarEventLauncher]).
 */
fun buildCalendarEvent(
    title: String,
    startMillis: Long? = null,
    rule: ReminderRepeatRule? = null,
    description: String? = null,
): CalendarEvent = CalendarEvent(
    title = title,
    description = description,
    startMillis = startMillis,
    endMillis = startMillis?.let { it + DEFAULT_DURATION_MILLIS },
    rrule = rule?.toRRule(),
)
