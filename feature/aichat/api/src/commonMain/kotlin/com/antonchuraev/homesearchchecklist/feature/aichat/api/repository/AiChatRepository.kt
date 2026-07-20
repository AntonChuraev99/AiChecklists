package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Entry-point for AI chat classification and Layer 3 full-chat completion.
 *
 * Classification flow:
 *   Layer 2 (cloud classifier, 1 credit) → Layer 3 (completion, 3 credits)
 *
 * **Layer 1 (local parser, 0 credits) is disconnected** — decision 2026-07-15
 * (`docs/decisions/2026-07-15-remove-ai-chat-layer1.md`). Every message starts at Layer 2.
 * The parser itself is a parked asset, not deleted: bringing it back is a revert of the
 * disconnect commit, so do not write a replacement.
 *
 * [classify] handles Layer 2 and returns [IntentClassification] whose [intent] may be
 * [ChatIntent.FreeForm]. When the ViewModel sees FreeForm it calls [completeFreeForm]
 * to invoke Layer 3 without going through another classify() call.
 */
interface AiChatRepository {
    /**
     * Classifies [input] and returns an [IntentClassification].
     *
     * Regardless of [skipLayer1], the request goes to Layer 2 and — when Layer 2 is vague,
     * unreachable, or the user is not registered yet — escalates to Layer 3:
     *   - Vague Layer 2 result (FreeForm / Unknown) → `IntentClassification(FreeForm, 1.0f, FullChat, null)`
     *     so the caller can escalate to Layer 3 via the agent loop.
     *   - InsufficientCredits (HTTP 402) → `IntentClassification(ChatIntent.InsufficientCredits,
     *     1.0f, Classifier, null)`. The refusal is carried in the intent TYPE so the caller can
     *     tell "out of credits" from "not understood" and answer with the paywall CTA. It must
     *     NOT be flattened into `Unknown` (the app then blames the user's phrasing for a billing
     *     state) nor escalated to Layer 3 (bills 3 credits to a wallet that just refused 1).
     *   - Network / service error or blank userId → `IntentClassification(FreeForm, 1.0f, FullChat, null)`;
     *     Layer 3 surfaces the failure as a visible error reply. There is no local fallback left
     *     to degrade to, so chat no longer works offline.
     *
     * @param input Raw user text to classify.
     * @param locale Language hint for the classifier.
     * @param skipLayer1 Historical name, kept as the anchor for re-routing Layer 1 (see the ADR
     *   above): with Layer 1 disconnected there is no local router left to skip, so the flag now
     *   only controls the **Deep Thinking bypass**.
     *   - `false` (default) → Deep Thinking ON routes straight to Layer 3, skipping Layer 2 so an
     *     open question costs 3 credits rather than 4.
     *   - `true` → the Deep Thinking toggle is ignored and the request always starts at Layer 2.
     *     Used by the "I meant something else" reject flow: rejecting a preview is an explicit
     *     request to re-interpret a concrete command, not an opt-in to free-form chat.
     */
    suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean = false): IntentClassification

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

    /**
     * One round of the stateless agent loop. Delegates to the `chat_agent` Cloud Function.
     *
     * The caller is responsible for building and extending the [transcript] between rounds.
     * [checklistsSummary] provides checklist context (names, counts, and a bounded window of
     * recent item text) so the agent can answer "what did I add recently / find the task about X".
     * Item text leaves the device only on this Layer 3 path — same payload as [completeFreeForm].
     *
     * [contextChecklistName] is the name of the checklist the user currently has open
     * (the dock was launched from [ChecklistDetailScreen]). When non-null it is forwarded to
     * the agent so list-less commands ("add milk") bias toward this checklist. Null → no focus.
     *
     * [requestId] makes the turn's 3-credit reservation idempotent. The caller owns turn
     * boundaries, so the caller must mint it: one id per TURN, reused by every round of that
     * turn (and by any transport retry of a round), never reused by a later turn. The server
     * dedups on `{user_id}__{request_id}` — an id that leaks into the next turn makes that turn
     * free, which is the failure this parameter exists to make impossible to reach by accident.
     *
     * [responseLanguage] is the BCP-47 primary subtag the user explicitly pinned for AI replies, or
     * null for Auto (server decides from the message). Forwarded as `response_language`; additive.
     *
     * Returns [AgentStepResult] — caller decides whether to continue the loop
     * ([AgentStepResult.ToolCalls]) or stop ([AgentStepResult.Final] / errors).
     */
    suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String? = null,
        requestId: String? = null,
        responseLanguage: String? = null,
    ): AgentStepResult

    /**
     * Transcribes a voice recording to text via the `transcribe_audio` Cloud Function.
     * Spends 1 AI credit per successful call.
     *
     * Contract:
     *   - The audio file at [audioPath] is read, base64-encoded, and sent to the
     *     server together with [mimeType]. The local file is deleted after the call
     *     regardless of outcome — callers must not assume the file still exists.
     *   - [mimeType] is passed through to the server, which normalizes it for Gemini.
     *     Pass whatever the platform recorder reported (Android: "audio/m4a", browsers:
     *     "audio/webm;codecs=opus", Safari: "audio/mp4").
     *   - On [TranscriptionOutcome.Success] the [transcript] may be an empty string
     *     when the audio was silent or unintelligible; callers should handle this as
     *     a soft failure (snackbar, no input change).
     *
     * Used by ChatViewModel for the press-and-hold mic gesture in the chat input row.
     */
    suspend fun transcribeAudio(
        audioPath: String,
        mimeType: String,
        locale: ChatLocale,
    ): TranscriptionOutcome
}

/**
 * Outcome of [AiChatRepository.transcribeAudio].
 *
 * Modelled as a sealed result (rather than `Result<String>`) so callers can branch
 * on specific failure modes without inspecting exception types.
 */
sealed interface TranscriptionOutcome {
    /** Gemini returned a non-empty transcript. */
    data class Success(val transcript: String) : TranscriptionOutcome

    /** Gemini returned an empty transcript (silent / unintelligible audio). */
    data object EmptyTranscript : TranscriptionOutcome

    /** The audio file at the supplied path could not be read or was empty. */
    data object FileMissing : TranscriptionOutcome

    /** User has no AI credits left (server returned 402). */
    data object InsufficientCredits : TranscriptionOutcome

    /** Network failure, timeout, or any transient connectivity error. */
    data object NetworkError : TranscriptionOutcome

    /** Server returned a non-402 error (5xx, Gemini failure, audio too large). */
    data object ServiceError : TranscriptionOutcome
}
