package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Entry-point for AI chat classification.
 *
 * In Phase A MVP this delegates exclusively to [LocalIntentRouter] (Layer 1).
 * Phase B will add Layer 2 (cloud classifier) fallback when confidence < 0.6.
 *
 * // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
 */
interface AiChatRepository {
    suspend fun classify(input: String, locale: ChatLocale): IntentClassification
}
