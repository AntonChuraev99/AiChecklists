package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.concurrent.Volatile
import kotlin.math.absoluteValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of [PushTokenRepository].
 *
 * Registers the FCM token + activity timestamp on the SAME `users/{user_id}` credit document that
 * holds `is_premium` / `ai_credits` / `device_id` / `google_uid`, so the promo sender can filter
 * (suppress premium/holdout) on one document.
 *
 * The write goes through the Cloud Function `register_push_token` (Admin SDK), NOT a direct
 * Firestore write. The client cannot write `users/{user_id}` directly: the Firestore rules make it
 * server-write-only, and — crucially for this feature — an ANONYMOUS user has no Firebase Auth at
 * all (`request.auth == null`), so an owner-write rule can never apply. Routing through the CF is
 * the only path that reaches the ~2/3 of the base that never signs in with Google.
 *
 * `user_id` is the canonical device-registration id (DataStore `USER_ID_KEY`) — the same id the
 * credits doc is keyed by and the one convergence re-points to the Google-linked doc. Because the
 * token is keyed by `user_id`, it automatically follows anon->Google convergence (same document).
 *
 * If `user_id` is not ready yet (register_user has not completed on a cold first start) we log a
 * warning and no-op; the token re-registers on the next app start / `onNewToken` (idempotent).
 */
internal class PushTokenRepositoryAndroid(
    private val logger: AppLogger,
    private val userApiService: UserApiService,
    private val userDataRepository: UserDataRepository,
    private val analytics: AnalyticsTracker,
    /**
     * Starts the bounded token-retry chain unless a recent one exhausted
     * ([PushTokenRetryWorker.enqueueIfDue]); injected for DI/tests. Suspend: reads the cooldown.
     */
    private val scheduleTokenRetry: suspend () -> Unit,
) : PushTokenRepository {

    // Set the sticky push_holdout user-property once per process (mirrors AiModelExperimentTracker).
    @Volatile
    private var holdoutReported = false

    private suspend fun currentUserId(): String = userDataRepository.getUserData().userId

    override suspend fun registerToken(token: String) {
        if (token.isBlank()) {
            logger.warning(TAG, "registerToken called with blank token — skipping")
            return
        }
        val userId = currentUserId()
        if (userId.isBlank()) {
            logger.warning(
                TAG,
                "registerToken skipped: user_id not ready yet (register_user pending) — " +
                    "will retry on next start / onNewToken",
            )
            return
        }
        // Client is the single source of truth for the promo holdout: a deterministic per-user_id
        // bucket, so it is stable across installs/versions and never drifts on re-registration.
        // The server filters on the stored `pushHoldout` boolean + `fcmOptIn`.
        val holdout = isPushHoldout(userId)
        userApiService.registerPushToken(
            userId = userId,
            token = token,
            platform = PLATFORM_ANDROID,
            pushHoldout = holdout,
            fcmOptIn = true,
        ).onFailure { e ->
            logger.error(TAG, "Failed to register FCM token via CF for user=${userId.take(8)}: ${e.message}", e)
        }
        // Sticky user-property so retention charts segment exposed vs holdout (incremental lift).
        // Independent of the CF write above — the client always knows its own bucket.
        reportHoldoutUserProperty(holdout)
    }

    /**
     * Deterministic ~15% control bucket keyed by user_id. Uses [String.hashCode] (the stable Java
     * algorithm) widened to Long before [absoluteValue] so `Int.MIN_VALUE` can't produce a negative
     * bucket — `Int.MIN_VALUE.absoluteValue` would overflow back to negative, `toLong()` avoids it.
     */
    private fun isPushHoldout(userId: String): Boolean =
        (userId.hashCode().toLong().absoluteValue % HOLDOUT_BUCKETS) < HOLDOUT_CONTROL_SIZE

    private fun reportHoldoutUserProperty(holdout: Boolean) {
        if (holdoutReported) return
        runCatching {
            // Out-of-session: token registration runs from Application.onCreate on every process
            // start, including background wakes with no Activity.
            analytics.setUserPropertiesOutOfSession(mapOf(AnalyticsParams.PUSH_HOLDOUT to holdout))
            holdoutReported = true // only latch on success so a failed attempt retries next start
        }.onFailure { e ->
            logger.warning(TAG, "Failed to set push_holdout user-property: ${e.message}")
        }
    }

    /**
     * Bump `lastActiveAt` for the dormancy window. The CF `register_push_token` is the only write
     * path and it requires a non-blank token (a token-less body is a 400), so we fetch the CURRENT
     * FCM token and re-register it — this refreshes `lastActiveAt` server-side without changing the
     * token value. A locally cached token is deliberately NOT re-sent instead: the sender deletes
     * `fcmToken` on UNREGISTERED, and re-sending a cached value would resurrect a dead token.
     *
     * A failed fetch (in prod overwhelmingly `IOException: FIS_AUTH_ERROR`, a device-side GMS /
     * Installations state) is a `warning` here and is handed to [PushTokenRetryWorker] via
     * [scheduleTokenRetry]: a bounded, backed-off chain that registers the token through
     * [registerToken] once obtained and reports ONE non-fatal if it never is — instead of one
     * non-fatal on every app start. Until a token exists `lastActiveAt` cannot move; a token-less
     * activity write needs a server-side endpoint change.
     */
    override suspend fun touchLastActive() {
        val userId = currentUserId()
        if (userId.isBlank()) {
            logger.warning(TAG, "touchLastActive skipped: user_id not ready yet")
            return
        }
        val fetch = runCatching { FirebaseMessaging.getInstance().token.await() }
        val token = fetch.getOrNull()
        if (token.isNullOrBlank()) {
            val failure = fetch.exceptionOrNull()
            if (failure is CancellationException) throw failure
            val reason = failure?.let { "${it::class.simpleName}: ${it.message}" } ?: "blank token"
            logger.warning(TAG, "touchLastActive: FCM token unavailable ($reason) — scheduling bounded retry")
            scheduleTokenRetry()
            return
        }
        registerToken(token)
    }

    private companion object {
        const val TAG = "PushToken"
        const val PLATFORM_ANDROID = "android"

        // ~15 of every 100 user_id buckets fall into the no-promo control group.
        const val HOLDOUT_BUCKETS = 100L
        const val HOLDOUT_CONTROL_SIZE = 15L
    }
}
