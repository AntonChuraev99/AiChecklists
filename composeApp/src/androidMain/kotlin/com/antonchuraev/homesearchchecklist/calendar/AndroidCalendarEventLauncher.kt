package com.antonchuraev.homesearchchecklist.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEvent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher

/**
 * Android [CalendarEventLauncher]: opens the system calendar's "new event" editor pre-filled
 * with the exported reminder via [Intent.ACTION_INSERT] on [CalendarContract.Events.CONTENT_URI].
 *
 * One-way export only — the app never reads events back. The user reviews and saves the event in
 * their calendar app, so no calendar write permission is needed (ACTION_INSERT hands off to the
 * provider's own UI). [addEvent] is synchronous, satisfying the [CalendarEventLauncher] contract
 * (shared with the Web popup-safety requirement).
 */
class AndroidCalendarEventLauncher(
    private val context: Context,
    private val logger: AppLogger,
) : CalendarEventLauncher {
    override fun addEvent(event: CalendarEvent): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            event.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            // No pre-filled time when null — the calendar editor opens with its own default
            // and the user picks the time.
            event.startMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            event.endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            event.rrule?.let { putExtra(CalendarContract.Events.RRULE, it) }
            // Launched from a non-Activity context (Koin singleton holds the app Context).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            logger.error("Calendar", "No calendar app for ACTION_INSERT: ${e.message}", e)
            false
        }
    }
}
