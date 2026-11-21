package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analysis_notification_channel_progress
import aichecklists.core.designsystem.generated.resources.analysis_notification_channel_results
import aichecklists.core.designsystem.generated.resources.analysis_notification_channel_results_desc
import aichecklists.core.designsystem.generated.resources.analysis_notification_progress_title
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.jetbrains.compose.resources.getString

/**
 * Small icon of the analysis notifications. Bound by the app module (the drawable lives in its
 * resources); without a binding the launcher icon is used and a warning is logged.
 */
class AnalysisNotificationStyle(val smallIconRes: Int)

/**
 * Tap contract between [AndroidAnalysisReadyNotifier] and the app's launcher Activity.
 *
 * Its OWN action and extra on purpose: reusing the reminder's `ACTION_OPEN_CHECKLIST` would make
 * MainActivity count the tap as `reminder_notification_tapped` and navigate without a delivery claim.
 */
object AnalysisNotificationIntents {
    const val ACTION_OPEN_ANALYSIS_RESULT: String = "com.antonchuraev.aichecklists.action.OPEN_ANALYSIS_RESULT"
    const val EXTRA_ANALYSIS_JOB_ID: String = "analysis_job_id"

    /** The job id carried by an analysis-notification tap, or null for any other intent. */
    fun jobIdFrom(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN_ANALYSIS_RESULT) return null
        return intent.getStringExtra(EXTRA_ANALYSIS_JOB_ID)?.takeIf { it.isNotBlank() }
    }

    /**
     * PendingIntent to the launcher Activity (the app module's MainActivity; this library cannot name
     * it). NEW_TASK | CLEAR_TOP | SINGLE_TOP: a running MainActivity receives `onNewIntent` and keeps
     * its back stack; otherwise `onCreate` gets the intent. Null when the package has no launcher.
     */
    fun tapPendingIntent(context: Context, jobId: String): PendingIntent? {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
            ?: return null
        val intent = Intent(ACTION_OPEN_ANALYSIS_RESULT).apply {
            component = launcher
            putExtra(EXTRA_ANALYSIS_JOB_ID, jobId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            AnalysisNotifications.requestCode(jobId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal object AnalysisNotifications {
    /** Finished analyses: default importance (sound, no heads-up), one notification per job (tag = job id). */
    const val CHANNEL_RESULTS: String = "analysis_results"

    /** The ongoing notification WorkManager shows for expedited work on API ≤ 30 only. Silent. */
    const val CHANNEL_PROGRESS: String = "analysis_progress"

    /** Notification id of a result; the job id is the tag, so jobs never overwrite each other. */
    const val RESULT_NOTIFICATION_ID: Int = 7_401

    fun requestCode(jobId: String): Int = jobId.hashCode()

    /** Progress notifications need distinct ids per job (two expedited jobs can run at once on API ≤ 30). */
    fun progressNotificationId(jobId: String): Int = 7_500 + (jobId.hashCode() and 0x3FF)

    /**
     * Creates both channels (idempotent). NotificationChannel exists only on API 26+; referencing it on
     * API 24/25 throws NoClassDefFoundError (the reminder-channel precedent), hence the guard.
     */
    suspend fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                getString(Res.string.analysis_notification_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(Res.string.analysis_notification_channel_results_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                getString(Res.string.analysis_notification_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    suspend fun progressNotification(context: Context, smallIconRes: Int): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(smallIconRes)
            .setContentTitle(getString(Res.string.analysis_notification_progress_title))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** The configured icon, else the launcher icon (never 0 — a 0 small icon throws on post). */
    fun resolveSmallIcon(context: Context, style: AnalysisNotificationStyle?): Int =
        style?.smallIconRes?.takeIf { it != 0 } ?: context.applicationInfo.icon
}
