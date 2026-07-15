package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ════════════════════════════════════════════════════════════════════════════
// D1 B-branch — the question stays, but it now carries the OBJECT.
//
// Delete / SetItemReminder / MoveAllReminders / CreateChecklist / AttachToItem are irreversible or
// heavy, so they still ask first. The D1 change is that the question itself became ARGUMENT-LESS
// ("Delete an item?") and the thing being acted on moved into its own preview line underneath
// (PendingChoice.batchItems). A one-slot prompt could only ever name ONE of item/list — which is
// how "Add to Shopping?" shipped never saying WHAT. So the preview line is the only place the user
// can read what they are agreeing to, and that is what these tests pin.
//
// ─── Why Robolectric and not commonTest ─────────────────────────────────────
// ToolCallPreviewRendererImpl resolves Compose Resources (getString) for the " (в <список>)" suffix
// and the attachment labels. In a plain unit host that throws ("Resources.getSystem not mocked"),
// ChatViewModel's runCatching swallows it, and the test sees a null pendingChoice instead of a
// preview — an assertion about copy is simply not reachable there. Robolectric +
// isIncludeAndroidResources = true (this module's build.gradle) is the one host where it resolves.
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

    /**
     * Deleting is irreversible, so the confirmation stays — but "Delete an item?" without naming
     * the victim is unanswerable. Both the item AND the list it lives in must be on the card.
     */
    @Test
    fun showWriteChoice_deleteItem_previewCarriesItemAndList() = runTest {
        val rig = rigFor(
            intent = ChatIntent.DeleteItem,
            toolCall = ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "Молоко"),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("удали молоко из покупок")

        val preview = rig.viewModel.screenState.value.singleBatchText()
        assertTrue("preview must name the item being deleted: '$preview'", preview.contains("Молоко"))
        assertTrue("preview must name the list it is deleted from: '$preview'", preview.contains("Покупки"))
        assertTrue(
            "a delete preview line must be flagged destructive so the UI can tint it",
            rig.viewModel.screenState.value.pendingChoice?.batchItems?.single()?.isDestructive == true,
        )
        assertEquals(
            "delete must not dispatch before the user confirms",
            emptyList<ToolCall>(),
            rig.dispatcher.dispatched,
        )
    }

    /**
     * A reminder IS its time — "Set a reminder for milk?" without the moment is not a question the
     * user can answer. Guards the preview against dropping [ToolCall.SetItemReminder.at].
     */
    @Test
    fun showWriteChoice_setItemReminder_previewCarriesTime() = runTest {
        val at = 1_784_000_000_000L
        val rig = rigFor(
            intent = ChatIntent.SetReminder,
            toolCall = ToolCall.SetItemReminder(checklistHint = "Покупки", itemText = "Молоко", at = at),
            lists = listOf("Покупки"),
        )

        rig.send("напомни про молоко завтра в 9")

        val preview = rig.viewModel.screenState.value.singleBatchText()
        assertTrue("preview must name the item: '$preview'", preview.contains("Молоко"))
        assertTrue("preview must name the list: '$preview'", preview.contains("Покупки"))
        assertTrue(
            "preview must route `at` through ChatDateFormatter — the moment IS the reminder: '$preview'",
            preview.contains("DT($at)"),
        )
    }

    /** Moving reminders between days is meaningless without BOTH endpoints on the card. */
    @Test
    fun showWriteChoice_moveAllReminders_previewCarriesBothDates() = runTest {
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

        val preview = rig.viewModel.screenState.value.singleBatchText()
        assertTrue("preview must show the source day: '$preview'", preview.contains("DAY($from)"))
        assertTrue("preview must show the target day: '$preview'", preview.contains("DAY($to)"))
    }

    /** "Create a list?" must show what would be IN it — the items are the decision. */
    @Test
    fun showWriteChoice_createChecklist_previewCarriesInitialItems() = runTest {
        val rig = rigFor(
            intent = ChatIntent.CreateChecklist("Отпуск"),
            toolCall = ToolCall.CreateChecklist(name = "Отпуск", initialItems = listOf("Паспорт", "Билеты")),
            lists = emptyList(),
        )

        rig.send("создай список отпуск с паспортом и билетами")

        val preview = rig.viewModel.screenState.value.singleBatchText()
        assertTrue("preview must name the new list: '$preview'", preview.contains("Отпуск"))
        assertTrue("preview must list initial item 1: '$preview'", preview.contains("Паспорт"))
        assertTrue("preview must list initial item 2: '$preview'", preview.contains("Билеты"))
    }

    /**
     * The which-list picker asks "into which list?" — the user cannot answer without seeing WHAT is
     * being placed. The object must survive the hint-swap that builds the candidate chips.
     */
    @Test
    fun showWhichListChoice_previewCarriesItemText() = runTest {
        val rig = rigFor(
            intent = ChatIntent.CreateItem,
            toolCall = ToolCall.AddItem(checklistHint = null, itemText = "Молоко"),
            lists = listOf("Покупки", "Работа"),
        )

        rig.send("добавь молоко")

        val preview = rig.viewModel.screenState.value.singleBatchText()
        assertTrue("which-list picker must keep the object visible: '$preview'", preview.contains("Молоко"))
    }

    /**
     * The RU preview must not leak the renderer's former English literals (" (in ", "Attach ").
     * Asserted through the ViewModel because that is the path the user's eyes actually take.
     */
    @Test
    fun showWriteChoice_ruLocale_previewHasNoEnglishHardcode() = runTest {
        val rig = rigFor(
            intent = ChatIntent.DeleteItem,
            toolCall = ToolCall.DeleteItem(checklistHint = "Покупки", itemText = "Молоко"),
            lists = listOf("Покупки"),
        )

        rig.send("удали молоко из покупок")

        val preview = rig.viewModel.screenState.value.singleBatchText()
        assertTrue(
            "«(in …)» is an English literal — the list suffix must come from strings.xml: '$preview'",
            !preview.contains("(in "),
        )
    }
}
