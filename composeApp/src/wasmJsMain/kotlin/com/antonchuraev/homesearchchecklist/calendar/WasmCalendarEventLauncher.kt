package com.antonchuraev.homesearchchecklist.calendar

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEvent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.buildGoogleCalendarTemplateUrl
import kotlinx.browser.window

/**
 * Web (wasmJs) one-way calendar export: opens a Google Calendar "template" URL in a new
 * tab via [window.open]. Requires no OAuth — the URL pre-fills the event-creation form.
 *
 * Popup safety: [addEvent] is fully synchronous (no suspend/await). `window.open` only
 * succeeds inside the user-gesture call stack, so the caller must invoke this directly
 * from the click/intent handler — see [CalendarEventLauncher] KDoc.
 *
 * Note on the signature: the wasmJs `org.w3c.dom.Window.open` is
 * `fun open(url: String, target: String, features: String): Window?` — unlike the legacy
 * JS stdlib it has **no** `definedExternally` defaults, so all three args are required.
 * The `""` features string keeps default browser behavior (new tab honoring user prefs).
 * A null return means the popup was blocked → report `false` so the caller can snackbar.
 */
class WasmCalendarEventLauncher : CalendarEventLauncher {
    override fun addEvent(event: CalendarEvent): Boolean {
        val url = buildGoogleCalendarTemplateUrl(event)
        val opened = window.open(url, "_blank", "")
        return opened != null
    }
}
