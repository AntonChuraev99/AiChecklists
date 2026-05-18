package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
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
    private val userDataRepository: UserDataRepository,
    private val aiChatPreferencesRepository: AiChatPreferencesRepository,
    private val logger: AppLogger,
) : AiChatRepository {

    companion object {
        private const val TAG = "AiChatRepository"
        private const val LAYER_1_CONFIDENCE_THRESHOLD = 0.7f
    }

    override suspend fun classify(input: String, locale: ChatLocale): IntentClassification {
        // Deep Thinking bypass — user opted into "always full chat" mode.
        // Skip Layer 1 and Layer 2 entirely; return FreeForm at full confidence so
        // ViewModel routes straight to handleFreeForm → completeFreeForm (Layer 3, 3 credits).
        if (aiChatPreferencesRepository.deepThinkingEnabledFlow.first()) {
            logger.info(TAG, "Deep thinking ON — bypassing Layer 1/2, returning FreeForm")
            return IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
                preBuiltToolCall = null,
            )
        }

        val layer1 = router.route(input, locale)
        logger.debug(TAG, "Layer1: ${layer1.intent::class.simpleName} conf=${layer1.confidence}")

        if (layer1.confidence >= LAYER_1_CONFIDENCE_THRESHOLD) {
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
}
