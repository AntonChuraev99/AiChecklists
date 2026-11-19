package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * Handles ONE bot→app sign-in link (`?tg_link=<token>`): wait for Google sign-in, `claim`, feedback.
 *
 * Extracted from App.kt's `LaunchedEffect` so the flow is unit-testable (same reason as
 * `handleGalleryDeepLink`). App.kt supplies the UI effects as lambdas.
 *
 * - Not signed in → [openTelegramScreen] first: its SignedOut state already says "Sign in to connect
 *   Telegram". The claim then waits for [authPhase] to reach SignedIn (suspends, no polling).
 * - Success → `telegram_link_completed {source: web_link}`, [PendingTelegramLink.notifyLinkChanged]
 *   (an open screen refreshes), the Telegram screen, snackbar `Linked`.
 * - 410 / 404 → `LinkExpired`; anything else → `LinkFailed`. Never a silent outcome.
 *
 * [awaitAppShell] suspends until the app shell's root route is on the back stack. Every
 * [openTelegramScreen] waits for it — the claim itself does not.
 *
 * The token is consumed in `finally` only when the coroutine is still active: an Activity recreation
 * cancels the collector mid-claim and the next collector must retry, not lose the link.
 */
internal suspend fun handleTelegramLinkClaim(
    token: String,
    pending: PendingTelegramLink,
    authPhase: Flow<TelegramAuthPhase>,
    repository: TelegramLinkRepository,
    analyticsTracker: AnalyticsTracker,
    awaitAppShell: suspend () -> Unit,
    openTelegramScreen: () -> Unit,
    showSnackbar: suspend (TelegramSnackbar) -> Unit,
) {
    // Every push waits for the app shell: on a cold start the stack is [Splash] (or onboarding), and
    // splash finishes with navigateToMainScreen(clearBackStack = true) → replaceStack, which wipes a
    // route pushed before it. Live bug 2026-11-19: `?tg_link` on web landed on Inbox, not Telegram.
    suspend fun openWhenShellIsUp() {
        awaitAppShell()
        openTelegramScreen()
    }
    try {
        if (authPhase.first() != TelegramAuthPhase.SignedIn) {
            openWhenShellIsUp()
            authPhase.first { it == TelegramAuthPhase.SignedIn }
        }
        val result = repository.claim(token)
        // Consume BEFORE the snackbar: showSnackbar suspends until dismissed, and a recreation in
        // that window would otherwise re-claim a spent token and report "expired" after a success.
        pending.consume(token)
        when (result) {
            is TelegramAccountResult.Success -> {
                analyticsTracker.event(
                    AnalyticsEvents.Telegram.LINK_COMPLETED,
                    mapOf(AnalyticsParams.SOURCE to AnalyticsEvents.Telegram.SOURCE_WEB_LINK),
                )
                pending.notifyLinkChanged()
                openWhenShellIsUp()
                showSnackbar(TelegramSnackbar.Linked(result.value.username))
            }
            is TelegramAccountResult.Failure -> showSnackbar(
                when (result.error) {
                    TelegramAccountError.TokenExpired, TelegramAccountError.TokenUnknown ->
                        TelegramSnackbar.LinkExpired
                    else -> TelegramSnackbar.LinkFailed
                },
            )
        }
    } finally {
        if (currentCoroutineContext().isActive) pending.consume(token)
    }
}
