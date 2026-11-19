package com.antonchuraev.homesearchchecklist.telegram

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    private val repository = FakeTelegramLinkRepository()
    private val analytics = RecordingTelegramAnalytics()
    private val authPhase = MutableStateFlow(TelegramAuthPhase.SignedIn)
    private val linkChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    /**
     * Owns every VM of the test. Cleared BEFORE resetMain: a VM left alive keeps viewModelScope
     * continuations bound to Dispatchers.Main, and once Main is reset they leak into whatever test
     * runs next in this JVM (UncaughtExceptionsBeforeTest / TestMainDispatcher ISE elsewhere).
     */
    private val store = ViewModelStore()

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun newViewModel(): TelegramViewModel = ViewModelProvider.create(
        store,
        viewModelFactory {
            initializer {
                TelegramViewModel(
                    repository = repository,
                    authPhase = authPhase,
                    linkChanged = linkChanged,
                    analyticsTracker = analytics,
                    nowMs = { scheduler.currentTime },
                )
            }
        },
    )[TelegramViewModel::class]

    /** VM + recorded side effects; the VM is visible (entered + resumed) and settled. */
    private fun TestScope.openScreen(): Pair<TelegramViewModel, MutableList<TelegramSideEffect>> {
        val vm = newViewModel()
        val effects = mutableListOf<TelegramSideEffect>()
        backgroundScope.launch { vm.sideEffect.collect { effects += it } }
        runCurrent()
        vm.onIntent(TelegramIntent.ScreenEntered)
        vm.onIntent(TelegramIntent.ScreenResumed)
        runCurrent()
        return vm to effects
    }

    /**
     * The VM keeps re-minting its token while visible, so the scheduler never idles. Every test
     * ends by pausing the screen, which stops the poll and the token refresh.
     */
    private fun TestScope.closeScreen(vm: TelegramViewModel) {
        vm.onIntent(TelegramIntent.ScreenPaused)
        runCurrent()
    }

    private fun linked(username: String? = "anton") =
        TelegramAccountResult.Success(TelegramLinkStatus.Linked(username, "Anton", 1_000L))

    private fun snackbars(effects: List<TelegramSideEffect>) =
        effects.filterIsInstance<TelegramSideEffect.ShowSnackbar>().map { it.message }

    // ── Auth ────────────────────────────────────────────────────────────────

    @Test
    fun signedOut_showsSignedOut_andSigningInFlagFollowsAuth() = runTest(dispatcher) {
        authPhase.value = TelegramAuthPhase.SignedOut
        val (vm, _) = openScreen()
        assertEquals(TelegramScreenState.SignedOut(isSigningIn = false), vm.screenState.value)
        assertEquals(0, repository.statusCalls)

        authPhase.value = TelegramAuthPhase.SigningIn
        runCurrent()
        assertEquals(TelegramScreenState.SignedOut(isSigningIn = true), vm.screenState.value)

        // Sign-in completes → Loading → status → NotLinked.
        authPhase.value = TelegramAuthPhase.SignedIn
        runCurrent()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        closeScreen(vm)
    }

    @Test
    fun statusWithoutIdToken_goesSignedOut() = runTest(dispatcher) {
        repository.defaultStatus = TelegramAccountResult.Failure(TelegramAccountError.NotSignedIn)
        val (vm, _) = openScreen()
        assertEquals(TelegramScreenState.SignedOut(), vm.screenState.value)
        closeScreen(vm)
    }

    // ── Entry, error, retry ─────────────────────────────────────────────────

    @Test
    fun entry_loadsStatus_andCountsScreenOpened() = runTest(dispatcher) {
        repository.statusQueue += linked()
        val (vm, _) = openScreen()
        assertEquals(TelegramScreenState.Linked("anton", "Anton", 1_000L), vm.screenState.value)
        assertEquals(1, analytics.count(AnalyticsEvents.Telegram.SCREEN_OPENED))
        // Entry + the resume right after it make ONE status call, not two.
        assertEquals(1, repository.statusCalls)
        closeScreen(vm)
    }

    @Test
    fun statusFailure_showsErrorByReason_andRetryReloads() = runTest(dispatcher) {
        repository.statusQueue += TelegramAccountResult.Failure(TelegramAccountError.NoAccount)
        repository.statusQueue += linked()
        val (vm, _) = openScreen()
        assertEquals(TelegramScreenState.Error(TelegramErrorReason.Account), vm.screenState.value)

        vm.onIntent(TelegramIntent.RetryClicked)
        assertEquals(TelegramScreenState.Loading, vm.screenState.value)
        runCurrent()
        assertTrue(vm.screenState.value is TelegramScreenState.Linked)
        closeScreen(vm)
    }

    @Test
    fun networkAndServerErrors_mapToTheirReasons() = runTest(dispatcher) {
        repository.statusQueue += TelegramAccountResult.Failure(TelegramAccountError.Network(RuntimeException("offline")))
        val (vm, _) = openScreen()
        assertEquals(TelegramScreenState.Error(TelegramErrorReason.Network), vm.screenState.value)

        repository.statusQueue += TelegramAccountResult.Failure(TelegramAccountError.Server(500, "internal"))
        vm.onIntent(TelegramIntent.RetryClicked)
        runCurrent()
        assertEquals(TelegramScreenState.Error(TelegramErrorReason.Server), vm.screenState.value)
        closeScreen(vm)
    }

    // ── Pre-minted token ────────────────────────────────────────────────────

    @Test
    fun notLinked_preMintsToken_beforeAnyTap() = runTest(dispatcher) {
        val (vm, _) = openScreen()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        assertEquals(1, repository.createTokenCalls)
        assertEquals("https://t.me/gisti_app_bot?start=t1", vm.readyDeepLink())
        closeScreen(vm)
    }

    @Test
    fun token_isReMintedSixtySecondsBeforeExpiry_whileVisible() = runTest(dispatcher) {
        val (vm, _) = openScreen()
        // 600 s token, reuse margin 60 s → the refresh fires at 540 s.
        advanceTimeBy(539_000)
        runCurrent()
        assertEquals(1, repository.createTokenCalls)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, repository.createTokenCalls)
        assertEquals("https://t.me/gisti_app_bot?start=t2", vm.readyDeepLink())
        closeScreen(vm)
    }

    @Test
    fun paused_stopsTokenRefresh_andStaleTokenIsNotReady() = runTest(dispatcher) {
        val (vm, _) = openScreen()
        closeScreen(vm)
        advanceTimeBy(541_000)
        runCurrent()
        assertEquals(1, repository.createTokenCalls, "no re-mint while the screen is not visible")
        assertNull(vm.readyDeepLink(), "under 60 s left → the host must not open it")

        // Back on screen → a fresh token is minted at once.
        vm.onIntent(TelegramIntent.ScreenResumed)
        runCurrent()
        assertEquals(2, repository.createTokenCalls)
        assertNotNull(vm.readyDeepLink())
        closeScreen(vm)
    }

    // ── Connect ─────────────────────────────────────────────────────────────

    @Test
    fun connect_withReadyLink_hostOpens_goesPending_andCountsTap() = runTest(dispatcher) {
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = true))
        vm.onIntent(TelegramIntent.UrlOpened(TelegramUrlPurpose.Connect, opened = true))
        runCurrent()
        assertEquals(TelegramScreenState.Pending(), vm.screenState.value)
        assertEquals(1, analytics.count(AnalyticsEvents.Telegram.CONNECT_TAPPED))
        assertTrue(effects.none { it is TelegramSideEffect.OpenUrl }, "the host already opened it")
        assertEquals(1, repository.createTokenCalls, "the pre-minted token was used")
        closeScreen(vm)
    }

    @Test
    fun connect_hostCouldNotOpen_staysNotLinked_withOpenFailedSnackbar() = runTest(dispatcher) {
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = true))
        vm.onIntent(TelegramIntent.UrlOpened(TelegramUrlPurpose.Connect, opened = false))
        runCurrent()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.OpenFailed), snackbars(effects))
        closeScreen(vm)
    }

    @Test
    fun connect_withoutReadyToken_mintsThenEmitsOpenUrl() = runTest(dispatcher) {
        repository.tokenResult = { TelegramAccountResult.Failure(TelegramAccountError.Network(RuntimeException("x"))) }
        val (vm, effects) = openScreen()
        assertNull(vm.readyDeepLink(), "pre-mint failed silently")
        assertTrue(effects.isEmpty(), "a failed pre-fetch is not the user's action — no snackbar")

        repository.tokenResult = null
        vm.onIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = false))
        assertEquals(TelegramScreenState.NotLinked(isConnecting = true), vm.screenState.value)
        runCurrent()
        val open = effects.filterIsInstance<TelegramSideEffect.OpenUrl>().single()
        assertEquals(TelegramUrlPurpose.Connect, open.purpose)
        assertTrue(open.url.startsWith("https://t.me/gisti_app_bot?start="))
        // Double tap while connecting is swallowed (the button spins).
        vm.onIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = false))
        assertEquals(1, analytics.count(AnalyticsEvents.Telegram.CONNECT_TAPPED))

        vm.onIntent(TelegramIntent.UrlOpened(TelegramUrlPurpose.Connect, opened = true))
        runCurrent()
        assertEquals(TelegramScreenState.Pending(), vm.screenState.value)
        closeScreen(vm)
    }

    @Test
    fun connect_tokenCreationFails_snackbarWithReason_staysNotLinked() = runTest(dispatcher) {
        repository.tokenResult = { TelegramAccountResult.Failure(TelegramAccountError.NoAccount) }
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = false))
        runCurrent()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        assertEquals(
            listOf<TelegramSnackbar>(TelegramSnackbar.ConnectFailed(TelegramErrorReason.Account)),
            snackbars(effects),
        )
        closeScreen(vm)
    }

    // ── Pending ─────────────────────────────────────────────────────────────

    private fun TestScope.pendingScreen(): Pair<TelegramViewModel, MutableList<TelegramSideEffect>> {
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.ConnectClicked(linkOpenedByHost = true))
        vm.onIntent(TelegramIntent.UrlOpened(TelegramUrlPurpose.Connect, opened = true))
        runCurrent()
        return vm to effects
    }

    @Test
    fun pending_pollsEveryThreeSeconds_andAnnouncesLink() = runTest(dispatcher) {
        val (vm, effects) = pendingScreen()
        val before = repository.statusCalls
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(before + 1, repository.statusCalls)

        repository.statusQueue += linked("anton")
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(TelegramScreenState.Linked("anton", "Anton", 1_000L), vm.screenState.value)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.Linked("anton")), snackbars(effects))
        assertEquals(
            listOf(mapOf<String, Any>(AnalyticsParams.SOURCE to AnalyticsEvents.Telegram.SOURCE_SCREEN)),
            analytics.params(AnalyticsEvents.Telegram.LINK_COMPLETED),
        )
        // Linked stops the poll.
        val afterLink = repository.statusCalls
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(afterLink, repository.statusCalls)
        closeScreen(vm)
    }

    @Test
    fun pending_pollStopsAfterTwoMinutes() = runTest(dispatcher) {
        val (vm, _) = pendingScreen()
        val start = repository.statusCalls
        advanceTimeBy(TelegramViewModel.POLL_WINDOW_MS + 1)
        runCurrent()
        val atWindowEnd = repository.statusCalls
        assertEquals(start + (TelegramViewModel.POLL_WINDOW_MS / TelegramViewModel.POLL_INTERVAL_MS).toInt(), atWindowEnd)
        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertEquals(atWindowEnd, repository.statusCalls, "no polling past the window")
        assertEquals(TelegramScreenState.Pending(), vm.screenState.value, "still Pending — Check again remains")
        closeScreen(vm)
    }

    @Test
    fun pending_pausedStopsPoll_resumeChecksOnce() = runTest(dispatcher) {
        val (vm, _) = pendingScreen()
        vm.onIntent(TelegramIntent.ScreenPaused)
        runCurrent()
        val paused = repository.statusCalls
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(paused, repository.statusCalls, "no poll while not visible")

        repository.statusQueue += linked()
        vm.onIntent(TelegramIntent.ScreenResumed)
        runCurrent()
        assertTrue(vm.screenState.value is TelegramScreenState.Linked, "resume check picks up the link")
        closeScreen(vm)
    }

    @Test
    fun checkAgain_notYet_showsSnackbar_andIgnoresDoubleTap() = runTest(dispatcher) {
        val (vm, effects) = pendingScreen()
        val before = repository.statusCalls
        vm.onIntent(TelegramIntent.CheckAgainClicked)
        vm.onIntent(TelegramIntent.CheckAgainClicked)
        assertEquals(TelegramScreenState.Pending(isChecking = true), vm.screenState.value)
        runCurrent()
        assertEquals(before + 1, repository.statusCalls)
        assertEquals(TelegramScreenState.Pending(isChecking = false), vm.screenState.value)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.NotYet), snackbars(effects))
        closeScreen(vm)
    }

    @Test
    fun checkAgain_failure_showsRequestFailed() = runTest(dispatcher) {
        val (vm, effects) = pendingScreen()
        repository.statusQueue += TelegramAccountResult.Failure(TelegramAccountError.Network(RuntimeException("x")))
        vm.onIntent(TelegramIntent.CheckAgainClicked)
        runCurrent()
        assertEquals(
            listOf<TelegramSnackbar>(TelegramSnackbar.RequestFailed(TelegramErrorReason.Network)),
            snackbars(effects),
        )
        assertEquals(TelegramScreenState.Pending(), vm.screenState.value)
        closeScreen(vm)
    }

    @Test
    fun reopen_withoutReadyToken_mintsAndEmitsReopenUrl() = runTest(dispatcher) {
        val (vm, effects) = pendingScreen()
        vm.onIntent(TelegramIntent.ScreenPaused)
        advanceTimeBy(541_000)
        runCurrent()
        assertNull(vm.readyDeepLink())
        vm.onIntent(TelegramIntent.ScreenResumed)
        // Resume re-mints at once; simulate a tap that raced it before the mint landed.
        vm.onIntent(TelegramIntent.OpenTelegramClicked(linkOpenedByHost = false))
        runCurrent()
        val open = effects.filterIsInstance<TelegramSideEffect.OpenUrl>().single()
        assertEquals(TelegramUrlPurpose.Reopen, open.purpose)
        assertEquals(TelegramScreenState.Pending(), vm.screenState.value)
        closeScreen(vm)
    }

    @Test
    fun reEntry_fromPending_startsWithLoading_thenNotLinked() = runTest(dispatcher) {
        val (vm, _) = pendingScreen()
        vm.onIntent(TelegramIntent.ScreenPaused)
        vm.onIntent(TelegramIntent.ScreenEntered)
        assertEquals(TelegramScreenState.Loading, vm.screenState.value)
        runCurrent()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        closeScreen(vm)
    }

    // ── Linked / disconnect ─────────────────────────────────────────────────

    @Test
    fun disconnect_success_goesNotLinked_withSnackbarAndEvent() = runTest(dispatcher) {
        repository.statusQueue += linked()
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.DisconnectConfirmed)
        runCurrent()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.Disconnected), snackbars(effects))
        assertEquals(1, analytics.count(AnalyticsEvents.Telegram.UNLINKED))
        closeScreen(vm)
    }

    @Test
    fun disconnect_failure_reEnablesControls_withSnackbar() = runTest(dispatcher) {
        repository.statusQueue += linked()
        repository.unlinkResult = TelegramAccountResult.Failure(TelegramAccountError.Server(500, null))
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.DisconnectConfirmed)
        runCurrent()
        assertEquals(TelegramScreenState.Linked("anton", "Anton", 1_000L, isDisconnecting = false), vm.screenState.value)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.DisconnectFailed), snackbars(effects))
        assertEquals(0, analytics.count(AnalyticsEvents.Telegram.UNLINKED))
        closeScreen(vm)
    }

    @Test
    fun resumeAndOutsideChange_duringUnlink_doNotTouchState() = runTest(dispatcher) {
        repository.statusQueue += linked()
        val gate = CompletableDeferred<TelegramAccountResult<Unit>>()
        repository.unlinkGate = gate
        val (vm, _) = openScreen()
        vm.onIntent(TelegramIntent.DisconnectConfirmed)
        vm.onIntent(TelegramIntent.DisconnectConfirmed) // double confirm
        runCurrent()
        val callsDuringUnlink = repository.statusCalls

        vm.onIntent(TelegramIntent.ScreenPaused)
        advanceTimeBy(10_000)
        vm.onIntent(TelegramIntent.ScreenResumed)
        linkChanged.tryEmit(Unit)
        runCurrent()
        assertEquals(TelegramScreenState.Linked("anton", "Anton", 1_000L, isDisconnecting = true), vm.screenState.value)
        assertEquals(callsDuringUnlink, repository.statusCalls)
        assertEquals(1, repository.unlinkCalls)

        gate.complete(TelegramAccountResult.Success(Unit))
        runCurrent()
        assertEquals(TelegramScreenState.NotLinked(), vm.screenState.value)
        closeScreen(vm)
    }

    @Test
    fun linkChangedOutside_refreshesToLinked_withoutScreenSnackbarOrEvent() = runTest(dispatcher) {
        val (vm, effects) = openScreen()
        repository.statusQueue += linked()
        linkChanged.tryEmit(Unit)
        runCurrent()
        assertTrue(vm.screenState.value is TelegramScreenState.Linked)
        assertTrue(snackbars(effects).isEmpty(), "the claim handler announces it, not the screen")
        assertEquals(0, analytics.count(AnalyticsEvents.Telegram.LINK_COMPLETED))
        closeScreen(vm)
    }

    @Test
    fun openBotFailure_showsOpenFailed() = runTest(dispatcher) {
        repository.statusQueue += linked()
        val (vm, effects) = openScreen()
        vm.onIntent(TelegramIntent.UrlOpened(TelegramUrlPurpose.Bot, opened = false))
        runCurrent()
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.OpenFailed), snackbars(effects))
        closeScreen(vm)
    }
}
