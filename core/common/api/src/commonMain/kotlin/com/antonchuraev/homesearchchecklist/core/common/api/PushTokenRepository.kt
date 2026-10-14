package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform-agnostic registration point for push (FCM) tokens and activity tracking.
 *
 * Used by re-engagement messaging: the server reads `users/{user_id}.fcmToken` to target
 * pushes and `users/{user_id}.lastActiveAt` to decide who is dormant. `lastActiveAt` is written
 * only from a foreground ([touchLastActive]); every other path sends `mark_active = false`.
 *
 * Implementations register the token on the canonical `users/{user_id}` credit document (the
 * device-registration id, shared with credits) via a server Cloud Function — NOT a direct
 * Firestore write. This reaches BOTH anonymous and signed-in users: the credit doc is
 * server-write-only, and anonymous users have no Firebase Auth to authorize a direct write.
 * If `user_id` is not ready yet (registration pending on a cold first start), implementations
 * log a warning and no-op; the token re-registers on the next start / token rotation.
 *
 * Android implementation: `PushTokenRepositoryAndroid` (composeApp/androidMain).
 * iOS/wasmJs: no binding yet (FCM client deferred on those targets) — callers on those
 * platforms must guard via Koin `getOrNull()`.
 */
interface PushTokenRepository {

    /**
     * Persist [token] for the current user (sets `fcmToken` + `platform`). `lastActiveAt` moves
     * only when [markActive] is true, which only a visible foreground may pass: background callers
     * (`onNewToken`, the token-retry worker, a process start) pass false, because a process woken
     * by the promo push itself is not the user reacting to it.
     * Safe to call before registration completes — implementation no-ops with a warning.
     */
    suspend fun registerToken(token: String, markActive: Boolean)

    /**
     * The app came to the foreground: refresh `lastActiveAt` (server timestamp) for the
     * re-engagement dormancy window, together with the current token. Call ONLY from a visible
     * Activity, never from `Application.onCreate` or a receiver — those run on every background
     * process wake. Throttled per process, so calling it on every resume is fine. When no token can
     * be obtained the implementation sends an activity-only write instead, which moves only
     * `lastActiveAt`.
     * Safe to call before registration completes — implementation no-ops with a warning.
     */
    suspend fun touchLastActive()

    /**
     * Process start (foreground or background): register the current token WITHOUT moving
     * `lastActiveAt`, and only if the server has not confirmed this user/token pair yet — so a
     * background wake normally sends nothing.
     */
    suspend fun syncTokenOnProcessStart()
}
