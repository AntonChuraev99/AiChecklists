package com.antonchuraev.homesearchchecklist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Reschedules all active reminders after device reboot.
 * AlarmManager alarms are lost when the device powers off.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler: ChecklistReminderScheduler =
                    GlobalContext.getOrNull()?.get() ?: return@launch
                scheduler.rescheduleAllActive()
            } catch (_: Exception) {
                // Non-critical — reminders will be rescheduled on next app launch
            } finally {
                pendingResult.finish()
            }
        }
    }
}
