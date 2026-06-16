package com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar

/**
 * Platform-agnostic description of a calendar event exported one-way to the device
 * calendar (Android) or to Google Calendar via a template URL (Web).
 *
 * This is an export payload only — the app does not read events back or sync changes.
 *
 * @property title event title (checklist name or item text)
 * @property description optional event body
 * @property startMillis event start (epoch millis), or null to let the user pick the time
 *   in the calendar app (no pre-filled time)
 * @property endMillis event end (epoch millis), or null when [startMillis] is null
 * @property rrule iCalendar RRULE body (without the "RRULE:" prefix) for a recurring
 *   event, e.g. "FREQ=WEEKLY;BYDAY=MO,WE", or null for a one-shot event.
 */
data class CalendarEvent(
    val title: String,
    val description: String? = null,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val rrule: String? = null,
)
