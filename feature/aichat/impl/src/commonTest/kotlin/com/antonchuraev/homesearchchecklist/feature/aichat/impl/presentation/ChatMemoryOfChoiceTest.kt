package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.UndoHandle
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ════════════════════════════════════════════════════════════════════════════
// D2 — memory of choice (AI-chat Stage 1, 2026-07-15).
//
// "Remember my choice" on the which-list picker turns one answer into a routing default. That is
// convenience the moment the user opts in and a trap the moment they do not notice it, so every
// test here guards a consent property rather than a mechanism:
//
//   • it is never pre-checked;
//   • nothing persists until a list is actually picked (there is no "that list" before the tap);
//   • the reply after the tap DISCLOSES the new default;
//   • it is offered only where the answer generalises — an add, never a delete/reminder/attach,
//     and never on the D1 post-action move (a correction of a mistake is not a preference).
//
// Assertions read what the ViewModel PERSISTED and which message KEY it emitted — both are Kotlin
// values, unlike resolved copy, which is "…" on a plain unit host (see ChatUndoChoiceTest:50).
// ════════════════════════════════════════════════════════════════════════════

private fun seedList(id: Long, name: String, itemCount: Int = 0) = Checklist(
    id = id,
    name = name,
    items = List(itemCount) { ChecklistItem(text = "item $it") },
)

private fun addClassification(
    itemText: String = "молоко",
    checklistHint: String? = null,
) = IntentClassification(
    intent = ChatIntent.CreateItem,
    confidence = 1f,
    layer = RoutingLayer.Local,
    preBuiltToolCall = ToolCall.AddItem(checklistHint = checklistHint, itemText = itemText),
)

private val twoLists = listOf(
    seedList(id = 1L, name = "Покупки", itemCount = 12),
    seedList(id = 2L, name = "Работа", itemCount = 3),
)

/** Ids of the candidate chips, in shown order. */
private fun ChatScreenState.candidateChipIds(): List<String> =
    pendingChoice?.choice?.options.orEmpty().filter { it.action is ChoiceAction.Execute }.map { it.id }

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMemoryOfChoiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 11. Opt in, pick a list → the choice is remembered and then honoured ──

    /**
     * The full loop: the toggle alone must persist nothing (there is no chosen list yet), the chip
     * tap commits the pick, and the NEXT hintless add — the one that used to ask — routes straight
     * into the remembered list.
     */
    @Test
    fun memoryToggle_thenCandidateTap_persistsDefaultAndNextAddSkipsTheQuestion() = runTest {
        val prefs = FakeAiChatPreferences()
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
            prefs = prefs,
            dispatchOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("молоко", "Покупки"),
                linkedChecklistId = 1L,
                undo = UndoHandle.AddedItem(
                    checklistId = 1L,
                    checklistName = "Покупки",
                    fillId = 11L,
                    fillItemId = "fill_new",
                    templateItemId = "tpl_new",
                    itemText = "молоко",
                ),
            ),
        )

        rig.send("добавь молоко")
        val picker = rig.viewModel.screenState.value
        assertNotNull(picker.pendingChoice, "a hintless add with 2 lists must ask")

        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceMemoryToggle(enabled = true))
        assertEquals(
            emptyList(),
            prefs.defaultChecklistIdWrites,
            "the toggle only bookkeeps: 'add to THAT list from now on' has no meaning before a list is picked",
        )

        // Tap the first candidate — "Покупки" (id 1), the MRU-first chip.
        val firstChip = rig.viewModel.screenState.value.candidateChipIds().first()
        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceSelected(firstChip))

        assertEquals(
            listOf<Long?>(1L),
            prefs.defaultChecklistIdWrites,
            "tapping a candidate with the toggle on must persist THAT list as the default, exactly once",
        )

        // The next hintless add must not ask again — it goes where the user said.
        // (The scripted classifier replays the same AddItem for any input, so the item text repeats;
        // what this asserts is the ROUTING of a second hintless add, not the parse.)
        rig.send("добавь хлеб")

        val adds = rig.dispatcher.dispatched.filterIsInstance<ToolCall.AddItem>()
        assertEquals(
            2,
            adds.size,
            "the second hintless add must dispatch instead of asking again — a question would leave it " +
                "undispatched; dispatched = ${rig.dispatcher.dispatched}",
        )
        assertEquals(
            "Покупки",
            adds.last().checklistHint,
            "a remembered default must be applied as the hint — otherwise the memory bought the user nothing",
        )
    }

    // ── 12. Saving a default must be disclosed ────────────────────────────────

    /**
     * A routing preference the user is not told about is a dark pattern: from now on their items
     * land somewhere they never see chosen. The reply after the tap must say so.
     */
    @Test
    fun memoryToggle_afterCandidateTap_replyDisclosesTheRememberedList() = runTest {
        val prefs = FakeAiChatPreferences()
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
            prefs = prefs,
        )
        collectEffects(rig, testDispatcher)

        rig.send("добавь молоко")
        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceMemoryToggle(enabled = true))
        rig.viewModel.sendIntent(
            ChatScreenIntent.OnChoiceSelected(rig.viewModel.screenState.value.candidateChipIds().first()),
        )

        val keys = rig.effects.filterIsInstance<ChatScreenSideEffect.ShowAssistantMessage>().map { it.messageKey }
        assertTrue(
            "chat_result_remembered_list" in keys,
            "saving a default must be disclosed in the reply — silently sticky routing is a dark pattern; " +
                "message keys = $keys",
        )
    }

    /** Without the opt-in nothing is remembered — and nothing claims to be. */
    @Test
    fun candidateTap_withoutMemoryToggle_persistsNothingAndSaysNothing() = runTest {
        val prefs = FakeAiChatPreferences()
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
            prefs = prefs,
        )
        collectEffects(rig, testDispatcher)

        rig.send("добавь молоко")
        rig.viewModel.sendIntent(
            ChatScreenIntent.OnChoiceSelected(rig.viewModel.screenState.value.candidateChipIds().first()),
        )

        assertEquals(
            emptyList(),
            prefs.defaultChecklistIdWrites,
            "picking a list once is an answer, not a preference — nothing may be persisted without the opt-in",
        )
        val keys = rig.effects.filterIsInstance<ChatScreenSideEffect.ShowAssistantMessage>().map { it.messageKey }
        assertFalse(
            "chat_result_remembered_list" in keys,
            "nothing was remembered, so nothing may announce it; message keys = $keys",
        )
    }

    // ── 13. Where the toggle may appear, and its initial state ────────────────

    /** The one case where the answer generalises into a preference. Never pre-checked. */
    @Test
    fun memoryToggle_offeredOnAddWhichList_andNeverPreChecked() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
        )

        rig.send("добавь молоко")

        val pending = assertNotNull(rig.viewModel.screenState.value.pendingChoice, "a hintless add must ask")
        assertTrue(
            pending.showMemoryToggle,
            "'always add here' is exactly what the which-list picker for an add can learn",
        )
        assertFalse(
            pending.rememberChoice,
            "a sticky preference the user did not knowingly opt into is a dark pattern — never pre-checked",
        )
    }

    /** Delete asks which list too, but "always delete from here" is not a preference to learn. */
    @Test
    fun memoryToggle_notOfferedOnDeleteWhichList() = runTest {
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.DeleteItem,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "молоко"),
            ),
            checklists = twoLists,
        )

        rig.send("удали молоко")

        val pending = assertNotNull(rig.viewModel.screenState.value.pendingChoice, "delete must ask")
        assertFalse(
            pending.showMemoryToggle,
            "a destructive one-off must never become a standing default",
        )
    }

    @Test
    fun memoryToggle_notOfferedOnReminderQuestion() = runTest {
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.SetReminder,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.SetItemReminder(
                    checklistHint = "Покупки",
                    itemText = "купить масло",
                    at = 1_800_000_000_000L,
                ),
            ),
            checklists = twoLists,
        )

        rig.send("напомни купить масло завтра")

        val pending = assertNotNull(rig.viewModel.screenState.value.pendingChoice, "a reminder must ask")
        assertFalse(pending.showMemoryToggle, "a reminder is a one-off, not a routing rule")
    }

    @Test
    fun memoryToggle_notOfferedOnAttachQuestion() = runTest {
        val attachment = ChatAttachment(
            sourcePath = "/tmp/scan.pdf",
            mimeType = "application/pdf",
            fileName = "scan.pdf",
        )
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.AttachToItem(itemText = "молоко", checklistHint = "Покупки"),
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.AttachToItem(
                    checklistHint = "Покупки",
                    itemText = "молоко",
                    attachments = listOf(attachment),
                ),
            ),
            checklists = twoLists,
        )
        rig.viewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(attachment))

        rig.send("прикрепи это к молоку")

        val pending = assertNotNull(rig.viewModel.screenState.value.pendingChoice, "attaching must ask")
        assertFalse(pending.showMemoryToggle, "attaching a file says nothing about where future items belong")
    }

    /**
     * The D1 post-action move picker looks like a which-list picker but means the opposite: the user
     * is correcting a landing they did not want. Learning "always put items there" from a correction
     * would cement the mistake.
     */
    @Test
    fun memoryToggle_notOfferedOnPostActionMovePicker() = runTest {
        val handle = UndoHandle.AddedItem(
            checklistId = 1L,
            checklistName = "Покупки",
            fillId = 11L,
            fillItemId = "fill_new",
            templateItemId = "tpl_new",
            itemText = "молоко",
        )
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = "Покупки"),
            checklists = twoLists,
            dispatchOutcome = DispatchOutcome.Success(
                messageKey = "chat_dispatch_added_to",
                args = listOf("молоко", "Покупки"),
                linkedChecklistId = 1L,
                undo = handle,
            ),
        )

        // A confident add applies at once and offers Undo / move-to-list (D1 C-branch).
        rig.send("добавь молоко в покупки")
        val moveChipId = rig.viewModel.screenState.value.optionIdFor { it is ChoiceAction.MoveToList }
        rig.viewModel.sendIntent(ChatScreenIntent.OnChoiceSelected(moveChipId))

        val pending = assertNotNull(rig.viewModel.screenState.value.pendingChoice, "the move picker must be shown")
        assertFalse(
            pending.showMemoryToggle,
            "moving an item is a correction of a mistake — a preference learned from it cements the mistake",
        )
    }

    // ── Reset — the escape hatch that keeps the preference honest ─────────────

    @Test
    fun onResetDefaultChecklist_clearsThePersistedDefault() = runTest {
        val prefs = FakeAiChatPreferences(initialDefaultChecklistId = 1L)
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
            prefs = prefs,
        )

        rig.viewModel.sendIntent(ChatScreenIntent.OnResetDefaultChecklist)

        assertEquals(
            listOf<Long?>(null),
            prefs.defaultChecklistIdWrites,
            "'Ask me every time' must clear the stored id — without a working reset the preference is a one-way trap",
        )

        // And the chat must go back to asking.
        rig.send("добавь молоко")
        assertNotNull(
            rig.viewModel.screenState.value.pendingChoice,
            "after a reset a hintless add must ask again",
        )
    }

    /**
     * A remembered list the user cannot see is one they cannot revoke, so the id is resolved to a
     * NAME for the settings row — and a deleted list resolves to null, which also stops the routing.
     */
    @Test
    fun defaultChecklistName_persistedIdResolvedAgainstLiveLists() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
            prefs = FakeAiChatPreferences(initialDefaultChecklistId = 1L),
        )

        assertEquals(
            "Покупки",
            rig.viewModel.screenState.value.defaultChecklistName,
            "the settings row must name the remembered list — a default the user cannot see cannot be revoked",
        )
    }

    @Test
    fun defaultChecklistName_persistedIdOfDeletedList_resolvesToNullAndChatAsksAgain() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко"),
            checklists = twoLists,
            prefs = FakeAiChatPreferences(initialDefaultChecklistId = 404L),
        )

        assertEquals(
            null,
            rig.viewModel.screenState.value.defaultChecklistName,
            "a default pointing at a deleted list must resolve to null, not to a stale name",
        )

        rig.send("добавь молоко")
        assertNotNull(
            rig.viewModel.screenState.value.pendingChoice,
            "with the remembered list gone the chat must resume asking, not route into nothing",
        )
    }
}
