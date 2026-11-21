package com.antonchuraev.homesearchchecklist.telegram

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reminder-channel section of the Linked state (`reminders-addendum.md` §4, §6). */
@OptIn(ExperimentalCoroutinesApi::class)
class TelegramReminderChannelViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = FakeTelegramLinkRepository()
    private val analytics = RecordingTelegramAnalytics()
    private val authPhase = MutableStateFlow(TelegramAuthPhase.SignedIn)
    private val linkChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val store = ViewModelStore()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun linked(channel: ReminderChannel?, username: String = "anton") =
        TelegramAccountResult.Success(TelegramLinkStatus.Linked(username, "Anton", 1_000L, channel))

    private fun TestScope.openLinked(channel: ReminderChannel?): Pair<TelegramViewModel, MutableList<TelegramSideEffect>> {
        repository.statusQueue += linked(channel)
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    TelegramViewModel(repository, authPhase, linkChanged, analytics, nowMs = { scheduler.currentTime })
                }
            },
        )[TelegramViewModel::class]
        val effects = mutableListOf<TelegramSideEffect>()
        backgroundScope.launch { vm.sideEffect.collect { effects += it } }
        runCurrent()
        vm.onIntent(TelegramIntent.ScreenEntered)
        vm.onIntent(TelegramIntent.ScreenResumed)
        runCurrent()
        return vm to effects
    }

    private fun TelegramViewModel.linkedState() = screenState.value as TelegramScreenState.Linked

    private fun snackbars(effects: List<TelegramSideEffect>) =
        effects.filterIsInstance<TelegramSideEffect.ShowSnackbar>().map { it.message }

    private fun gate() = CompletableDeferred<TelegramAccountResult<ReminderChannel>>().also { repository.channelGates += it }

    // ── Visibility ──────────────────────────────────────────────────────────

    @Test
    fun preRolloutServer_hidesSection_andTapsSendNothing() = runTest(dispatcher) {
        val (vm, _) = openLinked(channel = null)
        assertFalse(vm.linkedState().showsReminderSection)
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()
        assertTrue(repository.channelRequests.isEmpty())
    }

    @Test
    fun disconnectNote_onlyForNonPushChannel() = runTest(dispatcher) {
        assertFalse(TelegramScreenState.Linked(null, null, null, reminderChannel = ReminderChannel.Push).showsDisconnectReminderNote)
        assertFalse(TelegramScreenState.Linked(null, null, null, reminderChannel = null).showsDisconnectReminderNote)
        assertTrue(TelegramScreenState.Linked(null, null, null, reminderChannel = ReminderChannel.Telegram).showsDisconnectReminderNote)
        assertTrue(TelegramScreenState.Linked(null, null, null, reminderChannel = ReminderChannel.Both).showsDisconnectReminderNote)
    }

    // ── Save ────────────────────────────────────────────────────────────────

    @Test
    fun tap_showsSavingAtOnce_thenConfirmedValue_andCountsEvent() = runTest(dispatcher) {
        val (vm, effects) = openLinked(ReminderChannel.Push)
        val g = gate()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        assertEquals(ReminderChannel.Telegram, vm.linkedState().channelSaving, "the tap shows before the network")
        assertEquals(ReminderChannel.Push, vm.linkedState().reminderChannel, "confirmed value is unchanged while saving")

        g.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(ReminderChannel.Telegram, vm.linkedState().reminderChannel)
        assertEquals(null, vm.linkedState().channelSaving)
        assertTrue(snackbars(effects).isEmpty(), "saved = the radio, no toast (addendum §4)")
        assertEquals(
            listOf(mapOf<String, Any>(AnalyticsEvents.Telegram.PARAM_CHANNEL to "telegram", AnalyticsParams.SOURCE to "app")),
            analytics.params(AnalyticsEvents.Telegram.REMINDER_CHANNEL_SET),
        )
    }

    @Test
    fun tappingTheSelectedRow_sendsNothing() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Both)
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Both))
        runCurrent()
        assertTrue(repository.channelRequests.isEmpty())
        assertEquals(null, vm.linkedState().channelSaving)
    }

    @Test
    fun latestTapWins_secondTapWaitsForInFlight_thenIsSent() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Push)
        val first = gate()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Both))
        runCurrent()
        assertEquals(ReminderChannel.Both, vm.linkedState().channelSaving, "spinner moves to the newest tap")
        assertEquals(listOf(ReminderChannel.Telegram), repository.channelRequests, "one request in flight at a time")

        first.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Telegram, ReminderChannel.Both), repository.channelRequests)
        assertEquals(ReminderChannel.Both, vm.linkedState().reminderChannel)
        assertEquals(null, vm.linkedState().channelSaving)
    }

    @Test
    fun tapBackToConfirmedWhileSaving_isSentToo_becauseTheInFlightValueMayLand() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Push)
        val first = gate()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Push))
        first.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Telegram, ReminderChannel.Push), repository.channelRequests)
        assertEquals(ReminderChannel.Push, vm.linkedState().reminderChannel)
    }

    @Test
    fun failure_revertsToLastConfirmed_withRetrySnackbarCarryingTheAttempt() = runTest(dispatcher) {
        val (vm, effects) = openLinked(ReminderChannel.Push)
        repository.channelResult = { TelegramAccountResult.Failure(TelegramAccountError.Network(RuntimeException("offline"))) }
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(ReminderChannel.Push, vm.linkedState().reminderChannel)
        assertEquals(null, vm.linkedState().channelSaving)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.ReminderSaveFailed(ReminderChannel.Telegram)), snackbars(effects))
        assertEquals(0, analytics.count(AnalyticsEvents.Telegram.REMINDER_CHANNEL_SET))

        // Retry = the same intent with the attempted value.
        repository.channelResult = { TelegramAccountResult.Success(it) }
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(ReminderChannel.Telegram, vm.linkedState().reminderChannel)
    }

    @Test
    fun failureAfterAnIntermediateSuccess_revertsToThatConfirmedValue_notTheOriginal() = runTest(dispatcher) {
        val (vm, effects) = openLinked(ReminderChannel.Push)
        val first = gate()
        val second = gate()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Both))
        first.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        runCurrent()
        second.complete(TelegramAccountResult.Failure(TelegramAccountError.Server(500, null)))
        runCurrent()
        assertEquals(ReminderChannel.Telegram, vm.linkedState().reminderChannel)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.ReminderSaveFailed(ReminderChannel.Both)), snackbars(effects))
    }

    // ── Refresh guard ───────────────────────────────────────────────────────

    @Test
    fun backgroundRefreshDuringSave_doesNotMoveTheRadio_butUpdatesTheCard() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Push)
        val g = gate()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Telegram))
        runCurrent()

        // Stale server value arrives through a resume refresh while the save is in flight.
        repository.statusQueue += linked(ReminderChannel.Push, username = "renamed")
        vm.onIntent(TelegramIntent.ScreenPaused)
        advanceTimeBy(TelegramViewModel.STATUS_FRESH_MS + 1)
        vm.onIntent(TelegramIntent.ScreenResumed)
        runCurrent()
        assertEquals("renamed", vm.linkedState().username)
        assertEquals(ReminderChannel.Telegram, vm.linkedState().channelSaving)

        g.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(ReminderChannel.Telegram, vm.linkedState().reminderChannel)
        vm.onIntent(TelegramIntent.ScreenPaused)
        runCurrent()
    }

    @Test
    fun refreshWhenIdle_picksUpAChangeMadeInTheBot() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Push)
        repository.statusQueue += linked(ReminderChannel.Both)
        vm.onIntent(TelegramIntent.ScreenPaused)
        advanceTimeBy(TelegramViewModel.STATUS_FRESH_MS + 1)
        vm.onIntent(TelegramIntent.ScreenResumed)
        runCurrent()
        assertEquals(ReminderChannel.Both, vm.linkedState().reminderChannel)
        vm.onIntent(TelegramIntent.ScreenPaused)
        runCurrent()
    }

    @Test
    fun disconnecting_disablesTheGroup_tapsSendNothing() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Telegram)
        repository.unlinkGate = CompletableDeferred()
        vm.onIntent(TelegramIntent.DisconnectConfirmed)
        runCurrent()
        vm.onIntent(TelegramIntent.ReminderChannelClicked(ReminderChannel.Push))
        runCurrent()
        assertTrue(repository.channelRequests.isEmpty())
        repository.unlinkGate!!.complete(TelegramAccountResult.Success(Unit))
        runCurrent()
        vm.onIntent(TelegramIntent.ScreenPaused)
        runCurrent()
    }

    // ── Web ─────────────────────────────────────────────────────────────────

    @Test
    fun web_onFromPush_savesBoth_offSavesPush_selectedRowIsNoOp() = runTest(dispatcher) {
        val (vm, _) = openLinked(ReminderChannel.Push)
        vm.onIntent(TelegramIntent.WebReminderChannelClicked(on = false)) // already selected
        runCurrent()
        assertTrue(repository.channelRequests.isEmpty())

        vm.onIntent(TelegramIntent.WebReminderChannelClicked(on = true))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Both), repository.channelRequests)

        vm.onIntent(TelegramIntent.WebReminderChannelClicked(on = false))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Both, ReminderChannel.Push), repository.channelRequests)
    }

    @Test
    fun webTarget_mapping() {
        assertEquals(ReminderChannel.Both, webReminderTarget(ReminderChannel.Push, on = true))
        assertEquals(null, webReminderTarget(ReminderChannel.Telegram, on = true))
        assertEquals(null, webReminderTarget(ReminderChannel.Both, on = true))
        assertEquals(ReminderChannel.Push, webReminderTarget(ReminderChannel.Telegram, on = false))
        assertEquals(ReminderChannel.Push, webReminderTarget(ReminderChannel.Both, on = false))
        assertEquals(null, webReminderTarget(ReminderChannel.Push, on = false))
    }

    @Test
    fun webDisplay_telegramShowsAsOn() {
        assertTrue(ReminderChannel.Telegram.isWebOn())
        assertTrue(ReminderChannel.Both.isWebOn())
        assertFalse(ReminderChannel.Push.isWebOn())
    }
}
