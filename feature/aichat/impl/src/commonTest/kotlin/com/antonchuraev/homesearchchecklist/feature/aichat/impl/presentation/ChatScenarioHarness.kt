package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentTranscriptRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatClassifierApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatCompletionApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatHistoryRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteClassificationResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteTranscriptionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscribeAudioApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatScreenSnapshot
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
// Parked Layer-1 scenario harness — Tier 1 (offline, free, zero-network)
//
// ⚠️ THIS HARNESS DOES NOT TEST PRODUCTION ROUTING.
//   Since 2026-07-15 (docs/decisions/2026-07-15-remove-ai-chat-layer1.md) production starts at
//   Layer 2: AiChatRepositoryImpl no longer takes a router, and Layer 1 is disconnected. Live
//   prod routing is covered by repository/AiChatRepositoryImplTest.kt — NOT here.
//
// WHAT IS REAL vs. MIRRORED
//   - REAL   Layer-1 parser (LocalIntentRouterImpl) — the thing under measurement.
//   - REAL   ChatViewModel, driven through the same public Intents the UI uses
//            (OnInputChange + OnSendClick), so a scenario exercises the full round-trip.
//   - MIRROR Routing (ParkedLayer1RoutingRepository) — a TEST-ONLY reimplementation of the
//            ladder Layer 1 *will be re-connected to*. It stands in for the real repository,
//            which can no longer reach the parser.
//   - FAKE   Every cloud layer (classifier / completion / agent / transcribe) and the
//            ToolCallDispatcher, which records dispatched ToolCalls and returns a
//            per-scenario DispatchOutcome (default Success).
//
// PURPOSE
//   The suite measures the PARKED PARSER — not what a user sees today. It is the capability
//   dashboard for deciding when Layer 1 is good enough to re-route (owner's condition: «когда
//   я буду им доволен»; work list: docs/todos/2026-07-13-aichat-layer1-thumbsdown-backlog.md).
//   Without it the parser keeps its 168 unit tests but loses every end-to-end row and rots
//   during the parking period — the exact outcome the ADR exists to prevent.
//
//   A red row here is a gap in the parked parser, NOT a production regression. Do not "fix"
//   prod because this dashboard is red, and do not re-route Layer 1 to make it green — that is
//   a product call. GROW IT: add a row to [ChatScenario]; expectRedNow rows = the roadmap.
//
// ON REVERT
//   When Layer 1 is re-routed, delete ParkedLayer1RoutingRepository and hand the real
//   AiChatRepositoryImpl back to [buildHarnessRig] — the mirror exists only while L1 is parked.
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

/**
 * Records every dispatched ToolCall; returns a per-scenario [outcome].
 *
 * [undoOutcome] / [moveOutcome] back the D1 post-hoc chips (Undo / move-to-list). They default to
 * a generic Success so scenarios that never touch those chips stay unaffected.
 */
class RecordingToolCallDispatcher(
    private val outcome: DispatchOutcome,
    private val undoOutcome: DispatchOutcome = DispatchOutcome.Success("chat_undo_removed", listOf("item")),
    private val moveOutcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added_to", listOf("item", "list")),
) : ToolCallDispatcher {
    val dispatched = mutableListOf<ToolCall>()

    /** Every [undo] call, in order. */
    val undone = mutableListOf<UndoHandle>()

    /** Every [moveAddedItem] call as (handle, targetChecklistName), in order. */
    val moved = mutableListOf<Pair<UndoHandle.AddedItem, String>>()

    override suspend fun dispatch(toolCall: ToolCall): DispatchOutcome {
        dispatched.add(toolCall)
        return outcome
    }

    override suspend fun undo(handle: UndoHandle): DispatchOutcome {
        undone.add(handle)
        return undoOutcome
    }

    override suspend fun moveAddedItem(
        handle: UndoHandle.AddedItem,
        targetChecklistName: String,
    ): DispatchOutcome {
        moved.add(handle to targetChecklistName)
        return moveOutcome
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
        responseLanguage: String?,
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
        screenSnapshot: ChatScreenSnapshot?,
        requestId: String?,
        responseLanguage: String?,
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

class FakeAiChatPreferences(
    initial: Boolean = false,
    initialDefaultChecklistId: Long? = null,
) : AiChatPreferencesRepository {
    private val flow = MutableStateFlow(initial)
    override val deepThinkingEnabledFlow: Flow<Boolean> = flow
    override suspend fun setDeepThinkingEnabled(enabled: Boolean) { flow.value = enabled }

    // ── D2 memory of choice ──
    private val defaultIdFlow = MutableStateFlow(initialDefaultChecklistId)
    override val defaultChecklistIdFlow: Flow<Long?> = defaultIdFlow

    /**
     * Every [setDefaultChecklistId] call, in order — including `null` (the reset). A list rather
     * than a `lastSet` field so a test can tell "never persisted" from "persisted then cleared".
     */
    val defaultChecklistIdWrites = mutableListOf<Long?>()

    override suspend fun setDefaultChecklistId(checklistId: Long?) {
        defaultChecklistIdWrites.add(checklistId)
        defaultIdFlow.value = checklistId
    }

    // ── Response-language override ──
    private val responseLanguageState = MutableStateFlow<String?>(null)
    override val responseLanguageFlow: Flow<String?> = responseLanguageState
    val responseLanguageWrites = mutableListOf<String?>()
    override suspend fun setResponseLanguage(code: String?) {
        responseLanguageWrites.add(code)
        responseLanguageState.value = code
    }
}

class FakeChatHistory : ChatHistoryRepository {
    private val stored = mutableListOf<ChatMessage>()
    override fun observeRecent(limit: Int): Flow<List<ChatMessage>> = flowOf(stored.takeLast(limit))
    override suspend fun append(message: ChatMessage) { stored.add(message) }
    override suspend fun clear() { stored.clear() }
    override suspend fun count(): Int = stored.size
}

/**
 * In-memory [AgentTranscriptRepository] for the ViewModel rigs. Records each appended round so a
 * test can assert the agent's tool memory was persisted, and serves it back keyed by turn so a
 * seed-splice test can drive the restore path — all without a Room instance.
 */
class FakeAgentTranscript : AgentTranscriptRepository {
    private val rounds = linkedMapOf<String, MutableList<AgentTranscriptEntry>>()
    val appendedTurnIds = mutableListOf<String>()

    override suspend fun loadForTurns(
        turnMessageIds: List<String>,
    ): Map<String, List<AgentTranscriptEntry>> =
        turnMessageIds
            .mapNotNull { id -> rounds[id]?.let { id to it.toList() } }
            .toMap()

    override suspend fun appendRound(
        turnMessageId: String,
        calls: AgentTranscriptEntry.ModelToolCalls,
        results: AgentTranscriptEntry.ToolResults,
    ) {
        appendedTurnIds.add(turnMessageId)
        rounds.getOrPut(turnMessageId) { mutableListOf() }.apply {
            add(calls)
            add(results)
        }
    }

    override suspend fun deleteTurn(turnMessageId: String) { rounds.remove(turnMessageId) }

    override suspend fun pruneToRecentTurns(keepTurns: Int) {
        while (rounds.size > keepTurns) rounds.remove(rounds.keys.first())
    }

    override suspend fun clear() { rounds.clear() }
}

/**
 * Minimal fake checklist repository: seeds [checklists] from scenario list-names so
 * context-bias and summary code paths have data, without any DB. Names are turned into
 * empty-item checklists (the dispatcher is faked, so item content is irrelevant here).
 *
 * [seedChecklists] takes precedence when given: D2's chip metadata (item count) and MRU ordering
 * read fields the name-only seed cannot express (`items`, `updatedAt`), so those tests hand over
 * whole [Checklist] objects. Name-only callers are unaffected.
 */
class HarnessChecklistRepository(
    listNames: List<String> = emptyList(),
    seedChecklists: List<Checklist>? = null,
) : ChecklistRepository {
    private val seed: List<Checklist> = seedChecklists ?: listNames.mapIndexed { index, name ->
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

object HarnessNoOpModelExperimentTracker :
    com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker {
    override suspend fun report(variant: String?, modelId: String?, aiFlow: String?) = Unit
    override suspend fun current():
        com.antonchuraev.homesearchchecklist.core.common.api.AiModelArm? = null
}

// ─── Wiring: REAL parser + REAL VM + MIRRORED routing + FAKE cloud/dispatcher ─

/**
 * Routing ladder for the **parked** Layer 1 — the harness's reason to exist.
 *
 * Layer 1 was disconnected from production routing on 2026-07-15
 * (`docs/decisions/2026-07-15-remove-ai-chat-layer1.md`), so [AiChatRepositoryImpl] no longer
 * takes a router and cannot drive this suite any more. The suite is NOT obsolete: it is the
 * capability dashboard for the parser the owner intends to re-route once he is happy with it
 * (work list: `docs/todos/2026-07-13-aichat-layer1-thumbsdown-backlog.md`). Without it the
 * parser keeps 168 unit tests but loses every end-to-end row — exactly the silent rot the ADR
 * says to prevent.
 *
 * So this class reproduces the ladder Layer 1 *will be re-connected to*, around the REAL parser
 * and the fake cloud APIs:
 *   confident (>= 0.7) → return the Layer 1 result (0 credits)
 *   otherwise          → Layer 2 fake; on vague/error degrade back to the Layer 1 result
 *
 * ⚠️ This is a TEST-ONLY mirror of routing that production no longer performs. It measures the
 * parser, NOT what a user gets today — for live routing read
 * `repository/AiChatRepositoryImplTest.kt`. When Layer 1 is re-routed (a revert of the
 * disconnect commit), delete this class and hand `AiChatRepositoryImpl` back to [buildHarnessRig].
 */
private class ParkedLayer1RoutingRepository(
    private val router: LocalIntentRouterImpl,
    private val classifierApi: FakeClassifierApi,
    private val completionApi: FakeCompletionApi,
    private val agentApi: FakeAgentApi,
    private val userDataRepository: UserDataRepository,
) : com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository {

    override suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean): IntentClassification {
        val layer1 = router.route(input, locale)
        if (!skipLayer1 && layer1.confidence >= LAYER_1_CONFIDENCE_THRESHOLD) return layer1

        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) return layer1

        return when (val remote = classifierApi.classify(userId, input, locale)) {
            is RemoteClassificationResult.Success -> IntentClassification(
                intent = remote.intent,
                confidence = remote.confidence,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = remote.toolCall,
            )
            // Mirrors AiChatRepositoryImpl: the refusal travels in the type. Mapping it to
            // Unknown here (as this mirror and prod both did until 2026-07-16) is the bug —
            // the ViewModel renders Unknown as "I didn't catch that" and never offers the
            // paywall. No parked scenario feeds a 402 today; this branch is kept honest so
            // that one written tomorrow measures the fixed behaviour, not the defect.
            RemoteClassificationResult.InsufficientCredits -> IntentClassification(
                intent = ChatIntent.InsufficientCredits,
                confidence = 1f,
                layer = RoutingLayer.Classifier,
                preBuiltToolCall = null,
            )
            // Cloud unreachable → degrade to whatever Layer 1 made of it (the offline behaviour
            // the disconnect gave up in prod, still asserted here for the parser's sake).
            RemoteClassificationResult.NetworkError,
            RemoteClassificationResult.ServiceError -> layer1
        }
    }

    override suspend fun completeFreeForm(
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult =
        completionApi.complete(userDataRepository.getUserData().userId, messages, locale, checklistsSummary)

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
        screenSnapshot: ChatScreenSnapshot?,
        requestId: String?,
        responseLanguage: String?,
    ): AgentStepResult =
        agentApi.step(
            userDataRepository.getUserData().userId,
            transcript,
            locale,
            checklistsSummary,
            contextChecklistName,
            screenSnapshot,
            requestId,
            responseLanguage,
        )

    override suspend fun transcribeAudio(
        audioPath: String,
        mimeType: String,
        locale: ChatLocale,
    ): com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome =
        com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome.ServiceError

    private companion object {
        /** The threshold the disconnected [AiChatRepositoryImpl] used to apply. */
        const val LAYER_1_CONFIDENCE_THRESHOLD = 0.7f
    }
}

/**
 * Builds a [ChatViewModel] wired with the REAL Layer-1 parser ([LocalIntentRouterImpl]) and the
 * parked-Layer-1 ladder ([ParkedLayer1RoutingRepository]), with every cloud layer and the
 * dispatcher faked. [dispatcher] is returned so the caller can inspect recorded ToolCalls.
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
 * Remote Config for the offline rigs: answers every key with the caller's own default unless
 * [longs] overrides it.
 *
 * The override matters for the out-of-credits paywall CTA: with a pure echo-the-default fake, a
 * ViewModel that HARDCODED 300 would look identical to one that reads `ai_daily_limit_premium`
 * (the compiled default is 300). Tests that assert the RC wiring pass a non-default number.
 */
internal class HarnessRemoteConfigProvider(
    private val longs: Map<String, Long> = emptyMap(),
) : RemoteConfigProvider {
    override suspend fun fetchAndActivate(): Boolean = true
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    override fun getString(key: String, defaultValue: String): String = defaultValue
    override fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue
}

/**
 * Renders a ToolCall to a short preview string for the agent plan-card. Real renderer is
 * Android-only; this minimal one is enough for the offline harness (the agent path is faked
 * to ServiceError, so render is rarely hit — but the VM requires a non-null renderer).
 */
private object HarnessPreviewRenderer :
    com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer {
    override suspend fun render(toolCall: ToolCall): String = toolCall.toString()
}

fun buildHarnessRig(scenario: ChatScenario): HarnessRig {
    val dispatcher = RecordingToolCallDispatcher(scenario.dispatchOutcome)
    val classifierApi = FakeClassifierApi()
    val completionApi = FakeCompletionApi()
    val agentApi = FakeAgentApi()
    val prefs = FakeAiChatPreferences(initial = false)
    val userRepo = HarnessUserDataRepository("u1")

    // REAL Layer-1 parser + the parked-Layer-1 ladder, FAKE cloud layers (zero cost).
    // NOT prod routing: prod starts at Layer 2 since 2026-07-15 (see ParkedLayer1RoutingRepository).
    val repository = ParkedLayer1RoutingRepository(
        router = LocalIntentRouterImpl(HarnessNoOpLogger),
        classifierApi = classifierApi,
        completionApi = completionApi,
        agentApi = agentApi,
        userDataRepository = userRepo,
    )

    val viewModel = ChatViewModel(
        aiChatRepository = repository,
        toolCallDispatcher = dispatcher,
        previewRenderer = HarnessPreviewRenderer,
        // Injective + timezone-independent: the scenarios assert routing, not wording, and a real
        // formatter would make an expected string depend on the machine's zone.
        dateFormatter = TokenDateFormatter(),
        localeProvider = FixedLocaleProvider(scenario.locale),
        chatHistoryRepository = FakeChatHistory(),
        agentTranscriptRepository = FakeAgentTranscript(),
        checklistRepository = HarnessChecklistRepository(scenario.lists),
        userDataRepository = userRepo,
        aiChatPreferencesRepository = prefs,
        analytics = HarnessAnalytics(),
        aiModelExperimentTracker = HarnessNoOpModelExperimentTracker,
        remoteConfigProvider = HarnessRemoteConfigProvider(),
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
