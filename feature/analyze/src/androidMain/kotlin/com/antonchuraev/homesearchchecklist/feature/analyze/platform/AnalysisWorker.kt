package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ProcessResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.isTerminal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlinx.coroutines.CancellationException
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/**
 * Runs one background analysis: hands the job id to the common [AnalysisJobProcessor] and maps its
 * answer onto WorkManager. All network / Room / analytics work is the processor's; this class only
 * decides retry vs done.
 *
 * Koin is started synchronously in `Application.onCreate`, before WorkManager can run a worker in a
 * fresh process, so the processor is resolved from [GlobalContext] (no worker factory needed — same
 * pattern as `PushTokenRetryWorker`).
 */
class AnalysisWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull()
        val logger: AppLogger? = koin?.getOrNull()
        val jobId = inputData.getString(AnalysisWorkPolicy.KEY_JOB_ID)
        if (jobId.isNullOrBlank()) {
            logger?.error(TAG, "analysis worker started without a job id — dropped", IllegalStateException("no job id"))
            return Result.failure()
        }
        val processor: AnalysisJobProcessor? = koin?.getOrNull()
        if (processor == null) {
            Log.e(TAG, "analysis worker: no AnalysisJobProcessor bound — job $jobId stays as it is")
            logger?.error(TAG, "no AnalysisJobProcessor bound — job $jobId not run", IllegalStateException("missing binding"))
            return Result.failure()
        }

        val isLastAttempt = AnalysisWorkPolicy.isLastAttempt(runAttemptCount)
        val attempt = "${runAttemptCount + 1}/${AnalysisWorkPolicy.MAX_ATTEMPTS}"
        val outcome = runCatching { processor.process(jobId, isLastAttempt) }
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            // Worker stop (constraint lost, quota, user cancel) is teardown: WorkManager reschedules it.
            if (failure is CancellationException) throw failure
            logger?.error(TAG, "process($jobId) threw on attempt $attempt: ${failure.message}", failure)
            return if (isLastAttempt) {
                failStuckJob(koin, jobId, logger)
                Result.failure()
            } else {
                Result.retry()
            }
        }

        return when (outcome.getOrNull()) {
            ProcessResult.RetryLater -> if (isLastAttempt) {
                // Contract breach: the processor must write Failed itself on the last attempt.
                logger?.warning(TAG, "process($jobId) asked to retry on its last attempt — failing it here")
                failStuckJob(koin, jobId, logger)
                Result.failure()
            } else {
                logger?.info(TAG, "job $jobId: transient failure on attempt $attempt — retrying with backoff")
                Result.retry()
            }
            else -> Result.success()
        }
    }

    /**
     * Required for expedited work: on API ≤ 30 WorkManager runs it as a foreground service and calls
     * this to get the notification; without the override the run crashes there. API 31+ never calls it
     * for an expedited job (JobScheduler expedited jobs show no notification).
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val jobId = inputData.getString(AnalysisWorkPolicy.KEY_JOB_ID).orEmpty()
        val style: AnalysisNotificationStyle? = GlobalContext.getOrNull()?.getOrNull()
        val notification = AnalysisNotifications.progressNotification(
            applicationContext,
            AnalysisNotifications.resolveSmallIcon(applicationContext, style),
        )
        // No foregroundServiceType: types are only enforced on API 34+, where this path never runs,
        // and naming one WorkManager's SystemForegroundService does not declare would throw on 29/30.
        return ForegroundInfo(AnalysisNotifications.progressNotificationId(jobId), notification)
    }

    /**
     * The processor threw on the last attempt, so nobody wrote a terminal status: without this the
     * job would show "Analyzing" forever. Retryable — the input itself was never judged.
     */
    private suspend fun failStuckJob(koin: Koin?, jobId: String, logger: AppLogger?) {
        val store: AnalysisJobStore = koin?.getOrNull() ?: run {
            logger?.warning(TAG, "no AnalysisJobStore bound — job $jobId left non-terminal")
            return
        }
        runCatching {
            store.update(jobId) { job ->
                if (job.status.isTerminal) {
                    null
                } else {
                    job.copy(
                        status = AnalysisJobStatus.Failed(
                            AnalysisJobFailure(reason = AiFailureReason.UNKNOWN, retryableWithSameInput = true),
                        ),
                    )
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            logger?.error(TAG, "could not fail stuck job $jobId: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}
