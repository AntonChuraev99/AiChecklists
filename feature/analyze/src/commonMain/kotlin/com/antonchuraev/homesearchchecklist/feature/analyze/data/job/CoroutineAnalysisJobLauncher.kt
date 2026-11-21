package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ProcessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [AnalysisJobProcessor.process] in an app-scoped coroutine — the launcher of wasmJs and iOS, and
 * the in-process fallback of the Android WorkManager launcher when WorkManager cannot be built.
 *
 * - App scope (`SupervisorJob`), never `viewModelScope`: leaving the screen must not cancel the job.
 * - One attempt, always the last one: there is no OS scheduler to wait for connectivity, so the
 *   processor writes the final status itself (a transport failure becomes a final NETWORK failure).
 * - At most one run per job id in this process: `resumePending` and `enqueue` racing on the same id
 *   would otherwise send the request twice (the server dedups credits by id, but the second run is
 *   still wasted work).
 * - [cancel] needs no coroutine handle: it is only used for a Queued job, and the processor returns
 *   `Done` without work for a job the scheduler already marked Cancelled.
 *
 * [processor] is a provider so the Koin graph can resolve the processor lazily (the processor
 * depends on stores the scheduler also uses; resolving it at construction would order modules).
 */
class CoroutineAnalysisJobLauncher(
    private val processor: () -> AnalysisJobProcessor,
    private val logger: AppLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AnalysisJobLauncher {

    private val lock = Mutex()
    private val inFlight = mutableSetOf<String>()

    private val crashGuard = CoroutineExceptionHandler { _, e ->
        // Last line of defence: an app-scope coroutine has no screen to absorb an uncaught throwable.
        logger.error(TAG, "analysis job coroutine crashed: ${e.message}", e)
    }

    override fun launch(jobId: String) {
        scope.launch(crashGuard) {
            val claimed = lock.withLock { inFlight.add(jobId) }
            if (!claimed) {
                logger.info(TAG, "job $jobId already running in this process — duplicate launch ignored")
                return@launch
            }
            try {
                runOnce(jobId)
            } finally {
                lock.withLock { inFlight.remove(jobId) }
            }
        }
    }

    override fun cancel(jobId: String) {
        logger.info(TAG, "cancel($jobId): nothing to drop in-process; the processor skips a Cancelled job")
    }

    private suspend fun runOnce(jobId: String) {
        val result = runCatching { processor().process(jobId, isLastAttempt = true) }
        result.exceptionOrNull()?.let { e ->
            if (e is CancellationException) throw e
            logger.error(TAG, "process($jobId) threw: ${e.message}", e)
            return
        }
        if (result.getOrNull() == ProcessResult.RetryLater) {
            // Contract: on the last attempt the processor writes Failed itself and returns Done.
            logger.warning(TAG, "process($jobId) returned RetryLater on its only attempt — status may stay Queued")
        }
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}
