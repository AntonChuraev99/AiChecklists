package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Network client for the agentic chat bridge (chat_agent CF). One call == one round
 * of the stateless ping-pong loop. Charges 3 credits server-side only on the first
 * round of a turn (transcript has no tool turn yet).
 */
interface ChatAgentApiService {
    /**
     * @param contextChecklistName Name of the checklist the user currently has open
     *   (e.g. the dock was launched from [ChecklistDetailScreen]). When non-null it is sent
     *   to the server as a top-level `context_checklist.name` field so the agent biases
     *   ambiguous, list-less commands toward this checklist instead of guessing the first one.
     *   Null → omit the field entirely (server treats absence as "home screen, no focus").
     */
    suspend fun step(
        userId: String,
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,   // reuse type from ChatCompletionApiService.kt
        contextChecklistName: String? = null,
    ): AgentStepResult
}

sealed interface AgentStepResult {
    data class ToolCalls(val calls: List<AgentToolCall>, val creditsRemaining: Int) : AgentStepResult
    data class Final(val content: String, val creditsRemaining: Int) : AgentStepResult
    data object InsufficientCredits : AgentStepResult   // 402
    data object NetworkError : AgentStepResult           // timeout / connection / parse failure
    data object ServiceError : AgentStepResult           // non-402 error, success=false, or malformed body
}
