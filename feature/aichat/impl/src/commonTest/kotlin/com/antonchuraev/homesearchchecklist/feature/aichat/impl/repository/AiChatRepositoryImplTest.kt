package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/*
 * Routing contract after the Layer 1 disconnect (decision 2026-07-15,
 * docs/decisions/2026-07-15-remove-ai-chat-layer1.md).
 *
 * There is no local router left to inject, so these tests lock the two-tier ladder:
 *   Deep Thinking ON → Layer 3 (skips Layer 2)
 *   otherwise        → Layer 2 → (vague / unreachable / unregistered) → Layer 3
 *
 * The parser itself is NOT covered here — it stays alive and fully tested in
 * parser/LocalIntentRouterImplTest.kt (168 tests). Those tests are what makes re-routing
 * Layer 1 a revert rather than a rewrite; do not delete them because this file stopped
 * referencing the router.
 */

// ─── Fakes ────────────────────────────────────────────────────────────────────

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
    var lastUserId: String? = null
    var lastMessages: List<ChatMessage>? = null
    var lastLocale: ChatLocale? = null
    var lastChecklistsSummary: List<ChecklistContext>? = null

    override suspend fun complete(
        userId: String,
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        responseLanguage: String?,
    ): RemoteCompletionResult {
        callCount++
        lastUserId = userId
        lastMessages = messages
        lastLocale = locale
        lastChecklistsSummary = checklistsSummary
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
        mimeType: String,
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

private class FakeChatAgentApiService : ChatAgentApiService {
    override suspend fun step(
        userId: String,
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
        requestId: String?,
        responseLanguage: String?,
    ): AgentStepResult = AgentStepResult.ServiceError
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

    // D2's default-list preference is a ViewModel concern; the repository never reads it.
    private val _defaultChecklistId = MutableStateFlow<Long?>(null)
    override val defaultChecklistIdFlow: Flow<Long?> = _defaultChecklistId
    override suspend fun setDefaultChecklistId(checklistId: Long?) { _defaultChecklistId.value = checklistId }

    private val _responseLanguage = MutableStateFlow<String?>(null)
    override val responseLanguageFlow: Flow<String?> = _responseLanguage
    override suspend fun setResponseLanguage(code: String?) { _responseLanguage.value = code }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private data class Fixture(
    val repo: AiChatRepositoryImpl,
    val classifier: FakeChatClassifierApiService,
    val completion: FakeChatCompletionApiService,
)

private fun makeRepo(
    remoteResult: RemoteClassificationResult = RemoteClassificationResult.ServiceError,
    completionResult: RemoteCompletionResult = RemoteCompletionResult.ServiceError,
    userId: String = "user-123",
    deepThinking: Boolean = false,
): Fixture {
    val classifier = FakeChatClassifierApiService(remoteResult)
    val completion = FakeChatCompletionApiService(completionResult)
    val repo = AiChatRepositoryImpl(
        classifierApi = classifier,
        completionApi = completion,
        transcribeApi = FakeTranscribeAudioApiService(),
        chatAgentApi = FakeChatAgentApiService(),
        userDataRepository = FakeUserDataRepository(userId),
        aiChatPreferencesRepository = FakeAiChatPreferencesRepository(deepThinking),
        logger = NoOpLogger,
    )
    return Fixture(repo, classifier, completion)
}

private fun layer2Success(
    intent: ChatIntent = ChatIntent.CreateItem,
    toolCall: ToolCall? = null,
    confidence: Float = 0.95f,
) = RemoteClassificationResult.Success(
    intent = intent,
    toolCall = toolCall,
    confidence = confidence,
    creditsRemaining = 42,
)

// ─── Tests ────────────────────────────────────────────────────────────────────

class AiChatRepositoryImplTest {

    // ── 1. Every message starts at Layer 2 — there is no free local shortcut ──
    //
    // The single most important assertion in this file: a command-shaped phrase that Layer 1
    // used to answer for free now goes to the cloud classifier and costs a credit. If this
    // test starts failing because something short-circuits Layer 2, that "something" is a
    // second local parser — forbidden by the ADR; re-route the real Layer 1 instead.

    @Test
    fun classify_commandPhrase_alwaysCallsLayer2() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.CreateItem),
        )

        val result = repo.classify("add milk to shopping", ChatLocale.En)

        assertEquals(1, classifier.callCount, "Layer 2 must be called — Layer 1 is disconnected")
        assertEquals(RoutingLayer.Classifier, result.layer, "Local routing must never be produced")
    }

    // ── 2. Layer 2 Success → returns Classifier result with the pre-built ToolCall ──

    @Test
    fun classify_layer2Success_returnsClassifierResult() = runTest {
        val preBuiltToolCall = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk")
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.CreateItem, preBuiltToolCall),
        )

        val result = repo.classify("add milk to shopping", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        assertEquals(RoutingLayer.Classifier, result.layer)
        assertIs<ChatIntent.CreateItem>(result.intent)
        assertEquals(0.95f, result.confidence)
        val toolCall = result.preBuiltToolCall
        assertIs<ToolCall.AddItem>(toolCall)
        assertEquals("milk", toolCall.itemText)
        assertEquals("shopping", toolCall.checklistHint)
    }

    // ── 3. Layer 2 vague (FreeForm) → escalates to Layer 3 ───────────────────

    @Test
    fun classify_layer2Vague_escalatesToLayer3() = runTest {
        val (repo, _, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.FreeForm, confidence = 0.5f),
        )

        val result = repo.classify("what should I do this week", ChatLocale.En)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(1.0f, result.confidence)
        assertEquals(RoutingLayer.FullChat, result.layer)
        assertNull(result.preBuiltToolCall)
    }

    // ── 4. Layer 2 Unknown → escalates to Layer 3 ────────────────────────────

    @Test
    fun classify_layer2Unknown_escalatesToLayer3() = runTest {
        val (repo, _, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.Unknown("?"), confidence = 0.2f),
        )

        val result = repo.classify("asdfgh", ChatLocale.En)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
    }

    // ── 5. InsufficientCredits → the reason must SURVIVE classification ───────
    //
    // 🔴 RED repro, 2026-07-16.
    // docs/todos/2026-07-16-aichat-insufficient-credits-shows-unknown-hint.md
    //
    // This test previously asserted `assertIs<ChatIntent.Unknown>(result.intent)` and called it
    // "caller shows the paywall hint". Both halves were false: no caller shows a paywall hint,
    // because by then there is nothing left to show one FROM. The test was pinning the bug as
    // the spec, so it is rewritten rather than kept — the behaviour it locked is the defect.
    //
    // What a user gets today: free tier, 10 credits spent, types «добавь молоко» → HTTP 402 →
    // this branch flattens the refusal into ChatIntent.Unknown → ChatViewModel renders
    // "Sorry, I didn't quite catch that" + an "Ask AI" button. The app blames the user's phrasing
    // for a billing state and never offers the paywall — the exact conversion moment the Layer 1
    // disconnect was accepted to buy (docs/decisions/2026-07-15-remove-ai-chat-layer1.md).
    //
    // The reason is erased HERE, so no call-site can recover it: the fix has to carry it in the
    // type. Two candidate shapes (a ChatIntent variant, or an outcome wrapper around classify())
    // are both fine — this test therefore fences the two WRONG answers instead of naming the
    // right one. If the wrapper shape wins, classify()'s signature changes and this test moves to
    // the new seam: keep the property, drop the type.
    //
    // Asserted through the user's wallet, both directions:
    //   • NOT Unknown  → "I didn't understand you" (the shipped lie).
    //   • NOT FreeForm → escalation to Layer 3, i.e. billing 3 more credits to a wallet that just
    //                    refused 1. Quieter than the lie, and worse.

    @Test
    fun classify_insufficientCredits_neitherClaimsNotUnderstoodNorEscalatesToPaidLayer() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = RemoteClassificationResult.InsufficientCredits,
        )

        val result = repo.classify("add milk to shopping", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        assertFalse(
            result.intent is ChatIntent.Unknown,
            "402 must not be reported as 'message not understood': the ViewModel renders that as " +
                "chat_unknown_intent_hint, blaming the user's phrasing for an empty wallet",
        )
        assertFalse(
            result.intent is ChatIntent.FreeForm,
            "402 must not escalate to Layer 3 either — that bills 3 credits to a wallet that just " +
                "refused to pay 1",
        )
    }

    // ── 6. NetworkError → Layer 3, NOT a local fallback ──────────────────────
    //
    // Layer 1 used to absorb this (chat worked offline). It cannot any more, so the request
    // escalates to Layer 3 and the ViewModel surfaces the failure as a visible
    // `chat_completion_error` reply. What must NOT happen is a Local-layer result: that would
    // mean a fallback parser crept back in.

    @Test
    fun classify_layer2NetworkError_escalatesToLayer3() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        val result = repo.classify("add milk to shopping", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
    }

    // ── 7. ServiceError → Layer 3 ────────────────────────────────────────────

    @Test
    fun classify_layer2ServiceError_escalatesToLayer3() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = RemoteClassificationResult.ServiceError,
        )

        val result = repo.classify("ambiguous input", ChatLocale.En)

        assertEquals(1, classifier.callCount)
        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
    }

    // ── 8. Blank userId → Layer 2 skipped, escalate to Layer 3 ───────────────
    //
    // Unregistered user (web before login). Layer 1 used to answer locally; now the only honest
    // outcome is Layer 3, where agentStep short-circuits to ServiceError and the user gets a
    // visible error reply instead of silence.

    @Test
    fun classify_blankUserId_skipsLayer2AndEscalatesToLayer3() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.CreateItem),
            userId = "",
        )

        val result = repo.classify("some input", ChatLocale.En)

        assertEquals(0, classifier.callCount, "Layer 2 must NOT be called when userId is blank")
        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
    }

    // ── 9. Layer 2 called with correct userId and text ───────────────────────

    @Test
    fun classify_layer2_receivesCorrectUserIdAndText() = runTest {
        val expectedUserId = "firebase-uid-abc123"
        val inputText = "move things from monday to next week"
        val (repo, classifier, _) = makeRepo(
            remoteResult = RemoteClassificationResult.NetworkError,
            userId = expectedUserId,
        )

        repo.classify(inputText, ChatLocale.En)

        assertEquals(expectedUserId, classifier.lastUserId)
        assertEquals(inputText, classifier.lastText)
    }

    // ── Deep Thinking toggle ─────────────────────────────────────────────────

    // ── 10. Deep Thinking ON → straight to Layer 3, Layer 2 never charged ────

    @Test
    fun classify_deepThinkingOn_returnsFreeFormWithoutCallingLayer2() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.CreateItem),
            deepThinking = true,
        )

        val result = repo.classify("плануй мою неделю", ChatLocale.Ru)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(1.0f, result.confidence)
        assertEquals(RoutingLayer.FullChat, result.layer)
        assertEquals(0, classifier.callCount,
            "Deep Thinking must skip Layer 2 — an open question costs 3 credits, not 4")
    }

    // ── 11. Deep Thinking ON + command phrase → still Layer 3 (accepted cost) ──
    //
    // Documents a deliberate regression. The old guard (2026-05-18) let a confident Layer 1
    // command win over the toggle so «добавь молоко» stayed free. With Layer 1 disconnected
    // nothing local can recognise a command, so the toggle wins and the user pays 3 credits.
    // Accepted knowingly in the ADR — if this test is what pushes someone to "just add a tiny
    // local check", that check is the forbidden second parser.

    @Test
    fun classify_deepThinkingOn_commandPhrase_stillRoutesToLayer3() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.CreateItem),
            deepThinking = true,
        )

        val result = repo.classify("добавь молоко в покупки", ChatLocale.Ru)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
        assertEquals(0, classifier.callCount)
    }

    // ── 12. Deep Thinking OFF → Layer 2, toggle stays meaningful ─────────────
    //
    // Paired with #10: together they prove ON and OFF are still different routes. If both ever
    // land on the same layer, the toggle is a lie and the change that caused it is wrong.

    @Test
    fun classify_deepThinkingOff_routesToLayer2() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.CreateItem),
            deepThinking = false,
        )

        val result = repo.classify("плануй мою неделю", ChatLocale.Ru)

        assertEquals(1, classifier.callCount, "Deep Thinking OFF must start at Layer 2")
        assertEquals(RoutingLayer.Classifier, result.layer)
    }

    // ── skipLayer1 (reject flow) ─────────────────────────────────────────────

    // ── 13. skipLayer1=true → Layer 2 is called ──────────────────────────────

    @Test
    fun classify_skipLayer1_callsClassifierApi() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertEquals(1, classifier.callCount, "Layer2 classifier must be called when skipLayer1=true")
    }

    // ── 14. skipLayer1=true ignores the Deep Thinking toggle ─────────────────
    //
    // The reject flow ("I meant something else") is an explicit ask to re-interpret a concrete
    // command, so it must start at Layer 2 even with Deep Thinking ON — otherwise rejecting a
    // preview would dump the user into free-form chat. This is the only behaviour `skipLayer1`
    // still controls now that Layer 1 is disconnected.

    @Test
    fun classify_skipLayer1_deepThinkingOn_stillRoutesToLayer2() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.DeleteItem),
            deepThinking = true,
        )

        val result = repo.classify("delete milk", ChatLocale.En, skipLayer1 = true)

        assertEquals(1, classifier.callCount,
            "skipLayer1=true must NOT honour Deep Thinking — the reject flow wants Layer 2")
        assertEquals(RoutingLayer.Classifier, result.layer)
        assertIs<ChatIntent.DeleteItem>(result.intent)
    }

    // ── 15. skipLayer1=true + Layer2 vague → FreeForm for Layer 3 ────────────

    @Test
    fun classify_skipLayer1_layer2Vague_returnsFreeForm() = runTest {
        val (repo, _, _) = makeRepo(
            remoteResult = layer2Success(ChatIntent.FreeForm, confidence = 0.5f),
        )

        val result = repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(1.0f, result.confidence)
        assertEquals(RoutingLayer.FullChat, result.layer)
        assertNull(result.preBuiltToolCall)
    }

    // ── 16. skipLayer1=true + Layer2 NetworkError → FreeForm for Layer 3 ─────

    @Test
    fun classify_skipLayer1_layer2NetworkError_returnsFreeForm() = runTest {
        val (repo, classifier, _) = makeRepo(
            remoteResult = RemoteClassificationResult.NetworkError,
        )

        val result = repo.classify("how do I add an attachment", ChatLocale.En, skipLayer1 = true)

        assertEquals(1, classifier.callCount)
        assertIs<ChatIntent.FreeForm>(result.intent)
        assertEquals(RoutingLayer.FullChat, result.layer)
    }

    // ── completeFreeForm (Layer 3) ───────────────────────────────────────────

    // ── 17. completeFreeForm: success → returns RemoteCompletionResult.Success ──

    @Test
    fun completeFreeForm_success_returnsSuccessResult() = runTest {
        val expectedContent = "Here are some suggestions for your week."
        val (repo, _, completion) = makeRepo(
            completionResult = RemoteCompletionResult.Success(content = expectedContent, creditsRemaining = 297),
            userId = "user-xyz",
        )

        val result = repo.completeFreeForm(
            messages = emptyList(),
            locale = ChatLocale.En,
            checklistsSummary = emptyList(),
        )

        assertEquals(1, completion.callCount)
        assertIs<RemoteCompletionResult.Success>(result)
        assertEquals(expectedContent, result.content)
        assertEquals(297, result.creditsRemaining)
    }

    // ── 18. completeFreeForm: InsufficientCredits propagated ─────────────────

    @Test
    fun completeFreeForm_insufficientCredits_propagatedToCallerUnchanged() = runTest {
        val (repo, _, _) = makeRepo(
            completionResult = RemoteCompletionResult.InsufficientCredits,
            userId = "user-abc",
        )

        val result = repo.completeFreeForm(
            messages = emptyList(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertIs<RemoteCompletionResult.InsufficientCredits>(result)
    }

    // ── 19. completeFreeForm: delegates messages/locale/checklists to completion API ──

    @Test
    fun completeFreeForm_nonBlankUserId_delegatesAllArgsToCompletionApi() = runTest {
        val messages = listOf(
            ChatMessage(id = "1", role = ChatRole.User, content = "plan my week", timestamp = 1L),
        )
        val checklists = listOf(ChecklistContext("Shopping", 5, 2))
        val (repo, _, completion) = makeRepo(
            completionResult = RemoteCompletionResult.Success("Sure!", 280),
            userId = "delegate-user",
        )

        repo.completeFreeForm(messages = messages, locale = ChatLocale.En, checklistsSummary = checklists)

        assertEquals(1, completion.callCount, "completionApi.complete() must be called once")
        assertEquals("delegate-user", completion.lastUserId)
        assertEquals(messages, completion.lastMessages)
        assertEquals(ChatLocale.En, completion.lastLocale)
        assertEquals(checklists, completion.lastChecklistsSummary)
    }

    // ── 20. completeFreeForm: blank userId → ServiceError, zero API calls ─────

    @Test
    fun completeFreeForm_blankUserId_returnsServiceErrorWithoutApiCall() = runTest {
        val (repo, _, completion) = makeRepo(
            completionResult = RemoteCompletionResult.Success("unreachable", 0),
            userId = "",
        )

        val result = repo.completeFreeForm(
            messages = emptyList(),
            locale = ChatLocale.Ru,
            checklistsSummary = emptyList(),
        )

        assertEquals(0, completion.callCount, "API must NOT be called when userId is blank")
        assertIs<RemoteCompletionResult.ServiceError>(result)
    }

    // ── 21. completeFreeForm: NetworkError propagated unchanged ──────────────

    @Test
    fun completeFreeForm_networkError_propagated() = runTest {
        val (repo, _, _) = makeRepo(completionResult = RemoteCompletionResult.NetworkError)

        val result = repo.completeFreeForm(emptyList(), ChatLocale.En, emptyList())

        assertIs<RemoteCompletionResult.NetworkError>(result)
    }
}
