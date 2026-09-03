package com.antonchuraev.homesearchchecklist.retention

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.antonchuraev.aichecklists.R
import com.antonchuraev.homesearchchecklist.AppBuildConfig
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import com.antonchuraev.homesearchchecklist.push.PushAnalytics
import com.antonchuraev.homesearchchecklist.push.PushNotificationChannels
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fires the LOCAL retention auto-pushes scheduled by [RetentionPushScheduler].
 *
 * Two entry actions:
 *  - [ACTION_RETENTION_DAILY] — evaluates streak-save (highest value) then overdue, shows AT MOST one
 *    (subject to the daily frequency cap), and reschedules the daily alarm.
 *  - [ACTION_RETENTION_DIGEST] — posts the weekly cross-checklist summary and reschedules +7 days.
 *
 * All notification/analytics plumbing is shared with the FCM path: the tap deep-link reuses
 * [ReminderReceiver.ACTION_OPEN_CHECKLIST] and [PushAnalytics.writeExtras] marks the intent as a push,
 * so `MainActivity` emits [AnalyticsEvents.Push.OPENED] on tap AND suppresses the local
 * `reminder_notification_tapped` double-count — no MainActivity change needed. The "shown" side is
 * emitted here as [AnalyticsEvents.Push.RECEIVED]; the local-exclusive `push_type` values
 * ([AnalyticsEvents.LocalPushType]) keep this separable from server pushes in the shared funnel.
 *
 * Cold-process safe: every dependency resolves through `GlobalContext.getOrNull()`; if Koin has not
 * started (a push alarm woke a cold process) the handler logs and no-ops instead of crashing.
 */
class RetentionPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RETENTION_DAILY -> handleDaily(context)
            ACTION_RETENTION_DIGEST -> handleDigest(context)
            ACTION_RETENTION_COMEBACK -> handleComeback(context, intent)
        }
    }

    private fun handleDaily(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = resolveDeps() ?: return@launch
                // Always keep the daily chain alive first, even if we skip showing anything today.
                runCatching { deps.scheduler.rescheduleDaily() }
                    .onFailure { deps.logger.warning(TAG, "rescheduleDaily failed: ${it.message}") }

                val dateKey = todayKey()
                if (!deps.prefs.canShowOn(dateKey)) {
                    deps.logger.debug(TAG, "daily: frequency cap hit for $dateKey — skipping")
                    return@launch
                }

                val spec = findStreakSave(deps) ?: findOverdue(deps)
                if (spec == null) {
                    deps.logger.debug(TAG, "daily: no streak/overdue candidate — nothing to show")
                    return@launch
                }
                showRetentionPush(context, deps, spec)
                deps.prefs.markShown(dateKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logSafe("daily handler crashed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDigest(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = resolveDeps() ?: return@launch
                runCatching { deps.scheduler.rescheduleDigest() }
                    .onFailure { deps.logger.warning(TAG, "rescheduleDigest failed: ${it.message}") }

                val dateKey = todayKey()
                if (!deps.prefs.canShowOn(dateKey)) {
                    deps.logger.debug(TAG, "digest: frequency cap hit for $dateKey — skipping")
                    return@launch
                }

                val spec = buildDigest(context, deps)
                if (spec == null) {
                    deps.logger.debug(TAG, "digest: no active checklists — nothing to summarize")
                    return@launch
                }
                showRetentionPush(context, deps, spec)
                deps.prefs.markShown(dateKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logSafe("digest handler crashed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * The D0->D1 come-back nudge (one-shot). Unlike daily/digest it has NO recurring/overdue
     * precondition — it only requires that the FIRST checklist still exists, still has open items,
     * and the user has not returned on their own since the alarm was armed. This is the whole point:
     * it covers the day-1 leak the other three structurally cannot reach.
     *
     * The alarm marks itself FIRED first (so a later boot never re-arms it), then runs the honest
     * gates, emitting [AnalyticsEvents.Retention.COMEBACK_SKIPPED] with a precise reason on any skip.
     *
     * Debug force path: a manual `am broadcast ... --ez $EXTRA_COMEBACK_FORCE true` (honored ONLY on
     * debuggable builds) bypasses every gate and shows for the newest checklist, so the nudge can be
     * exercised on demand without a fresh install or a 22h wait.
     */
    private fun handleComeback(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deps = resolveDeps()
                if (deps == null) {
                    // Cold-process silent-drop made VISIBLE. The alarm fired but Koin wasn't bound,
                    // so the nudge can't run. This used to return with ZERO signal — the prime suspect
                    // for scheduled(20) -> ~0 deliveries. Crashlytics is Koin-free, so record it there;
                    // scheduled - COMEBACK_FIRED - <this> = alarm-miss becomes computable.
                    runCatching { FirebaseCrashlytics.getInstance().log("comeback dropped: deps_not_ready (cold process)") }
                    android.util.Log.w(TAG, "comeback: dropped — deps not ready (cold process)")
                    return@launch
                }
                // Fire-side signal (warm path) — emitted before any gate so the funnel is complete.
                // Out-of-session throughout this receiver: an AlarmManager broadcast wakes the
                // process with no Activity, so a plain event would mint a phantom session_start.
                runCatching { deps.analytics.eventOutOfSession(AnalyticsEvents.Retention.COMEBACK_FIRED) }
                    .onFailure { deps.logger.warning(TAG, "comeback fired-event emit failed: ${it.message}") }

                val force = AppBuildConfig.isDebug && intent.getBooleanExtra(EXTRA_COMEBACK_FORCE, false)

                // One-shot: consume it now so a reboot can never re-arm a fired come-back.
                if (!force) deps.prefs.markComebackFired()

                if (!force && !notificationsEnabled(context)) {
                    emitSkipped(deps, REASON_PERMISSION_OFF)
                    return@launch
                }

                // Honest signal: the user already came back on their own after we armed — no nudge.
                if (!force) {
                    val armedAt = deps.prefs.comebackArmedAt()
                    val lastActive = deps.prefs.lastActiveMs()
                    if (armedAt > 0L && lastActive > armedAt) {
                        emitSkipped(deps, REASON_ALREADY_ACTIVE)
                        return@launch
                    }
                }

                val dateKey = todayKey()
                if (!force && !deps.prefs.canShowOn(dateKey)) {
                    emitSkipped(deps, REASON_FREQUENCY_CAP)
                    return@launch
                }

                // findComeback emits its own skip reason (no_checklist / no_unchecked) and returns null.
                val spec = findComeback(deps, intent, force) ?: return@launch
                showRetentionPush(context, deps, spec)
                if (!force) deps.prefs.markShown(dateKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logSafe("comeback handler crashed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Resolve the come-back target: the checklist id captured at arm time (intent extra), or — on a
     * debug force run with no id — the newest checklist. Shows only if it still exists and still has
     * unchecked items; otherwise emits the precise skip reason and returns null.
     */
    private suspend fun findComeback(deps: Deps, intent: Intent, force: Boolean): RetentionPushSpec? {
        val extraId = intent.getLongExtra(EXTRA_COMEBACK_CHECKLIST_ID, -1L).takeIf { it > 0L }
        // `projects`: the force path picks `maxByOrNull { it.id }`, which would ALWAYS be the
        // freshly auto-created system Inbox in the v2 arm — a push about a list the user never made.
        val checklists = deps.repository.projects.first()
        val target = when {
            extraId != null -> checklists.firstOrNull { it.id == extraId }
            force -> checklists.maxByOrNull { it.id }
            else -> null
        }
        if (target == null) {
            emitSkipped(deps, REASON_NO_CHECKLIST)
            return null
        }
        val fill = deps.repository.getDefaultFillOneShot(target.id)
        val unchecked = fill?.let { uncheckedCount(it) } ?: 0
        if (unchecked <= 0) {
            emitSkipped(deps, REASON_NO_UNCHECKED)
            return null
        }
        val context = deps.appContext
        val name = target.name.ifBlank { intent.getStringExtra(EXTRA_COMEBACK_CHECKLIST_NAME).orEmpty() }
        return RetentionPushSpec(
            pushType = AnalyticsEvents.LocalPushType.COMEBACK,
            channelToken = PushNotificationChannels.DATA_CHANNEL_REMINDERS,
            audienceClass = AUDIENCE_FUNCTIONAL,
            campaignId = CAMPAIGN_COMEBACK,
            title = context.getString(R.string.retention_comeback_title),
            body = context.resources.getQuantityString(
                R.plurals.retention_comeback_body, unchecked, unchecked, name,
            ),
            checklistId = target.id,
        )
    }

    private fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun emitSkipped(deps: Deps, reason: String) {
        deps.logger.debug(TAG, "comeback: skipped — reason=$reason")
        runCatching {
            deps.analytics.eventOutOfSession(
                AnalyticsEvents.Retention.COMEBACK_SKIPPED,
                mapOf(AnalyticsParams.REASON to reason),
            )
        }.onFailure { deps.logger.warning(TAG, "comeback skipped-event emit failed: ${it.message}") }
    }

    // ─── Candidate selection (honest signals computed from Room only) ───

    /**
     * Streak-save: a checklist with an active recurring schedule whose next fire is within ~a day
     * (i.e. a daily-habit list) that STILL has open items right now. No fake urgency — the copy
     * simply states the count.
     */
    private suspend fun findStreakSave(deps: Deps): RetentionPushSpec? {
        val now = System.currentTimeMillis()
        val schedules = deps.repository.getActiveRepeatSchedules()
        for (schedule in schedules) {
            val delta = schedule.repeatNextAt - now
            val dailyIsh = delta in 1..DAILY_ISH_MS
            if (!dailyIsh) continue
            val fill = deps.repository.getDefaultFillOneShot(schedule.id) ?: continue
            val unchecked = uncheckedCount(fill)
            if (unchecked <= 0) continue
            val context = deps.appContext
            return RetentionPushSpec(
                pushType = AnalyticsEvents.LocalPushType.STREAK_SAVE,
                channelToken = PushNotificationChannels.DATA_CHANNEL_REMINDERS,
                audienceClass = AUDIENCE_FUNCTIONAL,
                campaignId = CAMPAIGN_STREAK,
                title = context.getString(R.string.retention_streak_title),
                body = context.resources.getQuantityString(
                    R.plurals.retention_streak_body, unchecked, unchecked, schedule.name,
                ),
                checklistId = schedule.id,
            )
        }
        return null
    }

    /**
     * Overdue: a checklist left untouched for >= 1 day that still has open items (an abandoned
     * in-progress list). Uses [ChecklistFill.updatedAt] as the "last worked on" signal — reminder
     * timestamps can't be used because they clear/advance the moment the reminder fires.
     */
    private suspend fun findOverdue(deps: Deps): RetentionPushSpec? {
        val now = System.currentTimeMillis()
        // `projects`: an untouched auto-created Inbox is not an "abandoned in-progress list".
        val checklists = deps.repository.projects.first()
        var bestId: Long? = null
        var bestName = ""
        var bestUnchecked = 0
        var bestUpdatedAt = 0L
        for (checklist in checklists) {
            val fill = deps.repository.getDefaultFillOneShot(checklist.id) ?: continue
            if (fill.items.size < MIN_OVERDUE_ITEMS) continue
            if (fill.updatedAt >= now - ONE_DAY_MS) continue
            val unchecked = uncheckedCount(fill)
            if (unchecked <= 0) continue
            val better = unchecked > bestUnchecked ||
                (unchecked == bestUnchecked && fill.updatedAt > bestUpdatedAt)
            if (better) {
                bestId = checklist.id
                bestName = checklist.name
                bestUnchecked = unchecked
                bestUpdatedAt = fill.updatedAt
            }
        }
        val id = bestId ?: return null
        val context = deps.appContext
        return RetentionPushSpec(
            pushType = AnalyticsEvents.LocalPushType.OVERDUE,
            channelToken = PushNotificationChannels.DATA_CHANNEL_REMINDERS,
            audienceClass = AUDIENCE_FUNCTIONAL,
            campaignId = CAMPAIGN_OVERDUE,
            title = context.getString(R.string.retention_overdue_title),
            body = context.resources.getQuantityString(
                R.plurals.retention_overdue_body, bestUnchecked, bestUnchecked, bestName,
            ),
            checklistId = id,
        )
    }

    /**
     * Weekly digest: an honest cross-checklist summary. We do NOT fabricate a "done this week" count
     * (the model has no per-item completion timestamp); instead we report open items still waiting and
     * the number of active checklists. Promotional channel ("Tips & Offers") — independently mutable.
     */
    private suspend fun buildDigest(context: Context, deps: Deps): RetentionPushSpec? {
        val now = System.currentTimeMillis()
        // `projects`: `checklists.size` below goes straight into a user-visible plural ("N active
        // checklists"). Counting the hidden system Inbox would show a number the user cannot
        // reconcile with their own list — and would inflate the digest count in the v2 arm only.
        val checklists = deps.repository.projects.first()
        if (checklists.isEmpty()) return null
        var openItems = 0
        for (checklist in checklists) {
            val fill = deps.repository.getDefaultFillOneShot(checklist.id) ?: continue
            // Count "still open" only in lists that have been sitting untouched for a day+ — a fresh
            // list the user is actively working today shouldn't inflate the "waiting" number.
            if (fill.updatedAt >= now - ONE_DAY_MS) continue
            openItems += uncheckedCount(fill)
        }
        val activeCount = checklists.size
        val body = if (openItems > 0) {
            context.resources.getQuantityString(
                R.plurals.retention_digest_body_open, openItems, openItems, activeCount,
            )
        } else {
            context.getString(R.string.retention_digest_body_caught_up, activeCount)
        }
        return RetentionPushSpec(
            pushType = AnalyticsEvents.LocalPushType.DIGEST,
            channelToken = PushNotificationChannels.DATA_CHANNEL_PROMO,
            audienceClass = AUDIENCE_PROMOTIONAL,
            campaignId = CAMPAIGN_DIGEST,
            title = context.getString(R.string.retention_digest_title),
            body = body,
            checklistId = null,
        )
    }

    // Fill items are always flat checkable leaves (folder structure lives only on the template),
    // so "open" is simply the unchecked count.
    private fun uncheckedCount(fill: ChecklistFill): Int = fill.items.count { !it.checked }

    // ─── Notification + analytics (shared with the FCM path via PushAnalytics) ───

    private fun showRetentionPush(context: Context, deps: Deps, spec: RetentionPushSpec) {
        val channelId = PushNotificationChannels.channelIdFor(spec.channelToken)
        val isReminders = spec.channelToken == PushNotificationChannels.DATA_CHANNEL_REMINDERS
        val arm = deps.timingResolver.arm()

        val contentPendingIntent = buildContentPendingIntent(context, spec, arm)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_checkbox_checked)
            .setContentTitle(spec.title.take(200))
            .setContentText(spec.body.take(400))
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body.take(400)))
            .setPriority(if (isReminders) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .apply { if (isReminders) setCategory(NotificationCompat.CATEGORY_REMINDER) }
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(RETENTION_NOTIFICATION_ID, notification)

        // "Shown" event — mirrors GistiFirebaseMessagingService.emitReceived, distinguished by push_type.
        runCatching {
            deps.analytics.eventOutOfSession(
                AnalyticsEvents.Push.RECEIVED,
                PushAnalytics.params(
                    campaignId = spec.campaignId,
                    pushType = spec.pushType,
                    channel = spec.channelToken,
                    audienceClass = spec.audienceClass,
                    abExperiment = PushTimingResolver.EXPERIMENT_TIMING,
                    abArm = arm,
                ),
            )
        }.onFailure { deps.logger.warning(TAG, "shown-event emit failed: ${it.message}") }
    }

    private fun buildContentPendingIntent(
        context: Context,
        spec: RetentionPushSpec,
        arm: String,
    ): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            spec.checklistId?.let { id ->
                action = ReminderReceiver.ACTION_OPEN_CHECKLIST
                putExtra(ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID, id)
            }
            PushAnalytics.writeExtras(
                intent = this,
                campaignId = spec.campaignId,
                pushType = spec.pushType,
                channel = spec.channelToken,
                audienceClass = spec.audienceClass,
                abExperiment = PushTimingResolver.EXPERIMENT_TIMING,
                abArm = arm,
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (spec.checklistId != null) {
            TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(contentIntent)
                getPendingIntent(RETENTION_NOTIFICATION_ID, flags)!!
            }
        } else {
            PendingIntent.getActivity(context, RETENTION_NOTIFICATION_ID, contentIntent, flags)
        }
    }

    // ─── Dependency resolution (cold-process safe) ───

    private data class Deps(
        val appContext: Context,
        val repository: ChecklistRepository,
        val prefs: RetentionPrefs,
        val timingResolver: PushTimingResolver,
        val scheduler: RetentionPushScheduler,
        val analytics: AnalyticsTracker,
        val logger: AppLogger,
    )

    private fun resolveDeps(): Deps? {
        val koin = GlobalContext.getOrNull() ?: return null
        return runCatching {
            Deps(
                appContext = koin.get(),
                repository = koin.get(),
                prefs = koin.get(),
                timingResolver = koin.get(),
                scheduler = koin.get(),
                analytics = koin.get(),
                logger = koin.get(),
            )
        }.getOrElse {
            // Cold process before Koin finished binding — skip this pass; app start reschedules.
            android.util.Log.w(TAG, "resolveDeps: dependencies not ready — ${it.message}")
            null
        }
    }

    private fun logSafe(message: String, e: Throwable) {
        GlobalContext.getOrNull()?.getOrNull<AppLogger>()?.error(TAG, message, e)
            ?: android.util.Log.e(TAG, message, e)
    }

    private fun todayKey(): String = DATE_KEY_FORMAT.format(Date(System.currentTimeMillis()))

    private data class RetentionPushSpec(
        val pushType: String,
        val channelToken: String,
        val audienceClass: String,
        val campaignId: String,
        val title: String,
        val body: String,
        val checklistId: Long?,
    )

    companion object {
        private const val TAG = "RetentionPush"

        const val ACTION_RETENTION_DAILY = "com.antonchuraev.aichecklists.ACTION_RETENTION_DAILY"
        const val ACTION_RETENTION_DIGEST = "com.antonchuraev.aichecklists.ACTION_RETENTION_DIGEST"
        const val ACTION_RETENTION_COMEBACK = "com.antonchuraev.aichecklists.ACTION_RETENTION_COMEBACK"

        // Come-back alarm extras (namespaced; the id/name are captured at arm time, name re-resolved
        // live at fire time). EXTRA_COMEBACK_FORCE is honored ONLY on debuggable builds — the debug
        // on-demand test hook that bypasses the honest gates.
        const val EXTRA_COMEBACK_CHECKLIST_ID = "gisti.comeback.checklist_id"
        const val EXTRA_COMEBACK_CHECKLIST_NAME = "gisti.comeback.checklist_name"
        const val EXTRA_COMEBACK_FORCE = "gisti.comeback.force"

        /** Single notification id so a new retention push replaces the previous one (never stacks). */
        private const val RETENTION_NOTIFICATION_ID = 990_001

        private const val AUDIENCE_FUNCTIONAL = "functional"
        private const val AUDIENCE_PROMOTIONAL = "promotional"

        // Synthetic campaign ids: double as the PushAnalytics.isPushIntent marker + the campaign_id
        // analytics dimension so local pushes are individually sliceable alongside server campaigns.
        private const val CAMPAIGN_STREAK = "local_streak_save"
        private const val CAMPAIGN_OVERDUE = "local_overdue"
        private const val CAMPAIGN_DIGEST = "local_weekly_digest"
        private const val CAMPAIGN_COMEBACK = "local_comeback"

        // COMEBACK_SKIPPED reasons (wire values — keep in sync with AnalyticsEvents.Retention doc).
        private const val REASON_PERMISSION_OFF = "permission_off"
        private const val REASON_NO_CHECKLIST = "no_checklist"
        private const val REASON_ALREADY_ACTIVE = "already_active"
        private const val REASON_FREQUENCY_CAP = "frequency_cap"
        private const val REASON_NO_UNCHECKED = "no_unchecked"

        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        /** A recurring schedule counts as "daily-ish" if its next fire is within ~26h (drift tolerant). */
        private const val DAILY_ISH_MS = 26 * 60 * 60 * 1000L

        /** Don't nudge trivial one/zero-item lists as "overdue". */
        private const val MIN_OVERDUE_ITEMS = 2

        private val DATE_KEY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
