package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatScreenSnapshot
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
     * @param screenSnapshot What the user is LOOKING AT, for the surfaces whose content
     *   [checklistsSummary] structurally cannot carry (the system Inbox, the day). Sent as
     *   top-level `context_screen`. Additive + nullable: `explicitNulls = false` drops the key, so
     *   the control arm, every non-tab route and every older server see today's exact payload.
     *   Never sent together with a `context_checklist` — the client gates the two apart.
     * @param requestId Idempotency key for the turn's credit reservation, sent as top-level
     *   `request_id`. It MUST be stable across every round AND every transport retry of ONE
     *   turn, and MUST differ between turns — the server dedups on `{user_id}__{request_id}`,
     *   so a reused id reads as a replay and the turn is never charged. Null → the server's
     *   legacy non-deduped reserve (same cost), which is what store clients still use.
     * @param responseLanguage BCP-47 primary subtag ("en", "es", …) the user explicitly pinned for
     *   AI replies, forwarded as top-level `response_language`. `null` (the default) is Auto — the
     *   server matches the message language. Additive: null is omitted from the payload
     *   (explicitNulls=false), so the legacy request stays byte-identical.
     */
    suspend fun step(
        userId: String,
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,   // reuse type from ChatCompletionApiService.kt
        contextChecklistName: String? = null,
        screenSnapshot: ChatScreenSnapshot? = null,
        requestId: String? = null,
        responseLanguage: String? = null,
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
    data object ServiceError : AgentStepResult           // 5xx, success=false, or malformed body

    /**
     * HTTP 400 — the server REFUSED this payload.
     *
     * Split off [ServiceError] because the two need opposite answers. A 5xx is transient, so
     * "try again in a moment" is good advice; a 400 is deterministic — the identical request is
     * refused forever, so the same copy plus a Retry chip is a loop the user cannot win. Until
     * this variant existed, a rejected turn read as
     * "The AI service isn't responding right now. Please try again in a moment." — untrue on both
     * halves: the service answered, and trying again changes nothing.
     *
     * ### Why one variant is enough to name a cause
     *
     * `chat_agent` answers 400 from six places (`main.py`), and five are unreachable from this
     * client, because the shape of the request is built here rather than typed by the user:
     * `user_id` is guarded in [AiChatRepository.agentStep], the transcript is never empty on a
     * send, entries are constructed as objects over a fixed role vocabulary, and
     * `AgentTranscriptWindow` caps entries at 30 + 10 live rounds against the server's 60. The
     * one cap the client cannot pre-empt is the total-chars ceiling: the window deliberately
     * keeps the newest turn whole even when that turn alone busts it, because dropping the
     * message the user just typed would be worse than a big request. A single long paste is
     * therefore what produces this in production, and "too long" is the honest thing to say.
     *
     * [reason] is the server's own `error` string, carried for the LOG only — never rendered
     * (it is English server prose; the UI speaks the user's language). It is what keeps the
     * paragraph above falsifiable: a 400 arriving for some other cause says so in Crashlytics
     * instead of hiding behind copy that already fits.
     */
    data class InvalidRequest(val reason: String?) : AgentStepResult
}
