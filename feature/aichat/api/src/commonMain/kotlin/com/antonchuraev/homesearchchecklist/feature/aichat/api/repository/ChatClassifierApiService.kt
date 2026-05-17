package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Network client for the Layer 2 cloud intent classifier.
 *
 * Spends 1 AI credit per successful classification. Called only when
 * [AiChatRepository.classify] produces confidence < [LAYER_1_CONFIDENCE_THRESHOLD].
 */
interface ChatClassifierApiService {
    suspend fun classify(
        userId: String,
        text: String,
        locale: ChatLocale,
    ): RemoteClassificationResult
}

/**
 * Result of a remote classification call.
 *
 * [Success] carries a pre-built [toolCall] so the repository does not need to
 * re-run entity extraction from raw text (Gemini already extracted entities server-side).
 * [toolCall] may be null for free-form / unknown intents.
 */
sealed interface RemoteClassificationResult {
    data class Success(
        val intent: ChatIntent,
        val toolCall: ToolCall?,
        val confidence: Float,
        val creditsRemaining: Int,
    ) : RemoteClassificationResult

    /** Server returned 402 — user has no AI credits left. */
    data object InsufficientCredits : RemoteClassificationResult

    /** Connection failure, timeout, or any transient network error. */
    data object NetworkError : RemoteClassificationResult

    /** Server returned a non-402 error (5xx, Gemini failure, parse error). */
    data object ServiceError : RemoteClassificationResult
}
