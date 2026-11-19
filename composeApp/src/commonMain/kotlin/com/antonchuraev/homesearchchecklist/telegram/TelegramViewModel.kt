package com.antonchuraev.homesearchchecklist.telegram

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkToken
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Telegram account-link screen (`docs/designs/telegram-screen-2026-11-19/spec.md`).
 *
 * ## Pre-minted link token (web popup blocker)
 * On the web `window.open` after an `await` is blocked as a non-gesture popup. So a token is created
 * AHEAD of the tap — as soon as NotLinked / Pending is on screen and visible — and re-created
 * [TOKEN_REUSE_MARGIN_MS] before it expires. The host reads [readyDeepLink] synchronously inside the
 * tap handler and opens it there. Only when no token is ready does the VM create one and emit
 * [TelegramSideEffect.OpenUrl] (fine on Android; on the web Pending's "Open Telegram" then has a
 * ready token for the next tap).
 *
 * ## Pending poll
 * While Pending is visible, `status` every [POLL_INTERVAL_MS] until [POLL_WINDOW_MS] after the last
 * open; plus one check on every resume. Pause stops the poll and token refresh.
 *
 * ## Background refresh never clobbers user work
 * Resume / poll / `linkChanged` refreshes are silent (no Loading, no error snackbar) and are skipped
 * while `unlink` runs. The Disconnect dialog lives in the UI, so replacing an equal Linked state
 * does not close it.
 *
 * Koin creates this VM without a nav-entry decorator, so it outlives a single visit — every visit
 * starts with [TelegramIntent.ScreenEntered], which resets to Loading → status (spec §4.4).
 */
class TelegramViewModel(
    private val repository: TelegramLinkRepository,
    private val authPhase: Flow<TelegramAuthPhase>,
    private val linkChanged: Flow<Unit>,
    private val analyticsTracker: AnalyticsTracker,
    private val nowMs: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : AppViewModel<TelegramScreenState, TelegramIntent, TelegramSideEffect>() {

    private val _screenState = MutableStateFlow<TelegramScreenState>(TelegramScreenState.Loading)
    override val screenState: StateFlow<TelegramScreenState> = _screenState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<TelegramSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<TelegramSideEffect> = _sideEffect.asSharedFlow()

    private var phase: TelegramAuthPhase? = null
    private var entered = false
    private var visible = false

    private var token: TelegramLinkToken? = null
    private var tokenExpiresAtMs = 0L
    private var mintJob: Deferred<TelegramAccountResult<TelegramLinkToken>>? = null
    private var tokenRefreshJob: Job? = null

    private var statusJob: Job? = null
    private var lastStatusAtMs = Long.MIN_VALUE / 2

    private var pollJob: Job? = null
    private var pollDeadlineMs = 0L

    private var unlinkJob: Job? = null

    init {
        viewModelScope.launch { authPhase.collect(::onAuthPhase) }
        viewModelScope.launch { linkChanged.collect { onLinkChangedOutside() } }
    }

    /**
     * The deep link the host may open synchronously inside a tap, or null when there is no token
     * with more than [TOKEN_REUSE_MARGIN_MS] left. Read on the main thread from the click handler.
     */
    fun readyDeepLink(): String? {
        val current = token ?: return null
        return if (tokenExpiresAtMs - nowMs() > TOKEN_REUSE_MARGIN_MS) current.deepLink else null
    }

    override fun onIntent(intent: TelegramIntent) {
        when (intent) {
            TelegramIntent.ScreenEntered -> onScreenEntered()
            TelegramIntent.ScreenResumed -> onResumed()
            TelegramIntent.ScreenPaused -> onPaused()
            is TelegramIntent.ConnectClicked -> onConnect(intent.linkOpenedByHost)
            is TelegramIntent.OpenTelegramClicked -> onReopen(intent.linkOpenedByHost)
            is TelegramIntent.UrlOpened -> onUrlOpened(intent.purpose, intent.opened)
            TelegramIntent.CheckAgainClicked -> onCheckAgain()
            TelegramIntent.DisconnectConfirmed -> onDisconnect()
            TelegramIntent.RetryClicked -> if (_screenState.value is TelegramScreenState.Error) loadStatus(showLoading = true)
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    private fun onAuthPhase(newPhase: TelegramAuthPhase) {
        val previous = phase
        phase = newPhase
        when (newPhase) {
            // Before the first entry the status load belongs to ScreenEntered (one call, not two);
            // after it, signing in on the screen loads at once.
            TelegramAuthPhase.SignedIn ->
                if (previous != TelegramAuthPhase.SignedIn && entered) loadStatus(showLoading = true)
            TelegramAuthPhase.SignedOut, TelegramAuthPhase.SigningIn -> {
                cancelAccountWork()
                _screenState.value = TelegramScreenState.SignedOut(isSigningIn = newPhase == TelegramAuthPhase.SigningIn)
            }
        }
    }

    /** Drops everything bound to the previous account: a token minted for it must never be opened. */
    private fun cancelAccountWork() {
        statusJob?.cancel()
        pollJob?.cancel()
        tokenRefreshJob?.cancel()
        mintJob?.cancel()
        mintJob = null
        unlinkJob?.cancel()
        token = null
        tokenExpiresAtMs = 0L
    }

    private fun goSignedOut() {
        cancelAccountWork()
        _screenState.value = TelegramScreenState.SignedOut()
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    private fun onScreenEntered() {
        analyticsTracker.event(AnalyticsEvents.Telegram.SCREEN_OPENED)
        entered = true
        pollJob?.cancel()
        if (phase == TelegramAuthPhase.SignedIn) {
            if (unlinkJob?.isActive == true) return // an unlink from the previous visit is still landing
            loadStatus(showLoading = true)
        }
    }

    private fun onResumed() {
        visible = true
        if (phase != TelegramAuthPhase.SignedIn) return
        when (val state = _screenState.value) {
            is TelegramScreenState.Pending -> {
                checkPending(manual = false)
                startPolling(resetDeadline = false)
                ensureTokenFresh()
            }
            is TelegramScreenState.NotLinked -> {
                ensureTokenFresh()
                refreshSilently()
            }
            is TelegramScreenState.Linked -> if (!state.isDisconnecting) refreshSilently()
            else -> Unit // Loading is already fetching; Error waits for Retry; SignedOut waits for auth.
        }
    }

    private fun onPaused() {
        visible = false
        pollJob?.cancel()
        tokenRefreshJob?.cancel()
    }

    private fun onLinkChangedOutside() {
        if (phase != TelegramAuthPhase.SignedIn) return
        val state = _screenState.value
        if (state is TelegramScreenState.Linked && state.isDisconnecting) return
        if (state is TelegramScreenState.Loading) return
        // A claim happened elsewhere; the claim handler already announced it — no snackbar here.
        statusJob?.cancel()
        statusJob = viewModelScope.launch { applyStatusResult(repository.status(), announce = false, showErrors = false) }
    }

    // ── Status ──────────────────────────────────────────────────────────────

    private fun loadStatus(showLoading: Boolean) {
        statusJob?.cancel()
        if (showLoading) _screenState.value = TelegramScreenState.Loading
        statusJob = viewModelScope.launch {
            applyStatusResult(repository.status(), announce = false, showErrors = showLoading)
        }
    }

    private fun refreshSilently() {
        if (statusJob?.isActive == true) return
        if (nowMs() - lastStatusAtMs < STATUS_FRESH_MS) return
        statusJob = viewModelScope.launch {
            applyStatusResult(repository.status(), announce = false, showErrors = false)
        }
    }

    /**
     * Applies a `status` reply. [announce] = this is a Pending check, so Pending → Linked shows the
     * success snackbar and counts `telegram_link_completed {source: screen}`.
     */
    private fun applyStatusResult(
        result: TelegramAccountResult<TelegramLinkStatus>,
        announce: Boolean,
        showErrors: Boolean,
    ) {
        val current = _screenState.value
        // An unlink started after this request went out — its reply is stale.
        if (current is TelegramScreenState.Linked && current.isDisconnecting) return
        if (current is TelegramScreenState.SignedOut) return
        when (result) {
            is TelegramAccountResult.Success -> {
                lastStatusAtMs = nowMs()
                when (val status = result.value) {
                    is TelegramLinkStatus.Linked -> {
                        val wasPending = current is TelegramScreenState.Pending
                        pollJob?.cancel()
                        _screenState.value = status.toState()
                        if (announce && wasPending) {
                            analyticsTracker.event(
                                AnalyticsEvents.Telegram.LINK_COMPLETED,
                                mapOf(AnalyticsParams.SOURCE to AnalyticsEvents.Telegram.SOURCE_SCREEN),
                            )
                            emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.Linked(status.username)))
                        }
                    }
                    TelegramLinkStatus.NotLinked -> {
                        // Pending stays Pending until the link lands (or the user leaves the screen).
                        if (current !is TelegramScreenState.Pending && current !is TelegramScreenState.NotLinked) {
                            _screenState.value = TelegramScreenState.NotLinked()
                        }
                        ensureTokenFresh()
                    }
                }
            }
            is TelegramAccountResult.Failure -> when (result.error) {
                TelegramAccountError.NotSignedIn -> goSignedOut()
                else -> if (showErrors) _screenState.value = TelegramScreenState.Error(result.error.toReason())
                // Silent refresh: the repository logged it; the current screen stays usable.
            }
        }
    }

    // ── Token ───────────────────────────────────────────────────────────────

    /** Keeps a ready token while NotLinked / Pending is visible. Failures stay silent (pre-fetch). */
    private fun ensureTokenFresh() {
        if (!visible || phase != TelegramAuthPhase.SignedIn) return
        val state = _screenState.value
        if (state !is TelegramScreenState.NotLinked && state !is TelegramScreenState.Pending) return
        if (readyDeepLink() != null) {
            scheduleTokenRefresh()
            return
        }
        startMint() // eager async; a running mint is reused, not duplicated
    }

    private fun startMint(): Deferred<TelegramAccountResult<TelegramLinkToken>> {
        mintJob?.takeIf { it.isActive }?.let { return it }
        val job = viewModelScope.async {
            val result = repository.createToken()
            if (result is TelegramAccountResult.Success) {
                token = result.value
                tokenExpiresAtMs = nowMs() + result.value.expiresInSec * 1_000L
                scheduleTokenRefresh()
            }
            result
        }
        mintJob = job
        return job
    }

    private fun scheduleTokenRefresh() {
        tokenRefreshJob?.cancel()
        if (!visible) return
        val waitMs = (tokenExpiresAtMs - TOKEN_REUSE_MARGIN_MS - nowMs()).coerceAtLeast(0L)
        tokenRefreshJob = viewModelScope.launch {
            delay(waitMs)
            ensureTokenFresh()
        }
    }

    // ── Connect / reopen ────────────────────────────────────────────────────

    private fun onConnect(linkOpenedByHost: Boolean) {
        val state = _screenState.value as? TelegramScreenState.NotLinked ?: return
        if (state.isConnecting) return // double tap while the token is created — the button spins
        analyticsTracker.event(AnalyticsEvents.Telegram.CONNECT_TAPPED)
        if (linkOpenedByHost) return // the host opened readyDeepLink() and reports UrlOpened(Connect)
        _screenState.value = TelegramScreenState.NotLinked(isConnecting = true)
        viewModelScope.launch {
            val result = startMint().await()
            if (_screenState.value != TelegramScreenState.NotLinked(isConnecting = true)) return@launch
            when (result) {
                is TelegramAccountResult.Success -> {
                    // Stays "connecting" until the host reports the open outcome.
                    emit(TelegramSideEffect.OpenUrl(result.value.deepLink, TelegramUrlPurpose.Connect))
                }
                is TelegramAccountResult.Failure -> {
                    if (result.error == TelegramAccountError.NotSignedIn) {
                        goSignedOut()
                    } else {
                        _screenState.value = TelegramScreenState.NotLinked()
                        emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.ConnectFailed(result.error.toReason())))
                    }
                }
            }
        }
    }

    private fun onReopen(linkOpenedByHost: Boolean) {
        val state = _screenState.value as? TelegramScreenState.Pending ?: return
        if (state.isOpening) return
        if (linkOpenedByHost) return
        _screenState.value = state.copy(isOpening = true)
        viewModelScope.launch {
            val result = startMint().await()
            val now = _screenState.value as? TelegramScreenState.Pending ?: return@launch
            _screenState.value = now.copy(isOpening = false)
            when (result) {
                is TelegramAccountResult.Success ->
                    emit(TelegramSideEffect.OpenUrl(result.value.deepLink, TelegramUrlPurpose.Reopen))
                is TelegramAccountResult.Failure ->
                    if (result.error == TelegramAccountError.NotSignedIn) {
                        goSignedOut()
                    } else {
                        emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.ConnectFailed(result.error.toReason())))
                    }
            }
        }
    }

    private fun onUrlOpened(purpose: TelegramUrlPurpose, opened: Boolean) {
        when (purpose) {
            TelegramUrlPurpose.Connect -> {
                if (_screenState.value !is TelegramScreenState.NotLinked) return
                if (opened) {
                    _screenState.value = TelegramScreenState.Pending()
                    startPolling(resetDeadline = true)
                    ensureTokenFresh()
                } else {
                    _screenState.value = TelegramScreenState.NotLinked()
                    emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.OpenFailed))
                }
            }
            TelegramUrlPurpose.Reopen ->
                if (opened) startPolling(resetDeadline = true)
                else emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.OpenFailed))
            TelegramUrlPurpose.Bot ->
                if (!opened) emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.OpenFailed))
        }
    }

    // ── Pending checks ──────────────────────────────────────────────────────

    private fun startPolling(resetDeadline: Boolean) {
        if (resetDeadline) pollDeadlineMs = nowMs() + POLL_WINDOW_MS
        pollJob?.cancel()
        if (!visible) return
        pollJob = viewModelScope.launch {
            while (nowMs() + POLL_INTERVAL_MS <= pollDeadlineMs) {
                delay(POLL_INTERVAL_MS)
                if (_screenState.value !is TelegramScreenState.Pending) break
                applyStatusResult(repository.status(), announce = true, showErrors = false)
            }
        }
    }

    private fun checkPending(manual: Boolean) {
        viewModelScope.launch {
            val result = repository.status()
            applyStatusResult(result, announce = true, showErrors = false)
            if (!manual) return@launch
            val state = _screenState.value as? TelegramScreenState.Pending
            if (state != null) _screenState.value = state.copy(isChecking = false)
            when {
                result is TelegramAccountResult.Success && result.value == TelegramLinkStatus.NotLinked ->
                    emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.NotYet))
                result is TelegramAccountResult.Failure && result.error != TelegramAccountError.NotSignedIn ->
                    emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.RequestFailed(result.error.toReason())))
            }
        }
    }

    private fun onCheckAgain() {
        val state = _screenState.value as? TelegramScreenState.Pending ?: return
        if (state.isChecking) return
        _screenState.value = state.copy(isChecking = true)
        checkPending(manual = true)
    }

    // ── Disconnect ──────────────────────────────────────────────────────────

    private fun onDisconnect() {
        val state = _screenState.value as? TelegramScreenState.Linked ?: return
        if (state.isDisconnecting) return
        statusJob?.cancel() // an older status reply must not land after the unlink
        _screenState.value = state.copy(isDisconnecting = true)
        unlinkJob = viewModelScope.launch {
            when (val result = repository.unlink()) {
                is TelegramAccountResult.Success -> {
                    analyticsTracker.event(AnalyticsEvents.Telegram.UNLINKED)
                    _screenState.value = TelegramScreenState.NotLinked()
                    emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.Disconnected))
                    ensureTokenFresh()
                }
                is TelegramAccountResult.Failure ->
                    if (result.error == TelegramAccountError.NotSignedIn) {
                        goSignedOut()
                    } else {
                        val now = _screenState.value as? TelegramScreenState.Linked
                        _screenState.value = (now ?: state).copy(isDisconnecting = false)
                        emit(TelegramSideEffect.ShowSnackbar(TelegramSnackbar.DisconnectFailed))
                    }
            }
        }
    }

    private fun emit(effect: TelegramSideEffect) {
        _sideEffect.tryEmit(effect)
    }

    private fun TelegramLinkStatus.Linked.toState() = TelegramScreenState.Linked(
        username = username,
        firstName = firstName,
        linkedAtMs = linkedAtMs,
    )

    companion object {
        /** Spec §4.4: reuse a token only while more than 60 s remain. */
        const val TOKEN_REUSE_MARGIN_MS = 60_000L
        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_WINDOW_MS = 120_000L

        /** A resume right after a fresh `status` (entry → first resume) skips the duplicate call. */
        const val STATUS_FRESH_MS = 5_000L
    }
}
