package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform-agnostic registration point for push (FCM) tokens and activity tracking.
 *
 * Used by re-engagement messaging: the server reads `users/{user_id}.fcmToken` to target
 * pushes and `users/{user_id}.lastActiveAt` to decide who is dormant.
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
     * Persist [token] for the current user (sets `fcmToken` + `platform` + `lastActiveAt`).
     * Called from `onNewToken` and on app start once a token is available.
     * Safe to call before registration completes — implementation no-ops with a warning.
     */
    suspend fun registerToken(token: String)

    /**
     * Refresh `lastActiveAt` (server timestamp) for the current user to feed the re-engagement
     * dormancy window. Called on app start. Under the Cloud-Function model there is no token-less
     * write, so the implementation re-registers the current token (never a blank one).
     * Safe to call before registration completes — implementation no-ops with a warning.
     */
    suspend fun touchLastActive()
}
