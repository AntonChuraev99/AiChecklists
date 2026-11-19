package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramLinkClaimHandlerTest {

    private val repository = FakeTelegramLinkRepository()
    private val analytics = RecordingTelegramAnalytics()
    private val pending = PendingTelegramLink()
    private val snackbars = mutableListOf<TelegramSnackbar>()
    private var screenOpens = 0

    /** Completed by default = the shell is already up (warm link). Cold-start tests replace it. */
    private var shellReady = CompletableDeferred<Unit>().apply { complete(Unit) }

    private suspend fun handle(token: String, phase: MutableStateFlow<TelegramAuthPhase>) =
        handleTelegramLinkClaim(
            token = token,
            pending = pending,
            authPhase = phase,
            repository = repository,
            analyticsTracker = analytics,
            awaitAppShell = { shellReady.await() },
            openTelegramScreen = { screenOpens++ },
            showSnackbar = { snackbars += it },
        )

    // Live bug 2026-11-19 (wasmJs, cold start with ?tg_link, signed out): the screen was pushed onto
    // [Splash]; Splash then replaced the whole stack with the shell root and the Telegram route was
    // wiped — the user landed on Inbox. The push must wait until the shell root is on the stack.
    @Test
    fun coldStart_signedOut_opensScreenOnlyAfterShellIsUp() = runTest {
        shellReady = CompletableDeferred()
        val phase = MutableStateFlow(TelegramAuthPhase.SignedOut)
        val job = launch { handle("tok", phase) }
        runCurrent()
        assertEquals(0, screenOpens, "a push onto the splash stack is wiped by splash's replaceStack")

        shellReady.complete(Unit)
        runCurrent()
        assertEquals(1, screenOpens)
        job.cancel()
    }

    @Test
    fun coldStart_signedIn_claimsAtOnce_butOpensScreenOnlyAfterShellIsUp() = runTest {
        shellReady = CompletableDeferred()
        val job = launch { handle("tok", MutableStateFlow(TelegramAuthPhase.SignedIn)) }
        runCurrent()
        assertEquals(listOf("tok"), repository.claimedTokens)
        assertEquals(0, screenOpens)

        shellReady.complete(Unit)
        runCurrent()
        job.join()
        assertEquals(1, screenOpens)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.Linked("anton")), snackbars)
    }

    @Test
    fun signedIn_claimSucceeds_announcesOpensScreenAndConsumes() = runTest {
        pending.submit("tok")
        var linkChanged = 0
        backgroundScope.launch { pending.linkChanged.collect { linkChanged++ } }
        runCurrent()

        handle("tok", MutableStateFlow(TelegramAuthPhase.SignedIn))
        runCurrent()

        assertEquals(listOf("tok"), repository.claimedTokens)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.Linked("anton")), snackbars)
        assertEquals(1, screenOpens)
        assertEquals(1, linkChanged, "an open Telegram screen must refresh")
        assertEquals(
            listOf(mapOf<String, Any>(AnalyticsParams.SOURCE to AnalyticsEvents.Telegram.SOURCE_WEB_LINK)),
            analytics.params(AnalyticsEvents.Telegram.LINK_COMPLETED),
        )
        assertNull(pending.pending.value)
    }

    @Test
    fun signedOut_opensScreenFirst_thenClaimsAfterSignIn() = runTest {
        val phase = MutableStateFlow(TelegramAuthPhase.SignedOut)
        pending.submit("tok")
        val job = launch { handle("tok", phase) }
        runCurrent()
        assertEquals(1, screenOpens, "the SignedOut screen explains what to do")
        assertTrue(repository.claimedTokens.isEmpty(), "no claim before sign-in")

        phase.value = TelegramAuthPhase.SigningIn
        runCurrent()
        assertTrue(repository.claimedTokens.isEmpty())

        phase.value = TelegramAuthPhase.SignedIn
        runCurrent()
        job.join()
        assertEquals(listOf("tok"), repository.claimedTokens)
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.Linked("anton")), snackbars)
    }

    @Test
    fun spentOrUnknownToken_showsLinkExpired() = runTest {
        repository.claimResult = TelegramAccountResult.Failure(TelegramAccountError.TokenExpired)
        handle("a", MutableStateFlow(TelegramAuthPhase.SignedIn))
        repository.claimResult = TelegramAccountResult.Failure(TelegramAccountError.TokenUnknown)
        handle("b", MutableStateFlow(TelegramAuthPhase.SignedIn))
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.LinkExpired, TelegramSnackbar.LinkExpired), snackbars)
        assertEquals(0, analytics.count(AnalyticsEvents.Telegram.LINK_COMPLETED))
        assertEquals(0, screenOpens)
    }

    @Test
    fun networkOrServerFailure_showsLinkFailed() = runTest {
        repository.claimResult = TelegramAccountResult.Failure(TelegramAccountError.Network(RuntimeException("x")))
        handle("a", MutableStateFlow(TelegramAuthPhase.SignedIn))
        repository.claimResult = TelegramAccountResult.Failure(TelegramAccountError.Server(500, null))
        handle("b", MutableStateFlow(TelegramAuthPhase.SignedIn))
        assertEquals(listOf<TelegramSnackbar>(TelegramSnackbar.LinkFailed, TelegramSnackbar.LinkFailed), snackbars)
    }

    @Test
    fun consume_keepsANewerToken() = runTest {
        pending.submit("old")
        pending.submit("new")
        handle("old", MutableStateFlow(TelegramAuthPhase.SignedIn))
        assertEquals("new", pending.pending.value)
    }

    @Test
    fun authPhase_requiresGoogleLinkNotJustFirebase() {
        val user = com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState.Authenticated(
            com.antonchuraev.homesearchchecklist.core.auth.api.GoogleUser(
                firebaseUid = "u",
                email = "e@x",
                displayName = "U",
            ),
        )
        assertEquals(TelegramAuthPhase.SignedIn, telegramAuthPhase(user, isGoogleLinked = true))
        assertEquals(TelegramAuthPhase.SignedOut, telegramAuthPhase(user, isGoogleLinked = false))
        assertEquals(
            TelegramAuthPhase.SigningIn,
            telegramAuthPhase(com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState.Loading, false),
        )
        assertEquals(
            TelegramAuthPhase.SignedOut,
            telegramAuthPhase(com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState.NotAuthenticated, true),
        )
    }
}
