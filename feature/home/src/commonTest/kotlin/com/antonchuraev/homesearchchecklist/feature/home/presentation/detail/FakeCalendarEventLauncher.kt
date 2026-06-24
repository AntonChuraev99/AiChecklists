package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
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

/**
 * No-op [AppLogger] test double. Satisfies the `logger` constructor parameter on
 * [ChecklistDetailViewModel] (diagnostic logging for the attachment add/display path). These unit
 * tests don't assert on log output, so every method is a no-op.
 */
internal object NoOpAppLogger : AppLogger {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}
