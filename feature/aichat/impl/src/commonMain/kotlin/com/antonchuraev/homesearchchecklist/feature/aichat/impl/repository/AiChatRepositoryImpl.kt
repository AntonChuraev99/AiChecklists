package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
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
 * Phase C implementation: Layer 1 → Layer 2 → Layer 3 routing chain.
 *
 * Classification flow ([classify]):
 *   1. [LocalIntentRouter.route] → Layer 1 result
 *   2. If confidence >= [LAYER_1_CONFIDENCE_THRESHOLD] → return immediately (0 credits)
 *   3. Otherwise → [ChatClassifierApiService.classify] (Layer 2, 1 credit)
 *      - Success with intent=FreeForm → ViewModel calls [completeFreeForm] (Layer 3, 3 credits)
 *      - Success with structured intent → return Classifier result
 *      - InsufficientCredits → return Unknown (caller shows inline message)
 *      - NetworkError / ServiceError → graceful degradation to Layer 1 result
 *
 * [completeFreeForm] delegates to [ChatCompletionApiService] with full message history.
 * Layer 2 and Layer 3 are skipped when [userId] is blank (unregistered user).
 */
internal class AiChatRepositoryImpl(
    private val router: LocalIntentRouter,
    private val classifierApi: ChatClassifierApiService,
    private val completionApi: ChatCompletionApiService,
    private val transcribeApi: TranscribeAudioApiService,
    private val chatAgentApi: ChatAgentApiService,
    private val remoteConfig: RemoteConfigProvider,
    private val userDataRepository: UserDataRepository,
    private val aiChatPreferencesRepository: AiChatPreferencesRepository,
    private val logger: AppLogger,
) : AiChatRepository {

    companion object {
        private const val TAG = "AiChatRepository"
        private const val LAYER_1_CONFIDENCE_THRESHOLD = 0.7f
    }

    override suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean): IntentClassification {
        // skipLayer1=true: user rejected a Layer 1 preview and wants the next-tier interpretation.
        // We skip the local router entirely and go straight to Layer 2 (cloud classifier).
        if (skipLayer1) {
            return classifySkipLayer1(input, locale)
        }

        // Always run Layer 1 first — it is free, fast, and command-intent recognition
        // must NOT be bypassed by user-facing toggles. Deep Thinking is for open-ended
        // questions ("plan my week"), not for "add milk to shopping" type commands.
        // Real-world signal (2026-05-18): users left Deep Thinking ON between sessions,
        // every add/delete/find command burned 3 credits on a Layer 3 meta-reply.
        val layer1 = router.route(input, locale)
        logger.debug(TAG, "Layer1: ${layer1.intent::class.simpleName} conf=${layer1.confidence}")

        val layer1IsCommand = layer1.intent !is ChatIntent.Unknown &&
            layer1.intent !is ChatIntent.FreeForm
        val layer1IsConfident = layer1.confidence >= LAYER_1_CONFIDENCE_THRESHOLD

        // Deep Thinking bypass — user opted into "always full chat" mode.
        // Honour the toggle ONLY when Layer 1 didn't find a confident command-intent;
        // otherwise the user almost certainly meant a quick action, not a free-form chat.
        if (aiChatPreferencesRepository.deepThinkingEnabledFlow.first()) {
            if (layer1IsCommand && layer1IsConfident) {
                logger.info(TAG, "Deep thinking ON, but Layer1 matched ${layer1.intent::class.simpleName} confidently — routing as command")
                return layer1
            }
            logger.info(TAG, "Deep thinking ON — routing to Layer 3 (FreeForm)")
            return IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
                preBuiltToolCall = null,
            )
        }

        if (layer1IsConfident) {
            return layer1
        }

        logger.debug(TAG, "Layer1 confidence below threshold — escalating to Layer 2")

        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "Layer2 skipped: userId blank (user not registered yet)")
            return layer1
        }

        return when (val remote = classifierApi.classify(userId, input, locale)) {
            is RemoteClassificationResult.Success -> {
                logger.info(TAG, "Layer2 success: ${remote.intent::class.simpleName} conf=${remote.confidence} credits_remaining=${remote.creditsRemaining}")
                // Graceful Layer 1 preference: if the cloud classifier gave up (FreeForm or Unknown)
                // BUT the local router already matched a concrete command-intent, prefer the local
                // result. This routes the user straight to a preview-card instead of burning 3 more
                // credits on Layer 3 only to get a "sorry, didn't quite parse..." meta-reply.
                // Real-world signal: ai_chat_feedback events showed Layer 3 spam on phrases like
                // «добавь в дела купить 2 мус ведра» where Layer 1 was already confident enough.
                val classifierIsVague = remote.intent is ChatIntent.FreeForm ||
                    remote.intent is ChatIntent.Unknown
                val layer1IsCommand = layer1.intent !is ChatIntent.Unknown &&
                    layer1.intent !is ChatIntent.FreeForm
                if (classifierIsVague && layer1IsCommand) {
                    logger.info(
                        TAG,
                        "Layer2 vague (${remote.intent::class.simpleName}) — preferring Layer1 ${layer1.intent::class.simpleName}",
                    )
                    // Bump confidence to threshold so downstream handlers treat it as actionable,
                    // but mark layer=Classifier because the credit was already spent on Layer 2.
                    return IntentClassification(
                        intent = layer1.intent,
                        confidence = maxOf(layer1.confidence, LAYER_1_CONFIDENCE_THRESHOLD),
                        layer = RoutingLayer.Classifier,
                        preBuiltToolCall = null,
                    )
                }
                IntentClassification(
                    intent = remote.intent,
                    confidence = remote.confidence,
                    layer = RoutingLayer.Classifier,
                    preBuiltToolCall = remote.toolCall,
                )
            }
            RemoteClassificationResult.InsufficientCredits -> {
                logger.info(TAG, "Layer2: InsufficientCredits — returning Unknown")
                IntentClassification(
                    intent = ChatIntent.Unknown(rawText = input),
                    confidence = 0f,
                    layer = RoutingLayer.Classifier,
                    preBuiltToolCall = null,
                )
            }
            RemoteClassificationResult.NetworkError -> {
                logger.warning(TAG, "Layer2 NetworkError — graceful degradation to Layer 1 result")
                layer1
            }
            RemoteClassificationResult.ServiceError -> {
                logger.warning(TAG, "Layer2 ServiceError — graceful degradation to Layer 1 result")
                layer1
            }
        }
    }

    /**
     * Reject-flow classification: skips Layer 1 entirely and goes straight to Layer 2.
     *
     * Does NOT respect the Deep Thinking toggle — when the user taps "I meant something else"
     * they are explicitly asking for a higher-tier interpretation, not free-form chat.
     */
    private suspend fun classifySkipLayer1(input: String, locale: ChatLocale): IntentClassification {
        logger.info(TAG, "classifySkipLayer1: skipping Layer1, going straight to Layer2")

        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "classifySkipLayer1: userId blank — escalating to Layer3 (FreeForm)")
            return IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
                preBuiltToolCall = null,
            )
        }

        return when (val remote = classifierApi.classify(userId, input, locale)) {
            is RemoteClassificationResult.Success -> {
                logger.info(TAG, "classifySkipLayer1 Layer2 success: ${remote.intent::class.simpleName} conf=${remote.confidence}")
                val isVague = remote.intent is ChatIntent.FreeForm || remote.intent is ChatIntent.Unknown
                if (isVague) {
                    // Vague Layer 2 → escalate to Layer 3
                    logger.info(TAG, "classifySkipLayer1: Layer2 vague (${remote.intent::class.simpleName}) → FreeForm for Layer3")
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
                logger.info(TAG, "classifySkipLayer1: InsufficientCredits — returning Unknown")
                IntentClassification(
                    intent = ChatIntent.Unknown(rawText = input),
                    confidence = 0f,
                    layer = RoutingLayer.Classifier,
                    preBuiltToolCall = null,
                )
            }
            RemoteClassificationResult.NetworkError,
            RemoteClassificationResult.ServiceError -> {
                logger.warning(TAG, "classifySkipLayer1: Layer2 ${remote::class.simpleName} — escalating to Layer3 (FreeForm)")
                IntentClassification(
                    intent = ChatIntent.FreeForm,
                    confidence = 1.0f,
                    layer = RoutingLayer.FullChat,
                    preBuiltToolCall = null,
                )
            }
        }
    }

    override fun isAgenticChatEnabled(): Boolean =
        remoteConfig.getBoolean(
            RemoteConfigKeys.AI_CHAT_AGENTIC_ENABLED,
            RemoteConfigDefaults.AI_CHAT_AGENTIC_ENABLED,
        )

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): AgentStepResult {
        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "agentStep skipped: userId blank (user not registered yet)")
            return AgentStepResult.ServiceError
        }
        logger.debug(TAG, "agentStep: transcript=${transcript.size} entries locale=$locale checklists=${checklistsSummary.size}")
        return chatAgentApi.step(
            userId = userId,
            transcript = transcript,
            locale = locale,
            checklistsSummary = checklistsSummary,
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
