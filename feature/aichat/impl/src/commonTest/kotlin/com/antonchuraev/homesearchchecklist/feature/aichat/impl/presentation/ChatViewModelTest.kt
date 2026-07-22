package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentTranscriptRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistItemContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository.AiChatRepositoryImpl
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
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
import kotlin.test.assertNotEquals
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
    /** Captures the checklistsSummary forwarded to each agentStep call (recent-items context). */
    val agentStepChecklists = mutableListOf<List<ChecklistContext>>()

    /** Captures the requestId forwarded to each agentStep call (Stage 3 idempotency key). */
    val agentStepRequestIds = mutableListOf<String?>()

    /** Captures the responseLanguage forwarded to each agentStep call (explicit reply-language override). */
    val agentStepResponseLanguages = mutableListOf<String?>()

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
        requestId: String?,
        responseLanguage: String?,
    ): AgentStepResult {
        agentStepCallCount++
        agentStepTranscripts.add(transcript.toList())
        agentStepContextNames.add(contextChecklistName)
        agentStepChecklists.add(checklistsSummary.toList())
        agentStepRequestIds.add(requestId)
        agentStepResponseLanguages.add(responseLanguage)
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
    private val undoOutcome: DispatchOutcome = DispatchOutcome.Success("chat_undo_removed", listOf("item")),
    private val moveOutcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added_to", listOf("item", "list")),
) : ToolCallDispatcher {
    var lastDispatched: ToolCall? = null
    var dispatchCount = 0
    var lastUndone: UndoHandle? = null
    var undoCount = 0
    var lastMovedTo: String? = null
    var moveCount = 0

    override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome {
        dispatchCount++
        lastDispatched = toolCall
        return outcome
    }

    override suspend fun undo(handle: UndoHandle): DispatchOutcome {
        undoCount++
        lastUndone = handle
        return undoOutcome
    }

    override suspend fun moveAddedItem(
        handle: UndoHandle.AddedItem,
        targetChecklistName: String,
    ): DispatchOutcome {
        moveCount++
        lastMovedTo = targetChecklistName
        return moveOutcome
    }
}

/**
 * An [UndoHandle] for a just-added row. Only its identity matters here: the C-branch tests assert
 * the chips carry back the very handle the dispatcher returned, so the field values are arbitrary
 * but must round-trip unchanged. Rollback semantics live in ChatUndoChoiceTest.
 */
private fun addedItemHandle(
    itemText: String = "milk",
    checklistName: String = "Shopping",
) = UndoHandle.AddedItem(
    checklistId = 1L,
    checklistName = checklistName,
    fillId = 11L,
    fillItemId = "fill_new",
    templateItemId = "tpl_new",
    itemText = itemText,
)

private object FakePreviewRenderer : ToolCallPreviewRenderer {
    override suspend fun render(toolCall: ToolCall): String = when (toolCall) {
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
    initialDefaultChecklistId: Long? = null,
) : AiChatPreferencesRepository {
    private val _flow = MutableStateFlow(initial)
    var lastSet: Boolean? = null

    override val deepThinkingEnabledFlow: kotlinx.coroutines.flow.Flow<Boolean> = _flow

    override suspend fun setDeepThinkingEnabled(enabled: Boolean) {
        lastSet = enabled
        _flow.value = enabled
    }

    // ── D2 memory of choice (asserted in ChatMemoryOfChoiceTest) ──
    private val _defaultChecklistId = MutableStateFlow(initialDefaultChecklistId)
    override val defaultChecklistIdFlow: kotlinx.coroutines.flow.Flow<Long?> = _defaultChecklistId

    override suspend fun setDefaultChecklistId(checklistId: Long?) {
        _defaultChecklistId.value = checklistId
    }

    // ── Response-language override ──
    private val _responseLanguage = MutableStateFlow<String?>(null)
    var lastResponseLanguageSet: String? = null
    override val responseLanguageFlow: kotlinx.coroutines.flow.Flow<String?> = _responseLanguage

    override suspend fun setResponseLanguage(code: String?) {
        lastResponseLanguageSet = code
        _responseLanguage.value = code
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
    /** Every setUserProperties(...) call, in order — used to assert the sticky A/B arm property. */
    val userPropertyCalls = mutableListOf<Map<String, Any>>()
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {
        userPropertyCalls.add(properties)
    }
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

    /** All setUserProperties calls that set [key], newest last. */
    fun userPropertyCallsFor(key: String): List<Map<String, Any>> =
        userPropertyCalls.filter { it.containsKey(key) }
}

/**
 * Records every [report] ChatViewModel forwards. The dedupe / sticky-property / persistence
 * behaviour now lives in AiModelExperimentTrackerImpl (covered by its own test) — here we only
 * assert the ViewModel hands the server arm to the tracker on carrying rounds.
 */
private class FakeAiModelExperimentTracker :
    com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker {
    data class Report(val variant: String?, val modelId: String?, val aiFlow: String?)
    val reports = mutableListOf<Report>()
    override suspend fun report(variant: String?, modelId: String?, aiFlow: String?) {
        reports.add(Report(variant, modelId, aiFlow))
    }
    override suspend fun current():
        com.antonchuraev.homesearchchecklist.core.common.api.AiModelArm? = null
}

private fun makeVm(
    repo: AiChatRepository = FakeAiChatRepository(),
    dispatcher: FakeToolCallDispatcher = FakeToolCallDispatcher(),
    renderer: ToolCallPreviewRenderer = FakePreviewRenderer,
    historyRepo: ChatHistoryRepository = FakeChatHistoryRepository(),
    agentTranscriptRepo: AgentTranscriptRepository = FakeAgentTranscript(),
    checklistRepo: ChecklistRepository = FakeChecklistRepository(),
    userDataRepo: UserDataRepository = FakeUserDataRepository(),
    aiChatPreferencesRepo: AiChatPreferencesRepository = FakeAiChatPreferencesRepository(),
    analytics: com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker = FakeAnalyticsTracker(),
    experimentTracker: FakeAiModelExperimentTracker = FakeAiModelExperimentTracker(),
    remoteConfig: HarnessRemoteConfigProvider = HarnessRemoteConfigProvider(),
): ChatViewModel = ChatViewModel(
    aiChatRepository = repo,
    toolCallDispatcher = dispatcher,
    previewRenderer = renderer,
    // Injective + timezone-independent — see TokenDateFormatter (ChatUndoChoiceTest).
    dateFormatter = TokenDateFormatter(),
    localeProvider = FakeLocaleProvider,
    chatHistoryRepository = historyRepo,
    agentTranscriptRepository = agentTranscriptRepo,
    checklistRepository = checklistRepo,
    userDataRepository = userDataRepo,
    aiChatPreferencesRepository = aiChatPreferencesRepo,
    analytics = analytics,
    aiModelExperimentTracker = experimentTracker,
    remoteConfigProvider = remoteConfig,
    logger = NoOpLogger,
)

/**
 * A ViewModel whose Layer 2 is out of credits: the REAL [AiChatRepositoryImpl] wired to a
 * classifier fake that returns [RemoteClassificationResult.InsufficientCredits] — exactly what
 * `ChatClassifierApiServiceImpl` produces on HTTP 402 (its line 86).
 *
 * **Why the real repository here, when every other test in this file fakes it.** The bug is the
 * chain, and the load-bearing step is inside the repository: it flattens the refusal into
 * `ChatIntent.Unknown` before the ViewModel ever sees it. Handing the ViewModel a hand-built
 * [IntentClassification] would (a) test the ViewModel against the very representation under
 * debate, and (b) force this test to name the fix's type — pinning it to one of the two candidate
 * designs (a new ChatIntent variant vs. an outcome wrapper around classify()). Entering through
 * the classifier API instead keeps these tests blind to the repository's internal representation:
 * they assert only what the user sees and what Amplitude receives, so either design can satisfy
 * them unchanged.
 *
 * Every other collaborator is a fake and no HTTP client exists here, so this stays free/offline.
 * Deep Thinking is OFF, so classify() starts at Layer 2 — the path a real free-tier send takes.
 */
private fun makeVmOutOfCredits(
    analytics: FakeAnalyticsTracker = FakeAnalyticsTracker(),
    remoteConfig: HarnessRemoteConfigProvider = HarnessRemoteConfigProvider(),
): ChatViewModel {
    // Non-blank userId: a blank one short-circuits Layer 2 before the 402 can happen.
    val userRepo = HarnessUserDataRepository("u1")
    val realRepo = AiChatRepositoryImpl(
        classifierApi = FakeClassifierApi(RemoteClassificationResult.InsufficientCredits),
        completionApi = FakeCompletionApi(),
        transcribeApi = FakeTranscribeApi(),
        chatAgentApi = FakeAgentApi(),
        userDataRepository = userRepo,
        aiChatPreferencesRepository = FakeAiChatPreferences(initial = false),
        logger = NoOpLogger,
    )
    return makeVm(
        repo = realRepo,
        userDataRepo = userRepo,
        analytics = analytics,
        remoteConfig = remoteConfig,
    )
}

/**
 * A ViewModel whose **Layer 3** is out of credits, reached the way a real user reaches it: Deep
 * Thinking ON, which skips Layer 2 entirely and routes straight to the agent loop
 * (`docs/decisions/2026-07-15-remove-ai-chat-layer1.md`).
 *
 * Real [AiChatRepositoryImpl] again — the wiring under test spans repository → ViewModel, and the
 * Layer 2 classifier is left un-stubbed on purpose: if the Deep Thinking bypass ever regressed and
 * the request went through Layer 2, [FakeClassifierApi]'s default ServiceError would change the
 * path and this test would stop measuring Layer 3.
 *
 * The 402 arrives as [AgentStepResult.InsufficientCredits] — what `ChatAgentApiServiceImpl`
 * produces on HTTP 402 (its line 121).
 */
private fun makeVmDeepThinkingOutOfCredits(
    analytics: FakeAnalyticsTracker = FakeAnalyticsTracker(),
): ChatViewModel {
    val userRepo = HarnessUserDataRepository("u1")
    val realRepo = AiChatRepositoryImpl(
        classifierApi = FakeClassifierApi(),
        completionApi = FakeCompletionApi(),
        transcribeApi = FakeTranscribeApi(),
        chatAgentApi = FakeAgentApi(AgentStepResult.InsufficientCredits),
        userDataRepository = userRepo,
        // Deep Thinking ON → classify() never calls Layer 2, it returns FreeForm for Layer 3.
        aiChatPreferencesRepository = FakeAiChatPreferences(initial = true),
        logger = NoOpLogger,
    )
    return makeVm(repo = realRepo, userDataRepo = userRepo, analytics = analytics)
}

// ─── Choice-block test helpers ──────────────────────────────────────────────
//
// The write-intent preview card and agent plan card were replaced by a single
// AiChoiceResponse block (PendingChoice). These helpers read the new structure so the
// migrated tests stay readable: the Execute tool call is carried by the primary chip's
// ChoiceAction.Execute; the escape chip id is "escape"; ExecuteAll chip id is "execute_all".
//
// ─── Which intent to drive the QUESTION mechanics with (D1, 2026-07-15) ──────
//
// D1 ("ceremony proportional to reversibility") split the write intents in two:
//   • AddItem / CompleteItem  → reversible → applied at once, Undo offered after (NO question).
//   • DeleteItem / CreateChecklist / SetItemReminder / AttachToItem → still ask first.
//
// Every test below that is really about the QUESTION machinery — the Edit chip, Execute/Apply,
// the FreeForm escape ladder, Dismiss, the preview_shown/confirmed/rejected funnel, the
// RequiresPremium snackbar, linkedChecklistId plumbing — therefore drives DeleteItem: it is the
// intent that still asks AND carries both an Execute and an Edit chip. Those tests used AddItem
// only because, before D1, every write intent asked. Their assertions are unchanged; only the
// carrier intent moved, so the mechanics stay guarded.
//
// The add-specific behaviour (dispatch-without-question, the post-hoc chips, the P5 hint bias)
// keeps using AddItem and asserts through the dispatcher — see the C-branch tests.

/**
 * A classification for a write intent that STILL asks (delete), with the ToolCall pre-built so the
 * test does not depend on classifier phrasing. [layer] is what the escalation ladder reads back.
 *
 * Defaults to [RoutingLayer.Classifier]: since the Layer 1 disconnect (2026-07-15) every
 * classify() result comes from Layer 2, so Classifier is what a real send now produces. Tests
 * that specifically exercise the Local branch of the escalation ladder pass `layer` explicitly —
 * that branch is still reachable in prod via the attachment-only send, which tags Local.
 */
private fun deleteClassification(
    itemText: String = "milk",
    checklistHint: String? = "shopping",
    layer: RoutingLayer = RoutingLayer.Classifier,
) = IntentClassification(
    intent = ChatIntent.DeleteItem,
    confidence = 1.0f,
    layer = layer,
    preBuiltToolCall = ToolCall.DeleteItem(checklistHint = checklistHint, itemText = itemText),
)

/** The ToolCall behind the (single) Execute option of the pending choice, or null. */
private fun ChatScreenState.executeToolCall(): ToolCall? =
    pendingChoice?.choice?.options
        ?.firstNotNullOfOrNull { (it.action as? com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction.Execute)?.toolCall }

/** The candidate-list (Execute) tool calls of an AmbiguousMatch "Which list?" choice. */
private fun ChatScreenState.candidateToolCalls(): List<ToolCall> =
    pendingChoice?.choice?.options
        ?.mapNotNull { (it.action as? com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction.Execute)?.toolCall }
        ?: emptyList()

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
        assertNull(state.pendingChoice)
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
        assertNull(state.pendingChoice)
        assertEquals(false, state.isProcessing)

        // The ShowAssistantMessage side effect carries the localisation key
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_unknown_intent_hint", effect.messageKey)
    }

    // ── 4. CreateItem intent → applied at once, no question (D1 C-branch) ─────

    /**
     * A confident add is one-tap reversible, so D1 applies it immediately and offers Undo after
     * instead of asking first.
     *
     * Overlaps ChatUndoChoiceTest's C-branch test on purpose but is NOT redundant: that one scripts
     * a preBuiltToolCall, whereas this classification carries none, so the ViewModel's OWN
     * text → ToolCall extraction runs. It therefore also pins that "add milk to shopping" reaches
     * the dispatcher as item "milk" for list "shopping" — the object the user actually named.
     */
    @Test
    fun sendClick_createItemIntent_dispatchesWithoutQuestion() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val handle = addedItemHandle(itemText = "milk", checklistName = "shopping")
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("milk", "shopping"),
                undo = handle,
            ),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        // It happened — with no confirmation round-trip.
        assertEquals(1, fakeDispatcher.dispatchCount, "A confident add must dispatch immediately")
        val dispatched = fakeDispatcher.lastDispatched
        assertIs<ToolCall.AddItem>(dispatched)
        assertEquals("milk", dispatched.itemText, "The named item must reach the dispatcher")
        assertEquals("shopping", dispatched.checklistHint, "The named list must reach the dispatcher")

        // What is left on screen is a chip strip, not a question.
        val state = vm.screenState.value
        assertNotNull(state.pendingChoice, "The post-hoc Undo strip must be shown")
        assertEquals("", state.pendingChoice?.choice?.prompt,
            "The C-branch asks nothing — the prompt must be empty")
        assertEquals(
            listOf<ChoiceAction>(ChoiceAction.Undo(handle), ChoiceAction.MoveToList(handle)),
            state.actions(),
            "An added item offers Undo + move-to-list, bound to the handle the dispatcher returned",
        )
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

        assertNull(vm.screenState.value.pendingChoice)
        assertIs<ToolCall.FindItemsQuery>(fakeDispatcher.lastDispatched)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_dispatch_find_success", effect.messageKey)
        assertEquals("1", effect.args[0])
    }

    // ── 6. PreviewApply → dispatches ToolCall, clears pendingPreview, emits ShowAssistantMessage ──

    @Test
    fun previewApply_dispatchesAndClearsPendingPreview() = runTest {
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_deleted_from", listOf("milk", "Shopping"))
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        // Build a choice block
        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        // Nothing may run before the user confirms — that is what the question is for.
        assertEquals(0, fakeDispatcher.dispatchCount, "An irreversible action must not run before Apply")

        // Collect the SideEffect emitted on execute
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        // Tap the primary "execute" chip
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute"))

        assertNull(vm.screenState.value.pendingChoice)
        assertIs<ToolCall.DeleteItem>(fakeDispatcher.lastDispatched)

        // Success outcome emits ShowAssistantMessage with the dispatch key
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_dispatch_deleted_from", effect.messageKey)
        assertEquals(listOf("milk", "Shopping"), effect.args)
    }

    // ── 7. PreviewCancel → clears pendingPreview + emits ShowAssistantMessage ─

    @Test
    fun previewCancel_clearsPendingPreviewAndEmitsCancelledMessage() = runTest {
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)
        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnChoiceDismissed)

        // Choice must be cleared
        assertNull(vm.screenState.value.pendingChoice)
        // Cancelling a question must cancel the action, not just the bubble.
        assertEquals(0, fakeDispatcher.dispatchCount, "Dismiss must not dispatch")
        // Assistant cancelled message must be emitted (silent dismiss FORBIDDEN per CLAUDE.md)
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_choice_dismissed_message", effect.messageKey)
    }

    // ── 8. A Local-layer classification → user message costCredits == 0 ──────

    /**
     * Locks the `Local → 0 credits` mapping in the ViewModel.
     *
     * Since the Layer 1 disconnect (2026-07-15) `classify()` never returns Local, so this exact
     * route cannot happen in prod — but the mapping itself is still live: the attachment-only
     * send tags its user message Local (cost 0, the charge lands on execution), and messages
     * persisted before the disconnect replay from Room carrying Local. Deleting the Local branch
     * of `creditsForLayer` as "dead" would mis-price both, and would also have to be undone when
     * Layer 1 is re-routed. Kept deliberately.
     */
    @Test
    fun sendClick_localLayerClassification_userMessageCostCreditsIsZero() = runTest {
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
        assertEquals(0, userMsg.costCredits, "A Local-layer message is free — costCredits must be 0")
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
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val fakeDispatcher = FakeToolCallDispatcher(outcome = DispatchOutcome.RequiresPremium)
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { vm.sideEffect.first() }
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute"))

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
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_deleted_from",
                args = listOf("milk", "Shopping"),
                linkedChecklistId = 42L,
            )
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute"))

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
        // Setup: get a question from Layer 1 (delete — the reversible intents no longer ask).
        val layer1Result = deleteClassification(layer = RoutingLayer.Local)
        // Reject will produce FreeForm from Layer 2 → agent loop runs
        val layer2RejectResult = IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = 1.0f,
            layer = RoutingLayer.FullChat,
            preBuiltToolCall = null,
        )
        // No scripted agentStepResults → agentStep returns ServiceError → chat_error_service (F1).
        val repo = FakeAiChatRepository(
            classifyResult = layer1Result,
            skipLayer1Result = layer2RejectResult,
        )
        val vm = makeVm(repo = repo)

        // Send to get a choice block from Layer 1
        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        // Reset call count after initial classify (for initial OnSendClick)
        val initialCallCount = repo.classifyCallCount

        // Collect the next sideEffect before escalating
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        // Tap the escape "Something else" chip → FreeForm escalation
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("escape"))

        // Choice must be cleared immediately
        assertNull(vm.screenState.value.pendingChoice)
        // classify must have been called with skipLayer1=true
        assertEquals(initialCallCount + 1, repo.classifyCallCount, "classify must be called once more on escalate")
        assertEquals(true, repo.lastSkipLayer1, "Escalate from Local source must set skipLayer1=true")
        // Since Layer 2 returned FreeForm, the agent loop runs (no legacy completeFreeForm).
        assertTrue(repo.agentStepCallCount >= 1, "agentStep must be called when Layer2 returns FreeForm on reject")
        assertEquals(0, repo.completeFreeFormCallCount, "completeFreeForm must NOT be called anymore")

        effectDeferred.await() // drain the sideEffect (ServiceError → chat_completion_error)
    }

    // ── 26. OnPreviewReject from Classifier source → agent loop runs directly ──

    @Test
    fun onPreviewReject_classifierSource_runsAgentLoopDirectly() = runTest {
        // Setup: produce a Classifier-layer question (delete — the reversible intents no longer ask).
        val classifierResult = deleteClassification(layer = RoutingLayer.Classifier)
        // No scripted agentStepResults → agentStep returns ServiceError → chat_error_service (F1).
        val repo = FakeAiChatRepository(
            classifyResult = classifierResult,
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        val initialClassifyCount = repo.classifyCallCount

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("escape"))

        assertNull(vm.screenState.value.pendingChoice)
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
        // (ServiceError → chat_error_service, F1). The Final path calls addAndPersistAssistantMessage
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

        // Drain the connectivity-error ShowAssistantMessage from the ServiceError path (F1).
        val fallbackEffect = fallbackEffectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(fallbackEffect)
        assertEquals("chat_error_service", fallbackEffect.messageKey,
            "ServiceError from the agent loop must emit chat_error_service (not the AI-blaming chat_completion_error)")
        assertEquals("это штука что ты", fallbackEffect.retryText,
            "the error reply must carry the original text so the Retry chip can re-send it")
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
        assertNull(vm.screenState.value.pendingChoice,
            "pendingChoice must be null after a Final result")
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
        assertNull(vm.screenState.value.pendingChoice,
            "No choice block for read-only tools")
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

        // After first agentStep returns ToolCalls, the choice block should be visible.
        testScheduler.advanceUntilIdle()
        val choiceAfterStep1 = vm.screenState.value.pendingChoice
        assertNotNull(choiceAfterStep1, "pendingChoice must be set after ToolCalls result")
        assertEquals(1, choiceAfterStep1.batchItems?.size, "One batch item expected")
        assertFalse(vm.screenState.value.isProcessing,
            "isProcessing must be false while choice block is shown")
        assertEquals(0, fakeDispatcher.dispatchCount,
            "Dispatcher must NOT be called before user approves")

        // User taps "Do it all" → loop resumes
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute_all"))
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

        // Choice block is cleared and Final message is persisted.
        assertNull(vm.screenState.value.pendingChoice, "Choice block must be cleared after Final")
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size)
        assertEquals("Done, added milk.", assistantMsgs.first().content)
    }

    // ── 37b. clear_completed_items — ambiguous hint fires the which-list picker ──

    @Test
    fun agentLoop_clearCompleted_ambiguousHint_firesWhichListPicker() = runTest {
        val clearCall = AgentToolCall(
            id = "call-clear",
            name = "clear_completed_items",
            args = buildJsonObject { put("checklist_hint", "Покупки") },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(calls = listOf(clearCall), creditsRemaining = 297),
                // Safety-net Final: the ambiguous clear ends the turn AT the picker before a second
                // agentStep, so this must never be reached (asserted via agentStepCallCount below).
                AgentStepResult.Final(content = "unreachable", creditsRemaining = 297),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.AmbiguousMatch(listOf("Покупки дом", "Покупки офис")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("удали выполненные из покупок"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        // Approve the batch → dispatch returns AmbiguousMatch → the which-list picker takes over.
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute_all"))
        testScheduler.advanceUntilIdle()

        val picker = assertNotNull(
            vm.screenState.value.pendingChoice,
            "an ambiguous bulk clear must ask which list — never a prose question, never a guess",
        )
        val executeClears = picker.choice.options
            .mapNotNull { (it.action as? ChoiceAction.Execute)?.toolCall }
            .filterIsInstance<ToolCall.ClearCompleted>()
        assertEquals(
            listOf("Покупки дом", "Покупки офис"),
            executeClears.map { it.checklistHint },
            "each chip re-runs clear_completed against one specific candidate list",
        )
        assertEquals(
            1,
            repo.agentStepCallCount,
            "the picker ends the turn; the loop must not continue nor serialize ambiguous back to Gemini",
        )
    }

    // ── 37c. clear_completed_items is a bulk delete → flagged destructive ──

    @Test
    fun agentLoop_clearCompleted_planItemFlaggedDestructive() = runTest {
        val clearCall = AgentToolCall(
            id = "call-clear",
            name = "clear_completed_items",
            args = buildJsonObject { put("checklist_hint", "Shopping") },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(calls = listOf(clearCall), creditsRemaining = 297),
                AgentStepResult.Final(content = "Cleared.", creditsRemaining = 297),
            ),
        )
        val vm = makeVm(repo = repo, dispatcher = FakeToolCallDispatcher())

        vm.sendIntent(ChatScreenIntent.OnInputChange("clear completed"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val pending = assertNotNull(vm.screenState.value.pendingChoice)
        assertTrue(
            pending.batchItems?.first()?.isDestructive == true,
            "clear_completed_items removes user data — it must be flagged destructive like delete_item",
        )
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

        // Choice block visible with destructive batch item
        val pending = vm.screenState.value.pendingChoice
        assertNotNull(pending)
        assertTrue(pending.batchItems?.first()?.isDestructive == true, "delete_item must be flagged as destructive")

        // User cancels (escape chip)
        vm.sendIntent(ChatScreenIntent.OnChoiceDismissed)
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

        // Final message received after cancelled batch
        assertNull(vm.screenState.value.pendingChoice)
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
        assertNull(vm.screenState.value.pendingChoice,
            "pendingChoice must be null after round-cap")
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

        // read_checklist should auto-execute, add_item should show the choice block
        val pending = vm.screenState.value.pendingChoice
        assertNotNull(pending, "Choice block must be shown for the mutating add_item call")
        assertEquals(1, pending.batchItems?.size, "Only mutating calls appear in the batch list")

        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute_all"))
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

    // ══════════════════════════════════════════════════════════════════════════
    // AiChoiceResponse mapping — write-intent / ambiguous / edit / dismiss
    // ══════════════════════════════════════════════════════════════════════════

    // ── C1. Write-intent choice carries a primary Execute chip with the tool call ──
    /**
     * Driven by SetItemReminder: it is a NON-destructive intent that still asks, so it keeps this
     * test on the Primary role. (Delete would collapse it into C2, which owns the Destructive role.)
     */
    @Test
    fun writeIntent_choiceHasPrimaryExecuteChip() = runTest {
        val preBuilt = ToolCall.SetItemReminder(checklistHint = "shopping", itemText = "milk", at = 1_800_000_000_000L)
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.SetReminder,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = preBuilt,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("remind me about milk in shopping tomorrow at 9"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val choice = vm.screenState.value.pendingChoice?.choice
        assertNotNull(choice, "A write intent that still asks must produce a choice block")
        // There is exactly one Execute option and it is the Primary chip.
        val executeOption = choice.options.first { it.id == "execute" }
        assertEquals(ChoiceRole.Primary, executeOption.role,
            "Setting a reminder is a Primary (non-destructive) action")
        assertIs<ChoiceAction.Execute>(executeOption.action)
        assertEquals(preBuilt, (executeOption.action as ChoiceAction.Execute).toolCall,
            "The chip must carry the very tool call the question was asked about")
        // Escape chip re-classifies (FreeForm), carrying the original text.
        assertEquals("escape", choice.escape?.id)
        assertIs<ChoiceAction.FreeForm>(choice.escape?.action)
    }

    // ── C2. Delete intent → Execute chip uses the Destructive role ──
    @Test
    fun deleteIntent_executeChipIsDestructive() = runTest {
        val preBuilt = ToolCall.DeleteItem(checklistHint = "shopping", itemText = "milk")
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.DeleteItem,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = preBuilt,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val executeOption = vm.screenState.value.pendingChoice?.choice?.options?.first { it.id == "execute" }
        assertNotNull(executeOption)
        assertEquals(ChoiceRole.Destructive, executeOption.role, "Delete must be a Destructive chip")
    }

    // ── C3. AmbiguousMatch → "Which list?" choice with one Execute chip per candidate ──
    @Test
    fun executeChoice_ambiguousMatch_buildsCandidateChips() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        // First dispatch (on execute) returns AmbiguousMatch → ViewModel rebuilds a candidate choice.
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.AmbiguousMatch(candidates = listOf("Shopping", "Snacks")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        // Tap execute → dispatcher returns AmbiguousMatch → a "Which list?" choice replaces it.
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute"))

        val candidateCalls = vm.screenState.value.candidateToolCalls()
        assertEquals(2, candidateCalls.size, "One Execute chip per candidate list")
        val hints = candidateCalls.filterIsInstance<ToolCall.AddItem>().map { it.checklistHint }
        assertEquals(listOf("Shopping", "Snacks"), hints,
            "Each candidate chip re-runs the command against that specific list")
    }

    // ── C4. OnChoiceDismissed (single choice) → choice cleared + visible reply ──
    @Test
    fun choiceDismissed_singleChoice_clearsAndEmitsMessage() = runTest {
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }
        vm.sendIntent(ChatScreenIntent.OnChoiceDismissed)

        assertNull(vm.screenState.value.pendingChoice, "Dismiss must clear the choice")
        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effect)
        assertEquals("chat_choice_dismissed_message", effect.messageKey,
            "Silent dismiss is FORBIDDEN — a visible reply must be emitted")
    }

    // ── C5. Edit then confirm → applyEditedText replaces item text before dispatch ──
    @Test
    fun choiceEdit_confirm_dispatchesEditedText() = runTest {
        val repo = FakeAiChatRepository(classifyResult = deleteClassification(layer = RoutingLayer.Classifier))
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_deleted", listOf("oat milk")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        // Open the inline edit field.
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("edit"))
        assertNotNull(vm.screenState.value.pendingChoice?.editText, "Edit must open the inline field")

        // Type a new value and confirm.
        vm.sendIntent(ChatScreenIntent.OnChoiceEditChange("oat milk"))
        vm.sendIntent(ChatScreenIntent.OnChoiceEditConfirmed)

        val dispatched = fakeDispatcher.lastDispatched
        assertIs<ToolCall.DeleteItem>(dispatched)
        assertEquals("oat milk", dispatched.itemText, "Edited text must be applied before dispatch")
        assertEquals("shopping", dispatched.checklistHint, "Editing the item must not drop the target list")
        assertNull(vm.screenState.value.pendingChoice, "Choice cleared after the edited dispatch")
    }

    // ── C6. Edit confirm with blank text → hint snackbar, no dispatch ──
    @Test
    fun choiceEdit_blankConfirm_emitsHintNoDispatch() = runTest {
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("edit"))

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }
        vm.sendIntent(ChatScreenIntent.OnChoiceEditChange("   "))
        vm.sendIntent(ChatScreenIntent.OnChoiceEditConfirmed)

        val effect = effectDeferred.await()
        assertIs<ChatScreenSideEffect.ShowSnackbar>(effect)
        assertEquals("chat_choice_edit_empty_hint", effect.messageKey,
            "Blank edit must hint, not silently drop (CLAUDE.md silent-skip guard)")
        assertEquals(0, fakeDispatcher.dispatchCount, "Blank edit must NOT dispatch")
    }

    // ── C7. Agent batch ExecuteAll resolves the deferred and dispatches ──
    @Test
    fun agentBatch_executeAllChip_resolvesDeferredAndDispatches() = runTest {
        val addItemCall = AgentToolCall(
            id = "call-x",
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
                AgentStepResult.ToolCalls(calls = listOf(addItemCall), creditsRemaining = 297),
                AgentStepResult.Final(content = "Done.", creditsRemaining = 297),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_added", listOf("milk")),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        // Batch choice carries an ExecuteAll primary chip.
        val choice = vm.screenState.value.pendingChoice?.choice
        assertNotNull(choice)
        val executeAll = choice.options.first { it.id == "execute_all" }
        assertIs<ChoiceAction.ExecuteAll>(executeAll.action)

        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute_all"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeDispatcher.dispatchCount, "ExecuteAll must dispatch the batched call")
        assertNull(vm.screenState.value.pendingChoice, "Choice cleared after Final")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 2 — AI-generated answer options (AgentStepResult.Options)
    // ══════════════════════════════════════════════════════════════════════════

    // ── C8. Options result → question lives INSIDE the choice block, NOT persisted yet ──
    @Test
    fun agentOptions_promptInChoiceBlock_notPersistedUntilResolve() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Options(
                    prompt = "What kind of trip is it?",
                    options = listOf("Beach", "City", "Hiking"),
                    creditsRemaining = 97,
                ),
            ),
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("plan a trip checklist"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        // The question is NOT persisted as a message yet — it lives inside the choice block so the
        // inline dock (which overlays only pendingChoice) shows it. Persist happens on resolve.
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(0, assistantMsgs.size, "Options question must NOT be persisted until the user resolves it")

        // The choice block carries the question as its prompt + one SendMessage chip per option.
        val choice = vm.screenState.value.pendingChoice?.choice
        assertNotNull(choice, "Options must produce a choice block")
        assertEquals("What kind of trip is it?", choice.prompt, "The question must live inside the choice block")
        assertEquals(3, choice.options.size, "One chip per option label")
        assertEquals(
            listOf("Beach", "City", "Hiking"),
            choice.options.map { it.label },
        )
        choice.options.forEach {
            assertIs<ChoiceAction.SendMessage>(it.action, "Each option chip sends a fresh agent turn")
        }
        assertFalse(vm.screenState.value.isProcessing, "Options is terminal — processing must stop")
    }

    // ── C9. Tapping an option chip → fresh agent turn with the chip label ──
    @Test
    fun agentOptions_tapChip_sendsLabelAsNewAgentTurn() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                // Round 1: produces options. Round 2 (after tapping): a Final answer.
                AgentStepResult.Options(
                    prompt = "What kind of trip?",
                    options = listOf("Beach", "City"),
                    creditsRemaining = 97,
                ),
                AgentStepResult.Final(content = "Here is your Beach trip checklist.", creditsRemaining = 94),
            ),
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("plan a trip"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repo.agentStepCallCount)

        // Question is not yet a message (it's inside the choice block).
        assertEquals(0, vm.screenState.value.messages.count { it.role == ChatRole.Assistant && it.content == "What kind of trip?" })

        // Tap the first option chip → sends "Beach" as a new agent turn.
        val firstChipId = vm.screenState.value.pendingChoice!!.choice.options.first().id
        vm.sendIntent(ChatScreenIntent.OnChoiceSelected(firstChipId))
        testScheduler.advanceUntilIdle()

        // A second agent turn ran with the label as a user message.
        assertEquals(2, repo.agentStepCallCount, "Tapping an option must start a fresh agent turn")
        // classify is bypassed (forceAgent semantics) — the label goes straight to the agent.
        assertEquals(1, repo.classifyCallCount, "Only the initial send classifies; option-tap bypasses classify")
        assertNull(vm.screenState.value.pendingChoice, "Choice cleared after the new turn's Final")

        // History order: [user: "plan a trip"][assistant: question][user: "Beach"][assistant: answer].
        val contents = vm.screenState.value.messages.map { it.role to it.content }
        assertEquals(
            listOf(
                ChatRole.User to "plan a trip",
                ChatRole.Assistant to "What kind of trip?",
                ChatRole.User to "Beach",
                ChatRole.Assistant to "Here is your Beach trip checklist.",
            ),
            contents,
            "Resolving an option must persist [question][label][answer] in order",
        )
    }

    // ── C10. Dismissing an Options choice → question persisted (stays visible), no 'cancelled' reply ──
    @Test
    fun agentOptions_dismiss_persistsQuestionNoCancelledReply() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.Options(
                    prompt = "Which one?",
                    options = listOf("A", "B"),
                    creditsRemaining = 97,
                ),
            ),
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("ask me"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.screenState.value.pendingChoice)
        // Not yet persisted while the choice block is shown.
        assertEquals(0, vm.screenState.value.messages.count { it.role == ChatRole.Assistant })

        vm.sendIntent(ChatScreenIntent.OnChoiceDismissed)
        testScheduler.advanceUntilIdle()

        assertNull(vm.screenState.value.pendingChoice, "Dismiss must clear the options block")
        // The question is persisted on dismiss so it stays visible in history — and it is the
        // ONLY assistant message (no extra 'cancelled' reply is appended for the options case).
        val assistantMsgs = vm.screenState.value.messages.filter { it.role == ChatRole.Assistant }
        assertEquals(1, assistantMsgs.size,
            "Dismissing options persists the question and adds NO 'cancelled' reply")
        assertEquals("Which one?", assistantMsgs.first().content,
            "The dismissed question must be persisted so it stays visible in history")
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
    //
    // The bias itself is UNCHANGED by D1 — it still runs before the reversible branch, on the same
    // rule (explicit hint wins; null hint takes the open checklist's name; unknown context → null).
    // Only the observation point moved: an add no longer parks in a question, so the biased hint is
    // read off the ToolCall that reached the dispatcher instead of off the pending choice. Asserting
    // the hint (not the question copy) is also the more durable contract: the hint is what actually
    // routes the item to a list.

    private fun groceriesChecklist(id: Long = 42L) = Checklist(
        id = id,
        name = "Groceries",
        items = emptyList(),
    )

    /** The hint of the single AddItem that reached the dispatcher; fails loudly if none did. */
    private fun FakeToolCallDispatcher.dispatchedAddHint(): String? {
        assertEquals(1, dispatchCount, "Exactly one add must have been dispatched")
        val dispatched = lastDispatched
        assertIs<ToolCall.AddItem>(dispatched)
        return dispatched.checklistHint
    }

    @Test
    fun createItem_nullHint_withContextChecklist_biasesHintToContextName() = runTest {
        // AddItem extracted with no explicit list ("add milk") while the dock is focused on
        // checklist id=42 ("Groceries") → the dispatched toolCall's hint must become "Groceries".
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
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher, checklistRepo = checklistRepo)

        // Focus the dock on Groceries, then send a list-less command.
        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        assertEquals("Groceries", fakeDispatcher.dispatchedAddHint(),
            "Null-hint AddItem must be biased to the open checklist name — that hint is what lands it in a list")
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
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 42L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        assertEquals("shopping", fakeDispatcher.dispatchedAddHint(),
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
        // One list only, so the hintless add is NOT ambiguous: it falls through to the dispatcher
        // (which resolves a null hint itself) rather than stopping to ask "which list?".
        val checklistRepo = FakeChecklistRepository(seed = listOf(groceriesChecklist(id = 42L)))
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        assertNull(fakeDispatcher.dispatchedAddHint(),
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
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher, checklistRepo = checklistRepo)

        vm.sendIntent(ChatScreenIntent.OnSetContextChecklist(checklistId = 99L))
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        assertNull(fakeDispatcher.dispatchedAddHint(),
            "Deleted context checklist must fall back to null hint, never invent a name")
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

        val toolCall = vm.screenState.value.executeToolCall()
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
    fun prefillAndSend_withoutForceAgent_createItem_stillDispatchesWriteIntent_noRegression() = runTest {
        // A non-force prefill that classifies to a write-intent must still run the normal
        // classify → write-intent flow (proves forceAgent didn't short-circuit it). Since D1 a
        // confident add lands straight on the dispatcher instead of a preview card, so that is
        // where the flow is observed.
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Local,
            )
        )
        val fakeDispatcher = FakeToolCallDispatcher()
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        vm.sendIntent(ChatScreenIntent.OnPrefillAndSend(text = "add milk to shopping"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.classifyCallCount, "Non-force prefill must classify")
        assertEquals(0, repo.agentStepCallCount, "CreateItem must NOT hit the agent loop")
        val dispatched = fakeDispatcher.lastDispatched
        assertIs<ToolCall.AddItem>(dispatched)
        assertEquals("milk", dispatched.itemText, "The prefilled text must be parsed, not passed through raw")
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
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params, "ai_chat_response_received must be emitted when a preview is shown")
        assertEquals("preview", params["outcome"])
        assertEquals("Classifier", params["routed_layer"])
        assertEquals(1, params["credits_used"], "Layer 2 (Classifier) costs 1 credit")
        // preview_shown carries the action type
        val previewParams = analytics.paramsOf("ai_chat_preview_shown")
        assertNotNull(previewParams, "ai_chat_preview_shown must be emitted")
        assertEquals("DeleteItem", previewParams["action_type"])
    }

    // ── A5b. The C-branch reports its own turn: outcome="action", no preview_shown ──
    /**
     * D1 moved confident add/complete off the preview funnel: there is no preview to show, the
     * action already ran. Locks the replacement so the turn is never left unreported (which would
     * silently punch a hole in the response_received funnel) and so preview_shown volume stays a
     * true count of questions asked rather than quietly counting auto-applied actions too.
     */
    @Test
    fun sendClick_reversibleIntent_emitsResponseReceivedActionAndAutoApplied() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = ToolCall.AddItem(checklistHint = "shopping", itemText = "milk"),
            )
        )
        val analytics = FakeAnalyticsTracker()
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("milk", "shopping"),
                undo = addedItemHandle(),
            ),
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params, "The auto-applied turn must still report a response")
        assertEquals("action", params["outcome"], "An applied action is not a 'preview' outcome")
        assertEquals("Classifier", params["routed_layer"])

        val applied = analytics.paramsOf("ai_chat_action_auto_applied")
        assertNotNull(applied, "Auto-applying must be measurable (it replaces the preview funnel)")
        assertEquals("AddItem", applied["action_type"])
        assertEquals("Classifier", applied["routed_layer"])

        assertNull(analytics.paramsOf("ai_chat_preview_shown"),
            "No question was asked, so preview_shown must NOT fire")
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

    // ── A6b. model_variant → forwarded to the experiment tracker + response_received dimension ──
    @Test
    fun freeForm_modelVariant_forwardedToExperimentTracker() = runTest {
        val findCall = AgentToolCall(
            id = "call-find",
            name = "find_items", // read-only → auto-dispatched, loop continues to the Final round
            args = buildJsonObject { put("query", "milk") },
        )
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            // Two rounds, BOTH carrying the same arm — the guard must collapse them to one set.
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(
                    calls = listOf(findCall),
                    creditsRemaining = 99,
                    modelVariant = "variant_b",
                    modelId = "gemini-3.1-flash-lite",
                    aiFlow = "chat_agent",
                ),
                AgentStepResult.Final(
                    content = "Here is your week.",
                    creditsRemaining = 98,
                    modelVariant = "variant_b",
                    modelId = "gemini-3.1-flash-lite",
                    aiFlow = "chat_agent",
                ),
            ),
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_find_success", listOf("0", "")),
        )
        val analytics = FakeAnalyticsTracker()
        val experimentTracker = FakeAiModelExperimentTracker()
        val vm = makeVm(
            repo = repo,
            dispatcher = fakeDispatcher,
            analytics = analytics,
            experimentTracker = experimentTracker,
        )

        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        // ChatViewModel forwards the server arm to the shared tracker on the carrying rounds; the
        // sticky-property / dedupe lives in AiModelExperimentTrackerImpl (its own test).
        assertTrue(
            experimentTracker.reports.any {
                it == FakeAiModelExperimentTracker.Report("variant_b", "gemini-3.1-flash-lite", "chat_agent")
            },
            "the server arm must be forwarded to the experiment tracker",
        )

        // Also attached as guardrail dimensions on the response_received event.
        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params)
        assertEquals("variant_b", params["ai_model_variant"])
        assertEquals("gemini-3.1-flash-lite", params["ai_model_id"])
        assertEquals("chat_agent", params["ai_flow"])
    }

    // ── A6c. No model_variant from server → no real arm forwarded, no event dimension (backward compat) ──
    @Test
    fun freeForm_noModelVariant_forwardsNoArmAndOmitsEventDimension() = runTest {
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
        val experimentTracker = FakeAiModelExperimentTracker()
        val vm = makeVm(repo = repo, analytics = analytics, experimentTracker = experimentTracker)

        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertTrue(
            experimentTracker.reports.none { it.variant != null },
            "Missing model_variant (experiment off / older server) must not forward a real arm",
        )
        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params)
        assertFalse(params.containsKey("ai_model_variant"),
            "ai_model_variant must be omitted from the event when the server didn't send it")
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

    // ── A8b. routed_layer end-to-end — a Classifier-path reply carries its tier, not "unknown" ──

    /**
     * Locks the whole GOAL-1 chain: a Layer-2 (Classifier) inline reply must reach the user's
     * thumb-down carrying routed_layer=Classifier, not the "unknown" that masked 86% of feedback.
     *
     * The bug lived in the round-trip: `addAssistantMessage` (the sibling of the correctly-tagged
     * `addAndPersistAssistantMessage`) created every non-agent reply with routedLayer=null, so the
     * ShowAssistantMessage → AppendAssistantMessage hop dropped the tier. This drives the real path:
     * the ViewModel emits the inline FindItems reply, the test replays ChatRoute's round-trip
     * (forwarding effect.routedLayer), and then a thumb-down on the resulting bubble is measured.
     */
    @Test
    fun findItemsReply_classifierLayer_thumbDownReportsClassifierNotUnknown() = runTest {
        val analytics = FakeAnalyticsTracker()
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FindItems,
                confidence = 0.9f,
                layer = RoutingLayer.Classifier,
            )
        )
        val fakeDispatcher = FakeToolCallDispatcher(
            outcome = DispatchOutcome.Success("chat_dispatch_find_success", listOf("1", "«milk» in Shopping"))
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher, analytics = analytics)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("find milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        // The inline reply effect must carry the classifying tier — the field that was always null.
        val effect = assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effectDeferred.await())
        assertEquals(RoutingLayer.Classifier, effect.routedLayer,
            "The FindItems inline reply must carry classification.layer, not null")

        // Replay the ChatRoute round-trip (resolve key → AppendAssistantMessage, forwarding the tier).
        vm.sendIntent(
            ChatScreenIntent.AppendAssistantMessage(
                text = "Found «milk» in Shopping",
                routedLayer = effect.routedLayer,
            )
        )
        testScheduler.advanceUntilIdle()

        // The appended assistant bubble carries the tier onto ChatMessage.routedLayer.
        val assistantMsg = vm.screenState.value.messages.last { it.role == ChatRole.Assistant }
        assertEquals(RoutingLayer.Classifier, assistantMsg.routedLayer,
            "AppendAssistantMessage must set routedLayer on the created ChatMessage")

        // Thumb-down on that bubble now reports the real tier instead of "unknown".
        vm.sendIntent(ChatScreenIntent.OnFeedbackOpen(assistantMsg))
        val params = analytics.paramsOf("ai_chat_thumb_down")
        assertNotNull(params, "ai_chat_thumb_down must fire on feedback open")
        assertEquals("Classifier", params["routed_layer"],
            "The thumb-down must report Classifier, not the pre-fix 'unknown'")
    }

    // ── A8c. chat response-language picker emits language_selected{source=chat_picker} ──

    /**
     * The chat reply-language picker is the SECOND surface of `language_selected` (the first is the
     * Settings UI picker, SettingsViewModelTest). Mirrors that test: an explicit pick emits the
     * shared event with source="chat_picker" so the two surfaces stay distinguishable in Amplitude.
     */
    @Test
    fun onResponseLanguageSelected_emitsLanguageSelectedWithChatPickerSource() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected("hi"))
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("language_selected")
        assertNotNull(params, "language_selected must fire on an explicit chat-picker selection")
        assertEquals("hi", params["language"])
        assertEquals("chat_picker", params["source"])
    }

    // ── A8d. chat language picker: Auto (null) maps to "system", consistent with Settings ──

    @Test
    fun onResponseLanguageSelected_autoNull_mapsLanguageToSystem() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected(null))
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("language_selected")
        assertNotNull(params)
        assertEquals("system", params["language"], "null code (Auto) must map to \"system\"")
        assertEquals("chat_picker", params["source"])
    }

    // ── A9. feedback — migrated to catalog name, submit emits ai_chat_feedback ──
    @Test
    fun onFeedbackSubmit_emitsFeedbackWithMigratedParams() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(analytics = analytics)
        // routedLayer=Local models a message persisted BEFORE the Layer 1 disconnect (2026-07-15):
        // the classifier no longer produces Local, but Room history replays it, and feedback on an
        // old message must still report the layer it was actually answered by.
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
        val repo = FakeAiChatRepository(classifyResult = deleteClassification())
        val analytics = FakeAnalyticsTracker()
        val vm = makeVm(repo = repo, analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.screenState.value.pendingChoice)

        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("execute"))
        testScheduler.advanceUntilIdle()

        val confirmed = analytics.paramsOf("ai_chat_preview_confirmed")
        assertNotNull(confirmed, "Tapping execute must emit ai_chat_preview_confirmed")
        assertEquals("DeleteItem", confirmed["action_type"])
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

    // ── Layer 3 recent-items context (buildChecklistsSummary) ─────────────────
    //
    // The summary forwarded to the agent (chat_agent CF) must now carry the text of recent
    // items + their checked state + a positional recency proxy, so the model can answer
    // "what did I add recently / find the task about X". Asserted via the agentStep capture.

    private fun freeFormRepo(creditsRemaining: Int = 100) = FakeAiChatRepository(
        classifyResult = IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = 1.0f,
            layer = RoutingLayer.FullChat,
        ),
        agentStepResults = listOf(
            AgentStepResult.Final(content = "ok", creditsRemaining = creditsRemaining),
        ),
    )

    @Test
    fun buildChecklistsSummary_freeFormTurn_includesRecentItemTextAndCounts() = runTest {
        val checklist = Checklist(
            id = 7L,
            name = "Shopping",
            items = listOf(
                ChecklistItem(text = "milk", checked = true),
                ChecklistItem(text = "bread", checked = false),
                ChecklistItem(text = "eggs", checked = false),
            ),
        )
        val repo = freeFormRepo()
        val vm = makeVm(repo = repo, checklistRepo = FakeChecklistRepository(seed = listOf(checklist)))

        vm.sendIntent(ChatScreenIntent.OnInputChange("what did I add to shopping?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "FreeForm must route through agentStep")
        val summary = repo.agentStepChecklists.first()
        assertEquals(1, summary.size)
        val ctx = summary.first()
        assertEquals("Shopping", ctx.name)
        assertEquals(3, ctx.totalItems, "totalItems counts leaf items")
        assertEquals(1, ctx.doneItems, "doneItems counts checked leaf items")
        // Item text must now be present (the whole point of this feature).
        assertEquals(
            listOf("milk", "bread", "eggs"),
            ctx.recentItems.map { it.text },
            "Recent items must carry the actual item text",
        )
        assertEquals(
            listOf(true, false, false),
            ctx.recentItems.map { it.checked },
            "Recent items must carry the checked state",
        )
        assertEquals(
            listOf(0, 1, 2),
            ctx.recentItems.map { it.position },
            "Recent items must carry the 0-based list position (recency proxy)",
        )
    }

    @Test
    fun buildChecklistsSummary_excludesFolderNodes_keepsFreshestTail() = runTest {
        // 8 leaves + 1 folder. Folders must be excluded from recentItems AND from the counts;
        // only the freshest RECENT_ITEMS_PER_CHECKLIST (6) leaves are sent, preserving real position.
        val items = buildList {
            add(ChecklistItem(text = "Produce", checked = false, type = ChecklistNodeType.FOLDER))
            repeat(8) { i -> add(ChecklistItem(text = "item$i", checked = i % 2 == 0)) }
        }
        val checklist = Checklist(id = 1L, name = "Big", items = items)
        val repo = freeFormRepo()
        val vm = makeVm(repo = repo, checklistRepo = FakeChecklistRepository(seed = listOf(checklist)))

        vm.sendIntent(ChatScreenIntent.OnInputChange("summarize"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val ctx = repo.agentStepChecklists.first().first()
        assertEquals(8, ctx.totalItems, "Folder node must not be counted as an item")
        // Freshest 6 of the 8 leaves: item2..item7 (the folder at index 0 shifts leaves to 1..8).
        assertEquals(
            listOf("item2", "item3", "item4", "item5", "item6", "item7"),
            ctx.recentItems.map { it.text },
            "Must keep the last 6 leaf items (the freshest tail), folders excluded",
        )
        // Positions reflect the FULL list index (folder occupies index 0), not the leaf index.
        assertEquals(listOf(3, 4, 5, 6, 7, 8), ctx.recentItems.map { it.position })
        assertTrue(ctx.recentItems.none { it.text == "Produce" }, "Folder text must never be sent")
    }

    @Test
    fun buildChecklistsSummary_respectsGlobalItemBudget() = runTest {
        // 8 checklists × 6 items each = 48 candidate item lines, but the global budget caps the
        // total at RECENT_ITEMS_TOTAL_BUDGET (30). Earlier checklists fill the budget first.
        val checklists = (0 until 8).map { c ->
            Checklist(
                id = c.toLong(),
                name = "List$c",
                items = (0 until 6).map { i -> ChecklistItem(text = "c${c}i$i", checked = false) },
            )
        }
        val repo = freeFormRepo()
        val vm = makeVm(repo = repo, checklistRepo = FakeChecklistRepository(seed = checklists))

        vm.sendIntent(ChatScreenIntent.OnInputChange("what's on my lists"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val summary = repo.agentStepChecklists.first()
        assertEquals(8, summary.size, "All 8 checklists are still summarized (name + counts)")
        val totalRecent = summary.sumOf { it.recentItems.size }
        assertEquals(30, totalRecent, "Total recent items across all lists must not exceed the budget")
        // The 6th checklist onward should be starved of item text once the budget is spent
        // (5 lists × 6 = 30), even though their name + counts are still present.
        assertTrue(summary[0].recentItems.isNotEmpty(), "Earliest list keeps its items")
        assertTrue(summary.last().recentItems.isEmpty(), "Budget-exhausted list sends no item text")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 🔴 RED repro — out of credits is reported as "I didn't catch that" (2026-07-16)
    // docs/todos/2026-07-16-aichat-insufficient-credits-shows-unknown-hint.md
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Free user, 10/10 daily AI credits spent, types «добавь молоко»:
    //
    //   ChatClassifierApiServiceImpl:86   HTTP 402 → RemoteClassificationResult.InsufficientCredits
    //   AiChatRepositoryImpl:136-143      → ChatIntent.Unknown(rawText)          ← reason erased
    //   ChatViewModel:764-777             → ShowAssistantMessage("chat_unknown_intent_hint")
    //                                       + askAiForText → an "Ask AI" button
    //
    // So the app tells the user their phrasing was unclear, and the only way to learn the truth is
    // to tap "Ask AI" — which asks for 3 MORE credits from an empty wallet, 402s again, and only
    // THERE finally says "out of credits". The paywall never appears on the classify path;
    // `chat_insufficient_credits` is emitted only from the agent paths (:1967, :2310).
    //
    // Pre-existing, but the Layer 1 disconnect (docs/decisions/2026-07-15-remove-ai-chat-layer1.md)
    // promoted it from edge case to the DEFAULT free-tier experience: L1 used to answer «добавь
    // молоко» for 0 credits and never reached Layer 2. The ADR priced the disconnect as "free users
    // hit the paywall on the core action" — this is the code failing to charge that accepted price.
    //
    // These tests enter through the classifier API (see [makeVmOutOfCredits]) so they assert the
    // user-visible + Amplitude-visible contract without naming the repository's internal fix.

    // ── R1. The user is told the wallet is empty, not that they were unclear ──

    @Test
    fun sendClick_layer2InsufficientCredits_tellsUserCreditsRanOutInsteadOfBlamingTheirPhrasing() = runTest {
        val vm = makeVmOutOfCredits()

        // sideEffect is a replay=0 SharedFlow — subscribe BEFORE the send or the assert goes vacuous.
        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val effect = effectDeferred.await()
        val message = assertIs<ChatScreenSideEffect.ShowAssistantMessage>(
            effect,
            "A refused turn must answer in the chat, not vanish or snackbar; got $effect",
        )
        assertEquals(
            "chat_insufficient_credits",
            message.messageKey,
            "Out of credits must say so. chat_unknown_intent_hint blames the user's phrasing for a " +
                "billing state — and the string chat_insufficient_credits already exists (EN+RU, " +
                "wired in App.kt:825 + ChatRoute.kt:247)",
        )
        assertNull(
            message.askAiForText,
            "The 'Ask AI' button must NOT be offered here: it asks an empty wallet for 3 more " +
                "credits, 402s again, and is how the user currently has to discover the truth",
        )
    }

    // ── R2. The refused turn is reported honestly to Amplitude ───────────────
    //
    // ChatViewModel:775 reports outcome="answer" for this turn (there was no answer) and
    // creditsForLayer(Classifier)=1 (nothing was charged — 402 IS the refusal). So the outcome
    // distribution is polluted and credits_used over-counts every refusal.
    // "insufficient_credits" is not a new vocabulary word: the voice path already reports exactly
    // that on the same condition (ChatViewModel:1964).

    @Test
    fun sendClick_layer2InsufficientCredits_reportsInsufficientCreditsOutcomeAndZeroCreditsUsed() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVmOutOfCredits(analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params, "The refused turn must still close the funnel, not go unreported")
        assertEquals(
            "insufficient_credits",
            params["outcome"],
            "The user got no answer — reporting outcome='answer' hides every paywall-worthy " +
                "refusal inside the success bucket",
        )
        assertEquals(
            0,
            params["credits_used"],
            "402 means the server charged nothing; billing this turn 1 credit inflates the " +
                "credits_used sum by every refusal",
        )
    }

    // ── R3. The refused turn is not persisted as a paid one ──────────────────
    //
    // Same lie, second surface: ChatViewModel:750-757 prices the user message off the LAYER alone
    // (Classifier → 1), so Room keeps a history row claiming this turn cost a credit.
    // Paired with test #9 (a SUCCESSFUL Classifier turn must stay costCredits=1), this pins the
    // fix to the refusal itself — re-pricing the whole Classifier layer to 0 turns #9 red.

    @Test
    fun sendClick_layer2InsufficientCredits_userMessageCostCreditsIsZero() = runTest {
        val vm = makeVmOutOfCredits()

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val userMsg = vm.screenState.value.messages.first()
        assertEquals(
            0,
            userMsg.costCredits,
            "A 402'd turn charged the user nothing — persisting costCredits=1 makes the chat's own " +
                "history disagree with the wallet",
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Green coverage — the paywall CTA on the out-of-credits reply (2026-07-16)
    // ══════════════════════════════════════════════════════════════════════════
    //
    // The owner's ask: "пусть ии возвращает текстом ответ что недостаточно кредитов и показывает
    // кнопку become pro and get 300 credits now and every day". The reply text is asserted by the
    // RED tests above; these pin the CTA — the conversion moment the Layer 1 disconnect was
    // accepted to buy (docs/decisions/2026-07-15-remove-ai-chat-layer1.md).

    // ── G1. The refusal carries a paywall CTA, and its number comes from Remote Config ──
    //
    // "300" is the Premium daily allowance (RC ai_daily_limit_premium), NOT a constant: the label
    // promises a specific number to a user about to pay for it, so a hardcoded one turns into a
    // false promise the day the limit is retuned. The fake therefore serves a NON-default value —
    // with an echo-the-default fake (300) a hardcoded 300 would look identical and this test would
    // prove nothing.

    @Test
    fun sendClick_layer2InsufficientCredits_offersPaywallCtaWithRemoteConfigCreditAllowance() = runTest {
        val vm = makeVmOutOfCredits(
            remoteConfig = HarnessRemoteConfigProvider(
                longs = mapOf(RemoteConfigKeys.AI_DAILY_LIMIT_PREMIUM to 777L),
            ),
        )

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val message = assertIs<ChatScreenSideEffect.ShowAssistantMessage>(effectDeferred.await())
        assertEquals(
            777,
            message.paywallCtaCredits,
            "The CTA advertises the Premium daily allowance, which lives in Remote Config " +
                "(ai_daily_limit_premium). A hardcoded number silently lies once that key moves",
        )
    }

    // ── G2. The CTA survives the ChatRoute round-trip onto the bubble ─────────
    //
    // ShowAssistantMessage is resolved to text by the host (ChatRoute / App.kt dock) and sent back
    // as AppendAssistantMessage. A field dropped in that round-trip renders a bubble with no
    // button — the message would say "upgrade to Premium" and offer no way to do it.

    @Test
    fun appendAssistantMessage_withPaywallCta_bubbleCarriesItForRendering() = runTest {
        val vm = makeVm()

        vm.sendIntent(
            ChatScreenIntent.AppendAssistantMessage(
                text = "Not enough credits for AI assist.",
                paywallCtaCredits = 300,
            )
        )
        testScheduler.advanceUntilIdle()

        val assistantMsg = vm.screenState.value.messages.last()
        assertEquals(
            300,
            assistantMsg.paywallCtaCredits,
            "ChatMessageBubble shows the CTA off this field; dropping it in the host round-trip " +
                "leaves the user told to upgrade with nothing to tap",
        )
    }

    // ── G3. Tapping the CTA navigates to the paywall, attributed to the limit ──
    //
    // The source tag is load-bearing, not decoration. The Layer 1 disconnect
    // (docs/decisions/2026-07-15-remove-ai-chat-layer1.md) lists "paywall_shown with source=chat —
    // expect a rise in users hitting the limit" as the signal it is judged on, and the owner
    // accepted "free users hit the paywall on the core action" as its price. If this tap reports
    // the same source as the credits chip, the funnel merges a user who RAN OUT mid-turn with one
    // who tapped the balance out of curiosity — and the ADR's question gets answered with a number
    // that cannot answer it. Same failure class as checklist_created / folder_deleted: the metric
    // keeps its name and quietly changes meaning.

    @Test
    fun onPaywallCtaClick_navigatesToPaywallAttributedToCreditExhaustionNotTheChip() = runTest {
        val vm = makeVm()

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnPaywallCtaClick)

        val effect = assertIs<ChatScreenSideEffect.NavigateToPaywall>(
            effectDeferred.await(),
            "The CTA must reach the paywall — both hosts (ChatRoute + the App.kt dock) observe " +
                "this effect. Without it the button is decoration on a conversion moment",
        )
        assertEquals(
            "chat_insufficient_credits",
            effect.source,
            "paywall_shown must show WHY the user arrived. Literal, not the constant, on purpose: " +
                "this string is an Amplitude dimension — renaming the constant is free, renaming " +
                "the wire value silently breaks every saved chart built on it",
        )
        assertNotEquals(
            ChatScreenSideEffect.NavigateToPaywall.SOURCE_CREDITS_CHIP,
            effect.source,
            "Hitting the credit limit is not the same event as tapping the credits chip; the " +
                "disconnect's whole cost/benefit read depends on separating them",
        )
    }

    // ── G4. The reject flow ("I meant something else") answers a 402 the same way ──
    //
    // Second classify() call-site (escalateChoice, skipLayer1=true). It can 402 exactly like the
    // send path, and before 2026-07-16 the refusal arrived as Unknown and fell into the
    // runAgentTurn branch: a silent 3-credit request to a wallet that had just refused 1. The user
    // saw a generic error and paid for the privilege.
    //
    // Reachable today only via a Local-source choice (which the parked Layer 1 no longer mints),
    // so this is also the guard that re-routing L1 does not re-open the hole.

    @Test
    fun onPreviewReject_layer2InsufficientCredits_tellsUserAndDoesNotBillAgentTurn() = runTest {
        val repo = FakeAiChatRepository(
            // First classify → a Local-source question, so tapping "escape" re-classifies.
            classifyResult = deleteClassification(layer = RoutingLayer.Local),
            // The re-classify (skipLayer1=true) hits the empty wallet.
            skipLayer1Result = IntentClassification(
                intent = ChatIntent.InsufficientCredits,
                confidence = 1.0f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = null,
            ),
        )
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnInputChange("delete milk from shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertNotNull(vm.screenState.value.pendingChoice)

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnChoiceSelected("escape"))
        testScheduler.advanceUntilIdle()

        val message = assertIs<ChatScreenSideEffect.ShowAssistantMessage>(
            effectDeferred.await(),
            "A 402 on the reject path must answer in the chat, not fail silently",
        )
        assertEquals("chat_insufficient_credits", message.messageKey)
        assertNotNull(message.paywallCtaCredits, "The reject path offers the same paywall CTA")
        assertEquals(
            0,
            repo.agentStepCallCount,
            "Escalating to the agent here bills 3 credits to a wallet that just refused 1 — the " +
                "quiet version of the same bug",
        )
        assertFalse(vm.screenState.value.isProcessing, "The turn is over — the spinner must stop")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // G5-G6. The SAME wall on Layer 3 — Deep Thinking ON (2026-07-16)
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Found by @bug-pattern-reviewer AFTER the Layer 2 fix shipped: the classify path was fixed
    // and its Layer 3 sibling was left with all three defects intact (CTA-less snackbar,
    // outcome="error", credits_used=3 on a turn charged 0). That is the recurring shape — "fixed
    // one emit site, the sibling kept the bug" — so these tests exist to hold the LAYER 3 door
    // shut, not just to cover a branch.
    //
    // This is a live path, not a corner: Deep Thinking ON skips Layer 2 by design and lands here
    // directly. The likeliest repro is a free user with 1-2 credits — Layer 2 passes and takes 1,
    // Layer 3 wants 3 → 402.

    // ── G5. Layer 3 refusal answers with the CTA, like Layer 2 does ──────────

    @Test
    fun deepThinkingSend_layer3InsufficientCredits_showsSameCtaReplyAsLayer2() = runTest {
        val vm = makeVmDeepThinkingOutOfCredits()

        val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.sideEffect.first()
        }

        vm.sendIntent(ChatScreenIntent.OnInputChange("what should I pack for a trip?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val message = assertIs<ChatScreenSideEffect.ShowAssistantMessage>(
            effectDeferred.await(),
            "A refused Layer 3 turn must answer in the chat. A ShowSnackbar here is the shipped " +
                "bug: it vanishes, carries no button, and the owner asked for the Become Pro CTA " +
                "on the out-of-credits reply — on every path that can produce one",
        )
        assertEquals("chat_insufficient_credits", message.messageKey)
        assertNotNull(
            message.paywallCtaCredits,
            "Out of credits on Layer 3 is the same conversion moment as on Layer 2 — a snackbar " +
                "tells the user to upgrade and gives them nothing to tap",
        )
        assertNull(message.askAiForText, "Never offer 'Ask AI' to an empty wallet")
    }

    // ── G6. Layer 3 refusal is reported honestly ─────────────────────────────
    //
    // outcome="error" buried every credit refusal in the same bucket as "the agent crashed", and
    // credits_used defaults to creditsForLayer(FullChat)=3 — inflating the sum by 3 per refusal
    // on a turn the server charged 0 for. routed_layer must still say FullChat: the funnel needs
    // to tell a 1-credit refusal from a 3-credit one even though both charged nothing.

    @Test
    fun deepThinkingSend_layer3InsufficientCredits_reportsRefusalNotErrorAndZeroCreditsUsed() = runTest {
        val analytics = FakeAnalyticsTracker()
        val vm = makeVmDeepThinkingOutOfCredits(analytics = analytics)

        vm.sendIntent(ChatScreenIntent.OnInputChange("what should I pack for a trip?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val params = analytics.paramsOf("ai_chat_response_received")
        assertNotNull(params, "The refused turn must still close the funnel")
        assertEquals(
            "insufficient_credits",
            params["outcome"],
            "outcome='error' cannot distinguish 'out of credits' from 'the AI fell over' — one " +
                "needs a paywall, the other needs an on-call",
        )
        assertEquals(
            0,
            params["credits_used"],
            "402 means the server charged nothing; the FullChat list price (3) is what the turn " +
                "WOULD have cost, not what it did",
        )
        assertEquals(
            "FullChat",
            params["routed_layer"],
            "The refusal happened on Layer 3 and the funnel should say so",
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Stage 3 — the chat remembers its tool rounds across turns (and restarts)
    // ══════════════════════════════════════════════════════════════════════════

    // ── 60. A completed tool round is persisted, then restored into the NEXT turn's seed ──

    @Test
    fun agentTurn_persistsToolRound_andRestoresItIntoTheNextTurnTranscript() = runTest {
        val readCall = AgentToolCall(
            id = "call-1",
            name = "read_checklist",
            args = buildJsonObject { put("name", "Shopping") },
        )
        // Turn 1: read-only tool round (dispatched with no plan-card) → Final.
        // Turn 2: a plain Final. The 3rd agentStep is turn 2's FIRST round — its seed must carry
        // turn 1's rounds spliced back in.
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.FreeForm,
                confidence = 1.0f,
                layer = RoutingLayer.FullChat,
            ),
            agentStepResults = listOf(
                AgentStepResult.ToolCalls(calls = listOf(readCall), creditsRemaining = 297),
                AgentStepResult.Final(content = "Shopping has 3 items.", creditsRemaining = 297),
                AgentStepResult.Final(content = "You are welcome.", creditsRemaining = 294),
            ),
        )
        val transcriptRepo = FakeAgentTranscript()
        val vm = makeVm(
            repo = repo,
            dispatcher = FakeToolCallDispatcher(
                outcome = DispatchOutcome.ChecklistContent(checklistName = "Shopping", items = emptyList()),
            ),
            agentTranscriptRepo = transcriptRepo,
        )

        // Turn 1
        vm.sendIntent(ChatScreenIntent.OnInputChange("what is in shopping?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, transcriptRepo.appendedTurnIds.size,
            "the read round of turn 1 must be persisted exactly once")

        // Turn 2
        vm.sendIntent(ChatScreenIntent.OnInputChange("thanks"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val turn2Seed = repo.agentStepTranscripts[2]
        val hasRestoredRound = turn2Seed.any { it is AgentTranscriptEntry.ModelToolCalls } &&
            turn2Seed.any { it is AgentTranscriptEntry.ToolResults }
        assertTrue(hasRestoredRound,
            "turn 2's seed must splice back turn 1's persisted tool round — that is the memory")

        // Pairing invariant survives the restore: a ToolResults is never orphaned.
        val calls = turn2Seed.count { it is AgentTranscriptEntry.ModelToolCalls }
        val results = turn2Seed.count { it is AgentTranscriptEntry.ToolResults }
        assertEquals(calls, results, "restored transcript must keep call↔results pairs balanced")
    }

    // ── 61. request_id is stable across a turn's rounds, and differs between turns ──

    @Test
    fun agentTurn_requestId_isStableWithinATurn_andFreshPerTurn() = runTest {
        val readCall = AgentToolCall(
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
                // Turn 1 spans two rounds (ToolCalls then Final) → two agentStep calls.
                AgentStepResult.ToolCalls(calls = listOf(readCall), creditsRemaining = 297),
                AgentStepResult.Final(content = "Shopping has 3 items.", creditsRemaining = 297),
                // Turn 2 is a single round.
                AgentStepResult.Final(content = "You are welcome.", creditsRemaining = 294),
            ),
        )
        val vm = makeVm(
            repo = repo,
            dispatcher = FakeToolCallDispatcher(
                outcome = DispatchOutcome.ChecklistContent(checklistName = "Shopping", items = emptyList()),
            ),
        )

        vm.sendIntent(ChatScreenIntent.OnInputChange("what is in shopping?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        vm.sendIntent(ChatScreenIntent.OnInputChange("thanks"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        val ids = repo.agentStepRequestIds
        assertEquals(3, ids.size, "three agentStep calls expected (2 for turn 1, 1 for turn 2)")
        assertTrue(ids.all { it != null && it.isNotBlank() }, "every round must carry a request_id")

        // Same turn → same key (a transport retry of round 2 must not re-charge).
        assertEquals(ids[0], ids[1], "both rounds of turn 1 must share one request_id")
        // New turn → new key (else the server reads it as a replay and the turn is free).
        assertNotEquals(ids[1], ids[2], "turn 2 must mint a fresh request_id")
    }

    // ── 62. Clear chat wipes the agent's tool memory too, not just the visible prose ──

    @Test
    fun clearChat_alsoClearsAgentTranscript() = runTest {
        val readCall = AgentToolCall(
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
                AgentStepResult.ToolCalls(calls = listOf(readCall), creditsRemaining = 297),
                AgentStepResult.Final(content = "Shopping has 3 items.", creditsRemaining = 297),
            ),
        )
        val transcriptRepo = FakeAgentTranscript()
        val vm = makeVm(
            repo = repo,
            dispatcher = FakeToolCallDispatcher(
                outcome = DispatchOutcome.ChecklistContent(checklistName = "Shopping", items = emptyList()),
            ),
            agentTranscriptRepo = transcriptRepo,
        )

        vm.sendIntent(ChatScreenIntent.OnInputChange("what is in shopping?"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()
        assertTrue(transcriptRepo.appendedTurnIds.isNotEmpty(), "precondition: a round was stored")

        vm.sendIntent(ChatScreenIntent.OnClearChat)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyMap(), transcriptRepo.loadForTurns(transcriptRepo.appendedTurnIds),
            "Clear chat must leave no tool memory behind the wiped conversation")
    }

    // ── 63. Response-language override — WIRE: selection reaches agentStep ─────

    /**
     * Builds a ViewModel whose every send routes straight to the agent loop (FreeForm/FullChat) so
     * the reply-language override on `agentStep(...)` is exercised. One Final result per round; the
     * fake repeats the last result when its list is exhausted, so multiple sends are fine.
     */
    private fun freeFormAgentRepo() = FakeAiChatRepository(
        classifyResult = IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = 1.0f,
            layer = RoutingLayer.FullChat,
        ),
        agentStepResults = listOf(
            AgentStepResult.Final(content = "ok", creditsRemaining = 100),
        ),
    )

    @Test
    fun onResponseLanguageSelected_code_forwardsThatLanguageToAgentStep() = runTest {
        val repo = freeFormAgentRepo()
        val vm = makeVm(repo = repo)

        // Pin Spanish, then send a free-form turn.
        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected("es"))
        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "the free-form send must reach agentStep")
        assertEquals(
            "es",
            repo.agentStepResponseLanguages.last(),
            "the pinned reply-language must be forwarded to agentStep as responseLanguage",
        )
    }

    @Test
    fun onResponseLanguageSelected_auto_forwardsNullAfterHavingSelectedCode() = runTest {
        val repo = freeFormAgentRepo()
        val vm = makeVm(repo = repo)

        // First pin Spanish and send — proves a non-null override is actually carried…
        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected("es"))
        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()
        assertEquals("es", repo.agentStepResponseLanguages[0], "precondition: Spanish carried on turn 1")

        // …then switch back to Auto (null) and send again — the next turn must drop the override.
        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected(null))
        vm.sendIntent(ChatScreenIntent.OnInputChange("and the week after"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(2, repo.agentStepCallCount, "the second send must also reach agentStep")
        assertNull(
            repo.agentStepResponseLanguages[1],
            "selecting Auto must forward null responseLanguage (server decides the reply language)",
        )
    }

    @Test
    fun agentStep_noLanguageSelected_forwardsNullResponseLanguage() = runTest {
        val repo = freeFormAgentRepo()
        val vm = makeVm(repo = repo)

        // No OnResponseLanguageSelected at all — the default must be Auto (null).
        vm.sendIntent(ChatScreenIntent.OnInputChange("plan my week"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.agentStepCallCount, "the free-form send must reach agentStep")
        assertNull(
            repo.agentStepResponseLanguages.last(),
            "with no language pinned, agentStep must receive null responseLanguage",
        )
    }

    // ── 64. Response-language override — PERSIST + sheet + state mirror ────────

    @Test
    fun onResponseLanguageSelected_code_persistsClosesSheetAndReflectsInState() = runTest {
        val prefs = FakeAiChatPreferencesRepository()
        val vm = makeVm(aiChatPreferencesRepo = prefs)

        // Open the sheet first so "closes on select" is a real transition, not the default.
        vm.sendIntent(ChatScreenIntent.OnResponseLanguageClick)
        assertTrue(vm.screenState.value.showResponseLanguageSheet, "precondition: sheet is open")

        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected("es"))
        testScheduler.advanceUntilIdle()

        assertEquals("es", prefs.lastResponseLanguageSet, "the code must be persisted to DataStore")
        assertEquals(
            "es",
            vm.screenState.value.responseLanguage,
            "state.responseLanguage must mirror the persisted selection (single source of truth)",
        )
        assertFalse(
            vm.screenState.value.showResponseLanguageSheet,
            "picking a language must close the picker sheet",
        )
    }

    @Test
    fun onResponseLanguageSelected_auto_persistsNullAndReflectsAutoInState() = runTest {
        val prefs = FakeAiChatPreferencesRepository()
        val vm = makeVm(aiChatPreferencesRepo = prefs)

        // Pin a code first so switching to Auto is an observable change, not the initial null.
        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected("es"))
        testScheduler.advanceUntilIdle()
        assertEquals("es", vm.screenState.value.responseLanguage, "precondition: Spanish pinned")

        vm.sendIntent(ChatScreenIntent.OnResponseLanguageSelected(null))
        testScheduler.advanceUntilIdle()

        assertNull(prefs.lastResponseLanguageSet, "Auto must persist null")
        assertNull(
            vm.screenState.value.responseLanguage,
            "state.responseLanguage must mirror Auto (null) after switching back",
        )
    }

    @Test
    fun onResponseLanguageClick_opensResponseLanguageSheet() {
        val vm = makeVm()
        assertFalse(vm.screenState.value.showResponseLanguageSheet, "sheet is closed by default")

        vm.sendIntent(ChatScreenIntent.OnResponseLanguageClick)

        assertTrue(
            vm.screenState.value.showResponseLanguageSheet,
            "tapping the response-language row must open the picker sheet",
        )
    }

    // ── 65. OnPrefillInput — sets the composer text without sending ───────────

    @Test
    fun onPrefillInput_setsInputTextAndDoesNotSend() = runTest {
        val repo = freeFormAgentRepo()
        val vm = makeVm(repo = repo)

        vm.sendIntent(ChatScreenIntent.OnPrefillInput("buy milk and eggs"))
        testScheduler.advanceUntilIdle()

        assertEquals(
            "buy milk and eggs",
            vm.screenState.value.inputText,
            "OnPrefillInput must load the text into the composer",
        )
        // Prefill only fills the composer — nothing is classified or sent until the user taps send.
        assertEquals(0, repo.classifyCallCount, "prefill must NOT classify")
        assertEquals(0, repo.agentStepCallCount, "prefill must NOT reach the agent loop")
        assertEquals(0, vm.screenState.value.messages.size, "prefill must NOT append a message")
    }
}
