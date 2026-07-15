package com.antonchuraev.homesearchchecklist.feature.aichat.api.format

/**
 * Single source of localized date/time copy for every chat surface.
 *
 * Both the preview renderer (what the chat *offers* to do) and the tool-call dispatcher (what it
 * *reports* it did) render the same reminder timestamp in the same turn — two independent
 * formatters there would drift and show the user two different spellings of one moment. Hence one
 * interface in api/, one impl, injected into both.
 *
 * `suspend` because the strings come from Compose Resources (`getString`), which is suspending;
 * every call site is already inside a coroutine.
 */
interface ChatDateFormatter {
    /** Full moment, e.g. "Monday, July 20 at 09:00" / "Понедельник, 20 июля в 09:00". */
    suspend fun formatDateTime(epochMs: Long): String

    /** Calendar day only, e.g. "Monday, July 20" / "Понедельник, 20 июля". */
    suspend fun formatDay(epochMs: Long): String
}
