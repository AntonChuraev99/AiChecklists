package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalyzeImageLimits
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ProcessResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.StoredFileKind
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.isTerminal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.PreparedAnalyzeInput
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeEventParams
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import kotlinx.coroutines.CancellationException

/**
 * The common unit of work of one analysis ([AnalysisJobProcessor]). Runs wherever the platform
 * launcher runs it (WorkManager worker / app-scope coroutine); never on a ViewModel scope.
 *
 * Idempotent per job id: a terminal job returns [ProcessResult.Done] untouched, so a WorkManager
 * re-run after process death or a duplicate launch never sends a second request nor writes a second
 * checklist. Every attempt sends `request_id = job.id`, so the server reserves credits once.
 *
 * UI-bound hooks (activation coordinator, navigation) are NOT run here — they run at delivery
 * (screen / snackbar / notification tap), because a worker emission with no UI collector would burn
 * the one-time reminder opt-in sheet. Analytics of the outcome IS emitted here, exactly once.
 */
class AnalysisJobProcessorImpl(
    private val store: AnalysisJobStore,
    private val payloadStore: AnalysisPayloadStore,
    private val downscaler: ImageDownscaler,
    private val analyzeRepository: AnalyzeRepository,
    /** The fill target as it is NOW (the user may have edited or deleted it while the job waited). */
    private val findChecklist: suspend (Long) -> Checklist?,
    private val analyticsTracker: AnalyticsTracker,
    private val foregroundMonitor: AppForegroundMonitor,
    private val notifier: AnalysisReadyNotifier,
    private val logger: AppLogger,
    /** Localized fallback name for a checklist the server did not name (Compose Resources stay out of here). */
    private val defaultChecklistName: suspend () -> String,
    private val aiModelExperimentTracker: AiModelExperimentTracker? = null,
) : AnalysisJobProcessor {

    override suspend fun process(jobId: String, isLastAttempt: Boolean): ProcessResult {
        val started = store.update(jobId) { job ->
            if (job.status.isTerminal) null
            else job.copy(status = AnalysisJobStatus.Running, attempt = job.attempt + 1)
        }
        if (started == null) {
            logger.warning(TAG, "process($jobId): job is gone (pruned or never stored)")
            return ProcessResult.Done
        }
        if (started.status !is AnalysisJobStatus.Running) return ProcessResult.Done

        return try {
            run(started, isLastAttempt)
        } catch (e: CancellationException) {
            // The platform stopped the run (WorkManager quota, process shutdown): back to Queued so the
            // next attempt — same id — picks it up. The server dedups by request_id.
            store.update(jobId) { if (it.status.isTerminal) null else it.copy(status = AnalysisJobStatus.Queued(false)) }
            throw e
        } catch (e: Throwable) {
            logger.error(TAG, "process($jobId) crashed: ${e.message}", e)
            finishFailed(started, e, AiFailureReason.UNKNOWN)
            ProcessResult.Done
        }
    }

    private suspend fun run(job: AnalysisJob, isLastAttempt: Boolean): ProcessResult {
        val input = prepare(job) ?: run {
            val error = if (job.input is AnalysisJobInput.StoredFile) AiAnalyzeError.Interrupted else AiAnalyzeError.InputUnreadable
            finishFailed(job, error, AiFailureReason.classify(error))
            return ProcessResult.Done
        }
        val targetChecklist: Checklist? = when (val target = job.target) {
            is AnalysisJobTarget.NewChecklist -> null
            is AnalysisJobTarget.Fill -> findChecklist(target.checklistId) ?: run {
                logger.warning(TAG, "process(${job.id}): fill target ${target.checklistId} no longer exists")
                finishFailed(job, IllegalStateException("fill target ${target.checklistId} is gone"), AiFailureReason.TARGET_MISSING)
                return ProcessResult.Done
            }
        }

        val result = analyzeRepository.analyzePrepared(input, targetChecklist, requestId = job.id)
        val error = result.exceptionOrNull()
        if (error != null) {
            val reason = AiFailureReason.classify(error)
            if (reason.isTransient && !isLastAttempt) {
                logger.warning(TAG, "process(${job.id}): transient ${reason.wireValue}, attempt ${job.attempt} — retry later")
                val offline = reason == AiFailureReason.NETWORK || reason == AiFailureReason.TIMEOUT
                store.update(job.id) {
                    if (it.status.isTerminal) null
                    else it.copy(status = AnalysisJobStatus.Queued(waitingForNetwork = offline))
                }
                return ProcessResult.RetryLater
            }
            finishFailed(job, error, reason)
            return ProcessResult.Done
        }
        return succeed(job, result.getOrThrow(), isLastAttempt)
    }

    private suspend fun prepare(job: AnalysisJob): PreparedAnalyzeInput? = when (val input = job.input) {
        is AnalysisJobInput.Text -> PreparedAnalyzeInput.Text(input.text)
        is AnalysisJobInput.Url -> PreparedAnalyzeInput.Url(input.url)
        is AnalysisJobInput.StoredFile -> payloadStore.read(job.id)?.let { bytes ->
            when (input.kind) {
                StoredFileKind.PHOTO -> PreparedAnalyzeInput.Image(downscale(job.id, bytes))
                StoredFileKind.PDF -> PreparedAnalyzeInput.Pdf(bytes, input.fileName)
                StoredFileKind.AUDIO -> PreparedAnalyzeInput.Audio(bytes)
            }
        }
    }

    /** A failed downscale must not fail an analysis that would have worked: send the original. */
    private suspend fun downscale(jobId: String, bytes: ByteArray): ByteArray {
        val scaled = runCatching {
            downscaler.downscaleToJpeg(bytes, AnalyzeImageLimits.MAX_LONG_SIDE_PX, AnalyzeImageLimits.JPEG_QUALITY)
        }.onFailure { e ->
            if (e is CancellationException) throw e
            logger.warning(TAG, "downscale($jobId) threw, sending the original: ${e.message}")
        }.getOrNull()
        return scaled ?: bytes
    }

    private suspend fun succeed(job: AnalysisJob, result: AnalyzeResult, isLastAttempt: Boolean): ProcessResult {
        aiModelExperimentTracker?.report(result.modelVariant, result.modelId, result.aiFlow)

        val outcome: AnalysisJobOutcome = when (val target = job.target) {
            is AnalysisJobTarget.Fill -> AnalysisJobOutcome.FillReady(
                suggestedItems = result.suggestedItems,
                fillItems = result.fillItems,
                summary = result.summary,
            )
            is AnalysisJobTarget.NewChecklist -> {
                // Re-check under the store: a parallel run that already wrote the checklist wins.
                val stillRunning = store.get(job.id)?.status is AnalysisJobStatus.Running
                if (!stillRunning) {
                    logger.warning(TAG, "process(${job.id}): finished elsewhere, not writing a second checklist")
                    return ProcessResult.Done
                }
                val name = result.suggestedName?.takeIf { it.isNotBlank() } ?: defaultChecklistName()
                val created = analyzeRepository.createChecklistFromResult(name, result).getOrElse { e ->
                    logger.error(TAG, "process(${job.id}): checklist write failed: ${e.message}", e)
                    // The server already charged for THIS request id. A user "Try again" would be a new id
                    // and a second charge; running the SAME job again makes the server replay instead.
                    if (!isLastAttempt) {
                        store.update(job.id) {
                            if (it.status.isTerminal) null else it.copy(status = AnalysisJobStatus.Queued(waitingForNetwork = false))
                        }
                        return ProcessResult.RetryLater
                    }
                    finishFailed(job, e, AiFailureReason.UNKNOWN)
                    return ProcessResult.Done
                }
                analyticsTracker.event(
                    AnalyticsEvents.Checklist.CREATED,
                    mapOf(
                        AnalyticsParams.SOURCE to ChecklistSource.AI.wire,
                        AnalyticsParams.ITEM_COUNT to result.suggestedItems.size,
                        "has_folders" to result.hasFolders,
                    ),
                )
                AnalysisJobOutcome.CreatedChecklist(
                    checklistId = created.id,
                    checklistName = created.name,
                    itemCount = result.suggestedItems.size,
                    hasFolders = result.hasFolders,
                )
            }
        }

        var written = false
        val done = store.update(job.id) { current ->
            if (current.status.isTerminal) {
                null
            } else {
                written = true
                current.copy(status = AnalysisJobStatus.Succeeded(outcome))
            }
        }
        if (!written || done == null) return ProcessResult.Done
        analyticsTracker.event(
            AnalyticsEvents.Analyze.COMPLETED,
            AnalyzeEventParams.completed(job.analytics.inputType, job.analytics.source, result, appState = appState()),
        )
        // The bytes are only kept for "Try again"; a success never retries.
        payloadStore.delete(job.id)
        notifyIfBackground(done)
        return ProcessResult.Done
    }

    private suspend fun finishFailed(job: AnalysisJob, error: Throwable, reason: AiFailureReason) {
        val failure = AnalysisJobFailure(
            reason = reason,
            // Only the SERVER's own words are ever shown; a local exception's text (Room, a crash,
            // "fill target is gone") is English developer text and stays in the log.
            serverMessage = error.serverMessageOrNull(),
            retryableWithSameInput = reason.retryableWithSameInput,
        )
        var written = false
        val done = store.update(job.id) { current ->
            if (current.status.isTerminal) {
                null
            } else {
                written = true
                current.copy(status = AnalysisJobStatus.Failed(failure))
            }
        }
        // Terminal already (a parallel run finished first): its event was the one and only.
        if (!written || done == null) return
        logger.warning(TAG, "process(${job.id}) failed: ${reason.wireValue} ${error.message}")
        analyticsTracker.event(
            AnalyticsEvents.Analyze.FAILED,
            AnalyzeEventParams.failed(job.analytics.inputType, job.analytics.source, reason, error, appState = appState()),
        )
        notifyIfBackground(done)
    }

    private fun Throwable.serverMessageOrNull(): String? = when (this) {
        is AiAnalyzeError.Http, is AiAnalyzeError.ServerRefusal -> message
        else -> null
    }?.takeIf { it.isNotBlank() && !it.startsWith("http_") }

    private fun appState(): String =
        if (foregroundMonitor.isInForeground.value) AnalyzeEventParams.APP_STATE_FOREGROUND
        else AnalyzeEventParams.APP_STATE_BACKGROUND

    /** In the foreground the screen or the app snackbar delivers; the notification is for the rest. */
    private fun notifyIfBackground(job: AnalysisJob) {
        if (!foregroundMonitor.isInForeground.value) notifier.notifyFinished(job)
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}
