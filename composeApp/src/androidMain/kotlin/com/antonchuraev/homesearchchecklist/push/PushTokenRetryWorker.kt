package com.antonchuraev.homesearchchecklist.push

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Retries the FCM token fetch that failed on app start, then registers the token through the
 * SAME path as `onNewToken` and app start ([PushTokenRepository.registerToken] -> CF
 * `register_push_token`), which also refreshes `lastActiveAt` server-side.
 *
 * Enqueued only from the failure branch of `PushTokenRepositoryAndroid.touchLastActive` (via
 * [enqueueIfDue], which honours the post-exhaustion cooldown) — never on
 * the healthy start path, so WorkManager is not built on every cold start (see the
 * `WorkManagerInitializer` removal in the androidApp manifest: a ROM without
 * `JobScheduler.forNamespace` throws an `Error` while building WorkManager, hence [enqueue] runs
 * inside `runCatching`, which catches `Throwable`).
 *
 * Does not duplicate `GistiFirebaseMessagingService.onNewToken`: that callback fires only when FCM
 * mints or rotates a token. A device stuck in `FIS_AUTH_ERROR` never gets one, so nothing else
 * ever asks again until the next app start.
 *
 * Logging contract (Crashlytics non-fatal volume): intermediate failures are `warning`
 * (breadcrumb only); exhausting [PushTokenRetryPolicy.MAX_ATTEMPTS] is ONE `error` with the last
 * throwable — one non-fatal per failed chain, and at most one failed chain per
 * [PushTokenRetryPolicy.COOLDOWN_AFTER_EXHAUSTION_MS], instead of one per app start.
 */
class PushTokenRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull()
        val logger: AppLogger? = koin?.getOrNull()
        val repository: PushTokenRepository? = koin?.getOrNull()
        if (repository == null) {
            // Application.onCreate starts Koin synchronously before WorkManager can run a worker in
            // this process, so this is reached only if the binding itself failed. Nothing to retry.
            Log.w(TAG, "retry worker: no PushTokenRepository bound — chain dropped")
            logger?.warning(TAG, "retry worker: no PushTokenRepository bound — chain dropped")
            return Result.failure()
        }

        val attempt = runAttemptCount + 1
        val fetch = runCatching { FirebaseMessaging.getInstance().token.await() }
        val failure = fetch.exceptionOrNull()
        // Constraint loss / work cancellation is teardown, not a token failure.
        if (failure is CancellationException) throw failure

        val token = fetch.getOrNull()
        if (!token.isNullOrBlank()) {
            logger?.info(TAG, "retry worker: FCM token obtained on attempt $attempt — registering")
            repository.registerToken(token)
            return Result.success()
        }

        val reason = failure?.message ?: "blank token"
        return when (PushTokenRetryPolicy.afterFailure(runAttemptCount)) {
            PushTokenRetryPolicy.Next.RETRY -> {
                logger?.warning(
                    TAG,
                    "retry worker: FCM token fetch failed (attempt $attempt/" +
                        "${PushTokenRetryPolicy.MAX_ATTEMPTS}): $reason — retrying with backoff",
                )
                Result.retry()
            }
            PushTokenRetryPolicy.Next.GIVE_UP -> {
                // Written BEFORE the chain ends: a start racing this run then already sees the
                // cooldown, and the next starts do not open a fresh chain for a day.
                recordExhaustion(koin.getOrNull(), logger)
                logger?.error(
                    TAG,
                    "FCM token unavailable after ${PushTokenRetryPolicy.MAX_ATTEMPTS} attempts: $reason",
                    failure ?: IllegalStateException("FCM returned a blank token"),
                )
                Result.failure()
            }
        }
    }

    private suspend fun recordExhaustion(datastore: AppDatastore?, logger: AppLogger?) {
        if (datastore == null) {
            logger?.warning(TAG, "retry worker: no AppDatastore bound — exhaustion cooldown not recorded")
            return
        }
        runCatching {
            datastore.saveLong(PushTokenRetryPolicy.KEY_EXHAUSTED_AT_MS, System.currentTimeMillis())
        }.onFailure { e ->
            if (e is CancellationException) throw e
            logger?.warning(TAG, "retry worker: could not record exhaustion cooldown: ${e::class.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PushToken"

        /** Unique-work name: at most one retry chain per device at a time. */
        const val WORK_NAME = "push_token_retry"

        /**
         * Start a retry chain unless the last one exhausted less than
         * [PushTokenRetryPolicy.COOLDOWN_AFTER_EXHAUSTION_MS] ago ([PushTokenRetryPolicy.shouldStartChain]).
         *
         * The cooldown exists because unique-work KEEP only dedupes a PENDING/RUNNING chain: a
         * FAILED one is replaced on the very next start, and app start runs on every process start
         * (FCM, alarms, widget, the retry worker's own wake). Without it a device in persistent
         * `FIS_AUTH_ERROR` would again produce about one non-fatal per start.
         *
         * A failed read of the record fails OPEN (logged warning, chain allowed): KEEP still bounds
         * it to one live chain, and blocking would silently stop retries for good.
         */
        suspend fun enqueueIfDue(
            context: Context,
            datastore: AppDatastore,
            logger: AppLogger?,
            nowMs: Long = System.currentTimeMillis(),
        ) {
            val read = runCatching {
                datastore.observeLong(PushTokenRetryPolicy.KEY_EXHAUSTED_AT_MS, 0L).first()
            }
            read.exceptionOrNull()?.let { e ->
                if (e is CancellationException) throw e
                logger?.warning(
                    TAG,
                    "could not read token-retry cooldown (${e::class.simpleName}: ${e.message}) — enqueueing anyway",
                )
            }
            val lastExhaustedAtMs = read.getOrDefault(0L)
            if (!PushTokenRetryPolicy.shouldStartChain(lastExhaustedAtMs, nowMs)) {
                logger?.info(
                    TAG,
                    "FCM token retry not enqueued: last chain exhausted " +
                        "${(nowMs - lastExhaustedAtMs) / 60_000} min ago (cooldown 24h)",
                )
                return
            }
            enqueue(context, logger)
        }

        /**
         * Start a retry chain unless one is already pending or running.
         *
         * [ExistingWorkPolicy.KEEP], not REPLACE: the app-start fetch fails on EVERY start of an
         * affected device. REPLACE would cancel the live chain and restart it at attempt 0 on each
         * start — the backoff would never grow and an active user would never reach the give-up
         * branch, i.e. the one exhaustion non-fatal could never fire. KEEP does NOT cover a finished
         * chain — that gap is closed by the exhaustion cooldown in [enqueueIfDue], the only caller.
         */
        private fun enqueue(context: Context, logger: AppLogger?) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<PushTokenRetryWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setInitialDelay(PushTokenRetryPolicy.BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        PushTokenRetryPolicy.BACKOFF_MINUTES,
                        TimeUnit.MINUTES,
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }.onFailure { e ->
                // Broken-ROM WorkManager build (NoSuchMethodError) lands here instead of killing the
                // process; the next app start tries again.
                logger?.warning(TAG, "could not enqueue FCM token retry: ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}
