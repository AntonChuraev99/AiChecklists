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
    /**
     * Classifies [input] and returns an [IntentClassification].
     *
     * @param input Raw user text to classify.
     * @param locale Language hint for the classifier.
     * @param skipLayer1 When `true`, Layer 1 (local router) is skipped entirely and the
     *   request goes straight to Layer 2 (cloud classifier, 1 credit). This is used in the
     *   "I meant something else" reject flow when the user rejects a Layer 1 preview and
     *   wants a higher-tier interpretation.
     *   - If Layer 2 returns a vague result (FreeForm / Unknown), the method returns
     *     `IntentClassification(FreeForm, 1.0f, FullChat, null)` so the caller can escalate
     *     to Layer 3 via [completeFreeForm].
     *   - If Layer 2 returns InsufficientCredits, returns `Unknown` so the caller can show
     *     the insufficient-credits snackbar.
     *   - On network / service error, returns `IntentClassification(FreeForm, 1.0f, FullChat, null)`
     *     and lets Layer 3 handle recovery.
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
     * Transcribes a voice recording (AAC m4a) to text via the `transcribe_audio`
     * Cloud Function. Spends 1 AI credit per successful call.
     *
     * Contract:
     *   - The audio file at [audioPath] is read, base64-encoded, and sent to the
     *     server. The local file is deleted after the call regardless of outcome —
     *     callers must not assume the file still exists.
     *   - On [TranscriptionOutcome.Success] the [transcript] may be an empty string
     *     when the audio was silent or unintelligible; callers should handle this as
     *     a soft failure (snackbar, no input change).
     *
     * Used by ChatViewModel for the press-and-hold mic gesture in the chat input row.
     */
    suspend fun transcribeAudio(
        audioPath: String,
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
