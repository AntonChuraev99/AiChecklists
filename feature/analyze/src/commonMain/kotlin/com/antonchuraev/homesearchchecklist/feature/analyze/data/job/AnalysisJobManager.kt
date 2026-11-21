package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLimitReached
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLimits
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobRequest
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobRequestInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryClaimant
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.isTerminal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeEventParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The app-scoped owner of every analysis ([AnalysisJobScheduler]). Holds no platform code: the
 * [launcher] decides where a job runs, [AnalysisJobProcessorImpl] does the work.
 *
 * Acknowledged jobs are pruned (record + payload) at once: nothing reads them afterwards, and a
 * failed job keeps its payload until then so "Try again" can reuse the captured input.
 */
class AnalysisJobManager(
    private val store: AnalysisJobStore,
    private val payloadStore: AnalysisPayloadStore,
    private val launcher: AnalysisJobLauncher,
    private val analyticsTracker: AnalyticsTracker,
    private val logger: AppLogger,
    scope: CoroutineScope,
    private val readFileBytes: (String) -> ByteArray?,
    private val readFileText: (String) -> String?,
    private val now: () -> Long = ::currentTimeMillis,
    private val newId: () -> String = ::randomJobId,
    private val maxRunning: Int = AnalysisJobLimits.MAX_RUNNING,
) : AnalysisJobScheduler {

    /** Serializes the "count running, then insert" step of [enqueue] so two taps cannot both pass the cap. */
    private val enqueueLock = Mutex()

    override val jobs: StateFlow<List<AnalysisJob>> = store.jobs
        .map { all -> all.sortedByDescending { it.createdAtMs } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observe(jobId: String): Flow<AnalysisJob?> =
        store.jobs.map { all -> all.firstOrNull { it.id == jobId } }.distinctUntilChanged()

    override suspend fun enqueue(request: AnalysisJobRequest): Result<String> = enqueueLock.withLock {
        if (runningCount() >= maxRunning) return@withLock Result.failure(AnalysisJobLimitReached(maxRunning))
        val id = newId()
        val input = capture(id, request.input) ?: return@withLock Result.failure(AiAnalyzeError.InputUnreadable)
        val job = AnalysisJob(
            id = id,
            createdAtMs = now(),
            input = input,
            target = request.target,
            analytics = request.analytics,
            status = AnalysisJobStatus.Queued(waitingForNetwork = false),
        )
        // Record BEFORE launch: a crash in between leaves a Queued job that resumePending re-launches.
        store.put(job)
        launcher.launch(id)
        Result.success(id)
    }

    override suspend fun retry(jobId: String): Result<String> = enqueueLock.withLock {
        val old = store.get(jobId)
            ?: return@withLock Result.failure(IllegalStateException("job $jobId is gone"))
        if (runningCount() >= maxRunning) return@withLock Result.failure(AnalysisJobLimitReached(maxRunning))
        val id = newId()
        if (old.input is AnalysisJobInput.StoredFile) {
            val bytes = payloadStore.read(jobId)
            if (bytes == null || !payloadStore.put(id, bytes)) {
                logger.warning(TAG, "retry($jobId): captured payload is gone")
                return@withLock Result.failure(AiAnalyzeError.InputUnreadable)
            }
        }
        store.put(
            old.copy(
                id = id,
                createdAtMs = now(),
                status = AnalysisJobStatus.Queued(waitingForNetwork = false),
                attempt = 0,
                deliveredBy = null,
                acknowledged = false,
            ),
        )
        launcher.launch(id)
        prune(jobId)
        Result.success(id)
    }

    override suspend fun cancel(jobId: String): Boolean {
        var cancelled = false
        val job = store.update(jobId) { job ->
            if (job.status is AnalysisJobStatus.Queued) {
                cancelled = true
                job.copy(status = AnalysisJobStatus.Cancelled, acknowledged = true)
            } else {
                null
            }
        }
        if (!cancelled || job == null) return false
        launcher.cancel(jobId)
        // Closes the started → (completed | failed) funnel: a cancelled job is neither.
        analyticsTracker.event(
            AnalyticsEvents.Analyze.FAILED,
            AnalyzeEventParams.failed(
                inputType = job.analytics.inputType,
                source = job.analytics.source,
                reason = AiFailureReason.CANCELLED,
                error = AiAnalyzeError.Cancelled,
            ),
        )
        prune(jobId)
        return true
    }

    override suspend fun claimDelivery(jobId: String, claimant: DeliveryClaimant): Boolean {
        var won = false
        store.update(jobId) { job ->
            if (job.deliveredBy == null && !job.acknowledged && job.status.isTerminal) {
                won = true
                job.copy(deliveredBy = claimant)
            } else {
                null
            }
        }
        return won
    }

    override suspend fun acknowledge(jobId: String) {
        prune(jobId)
    }

    override suspend fun resumePending() {
        // Read the store, not [jobs]: at app start the eager StateFlow may not have its first value yet.
        val pending = currentSnapshot().filter { !it.status.isTerminal }
        pending.forEach { job ->
            val input = job.input
            if (input is AnalysisJobInput.StoredFile && payloadStore.read(job.id) == null) {
                // Web reload (owner decision 6, v1): the bytes lived in memory and are gone.
                val failed = store.update(job.id) {
                    if (it.status.isTerminal) null else it.copy(
                        status = AnalysisJobStatus.Failed(
                            AnalysisJobFailure(
                                reason = AiFailureReason.INTERRUPTED,
                                retryableWithSameInput = false,
                            ),
                        ),
                    )
                }
                if (failed?.status is AnalysisJobStatus.Failed) {
                    analyticsTracker.event(
                        AnalyticsEvents.Analyze.FAILED,
                        AnalyzeEventParams.failed(
                            inputType = job.analytics.inputType,
                            source = job.analytics.source,
                            reason = AiFailureReason.INTERRUPTED,
                            error = AiAnalyzeError.Interrupted,
                        ),
                    )
                }
            } else {
                launcher.launch(job.id)
            }
        }
        if (pending.isNotEmpty()) logger.info(TAG, "resumePending: ${pending.size} job(s)")
    }

    private suspend fun currentSnapshot(): List<AnalysisJob> =
        store.jobs.firstOrNull() ?: emptyList()

    private suspend fun runningCount(): Int = currentSnapshot().count { !it.status.isTerminal }

    private suspend fun prune(jobId: String) {
        store.delete(jobId)
        payloadStore.delete(jobId)
    }

    /** Copies the material into app storage NOW: the picker's path may be a cache the OS clears. */
    private suspend fun capture(id: String, input: AnalysisJobRequestInput): AnalysisJobInput? = when (input) {
        is AnalysisJobRequestInput.Text -> AnalysisJobInput.Text(input.text)
        is AnalysisJobRequestInput.Url -> AnalysisJobInput.Url(input.normalizedUrl)
        is AnalysisJobRequestInput.TextFile -> readFileText(input.filePath)
            ?.takeIf { it.isNotBlank() }
            ?.let { AnalysisJobInput.Text(it) }
            ?: run {
                logger.warning(TAG, "enqueue: text file unreadable or empty")
                null
            }
        is AnalysisJobRequestInput.File -> {
            val bytes = readFileBytes(input.filePath)
            when {
                bytes == null || bytes.isEmpty() -> {
                    logger.warning(TAG, "enqueue: ${input.kind} file unreadable")
                    null
                }
                !payloadStore.put(id, bytes) -> {
                    logger.error(TAG, "enqueue: could not store the ${input.kind} payload", null)
                    null
                }
                else -> AnalysisJobInput.StoredFile(input.kind, input.fileName, input.mimeType)
            }
        }
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomJobId(): String = Uuid.random().toString()
