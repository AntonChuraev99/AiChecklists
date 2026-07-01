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

/**
 * One round of the agent loop as seen by the caller.
 *
 * The success-carrying variants ([ToolCalls] / [Final] / [Options]) also echo the
 * server-driven AI-model A/B assignment so the client can mirror it into analytics:
 *   - [modelVariant] — the arm the server bucketed this user into ("control" / "variant_b" /
 *     "override"). Deterministic per user (hash of user_id), identical across every flow, so it
 *     is safe to set as a sticky user-property. Null when the server didn't send it (experiment
 *     off / older server) — treat null as "unknown", never as a variant.
 *   - [modelId] — the actual Gemini model used this round (guardrail dimension).
 *   - [aiFlow] — which server flow produced the response ("chat_agent", "analyze", …).
 * These are dimensions only — they never affect client behaviour, limits, or credits.
 */
sealed interface AgentStepResult {
    data class ToolCalls(
        val calls: List<AgentToolCall>,
        val creditsRemaining: Int,
        val modelVariant: String? = null,
        val modelId: String? = null,
        val aiFlow: String? = null,
    ) : AgentStepResult
    data class Final(
        val content: String,
        val creditsRemaining: Int,
        val modelVariant: String? = null,
        val modelId: String? = null,
        val aiFlow: String? = null,
    ) : AgentStepResult

    /**
     * Terminal turn result (like [Final]) but with AI-generated tappable answer options.
     * The server returns `type:"options"` with a [prompt] question and 2-4 short [options]
     * labels. Tapping an option sends its label back as a fresh agent turn (forceAgent) —
     * it is NOT re-classified. Credits are already deducted server-side this round.
     */
    data class Options(
        val prompt: String,
        val options: List<String>,
        val creditsRemaining: Int,
        val modelVariant: String? = null,
        val modelId: String? = null,
        val aiFlow: String? = null,
    ) : AgentStepResult

    data object InsufficientCredits : AgentStepResult   // 402
    data object NetworkError : AgentStepResult           // timeout / connection / parse failure
    data object ServiceError : AgentStepResult           // non-402 error, success=false, or malformed body
}
