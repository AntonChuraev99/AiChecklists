package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AiChatRepository
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview.ToolCallPreviewRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─── Fakes ────────────────────────────────────────────────────────────────────

private class FakeAiChatRepository(
    private val classifyResult: IntentClassification = IntentClassification(
        intent = ChatIntent.Unknown("?"),
        confidence = 0f,
        layer = RoutingLayer.Local,
    ),
) : AiChatRepository {
    override suspend fun classify(input: String, locale: ChatLocale): IntentClassification =
        classifyResult
}

private class FakeToolCallDispatcher(
    private val outcome: DispatchOutcome = DispatchOutcome.Success("Done."),
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

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun makeVm(
    repo: AiChatRepository = FakeAiChatRepository(),
    dispatcher: FakeToolCallDispatcher = FakeToolCallDispatcher(),
    renderer: ToolCallPreviewRenderer = FakePreviewRenderer,
): ChatViewModel = ChatViewModel(
    aiChatRepository = repo,
    toolCallDispatcher = dispatcher,
    previewRenderer = renderer,
    localeProvider = FakeLocaleProvider,
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
    fun initialState_hasWelcomeMessage() {
        val vm = makeVm()
        val state = vm.screenState.value
        assertEquals(1, state.messages.size)
        assertEquals("welcome", state.messages.first().id)
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
        // No extra messages added for blank input
        assertEquals(1, vm.screenState.value.messages.size)
    }

    // ── 3. Unknown intent → assistant message inline, not snackbar ────────────

    @Test
    fun sendClick_unknownIntent_addsAssistantMessageInline() = runTest {
        val repo = FakeAiChatRepository(
            classifyResult = IntentClassification(
                intent = ChatIntent.Unknown("gibberish"),
                confidence = 0f,
                layer = RoutingLayer.Local,
            )
        )
        val vm = makeVm(repo = repo)
        vm.sendIntent(ChatScreenIntent.OnInputChange("gibberish"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        val state = vm.screenState.value
        // welcome + user message + assistant reply = 3
        assertEquals(3, state.messages.size)
        assertTrue(state.messages.last().content.contains("didn't catch", ignoreCase = true))
        assertNull(state.pendingPreview)
        assertEquals(false, state.isProcessing)
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

    // ── 5. FindItems intent → dispatched inline, no preview ──────────────────

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
            outcome = DispatchOutcome.Success("Found 1 item: «milk» in Shopping")
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)
        vm.sendIntent(ChatScreenIntent.OnInputChange("find milk"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)

        assertNull(vm.screenState.value.pendingPreview)
        assertIs<ToolCall.FindItemsQuery>(fakeDispatcher.lastDispatched)
        // welcome + user + assistant reply
        assertEquals(3, vm.screenState.value.messages.size)
    }

    // ── 6. PreviewApply → dispatches ToolCall, clears pendingPreview ──────────

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
            outcome = DispatchOutcome.Success("Added «milk».")
        )
        val vm = makeVm(repo = repo, dispatcher = fakeDispatcher)

        // Build a preview
        vm.sendIntent(ChatScreenIntent.OnInputChange("add milk to shopping"))
        vm.sendIntent(ChatScreenIntent.OnSendClick)
        assertIs<PendingPreview>(vm.screenState.value.pendingPreview)

        // Apply it
        vm.sendIntent(ChatScreenIntent.OnPreviewApply)

        assertNull(vm.screenState.value.pendingPreview)
        assertIs<ToolCall.AddItem>(fakeDispatcher.lastDispatched)
        // welcome + user + assistant "Added..." reply
        val lastMsg = vm.screenState.value.messages.last()
        assertEquals("Added «milk».", lastMsg.content)
    }

    // ── 7. PreviewCancel → clears pendingPreview, no snackbar ────────────────

    @Test
    fun previewCancel_clearsPendingPreviewSilently() = runTest {
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

        // Track that no sideEffect is emitted
        var sideEffectEmitted = false
        val job = launch {
            vm.sideEffect.collect { sideEffectEmitted = true }
        }

        vm.sendIntent(ChatScreenIntent.OnPreviewCancel)

        job.cancel()
        assertNull(vm.screenState.value.pendingPreview)
        assertEquals(false, sideEffectEmitted, "Cancel must not emit any SideEffect")
    }

    // ── 8. RequiresPremium outcome → emits ShowSnackbar with correct key ──────

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
}
