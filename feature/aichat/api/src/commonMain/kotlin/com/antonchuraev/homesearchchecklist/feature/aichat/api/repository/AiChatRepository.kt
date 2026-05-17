package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Entry-point for AI chat classification and Layer 3 full-chat completion.
 *
 * Classification flow (Phase B+):
 *   Layer 1 (local, 0 credits) → Layer 2 (cloud classifier, 1 credit) → Layer 3 (completion, 3 credits)
 *
 * [classify] handles Layers 1–2 and returns [IntentClassification] whose [intent] may be
 * [ChatIntent.FreeForm]. When the ViewModel sees FreeForm it calls [completeFreeForm]
 * to invoke Layer 3 without going through another classify() call.
 */
interface AiChatRepository {
    suspend fun classify(input: String, locale: ChatLocale): IntentClassification

    /**
     * Layer 3: calls the cloud `chat_completion` function with full message history.
     *
     * [messages] is the current conversation history (latest N entries, sliding window
     * is server-side). [checklistsSummary] provides checklist context without item text.
     *
     * Returns [RemoteCompletionResult] — caller maps to assistant message or error SideEffect.
     */
    suspend fun completeFreeForm(
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult
}
