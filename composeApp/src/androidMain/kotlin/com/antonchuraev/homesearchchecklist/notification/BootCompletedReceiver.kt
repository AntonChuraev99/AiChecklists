package com.antonchuraev.homesearchchecklist.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.RecoverRecurringRemindersUseCase
import com.antonchuraev.homesearchchecklist.retention.RetentionPrefs
import com.antonchuraev.homesearchchecklist.retention.RetentionPushScheduler
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
 *
 * On MY_PACKAGE_REPLACED (our own app update) only the local retention alarms are re-registered.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> recoverAfterBoot()
            Intent.ACTION_MY_PACKAGE_REPLACED -> rearmRetentionAfterUpdate()
        }
    }

    /**
     * App update: user reminders are left to the existing app-start reschedule (re-running the boot
     * recovery here would advance past-due repeats for no reason). Only the retention alarms are
     * re-registered — idempotently — so a come-back armed by the previous build is guaranteed to be
     * pending at its persisted instant after the update. goAsync keeps the process alive until the
     * writes land; Application.onCreate's own re-arm runs in a scope that a process cached right
     * after this broadcast may never finish.
     */
    private fun rearmRetentionAfterUpdate() {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull()
                if (koin == null) {
                    // Koin starts synchronously in Application.onCreate, so this should be unreachable;
                    // if it happens, a pending come-back is NOT re-armed — leave a trace.
                    val message = "package replace: Koin not started — retention alarms not re-armed"
                    runCatching { FirebaseCrashlytics.getInstance().log(message) }
                    Log.w(TAG, message)
                    return@launch
                }
                runCatching {
                    koin.get<RetentionPushScheduler>().restoreAll(koin.get<RetentionPrefs>(), System.currentTimeMillis())
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    val message = "Error re-arming retention alarms after package replace: ${e.message}"
                    koin.getOrNull<AppLogger>()?.error(TAG, message, e) ?: Log.e(TAG, message, e)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun recoverAfterBoot() {
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
                val now = System.currentTimeMillis()
                recoverUseCase(nowMillis = now)

                // 4. Reschedule per-item reminders (one-shot + repeat) that are still in the future
                val repository: ChecklistRepository = koin.get()
                val itemReminders = repository.getAllItemRemindersForRescheduling()
                for (info in itemReminders) {
                    val reminderAt = info.reminderAt
                    if (reminderAt != null && reminderAt > now) {
                        scheduler.scheduleItemReminder(
                            info.checklistId, info.fillId, info.itemId, reminderAt
                        )
                    }
                    val repeatNextAt = info.repeatNextAt
                    if (info.repeatRule != null && repeatNextAt != null && repeatNextAt > now) {
                        scheduler.scheduleItemRepeat(
                            info.checklistId, info.fillId, info.itemId, repeatNextAt
                        )
                    }
                }

                // 5. Re-register the LOCAL retention auto-push alarms (daily + weekly digest) — like
                // all AlarmManager alarms they are cleared on power-off and must be restored on boot —
                // and re-arm the one-shot D0->D1 come-back ONLY if it is still pending, at its
                // persisted fire instant (or shortly if that already passed).
                koin.get<RetentionPushScheduler>().restoreAll(koin.get<RetentionPrefs>(), now)
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
