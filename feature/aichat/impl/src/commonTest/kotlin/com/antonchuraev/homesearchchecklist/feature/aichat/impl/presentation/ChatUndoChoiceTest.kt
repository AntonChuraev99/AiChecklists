package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ════════════════════════════════════════════════════════════════════════════
// D1 — "ceremony proportional to reversibility" (AI-chat Stage 1, 2026-07-15).
//
// C-BRANCH (this file): AddItem / CompleteItem on a CONFIDENT match dispatch immediately with NO
// question — asking "Add milk?" costs the user a round-trip and buys nothing when one tap undoes
// it. The result lands as an ordinary assistant message and the choice block degenerates into an
// argless chip strip (EMPTY prompt) carrying the post-hoc Undo / move-to-list.
//
// "Confident" is deliberately NOT "the parser was sure": it means the TARGET is unambiguous, i.e.
// NOT (no hint AND 2+ lists). Guessing a list the user never named produces an add they must hunt
// for in a list they never opened — the opposite of reversible.
//
// ─── Why the assertions look the way they do ────────────────────────────────
// ChatViewModel.choiceString() swallows the Compose-Resources failure of a plain unit host and
// returns "…" for EVERY localized string, so no assertion here may touch resolved copy. This file
// asserts STRUCTURE: which ToolCall was dispatched, which ChoiceAction types the chips carry, and
// the EMPTY prompt (a Kotlin literal, not a resource — hence assertable).
//
// The user-visible preview COPY is asserted in ChatPreviewContentTest (androidHostTest), which
// runs under Robolectric where getString actually resolves. It cannot live here: the real
// ToolCallPreviewRendererImpl calls getString for the " (in <list>)" suffix and for attachment
// labels, so a commonTest that renders a list-targeted ToolCall throws rather than asserts.
// ════════════════════════════════════════════════════════════════════════════

// ─── Fakes ───────────────────────────────────────────────────────────────────

/**
 * Feeds the ViewModel a scripted classification, bypassing the Layer-1 parser.
 *
 * D1 is a ViewModel-branching contract: which chips appear for a given (intent, toolCall) pair.
 * Driving it through the real parser would couple these tests to phrasing/lexicon, so a Layer-1
 * tweak would break tests that are not about Layer 1. [ChatScenarioHarness] covers the real parser
 * end-to-end; this covers the branch.
 */
internal class ScriptedAiChatRepository(
    private val classification: IntentClassification,
) : AiChatRepository {
    var classifyCallCount = 0
    var agentStepCallCount = 0

    override suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean): IntentClassification {
        classifyCallCount++
        return classification
    }

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
    ): AgentStepResult {
        agentStepCallCount++
        return AgentStepResult.ServiceError
    }

    override suspend fun completeFreeForm(
        messages: List<ChatMessage>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
    ): RemoteCompletionResult = RemoteCompletionResult.ServiceError

    override suspend fun transcribeAudio(
        audioPath: String,
        mimeType: String,
        locale: ChatLocale,
    ): TranscriptionOutcome = TranscriptionOutcome.ServiceError
}

/**
 * Deterministic stand-in for [ChatDateFormatter]: encodes the epoch it was handed into the output.
 *
 * Two properties matter. (1) Timezone-independence — the real formatter would make the expected
 * string depend on the machine's zone. (2) Injectivity — a renderer that dropped one of
 * MoveAllReminders' two dates, or swapped from/to, cannot produce the expected pair, so an
 * assertion over it still discriminates. The real formatter's RU/EN output is covered by
 * ChatDateFormatterLocaleTest.
 */
internal class TokenDateFormatter : ChatDateFormatter {
    override suspend fun formatDateTime(epochMs: Long): String = "DT($epochMs)"
    override suspend fun formatDay(epochMs: Long): String = "DAY($epochMs)"
}

// ─── Rig (shared with the Robolectric ChatPreviewContentTest) ────────────────

internal class VmRig(
    val viewModel: ChatViewModel,
    val dispatcher: RecordingToolCallDispatcher,
    val effects: MutableList<ChatScreenSideEffect>,
    /** The prefs double behind the VM — D2's memory-of-choice tests assert what it was told. */
    val prefs: FakeAiChatPreferences = FakeAiChatPreferences(),
)

/**
 * Builds a ChatViewModel with a scripted classification, reusing the [ChatScenarioHarness] fakes
 * (checklists / user / history / locale / analytics).
 *
 * [previewRenderer] defaults to the [ChatScenarioHarness] stub because the REAL renderer needs
 * Compose Resources; the Robolectric suite passes the real one in.
 *
 * [checklists] overrides [lists] when given: D2 chip metadata / MRU ordering need item counts and
 * `updatedAt`, which a bare name cannot carry.
 *
 * [repository] overrides the scripted-classification default for the agent path, whose branch is
 * chosen by what `agentStep` returns rather than by a classification.
 *
 * [dateFormatter] defaults to the injective [TokenDateFormatter]: the VM formats D2's time and
 * date-range rows itself, so a real formatter would make those assertions depend on the machine's
 * timezone. RU/EN wording is covered by ChatDateFormatterLocaleTest.
 */
internal fun buildVmRig(
    classification: IntentClassification,
    lists: List<String> = emptyList(),
    checklists: List<Checklist>? = null,
    dispatchOutcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added", listOf("item")),
    undoOutcome: DispatchOutcome = DispatchOutcome.Success("chat_undo_removed", listOf("item")),
    moveOutcome: DispatchOutcome = DispatchOutcome.Success("chat_dispatch_added_to", listOf("item", "list")),
    previewRenderer: ToolCallPreviewRenderer = StubPreviewRenderer,
    locale: ChatLocale = ChatLocale.Ru,
    prefs: FakeAiChatPreferences = FakeAiChatPreferences(initial = false),
    repository: AiChatRepository = ScriptedAiChatRepository(classification),
    dateFormatter: ChatDateFormatter = TokenDateFormatter(),
): VmRig {
    val dispatcher = RecordingToolCallDispatcher(dispatchOutcome, undoOutcome, moveOutcome)
    val userRepo = HarnessUserDataRepository("u1")
    val viewModel = ChatViewModel(
        aiChatRepository = repository,
        toolCallDispatcher = dispatcher,
        previewRenderer = previewRenderer,
        dateFormatter = dateFormatter,
        localeProvider = FixedLocaleProvider(locale),
        chatHistoryRepository = FakeChatHistory(),
        checklistRepository = HarnessChecklistRepository(lists, checklists),
        userDataRepository = userRepo,
        aiChatPreferencesRepository = prefs,
        analytics = HarnessAnalytics(),
        aiModelExperimentTracker = HarnessNoOpModelExperimentTracker,
        remoteConfigProvider = HarnessRemoteConfigProvider(),
        logger = HarnessNoOpLogger,
    )
    return VmRig(viewModel, dispatcher, mutableListOf(), prefs)
}

/** Resource-free renderer for the structural tests (never asserted on — see StubPreviewRenderer usage). */
internal object StubPreviewRenderer : ToolCallPreviewRenderer {
    override suspend fun render(toolCall: ToolCall): String = toolCall::class.simpleName.orEmpty()
}

// ─── State readers (structure, never resolved copy) ──────────────────────────

internal fun ChatScreenState.options(): List<ChoiceOption> = pendingChoice?.choice?.options.orEmpty()

internal fun ChatScreenState.actions(): List<ChoiceAction> = options().map { it.action }

// singleBatchText() lived here until D2 (2026-07-15): a single-action question carried its object in
// ONE flat batchItems line, and that helper read it. D2 replaced the line with typed
// ChatChoice.objectRows, so the readers moved to ChatObjectRowsTest / ChatPreviewContentTest.
// PendingChoice.batchItems now serves ONLY the agent batch (a numbered plan of several actions).

internal fun ChatScreenState.optionIdFor(predicate: (ChoiceAction) -> Boolean): String {
    val option = options().firstOrNull { predicate(it.action) }
    assertNotNull(option, "no chip with the expected action; chips = ${actions()}")
    return option.id
}

/** Candidate checklist hints of a "which list?" choice (Execute chips with a swapped hint). */
internal fun ChatScreenState.candidateHints(): List<String?> =
    actions().filterIsInstance<ChoiceAction.Execute>().map { it.toolCall.hintOrNull() }

/**
 * Subscribes to the replay-0 side-effect flow BEFORE a send, so nothing is missed.
 *
 * [TestScope.backgroundScope], not a plain `launch` on the test scope: an endless `collect` on the
 * test scope never completes, so runTest waits its full 60s timeout and reports
 * UncompletedCoroutinesError instead of the real assertion failure.
 */
internal fun TestScope.collectEffects(rig: VmRig, dispatcher: CoroutineDispatcher) {
    backgroundScope.launch(dispatcher) { rig.viewModel.sideEffect.collect { rig.effects.add(it) } }
}

internal fun VmRig.send(text: String) {
    viewModel.sendIntent(ChatScreenIntent.OnInputChange(text))
    viewModel.sendIntent(ChatScreenIntent.OnSendClick)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUndoChoiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Adding an item is cheap and reversible: apply immediately, report normally, and offer Undo /
     * move-to-list after the fact via an argless (EMPTY-prompt) chip strip.
     */
    @Test
    fun handleSend_addItem_confidentMatch_dispatchesWithoutQuestion() = runTest {
        val toolCall = ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко")
        val handle = UndoHandle.AddedItem(
            checklistId = 1L,
            checklistName = "Покупки",
            fillId = 11L,
            fillItemId = "fill_new",
            templateItemId = "tpl_new",
            itemText = "Молоко",
        )
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = toolCall,
            ),
            lists = listOf("Покупки", "Работа"),
            dispatchOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("Молоко", "Покупки"),
                linkedChecklistId = 1L,
                undo = handle,
            ),
        )
        collectEffects(rig, testDispatcher)

        rig.send("добавь молоко в покупки")

        // 1. It happened — with no confirmation round-trip.
        assertEquals(listOf<ToolCall>(toolCall), rig.dispatcher.dispatched, "a confident add must dispatch immediately")

        // 2. The result is reported as an ordinary assistant message.
        val message = rig.effects.filterIsInstance<ChatScreenSideEffect.ShowAssistantMessage>().firstOrNull()
        assertNotNull(message, "the add result must be reported; effects = ${rig.effects}")
        assertEquals("chat_dispatch_added_to", message.messageKey)

        // 3. What remains is an argless chip strip, NOT a question.
        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "the post-hoc Undo strip must be shown")
        assertEquals("", state.pendingChoice?.choice?.prompt, "the C-branch asks nothing — the prompt must be empty")

        // 4. Exactly the two reversal affordances, both bound to the handle the dispatcher returned.
        assertEquals(
            listOf<ChoiceAction>(ChoiceAction.Undo(handle), ChoiceAction.MoveToList(handle)),
            state.actions(),
            "an added item offers Undo + move-to-list, in that order",
        )
    }

    /**
     * Completing is reversible too, but there is nowhere to "move" a checkmark — Undo is the only
     * affordance. Guards against reusing the AddItem chip set wholesale.
     */
    @Test
    fun handleSend_completeItem_confidentMatch_dispatchesWithoutQuestion() = runTest {
        val toolCall = ToolCall.CompleteItem(checklistHint = "Покупки", itemText = "Молоко")
        val handle = UndoHandle.CompletedItem(
            checklistId = 1L,
            fillId = 11L,
            fillItemId = "fill_1",
            itemText = "Молоко",
        )
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.CompleteItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = toolCall,
            ),
            lists = listOf("Покупки", "Работа"),
            dispatchOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_completed",
                args = listOf("Молоко", "Покупки"),
                linkedChecklistId = 1L,
                undo = handle,
            ),
        )
        collectEffects(rig, testDispatcher)

        rig.send("отметь молоко")

        assertEquals(
            listOf<ToolCall>(toolCall),
            rig.dispatcher.dispatched,
            "a confident complete must dispatch immediately",
        )
        val state = rig.viewModel.screenState.value
        assertEquals("", state.pendingChoice?.choice?.prompt, "the C-branch asks nothing — the prompt must be empty")
        assertEquals(
            listOf<ChoiceAction>(ChoiceAction.Undo(handle)),
            state.actions(),
            "a completed item offers Undo only — there is nothing to move",
        )
    }

    /**
     * The C-branch must not swallow ambiguity. With no hint and 2+ lists the target is genuinely
     * unknown, so this is NOT a confident match: ask which list instead of guessing.
     */
    @Test
    fun handleSend_addItem_hintless_withTwoLists_asksWhichList() = runTest {
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.AddItem(checklistHint = null, itemText = "Молоко"),
            ),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("добавь молоко")

        assertEquals(emptyList(), rig.dispatcher.dispatched, "an unresolved target must not be dispatched on a guess")
        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "a which-list question must be shown")
        assertEquals(
            listOf("Покупки", "Работа"),
            state.candidateHints(),
            "one candidate chip per real list, each re-running the add against that list",
        )
    }

    /**
     * Post-dispatch ambiguity (a hint matching several lists) comes back from the dispatcher as
     * [DispatchOutcome.AmbiguousMatch] and must land on the same which-list picker — still carrying
     * the object so the user knows what they are placing.
     */
    @Test
    fun handleSend_addItem_ambiguousHint_showsWhichListWithObject() = runTest {
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.AddItem(checklistHint = "Покуп", itemText = "Молоко"),
            ),
            lists = listOf("Покупки дом", "Покупки офис"),
            dispatchOutcome = DispatchOutcome.AmbiguousMatch(listOf("Покупки дом", "Покупки офис")),
        )

        rig.send("добавь молоко в покуп")

        val state = rig.viewModel.screenState.value
        assertEquals(
            listOf("Покупки дом", "Покупки офис"),
            state.candidateHints(),
            "an ambiguous hint must offer the matching lists as chips",
        )
        assertEquals(
            "Молоко",
            state.pendingChoice?.choice?.objectRows?.firstOrNull { it.kind == RowKind.Item }?.value,
            "the object must stay visible while the user picks a list — the whole point of the picker is " +
                "choosing a home for a THING (rows = ${state.pendingChoice?.choice?.objectRows})",
        )
    }

    /**
     * After a move there is no "undo" that means anything to the user (the item is exactly where
     * they just asked for it) — but moving on again must stay possible. Guards against replaying
     * the add chip set on the move result.
     */
    @Test
    fun moveToList_afterMove_showsMoveChipWithoutUndo() = runTest {
        val handle = UndoHandle.AddedItem(
            checklistId = 1L,
            checklistName = "Покупки",
            fillId = 11L,
            fillItemId = "fill_new",
            templateItemId = "tpl_new",
            itemText = "Молоко",
        )
        val movedHandle = handle.copy(checklistId = 2L, checklistName = "Работа", fillItemId = "fill_moved")
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
            ),
            lists = listOf("Покупки", "Работа"),
            dispatchOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("Молоко", "Покупки"),
                linkedChecklistId = 1L,
                undo = handle,
            ),
            moveOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("Молоко", "Работа"),
                linkedChecklistId = 2L,
                undo = movedHandle,
            ),
        )
        collectEffects(rig, testDispatcher)
        rig.send("добавь молоко в покупки")

        // Open the move picker, then pick the only other list.
        val moveListChipId = rig.viewModel.screenState.value.optionIdFor { it is ChoiceAction.MoveToList }
        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceSelected(moveListChipId))

        val pickerState = rig.viewModel.screenState.value
        assertEquals(
            listOf("Работа"),
            pickerState.actions().filterIsInstance<ChoiceAction.MoveTo>().map { it.targetName },
            "the move picker offers every OTHER list (never the one the item is already in)",
        )

        val targetChipId = pickerState.optionIdFor { it is ChoiceAction.MoveTo }
        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceSelected(targetChipId))

        assertEquals(
            listOf(handle to "Работа"),
            rig.dispatcher.moved,
            "picking a target must move the ORIGINAL added row, by handle",
        )
        assertEquals(
            listOf<ChoiceAction>(ChoiceAction.MoveToList(movedHandle)),
            rig.viewModel.screenState.value.actions(),
            "after a move: still movable, but no Undo — and bound to the FRESH handle",
        )
    }

    /**
     * Undo tapped → the dispatcher rolls back BY HANDLE and the strip goes away: a chip that stays
     * put after being tapped reads as "nothing happened" and invites a second, destructive tap.
     */
    @Test
    fun undoChip_tapped_rollsBackByHandleAndClearsStrip() = runTest {
        val handle = UndoHandle.AddedItem(
            checklistId = 1L,
            checklistName = "Покупки",
            fillId = 11L,
            fillItemId = "fill_new",
            templateItemId = "tpl_new",
            itemText = "Молоко",
        )
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.CreateItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.AddItem(checklistHint = "Покупки", itemText = "Молоко"),
            ),
            lists = listOf("Покупки", "Работа"),
            dispatchOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("Молоко", "Покупки"),
                linkedChecklistId = 1L,
                undo = handle,
            ),
            undoOutcome = DispatchOutcome.Success(messageKey = "chat_undo_removed", args = listOf("Молоко")),
        )
        collectEffects(rig, testDispatcher)
        rig.send("добавь молоко в покупки")

        val undoChipId = rig.viewModel.screenState.value.optionIdFor { it is ChoiceAction.Undo }
        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceSelected(undoChipId))

        assertEquals(listOf<UndoHandle>(handle), rig.dispatcher.undone, "Undo must roll back the exact handle")
        assertTrue(
            rig.effects.filterIsInstance<ChatScreenSideEffect.ShowAssistantMessage>()
                .any { it.messageKey == "chat_undo_removed" },
            "the rollback must be confirmed visibly; effects = ${rig.effects}",
        )
        assertEquals(
            null,
            rig.viewModel.screenState.value.pendingChoice,
            "the strip must disappear once used — a lingering Undo chip invites a second tap",
        )
    }
}
