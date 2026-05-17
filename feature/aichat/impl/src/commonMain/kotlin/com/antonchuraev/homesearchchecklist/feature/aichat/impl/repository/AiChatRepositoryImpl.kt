package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository

/**
 * Phase A implementation: delegates classification to [LocalIntentRouter] only.
 *
 * Phase B will add a cloud classifier fallback when [IntentClassification.confidence] < 0.6.
 * // Pending: docs/todos/2026-05-13-ai-chat-assistant.md
 */
internal class AiChatRepositoryImpl(
    private val router: LocalIntentRouter,
) : AiChatRepository {

    override suspend fun classify(input: String, locale: ChatLocale): IntentClassification {
        return router.route(input, locale)
    }
}
