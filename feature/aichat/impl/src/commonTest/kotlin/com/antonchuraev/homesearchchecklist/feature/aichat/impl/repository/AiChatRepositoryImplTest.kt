package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteTranscriptionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscribeAudioApiService
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// ─── Fakes ────────────────────────────────────────────────────────────────────

private class FakeLocalIntentRouter(
    private val result: IntentClassification,
) : LocalIntentRouter {
    var callCount = 0
    override suspend fun route(input: String, locale: ChatLocale): IntentClassification {
        callCount++
        return result
    }
}

private class FakeChatClassifierApiService(
    private val result: RemoteClassificationResult,
) : ChatClassifierApiService {
    var callCount = 0
    var lastUserId: String? = null
    var lastText: String? = null

    override suspend fun classify(
        userId: String,
        text: String,
        locale: ChatLocale,
    ): RemoteClassificationResult {
        callCount++
        lastUserId = userId
        lastText = text
        return result
    }
}

private class FakeChatCompletionApiService(
    private val result: RemoteCompletionResult = RemoteCompletionResult.ServiceError,
) : ChatCompletionApiService {
    var callCount = 0

    override suspend fun complete(
        userId: String,
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult {
        callCount++
        return result
    }
}

private class FakeTranscribeAudioApiService(
    private val result: RemoteTranscriptionResult = RemoteTranscriptionResult.ServiceError,
) : TranscribeAudioApiService {
    var callCount = 0

    override suspend fun transcribe(
        userId: String,
        audioBase64: String,
        locale: ChatLocale,
    ): RemoteTranscriptionResult {
        callCount++
        return result
    }
}

private class FakeUserDataRepository(
    private val userId: String = "test-user-id-123",
) : UserDataRepository {
    override suspend fun getUserData(): UserData = UserData(userId = userId)
    override fun getUserDataFlow(): StateFlow<UserData> = MutableStateFlow(UserData(userId = userId))
    override suspend fun update(userData: UserData) = Unit
    override suspend fun ensureUserRegistered(): Result<com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData> =
        Result.failure(UnsupportedOperationException())
    override suspend fun syncWithServer(): Result<com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData> =
        Result.failure(UnsupportedOperationException())
    override suspend fun isPaywallLinked() = false
    override suspend fun setPaywallLinked(linked: Boolean) = Unit
    override suspend fun restoreCreditsAfterPurchase(): Result<Int> =
        Result.failure(UnsupportedOperationException())
    override suspend fun getFirstLaunchAtMillis() = 0L
}

private object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

private class FakeAiChatPreferencesRepository(
    initial: Boolean = false,
) : AiChatPreferencesRepository {
    private val _flow = MutableStateFlow(initial)
    override val deepThinkingEnabledFlow: Flow<Boolean> = _flow
    override suspend fun setDeepThinkingEnabled(enabled: Boolean) { _flow.value = enabled }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun highConfidenceLayer1(intent: ChatIntent = ChatIntent.CreateItem) = IntentClassification(
    intent = intent,
    confidence = 0.9f,
    layer = RoutingLayer.Local,
)

private fun lowConfidenceLayer1(intent: ChatIntent = ChatIntent.Unknown("?")) = IntentClassification(
    intent = intent,
    confidence = 0.5f,
    layer = RoutingLayer.Local,
)

private fun makeRepo(
    layer1Result: IntentClassification,
    remoteResult: RemoteClassificationResult = RemoteClassificationResult.ServiceError,
    completionResult: RemoteCompletionResult = RemoteCompletionResult.ServiceError,
    userId: String = "user-123",
    deepThinking: Boolean = false,
): Triple<AiChatRepositoryImpl, FakeLocalIntentRouter, FakeChatClassifierApiService> {
    val router = FakeLocalIntentRouter(layer1Result)
    val classifier = FakeChatClassifierApiService(remoteResult)
    val repo = AiChatRepositoryImpl(
        router = router,
        classifierApi = classifier,
        completionApi = FakeChatCompletionApiService(completionResult),
        transcribeApi = FakeTranscribeAudioApiService(),
        userDataRepository = FakeUserDataRepository(userId),
        aiChatPreferencesRepository = FakeAiChatPreferencesRepository(deepThinking),
        logger = NoOpLogger,
    )
    return Triple(repo, router, classifier)
}

// ─── Tests ────────────────────────────────────────────────────────────────────

class AiChatRepositoryImplTest {

    // ── 1. Layer 1 high confidence → no Layer 2 call ─────────────────────────

    @Test
    fun classify_layer1HighConfidence_doesNotCallLayer2() = runTest {
        val (repo, router, classifier) = makeRepo(
            layer1Result = highConfidenceLayer1(ChatIntent.CreateItem),
        )

        val result = repo.classify("add milk to shopping", ChatLocale.En)

        assertEquals(1, router.callCount, "Layer 1 must be called exactly once")
        assertEquals(0, classifier.callCount, "Layer 2 must NOT be called when confidence >= 0.7")
        assertEquals(RoutingLayer.Local, result.layer)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertEquals(0.9f, result.confidence)
    }

    // ── 2. Layer 1 low confidence + Layer 2 Success → returns Classifier result

    @Test
    fun classify_layer1LowConf_layer2Success_returnsClassifierResult() = runTest {
        val preBuiltToolCall = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk")
        val remoteSuccess = RemoteClassificationResult.Success(
            intent = ChatIntent.CreateItem,
            toolCall = preBuiltToolCall,
            confidence = 0.95f,
            creditsRemaining = 42,
        )
        val (repo, router, classifier) = makeRepo(
            layer1Result = lowConfidenceLayer1(),
            remoteResult = remoteSuccess,
        )

        val result = repo.classify("add milk to shopping", ChatLocale.En)

        assertEquals(1, router.callCount, "Layer 1 must still run")
        assertEquals(1, classifier.callCount, "Layer 2 must be called when confidence < 0.7")
        assertEquals(RoutingLayer.Classifier, result.layer)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertEquals(0.95f, result.confidence)
        val toolCall = result.preBuiltToolCall
        assertIs<ToolCall.AddItem>(toolCall)
        assertEquals("milk", toolCall.itemText)
        assertEquals("shopping", toolCall.checklistHint)
    }

    // ── 3. Layer 1 low confidence + InsufficientCredits → returns Unknown ────

    @Test
    fun classify_layer1LowConf_insufficientCredits_returnsUnknown() = runTest {
        val (repo, _, classifier) = makeRepo(
            layer1Result = lowConfidenceLayer1(),
            remoteResult = RemoteClassificationResult.InsufficientCredits,
        )

        val result = repo.classify("move things from monday to next week", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        assertIs<ChatIntent.Unknown>(result.intent)
        assertEquals(RoutingLayer.Classifier, result.layer)
        assertEquals(0f, result.confidence)
        assertNull(result.preBuiltToolCall)
    }

    // ── 4. Layer 1 low confidence + NetworkError → graceful degradation ───────

    @Test
    fun classify_layer1LowConf_networkError_returnsLayer1Result() = runTest {
        val layer1 = lowConfidenceLayer1(ChatIntent.Unknown("?"))
        val (repo, _, classifier) = makeRepo(
            layer1Result = layer1,
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        val result = repo.classify("ambiguous input", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        // Must return Layer 1 result unchanged
        assertEquals(RoutingLayer.Local, result.layer)
        assertEquals(layer1.confidence, result.confidence)
        assertNull(result.preBuiltToolCall)
    }

    // ── 5. Layer 1 low confidence + ServiceError → graceful degradation ───────

    @Test
    fun classify_layer1LowConf_serviceError_returnsLayer1Result() = runTest {
        val layer1 = lowConfidenceLayer1(ChatIntent.Unknown("?"))
        val (repo, _, classifier) = makeRepo(
            layer1Result = layer1,
            remoteResult = RemoteClassificationResult.ServiceError,
        )

        val result = repo.classify("ambiguous input", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        assertEquals(RoutingLayer.Local, result.layer)
        assertEquals(layer1.confidence, result.confidence)
    }

    // ── 6. Blank userId → Layer 2 skipped entirely ───────────────────────────

    @Test
    fun classify_blankUserId_layer2NotCalled() = runTest {
        val router = FakeLocalIntentRouter(lowConfidenceLayer1())
        val classifier = FakeChatClassifierApiService(RemoteClassificationResult.InsufficientCredits)
        val repo = AiChatRepositoryImpl(
            router = router,
            classifierApi = classifier,
            completionApi = FakeChatCompletionApiService(),
            transcribeApi = FakeTranscribeAudioApiService(),
            userDataRepository = FakeUserDataRepository(userId = ""),
            aiChatPreferencesRepository = FakeAiChatPreferencesRepository(),
            logger = NoOpLogger,
        )

        val result = repo.classify("some input", ChatLocale.En)

        assertEquals(0, classifier.callCount, "Layer 2 must NOT be called when userId is blank")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    // ── 7. Layer 2 called with correct userId and text ────────────────────────

    @Test
    fun classify_layer2_receivesCorrectUserIdAndText() = runTest {
        val expectedUserId = "firebase-uid-abc123"
        val inputText = "move things from monday to next week"
        val (repo, _, classifier) = makeRepo(
            layer1Result = lowConfidenceLayer1(),
            remoteResult = RemoteClassificationResult.NetworkError,
            userId = expectedUserId,
        )

        repo.classify(inputText, ChatLocale.En)

        assertEquals(expectedUserId, classifier.lastUserId)
        assertEquals(inputText, classifier.lastText)
    }

    // ── 8. Layer 1 exactly at threshold (0.7) → no Layer 2 call ─────────────

    @Test
    fun classify_layer1ExactlyAtThreshold_doesNotCallLayer2() = runTest {
        val atThreshold = IntentClassification(
            intent = ChatIntent.CreateItem,
            confidence = 0.7f,
            layer = RoutingLayer.Local,
        )
        val (repo, _, classifier) = makeRepo(
            layer1Result = atThreshold,
        )

        val result = repo.classify("add milk", ChatLocale.En)

        assertEquals(0, classifier.callCount, "Threshold is >=, so 0.7 must not escalate")
        assertEquals(RoutingLayer.Local, result.layer)
    }

    // ── 9. completeFreeForm: success → returns RemoteCompletionResult.Success ──

    @Test
    fun completeFreeForm_success_returnsSuccessResult() = runTest {
        val expectedContent = "Here are some suggestions for your week."
        val router = FakeLocalIntentRouter(highConfidenceLayer1())
        val completionApi = FakeChatCompletionApiService(
            RemoteCompletionResult.Success(content = expectedContent, creditsRemaining = 297)
        )
        val repo = AiChatRepositoryImpl(
            router = router,
            classifierApi = FakeChatClassifierApiService(RemoteClassificationResult.ServiceError),
            completionApi = completionApi,
            transcribeApi = FakeTranscribeAudioApiService(),
            userDataRepository = FakeUserDataRepository("user-xyz"),
            aiChatPreferencesRepository = FakeAiChatPreferencesRepository(),
            logger = NoOpLogger,
        )

        val result = repo.completeFreeForm(
            messages = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertEquals(1, completionApi.callCount)
        assertIs<RemoteCompletionResult.Success>(result)
        assertEquals(expectedContent, result.content)
        assertEquals(297, result.creditsRemaining)
    }

    // ── 10. completeFreeForm: blank userId → ServiceError, no network call ─────

    @Test
    fun completeFreeForm_blankUserId_returnsServiceError() = runTest {
        val completionApi = FakeChatCompletionApiService(RemoteCompletionResult.Success("x", 0))
        val repo = AiChatRepositoryImpl(
            router = FakeLocalIntentRouter(highConfidenceLayer1()),
            classifierApi = FakeChatClassifierApiService(RemoteClassificationResult.ServiceError),
            completionApi = completionApi,
            transcribeApi = FakeTranscribeAudioApiService(),
            userDataRepository = FakeUserDataRepository(userId = ""),
            aiChatPreferencesRepository = FakeAiChatPreferencesRepository(),
            logger = NoOpLogger,
        )

        val result = repo.completeFreeForm(
            messages = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertEquals(0, completionApi.callCount, "No network call when userId is blank")
        assertIs<RemoteCompletionResult.ServiceError>(result)
    }

    // ── 11. completeFreeForm: InsufficientCredits propagated ─────────────────

    @Test
    fun completeFreeForm_insufficientCredits_propagatedToCallerUnchanged() = runTest {
        val completionApi = FakeChatCompletionApiService(RemoteCompletionResult.InsufficientCredits)
        val repo = AiChatRepositoryImpl(
            router = FakeLocalIntentRouter(highConfidenceLayer1()),
            classifierApi = FakeChatClassifierApiService(RemoteClassificationResult.ServiceError),
            completionApi = completionApi,
            transcribeApi = FakeTranscribeAudioApiService(),
            userDataRepository = FakeUserDataRepository("user-abc"),
            aiChatPreferencesRepository = FakeAiChatPreferencesRepository(),
            logger = NoOpLogger,
        )

        val result = repo.completeFreeForm(
            messages = emptyList(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.InsufficientCredits>(result)
    }

    // ── 12. Deep Thinking bypass: free-form input routes to Layer 3 ────────

    @Test
    fun classify_deepThinkingOn_layer1Unknown_returnsFreeForm() = runTest {
        // Deep Thinking ON + Layer 1 cannot match a command → genuine open-ended question
        // → must escalate to Layer 3 (FullChat). Layer 1 is still consulted (cheap), but
        // its Unknown result lets the bypass kick in.
        val router = FakeLocalIntentRouter(lowConfidenceLayer1())
        val classifier = FakeChatClassifierApiService(RemoteClassificationResult.ServiceError)
        val repo = AiChatRepositoryImpl(
            router = router,
            classifierApi = classifier,
            completionApi = FakeChatCompletionApiService(),
            transcribeApi = FakeTranscribeAudioApiService(),
            userDataRepository = FakeUserDataRepository("user-deep"),
            aiChatPreferencesRepository = FakeAiChatPreferencesRepository(initial = true),
            logger = NoOpLogger,
        )

        val result = repo.classify("плануй мою неделю", ChatLocale.Ru)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(1.0f, result.confidence)
        assertEquals(RoutingLayer.FullChat, result.layer)
        assertEquals(0, classifier.callCount, "Layer 2 must NOT be called when deep thinking is ON")
    }

    // ── skipLayer1 tests ─────────────────────────────────────────────────────

    // ── 14a. skipLayer1=true → router.route() NOT called ────────────────────

    @Test
    fun classify_skipLayer1_doesNotCallRouter() = runTest {
        val (repo, router, _) = makeRepo(
            layer1Result = highConfidenceLayer1(ChatIntent.CreateItem),
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertEquals(0, router.callCount, "Layer1 router must NOT be called when skipLayer1=true")
    }

    // ── 14b. skipLayer1=true → classifierApi.classify() IS called ───────────

    @Test
    fun classify_skipLayer1_callsClassifierApi() = runTest {
        val (repo, _, classifier) = makeRepo(
            layer1Result = highConfidenceLayer1(ChatIntent.CreateItem),
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertEquals(1, classifier.callCount, "Layer2 classifier must be called when skipLayer1=true")
    }

    // ── 14c. skipLayer1=true + Layer2 FreeForm → returns FreeForm for Layer3 ─

    @Test
    fun classify_skipLayer1_layer2Vague_returnsFreeForm() = runTest {
        val vagueResult = RemoteClassificationResult.Success(
            intent = ChatIntent.FreeForm,
            toolCall = null,
            confidence = 0.5f,
            creditsRemaining = 10,
        )
        val (repo, _, _) = makeRepo(
            layer1Result = highConfidenceLayer1(ChatIntent.CreateItem),
            remoteResult = vagueResult,
        )

        val result = repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(1.0f, result.confidence)
        assertEquals(RoutingLayer.FullChat, result.layer)
        assertNull(result.preBuiltToolCall)
    }

    // ── 14d. skipLayer1=true + Layer2 NetworkError → returns FreeForm for Layer3

    @Test
    fun classify_skipLayer1_layer2NetworkError_returnsFreeForm() = runTest {
        val (repo, _, classifier) = makeRepo(
            layer1Result = highConfidenceLayer1(ChatIntent.CreateItem),
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        val result = repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertEquals(1, classifier.callCount)
        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
    }

    // ── 13. Deep Thinking + Layer 1 command-intent: Layer 1 wins ───────────────

    @Test
    fun classify_deepThinkingOn_layer1ConfidentCommand_returnsLayer1() = runTest {
        // Real-world scenario (2026-05-18 ai_chat_feedback events): user leaves Deep
        // Thinking ON between sessions, then types «добавь в дела купить ведра». Layer 1
        // detects CreateItem with CONF_FULL — we MUST prefer that over burning 3 credits
        // on Layer 3 free-form chat. Otherwise every command-shaped query costs 3 credits
        // and answers «извините, не поняла».
        val router = FakeLocalIntentRouter(highConfidenceLayer1(ChatIntent.CreateItem))
        val classifier = FakeChatClassifierApiService(RemoteClassificationResult.ServiceError)
        val repo = AiChatRepositoryImpl(
            router = router,
            classifierApi = classifier,
            completionApi = FakeChatCompletionApiService(),
            transcribeApi = FakeTranscribeAudioApiService(),
            userDataRepository = FakeUserDataRepository("user-deep"),
            aiChatPreferencesRepository = FakeAiChatPreferencesRepository(initial = true),
            logger = NoOpLogger,
        )

        val result = repo.classify("добавь молоко в покупки", ChatLocale.Ru)

        assertIs<ChatIntent.CreateItem>(result.intent)
        assertEquals(RoutingLayer.Local, result.layer)
        assertEquals(0, classifier.callCount, "Layer 2 must NOT be called — Layer 1 already won")
    }
}
