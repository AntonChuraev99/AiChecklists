package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatAttachment
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRendererImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ════════════════════════════════════════════════════════════════════════════
// The question carries its OBJECT — and says it out loud (D1 B-branch → D2 rows).
//
// Delete / SetItemReminder / MoveAllReminders / CreateChecklist / AttachToItem are irreversible or
// heavy, so they still ask first. The question itself is ARGUMENT-LESS ("Delete an item?") and the
// thing being acted on lives underneath it: D1 put it in ONE flat preview line, D2 (2026-07-15)
// replaced that line with typed rows so a question with three entities (item + list + time) no
// longer has to pick one to say out loud. Either way the rows are the only place the user can read
// what they are agreeing to — that is what these tests pin.
//
// ─── Why Robolectric and not commonTest ─────────────────────────────────────
// The ViewModel resolves Compose Resources (getString) for every row's contentDescription, and
// ToolCallPreviewRendererImpl does the same for the agent batch. In a plain unit host that throws,
// ChatViewModel.choiceString() swallows it and returns "…" for EVERY localized string, so an
// assertion about copy is not merely weak there — it is unreachable. Robolectric +
// isIncludeAndroidResources = true (this module's build.gradle) is the one host where it resolves,
// which makes this the ONLY place the a11y descriptions can be checked at all.
//
// Locale is pinned to RU (qualifiers + Locale.setDefault, mirroring LocalAppLocale.android.kt) so
// the assertions can also prove the copy is localized rather than hardcoded English.
// ════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru")
class ChatPreviewContentTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var previousLocale: java.util.Locale

    /** The REAL renderer — a stub returning toolCall.toString() would make every "contains" pass. */
    private val realRenderer = ToolCallPreviewRendererImpl(TokenDateFormatter())

    @Before
    fun setUp() {
        previousLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru"))
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        java.util.Locale.setDefault(previousLocale)
    }

    private fun rigFor(
        intent: ChatIntent,
        toolCall: ToolCall,
        lists: List<String>,
    ): VmRig = buildVmRig(
        classification = IntentClassification(
            intent = intent,
            confidence = 1f,
            layer = RoutingLayer.Local,
            preBuiltToolCall = toolCall,
        ),
        lists = lists,
        previewRenderer = realRenderer,
    )

    private fun ChatScreenState.rows(): List<ChoiceObjectRow> {
        val pending = pendingChoice
        assertNotNull("no pendingChoice shown — the question was never asked", pending)
        return pending!!.choice.objectRows
    }

    private fun ChatScreenState.rowsText(): String = rows().joinToString(" | ") { "${it.kind}='${it.value}'" }

    private fun ChatScreenState.row(kind: RowKind): ChoiceObjectRow {
        val match = rows().filter { it.kind == kind }
        assertEquals("expected exactly one $kind row, rows = ${rowsText()}", 1, match.size)
        return match.first()
    }

    /**
     * Deleting is irreversible, so the confirmation stays — but "Delete an item?" without naming the
     * victim is unanswerable. Both the item AND the list it lives in must be on the block, and the
     * item must be marked as the thing being destroyed.
     */
    @Test
    fun showWriteChoice_deleteItem_rowsCarryItemAndList() = runTest {
        val rig = rigFor(
            intent = ChatIntent.DeleteItem,
            toolCall = ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "Молоко"),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("удали молоко из покупок")

        val state = rig.viewModel.screenState.value
        assertEquals("the row must name the item being deleted", "Молоко", state.row(RowKind.Item).value)
        assertEquals("the row must name the list it is deleted from", "Покупки", state.row(RowKind.Destination).value)
        assertEquals(
            "a delete's object must be flagged destructive so the UI can tint it",
            RowEmphasis.Danger,
            state.row(RowKind.Item).emphasis,
        )
        assertEquals(
            "delete must not dispatch before the user confirms",
            emptyList<ToolCall>(),
            rig.dispatcher.dispatched,
        )
    }

    /**
     * A reminder IS its time — "Set a reminder for milk?" without the moment is not a question the
     * user can answer. Guards the rows against dropping [ToolCall.SetItemReminder.at].
     */
    @Test
    fun showWriteChoice_setItemReminder_rowsCarryItemListAndTime() = runTest {
        val at = 1_784_000_000_000L
        val rig = rigFor(
            intent = ChatIntent.SetReminder,
            toolCall = ToolCall.SetItemReminder(checklistHint = "Покупки", itemText = "Молоко", at = at),
            lists = listOf("Покупки"),
        )

        rig.send("напомни про молоко завтра в 9")

        val state = rig.viewModel.screenState.value
        assertEquals("Молоко", state.row(RowKind.Item).value)
        assertEquals("Покупки", state.row(RowKind.Destination).value)
        assertEquals(
            "the time row must route `at` through ChatDateFormatter — the moment IS the reminder",
            "DT($at)",
            state.row(RowKind.Time).value,
        )
    }

    /** Moving reminders between days is meaningless without BOTH endpoints on the block. */
    @Test
    fun showWriteChoice_moveAllReminders_rowCarriesBothDates() = runTest {
        val from = 1_784_000_000_000L
        val to = 1_784_600_000_000L
        val rig = rigFor(
            intent = ChatIntent.MoveReminders,
            toolCall = ToolCall.MoveAllReminders(
                fromDayStartMs = from,
                fromDayEndMs = from + 86_399_999L,
                toDayStartMs = to,
            ),
            lists = listOf("Покупки"),
        )

        rig.send("перенеси все напоминания с завтра на послезавтра")

        val range = rig.viewModel.screenState.value.row(RowKind.DateRange).value
        assertTrue("the range must show the source day: '$range'", range.contains("DAY($from)"))
        assertTrue("the range must show the target day: '$range'", range.contains("DAY($to)"))
    }

    /** "Create a list?" must show what would be IN it — the items are the decision. */
    @Test
    fun showWriteChoice_createChecklist_rowsCarryNameAndInitialItems() = runTest {
        val rig = rigFor(
            intent = ChatIntent.CreateChecklist("Отпуск"),
            toolCall = ToolCall.CreateChecklist(name = "Отпуск", initialItems = listOf("Паспорт", "Билеты")),
            lists = emptyList(),
        )

        rig.send("создай список отпуск с паспортом и билетами")

        val state = rig.viewModel.screenState.value
        assertEquals("the row must name the new list", "Отпуск", state.row(RowKind.Name).value)
        assertEquals(
            "every proposed item must be previewed, in order",
            listOf("Паспорт", "Билеты"),
            state.rows().filter { it.kind == RowKind.Preview }.map { it.value },
        )
    }

    /**
     * The which-list picker asks "into which list?" — the user cannot answer without seeing WHAT is
     * being placed. The object must survive the hint-swap that builds the candidate chips.
     */
    @Test
    fun showWhichListChoice_rowsCarryItemText() = runTest {
        val rig = rigFor(
            intent = ChatIntent.CreateItem,
            toolCall = ToolCall.AddItem(checklistHint = null, itemText = "Молоко"),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("добавь молоко")

        assertEquals(
            "the which-list picker must keep the object visible",
            "Молоко",
            rig.viewModel.screenState.value.row(RowKind.Item).value,
        )
    }

    // ── The confirm chip must name the OUTCOME, not the mechanism ─────────────

    /**
     * "Применить" tells the user nothing about what is about to happen; the chip that commits an
     * AI-proposed list must state the size of what it creates. The count is the decision — approving
     * 8 unseen items is a different act from approving 1.
     *
     * The RU plural forms are asserted explicitly because RU has three (пункт / пункта / пунктов)
     * and an `items_count` wired as a plain string would still read fine at 1 and break at 8 — the
     * exact bug the design flagged for `chat_preview_files_count` ("1 files").
     */
    @Test
    fun showWriteChoice_createChecklistWithItems_confirmChipCountsTheItems() = runTest {
        fun labelFor(itemCount: Int): String {
            val rig = rigFor(
                intent = ChatIntent.CreateChecklist("Отпуск"),
                toolCall = ToolCall.CreateChecklist(
                    name = "Отпуск",
                    initialItems = List(itemCount) { "Пункт $it" },
                ),
                lists = emptyList(),
            )
            rig.send("создай список отпуск")
            val options = rig.viewModel.screenState.value.pendingChoice?.choice?.options.orEmpty()
            val execute = options.first { it.action is com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction.Execute }
            return execute.label
        }

        assertEquals("Создать 8 пунктов", labelFor(8))
        assertEquals("the RU 'one' form — a plain string would read '1 пунктов'", "Создать 1 пункт", labelFor(1))
        assertEquals("the RU 'few' form (2-4)", "Создать 3 пункта", labelFor(3))
        assertEquals(
            "an empty list has no count to state — the chip must not read 'Создать 0 пунктов'",
            "Создать",
            labelFor(0),
        )
    }

    // ── The rows must SPEAK — a11y descriptions, resolved and localized ───────

    /**
     * The prompt bubble merges its descendants for accessibility, so without a per-row description a
     * screen reader flattens "Set a reminder? / Молоко / Покупки / DT(…)" into one run of words and
     * every role is lost — the user hears a list of nouns and no indication of which is the item,
     * which is the destination and which is the alarm time.
     *
     * Only reachable here: on a plain unit host every one of these is the "…" fallback.
     */
    @Test
    fun objectRows_ruLocale_everyRowHasResolvedLocalizedContentDescription() = runTest {
        val at = 1_784_000_000_000L
        val rig = rigFor(
            intent = ChatIntent.SetReminder,
            toolCall = ToolCall.SetItemReminder(checklistHint = "Покупки", itemText = "Молоко", at = at),
            lists = listOf("Покупки"),
        )

        rig.send("напомни про молоко завтра в 9")
        val state = rig.viewModel.screenState.value

        state.rows().forEach { row ->
            assertTrue(
                "${row.kind}: contentDescription is the choiceString fallback — the resource did not " +
                    "resolve, so the row would be read as an ellipsis: '${row.contentDescription}'",
                row.contentDescription != "…",
            )
            assertTrue(
                "${row.kind}: contentDescription leaked a raw resource key: '${row.contentDescription}'",
                !row.contentDescription.contains("chat_"),
            )
            assertTrue(
                "${row.kind}: contentDescription must include the row's value, or the reader announces " +
                    "a role with no content: '${row.contentDescription}' vs '${row.value}'",
                row.contentDescription.contains(row.value),
            )
            assertTrue(
                "${row.kind}: contentDescription must SAY the role, not just echo the value — that is the " +
                    "whole reason the row carries one: '${row.contentDescription}'",
                row.contentDescription != row.value,
            )
        }

        // …and it is the RU copy, not an English literal (the strings are "Пункт: %1$s" / "Время: %1$s").
        assertTrue(
            "the item row must be spoken in RU: '${state.row(RowKind.Item).contentDescription}'",
            state.row(RowKind.Item).contentDescription.startsWith("Пункт"),
        )
        assertTrue(
            "the time row must be spoken in RU: '${state.row(RowKind.Time).contentDescription}'",
            state.row(RowKind.Time).contentDescription.startsWith("Время"),
        )
        assertTrue(
            "the destination row must be spoken in RU: '${state.row(RowKind.Destination).contentDescription}'",
            state.row(RowKind.Destination).contentDescription.startsWith("В чек-лист"),
        )
    }

    /** The file row speaks its own role, and the RU copy comes from strings.xml. */
    @Test
    fun objectRows_ruLocale_attachRowsAreResolvedAndLocalized() = runTest {
        val attachment = ChatAttachment(
            sourcePath = "/tmp/scan.pdf",
            mimeType = "application/pdf",
            fileName = "scan.pdf",
        )
        val rig = rigFor(
            intent = ChatIntent.AttachToItem(itemText = "Молоко", checklistHint = "Покупки"),
            toolCall = ToolCall.AttachToItem(
                checklistHint = "Покупки",
                itemText = "Молоко",
                attachments = listOf(attachment),
            ),
            lists = listOf("Покупки"),
        )
        rig.viewModel.sendIntent(ChatScreenIntent.OnAttachmentPicked(attachment))

        rig.send("прикрепи это к молоку")

        val file = rig.viewModel.screenState.value.row(RowKind.File)
        assertEquals("scan.pdf", file.value)
        assertTrue(
            "the file row must be spoken in RU: '${file.contentDescription}'",
            file.contentDescription.startsWith("Файл"),
        )
    }

    /**
     * The RU block must not leak English literals. Asserted through the ViewModel because that is
     * the path the user's eyes actually take.
     */
    @Test
    fun showWriteChoice_ruLocale_blockHasNoEnglishHardcode() = runTest {
        val rig = rigFor(
            intent = ChatIntent.DeleteItem,
            toolCall = ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "Молоко"),
            lists = listOf("Покупки"),
        )

        rig.send("удали молоко из покупок")

        val state = rig.viewModel.screenState.value
        val spoken = state.rows().joinToString(" ") { it.contentDescription }
        assertTrue(
            "«In checklist: …» is the EN string — the RU block must come from values-ru: '$spoken'",
            !spoken.contains("In checklist"),
        )
        assertTrue(
            "«Item: …» is the EN string — the RU block must come from values-ru: '$spoken'",
            !spoken.contains("Item:"),
        )
    }
}
