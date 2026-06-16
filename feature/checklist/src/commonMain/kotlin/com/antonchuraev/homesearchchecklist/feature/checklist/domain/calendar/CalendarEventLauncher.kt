package com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar

/**
 * Launches the platform "add event to calendar" flow for a one-way export.
 *
 * Implementations:
 *  - Android: [android.content.Intent.ACTION_INSERT] into the system calendar provider.
 *  - Web (wasmJs): `window.open()` with a Google Calendar template URL.
 *  - iOS: stub returning false until EventKit is wired (iOS not released yet).
 *
 * IMPORTANT — Web popup safety: `window.open` only succeeds inside a **synchronous
 * user-gesture call stack**. Callers MUST invoke [addEvent] synchronously from the
 * click/intent handler — never after a suspend/await — or the browser blocks the
 * popup. Build the [CalendarEvent] from already-loaded state (no suspend calls) and
 * emit any snackbar feedback *after* this returns.
 *
 * @return true if the calendar flow was launched; false if it could not be (no
 *   calendar app, popup blocked, or unsupported platform). Callers surface a
 *   snackbar when false — never fail silently.
 */
interface CalendarEventLauncher {
    fun addEvent(event: CalendarEvent): Boolean
}
