package com.antonchuraev.homesearchchecklist.feature.home.presentation.notifications

import kotlin.concurrent.Volatile

/**
 * App-session bookkeeping for the "notifications are off" banner.
 *
 * Session = process start, or a return to the foreground after at least [SESSION_GAP_MS] in the
 * background (no existing app-session notion fits: CSAT counts launches, Amplitude's session is
 * SDK-internal and unreliable in the background, see project memory on phantom sessions).
 *
 * Process-wide Koin singleton: App.kt feeds [onForeground]/[onBackground] from the activity
 * lifecycle; ChecklistDetailViewModel reads the per-session flags. Every flag is stored as the
 * session index it was set in, so rolling the session clears them all at once — nothing to reset.
 */
class NotificationsBannerSession(private val nowMs: () -> Long) {

    @Volatile private var state = ForegroundSessionState(index = 0, backgroundedAtMs = null)
    @Volatile private var dismissedInSession = NO_SESSION
    @Volatile private var suppressedInSession = NO_SESSION
    // Per surface: `notif_banner_shown` carries `screen`, so dedupe is per (session, screen) — a
    // session-wide dedupe left the second surface's CTA/dismiss events without a matching shown.
    // Touched from ViewModel coroutines on the main dispatcher only.
    private val shownSessionByScreen = mutableMapOf<String, Int>()

    val sessionIndex: Int get() = state.index

    fun onForeground() { state = state.onForeground(nowMs()) }
    fun onBackground() { state = state.onBackground(nowMs()) }

    val dismissedThisSession: Boolean get() = dismissedInSession == state.index
    val suppressedThisSession: Boolean get() = suppressedInSession == state.index

    fun markDismissed() { dismissedInSession = state.index }

    /** Hide for the session WITHOUT counting a dismiss (user denied in the activation sheet). */
    fun suppressForSession() { suppressedInSession = state.index }

    /** True exactly once per (session, [screen]) — gates the `notif_banner_shown` event. */
    fun claimShownEvent(screen: String): Boolean {
        if (shownSessionByScreen[screen] == state.index) return false
        shownSessionByScreen[screen] = state.index
        return true
    }

    companion object {
        const val SESSION_GAP_MS: Long = 30 * 60 * 1000L
        private const val NO_SESSION = -1
    }
}

/** Pure session clock: background ≥ [NotificationsBannerSession.SESSION_GAP_MS] starts a new session. */
data class ForegroundSessionState(val index: Int, val backgroundedAtMs: Long?) {
    fun onBackground(nowMs: Long): ForegroundSessionState = copy(backgroundedAtMs = nowMs)

    fun onForeground(nowMs: Long): ForegroundSessionState {
        val since = backgroundedAtMs ?: return this
        val next = if (nowMs - since >= NotificationsBannerSession.SESSION_GAP_MS) index + 1 else index
        return ForegroundSessionState(index = next, backgroundedAtMs = null)
    }
}

/** After this many dismisses (each in a different session) the banner is gone for good. */
const val NOTIFICATIONS_BANNER_MAX_DISMISSES = 3

/** Pure visibility rule of the banner. */
fun shouldShowNotificationsBanner(
    platformSupportsNotifications: Boolean,
    notificationsEnabled: Boolean,
    persistedDismissCount: Int,
    dismissedThisSession: Boolean,
    suppressedThisSession: Boolean,
): Boolean = platformSupportsNotifications &&
    !notificationsEnabled &&
    persistedDismissCount < NOTIFICATIONS_BANNER_MAX_DISMISSES &&
    !dismissedThisSession &&
    !suppressedThisSession

/** Persisted count after a × tap: a second dismiss in the same session does not count. */
fun dismissCountAfterDismiss(persistedDismissCount: Int, dismissedThisSession: Boolean): Int =
    if (dismissedThisSession) persistedDismissCount else persistedDismissCount + 1
