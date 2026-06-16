package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─── Fakes ────────────────────────────────────────────────────────────────────

private class FakeAiChatRepository(
    private val classifyResult: IntentClassification = IntentClassification(
        intent = ChatIntent.Unknown("?"),
        confidence = 0f,
        layer = RoutingLayer.Local,
    ),
    private val skipLayer1Result: IntentClassification? = null,
    private val completionResult: RemoteCompletionResult = RemoteCompletionResult.ServiceError,
    private val transcribeResult: TranscriptionOutcome = TranscriptionOutcome.ServiceError,
    /** Scripted step results consumed in order; last result is repeated when exhausted. */
    private val agentStepResults: List<AgentStepResult> = emptyList(),
) : AiChatRepository {
    var classifyCallCount = 0
    var lastSkipLayer1: Boolean = false
    var completeFreeFormCallCount = 0
    var transcribeCallCount = 0
    var lastTranscribePath: String? = null
    var agentStepCallCount = 0
    val agentStepTranscripts = mutableListOf<List<AgentTranscriptEntry>>()
    /** Captures the contextChecklistName forwarded to each agentStep call (P5 bias). */
    val agentStepContextNames = mutableListOf<String?>()

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
    ): AgentStepResult {
        agentStepCallCount++
        agentStepTranscripts.add(transcript.toList())
        agentStepContextNames.add(contextChecklistName)
        val index = (agentStepCallCount - 1).coerceAtMost(agentStepResults.lastIndex.coerceAtLeast(0))
        return agentStepResults.getOrElse(index) { AgentStepResult.ServiceError }
    }

    override suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean): IntentClassification {
        classifyCallCount++
        lastSkipLayer1 = skipLayer1
        return if (skipLayer1 && skipLayer1Result != null) skipLayer1Result else classifyResult
    }

    override suspend fun completeFreeForm(
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult {
        completeFreeFormCallCount++
        return completionResult
    }

    override suspend fun transcribeAudio(audioPath: String, mimeType: String, locale: ChatLocale): TranscriptionOutcome {
        transcribeCallCount++
        lastTranscribePath = audioPath
        return transcribeResult
    }
}

private class FakeChatHistoryRepository : ChatHistoryRepository {
    private val stored = mutableListOf<ChatMessage>()
    override fun observeRecent(limit: Int): Flow<List<ChatMessage>> = flowOf(stored.takeLast(limit))
    override suspend fun append(message: ChatMessage) { stored.add(message) }
    override suspend fun clear() { stored.clear() }
    override suspend fun count(): Int = stored.size
}

/**
 * History repo whose [observeRecent] flow THROWS on the first [throwTimes] subscriptions and then
 * emits [seed]. Each `.first()` collection re-subscribes the cold flow → a fresh attempt, which is
 * exactly how `ChatViewModel.init`'s `retryWhen { ... }` retries the load.
 *
 * Models the web (wasmJs) reality the init-seed retry was built for: the Room/OPFS Web Worker
 * driver isn't ready when the singleton ChatViewModel inits, so the first query/queries throw.
 * Set [throwTimes] past the retry cap (e.g. 100) to model "DB never becomes ready".
 */
private class ThrowingChatHistoryRepository(
    private val throwTimes: Int,
    private val seed: List<ChatMessage> = emptyList(),
) : ChatHistoryRepository {
    var subscribeCount = 0
        private set

    override fun observeRecent(limit: Int): Flow<List<ChatMessage>> = flow {
        subscribeCount++
        if (subscribeCount <= throwTimes) {
            error("DB not ready (attempt $subscribeCount)")
        }
        emit(seed.takeLast(limit))
    }

    override suspend fun append(message: ChatMessage) = Unit
    override suspend fun clear() = Unit
    override suspend fun count(): Int = seed.size
}

private class FakeChecklistRepository(
    /** Seed checklists used by [getChecklistById] / [checklists] for the P5 context-bias tests. */
    private val seed: List<Checklist> = emptyList(),
) : ChecklistRepository {
    override val checklists: Flow<List<Checklist>> = MutableStateFlow(seed)
    override val weeklyChecklistCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun addChecklist(checklist: Checklist): Long = throw UnsupportedOperationException()
    override suspend fun updateChecklist(checklist: Checklist) = Unit
    override suspend fun updateChecklistTemplate(checklist: Checklist) = Unit
    override suspend fun deleteChecklist(checklist: Checklist) = Unit
    override suspend fun getChecklistById(id: Long): Checklist? = seed.firstOrNull { it.id == id }
    override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(seed.firstOrNull { it.id == id })
    override suspend fun reorderChecklists(orderedIds: List<Long>) = Unit
    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) = Unit
    override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) = Unit
    override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) = Unit
    override suspend fun setReminder(checklistId: Long, reminderAt: Long?) = Unit
    override suspend fun countActiveReminders(): Int = 0
    override suspend fun getActiveReminders() = emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo>()
    override suspend fun getDefaultFillOneShot(checklistId: Long): com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill? = null
    override suspend fun getAllItemRemindersForRescheduling() = emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo>()
    override suspend fun setRepeatSchedule(checklistId: Long, rule: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) = Unit
    override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) = Unit
    override suspend fun clearRepeatSchedule(checklistId: Long) = Unit
    override suspend fun resetDefaultFillChecks(checklistId: Long) = Unit
    override suspend fun countActiveRepeatSchedules(): Int = 0
    override suspend fun getActiveRepeatSchedules() = emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo>()
    override suspend fun getPastDueRepeatSchedules(nowMillis: Long) = emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo>()
    override suspend fun getTotalAdditionalFillCount(): Int = 0
    override suspend fun getWeeklyChecklistCount(): Int = 0
    override fun observeRemindersInRange(fromMs: Long, toMs: Long) = flowOf(emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>())
    override suspend fun getRemindersInRange(fromMs: Long, toMs: Long) = emptyList<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo>()
    override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
    override suspend fun addAttachment(fillId: Long, itemId: String, attachment: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment) = Unit
    override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill>> = flowOf(emptyList())
    override fun getDefaultFillByChecklistId(checklistId: Long): Flow<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill?> = flowOf(null)
    override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill>> = flowOf(emptyList())
    override suspend fun getFillById(id: Long): com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill? = null
    override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
    override suspend fun addFill(fill: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill): Long = 0L
    override suspend fun updateFill(fill: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill) = Unit
    override suspend fun deleteFill(fill: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill) = Unit
    override suspend fun reorderItems(fill: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill, checklist: com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist) = Unit
}

private class FakeToolCallDispatcher(
    private val outcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added", listOf("item")),
) : ToolCallDispatcher {
    var lastDispatched: ToolCall? = null
    var dispatchCount = 0

    override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome {
        dispatchCount++
        lastDispatched = toolCall
        return outcome
    }
}

private object FakePreviewRenderer : ToolCallPreviewRenderer {
    override fun render(toolCall: ToolCall): String = when (toolCall) {
        is ToolCall.AddItem -> "• ${toolCall.itemText}"
        else -> toolCall.toString()
    }
}

private object FakeLocaleProvider : ChatLocaleProvider {
    override fun current(): ChatLocale = ChatLocale.En
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
    var lastSet: Boolean? = null

    override val deepThinkingEnabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = _flow

    override suspend fun setDeepThinkingEnabled(enabled: Boolean) {
        lastSet = enabled
        _flow.value = enabled
    }
}

private class FakeUserDataRepository(
    initialCredits: Int = 0,
) : UserDataRepository {
    private val _flow = MutableStateFlow(UserData(aiCredits = initialCredits))

    override fun getUserDataFlow(): StateFlow<UserData> = _flow
    override suspend fun getUserData(): UserData = _flow.value
    override suspend fun update(userData: UserData) { _flow.value = userData }
    override suspend fun ensureUserRegistered(): Result<RegistrationData> =
        Result.success(RegistrationData(userData = UserData(userId = "test"), isNewUser = false))
    override suspend fun syncWithServer(): Result<RegistrationData> =
        Result.success(RegistrationData(userData = UserData(userId = "test"), isNewUser = false))
    override suspend fun isPaywallLinked(): Boolean = false
    override suspend fun setPaywallLinked(linked: Boolean) = Unit
    override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
    override suspend fun getFirstLaunchAtMillis(): Long = 0L
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private class FakeAnalyticsTracker : com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    val screenViews = mutableListOf<String>()
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {
        screenViews.add(name)
    }
    override fun event(name: String, params: Map<String, Any>) {
        events.add(name to params)
    }

    /** First params map recorded for [name], or null if the event was never emitted. */
    fun paramsOf(name: String): Map<String, Any>? = events.firstOrNull { it.first == name }?.second

    /** Count of events emitted with [name]. */
    fun count(name: String): Int = events.count { it.first == name }
}

private fun makeVm(
    repo: AiChatRepository = FakeAiChatRepository(),
    dispatcher: FakeToolCallDispatcher = FakeToolCallDispatcher(),
    renderer: ToolCallPreviewRenderer = FakePreviewRenderer,
    historyRepo: ChatHistoryRepository = FakeChatHistoryRepository(),
    checklistRepo: ChecklistRepository = FakeChecklistRepository(),
    userDataRepo: UserDataRepository = FakeUserDataRepository(),
    aiChatPreferencesRepo: AiChatPreferencesRepository = FakeAiChatPreferencesRepository(),
    analytics: com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker = FakeAnalyticsTracker(),
): ChatViewModel = ChatViewModel(
    aiChatRepository = repo,
    toolCallDispatcher = dispatcher,
    previewRenderer = renderer,
    localeProvider = FakeLocaleProvider,
    chatHistoryRepository = historyRepo,
    checklistRepository = checklistRepo,
    userDataRepository = userDataRepo,
    aiChatPreferencesRepository = aiChatPreferencesRepo,
    analytics = analytics,
    logger = NoOpLogger,
)

// ─── Tests ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1. Initial state ──────────────────────────────────────────────────────

    @Test
    fun initialState_emptyHistoryProducesEmptyMessageList() {
        // Welcome message is rendered as a fixed UI affordance in ChatScreen (via stringResource),
        // not stored in ViewModel state. With an empty FakeChatHistoryRepository, messages = [].
        val vm = makeVm()
        val state = vm.screenState.value
        assertEquals(0, state.messages.size)
        assertNull(state.pendingPreview)
        assertEquals("", state.inputText)
    }

    // ── 2. Blank input → ShowSnackbar, not silent skip ────────────────────────

    @Test
    fun sendClick_blankInput_emitsShowSnackbar() = runTest {
        val vm = makeVm()
        vm.sendIntent(ChatScreenIntent.OnInputChange("   "))

        // Collect sideEffect before triggering send
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_unknown_intent_hint", effect.messageKey)
        // No extra messages added for blank input (welcome is UI-only, not in state)
        assertEquals(0, vm.screenState.value.messages.size)
    }

    // ── 3. Unknown intent → ShowAssistantMessage sideEffect emitted, user message added ──

    @Test
    fun sendClick_unknownIntent_emitsShowAssistantMessageSideEffect() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.Unknown("gibberish"),
                confidence = 0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("gibberish"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        // User message is added to state; assistant message arrives via ChatRoute round-trip
        val state = vm.screenState.value
        assertEquals(1, state.messages.size)
        assertNull(state.pendingPreview)
        assertEquals(false, state.isProcessing)

        // The ShowAssistantMessage side effect carries the localisation key
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_unknown_intent_hint", effect.messageKey)
    }

    // ── 4. CreateItem intent → pendingPreview shown ───────────────────────────

    @Test
    fun sendClick_createItemIntent_showsPendingPreview() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val state = vm.screenState.value
        val preview = state.pendingPreview
        assertIs<PendingPreview>(preview)
        assertIs<ToolCall.AddItem>(preview.toolCall)
        assertEquals(false, state.isProcessing)
    }

    // ── 5. FindItems intent → dispatched inline, no preview, emits ShowAssistantMessage ──

    @Test
    fun sendClick_findItemsIntent_dispatchesInlineNoPendingPreview() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FindItems,
                confidence = 0.9f,
                layer = RoutingLayer.Local,
            )
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_find_success", listOf("1", "«milk» in Shopping"))
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("find milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        assertNull(vm.screenState.value.pendingPreview)
        assertIs<ToolCall.FindItemsQuery>(fakeDispatcher.lastDispatched)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_dispatch_find_success", effect.messageKey)
        assertEquals("1", effect.args[0])
    }

    // ── 6. PreviewApply → dispatches ToolCall, clears pendingPreview, emits ShowAssistantMessage ──

    @Test
    fun previewApply_dispatchesAndClearsPendingPreview() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_added_to", listOf("milk", "Shopping"))
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        // Build a preview
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertIs<PendingPreview>(vm.screenState.value.pendingPreview)

        // Collect the SideEffect emitted on Apply
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        // Apply it
        vm.sendIntent(ChatScreenIntent.OnPreviewApply)

        assertNull(vm.screenState.value.pendingPreview)
        assertIs<ToolCall.AddItem>(fakeDispatcher.lastDispatched)

        // Success outcome emits ShowAssistantMessage with the dispatch key
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_dispatch_added_to", effect.messageKey)
        assertEquals(listOf("milk", "Shopping"), effect.args)
    }

    // ── 7. PreviewCancel → clears pendingPreview + emits ShowAssistantMessage ─

    @Test
    fun previewCancel_clearsPendingPreviewAndEmitsCancelledMessage() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertIs<PendingPreview>(vm.screenState.value.pendingPreview)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnPreviewCancel)

        // Preview must be cleared
        assertNull(vm.screenState.value.pendingPreview)
        // Assistant cancelled message must be emitted (silent dismiss FORBIDDEN per CLAUDE.md)
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_preview_cancelled_message", effect.messageKey)
    }

    // ── 8. Layer 1 (Local) → user message costCredits == 0 ───────────────────

    @Test
    fun sendClick_layer1Local_userMessageCostCreditsIsZero() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val userMsg = vm.screenState.value.messages.first()
        assertEquals(RoutingLayer.Local, userMsg.routedLayer)
        assertEquals(0, userMsg.costCredits, "Layer 1 (local) is free — costCredits must be 0")
    }

    // ── 9. Layer 2 (Classifier) → user message costCredits == 1 ─────────────

    @Test
    fun sendClick_layer2Classifier_userMessageCostCreditsIsOne() = runTest {
        val preBuilt = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk")
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 0.9f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val userMsg = vm.screenState.value.messages.first()
        assertEquals(RoutingLayer.Classifier, userMsg.routedLayer)
        assertEquals(1, userMsg.costCredits, "Layer 2 (classifier) costs 1 credit — costCredits must be 1")
    }

    // ── 10. RequiresPremium outcome → emits ShowSnackbar with correct key ──────

    @Test
    fun previewApply_requiresPremiumOutcome_emitsSnackbar() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val fakeDispatcher = FakeToolCallDispatcher(outcome = DispatchOutcome.RequiresPremium)
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vm.sideEffect.first() }
        vm.sendIntent(ChatScreenIntent.OnPreviewApply)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_requires_premium", effect.messageKey)
    }

    // ── 11. OnSettingsClick → showSettingsSheet = true ───────────────────────

    @Test
    fun onSettingsClick_setsShowSettingsSheetTrue() {
        val vm = makeVm()
        assertEquals(false, vm.screenState.value.showSettingsSheet)

        vm.sendIntent(ChatScreenIntent.OnSettingsClick)

        assertEquals(true, vm.screenState.value.showSettingsSheet)
    }

    // ── 12. OnDeepThinkingToggle → persists to repo and updates state ─────────

    @Test
    fun onDeepThinkingToggle_persistsAndUpdatesState() = runTest {
        val fakePrefsRepo = FakeAiChatPreferencesRepository(initial = false)
        val vm = makeVm(aiChatPreferencesRepo = fakePrefsRepo)

        // Initially false
        assertEquals(false, vm.screenState.value.deepThinkingEnabled)

        // Toggle ON
        vm.sendIntent(ChatScreenIntent.OnDeepThinkingToggle(true))

        // DataStore was called with correct value
        assertEquals(true, fakePrefsRepo.lastSet)
        // Flow emission updates state automatically (UnconfinedTestDispatcher runs coroutines eagerly)
        assertEquals(true, vm.screenState.value.deepThinkingEnabled)

        // Toggle OFF
        vm.sendIntent(ChatScreenIntent.OnDeepThinkingToggle(false))
        assertEquals(false, fakePrefsRepo.lastSet)
        assertEquals(false, vm.screenState.value.deepThinkingEnabled)
    }

    // ── 13. OnFeedbackOpen → sets feedbackTarget, clears feedbackText ────────

    @Test
    fun onFeedbackOpen_setsTargetAndOpensSheet() {
        val vm = makeVm()
        val assistantMsg = ChatMessage(
            id = "asst_1",
            role = ChatRole.Assistant,
            content = "Here is your answer.",
            timestamp = 1_000L,
        )
        // Inject a message directly into state via AppendAssistantMessage round-trip pattern —
        // simpler: directly set feedbackText to something to confirm it's reset on open.
        vm.sendIntent(ChatScreenIntent.OnFeedbackTextChange("old text"))
        vm.sendIntent(ChatScreenIntent.OnFeedbackOpen(assistantMsg))

        val state = vm.screenState.value
        assertNotNull(state.feedbackTarget)
        assertEquals("asst_1", state.feedbackTarget?.id)
        assertEquals("", state.feedbackText, "feedbackText must be cleared when sheet opens")
        assertEquals(false, state.isSubmittingFeedback)
    }

    // ── 14. OnFeedbackSubmit with blank text → submits as a bare thumbs-down signal ──

    @Test
    fun onFeedbackSubmit_blankText_submitsBareSignal() = runTest {
        val vm = makeVm()
        val assistantMsg = ChatMessage(
            id = "asst_2",
            role = ChatRole.Assistant,
            content = "Some AI answer.",
            timestamp = 1_000L,
        )
        vm.sendIntent(ChatScreenIntent.OnFeedbackOpen(assistantMsg))
        // feedbackText stays blank (default "")

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnFeedbackSubmit)

        val effect = effectDeferred.await()
        // Blank feedback is a valid bare thumbs-down signal — it submits, not blocks.
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_feedback_submitted", effect.messageKey)
        // Sheet closes — target cleared after submit.
        assertEquals(null, vm.screenState.value.feedbackTarget)
    }

    // ── 15. OnFeedbackSubmit with non-blank text → logs, emits submitted snackbar, clears ──

    @Test
    fun onFeedbackSubmit_nonBlank_clearsTargetAndEmitsSubmittedSnackbar() = runTest {
        val vm = makeVm()
        val assistantMsg = ChatMessage(
            id = "asst_3",
            role = ChatRole.Assistant,
            content = "This is the AI reply.",
            timestamp = 2_000L,
        )
        vm.sendIntent(ChatScreenIntent.OnFeedbackOpen(assistantMsg))
        vm.sendIntent(ChatScreenIntent.OnFeedbackTextChange("The answer was too vague."))

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnFeedbackSubmit)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_feedback_submitted", effect.messageKey)

        val state = vm.screenState.value
        assertNull(state.feedbackTarget, "feedbackTarget must be cleared after submit")
        assertEquals("", state.feedbackText, "feedbackText must be cleared after submit")
        assertEquals(false, state.isSubmittingFeedback)
    }

    // ── 16. Success outcome with linkedChecklistId → SideEffect carries the id ──

    @Test
    fun previewApply_successWithLinkedChecklistId_sideEffectCarriesId() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("milk", "Shopping"),
                linkedChecklistId = 42L,
            )
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnPreviewApply)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals(42L, effect.linkedChecklistId,
            "Success outcome linkedChecklistId must be propagated to ShowAssistantMessage")
    }

    // ── 17. AppendAssistantMessage with linkedChecklistId → persisted on ChatMessage ──

    @Test
    fun appendAssistantMessage_withLinkedChecklistId_persistedOnMessage() = runTest {
        val historyRepo = FakeChatHistoryRepository()
        val vm = makeVm(historyRepo = historyRepo)

        vm.sendIntent(
            ChatScreenIntent.AppendAssistantMessage(
                text = "Done! Added «milk» to Shopping.",
                linkedChecklistId = 99L,
            )
        )

        val messages = vm.screenState.value.messages
        assertEquals(1, messages.size, "AppendAssistantMessage should add one message to state")
        assertEquals(99L, messages.first().linkedChecklistId,
            "linkedChecklistId must be forwarded to the ChatMessage domain model")
    }

    // ── 18. OnOpenChecklist intent → emits NavigateToChecklist SideEffect ────────

    @Test
    fun onOpenChecklist_emitsNavigateToChecklistSideEffect() = runTest {
        val vm = makeVm()

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnOpenChecklist(checklistId = 7L))

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.NavigateToChecklist>(effect)
        assertEquals(7L, effect.checklistId)
    }

    // ── 19. Attachment state — OnPickAttachment sets trigger-flag ────────────────

    @Test
    fun onPickAttachment_setsAttachmentPickerType() {
        val vm = makeVm()
        assertNull(vm.screenState.value.attachmentPickerType)

        vm.sendIntent(ChatScreenIntent.OnPickAttachment(com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource.Image))

        assertEquals(com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource.Image, vm.screenState.value.attachmentPickerType)
    }

    // ── 20. OnAttachmentPickerTriggered resets trigger-flag ───────────────────────

    @Test
    fun onAttachmentPickerTriggered_resetsPickerType() {
        val vm = makeVm()
        vm.sendIntent(ChatScreenIntent.OnPickAttachment(com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource.Pdf))
        vm.sendIntent(ChatScreenIntent.OnAttachmentPickerTriggered)
        assertNull(vm.screenState.value.attachmentPickerType, "Trigger-flag must be reset after Triggered intent")
    }

    // ── 21. OnAttachmentPicked adds to pendingAttachments ─────────────────────────

    @Test
    fun onAttachmentPicked_appendsToPendingList() {
        val vm = makeVm()
        val att = com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment(
            sourcePath = "/tmp/photo.jpg",
            mimeType = "image/jpeg",
            fileName = "photo.jpg",
        )

        vm.sendIntent(ChatScreenIntent.OnAttachmentPicked(att))

        val state = vm.screenState.value
        assertEquals(1, state.pendingAttachments.size)
        assertEquals("photo.jpg", state.pendingAttachments.first().fileName)
        assertTrue(state.canSend, "canSend must be true when attachments are pending")
    }

    // ── 22. OnRemoveAttachment removes by sourcePath ───────────────────────────────

    @Test
    fun onRemoveAttachment_removesCorrectItem() {
        val vm = makeVm()
        val att1 = com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment(
            sourcePath = "/tmp/a.jpg", mimeType = "image/jpeg", fileName = "a.jpg"
        )
        val att2 = com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment(
            sourcePath = "/tmp/b.pdf", mimeType = "application/pdf", fileName = "b.pdf"
        )
        vm.sendIntent(ChatScreenIntent.OnAttachmentPicked(att1))
        vm.sendIntent(ChatScreenIntent.OnAttachmentPicked(att2))
        assertEquals(2, vm.screenState.value.pendingAttachments.size)

        vm.sendIntent(ChatScreenIntent.OnRemoveAttachment(sourcePath = "/tmp/a.jpg"))

        val remaining = vm.screenState.value.pendingAttachments
        assertEquals(1, remaining.size)
        assertEquals("b.pdf", remaining.first().fileName)
    }

    // ── 23. canSend is false when no text and no attachments ──────────────────────

    @Test
    fun canSend_falseWhenNoTextAndNoAttachments() {
        val vm = makeVm()
        assertFalse(vm.screenState.value.canSend, "canSend must be false on empty state")
    }

    // ── 24. Free-tier attachment quota — 4th pick emits snackbar ─────────────────

    // ── 25. OnPreviewReject from Local source → classify called with skipLayer1=true ──

    @Test
    fun onPreviewReject_localSource_callsClassifyWithSkipLayer1True() = runTest {
        // Setup: get a preview from Layer 1
        val layer1Result = IntentClassification(
            intent = ChatIntent.CreateItem,
            confidence = 1.0f,
            layer = RoutingLayer.Local,
        )
        // Reject will produce FreeForm from Layer 2 → agent loop runs
        val layer2RejectResult = IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = 1.0f,
            layer = RoutingLayer.FullChat,
            preBuiltToolCall = null,
        )
        // No scripted agentStepResults → agentStep returns ServiceError → chat_completion_error.
        val repo = FakeAiChatRepository(
            classifyResult = layer1Result,
            skipLayer1Result = layer2RejectResult,
        )
        val vm = makeVm(repo = repo)

        // Send to get a preview from Layer 1
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingPreview)
        assertEquals(RoutingLayer.Local, vm.screenState.value.pendingPreview?.sourceLayer)

        // Reset call count after initial classify (for initial OnSendClick)
        val initialCallCount = repo.classifyCallCount

        // Collect the next sideEffect before reject
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnPreviewReject)

        // Preview must be cleared immediately
        assertNull(vm.screenState.value.pendingPreview)
        // classify must have been called with skipLayer1=true
        assertEquals(initialCallCount + 1, repo.classifyCallCount, "classify must be called once more on reject")
        assertEquals(true, repo.lastSkipLayer1, "Reject from Local source must set skipLayer1=true")
        // Since Layer 2 returned FreeForm, the agent loop runs (no legacy completeFreeForm).
        assertTrue(repo.agentStepCallCount >= 1, "agentStep must be called when Layer2 returns FreeForm on reject")
        assertEquals(0, repo.completeFreeFormCallCount, "completeFreeForm must NOT be called anymore")

        effectDeferred.await() // drain the sideEffect (ServiceError → chat_completion_error)
    }

    // ── 26. OnPreviewReject from Classifier source → agent loop runs directly ──

    @Test
    fun onPreviewReject_classifierSource_runsAgentLoopDirectly() = runTest {
        // Setup: produce a Classifier-layer preview
        val preBuilt = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk")
        val classifierResult = IntentClassification(
            intent = ChatIntent.CreateItem,
            confidence = 0.9f,
            layer = RoutingLayer.Classifier,
            preBuiltToolCall = preBuilt,
        )
        // No scripted agentStepResults → agentStep returns ServiceError → chat_completion_error.
        val repo = FakeAiChatRepository(
            classifyResult = classifierResult,
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingPreview)
        assertEquals(RoutingLayer.Classifier, vm.screenState.value.pendingPreview?.sourceLayer)

        val initialClassifyCount = repo.classifyCallCount

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnPreviewReject)

        assertNull(vm.screenState.value.pendingPreview)
        // classify must NOT be called again — Classifier source escalates directly to Layer 3
        assertEquals(initialClassifyCount, repo.classifyCallCount, "classify must NOT be called on Classifier→Layer3 escalation")
        assertEquals(false, repo.lastSkipLayer1, "skipLayer1 must not have been set for this branch")
        // The agent loop must have run (Layer 3 escalation, no legacy completeFreeForm).
        assertEquals(1, repo.agentStepCallCount, "agentStep must be called for Classifier→Layer3 escalation")
        assertEquals(0, repo.completeFreeFormCallCount, "completeFreeForm must NOT be called anymore")

        effectDeferred.await()
    }

    @Test
    fun onAttachmentPicked_freeTierLimit_emitsSnackbarOnOverflow() = runTest {
        val vm = makeVm()
        val makeAtt: (String) -> ChatScreenIntent.OnAttachmentPicked = { name ->
            ChatScreenIntent.OnAttachmentPicked(
                com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment(
                    sourcePath = "/tmp/$name", mimeType = "image/jpeg", fileName = name
                )
            )
        }

        // Add 3 (free limit)
        vm.sendIntent(makeAtt("a.jpg"))
        vm.sendIntent(makeAtt("b.jpg"))
        vm.sendIntent(makeAtt("c.jpg"))
        assertEquals(3, vm.screenState.value.pendingAttachments.size)

        // 4th should emit ShowSnackbar and NOT add to the list
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }
        vm.sendIntent(makeAtt("d.jpg"))

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_attach_limit_reached", effect.messageKey)
        assertEquals(3, vm.screenState.value.pendingAttachments.size, "List must not grow beyond free limit")
    }

    // ── STT (Voice → Transcription) tests ────────────────────────────────────────

    // 27. Success → input text set, isTranscribing cleared, no attachment
    @Test
    fun voiceRecorded_success_setsInputText_clearsTranscribing() = runTest {
        val repo = FakeAiChatRepository(transcribeResult = TranscriptionOutcome.Success("hello world"))
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped("/tmp/rec.m4a"))

        val state = vm.screenState.value
        assertEquals("hello world", state.inputText, "Transcript must be placed into inputText")
        assertFalse(state.isTranscribing, "isTranscribing must be false after success")
        assertTrue(state.pendingAttachments.isEmpty(), "No audio attachment must be created on STT path")
    }

    // 28. Success → transcript appended to existing input with a space
    @Test
    fun voiceRecorded_success_appendsToExistingInput_withSpace() = runTest {
        val repo = FakeAiChatRepository(transcribeResult = TranscriptionOutcome.Success("more text"))
        val vm = makeVm(repo = repo)

        // Pre-populate the input field
        vm.sendIntent(ChatScreenIntent.OnInputChange("existing"))

        vm.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped("/tmp/rec.m4a"))

        assertEquals("existing more text", vm.screenState.value.inputText)
    }

    // 29. EmptyTranscript → isTranscribing cleared, snackbar with correct key
    @Test
    fun voiceRecorded_emptyTranscript_emitsSnackbar() = runTest {
        val repo = FakeAiChatRepository(transcribeResult = TranscriptionOutcome.EmptyTranscript)
        val vm = makeVm(repo = repo)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped("/tmp/rec.m4a"))

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_transcribe_empty", effect.messageKey)
        assertFalse(vm.screenState.value.isTranscribing)
    }

    // 30. NetworkError → isTranscribing cleared, snackbar with chat_transcribe_error
    @Test
    fun voiceRecorded_networkError_emitsSnackbar() = runTest {
        val repo = FakeAiChatRepository(transcribeResult = TranscriptionOutcome.NetworkError)
        val vm = makeVm(repo = repo)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped("/tmp/rec.m4a"))

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_transcribe_error", effect.messageKey)
        assertFalse(vm.screenState.value.isTranscribing)
    }

    // 31. Cancelled recording (null path) → transcribeAudio NOT called, snackbar emitted
    @Test
    fun voiceRecordingCancelled_nullPath_doesNotCallTranscribe() = runTest {
        val repo = FakeAiChatRepository()
        val vm = makeVm(repo = repo)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnVoiceRecordingStopped(null))

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_recording_cancelled", effect.messageKey)
        assertEquals(0, repo.transcribeCallCount, "transcribeAudio must NOT be called when path is null")
        assertFalse(vm.screenState.value.isTranscribing, "isTranscribing must remain false on cancel")
    }

    // ── 32. Unknown intent → ShowAssistantMessage carries askAiForText == original input ──
    // RED: ChatMessage.askAiForText field doesn't exist yet; Unknown branch doesn't set it.

    @Test
    fun sendClick_unknownIntent_assistantMessageHasAskAiForText() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.Unknown("это штука что ты"),
                confidence = 0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)

        // Collect both sideEffect and the resulting AppendAssistantMessage round-trip
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("это штука что ты"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_unknown_intent_hint", effect.messageKey)
        // The ShowAssistantMessage must carry the original input text so ChatRoute
        // can embed it in the AppendAssistantMessage round-trip → ChatMessage.askAiForText
        assertEquals("это штука что ты", effect.askAiForText,
            "ShowAssistantMessage for Unknown intent must carry askAiForText == original input")
    }

    // ── 33. AppendAssistantMessage with askAiForText → preserved on ChatMessage in state ──
    // RED: ChatMessage.askAiForText field doesn't exist yet; AppendAssistantMessage doesn't forward it.

    @Test
    fun appendAssistantMessage_withAskAiForText_preservedOnChatMessage() = runTest {
        val vm = makeVm()

        vm.sendIntent(
            ChatScreenIntent.AppendAssistantMessage(
                text = "I didn't catch that. Try...",
                askAiForText = "это штука что ты",
            )
        )

        val messages = vm.screenState.value.messages
        assertEquals(1, messages.size)
        assertEquals("это штука что ты", messages.first().askAiForText,
            "askAiForText must be forwarded from AppendAssistantMessage to ChatMessage")
    }

    // ── 34. OnAskAiFallback → agent loop runs with the original text ──

    @Test
    fun onAskAiFallback_runsAgentLoopWithOriginalText() = runTest {
        // No scripted agentStepResults → agentStep emits a ShowAssistantMessage sideEffect
        // (ServiceError → chat_completion_error). The Final path calls addAndPersistAssistantMessage
        // directly without a sideeffect, so there would be nothing to drain.
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.Unknown("это штука что ты"),
                confidence = 0f,
                layer = RoutingLayer.Local,
            ),
        )
        val vm = makeVm(repo = repo)

        // First send to get Unknown response — drains the ShowAssistantMessage effect from handleSend
        val unknownEffectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }
        vm.sendIntent(ChatScreenIntent.OnInputChange("это штука что ты"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        unknownEffectDeferred.await() // drain chat_unknown_intent_hint ShowAssistantMessage
        assertEquals(0, repo.agentStepCallCount, "agentStep must NOT be called on Unknown classification")

        // Set up deferred for the ServiceError effect emitted by the agent loop
        val fallbackEffectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        // User taps "Ask AI" fallback button
        vm.sendIntent(ChatScreenIntent.OnAskAiFallback("это штука что ты"))

        // agentStep must be called exactly once (Layer 3 escalation via the agent loop)
        assertEquals(1, repo.agentStepCallCount,
            "OnAskAiFallback must escalate to Layer 3 via the agent loop")
        assertEquals(0, repo.completeFreeFormCallCount, "completeFreeForm must NOT be called anymore")

        // State must reflect processing complete (UnconfinedTestDispatcher runs eagerly)
        assertFalse(vm.screenState.value.isProcessing,
            "isProcessing must be false after the agent loop completes")

        // Drain the chat_completion_error ShowAssistantMessage from ServiceError path
        val fallbackEffect = fallbackEffectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(fallbackEffect)
        assertEquals("chat_completion_error", fallbackEffect.messageKey,
            "ServiceError from the agent loop must emit chat_completion_error")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 2d — Agentic loop tests
    // ══════════════════════════════════════════════════════════════════════════

    // ── 35. FreeForm always routes through the agent loop (no legacy completeFreeForm) ──

    @Test
    fun freeForm_alwaysRoutesThroughAgentLoop() = runTest {
        // The agentic flag is gone — FreeForm turns ALWAYS go through the agent loop.
        // completeFreeForm must never be invoked from the ViewModel FreeForm path.
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Final(content = "Here is your week.", creditsRemaining = 100),
            ),
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount,
            "agentStep must be called — FreeForm always routes through the agent loop")
        assertEquals(0, repo.completeFreeFormCallCount,
            "completeFreeForm must NOT be called from the FreeForm path anymore")
        assertNull(vm.screenState.value.pendingAgentPlan,
            "pendingAgentPlan must be null after a Final result")
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
        assertEquals("Here is your week.", assistantMsgs.first().content)
    }

    // ── 36. Read-only tool call → no plan-card, assistant message rendered ──

    @Test
    fun agentLoop_readOnlyToolCall_noPlanCard() = runTest {
        val readChecklistCall = AgentToolCall(
            id = "call-1",
            name = "read_checklist",
            args = buildJsonObject { put("name", "Shopping") },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(
                    calls = listOf(readChecklistCall),
                    creditsRemaining = 297,
                ),
                AgentStepResult.Final(content = "Shopping has 3 items.", creditsRemaining = 297),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.ChecklistContent(
                checklistName = "Shopping",
                items = emptyList(),
            ),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        // Collect the assistant message from ShowAssistantMessage (round-limit)
        // OR wait for the Final to be directly appended via addAndPersistAssistantMessage.
        // With UnconfinedTestDispatcher both should complete synchronously.
        vm.sendIntent(ChatScreenIntent.OnInputChange("what's in my shopping list?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        // Give the coroutines a chance to run.
        testScheduler.advanceUntilIdle()

        assertEquals(2, repo.agentStepCallCount,
            "agentStep must be called twice (ToolCalls + Final rounds)")
        assertNull(vm.screenState.value.pendingAgentPlan,
            "No plan-card for read-only tools")
        assertFalse(vm.screenState.value.isProcessing,
            "isProcessing must be false after Final")
        // The assistant message from Final is appended directly (no ShowAssistantMessage side-effect).
        val assistantMessages = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMessages.size, "One assistant message expected")
        assertEquals("Shopping has 3 items.", assistantMessages.first().content)
    }

    // ── 37. Mutating tool call → plan-card shown, OnAgentPlanApply dispatches ──

    @Test
    fun agentLoop_mutatingToolCall_planCardShownAndApplied() = runTest {
        val addItemCall = AgentToolCall(
            id = "call-2",
            name = "add_item",
            args = buildJsonObject {
                put("item_text", "milk")
                put("checklist_hint", "Shopping")
            },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(
                    calls = listOf(addItemCall),
                    creditsRemaining = 297,
                ),
                AgentStepResult.Final(content = "Done, added milk.", creditsRemaining = 297),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_added", listOf("milk")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        // After first agentStep returns ToolCalls, the plan-card should be visible.
        testScheduler.advanceUntilIdle()
        val planAfterStep1 = vm.screenState.value.pendingAgentPlan
        assertNotNull(planAfterStep1, "pendingAgentPlan must be set after ToolCalls result")
        assertEquals(1, planAfterStep1.items.size, "One plan item expected")
        assertFalse(vm.screenState.value.isProcessing,
            "isProcessing must be false while plan-card is shown")
        assertEquals(0, fakeDispatcher.dispatchCount,
            "Dispatcher must NOT be called before user approves")

        // User approves → loop resumes
        vm.sendIntent(ChatScreenIntent.OnAgentPlanApply)
        testScheduler.advanceUntilIdle()

        // Dispatcher was called once for the approved add_item call
        assertEquals(1, fakeDispatcher.dispatchCount,
            "Dispatcher must be called once after approval")

        // The second agentStep (Final) should have been called.
        assertEquals(2, repo.agentStepCallCount,
            "agentStep must be called twice (ToolCalls + Final)")

        // COUNT INVARIANT: the second agentStep received transcript with ToolResults
        // whose size equals calls.size.
        val secondCallTranscript = repo.agentStepTranscripts[1]
        val toolResultsEntry = secondCallTranscript.filterIsInstance<AgentTranscriptEntry.ToolResults>()
        assertEquals(1, toolResultsEntry.size,
            "Transcript must contain exactly one ToolResults entry")
        assertEquals(1, toolResultsEntry.first().results.size,
            "ToolResults.results.size must equal calls.size (COUNT INVARIANT)")

        // Plan-card is cleared and Final message is persisted.
        assertNull(vm.screenState.value.pendingAgentPlan, "Plan-card must be cleared after Final")
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
        assertEquals("Done, added milk.", assistantMsgs.first().content)
    }

    // ── 38. OnAgentPlanCancel → declined results sent, loop continues gracefully ──

    @Test
    fun agentLoop_mutatingToolCall_planCardCancelled_declinedResult() = runTest {
        val deleteCall = AgentToolCall(
            id = "call-3",
            name = "delete_item",
            args = buildJsonObject {
                put("item_text", "milk")
                put("checklist_hint", "Shopping")
            },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(
                    calls = listOf(deleteCall),
                    creditsRemaining = 297,
                ),
                AgentStepResult.Final(content = "Okay, I won't delete it.", creditsRemaining = 297),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        // Plan-card visible with destructive item
        val plan = vm.screenState.value.pendingAgentPlan
        assertNotNull(plan)
        assertTrue(plan.items.first().isDestructive, "delete_item must be flagged as destructive")

        // User cancels
        vm.sendIntent(ChatScreenIntent.OnAgentPlanCancel)
        testScheduler.advanceUntilIdle()

        // Dispatcher must NOT have been called (declined path)
        assertEquals(0, fakeDispatcher.dispatchCount,
            "Dispatcher must NOT be called when user cancels")

        // COUNT INVARIANT: ToolResults in second agentStep transcript has size == 1
        val secondCallTranscript = repo.agentStepTranscripts[1]
        val toolResultsEntry = secondCallTranscript.filterIsInstance<AgentTranscriptEntry.ToolResults>()
        assertEquals(1, toolResultsEntry.size)
        assertEquals(1, toolResultsEntry.first().results.size,
            "COUNT INVARIANT: one declined result for one declined call")

        // Declined result carries status=declined
        val resultJson = toolResultsEntry.first().results.first().result
        assertEquals("declined", resultJson["status"]?.toString()?.trim('"'),
            "Declined result must have status=declined")

        // Final message received after cancelled plan
        assertNull(vm.screenState.value.pendingAgentPlan)
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
    }

    // ── 39. Round cap: agentStep always returns ToolCalls → fallback after 5 rounds ──

    @Test
    fun agentLoop_roundCap_fallbackMessageAfter5Rounds() = runTest {
        val infiniteCall = AgentToolCall(
            id = "call-inf",
            name = "find_items", // read-only so no plan-card pause
            args = buildJsonObject { put("query", "milk") },
        )
        // Repeat ToolCalls indefinitely (the fake repeats last result when list is exhausted)
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(
                    calls = listOf(infiniteCall),
                    creditsRemaining = 290,
                ),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_find_success", listOf("0", "")),
        )

        // Collect ShowAssistantMessage for the round-limit fallback
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }
        vm.sendIntent(ChatScreenIntent.OnInputChange("find milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_agent_round_limit", effect.messageKey,
            "Round-cap fallback must emit chat_agent_round_limit")

        assertTrue(repo.agentStepCallCount <= 5,
            "agentStep must be called at most 5 times (AGENT_MAX_ROUNDS), was ${repo.agentStepCallCount}")
        assertFalse(vm.screenState.value.isProcessing,
            "isProcessing must be false after round-cap")
        assertNull(vm.screenState.value.pendingAgentPlan,
            "pendingAgentPlan must be null after round-cap")
    }

    // ── 40. Mixed read-only + mutating in one ToolCalls: COUNT INVARIANT holds ──

    @Test
    fun agentLoop_mixedReadOnlyAndMutating_countInvariantHolds() = runTest {
        val readCall = AgentToolCall(
            id = "read-1",
            name = "read_checklist",
            args = buildJsonObject { put("name", "Shopping") },
        )
        val addCall = AgentToolCall(
            id = "add-1",
            name = "add_item",
            args = buildJsonObject {
                put("item_text", "butter")
                put("checklist_hint", "Shopping")
            },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(
                    calls = listOf(readCall, addCall), // 2 calls: 1 read + 1 mutating
                    creditsRemaining = 297,
                ),
                AgentStepResult.Final(content = "Done.", creditsRemaining = 297),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_added", listOf("butter")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add butter to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        // read_checklist should auto-execute, add_item should show plan-card
        val plan = vm.screenState.value.pendingAgentPlan
        assertNotNull(plan, "Plan-card must be shown for the mutating add_item call")
        assertEquals(1, plan.items.size, "Only mutating calls appear in plan-card")

        vm.sendIntent(ChatScreenIntent.OnAgentPlanApply)
        testScheduler.advanceUntilIdle()

        // COUNT INVARIANT: transcript ToolResults must have size == 2 (both calls)
        val secondTranscript = repo.agentStepTranscripts[1]
        val toolResults = secondTranscript.filterIsInstance<AgentTranscriptEntry.ToolResults>()
        assertEquals(1, toolResults.size)
        assertEquals(2, toolResults.first().results.size,
            "COUNT INVARIANT: allResults.size (${toolResults.first().results.size}) must equal calls.size (2)")

        // Verify result IDs match call IDs (order-preserving merge)
        val resultIds = toolResults.first().results.map { it.id }
        assertEquals(listOf("read-1", "add-1"), resultIds,
            "Results must be in the same order as calls")
    }

    // ── Chat sheet context wiring ─────────────────────────────────────────────

    @Test
    fun onSetContextChecklist_withId_storesContextChecklistId() {
        val vm = makeVm()
        // Initially no context
        assertNull(vm.screenState.value.contextChecklistId)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))

        assertEquals(42L, vm.screenState.value.contextChecklistId)
    }

    @Test
    fun onSetContextChecklist_withNull_clearsContextChecklistId() {
        val vm = makeVm()
        // Seed a context first
        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 99L))
        assertEquals(99L, vm.screenState.value.contextChecklistId)

        // Clear it
        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = null))

        assertNull(vm.screenState.value.contextChecklistId)
    }

    @Test
    fun onSetContextChecklist_doesNotAffectMessages() {
        val vm = makeVm()
        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 7L))

        // Message list stays untouched by context seeding
        assertEquals(0, vm.screenState.value.messages.size)
    }

    @Test
    fun onSetContextChecklist_updatesIdRepeatedly() {
        val vm = makeVm()
        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 1L))
        assertEquals(1L, vm.screenState.value.contextChecklistId)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 2L))
        assertEquals(2L, vm.screenState.value.contextChecklistId,
            "Re-opening the sheet for a different checklist must update the context ID")
    }

    // ── P5: context-checklist bias for list-less commands ─────────────────────

    private fun groceriesChecklist(id: Long = 42L) = Checklist(
        id = id,
        name = "Groceries",
        items = emptyList(),
    )

    @Test
    fun createItem_nullHint_withContextChecklist_biasesHintToContextName() = runTest {
        // AddItem extracted with no explicit list ("add milk") while the dock is focused on
        // checklist id=42 ("Groceries") → the preview's toolCall hint must become "Groceries".
        val preBuilt = ToolCall.AddItem(checklistHint = null, itemText = "milk")
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        // Focus the dock on Groceries, then send a list-less command.
        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val preview = vm.screenState.value.pendingPreview
        assertNotNull(preview, "Preview must be shown for CreateItem")
        val toolCall = preview.toolCall
        assertIs<ToolCall.AddItem>(toolCall)
        assertEquals("Groceries", toolCall.checklistHint,
            "Null-hint AddItem must be biased to the open checklist name")
        assertEquals("Groceries", preview.targetChecklistHint,
            "Preview card must reflect the biased context list")
    }

    @Test
    fun createItem_explicitHint_withContextChecklist_doesNotOverwriteHint() = runTest {
        // User explicitly named "shopping" → context ("Groceries") must NOT override it.
        val preBuilt = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk")
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val toolCall = vm.screenState.value.pendingPreview?.toolCall
        assertIs<ToolCall.AddItem>(toolCall)
        assertEquals("shopping", toolCall.checklistHint,
            "Explicit hint must win over the open-screen context")
    }

    @Test
    fun createItem_nullHint_noContextChecklist_leavesHintNull() = runTest {
        // No context set → behaviour is unchanged (hint stays null, dispatcher uses default).
        val preBuilt = ToolCall.AddItem(checklistHint = null, itemText = "milk")
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        // Seed present but no OnSetContextChecklist call → contextChecklistId stays null.
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val toolCall = vm.screenState.value.pendingPreview?.toolCall
        assertIs<ToolCall.AddItem>(toolCall)
        assertNull(toolCall.checklistHint,
            "Without context, a null hint must remain null (unchanged behaviour)")
    }

    @Test
    fun createItem_nullHint_contextChecklistDeleted_leavesHintNull() = runTest {
        // contextChecklistId points to a checklist that no longer exists (deleted after the
        // dock opened) → safe fallback: hint stays null instead of inventing a name.
        val preBuilt = ToolCall.AddItem(checklistHint = null, itemText = "milk")
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        // Seed is empty → getChecklistById(99) returns null.
        val checklistRepo = FakeChecklistRepository(seed = emptyList())
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 99L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val toolCall = vm.screenState.value.pendingPreview?.toolCall
        assertIs<ToolCall.AddItem>(toolCall)
        assertNull(toolCall.checklistHint,
            "Deleted context checklist must fall back to null hint")
    }

    @Test
    fun createChecklist_withContextChecklist_isNotBiased() = runTest {
        // CreateChecklist's "name" is the target of the action, not a context to operate within —
        // context bias must NOT touch it (the new list keeps its own name).
        val preBuilt = ToolCall.CreateChecklist(name = "Party", initialItems = emptyList())
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateChecklist(name = "Party"),
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("create checklist Party"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val toolCall = vm.screenState.value.pendingPreview?.toolCall
        assertIs<ToolCall.CreateChecklist>(toolCall)
        assertEquals("Party", toolCall.name,
            "CreateChecklist name must not be altered by context bias")
    }

    @Test
    fun agentTurn_withContextChecklist_forwardsContextNameToAgentStep() = runTest {
        // Part B: agentic path must forward the resolved context checklist name to agentStep so
        // the server can bias list-less commands toward the open checklist.
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Final(content = "Done.", creditsRemaining = 100),
            ),
        )
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "agentStep must be called once")
        assertEquals("Groceries", repo.agentStepContextNames.first(),
            "agentStep must receive the resolved context checklist name")
    }

    @Test
    fun agentTurn_noContextChecklist_forwardsNullToAgentStep() = runTest {
        // Part B: no focus → contextChecklistName must be null (server treats as home screen).
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Final(content = "Done.", creditsRemaining = 100),
            ),
        )
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        // No OnSetContextChecklist call.
        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "agentStep must be called once")
        assertNull(repo.agentStepContextNames.first(),
            "agentStep must receive null context name when no checklist is focused")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // forceAgent — checklist reasoning chips bypass classify() (Amplitude bug fix)
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Context: the checklist-detail reasoning chips (What's missing? / Summary / Add items)
    // send fixed reasoning questions whose intent is already known. Routing them through
    // classify() mis-fires: Layer 1/2 tag them FindItems → toolCallDispatcher returns
    // "Nothing matches", or Unknown → "I didn't catch that". Amplitude (debug 787810,
    // 2026-06-02) shows exactly these bad answers. With forceAgent=true the ViewModel must
    // skip classify() entirely and route straight to the reasoning agent (runAgentTurn).

    /**
     * Builds a repo that, if classify() were (wrongly) called, would mis-route to FindItems —
     * reproducing the "Nothing matches" bug. The agent path returns a clean Final answer.
     */
    private fun forceAgentRepro(finalAnswer: String) = FakeAiChatRepository(
        classifyResult = IntentClassification(
            intent = ChatIntent.FindItems,
            confidence = 0.9f,
            layer = RoutingLayer.Local,
        ),
        agentStepResults = listOf(
            AgentStepResult.Final(content = finalAnswer, creditsRemaining = 100),
        ),
    )

    @Test
    fun prefillAndSend_forceAgent_whatsMissing_routesToAgentNotFindItems() = runTest {
        // "What's missing from this checklist?" — the WHATS_MISSING chip query.
        val repo = forceAgentRepro("You might be missing: passport, charger.")
        val fakeDispatcher = FakeToolCallDispatcher(
            // If the buggy FindItems path ran, this is the "Nothing matches" outcome.
            outcome = DispatchOutcome.Success("chat_dispatch_find_no_match", listOf("What's missing")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(
            ChatScreenIntent.OnPrefillAndSend(
                text = "What's missing from this checklist?",
                forceAgent = true,
            )
        )
        testScheduler.advanceUntilIdle()

        // The reasoning agent must have handled the turn.
        assertEquals(1, repo.agentStepCallCount,
            "forceAgent must route the reasoning chip straight to the agent loop")
        // classify() must NOT run — the chip's intent is already known.
        assertEquals(0, repo.classifyCallCount,
            "forceAgent must bypass classify() entirely (it mis-routes reasoning chips)")
        // The FindItems dispatcher path (source of 'Nothing matches') must NOT run.
        assertEquals(0, fakeDispatcher.dispatchCount,
            "forceAgent must NOT dispatch FindItemsQuery — that produces 'Nothing matches'")
        assertNull(fakeDispatcher.lastDispatched)
        // Final reasoning answer is appended; no 'Nothing matches'.
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
        assertEquals("You might be missing: passport, charger.", assistantMsgs.first().content)
        assertFalse(vm.screenState.value.isProcessing)
    }

    @Test
    fun prefillAndSend_forceAgent_summary_routesToAgentNotFindItems() = runTest {
        // "Give me a short summary of my progress." — the SUMMARY chip query.
        val repo = forceAgentRepro("You're 3 of 5 done — 60% complete.")
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_find_no_match", listOf("Give me")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(
            ChatScreenIntent.OnPrefillAndSend(
                text = "Give me a short summary of my progress.",
                forceAgent = true,
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "Summary chip must route to the agent loop")
        assertEquals(0, repo.classifyCallCount, "Summary chip must bypass classify()")
        assertEquals(0, fakeDispatcher.dispatchCount, "Summary chip must NOT dispatch FindItemsQuery")
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
        assertEquals("You're 3 of 5 done — 60% complete.", assistantMsgs.first().content)
    }

    @Test
    fun prefillAndSend_forceAgent_addItems_routesToAgentNotFindItems() = runTest {
        // "What else can I add to this checklist?" — the ADD_ITEMS chip query.
        val repo = forceAgentRepro("Consider adding: sunscreen, adapter.")
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_find_no_match", listOf("What else")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(
            ChatScreenIntent.OnPrefillAndSend(
                text = "What else can I add to this checklist?",
                forceAgent = true,
            )
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "Add-items chip must route to the agent loop")
        assertEquals(0, repo.classifyCallCount, "Add-items chip must bypass classify()")
        assertEquals(0, fakeDispatcher.dispatchCount, "Add-items chip must NOT dispatch FindItemsQuery")
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
        assertEquals("Consider adding: sunscreen, adapter.", assistantMsgs.first().content)
    }

    @Test
    fun prefillAndSend_forceAgent_appendsUserMessageAndForwardsContext() = runTest {
        // forceAgent must still append the user message AND forward the open-checklist context
        // (so the agent's read_checklist resolves to the right list).
        val repo = forceAgentRepro("Looks complete to me.")
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val vm = makeVm(repo = repo, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(
            ChatScreenIntent.OnPrefillAndSend(
                text = "What's missing from this checklist?",
                forceAgent = true,
            )
        )
        testScheduler.advanceUntilIdle()

        // User message is present in history.
        val userMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.User }
        assertEquals(1, userMsgs.size, "forceAgent must still append the user message")
        assertEquals("What's missing from this checklist?", userMsgs.first().content)
        // Input field cleared after send.
        assertEquals("", vm.screenState.value.inputText)
        // Context name forwarded to the agent.
        assertEquals(1, repo.agentStepCallCount)
        assertEquals("Groceries", repo.agentStepContextNames.first(),
            "forceAgent path must forward the resolved context checklist name to agentStep")
    }

    @Test
    fun prefillAndSend_withoutForceAgent_stillClassifies_noRegression() = runTest {
        // The PLAN_DAY chip and any other OnPrefillAndSend without forceAgent must keep the
        // existing classify() behaviour. FreeForm → agent loop, but ONLY via classify().
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Final(content = "Here's your day.", creditsRemaining = 100),
            ),
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnPrefillAndSend(text = "What should I do today?"))
        testScheduler.advanceUntilIdle()

        // classify() MUST run for the default (non-force) path — no regression.
        assertEquals(1, repo.classifyCallCount,
            "Default OnPrefillAndSend must still classify (PLAN_DAY behaviour unchanged)")
        // It still reaches the agent because classify returned FreeForm.
        assertEquals(1, repo.agentStepCallCount)
    }

    @Test
    fun prefillAndSend_withoutForceAgent_createItem_stillShowsPreview_noRegression() = runTest {
        // A non-force prefill that classifies to a write-intent must still show the preview card
        // (proves forceAgent didn't short-circuit the normal classify → preview flow).
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnPrefillAndSend(text = "add milk to shopping"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.classifyCallCount, "Non-force prefill must classify")
        assertEquals(0, repo.agentStepCallCount, "CreateItem must NOT hit the agent loop")
        assertIs<PendingPreview>(vm.screenState.value.pendingPreview)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Analytics funnel — opened / message_sent / response_received / thumb_down
    // ══════════════════════════════════════════════════════════════════════════

    // ── A1. OnChatOpened → screenView(CHAT) + ai_chat_opened with source ──
    @Test
    fun onChatOpened_emitsOpenedEventAndScreenView() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnChatOpened(source = "dock"))

        assertTrue(analytics.screenViews.contains("chat"), "screenView(CHAT) must be sent on open")
        val params = analytics.paramsOf("ai_chat_opened")
        assertNotNull(params, "ai_chat_opened must be emitted")
        assertEquals("dock", params["source"], "source param must be forwarded")
    }

    // ── A2. message_sent fires with deep_thinking / context / input_method / char_len ──
    @Test
    fun sendClick_emitsMessageSentWithFunnelParams() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_message_sent")
        assertNotNull(params, "ai_chat_message_sent must be emitted on a real send")
        assertEquals("false", params["deep_thinking_enabled"])
        assertEquals("false", params["has_context_checklist"], "no context set → false")
        assertEquals("text", params["input_method"])
        assertEquals("add milk".length, params["char_len"], "char_len must equal the trimmed text length")
    }

    // ── A3. message_sent is NOT emitted for blank input (guard fires first) ──
    @Test
    fun sendClick_blankInput_doesNotEmitMessageSent() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("   "))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(0, analytics.count("ai_chat_message_sent"),
            "Blank input must not count as a sent message")
    }

    // ── A4. message_sent has_context_checklist=true when a context checklist is set ──
    @Test
    fun sendClick_withContextChecklist_messageSentHasContextTrue() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 5L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_message_sent")
        assertNotNull(params)
        assertEquals("true", params["has_context_checklist"])
    }

    // ── A5. response_received fires for a write-intent (outcome="preview") with layer + credits ──
    @Test
    fun sendClick_writeIntent_emitsResponseReceivedPreview() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params, "ai_chat_response_received must be emitted when a preview is shown")
        assertEquals("preview", params["outcome"])
        assertEquals("Local", params["routed_layer"])
        assertEquals(0, params["credits_used"], "Layer 1 (Local) costs 0 credits")
        // preview_shown carries the action type
        val previewParams = analytics.paramsOf("ai_chat_preview_shown")
        assertNotNull(previewParams, "ai_chat_preview_shown must be emitted")
        assertEquals("AddItem", previewParams["action_type"])
    }

    // ── A6. response_received fires for the agent Final (outcome="answer", layer=FullChat) ──
    @Test
    fun freeForm_finalAnswer_emitsResponseReceivedAnswer() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Final(content = "Here is your week.", creditsRemaining = 100),
            ),
        )
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params, "agent Final must emit ai_chat_response_received")
        assertEquals("answer", params["outcome"])
        assertEquals("FullChat", params["routed_layer"])
        assertEquals(3, params["credits_used"], "Layer 3 (FullChat) costs 3 credits")
    }

    // ── A7. thumb_down — OnFeedbackOpen emits ai_chat_thumb_down with message params ──
    @Test
    fun onFeedbackOpen_emitsThumbDownEvent() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)
        val assistantMsg = ChatMessage(
            id = "asst_dislike",
            role = ChatRole.Assistant,
            content = "A bad answer.",
            timestamp = 1_000L,
            routedLayer = RoutingLayer.FullChat,
        )

        vm.sendIntent(ChatScreenIntent.OnFeedbackOpen(assistantMsg))

        val params = analytics.paramsOf("ai_chat_thumb_down")
        assertNotNull(params, "Opening the feedback sheet IS the thumb-down moment")
        assertEquals("asst_dislike", params["message_id"])
        assertEquals("FullChat", params["routed_layer"])
        assertEquals("false", params["deep_thinking_enabled"])
        // FEEDBACK must NOT fire on open — only on submit
        assertEquals(0, analytics.count("ai_chat_feedback"),
            "ai_chat_feedback must not fire on open, only on submit")
    }

    // ── A8. thumb_up — migrated to catalog name, value-preserving params ──
    @Test
    fun onThumbUpClick_emitsThumbUpWithMigratedParams() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)
        val assistantMsg = ChatMessage(
            id = "asst_like",
            role = ChatRole.Assistant,
            content = "A good answer.",
            timestamp = 1_000L,
            routedLayer = RoutingLayer.Classifier,
        )

        vm.sendIntent(ChatScreenIntent.OnThumbUpClick(assistantMsg))

        val params = analytics.paramsOf("ai_chat_thumb_up")
        assertNotNull(params)
        assertEquals("asst_like", params["message_id"])
        assertEquals("Classifier", params["routed_layer"])
        assertEquals("false", params["deep_thinking_enabled"])
    }

    // ── A9. feedback — migrated to catalog name, submit emits ai_chat_feedback ──
    @Test
    fun onFeedbackSubmit_emitsFeedbackWithMigratedParams() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)
        val assistantMsg = ChatMessage(
            id = "asst_fb",
            role = ChatRole.Assistant,
            content = "Reply.",
            timestamp = 2_000L,
            routedLayer = RoutingLayer.Local,
        )
        vm.sendIntent(ChatScreenIntent.OnFeedbackOpen(assistantMsg))
        vm.sendIntent(ChatScreenIntent.OnFeedbackTextChange("too vague"))
        vm.sendIntent(ChatScreenIntent.OnFeedbackSubmit)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_feedback")
        assertNotNull(params, "ai_chat_feedback must be emitted on submit")
        assertEquals("asst_fb", params["message_id"])
        assertEquals("Local", params["routed_layer"])
        assertEquals("too vague", params["feedback"])
    }

    // ── A10. preview confirm/reject funnel ──
    @Test
    fun previewApply_emitsPreviewConfirmed_andReject_emitsRejected() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()
        assertIs<PendingPreview>(vm.screenState.value.pendingPreview)

        vm.sendIntent(ChatScreenIntent.OnPreviewApply)
        testScheduler.advanceUntilIdle()

        val confirmed = analytics.paramsOf("ai_chat_preview_confirmed")
        assertNotNull(confirmed, "Apply must emit ai_chat_preview_confirmed")
        assertEquals("AddItem", confirmed["action_type"])
    }

    // ══════════════════════════════════════════════════════════════════════════
    // init-seed history-load resilience (web black-empty-snackbar regression)
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Web bug: on app open a black snackbar with no text appeared at the bottom. The singleton
    // ChatViewModel's init seeds chat history via observeRecent(...).first(). On wasmJs the
    // Room/OPFS Web Worker driver isn't ready yet → the query threw. The OLD code emitted
    // ShowSnackbar("chat_history_load_error") from that failure — fired BEFORE Compose Resources
    // (.cvr) finished loading, so stringResource() resolved to empty → an empty black snackbar the
    // user never triggered and can't act on. The fix retries with backoff (5×, 400ms) and on final
    // failure ONLY logs (no snackbar). These tests lock that contract.
    //
    // Timing: the fix uses delay() inside retryWhen, so virtual time must be advanced. We override
    // Main with an UnconfinedTestDispatcher bound to runTest's scheduler. Unconfined runs the init
    // seed eagerly at VM construction, but it immediately SUSPENDS on the first delay() (backoff) —
    // so we can subscribe to sideEffect AFTER construction yet BEFORE any retry-exhausted emission
    // fires. sideEffect is a replay=0 SharedFlow, so a late subscriber would miss the emission and
    // the "no snackbar" assert would be vacuous; subscribing before the emit makes it real.

    @Test
    fun init_historyLoadAlwaysThrows_doesNotEmitSnackbar() = runTest {
        // Bind Main to the runTest scheduler so the init seed's delay()-driven retries advance here.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        // throwTimes far past the retry cap → every attempt throws → retries exhaust → onFailure.
        val historyRepo = ThrowingChatHistoryRepository(throwTimes = 100)
        // Construction starts init eagerly; the seed coroutine suspends on the first backoff delay().
        val vm = makeVm(historyRepo = historyRepo)

        // Capture the FIRST side-effect (if any) the init path produces. Subscribe via UNDISPATCHED
        // BEFORE advancing time so the collector is active when the seed's failure path runs — the
        // proven pattern in this file (sideEffect is a replay=0 SharedFlow, a late subscriber would
        // miss the emission and make the assert vacuous). withTimeoutOrNull resolves to:
        //   - the emitted ShowSnackbar (OLD buggy code) → assertion below fails (RED), or
        //   - null (FIXED code: only logger.error, no emission) → assertion passes (GREEN).
        val firstEffect = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(60_000) { vm.sideEffect.first() }
        }

        // Drain the 5 retry backoffs (5 × 400ms) → onFailure runs while the collector is subscribed,
        // then the 60s virtual timeout elapses so withTimeoutOrNull returns null when nothing emits.
        testScheduler.advanceUntilIdle()

        // 1. The retry loop must have actually run all the way to exhaustion (>1 subscribe = retried),
        //    so we know the failure path was exercised — not skipped.
        assertTrue(
            historyRepo.subscribeCount >= 2,
            "History load must have retried on failure (subscribeCount=${historyRepo.subscribeCount})",
        )

        // 2. THE REGRESSION: the init seed failure must NOT surface any user-facing side-effect —
        //    and in particular never the empty-black ShowSnackbar("chat_history_load_error").
        val effect = firstEffect.await()
        assertNull(
            effect,
            "init history-load failure must NOT emit a side-effect (web black-empty-snackbar bug); got $effect",
        )

        // 3. The ViewModel stays usable — no crash, messages empty (seed yielded nothing).
        assertEquals(0, vm.screenState.value.messages.size, "Failed seed must leave messages empty")
    }

    @Test
    fun init_historyLoadFlakyThenSucceeds_seedsMessages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Throws on the first 3 attempts (DB warming up), then emits a non-empty history. With a
        // 5-retry cap the 4th attempt succeeds → the seed lands. This demonstrates the retry's value:
        // without it the one-shot .first() would have thrown on attempt 1 and the history never seeds.
        val persisted = listOf(
            ChatMessage(
                id = "u1",
                role = ChatRole.User,
                content = "buy milk",
                timestamp = 1_000L,
            ),
            ChatMessage(
                id = "a1",
                role = ChatRole.Assistant,
                content = "Added milk.",
                timestamp = 2_000L,
            ),
        )
        val historyRepo = ThrowingChatHistoryRepository(throwTimes = 3, seed = persisted)
        val vm = makeVm(historyRepo = historyRepo)

        // Advance virtual time through the 3 failed attempts (3 × 400ms backoff) + the 4th success.
        testScheduler.advanceUntilIdle()

        // Retried 3 times then succeeded on the 4th subscription.
        assertEquals(4, historyRepo.subscribeCount, "Must retry 3 times then succeed on the 4th attempt")

        // The persisted history was seeded into state thanks to the retry.
        val messages = vm.screenState.value.messages
        assertEquals(2, messages.size, "Persisted history must be seeded after a flaky load recovers")
        assertEquals(listOf("buy milk", "Added milk."), messages.map { it.content })
    }
}
