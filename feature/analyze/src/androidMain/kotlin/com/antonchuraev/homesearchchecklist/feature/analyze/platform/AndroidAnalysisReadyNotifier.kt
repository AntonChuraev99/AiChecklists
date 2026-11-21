package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analysis_notification_checklist_ready_title
import aichecklists.core.designsystem.generated.resources.analysis_notification_failed_body
import aichecklists.core.designsystem.generated.resources.analysis_notification_failed_title
import aichecklists.core.designsystem.generated.resources.analysis_notification_fill_ready_title
import aichecklists.core.designsystem.generated.resources.analysis_notification_tap_to_open
import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStatus
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobTarget
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.userFacingMessage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * What a finished job's notification says, decided without Android or resources (host-testable).
 * [text] is a value from the job (checklist name / server message); null = use the generic line.
 */
sealed interface AnalysisNotificationCopy {
    val text: String?

    data class ChecklistReady(override val text: String?) : AnalysisNotificationCopy
    data class FillReady(override val text: String?) : AnalysisNotificationCopy
    data class Failed(override val text: String?) : AnalysisNotificationCopy

    companion object {
        /** Null for a job that is not terminal or was cancelled — nothing to announce. */
        fun of(job: AnalysisJob): AnalysisNotificationCopy? = when (val status = job.status) {
            is AnalysisJobStatus.Succeeded -> when (val outcome = status.outcome) {
                is AnalysisJobOutcome.CreatedChecklist -> ChecklistReady(outcome.checklistName.blankToNull())
                is AnalysisJobOutcome.FillReady -> FillReady((job.target as? AnalysisJobTarget.Fill)?.checklistName.blankToNull())
            }
            is AnalysisJobStatus.Failed -> Failed(status.failure.serverMessage.blankToNull())
            else -> null
        }

        private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Android [AnalysisReadyNotifier]: one auto-cancel notification per job (tag = job id) on the
 * `analysis_results` channel; tapping it opens the launcher Activity with
 * [AnalysisNotificationIntents.EXTRA_ANALYSIS_JOB_ID], where `AnalysisReadySignal.onOpen(jobId,
 * NOTIFICATION)` claims the delivery. With notifications blocked (POST_NOTIFICATIONS denied, app or
 * channel toggled off) nothing is posted — the in-app snackbar delivers the result on return.
 */
class AndroidAnalysisReadyNotifier(
    context: Context,
    private val logger: AppLogger,
    private val smallIconRes: Int,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AnalysisReadyNotifier {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    private val crashGuard = CoroutineExceptionHandler { _, e ->
        logger.error(TAG, "analysis notification failed: ${e.message}", e)
    }

    override fun notifyFinished(job: AnalysisJob) {
        val copy = AnalysisNotificationCopy.of(job) ?: run {
            logger.warning(TAG, "notifyFinished(${job.id}) for status ${job.status::class.simpleName} — nothing to post")
            return
        }
        if (!manager.areNotificationsEnabled()) {
            logger.info(TAG, "notifications blocked — job ${job.id} is delivered in-app on return")
            return
        }
        scope.launch(crashGuard) { post(job.id, copy.localized(job)) }
    }

    /**
     * A failure reads the same localized reason the Analyze screen shows (DESIGN_SPEC G3) instead of the
     * raw server string, which is English and absent for local failures (unreadable file, network).
     * Resolved here, not in [AnalysisNotificationCopy.of], because resources are suspend and `of` stays a
     * pure host-tested decision.
     */
    private suspend fun AnalysisNotificationCopy.localized(job: AnalysisJob): AnalysisNotificationCopy {
        val failure = (job.status as? AnalysisJobStatus.Failed)?.failure ?: return this
        return AnalysisNotificationCopy.Failed(failure.userFacingMessage())
    }

    override fun cancel(jobId: String) {
        runCatching { manager.cancel(jobId, AnalysisNotifications.RESULT_NOTIFICATION_ID) }
            .onFailure { e -> logger.warning(TAG, "cancel notification of $jobId failed: ${e.message}") }
    }

    // areNotificationsEnabled() was checked; a revoke in between surfaces as SecurityException → logged.
    @SuppressLint("MissingPermission")
    private suspend fun post(jobId: String, copy: AnalysisNotificationCopy) {
        AnalysisNotifications.ensureChannels(appContext)
        val (title, text) = when (copy) {
            is AnalysisNotificationCopy.ChecklistReady ->
                getString(Res.string.analysis_notification_checklist_ready_title) to
                    (copy.text ?: getString(Res.string.analysis_notification_tap_to_open))
            is AnalysisNotificationCopy.FillReady ->
                getString(Res.string.analysis_notification_fill_ready_title) to
                    (copy.text ?: getString(Res.string.analysis_notification_tap_to_open))
            is AnalysisNotificationCopy.Failed ->
                getString(Res.string.analysis_notification_failed_title) to
                    (copy.text ?: getString(Res.string.analysis_notification_failed_body))
        }
        val builder = NotificationCompat.Builder(appContext, AnalysisNotifications.CHANNEL_RESULTS)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(text.take(MAX_TEXT))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val tap = AnalysisNotificationIntents.tapPendingIntent(appContext, jobId)
        if (tap != null) {
            builder.setContentIntent(tap)
        } else {
            logger.error(TAG, "no launcher activity — notification of $jobId posted without a tap action", IllegalStateException("no launch intent"))
        }
        runCatching { manager.notify(jobId, AnalysisNotifications.RESULT_NOTIFICATION_ID, builder.build()) }
            .onFailure { e -> logger.error(TAG, "posting notification of $jobId failed: ${e.message}", e) }
    }

    private companion object {
        const val TAG = "AnalysisJob"
        const val MAX_TEXT = 200
    }
}
