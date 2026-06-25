package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteTranscriptionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscribeAudioApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.LocalIntentRouterImpl
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository.AiChatRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.reflect.KClass

// ════════════════════════════════════════════════════════════════════════════
// AI Chat scenario test harness — Tier 1 (offline, free, zero-network)
//
// PURPOSE
//   A reusable, data-driven harness for the AI Chat CLIENT routing. It drives the
//   REAL Layer-1 parser (LocalIntentRouterImpl) + REAL routing (AiChatRepositoryImpl
//   + ChatViewModel) through the same public Intent the UI uses (OnInputChange +
//   OnSendClick), while every CLOUD layer is a FAKE (classifier / completion / agent /
//   transcribe). This means:
//     - Real parser behavior is exercised (free, deterministic).
//     - Escalation cases just record "fake cloud invoked" (no network, no Gemini cost).
//     - The ToolCallDispatcher is a FAKE: it records every dispatched ToolCall and
//       returns a per-scenario-configurable DispatchOutcome (default Success).
//
//   This is a FOUNDATION to GROW: add a row to [ChatScenario] list, get a routing
//   assertion for free. The runner aggregates pass/fail into one readable map so the
//   suite is a living dashboard of where the client routing is solid vs. where the AI
//   still needs work (expectRedNow rows = the improvement roadmap).
//
// COST SAFETY
//   No real HTTP client, no Gemini, no Firebase. The cloud fakes are pure Kotlin and
//   return canned results synchronously. Running this harness is 100% free.
// ════════════════════════════════════════════════════════════════════════════

// ─── Scenario model ──────────────────────────────────────────────────────────

/**
 * One data-driven AI-chat routing scenario.
 *
 * @param id           Short stable id ("C3") — used in the aggregate report.
 * @param title        Human-readable label.
 * @param input        The exact user text fed via [ChatScreenIntent.OnInputChange].
 * @param locale       Parsing locale (En / Ru). The fake [ChatLocaleProvider] returns this.
 * @param lists        Checklist names present in context (seed the fake repository).
 * @param expected     The routing outcome to assert (see [Expected]).
 * @param dispatchOutcome The result the FAKE dispatcher returns for any ToolCall dispatched
 *                     in this scenario. Defaults to a generic Success. Set to AmbiguousMatch /
 *                     NotFound / etc. to model the repository layer's verdict (C5, C11, C23 …).
 * @param tapExecute   When true, after the first send the harness taps the primary "execute"
 *                     chip if a write-intent choice is shown — needed to drive the dispatcher
 *                     (and thus reach AmbiguousMatch → "which list?" or NotFound → message).
 * @param expectRedNow Documents a KNOWN GAP: the scenario is expected to FAIL on the current
 *                     code (an AI-improvement roadmap item). A red expectRedNow scenario is
 *                     reported but does NOT fail the build; an expectRedNow scenario that
 *                     unexpectedly PASSES is flagged (it got fixed → flip the flag).
 */
data class ChatScenario(
    val id: String,
    val title: String,
    val input: String,
    val locale: ChatLocale,
    val lists: List<String> = emptyList(),
    val expected: Expected,
    val dispatchOutcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added", listOf("item")),
    val tapExecute: Boolean = false,
    /**
     * Files to seed into [ChatScreenState.pendingAttachments] (via [ChatScreenIntent.OnAttachmentPicked])
     * BEFORE the send. Needed by attachment scenarios (C21/C22/C23): AttachToItem only produces a
     * preview when attachments are present, otherwise the VM emits a "no files" snackbar.
     */
    val attachments: List<com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment> = emptyList(),
    val expectRedNow: Boolean = false,
)

/**
 * What a scenario asserts about the routing outcome. Each variant maps to an observable
 * effect: the user message's routed layer, the fake dispatcher's recorded calls, the
 * ViewModel's [ChatScreenState.pendingChoice], or a side-effect/assistant message.
 */
sealed interface Expected {
    /** Layer-1 classified the input to this [ChatIntent] subtype (asserted on the user message's routedLayer = Local + intent kind via the dispatched call / pending choice). */
    data class Classified(val intentType: KClass<out ChatIntent>) : Expected

    /** The fake dispatcher received at least one ToolCall matching [match]. */
    data class Dispatches(val description: String, val match: (ToolCall) -> Boolean) : Expected

    /**
     * pendingChoice is a "which list?" choice whose candidate Execute chips target exactly
     * [lists] (order-independent), with the captured item text matching [itemContains].
     * This is the C3 flagship shape.
     */
    data class PickListChoice(val lists: List<String>, val itemContains: String) : Expected

    /** pendingChoice != null (any choice block: ambiguous match / options / write preview). */
    data object AnyChoice : Expected

    /** A cloud fake was invoked (FreeForm/agent routing) — i.e. the input escalated past Layer 1/2. */
    data object Escalates : Expected

    /** A visible assistant/snackbar message was emitted (NotFound, blank input, Unknown hint). */
    data object ShowsMessage : Expected
}

// ─── Fakes (cloud = zero cost) ───────────────────────────────────────────────

/** Records every dispatched ToolCall; returns a per-scenario [outcome]. */
class RecordingToolCallDispatcher(
    private val outcome: DispatchOutcome,
) : ToolCallDispatcher {
    val dispatched = mutableListOf<ToolCall>()
    override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome {
        dispatched.add(toolCall)
        return outcome
    }
}

/**
 * Fake Layer-2 classifier. Returns [result] and records invocation. For an OFFLINE harness
 * we default to [RemoteClassificationResult.ServiceError]: this makes Layer-2 a no-op that
 * gracefully degrades to the Layer-1 result, so escalation cases simply prove "cloud was
 * consulted" via [callCount] without any real classification.
 */
class FakeClassifierApi(
    private val result: RemoteClassificationResult = RemoteClassificationResult.ServiceError,
) : ChatClassifierApiService {
    var callCount = 0
    override suspend fun classify(userId: String, text: String, locale: ChatLocale): RemoteClassificationResult {
        callCount++
        return result
    }
}

/** Fake Layer-3 completion. ServiceError by default; records invocation. */
class FakeCompletionApi(
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

/**
 * Fake agent CF (Layer 3). ServiceError by default → the agent loop emits a
 * `chat_completion_error` assistant message (a visible response), with zero cost.
 * [callCount] proves the agent path was reached for escalation scenarios.
 */
class FakeAgentApi(
    private val result: AgentStepResult = AgentStepResult.ServiceError,
) : ChatAgentApiService {
    var callCount = 0
    override suspend fun step(
        userId: String,
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
    ): AgentStepResult {
        callCount++
        return result
    }
}

class FakeTranscribeApi(
    private val result: RemoteTranscriptionResult = RemoteTranscriptionResult.ServiceError,
) : TranscribeAudioApiService {
    override suspend fun transcribe(
        userId: String,
        audioBase64: String,
        mimeType: String,
        locale: ChatLocale,
    ): RemoteTranscriptionResult = result
}

/** Non-blank userId so the Layer-2 path is reachable (blank userId short-circuits Layer 2). */
class HarnessUserDataRepository(
    private val userId: String = "u1",
) : UserDataRepository {
    private val flow = MutableStateFlow(UserData(userId = userId, aiCredits = 100))
    override fun getUserDataFlow(): StateFlow<UserData> = flow
    override suspend fun getUserData(): UserData = flow.value
    override suspend fun update(userData: UserData) { flow.value = userData }
    override suspend fun ensureUserRegistered(): Result<RegistrationData> =
        Result.success(RegistrationData(userData = UserData(userId = userId), isNewUser = false))
    override suspend fun syncWithServer(): Result<RegistrationData> =
        Result.success(RegistrationData(userData = UserData(userId = userId), isNewUser = false))
    override suspend fun isPaywallLinked(): Boolean = false
    override suspend fun setPaywallLinked(linked: Boolean) = Unit
    override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
    override suspend fun getFirstLaunchAtMillis(): Long = 0L
}

class FakeAiChatPreferences(initial: Boolean = false) : AiChatPreferencesRepository {
    private val flow = MutableStateFlow(initial)
    override val deepThinkingEnabledFlow: Flow<Boolean> = flow
    override suspend fun setDeepThinkingEnabled(enabled: Boolean) { flow.value = enabled }
}

class FakeChatHistory : ChatHistoryRepository {
    private val stored = mutableListOf<ChatMessage>()
    override fun observeRecent(limit: Int): Flow<List<ChatMessage>> = flowOf(stored.takeLast(limit))
    override suspend fun append(message: ChatMessage) { stored.add(message) }
    override suspend fun clear() { stored.clear() }
    override suspend fun count(): Int = stored.size
}

/**
 * Minimal fake checklist repository: seeds [checklists] from scenario list-names so
 * context-bias and summary code paths have data, without any DB. Names are turned into
 * empty-item checklists (the dispatcher is faked, so item content is irrelevant here).
 */
class HarnessChecklistRepository(
    listNames: List<String>,
) : ChecklistRepository {
    private val seed: List<Checklist> = listNames.mapIndexed { index, name ->
        Checklist(id = (index + 1).toLong(), name = name, items = emptyList())
    }
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

class FixedLocaleProvider(private val locale: ChatLocale) : ChatLocaleProvider {
    override fun current(): ChatLocale = locale
}

object HarnessNoOpLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

class HarnessAnalytics : AnalyticsTracker {
    override fun setUserId(userId: String) = Unit
    override fun setUserProperties(properties: Map<String, Any>) = Unit
    override fun screenView(name: String) = Unit
    override fun event(name: String, params: Map<String, Any>) = Unit
}

// ─── Wiring helper: REAL parser + REAL routing + FAKE cloud + FAKE dispatcher ─

/**
 * Builds a [ChatViewModel] wired with the REAL Layer-1 parser ([LocalIntentRouterImpl])
 * and REAL routing ([AiChatRepositoryImpl]), with every cloud layer and the dispatcher
 * faked. [dispatcher] is returned so the caller can inspect recorded ToolCalls.
 *
 * Returns the assembled VM plus the fakes whose state the runner observes.
 */
class HarnessRig(
    val viewModel: ChatViewModel,
    val dispatcher: RecordingToolCallDispatcher,
    val classifierApi: FakeClassifierApi,
    val completionApi: FakeCompletionApi,
    val agentApi: FakeAgentApi,
)

/**
 * Renders a ToolCall to a short preview string for the agent plan-card. Real renderer is
 * Android-only; this minimal one is enough for the offline harness (the agent path is faked
 * to ServiceError, so render is rarely hit — but the VM requires a non-null renderer).
 */
private object HarnessPreviewRenderer :
    com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer {
    override fun render(toolCall: ToolCall): String = toolCall.toString()
}

fun buildHarnessRig(scenario: ChatScenario): HarnessRig {
    val dispatcher = RecordingToolCallDispatcher(scenario.dispatchOutcome)
    val classifierApi = FakeClassifierApi()
    val completionApi = FakeCompletionApi()
    val agentApi = FakeAgentApi()
    val prefs = FakeAiChatPreferences(initial = false)
    val userRepo = HarnessUserDataRepository("u1")

    // REAL repository — REAL Layer-1 parser, FAKE cloud layers (zero cost).
    val repository = AiChatRepositoryImpl(
        router = LocalIntentRouterImpl(HarnessNoOpLogger),
        classifierApi = classifierApi,
        completionApi = completionApi,
        transcribeApi = FakeTranscribeApi(),
        chatAgentApi = agentApi,
        userDataRepository = userRepo,
        aiChatPreferencesRepository = prefs,
        logger = HarnessNoOpLogger,
    )

    val viewModel = ChatViewModel(
        aiChatRepository = repository,
        toolCallDispatcher = dispatcher,
        previewRenderer = HarnessPreviewRenderer,
        localeProvider = FixedLocaleProvider(scenario.locale),
        chatHistoryRepository = FakeChatHistory(),
        checklistRepository = HarnessChecklistRepository(scenario.lists),
        userDataRepository = userRepo,
        aiChatPreferencesRepository = prefs,
        analytics = HarnessAnalytics(),
        logger = HarnessNoOpLogger,
    )

    return HarnessRig(viewModel, dispatcher, classifierApi, completionApi, agentApi)
}

// ─── State observation helpers ───────────────────────────────────────────────

/** Candidate-list Execute tool calls of an AmbiguousMatch / which-list pendingChoice. */
fun ChatScreenState.pendingExecuteToolCalls(): List<ToolCall> =
    pendingChoice?.choice?.options
        ?.mapNotNull { (it.action as? ChoiceAction.Execute)?.toolCall }
        ?: emptyList()

/** checklistHint of an AddItem-like ToolCall, else null. */
fun ToolCall.hintOrNull(): String? = when (this) {
    is ToolCall.AddItem -> checklistHint
    is ToolCall.DeleteItem -> checklistHint
    is ToolCall.CompleteItem -> checklistHint
    is ToolCall.SetItemReminder -> checklistHint
    is ToolCall.AttachToItem -> checklistHint
    is ToolCall.AddItems -> checklistHint
    else -> null
}

/** itemText of an item-bearing ToolCall, else "". */
fun ToolCall.itemTextOrEmpty(): String = when (this) {
    is ToolCall.AddItem -> itemText
    is ToolCall.DeleteItem -> itemText
    is ToolCall.CompleteItem -> itemText
    is ToolCall.SetItemReminder -> itemText
    is ToolCall.AttachToItem -> itemText
    is ToolCall.CreateChecklist -> name
    else -> ""
}
