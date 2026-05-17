package com.antonchuraev.homesearchchecklist.feature.aichat.api.parser

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification

/**
 * Classifies a user's natural-language input into an [IntentClassification]
 * without any network call or AI credits.
 *
 * Implementations must be thread-safe and non-blocking (pure text processing).
 * The result is always non-null — [com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent.Unknown]
 * is returned when no intent is recognized (confidence = 0f).
 */
interface LocalIntentRouter {
    suspend fun route(input: String, locale: ChatLocale): IntentClassification
}
