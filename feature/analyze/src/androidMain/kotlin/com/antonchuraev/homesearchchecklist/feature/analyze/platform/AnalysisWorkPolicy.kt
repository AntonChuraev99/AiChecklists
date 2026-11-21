package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * WorkManager shape of one background analysis. Pure (no Context) so the request and the retry rule
 * are host-testable.
 *
 * Attempts are counted only for runs that actually started: while the device is offline the
 * `CONNECTED` constraint holds the work without consuming an attempt, so "offline for an hour" does not
 * exhaust the job — only repeated transport failures with a connection do.
 */
object AnalysisWorkPolicy {

    /** Runs per job, the first one included. The last one writes the final status itself. */
    const val MAX_ATTEMPTS: Int = 5

    /** WorkManager's floor is 10 s; exponential → 10, 20, 40, 80 s between the five runs. */
    const val INITIAL_BACKOFF_SECONDS: Long = 10

    const val KEY_JOB_ID: String = "analysis_job_id"
    const val TAG: String = "analysis_job"

    fun uniqueWorkName(jobId: String): String = "analysis_job_$jobId"

    /** [runAttemptCount] is WorkManager's 0-based count of previous runs of this request. */
    fun isLastAttempt(runAttemptCount: Int): Boolean = runAttemptCount + 1 >= MAX_ATTEMPTS

    fun buildRequest(jobId: String): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(KEY_JOB_ID to jobId))
            // Expedited: starts right away and keeps network access in the background. Out of quota →
            // a regular job instead of failing the enqueue.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
}
