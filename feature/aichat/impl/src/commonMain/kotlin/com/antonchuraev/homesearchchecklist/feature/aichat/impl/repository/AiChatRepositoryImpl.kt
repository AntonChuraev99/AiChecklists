package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository

/**
 * Phase B implementation: Layer 1 local router with Layer 2 cloud classifier fallback.
 *
 * Classification flow:
 *   1. [LocalIntentRouter.route] → Layer 1 result
 *   2. If confidence >= [LAYER_1_CONFIDENCE_THRESHOLD] → return immediately (0 credits spent)
 *   3. Otherwise → call [ChatClassifierApiService.classify] (Layer 2, costs 1 credit)
 *      - [RemoteClassificationResult.Success]          → return Classifier result
 *      - [RemoteClassificationResult.InsufficientCredits] → return Unknown (caller shows inline message)
 *      - [RemoteClassificationResult.NetworkError]     → graceful degradation, return Layer 1 result
 *      - [RemoteClassificationResult.ServiceError]     → graceful degradation, return Layer 1 result
 *
 * Layer 2 is skipped entirely when [userId] is blank (unregistered user).
 */
internal class AiChatRepositoryImpl(
    private val router: LocalIntentRouter,
    private val classifierApi: ChatClassifierApiService,
    private val userDataRepository: UserDataRepository,
    private val logger: AppLogger,
) : AiChatRepository {

    companion object {
        private const val TAG = "AiChatRepository"
        private const val LAYER_1_CONFIDENCE_THRESHOLD = 0.7f
    }

    override suspend fun classify(input: String, locale: ChatLocale): IntentClassification {
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
}
