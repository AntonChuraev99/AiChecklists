package com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import kotlinx.datetime.TimeZone

/**
 * Parses a natural-language date/time/repeat phrase from user-typed text
 * without any network call or AI credits.
 *
 * Supported languages: Russian, English.
 * Returns a single best-match [ParsedDateToken], or null if no phrase is recognized.
 */
interface SmartDateParser {
    fun parse(input: String, now: Long, timeZone: TimeZone): ParsedDateToken?
}
