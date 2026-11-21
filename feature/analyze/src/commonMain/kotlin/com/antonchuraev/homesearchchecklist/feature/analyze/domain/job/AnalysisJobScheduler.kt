package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-scoped owner of every AI analysis (Koin `single`, common code). The Analyze ViewModel calls
 * [enqueue] and observes [observe]; the app shell observes [jobs] for the "ready" signal; the Android
 * worker and the web runner drive [AnalysisJobProcessor] by job id.
 *
 * Implementation (commonMain, `@feature-expert` IMPLEMENT) = [AnalysisJobStore] (persistence) +
 * [AnalysisPayloadStore] (file bytes) + [AnalysisJobLauncher] (platform: WorkManager / app-scope
 * coroutine). It holds no platform code itself.
 */
interface AnalysisJobScheduler {

    /** Every non-pruned job, newest first. Survives process death (backed by [AnalysisJobStore]). */
    val jobs: StateFlow<List<AnalysisJob>>

    /** One job; emits null once it is pruned (or never existed). */
    fun observe(jobId: String): Flow<AnalysisJob?>

    /**
     * Persists the job record (and copies file bytes into [AnalysisPayloadStore]) BEFORE asking the
     * launcher to run it, so a crash between the two leaves a Queued job the next start re-launches.
     * Returns the job id (= server request_id). Fails only when the input cannot be captured (file
     * unreadable) — that failure is local, nothing is sent, nothing is charged.
     */
    suspend fun enqueue(request: AnalysisJobRequest): Result<String>

    /** Cancels a job that is still [AnalysisJobStatus.Queued]. Returns false for any other status. */
    suspend fun cancel(jobId: String): Boolean

    /**
     * Single-flight claim of the outcome's delivery. Exactly one claimant wins per job (compare-and-set
     * on [AnalysisJob.deliveredBy]); every other caller gets false and must NOT navigate or show
     * anything. This is what stops "Back, then 13 s later the app yanks you onto the checklist" and a
     * snackbar + navigation double-delivery.
     */
    suspend fun claimDelivery(jobId: String, claimant: DeliveryClaimant): Boolean

    /** Marks the outcome as seen (snackbar shown/dismissed, error read, checklist opened). Prunable after. */
    suspend fun acknowledge(jobId: String)

    /**
     * Re-launches Queued/Running jobs found at app start (process death, web reload). Called once
     * from the app's startup path. Jobs whose payload is gone (web file job after reload) are
     * failed with [com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason]
     * `INTERRUPTED`, `retryableWithSameInput = false`.
     */
    suspend fun resumePending()

    /**
     * "Try again" on a failed job: a NEW job (new id = new server request_id) with the SAME captured
     * input — the payload is copied, so a photo whose picker path has since been cleared still works.
     * The old job is acknowledged. Fails when the old job or its payload is gone, or when
     * [AnalysisJobLimits.MAX_RUNNING] jobs are already running.
     */
    suspend fun retry(jobId: String): Result<String>
}

object AnalysisJobLimits {
    /** Owner decision 1 (2026-11-20): up to three analyses at once. The UI reads it from the state. */
    const val MAX_RUNNING: Int = 3
}

/** [AnalysisJobScheduler.enqueue] / [AnalysisJobScheduler.retry] refused: [max] jobs already run. */
class AnalysisJobLimitReached(val max: Int) : Exception("analysis_job_limit_reached")

/**
 * Which jobs an Analyze screen is watching right now. App-scoped (Koin single): the screen's
 * ViewModel attaches its job while the screen is composed; the app-level ready signal skips attached
 * jobs, because that screen delivers them itself.
 */
class AnalysisScreenAttachments {
    private val _attached = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val attached: StateFlow<Set<String>> = _attached

    fun attach(jobId: String) {
        _attached.value = _attached.value + jobId
    }

    fun detach(jobId: String) {
        _attached.value = _attached.value - jobId
    }
}

/** What the Analyze screen hands over on the Analyze tap. Built by the ViewModel from its state. */
data class AnalysisJobRequest(
    val input: AnalysisJobRequestInput,
    val target: AnalysisJobTarget,
    val analytics: AnalysisJobAnalytics,
)

/** Pre-capture input: file material still points at the picker's path until [AnalysisJobScheduler.enqueue] copies it. */
sealed interface AnalysisJobRequestInput {
    data class Text(val text: String) : AnalysisJobRequestInput
    data class Url(val normalizedUrl: String) : AnalysisJobRequestInput
    data class TextFile(val filePath: String) : AnalysisJobRequestInput
    data class File(
        val kind: StoredFileKind,
        val filePath: String,
        val fileName: String,
        val mimeType: String,
    ) : AnalysisJobRequestInput
}
