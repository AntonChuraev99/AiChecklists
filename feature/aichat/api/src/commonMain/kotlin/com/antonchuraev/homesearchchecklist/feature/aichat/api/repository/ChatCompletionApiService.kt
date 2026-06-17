package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Network client for the Layer 3 full chat completion endpoint.
 *
 * Spends 3 AI credits per successful call. Called only when Layer 2 classifier
 * returns [ChatIntent.FreeForm] (open-ended / conversational input).
 *
 * Privacy: [checklistsSummary] carries checklist names + item counts, plus the text of
 * a bounded window of recent items (see [ChecklistContext.recentItems]). This is sent so
 * the model can answer "what did I add recently / find the task about X" questions. The
 * server still does NOT read Firestore — the client supplies the snapshot. Item text only
 * leaves the device when the user invokes Layer 3 (Deep Thinking / free-form question).
 */
interface ChatCompletionApiService {
    suspend fun complete(
        userId: String,
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult
}

/**
 * Compact checklist summary sent to the Layer 3 endpoints (`chat_agent`, legacy `chat_completion`)
 * for context.
 *
 * [recentItems] is a bounded slice of the checklist's most-recently-added items (the tail of the
 * list — items are appended, so the tail is the freshest). It lets the model answer questions like
 * "what did I add last" or "find the task about milk" that pure name+count context cannot.
 *
 * Note: the domain [ChecklistItem] has no per-item timestamp, so "recency" here is positional
 * (list order), not wall-clock. Absolute "when did I add X" cannot be answered without a schema
 * change — see the build site in `ChatViewModel.buildChecklistsSummary`.
 */
data class ChecklistContext(
    val name: String,
    val totalItems: Int,
    val doneItems: Int,
    /** Tail-of-list slice of items (text + checked + 0-based position). Empty for folder-only lists. */
    val recentItems: List<ChecklistItemContext> = emptyList(),
)

/**
 * A single checklist item shared with Layer 3 for "recent items" context.
 *
 * @property text The item label (user content — privacy-sensitive; only sent on Layer 3).
 * @property checked Whether the item is completed.
 * @property position 0-based index of the item in its checklist. Used as a recency proxy because
 *   the domain model carries no add-timestamp; higher position ≈ added later.
 */
data class ChecklistItemContext(
    val text: String,
    val checked: Boolean,
    val position: Int,
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
