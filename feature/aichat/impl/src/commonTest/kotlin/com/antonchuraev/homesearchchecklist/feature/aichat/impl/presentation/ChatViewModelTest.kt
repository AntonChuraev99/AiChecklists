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
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) : AiChatRepository {
    var classifyCallCount = 0
    var lastSkipLayer1: Boolean = false
    var completeFreeFormCallCount = 0
    var transcribeCallCount = 0
    var lastTranscribePath: String? = null

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

private class FakeChecklistRepository : ChecklistRepository {
    override val checklists: Flow<List<Checklist>> = MutableStateFlow(emptyList())
    override val weeklyChecklistCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun addChecklist(checklist: Checklist): Long = throw UnsupportedOperationException()
    override suspend fun updateChecklist(checklist: Checklist) = Unit
    override suspend fun updateChecklistTemplate(checklist: Checklist) = Unit
    override suspend fun deleteChecklist(checklist: Checklist) = Unit
    override suspend fun getChecklistById(id: Long): Checklist? = null
    override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
    override suspend fun reorderChecklists(orderedIds: List<Long>) = Unit
    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) = Unit
    override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) = Unit
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
}

private class FakeToolCallDispatcher(
    private val outcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added", listOf("item")),
) : ToolCallDispatcher {
    var lastDispatched: ToolCall? = null

    override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome {
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
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {}
    override fun event(name: String, params: Map<String, Any>) {
        events.add(name to params)
    }
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

    // ── 14. OnFeedbackSubmit with blank text → emits hint snackbar, no dismiss ──

    @Test
    fun onFeedbackSubmit_blankText_emitsHintSnackbar() = runTest {
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
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_feedback_blank_hint", effect.messageKey)
        // Sheet must remain open — target is not cleared
        assertNotNull(vm.screenState.value.feedbackTarget)
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
        // Reject will produce FreeForm from Layer 2 → completeFreeForm called
        val layer2RejectResult = IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = 1.0f,
            layer = RoutingLayer.FullChat,
            preBuiltToolCall = null,
        )
        val repo = FakeAiChatRepository(
            classifyResult = layer1Result,
            skipLayer1Result = layer2RejectResult,
            completionResult = RemoteCompletionResult.ServiceError,
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
        // Since Layer 2 returned FreeForm, completeFreeForm was called (via handleFreeForm)
        assertTrue(repo.completeFreeFormCallCount >= 1, "completeFreeForm must be called when Layer2 returns FreeForm on reject")

        effectDeferred.await() // drain the sideEffect (ServiceError → chat_completion_error)
    }

    // ── 26. OnPreviewReject from Classifier source → completeFreeForm called directly ──

    @Test
    fun onPreviewReject_classifierSource_callsCompleteFreeFormDirectly() = runTest {
        // Setup: produce a Classifier-layer preview
        val preBuilt = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk")
        val classifierResult = IntentClassification(
            intent = ChatIntent.CreateItem,
            confidence = 0.9f,
            layer = RoutingLayer.Classifier,
            preBuiltToolCall = preBuilt,
        )
        val repo = FakeAiChatRepository(
            classifyResult = classifierResult,
            completionResult = RemoteCompletionResult.ServiceError,
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
        // completeFreeForm must have been called (Layer 3 escalation)
        assertEquals(1, repo.completeFreeFormCallCount, "completeFreeForm must be called for Classifier→Layer3 escalation")

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
}
