package com.antonchuraev.homesearchchecklist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.RecoverRecurringRemindersUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Reschedules all active alarms after device reboot.
 * AlarmManager alarms are lost when the device powers off.
 *
 * For one-shot reminders: [rescheduleAllActiveReminders] re-registers future alarms.
 * For repeat schedules: [rescheduleAllActiveRepeats] re-registers future repeat alarms,
 * and [RecoverRecurringRemindersUseCase] advances past-due occurrences.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val scheduler: ChecklistReminderScheduler = koin.get()

                // 1. Reschedule one-shot reminders that are still in the future
                scheduler.rescheduleAllActiveReminders()

                // 2. Reschedule repeat schedules that are still in the future
                scheduler.rescheduleAllActiveRepeats()

                // 3. Recover past-due repeat schedules (advance to next future occurrence)
                val recoverUseCase: RecoverRecurringRemindersUseCase = koin.get()
                recoverUseCase(nowMillis = System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error recovering reminders after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
