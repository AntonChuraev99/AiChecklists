package com.antonchuraev.homesearchchecklist.push

/**
 * Decides when the client moves the server-side `lastActiveAt` and when it may re-send its token
 * without doing so. `lastActiveAt` drives the promo sender's dormancy window and its
 * "N pushes without a reaction" stop, so it must mean "the user opened the app" — never "the
 * process woke up". `Application.onCreate` runs on every background wake (FCM delivery of the promo
 * itself, WorkManager, widget, alarms); an activity write there made every delivered promo look
 * answered.
 */
internal object PushActivityPolicy {

    /**
     * Minimum gap between two foreground activity writes in one process. A resume storm (billing
     * sheet, pickers, quick app switches) sends one request, while a real return to the app after
     * a promo is sent still lands well inside the server's day-scale windows.
     */
    const val FOREGROUND_MARK_INTERVAL_MS = 5 * 60 * 1000L

    /**
     * True when a foreground resume at [nowMs] should write activity. [lastMarkedAtMs] is the
     * previous attempt in this process, null if none. A clock that went backwards marks again
     * instead of freezing the user as dormant until it catches up.
     */
    fun shouldMarkForeground(lastMarkedAtMs: Long?, nowMs: Long): Boolean {
        if (lastMarkedAtMs == null) return true
        val elapsed = nowMs - lastMarkedAtMs
        return elapsed < 0 || elapsed >= FOREGROUND_MARK_INTERVAL_MS
    }

    /**
     * Value stored after a successful token registration: user id plus a hash of the token, so the
     * token itself is never persisted (and therefore never re-sent from storage).
     */
    fun tokenSyncMarker(userId: String, token: String): String = "$userId:${token.hashCode()}"

    /**
     * True when a background process start must register [token]: this (user id, token) pair has
     * not been confirmed by the server yet. Covers a token minted while the user id was not ready
     * and a user id that changed on account convergence. Everything else is already on the server,
     * so a background wake sends nothing at all.
     */
    fun shouldSyncTokenInBackground(storedMarker: String, userId: String, token: String): Boolean =
        storedMarker != tokenSyncMarker(userId, token)
}
