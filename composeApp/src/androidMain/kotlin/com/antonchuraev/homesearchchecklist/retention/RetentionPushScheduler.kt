package com.antonchuraev.homesearchchecklist.retention

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.antonchuraev.homesearchchecklist.AppBuildConfig
import java.util.Calendar

/**
 * Schedules the two LOCAL retention auto-push alarms via [AlarmManager]. These are entirely separate
 * from the user-set reminder alarms owned by `ReminderScheduler` — different receiver, different
 * request-code namespace ([DAILY_REQUEST_CODE] / [DIGEST_REQUEST_CODE], both negative so they can
 * NEVER collide with the positive reminder ranges 100k/200k/300k), and they only ever set the
 * delivery TIME of our own pushes. User reminders are untouched.
 *
 *  - **Daily retention alarm** — fires once a day at the resolved delivery hour. The receiver picks
 *    at most one push (streak-save > overdue) and reschedules the alarm for the next day.
 *  - **Weekly digest alarm** — fires on [DIGEST_DAY_OF_WEEK] at the resolved hour; the receiver posts
 *    the weekly summary and reschedules +7 days.
 *
 * [scheduleAll] is idempotent (FLAG_UPDATE_CURRENT) so it is safe to call on every app start / boot.
 */
class RetentionPushScheduler(
    private val context: Context,
    private val timingResolver: PushTimingResolver,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** (Re)schedule both retention alarms at the current delivery hour. Safe to call repeatedly. */
    suspend fun scheduleAll() {
        timingResolver.ensureStickyArm()
        val hour = timingResolver.preferredHour()
        val now = System.currentTimeMillis()
        scheduleAlarm(nextDailyTriggerMillis(hour, now), dailyPendingIntent())
        scheduleAlarm(nextWeeklyTriggerMillis(hour, DIGEST_DAY_OF_WEEK, now), digestPendingIntent())
    }

    /** Reschedule ONLY the daily alarm for its next occurrence (called by the receiver after firing). */
    suspend fun rescheduleDaily() {
        val hour = timingResolver.preferredHour()
        scheduleAlarm(nextDailyTriggerMillis(hour, System.currentTimeMillis()), dailyPendingIntent())
    }

    /** Reschedule ONLY the weekly digest alarm (called by the receiver after firing). */
    suspend fun rescheduleDigest() {
        val hour = timingResolver.preferredHour()
        scheduleAlarm(
            nextWeeklyTriggerMillis(hour, DIGEST_DAY_OF_WEEK, System.currentTimeMillis()),
            digestPendingIntent(),
        )
    }

    // ─── D0->D1 come-back nudge (ONE-SHOT; no recurring/overdue precondition) ───

    /**
     * Arm the one-shot come-back alarm ~[COMEBACK_DELAY_HOURS]h from [nowMs], keyed to [checklistId]
     * (the user's first checklist). It shows at most once; the receiver may move it later (user seen
     * recently, see [ComebackGate]) but never re-arms it once consumed. On debug builds the delay
     * collapses to [COMEBACK_DEBUG_DELAY_MS] so the nudge can be exercised without waiting a day.
     * [checklistName] is carried as a fallback label; the receiver re-resolves the live name.
     *
     * @return the fire instant, to be persisted via [RetentionPrefs.markComebackScheduled].
     */
    fun scheduleComeback(checklistId: Long, checklistName: String, nowMs: Long): Long {
        val fireAt = nowMs + comebackDelayMs()
        scheduleAlarm(fireAt, comebackPendingIntent(checklistId, checklistName))
        return fireAt
    }

    /** Move the pending come-back to [atMs] (same request code — replaces the previous alarm). */
    fun rescheduleComebackTo(atMs: Long, checklistId: Long, checklistName: String) {
        scheduleAlarm(atMs, comebackPendingIntent(checklistId, checklistName))
    }

    /**
     * Re-register a still-PENDING come-back at its persisted fire instant — or [COMEBACK_REARM_MIN_BUFFER_MS]
     * from now if that already passed (device off, app force-stopped, alarm otherwise lost).
     * Idempotent (same request code + FLAG_UPDATE_CURRENT), so it is safe on every app start, boot
     * and package replace. A consumed come-back is never re-armed. Returns the trigger time, or null
     * when nothing was pending.
     */
    suspend fun rearmPendingComeback(prefs: RetentionPrefs, nowMs: Long): Long? {
        if (!prefs.isComebackPending()) return null
        val checklistId = prefs.comebackChecklistId() ?: return null
        val persistedFireAt = prefs.comebackFireAt()
        val dueAt = if (persistedFireAt > 0L) persistedFireAt else prefs.comebackArmedAt() + comebackDelayMs()
        val trigger = maxOf(dueAt, nowMs + COMEBACK_REARM_MIN_BUFFER_MS)
        scheduleAlarm(trigger, comebackPendingIntent(checklistId, ""))
        return trigger
    }

    /**
     * The single "restore every LOCAL retention alarm" entry point for app start, boot and package
     * replace: daily + digest via [scheduleAll], then a still-pending come-back via
     * [rearmPendingComeback]. Idempotent.
     */
    suspend fun restoreAll(prefs: RetentionPrefs, nowMs: Long) {
        scheduleAll()
        rearmPendingComeback(prefs, nowMs)
    }

    /** Cancel a pending come-back alarm (PendingIntent equality ignores extras — id/name irrelevant). */
    fun cancelComeback() {
        alarmManager.cancel(comebackPendingIntent(0L, ""))
    }

    /** Quiet period before the nudge: [COMEBACK_DELAY_MS] release, [COMEBACK_DEBUG_DELAY_MS] debug. */
    fun comebackDelayMs(): Long =
        if (AppBuildConfig.isDebug) COMEBACK_DEBUG_DELAY_MS else COMEBACK_DELAY_MS

    /** Latest a reschedule may land after arming; past it the user counts as retained. */
    fun comebackMaxWindowMs(): Long =
        if (AppBuildConfig.isDebug) COMEBACK_DEBUG_MAX_WINDOW_MS else COMEBACK_MAX_WINDOW_MS

    private fun comebackPendingIntent(checklistId: Long, checklistName: String): PendingIntent {
        val intent = Intent(context, RetentionPushReceiver::class.java).apply {
            action = RetentionPushReceiver.ACTION_RETENTION_COMEBACK
            putExtra(RetentionPushReceiver.EXTRA_COMEBACK_CHECKLIST_ID, checklistId)
            putExtra(RetentionPushReceiver.EXTRA_COMEBACK_CHECKLIST_NAME, checklistName)
        }
        return PendingIntent.getBroadcast(
            context,
            COMEBACK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ─── Trigger-time computation (device-local timezone via Calendar) ───

    /** Next occurrence of [hour]:00 local; today if still in the future, otherwise tomorrow. */
    internal fun nextDailyTriggerMillis(hour: Int, now: Long): Long {
        val cal = atHourToday(hour, now)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Next [dayOfWeek] (Calendar constant) at [hour]:00 local, strictly in the future. */
    internal fun nextWeeklyTriggerMillis(hour: Int, dayOfWeek: Int, now: Long): Long {
        val cal = atHourToday(hour, now)
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        // Calendar.set(DAY_OF_WEEK) may resolve to earlier this week — push forward until future.
        while (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
        return cal.timeInMillis
    }

    private fun atHourToday(hour: Int, now: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // ─── Alarm plumbing (mirrors ReminderScheduler.scheduleAlarm) ───

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    private fun dailyPendingIntent(): PendingIntent = broadcast(
        RetentionPushReceiver.ACTION_RETENTION_DAILY,
        DAILY_REQUEST_CODE,
    )

    private fun digestPendingIntent(): PendingIntent = broadcast(
        RetentionPushReceiver.ACTION_RETENTION_DIGEST,
        DIGEST_REQUEST_CODE,
    )

    private fun broadcast(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RetentionPushReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        // Dedicated NEGATIVE request-code namespace: guaranteed disjoint from the reminder scheduler's
        // positive ranges (checklist folds to small positive ints; item codes are absoluteValue+200k/300k,
        // always >= 200_000). A negative fixed code can never collide with either.
        const val DAILY_REQUEST_CODE = -1_000_001
        const val DIGEST_REQUEST_CODE = -1_000_002

        /** One-shot come-back alarm — disjoint negative request code (never collides with the above). */
        const val COMEBACK_REQUEST_CODE = -1_000_003

        /** Weekly digest fires on Sundays (Calendar.SUNDAY). */
        const val DIGEST_DAY_OF_WEEK = Calendar.SUNDAY

        /** Come-back nudge fires ~22h after the first checklist — squarely in the D0->D1 gap. */
        const val COMEBACK_DELAY_HOURS = 22
        const val COMEBACK_DELAY_MS = COMEBACK_DELAY_HOURS * 60L * 60L * 1000L

        /** Debug-only collapsed delay so the nudge is testable without waiting a day (20s). */
        const val COMEBACK_DEBUG_DELAY_MS = 20_000L

        /** If the persisted come-back instant already passed (reboot, lost alarm), fire this soon after. */
        const val COMEBACK_REARM_MIN_BUFFER_MS = 60_000L

        /** A reschedule never lands later than this after arming (D0->D3); past it -> already_active. */
        const val COMEBACK_MAX_WINDOW_MS = 72 * 60L * 60L * 1000L

        /** Debug counterpart of [COMEBACK_MAX_WINDOW_MS]: ~3x the collapsed debug delay. */
        const val COMEBACK_DEBUG_MAX_WINDOW_MS = 60_000L
    }
}
