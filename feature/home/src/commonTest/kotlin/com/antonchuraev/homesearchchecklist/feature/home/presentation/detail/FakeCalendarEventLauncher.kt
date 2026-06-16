package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEvent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher

/**
 * No-op [CalendarEventLauncher] test double. These ViewModel unit tests don't exercise the
 * calendar-export flow; this exists so `createViewModel` can satisfy the constructor parameter
 * added by the Google Calendar feature. Returns true (launch "succeeded") to avoid the
 * not-launched snackbar path on the rare test that touches it.
 */
internal class FakeCalendarEventLauncher : CalendarEventLauncher {
    override fun addEvent(event: CalendarEvent): Boolean = true
}
