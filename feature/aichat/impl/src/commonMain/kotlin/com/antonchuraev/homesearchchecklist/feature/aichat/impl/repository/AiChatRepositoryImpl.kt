package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteTranscriptionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscribeAudioApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.data.AudioFileBytes
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.flow.first

/**
 * Layer 2 → Layer 3 routing chain.
 *
 * **Layer 1 (local parser) is disconnected** — decision 2026-07-15
 * (`docs/decisions/2026-07-15-remove-ai-chat-layer1.md`). Every message now starts at Layer 2,
 * so trivial mutations («add milk») cost 1 credit instead of 0 and no longer work offline.
 * This is a *disconnect, not a removal*: `LocalIntentRouterImpl` and its 168 tests stay in the
 * repo as a parked asset, and re-routing it is a revert of the disconnect commit — do not write
 * a second local parser.
 *
 * Classification flow ([classify]):
 *   1. Deep Thinking ON → straight to Layer 3 (FreeForm/FullChat), skipping Layer 2 so an
 *      open-ended question stays 3 credits rather than 4. Not honoured when `skipLayer1=true`.
 *   2. Otherwise → [ChatClassifierApiService.classify] (Layer 2, 1 credit)
 *      - Success with a structured intent → return the Classifier result
 *      - Success but vague (FreeForm / Unknown) → escalate to Layer 3 (FreeForm)
 *      - InsufficientCredits → return [ChatIntent.InsufficientCredits] (caller shows the
 *        out-of-credits reply + paywall CTA; the refusal must NOT be flattened into Unknown)
 *      - NetworkError / ServiceError → escalate to Layer 3, which surfaces the failure as a
 *        `chat_completion_error` reply (there is no local fallback to degrade to any more)
 *
 * [completeFreeForm] delegates to [ChatCompletionApiService] with full message history.
 * Layer 2 and Layer 3 are skipped when [userId] is blank (unregistered user).
 */
internal class AiChatRepositoryImpl(
    private val classifierApi: ChatClassifierApiService,
    private val completionApi: ChatCompletionApiService,
    private val transcribeApi: TranscribeAudioApiService,
    private val chatAgentApi: ChatAgentApiService,
    private val userDataRepository: UserDataRepository,
    private val aiChatPreferencesRepository: AiChatPreferencesRepository,
    private val logger: AppLogger,
) : AiChatRepository {

    companion object {
        private const val TAG = "AiChatRepository"
    }

    override suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean): IntentClassification {
        // Deep Thinking bypass — user opted into "always full chat" mode, so go straight to
        // Layer 3 without paying Layer 2's credit on the way (open question = 3 credits, not 4).
        //
        // The old guard "Deep Thinking ON, but Layer 1 matched a confident command → run it as a
        // command" (2026-05-18) is gone with Layer 1: nothing local can tell a command from a
        // question any more. Accepted cost of the disconnect — a user who leaves the toggle on
        // pays 3 credits for «add milk». This branch is what keeps the toggle meaningful: DT ON
        // skips Layer 2 entirely, DT OFF starts there.
        //
        // skipLayer1=true deliberately does NOT reach this branch (see [classifyViaLayer2]).
        if (!skipLayer1 && aiChatPreferencesRepository.deepThinkingEnabledFlow.first()) {
            logger.info(TAG, "Deep thinking ON — routing to Layer 3 (FreeForm)")
            return IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
                preBuiltToolCall = null,
            )
        }

        return classifyViaLayer2(input, locale)
    }

    /**
     * The only classification path left: Layer 2 (cloud classifier, 1 credit), escalating to
     * Layer 3 when the classifier is vague, unreachable, or the user is not registered yet.
     *
     * Entered either from [classify] with Deep Thinking OFF, or from the reject flow
     * (`skipLayer1=true`), which reaches it *regardless* of the Deep Thinking toggle: tapping
     * "I meant something else" is an explicit request for a higher-tier interpretation of a
     * concrete command, not an opt-in to free-form chat.
     *
     * There is no local fallback any more — a blank userId or a dead network both escalate to
     * Layer 3, where the failure surfaces as a visible `chat_completion_error` reply rather than
     * a misleading "I didn't catch that".
     */
    private suspend fun classifyViaLayer2(input: String, locale: ChatLocale): IntentClassification {
        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "Layer2 skipped: userId blank — escalating to Layer3 (FreeForm)")
            return IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
                preBuiltToolCall = null,
            )
        }

        return when (val remote = classifierApi.classify(userId, input, locale)) {
            is RemoteClassificationResult.Success -> {
                logger.info(TAG, "Layer2 success: ${remote.intent::class.simpleName} conf=${remote.confidence} credits_remaining=${remote.creditsRemaining}")
                val isVague = remote.intent is ChatIntent.FreeForm || remote.intent is ChatIntent.Unknown
                if (isVague) {
                    // Vague Layer 2 → escalate to Layer 3.
                    logger.info(TAG, "Layer2 vague (${remote.intent::class.simpleName}) → FreeForm for Layer3")
                    IntentClassification(
                        intent = ChatIntent.FreeForm,
                        confidence = 1.0f,
                        layer = RoutingLayer.FullChat,
                        preBuiltToolCall = null,
                    )
                } else {
                    IntentClassification(
                        intent = remote.intent,
                        confidence = remote.confidence,
                        layer = RoutingLayer.Classifier,
                        preBuiltToolCall = remote.toolCall,
                    )
                }
            }
            RemoteClassificationResult.InsufficientCredits -> {
                logger.info(TAG, "Layer2: InsufficientCredits — surfacing the refusal to the caller")
                // The refusal travels in the TYPE. Flattening it into Unknown here (until
                // 2026-07-16) destroyed the only copy of the reason, so no call-site could tell
                // "out of credits" from "unparseable" and every 402 rendered as
                // "Sorry, I didn't quite catch that" — the app blaming the user's phrasing for a
                // billing state. Escalating to Layer 3 instead would be quieter and worse: it
                // bills 3 credits to a wallet that just refused to pay 1.
                //
                // confidence = 1f: we are CERTAIN the wallet is empty. 0f used to mean "wild
                // guess", which is what dragged this into the low-confidence/Unknown bucket.
                // layer stays Classifier — Layer 2 is where the turn died, and analytics should
                // say so. It charged nothing; the caller prices the turn at 0.
                IntentClassification(
                    intent = ChatIntent.InsufficientCredits,
                    confidence = 1f,
                    layer = RoutingLayer.Classifier,
                    preBuiltToolCall = null,
                )
            }
            RemoteClassificationResult.NetworkError,
            RemoteClassificationResult.ServiceError -> {
                logger.warning(TAG, "Layer2 ${remote::class.simpleName} — escalating to Layer3 (FreeForm)")
                IntentClassification(
                    intent = ChatIntent.FreeForm,
                    confidence = 1.0f,
                    layer = RoutingLayer.FullChat,
                    preBuiltToolCall = null,
                )
            }
        }
    }

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
    ): AgentStepResult {
        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "agentStep skipped: userId blank (user not registered yet)")
            return AgentStepResult.ServiceError
        }
        logger.debug(TAG, "agentStep: transcript=${transcript.size} entries locale=$locale checklists=${checklistsSummary.size} context=${contextChecklistName ?: "none"}")
        return chatAgentApi.step(
            userId = userId,
            transcript = transcript,
            locale = locale,
            checklistsSummary = checklistsSummary,
            contextChecklistName = contextChecklistName,
        )
    }

    override suspend fun completeFreeForm(
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult {
        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "completeFreeForm skipped: userId blank (user not registered yet)")
            return RemoteCompletionResult.ServiceError
        }

        logger.debug(TAG, "completeFreeForm: messages=${messages.size} locale=$locale checklists=${checklistsSummary.size}")
        return completionApi.complete(
            userId = userId,
            messages = messages,
            locale = locale,
            checklistsSummary = checklistsSummary,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribeAudio(
        audioPath: String,
        mimeType: String,
        locale: ChatLocale,
    ): TranscriptionOutcome {
        val bytes = AudioFileBytes.read(audioPath)
        if (bytes == null || bytes.isEmpty()) {
            logger.warning(TAG, "transcribeAudio: file missing or empty at $audioPath")
            // No file → no credit to spend either, just bail cleanly.
            AudioFileBytes.delete(audioPath)
            return TranscriptionOutcome.FileMissing
        }

        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "transcribeAudio skipped: userId blank (user not registered yet)")
            AudioFileBytes.delete(audioPath)
            return TranscriptionOutcome.ServiceError
        }

        val base64 = Base64.encode(bytes)
        logger.debug(TAG, "transcribeAudio: bytes=${bytes.size} b64_len=${base64.length} mime=$mimeType locale=$locale")

        // File is no longer needed once we have the base64 payload — delete eagerly
        // so a failed transcription does not leak audio in cacheDir.
        AudioFileBytes.delete(audioPath)

        return when (val result = transcribeApi.transcribe(userId, base64, mimeType, locale)) {
            is RemoteTranscriptionResult.Success -> {
                if (result.transcript.isBlank()) {
                    logger.info(TAG, "transcribeAudio: empty transcript (silent or unintelligible)")
                    TranscriptionOutcome.EmptyTranscript
                } else {
                    logger.info(TAG, "transcribeAudio: success len=${result.transcript.length} credits=${result.creditsRemaining}")
                    TranscriptionOutcome.Success(result.transcript)
                }
            }
            RemoteTranscriptionResult.InsufficientCredits -> {
                logger.info(TAG, "transcribeAudio: InsufficientCredits")
                TranscriptionOutcome.InsufficientCredits
            }
            RemoteTranscriptionResult.NetworkError -> {
                logger.warning(TAG, "transcribeAudio: NetworkError")
                TranscriptionOutcome.NetworkError
            }
            RemoteTranscriptionResult.ServiceError -> {
                logger.warning(TAG, "transcribeAudio: ServiceError")
                TranscriptionOutcome.ServiceError
            }
        }
    }
}
