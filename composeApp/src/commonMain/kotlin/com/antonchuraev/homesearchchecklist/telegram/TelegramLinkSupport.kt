package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthState
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

object TelegramConfig {
    /** "Open bot" target in the Linked state — the bot itself, no token. */
    const val BOT_URL = "https://t.me/gisti_app_bot"

    /** Query parameter of the bot's sign-in link `https://app.gisti-ai.com/?tg_link=<token>`. */
    const val TG_LINK_QUERY_PARAM = "tg_link"
}

/** Whether the app has a Google account the bot can be linked to. */
enum class TelegramAuthPhase { SignedOut, SigningIn, SignedIn }

/**
 * Signed in for Telegram purposes = Firebase has a user AND the app finished `link_google_account`
 * (`isGoogleLinked`, what the drawer reads). Firebase alone is not enough: right after sign-in the
 * credit doc does not carry `google_uid` yet, and `telegram_account` would answer 409 `no_account`.
 */
internal fun telegramAuthPhase(authState: GoogleAuthState, isGoogleLinked: Boolean): TelegramAuthPhase =
    when {
        authState is GoogleAuthState.Authenticated && isGoogleLinked -> TelegramAuthPhase.SignedIn
        authState is GoogleAuthState.Loading -> TelegramAuthPhase.SigningIn
        // Authenticated but not linked: either link_google_account is still running (a moment) or it
        // FAILED (App.kt shows `sign_in_unavailable`). Reporting SigningIn here would leave the
        // Sign-in button spinning forever on the failure path, so it reads as SignedOut — a tap
        // re-runs handleSignIn, which re-links.
        else -> TelegramAuthPhase.SignedOut
    }

internal fun telegramAuthPhaseFlow(
    authRepository: GoogleAuthRepository,
    userDataRepository: UserDataRepository,
): Flow<TelegramAuthPhase> =
    combine(authRepository.authState, userDataRepository.getUserDataFlow()) { auth, user ->
        telegramAuthPhase(auth, user.isGoogleLinked)
    }.distinctUntilChanged()

internal fun TelegramAccountError.toReason(): TelegramErrorReason = when (this) {
    is TelegramAccountError.Network -> TelegramErrorReason.Network
    TelegramAccountError.NoAccount -> TelegramErrorReason.Account
    else -> TelegramErrorReason.Server
}

/**
 * App-scoped hand-off for the bot→app sign-in link `https://app.gisti-ai.com/?tg_link=<token>`
 * (mirrors [com.antonchuraev.homesearchchecklist.deeplink.PendingGalleryDeepLink]).
 *
 * Platform entry points ([submit]): wasmJs `main.kt` at boot, Android `MainActivity` on the App Link.
 * App.kt hands each token to [handleTelegramLinkClaim], which waits for Google sign-in, claims and
 * [consume]s it. A [StateFlow] so a token submitted before the collector mounts is not lost.
 *
 * [linkChanged] tells an open Telegram screen that the link state changed outside it (a claim), so
 * it refreshes even when the route was already on top and no re-entry happens.
 */
class PendingTelegramLink {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val _linkChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val linkChanged: SharedFlow<Unit> = _linkChanged.asSharedFlow()

    /** Latest wins. Blank tokens are ignored by the caller's parser, not here. */
    fun submit(token: String) {
        _pending.value = token
    }

    /** Compare-and-set so a newer token submitted while this one was claimed is not swallowed. */
    fun consume(handled: String) {
        _pending.compareAndSet(expect = handled, update = null)
    }

    fun notifyLinkChanged() {
        _linkChanged.tryEmit(Unit)
    }
}
