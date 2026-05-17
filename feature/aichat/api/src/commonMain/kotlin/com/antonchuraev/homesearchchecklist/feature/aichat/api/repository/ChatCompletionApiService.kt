package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Network client for the Layer 3 full chat completion endpoint.
 *
 * Spends 3 AI credits per successful call. Called only when Layer 2 classifier
 * returns [ChatIntent.FreeForm] (open-ended / conversational input).
 *
 * Privacy: [checklistsSummary] contains ONLY checklist names + item counts,
 * never item text — the server does not read Firestore checklist content.
 */
interface ChatCompletionApiService {
    suspend fun complete(
        userId: String,
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult
}

/** Compact checklist summary sent to the completion endpoint for context. */
data class ChecklistContext(
    val name: String,
    val totalItems: Int,
    val doneItems: Int,
)

/**
 * Result of a remote chat completion call.
 *
 * [Success] carries the AI-generated text and the updated credits balance.
 * Credits are refunded server-side on [ServiceError] — client only reads the new balance from [Success].
 */
sealed interface RemoteCompletionResult {
    data class Success(
        val content: String,
        val creditsRemaining: Int,
    ) : RemoteCompletionResult

    /** Server returned 402 — user has no AI credits left. */
    data object InsufficientCredits : RemoteCompletionResult

    /** Connection failure, timeout, or any transient network error. */
    data object NetworkError : RemoteCompletionResult

    /** Server returned a non-402 error (5xx, Gemini failure). Credits were refunded server-side. */
    data object ServiceError : RemoteCompletionResult
}
