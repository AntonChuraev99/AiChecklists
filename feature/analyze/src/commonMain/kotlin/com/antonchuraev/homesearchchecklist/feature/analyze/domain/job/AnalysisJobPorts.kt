package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistence of job records. Implementation (commonMain) serializes the list as JSON into one
 * DataStore string key (a short-lived queue, at most a handful of rows — not per-entity data, so not
 * Room). Every write is a read-modify-write inside one DataStore `edit`, so [update] is atomic.
 */
interface AnalysisJobStore {
    val jobs: Flow<List<AnalysisJob>>

    suspend fun get(jobId: String): AnalysisJob?

    suspend fun put(job: AnalysisJob)

    /**
     * Atomic read-modify-write. [transform] returns the new record, or null to leave it unchanged.
     * Returns the stored record after the call (null when the job does not exist).
     */
    suspend fun update(jobId: String, transform: (AnalysisJob) -> AnalysisJob?): AnalysisJob?

    suspend fun delete(jobId: String)
}

/**
 * Bytes of file material (photo / PDF / audio) for a job. Android: app-private `filesDir/analysis_jobs/`
 * (survives process death). wasmJs: in-memory map (a reload loses it — see
 * [AnalysisJobScheduler.resumePending]). Owners: `@android-platform-expert`, `@wasmjs-expert`.
 */
interface AnalysisPayloadStore {
    suspend fun put(jobId: String, bytes: ByteArray): Boolean

    suspend fun read(jobId: String): ByteArray?

    suspend fun delete(jobId: String)
}

/**
 * Platform runner of a persisted job. It only decides WHEN and WHERE [AnalysisJobProcessor.process]
 * runs; it never touches the network or Room itself.
 *
 * - Android: unique expedited `CoroutineWorker` per job id (`enqueueUniqueWork(jobId, KEEP, …)`,
 *   `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`, `NetworkType.CONNECTED`, bounded backoff,
 *   mandatory `getForegroundInfo()`), `Result.retry()` on [ProcessResult.RetryLater].
 * - wasmJs / iOS: launches [AnalysisJobProcessor.process] in an app-scoped `CoroutineScope`
 *   (Koin single, `SupervisorJob`) — NOT `viewModelScope`; [ProcessResult.RetryLater] becomes a
 *   final network failure (no OS scheduler to wait for connectivity).
 */
interface AnalysisJobLauncher {
    fun launch(jobId: String)

    /** Drops a not-yet-started run (used by [AnalysisJobScheduler.cancel] on a Queued job). */
    fun cancel(jobId: String)
}

/**
 * The common unit of work, called by the platform launcher with a job id. Implementation
 * (commonMain, `@feature-expert`): read job + payload → (photo) downscale via [ImageDownscaler] →
 * base64 → `AnalyzeRepository.analyzeData(..., requestId = job.id)` → on success write the checklist
 * (NewChecklist target) or persist [AnalysisJobOutcome.FillReady] → emit `ai_analyze_completed` /
 * `ai_analyze_failed` + `checklist_created` → update status → [AnalysisReadyNotifier] when the app
 * is not in the foreground.
 *
 * Idempotent per job id: a second call for a terminal job returns [ProcessResult.Done] without work;
 * a Succeeded NewChecklist job never writes a second checklist (checked under the store's atomic
 * update before writing).
 */
interface AnalysisJobProcessor {
    suspend fun process(jobId: String, isLastAttempt: Boolean): ProcessResult
}

sealed interface ProcessResult {
    /** Terminal status written (Succeeded / Failed / Cancelled). */
    data object Done : ProcessResult

    /**
     * Transient transport failure (network / timeout / 5xx after Ktor's own retries) and not the last
     * attempt: status is back to Queued(waitingForNetwork = true); the platform should retry with the
     * SAME job id. On the last attempt the processor writes Failed itself and returns [Done].
     */
    data object RetryLater : ProcessResult
}

/**
 * System notification for a job that finished while the app was not in the foreground.
 * Android (`@android-platform-expert`): own channel (API-O guard), POST_NOTIFICATIONS-aware, tap
 * opens MainActivity with the job id (its own extra — NOT `ACTION_OPEN_CHECKLIST`, which would count
 * as `reminder_notification_tapped`). wasmJs / iOS: no-op (web delivery is in-app on return).
 */
interface AnalysisReadyNotifier {
    fun notifyFinished(job: AnalysisJob)

    /** Removes the job's notification once the outcome was delivered in-app. */
    fun cancel(jobId: String)
}

/** True while an Activity / the browser tab is visible. Android: `ProcessLifecycleOwner`; web: `document.visibilityState`. */
interface AppForegroundMonitor {
    val isInForeground: StateFlow<Boolean>
}
