package com.antonchuraev.homesearchchecklist.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Reschedules all active reminders when the exact alarm permission state changes.
 * When the user grants SCHEDULE_EXACT_ALARM, this upgrades all pending alarms
 * from inexact to exact. When revoked, they continue as inexact on next schedule.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler: ChecklistReminderScheduler =
                    GlobalContext.getOrNull()?.get() ?: return@launch
                scheduler.rescheduleAllActive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ExactAlarmReceiver", "Error rescheduling alarms", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
