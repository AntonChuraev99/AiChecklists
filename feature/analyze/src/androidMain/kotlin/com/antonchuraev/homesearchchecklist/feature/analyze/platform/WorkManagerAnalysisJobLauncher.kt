package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android [AnalysisJobLauncher]: one unique expedited [AnalysisWorker] per job id
 * ([AnalysisWorkPolicy.buildRequest]).
 *
 * - `KEEP`: a second launch of the same id (resumePending racing enqueue) never restarts a running
 *   attempt and never resets its attempt count.
 * - WorkManager is built lazily here through the Application's `Configuration.Provider`
 *   (`GistiAndroidApplication`); the androidx.startup `WorkManagerInitializer` stays removed from the
 *   manifest. Building it can throw an `Error` on broken ROMs (`JobScheduler.forNamespace`
 *   NoSuchMethodError, 2026-08-13), so every call is guarded and a failed build falls back to the
 *   in-process [fallback] launcher — the analysis still runs while the app is alive instead of
 *   staying Queued forever.
 * - Offline at launch: marks the job `Queued(waitingForNetwork = true)` so the screen shows "Waiting
 *   for a connection" — WorkManager holds the work on its `CONNECTED` constraint and exposes no
 *   "blocked on constraints" state of its own. The processor flips it to Running when the worker starts.
 */
class WorkManagerAnalysisJobLauncher(
    context: Context,
    private val store: AnalysisJobStore,
    private val logger: AppLogger,
    private val fallback: AnalysisJobLauncher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AnalysisJobLauncher {

    private val appContext = context.applicationContext

    private val crashGuard = CoroutineExceptionHandler { _, e ->
        logger.error(TAG, "analysis launcher coroutine crashed: ${e.message}", e)
    }

    override fun launch(jobId: String) {
        val enqueued = runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                AnalysisWorkPolicy.uniqueWorkName(jobId),
                ExistingWorkPolicy.KEEP,
                AnalysisWorkPolicy.buildRequest(jobId),
            )
        }
        enqueued.exceptionOrNull()?.let { e ->
            logger.error(TAG, "WorkManager unavailable for job $jobId — running it in-process: ${e.message}", e)
            fallback.launch(jobId)
            return
        }
        scope.launch(crashGuard) { markWaitingIfOffline(jobId) }
    }

    override fun cancel(jobId: String) {
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(AnalysisWorkPolicy.uniqueWorkName(jobId))
        }.onFailure { e ->
            logger.warning(TAG, "cancelUniqueWork($jobId) failed: ${e::class.simpleName}: ${e.message}")
        }
        fallback.cancel(jobId)
    }

    private suspend fun markWaitingIfOffline(jobId: String) {
        if (hasValidatedNetwork()) return
        runCatching {
            store.update(jobId) { job ->
                val status = job.status
                if (status is AnalysisJobStatus.Queued && !status.waitingForNetwork) {
                    job.copy(status = AnalysisJobStatus.Queued(waitingForNetwork = true))
                } else {
                    null
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            logger.warning(TAG, "could not mark job $jobId as waiting for network: ${e.message}")
        }
    }

    /** INTERNET + VALIDATED on the default network. Unknown (SecurityException) → treated as online. */
    private fun hasValidatedNetwork(): Boolean = runCatching {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrElse { e ->
        logger.warning(TAG, "network state unreadable (${e::class.simpleName}) — assuming online")
        true
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}
