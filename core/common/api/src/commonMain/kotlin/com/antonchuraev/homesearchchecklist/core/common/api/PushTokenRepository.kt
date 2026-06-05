package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform-agnostic registration point for push (FCM) tokens and activity tracking.
 *
 * Used by re-engagement messaging: the server reads `users/{uid}.fcmToken` to target
 * pushes and `users/{uid}.lastActiveAt` to decide who is dormant.
 *
 * Implementations write to the same `users/{uid}` Firestore document the sync layer uses.
 * If there is no authenticated user, implementations log a warning and no-op (the project
 * has NO anonymous Firebase Auth — an unauthenticated user simply has no uid yet).
 *
 * Android implementation: `PushTokenRepositoryAndroid` (composeApp/androidMain).
 * iOS/wasmJs: no binding yet (FCM client deferred on those targets) — callers on those
 * platforms must guard via Koin `getOrNull()`.
 */
interface PushTokenRepository {

    /**
     * Persist [token] for the current user (sets `fcmToken` + `platform` + `lastActiveAt`).
     * Called from `onNewToken` and on app start once a token is available.
     * Safe to call when not signed in — implementation no-ops with a warning.
     */
    suspend fun registerToken(token: String)

    /**
     * Bump `lastActiveAt` (server timestamp) for the current user without touching the token.
     * Called on app start to feed the re-engagement dormancy window.
     * Safe to call when not signed in — implementation no-ops with a warning.
     */
    suspend fun touchLastActive()
}
