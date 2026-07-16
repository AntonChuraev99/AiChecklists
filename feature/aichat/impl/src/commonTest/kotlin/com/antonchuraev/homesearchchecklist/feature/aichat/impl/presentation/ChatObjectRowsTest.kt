package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.RemoteCompletionResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ════════════════════════════════════════════════════════════════════════════
// D2 — the typed OBJECT of a question (AI-chat Stage 1, 2026-07-15).
//
// D1 gave a question ONE flat preview line, so every question had to pick between naming the item
// and naming the list — which is how "Add to Shopping?" shipped never saying WHAT. D2 replaces that
// line with typed rows: the item, where it lands, and when it fires are separate, individually
// addressable entities.
//
// ─── Why no assertion here touches localized copy ───────────────────────────
// ChatViewModel.choiceString() swallows the Compose-Resources failure of a plain unit host and
// returns "…" for EVERY localized string (see ChatUndoChoiceTest:50). So these tests assert what
// the ViewModel ROUTES, never what it renders:
//   • kind / emphasis / role — types, resolved at compile time, always assertable;
//   • values that are pass-throughs of test-controlled data (item text, file name, list name);
//   • absence — an entity with no row.
// Consequently a row's `value` is asserted ONLY where it carries data the test itself supplied.
// The resolved copy (a11y phrasing, the "Create 8 items" counter) lives in ChatPreviewContentTest
// under Robolectric, where getString actually resolves.
// ════════════════════════════════════════════════════════════════════════════

// ─── Classifications ─────────────────────────────────────────────────────────

private fun addClassification(
    itemText: String = "молоко",
    checklistHint: String? = null,
    layer: RoutingLayer = RoutingLayer.Local,
) = IntentClassification(
    intent = ChatIntent.CreateItem,
    confidence = 1f,
    layer = layer,
    preBuiltToolCall = ToolCall.AddItem(checklistHint = checklistHint, itemText = itemText),
)

private fun classificationFor(intent: ChatIntent, toolCall: ToolCall) = IntentClassification(
    intent = intent,
    confidence = 1f,
    layer = RoutingLayer.Local,
    preBuiltToolCall = toolCall,
)

// ─── Seeds ───────────────────────────────────────────────────────────────────

/** A checklist with [itemCount] items — the count the chip meta is supposed to show. */
private fun seedList(
    id: Long,
    name: String,
    itemCount: Int = 0,
    updatedAt: Long = 0L,
) = Checklist(
    id = id,
    name = name,
    items = List(itemCount) { ChecklistItem(text = "item $it") },
    updatedAt = updatedAt,
)

// ─── Row readers ─────────────────────────────────────────────────────────────

private fun ChatScreenState.objectRows(): List<ChoiceObjectRow> =
    pendingChoice?.choice?.objectRows.orEmpty()

/** Rows summarised for a failure message — the whole point is to see what WAS produced. */
private fun ChatScreenState.rowSummary(): String =
    objectRows().joinToString { "${it.kind}/${it.emphasis}='${it.value}'" }

/** The single row of [kind]; fails loudly when absent or duplicated. */
private fun ChatScreenState.row(kind: RowKind): ChoiceObjectRow {
    val matches = objectRows().filter { it.kind == kind }
    assertEquals(1, matches.size, "expected exactly one $kind row, rows = [${rowSummary()}]")
    return matches.first()
}

private fun ChatScreenState.rowOrNull(kind: RowKind): ChoiceObjectRow? =
    objectRows().firstOrNull { it.kind == kind }

/**
 * The candidate chips of a "which list?" picker, in shown order — every option carrying an
 * [ChoiceAction.Execute] (the escape chip dismisses and is excluded by construction).
 */
private fun ChatScreenState.candidateOptions(): List<ChoiceOption> =
    pendingChoice?.choice?.options.orEmpty().filter { it.action is ChoiceAction.Execute }

// ─── Agent-batch rig ─────────────────────────────────────────────────────────

/**
 * Drives the agent path: the batch branch is chosen by what `agentStep` RETURNS, not by a
 * classification, so the scripted-classification repository cannot reach it.
 */
private class ScriptedAgentRepository(
    private val steps: List<AgentStepResult>,
) : AiChatRepository {
    private var index = 0

    override suspend fun classify(input: String, locale: ChatLocale, skipLayer1: Boolean) =
        IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = 1f,
            layer = RoutingLayer.FullChat,
            preBuiltToolCall = null,
        )

    override suspend fun agentStep(
        transcript: List<AgentTranscriptEntry>,
        locale: ChatLocale,
        checklistsSummary: List<ChecklistContext>,
        contextChecklistName: String?,
        requestId: String?,
    ): AgentStepResult = steps.getOrElse(index++) { AgentStepResult.ServiceError }

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

private fun addItemCall(id: String, checklist: String, item: String) = AgentToolCall(
    id = id,
    name = "add_item",
    args = buildJsonObject {
        put("checklist", checklist)
        put("item", item)
    },
)

private fun buildAgentBatchRig(toolCalls: List<AgentToolCall>): VmRig = buildVmRig(
    classification = addClassification(),
    lists = listOf("Покупки", "Работа"),
    repository = ScriptedAgentRepository(
        steps = listOf(AgentStepResult.ToolCalls(calls = toolCalls, creditsRemaining = 99)),
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatObjectRowsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1. AddItem, ambiguous target → the item must survive the which-list question ──

    /**
     * THE defect D2 exists to close: "which list?" used to be asked about an unnamed thing. The
     * object rides the question, so the user is picking a destination for something they can see.
     */
    @Test
    fun whichListChoice_ambiguousAdd_objectRowCarriesItemAsPrimary() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = null),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("добавь молоко")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "2+ lists and no hint must ask which list")
        val item = state.row(RowKind.Item)
        assertEquals(
            "молоко",
            item.value,
            "the which-list question must name WHAT is going into the list — rows = [${state.rowSummary()}]",
        )
        assertEquals(RowEmphasis.Primary, item.emphasis, "the item IS the object — it cannot be a supporting detail")
        assertTrue(state.pendingChoice!!.hasObjectRows, "hasObjectRows drives the dock's top anchor")
    }

    // ── 2. SetItemReminder → item + destination + ACCENT time ──────────────────

    /**
     * A reminder has three entities and the time is the one that surprises: a silent 3 a.m. alarm
     * is exactly what this block exists to prevent, so time is [RowEmphasis.Accent], never Detail.
     */
    @Test
    fun writeChoice_setItemReminder_objectRowsCarryItemDestinationAndAccentTime() = runTest {
        val at = 1_800_000_000_000L
        val rig = buildVmRig(
            classification = classificationFor(
                intent = ChatIntent.SetReminder,
                toolCall = ToolCall.SetItemReminder(
                    checklistHint = "Покупки",
                    itemText = "купить масло",
                    at = at,
                ),
            ),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("напомни купить масло завтра в 9")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "a reminder still asks first — it is not one-tap reversible")

        val item = state.row(RowKind.Item)
        assertEquals("купить масло", item.value)
        assertEquals(RowEmphasis.Primary, item.emphasis)

        val destination = state.row(RowKind.Destination)
        assertEquals("Покупки", destination.value, "the reminder's list must be named — rows = [${state.rowSummary()}]")
        assertEquals(RowEmphasis.Detail, destination.emphasis)

        val time = state.row(RowKind.Time)
        assertEquals(
            RowEmphasis.Accent,
            time.emphasis,
            "time sits at full strength on purpose: a reminder the user misreads fires while they sleep",
        )
        // The token formatter encodes the epoch it was handed, so this pins the row to THE moment
        // the reminder will fire — "not blank" would also pass on a row showing some other time.
        assertEquals(
            "DT($at)",
            time.value,
            "the time row must show the instant the reminder fires — rows = [${state.rowSummary()}]",
        )
    }

    // ── 3. DeleteItem → Danger row, and NO recommended action ─────────────────

    /**
     * Deleting is the one action whose object is the thing about to be destroyed, so its row is
     * [RowEmphasis.Danger] — and nothing in the block may be styled as the recommended path: a
     * Primary chip on a delete question nudges the user toward the irreversible option.
     */
    @Test
    fun writeChoice_deleteItem_objectRowIsDangerAndNoPrimaryChipOffered() = runTest {
        val rig = buildVmRig(
            classification = classificationFor(
                intent = ChatIntent.DeleteItem,
                toolCall = ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "молоко"),
            ),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("удали молоко из покупок")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "delete must ask first")

        val item = state.row(RowKind.Item)
        assertEquals("молоко", item.value)
        assertEquals(
            RowEmphasis.Danger,
            item.emphasis,
            "the target of an irreversible action must be marked as such — rows = [${state.rowSummary()}]",
        )

        val roles = state.options().map { it.role }
        assertFalse(
            ChoiceRole.Primary in roles,
            "no chip may be the recommended action on a delete — roles = $roles",
        )
    }

    // ── 4. CreateChecklist + AI fill → name + item preview ────────────────────

    /**
     * Creating a filled list proposes content the user never typed, so the block must show the name
     * AND what will be in it. (The chip's "Create 8 items" counter is resolved copy — asserted in
     * the Robolectric suite, not here.)
     */
    @Test
    fun writeChoice_createChecklistWithItems_objectRowsCarryNameAndItemPreview() = runTest {
        val items = listOf("паспорт", "билеты", "зарядка")
        val rig = buildVmRig(
            classification = classificationFor(
                intent = ChatIntent.CreateChecklist(name = "Поездка"),
                toolCall = ToolCall.CreateChecklist(name = "Поездка", initialItems = items),
            ),
            lists = listOf("Покупки"),
        )

        rig.send("создай список поездка")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "creating a list still asks first")

        val name = state.row(RowKind.Name)
        assertEquals("Поездка", name.value, "the new list's name must be shown — rows = [${state.rowSummary()}]")
        assertEquals(RowEmphasis.Primary, name.emphasis)

        val previews = state.objectRows().filter { it.kind == RowKind.Preview }
        assertEquals(
            items,
            previews.map { it.value },
            "every proposed item must be previewed, in order — the user is approving content they never typed",
        )
        assertTrue(
            previews.all { it.emphasis == RowEmphasis.Detail },
            "previews support the name, they are not the object — got ${previews.map { it.emphasis }}",
        )
    }

    // ── 5. MoveAllReminders → date range (the count is a data gap, see below) ─

    /**
     * A mass move must state its span in BOTH directions — "moving reminders" without from/to is
     * the least reversible question in the set.
     *
     * The design also calls for a "5 reminders" [RowKind.Count] row, but [ToolCall.MoveAllReminders]
     * carries only timestamps: the count exists nowhere on this path, so the row is omitted rather
     * than guessed. The absence is asserted deliberately — it is the tripwire for the data-layer
     * change that adds the count, and it fails the day someone fabricates one.
     */
    @Test
    fun writeChoice_moveAllReminders_objectRowCarriesDateRange_countOmittedUntilDataLayerHasIt() = runTest {
        val from = 1_800_000_000_000L
        val to = 1_800_172_800_000L
        val rig = buildVmRig(
            classification = classificationFor(
                intent = ChatIntent.MoveReminders,
                toolCall = ToolCall.MoveAllReminders(
                    fromDayStartMs = from,
                    fromDayEndMs = 1_800_086_399_000L,
                    toDayStartMs = to,
                ),
            ),
            lists = listOf("Покупки"),
        )

        rig.send("перенеси все напоминания с понедельника на вторник")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "a mass reminder move still asks first")

        val range = state.row(RowKind.DateRange)
        assertEquals(
            RowEmphasis.Primary,
            range.emphasis,
            "the span IS the object of a mass move — rows = [${state.rowSummary()}]",
        )
        // Both endpoints, in order: the token formatter is injective, so a row that dropped one date
        // or swapped from/to cannot produce this string.
        assertTrue(
            range.value.contains("DAY($from)") && range.value.contains("DAY($to)"),
            "the range must name both endpoints; got '${range.value}'",
        )
        assertTrue(
            range.value.indexOf("DAY($from)") < range.value.indexOf("DAY($to)"),
            "source date must precede target date — a swapped range moves reminders the wrong way; " +
                "got '${range.value}'",
        )

        assertNull(
            state.rowOrNull(RowKind.Count),
            "MoveAllReminders carries no count, so no count may be shown — an invented number here is " +
                "worse than a missing one. Add this row (and change this test) when the data layer carries it.",
        )
    }

    // ── 6. AttachToItem → file row, name only while the pickers carry no size ──

    /**
     * Every ChatRoute picker builds its ChatAttachment with `sizeBytes = 0L`, so a size suffix would
     * render "• 0 B" on every attach — a confident statement of a fact the app does not have. The
     * row is the bare file name until the pickers carry a real size.
     *
     * `sizeBytes = 0` is asserted explicitly because that is the ONLY value production produces
     * today: a test that passed a plausible 3.5 MB would be testing a path no user can reach.
     */
    @Test
    fun writeChoice_attachToItem_fileRowIsBareNameWhilePickersReportNoSize() = runTest {
        val attachment = ChatAttachment(
            sourcePath = "/tmp/scan.pdf",
            mimeType = "application/pdf",
            fileName = "scan.pdf",
            sizeBytes = 0L, // what all four picker call sites hand over today
        )
        val rig = buildVmRig(
            classification = classificationFor(
                intent = ChatIntent.AttachToItem(itemText = "молоко", checklistHint = "Покупки"),
                toolCall = ToolCall.AttachToItem(
                    checklistHint = "Покупки",
                    itemText = "молоко",
                    attachments = listOf(attachment),
                ),
            ),
            lists = listOf("Покупки"),
        )
        rig.viewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(attachment))

        rig.send("прикрепи это к молоку")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "attaching still asks first")

        val file = state.row(RowKind.File)
        assertEquals(RowEmphasis.Primary, file.emphasis, "the file IS the object being attached")
        assertEquals(
            "scan.pdf",
            file.value,
            "with no size from the picker the row must be the bare name — never an invented '0 B'",
        )

        // The file is the object; the item it lands on is context, not the headline.
        val item = state.row(RowKind.Item)
        assertEquals("молоко", item.value)
        assertEquals(RowEmphasis.Detail, item.emphasis, "on an attach the FILE is the object, not the item")
        assertEquals("Покупки", state.row(RowKind.Destination).value)
    }

    /** A command that named no list gets NO destination row — absence, not an empty one. */
    @Test
    fun writeChoice_noChecklistHint_omitsDestinationRowEntirely() = runTest {
        val rig = buildVmRig(
            classification = classificationFor(
                intent = ChatIntent.DeleteItem,
                toolCall = ToolCall.DeleteItem(checklistHint = null, itemText = "молоко"),
            ),
            lists = listOf("Покупки"),
        )

        rig.send("удали молоко")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice)
        assertNull(
            state.rowOrNull(RowKind.Destination),
            "a missing entity must be an absent row, not a blank one — a contract carried in an empty " +
                "string is one nobody can check (the D1 lesson); rows = [${state.rowSummary()}]",
        )
    }

    // ── 7. Agent batch → untouched by D2 ──────────────────────────────────────

    /**
     * The rows model ONE typed object; a batch is a numbered plan of several. D2 must not leak into
     * it — the batch keeps rendering through [PendingChoice.batchItems].
     */
    @Test
    fun agentBatch_multipleToolCalls_objectRowsStayEmptyAndBatchItemsUnchanged() = runTest {
        val rig = buildAgentBatchRig(
            toolCalls = listOf(
                addItemCall(id = "call_1", checklist = "Покупки", item = "молоко"),
                addItemCall(id = "call_2", checklist = "Покупки", item = "хлеб"),
            ),
        )

        rig.send("добавь молоко и хлеб в покупки")

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "a mutating agent batch must be confirmed")
        assertEquals(
            emptyList(),
            state.objectRows(),
            "the agent batch is not a single typed object — it must keep its numbered plan, " +
                "rows = [${state.rowSummary()}]",
        )
        assertFalse(state.pendingChoice!!.hasObjectRows, "no rows → the dock keeps its D1 anchor for batches")
        val batchItems = state.pendingChoice?.batchItems
        assertNotNull(batchItems, "the batch plan must still be rendered via batchItems")
        assertEquals(2, batchItems.size, "both proposed actions must be listed")
    }

    // ── 8. Chip meta — the count rides beside the label, not inside it ────────

    /**
     * Two lists called "Покупки" are indistinguishable without their size; the count is what tells
     * them apart. It is a SEPARATE field because after the tap the label collapses into a
     * user-style sent pill, and "Покупки • 12" reads broken as something the user said.
     */
    @Test
    fun whichListChoice_candidates_metaCarriesItemCountAndLabelStaysBareName() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = null),
            checklists = listOf(
                seedList(id = 1L, name = "Покупки", itemCount = 12),
                seedList(id = 2L, name = "Работа", itemCount = 3),
            ),
        )

        rig.send("добавь молоко")

        val candidates = rig.viewModel.screenState.value.candidateOptions()
        assertEquals(listOf("Покупки", "Работа"), candidates.map { it.label }, "the label must stay the bare name")
        assertEquals(
            listOf("12", "3"),
            candidates.map { it.meta },
            "each chip must carry its item count as meta — that is what separates two same-named lists",
        )
        assertTrue(
            candidates.none { it.meta!! in it.label },
            "meta must not be baked into the label: the label becomes the user's sent pill after the tap",
        )
    }

    // ── 9. More than six candidates → cap, and the useful six ─────────────────

    /**
     * The cap is not the interesting part — the ORDER is. Truncating 9 lists to the 6 the database
     * happened to return hides the list the user actually wants; most-recently-used first is what
     * makes a capped picker usable.
     */
    @Test
    fun whichListChoice_moreThanSixCandidates_showsSixMostRecentlyUsedFirst() = runTest {
        // updatedAt ascending with the name, so MRU order is the exact reverse of the seed order.
        val seeds = (1..9).map { i ->
            seedList(id = i.toLong(), name = "Список $i", itemCount = i, updatedAt = i * 1000L)
        }
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = null),
            checklists = seeds,
        )

        rig.send("добавь молоко")

        val labels = rig.viewModel.screenState.value.candidateOptions().map { it.label }
        assertEquals(6, labels.size, "MAX_CHOICE_OPTIONS caps the picker at 6 chips")
        assertEquals(
            listOf("Список 9", "Список 8", "Список 7", "Список 6", "Список 5", "Список 4"),
            labels,
            "with 9 lists and room for 6, the 6 shown must be the most recently used — " +
                "otherwise the cap hides the list the user came for",
        )
    }

    // ── 10. Count settles nothing → the whole block falls back to a date ──────

    /**
     * When two candidates share both name and count the count answers nothing, so the block adds
     * the last-updated day — to EVERY candidate, not just the colliding pair. A block mixing
     * "• 12" with "• 12 • 3 июля" reads as two different kinds of fact instead of one comparison.
     *
     * The meta is now worth asserting: until id-hints (D2, 2026-07-15) a chip carried only
     * `withHint(name)`, so two lists sharing a name dispatched the IDENTICAL ToolCall and no meta
     * could make the block actionable — a distinct meta would have described a difference the
     * picker could not act on. Chips now carry the candidate's id, so meta and dispatch agree;
     * `whichListChoice_sameNamedLists_eachChipTargetsItsOwnIdNotJustTheName` pins the other half.
     */
    @Test
    fun whichListChoice_nameAndCountCollide_everyCandidateFallsBackToDateMeta() = runTest {
        val workUpdatedAt = 1_750_000_000_000L
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = null),
            checklists = listOf(
                seedList(id = 1L, name = "Покупки", itemCount = 12, updatedAt = 1_700_000_000_000L),
                seedList(id = 2L, name = "Покупки", itemCount = 12, updatedAt = 1_800_000_000_000L),
                seedList(id = 3L, name = "Работа", itemCount = 3, updatedAt = workUpdatedAt),
            ),
        )

        rig.send("добавь молоко")

        val metas = rig.viewModel.screenState.value.candidateOptions().map { it.meta }
        assertTrue(metas.all { it != null }, "every candidate needs meta; got $metas")
        assertTrue(
            metas.all { it!!.contains("DAY(") },
            "the date fallback goes to EVERY chip in the block: one chip reading '12' beside another " +
                "reading '12 • 3 July' compares nothing; got $metas",
        )
        // The uninvolved list gets the date too — and it must be ITS date, not a neighbour's.
        // "•" is inlined because ChatViewModel's companion is private; pinning it here is deliberate
        // rather than incidental — its KDoc records that D1 shipped "→" and Skiko rendered tofu on
        // the web canvas, so an unverified glyph swap should trip a test.
        val work = rig.viewModel.screenState.value.candidateOptions().single { it.label == "Работа" }
        assertEquals(
            "3 • DAY($workUpdatedAt)",
            work.meta,
            "each chip's meta must describe its own list",
        )
    }

    // ── 10b. id-hints: the chips must be different ANSWERS, not just different labels ──

    /**
     * THE defect id-hints closes (D2, 2026-07-15). Two lists named "Покупки" produce two chips; if
     * each only carried `checklistHint = "Покупки"` they dispatched byte-identical ToolCalls, so
     * whichever the user tapped, the dispatcher re-ran the same ambiguous name-match and asked
     * again. That is the "which list… Shopping, Shopping or Shopping?" loop Siri answers by telling
     * users to rename their lists.
     *
     * Asserts the ids are (a) present, (b) DISTINCT, and (c) each paired with the row it actually
     * describes — an id that is merely non-null but copied from a neighbour would pass (a) and (b)
     * while still routing the add into the wrong list.
     */
    @Test
    fun whichListChoice_sameNamedLists_eachChipTargetsItsOwnIdNotJustTheName() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = null),
            checklists = listOf(
                seedList(id = 1L, name = "Покупки", itemCount = 12, updatedAt = 1_700_000_000_000L),
                seedList(id = 2L, name = "Покупки", itemCount = 3, updatedAt = 1_800_000_000_000L),
            ),
        )

        rig.send("добавь молоко")

        val chips = rig.viewModel.screenState.value.candidateOptions()
        assertEquals(2, chips.size, "two lists, no hint → one chip each")

        val calls = chips.map { (it.action as ChoiceAction.Execute).toolCall }
        assertTrue(calls.all { it is ToolCall.AddItem }, "the picker re-runs the original add; got $calls")
        val ids = calls.map { (it as ToolCall.AddItem).checklistId }
        assertEquals(
            listOf(2L, 1L),
            ids,
            "each chip must carry ITS list's id (MRU order: id=2 updated later). Same-named chips " +
                "sharing an id — or carrying none — dispatch the identical call, so the picker asks " +
                "a question it then throws away; got $calls",
        )
        assertTrue(
            calls.all { (it as ToolCall.AddItem).checklistHint == "Покупки" },
            "the name rides along for result copy and the id-less fallback; got $calls",
        )

        // (c) id ↔ meta agreement: the chip that SAYS 3 items must be the one that TARGETS the
        // 3-item list. Nothing else in the picker can catch an id/label pairing that slipped.
        val threeItems = chips.single { it.meta == "3" }
        assertEquals(
            2L,
            ((threeItems.action as ChoiceAction.Execute).toolCall as ToolCall.AddItem).checklistId,
            "the chip reading '3' must target the 3-item list (id=2) — meta and dispatch must " +
                "describe the same list, or the meta is decoration that lies",
        )
    }

    /**
     * The id is an addition, not a replacement: a candidate name the repository cannot resolve
     * (renamed/deleted mid-question) still produces a working chip that falls back to the name.
     * Without this the picker would silently drop the list the user came for.
     */
    @Test
    fun whichListChoice_unknownCandidateName_chipStillDispatchesWithNullIdAndTheName() = runTest {
        // A hint is required: a hintless add opens the picker up front and never reaches the
        // dispatcher, so the AmbiguousMatch branch under test would not run.
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = "покупки"),
            checklists = listOf(
                seedList(id = 1L, name = "Покупки", itemCount = 12, updatedAt = 1_700_000_000_000L),
                seedList(id = 2L, name = "Работа", itemCount = 3, updatedAt = 1_800_000_000_000L),
            ),
            dispatchOutcome = DispatchOutcome.AmbiguousMatch(listOf("Покупки", "Призрак")),
        )

        rig.send("добавь молоко в покупки")

        val calls = rig.viewModel.screenState.value.candidateOptions()
            .map { (it.action as ChoiceAction.Execute).toolCall as ToolCall.AddItem }
        val ghost = calls.single { it.checklistHint == "Призрак" }
        assertNull(ghost.checklistId, "an unresolvable candidate carries no id — it must not borrow one")
        assertEquals("Призрак", ghost.checklistHint, "…but it keeps the name, so the chip still dispatches")
        assertEquals(1L, calls.single { it.checklistHint == "Покупки" }.checklistId, "the resolvable one still gets its id")
    }

    /** No collision → the count alone; the date is noise the user does not need. */
    @Test
    fun whichListChoice_distinctCounts_metaStaysBareCountWithNoDate() = runTest {
        val rig = buildVmRig(
            classification = addClassification(itemText = "молоко", checklistHint = null),
            checklists = listOf(
                seedList(id = 1L, name = "Покупки", itemCount = 12, updatedAt = 1_700_000_000_000L),
                seedList(id = 2L, name = "Работа", itemCount = 3, updatedAt = 1_800_000_000_000L),
            ),
        )

        rig.send("добавь молоко")

        val metas = rig.viewModel.screenState.value.candidateOptions().map { it.meta }
        assertEquals(
            listOf("3", "12"),
            metas,
            "distinct counts already disambiguate — appending a date to every chip is noise; got $metas",
        )
    }
}
