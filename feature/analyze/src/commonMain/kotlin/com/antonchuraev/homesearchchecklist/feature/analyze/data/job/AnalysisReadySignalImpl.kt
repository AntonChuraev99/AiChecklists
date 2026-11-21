package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobHandoff
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadySignal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisScreenAttachments
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryClaimant
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.DeliveryRoute
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyAnalysis
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyDestination
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.resolveDelivery
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeEventParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * App-level "ready" signal ([AnalysisReadySignal]). [pending] is DERIVED from the persisted jobs —
 * so a signal missed while nothing collected it (process death, notification swiped) is still there
 * on the next composition — and filtered by [resolveDelivery]: only what the in-app host should show.
 */
class AnalysisReadySignalImpl(
    private val scheduler: AnalysisJobScheduler,
    foregroundMonitor: AppForegroundMonitor,
    attachments: AnalysisScreenAttachments,
    private val activationCoordinator: ActivationCoordinator,
    private val analyticsTracker: AnalyticsTracker,
    private val notifier: AnalysisReadyNotifier,
    private val logger: AppLogger,
    scope: CoroutineScope,
    /** Localized default name of a fill (`default_fill_name`), resolved by the caller's layer. */
    private val defaultFillName: suspend () -> String,
    /** A failed job opened from the snackbar / notification is handed to the Analyze screen here. */
    private val handoff: AnalysisJobHandoff = AnalysisJobHandoff(),
) : AnalysisReadySignal {

    override val pending: StateFlow<List<ReadyAnalysis>> = combine(
        scheduler.jobs,
        foregroundMonitor.isInForeground,
        attachments.attached,
    ) { jobs, foreground, attached ->
        jobs
            .filter { job ->
                val attachedId = job.id.takeIf { it in attached }
                resolveDelivery(job, attachedId, foreground) == DeliveryRoute.InAppHost
            }
            .sortedBy { it.createdAtMs }
            .mapNotNull { it.toReady() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun onDismissed(jobId: String) {
        scheduler.acknowledge(jobId)
    }

    override suspend fun onOpen(jobId: String, via: DeliveryClaimant): ReadyDestination? {
        // The store, not `jobs.value`: on a cold start from a notification tap the app-scoped StateFlow
        // may not have its first value yet, and the tap would do nothing.
        val job = scheduler.observe(jobId).first()
        if (job == null || !scheduler.claimDelivery(jobId, via)) {
            logger.info(TAG, "onOpen($jobId, $via): already delivered or gone — nothing to do")
            return null
        }
        notifier.cancel(jobId)
        return when (val status = job.status) {
            is AnalysisJobStatus.Succeeded -> when (val outcome = status.outcome) {
                is AnalysisJobOutcome.CreatedChecklist -> {
                    runCreateHooks(job, outcome.checklistId)
                    track(via, OUTCOME_CHECKLIST)
                    scheduler.acknowledge(jobId)
                    ReadyDestination.ChecklistDetail(outcome.checklistId)
                }
                is AnalysisJobOutcome.FillReady -> {
                    val target = job.target as? AnalysisJobTarget.Fill
                    AnalyzeResultHolder.set(
                        items = outcome.suggestedItems,
                        suggestedName = defaultFillName(),
                        summary = outcome.summary,
                        isFillMode = target?.isFillMode ?: true,
                        fillDefault = target?.fillDefault ?: false,
                        targetChecklistId = target?.checklistId,
                        targetChecklistName = target?.checklistName,
                        fillDefaultItems = outcome.fillItems,
                        hasFolders = false,
                        fromActivation = false,
                    )
                    track(via, OUTCOME_FILL)
                    scheduler.acknowledge(jobId)
                    ReadyDestination.FillPreview
                }
            }
            is AnalysisJobStatus.Failed -> {
                track(via, OUTCOME_FAILED)
                // NOT acknowledged: the Analyze screen attaches to this job and shows it Failed with its
                // input; "Try again" reuses the captured payload, "Change input" acknowledges it.
                handoff.offer(jobId)
                job.toRetryDestination()
            }
            else -> {
                logger.warning(TAG, "onOpen($jobId): claimed a non-terminal job ${job.status}")
                null
            }
        }
    }

    override suspend fun onOpenSummary(jobIds: List<String>) {
        jobIds.forEach { jobId ->
            val job = scheduler.observe(jobId).first() ?: return@forEach
            if (!scheduler.claimDelivery(jobId, DeliveryClaimant.IN_APP_HOST)) return@forEach
            notifier.cancel(jobId)
            val outcome = (job.status as? AnalysisJobStatus.Succeeded)?.outcome
            if (outcome is AnalysisJobOutcome.CreatedChecklist) runCreateHooks(job, outcome.checklistId)
            track(DeliveryClaimant.IN_APP_HOST, OUTCOME_CHECKLIST)
            scheduler.acknowledge(jobId)
        }
    }

    /** Same split the screen used: only the activation hero counts as the first AI checklist. */
    private suspend fun runCreateHooks(job: AnalysisJob, checklistId: Long) {
        val hero = (job.target as? AnalysisJobTarget.NewChecklist)?.fromActivationHero == true
        if (hero) activationCoordinator.onAiChecklistCreated(checklistId)
        else activationCoordinator.onUserChecklistCreated(checklistId)
    }

    private fun track(via: DeliveryClaimant, outcome: String) {
        analyticsTracker.event(AnalyzeEventParams.EVENT_RESULT_OPENED, AnalyzeEventParams.resultOpened(via.wire, outcome))
    }

    private companion object {
        const val TAG = "AnalysisJob"
        const val OUTCOME_CHECKLIST = "checklist"
        const val OUTCOME_FILL = "fill"
        const val OUTCOME_FAILED = "failed"
    }
}

/** `via` of `ai_analyze_result_opened`. */
val DeliveryClaimant.wire: String
    get() = when (this) {
        DeliveryClaimant.SCREEN -> "screen"
        DeliveryClaimant.IN_APP_HOST -> "in_app"
        DeliveryClaimant.NOTIFICATION -> "notification"
    }

/** Where "Try again" / "Details" of a failed job leads: the same material, restored as far as possible. */
internal fun AnalysisJob.toRetryDestination(): ReadyDestination.AnalyzeRetry {
    val fill = target as? AnalysisJobTarget.Fill
    return ReadyDestination.AnalyzeRetry(
        jobId = id,
        fillChecklistId = fill?.checklistId,
        fillDefault = fill?.fillDefault ?: false,
    )
}

internal fun AnalysisJob.toReady(): ReadyAnalysis? = when (val status = status) {
    is AnalysisJobStatus.Succeeded -> when (val outcome = status.outcome) {
        is AnalysisJobOutcome.CreatedChecklist -> ReadyAnalysis.ChecklistCreated(id, outcome.checklistName)
        is AnalysisJobOutcome.FillReady ->
            ReadyAnalysis.FillReady(id, (target as? AnalysisJobTarget.Fill)?.checklistName.orEmpty())
    }
    is AnalysisJobStatus.Failed -> ReadyAnalysis.Failed(id, status.failure)
    else -> null
}
