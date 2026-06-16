package com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar

/**
 * Build a Google Calendar "template" URL that pre-fills the event-creation form.
 * Works cross-platform (browser + mobile), requires no OAuth.
 *
 * Shape:
 * ```
 * https://calendar.google.com/calendar/render?action=TEMPLATE
 *   &text=<title>&dates=<startUTC>/<endUTC>&details=<desc>&recur=RRULE:<body>
 * ```
 * Dates use the iCalendar UTC basic format `yyyyMMddTHHmmssZ` (see [formatUtcBasic]).
 * Used by the wasmJs [CalendarEventLauncher] which opens this URL with `window.open`.
 */
fun buildGoogleCalendarTemplateUrl(event: CalendarEvent): String {
    val params = StringBuilder("action=TEMPLATE")
    params.append("&text=").append(encodeUriComponent(event.title))
    // Omit dates entirely when no time is set — Google Calendar then opens the form with its
    // own default time for the user to pick.
    val start = event.startMillis
    val end = event.endMillis
    if (start != null && end != null) {
        params.append("&dates=")
            .append(formatUtcBasic(start))
            .append("/")
            .append(formatUtcBasic(end))
    }
    if (!event.description.isNullOrBlank()) {
        params.append("&details=").append(encodeUriComponent(event.description))
    }
    if (!event.rrule.isNullOrBlank()) {
        params.append("&recur=").append(encodeUriComponent("RRULE:${event.rrule}"))
    }
    return "https://calendar.google.com/calendar/render?$params"
}

private val HEX = "0123456789ABCDEF".toCharArray()

/**
 * Percent-encode a string for use in a URL query component (RFC 3986 unreserved set
 * `A-Za-z0-9-_.~` kept verbatim, everything else as UTF-8 %XX bytes).
 *
 * Pure-Kotlin so it works on every KMP target — `encodeURIComponent` is a JS-only API
 * and pulling in ktor here just for encoding would be overkill.
 */
internal fun encodeUriComponent(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size * 3)
    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        val unreserved = code in 0x30..0x39 || // 0-9
            code in 0x41..0x5A ||              // A-Z
            code in 0x61..0x7A ||              // a-z
            code == 0x2D || code == 0x2E || code == 0x5F || code == 0x7E // - . _ ~
        if (unreserved) {
            sb.append(code.toChar())
        } else {
            sb.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
        }
    }
    return sb.toString()
}
