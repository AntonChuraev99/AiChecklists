package com.antonchuraev.homesearchchecklist.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.antonchuraev.aichecklists.R
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.computeNextOccurrence
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
        if (checklistId == -1L) return

        when (intent.action) {
            ACTION_REMINDER_FIRE -> handleOneShot(context, checklistId)
            ACTION_REPEAT_FIRE -> handleRepeat(context, checklistId)
            ACTION_ITEM_REMINDER_FIRE -> {
                val fillId = intent.getLongExtra(EXTRA_FILL_ID, -1L)
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                if (fillId == -1L) return
                handleItemOneShot(context, checklistId, fillId, itemId)
            }
            ACTION_ITEM_REPEAT_FIRE -> {
                val fillId = intent.getLongExtra(EXTRA_FILL_ID, -1L)
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
                if (fillId == -1L) return
                handleItemRepeat(context, checklistId, fillId, itemId)
            }
        }
    }

    private fun handleOneShot(context: Context, checklistId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val repository: ChecklistRepository = koin.get()

                val checklist = repository.getChecklistById(checklistId) ?: return@launch
                val defaultFill = repository.getDefaultFillOneShot(checklistId)
                val uncheckedCount = defaultFill?.items?.count { !it.checked } ?: 0

                // One-shot: clear reminderAt after firing
                repository.setReminder(checklistId, null)

                showNotification(context, checklistId, checklist.name, uncheckedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing one-shot reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleRepeat(context: Context, checklistId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val repository: ChecklistRepository = koin.get()
                val analytics: AnalyticsTracker? = koin.getOrNull()

                val checklist = repository.getChecklistById(checklistId) ?: return@launch
                val defaultFill = repository.getDefaultFillOneShot(checklistId)
                val uncheckedCount = defaultFill?.items?.count { !it.checked } ?: 0
                val repeatRule = checklist.repeatRule ?: return@launch

                val now = System.currentTimeMillis()
                val nextOccurrence = repeatRule.computeNextOccurrence(
                    currentTriggerMillis = checklist.repeatNextAt ?: now,
                    currentCount = checklist.repeatOccurrenceCount,
                    nowMillis = now
                )

                if (nextOccurrence != null) {
                    val newCount = checklist.repeatOccurrenceCount + 1
                    repository.advanceRepeatSchedule(
                        checklistId = checklistId,
                        nextAt = nextOccurrence,
                        newCount = newCount
                    )
                    val scheduler: ChecklistReminderScheduler = koin.get()
                    scheduler.scheduleRepeat(checklistId, nextOccurrence)

                    analytics?.event(AnalyticsEvents.Reminder.RECURRING_FIRED, mapOf(
                        "checklist_id" to checklistId.toString(),
                        "occurrence_count" to newCount.toString(),
                        "next_at" to nextOccurrence.toString()
                    ))

                    // Auto-reset checkboxes if enabled
                    if (repeatRule.resetChecks) {
                        repository.resetDefaultFillChecks(checklistId)
                        analytics?.event(AnalyticsEvents.Reminder.RECURRING_CHECKS_RESET, mapOf(
                            "checklist_id" to checklistId.toString(),
                            "items_count" to (defaultFill?.items?.size ?: 0).toString()
                        ))
                    }
                } else {
                    // End condition reached — stop repeating
                    repository.clearRepeatSchedule(checklistId)
                    analytics?.event(AnalyticsEvents.Reminder.RECURRING_ENDED, mapOf(
                        "checklist_id" to checklistId.toString(),
                        "end_reason" to repeatRule.endCondition::class.simpleName.orEmpty(),
                        "total_occurrences" to checklist.repeatOccurrenceCount.toString()
                    ))
                }

                showNotification(context, checklistId, checklist.name, uncheckedCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing repeat reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleItemOneShot(context: Context, checklistId: Long, fillId: Long, itemId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val repository: ChecklistRepository = koin.get()

                val fill = repository.getFillById(fillId) ?: return@launch
                val item = fill.items.find { it.id == itemId } ?: return@launch

                // Guard: skip notification if item already completed
                if (item.checked) return@launch

                // Clear one-shot field so it doesn't re-fire after reboot rescheduling
                val updatedItems = fill.items.map { if (it.id == itemId) it.withReminderAt(null) else it }
                repository.updateFill(fill.copy(items = updatedItems))

                val checklist = repository.getChecklistById(checklistId)
                val checklistName = checklist?.name ?: context.getString(R.string.app_name)
                showItemNotification(context, checklistId, fillId, itemId, checklistName, item.text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing item one-shot reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleItemRepeat(context: Context, checklistId: Long, fillId: Long, itemId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val repository: ChecklistRepository = koin.get()
                val scheduler: ChecklistReminderScheduler = koin.get()

                val fill = repository.getFillById(fillId) ?: return@launch
                val item = fill.items.find { it.id == itemId } ?: return@launch

                // Guard: skip notification if item already completed
                if (item.checked) return@launch

                val repeatRule = item.repeatRule ?: return@launch
                val now = System.currentTimeMillis()
                val nextOccurrence = repeatRule.computeNextOccurrence(
                    currentTriggerMillis = item.repeatNextAt ?: now,
                    currentCount = item.repeatOccurrenceCount,
                    nowMillis = now
                )

                val updatedItem = if (nextOccurrence != null) {
                    // Advance to next occurrence
                    item.withRepeatAdvanced(
                        nextAt = nextOccurrence,
                        newCount = item.repeatOccurrenceCount + 1
                    )
                } else {
                    // End condition reached — stop repeating, clear schedule
                    item.withRepeatAdvanced(nextAt = null, newCount = item.repeatOccurrenceCount + 1)
                        .withReminderAt(null)
                }

                val updatedItems = fill.items.map { if (it.id == itemId) updatedItem else it }
                repository.updateFill(fill.copy(items = updatedItems))

                // Reschedule next occurrence if schedule continues
                if (nextOccurrence != null) {
                    scheduler.scheduleItemRepeat(checklistId, fillId, itemId, nextOccurrence)
                }

                val checklist = repository.getChecklistById(checklistId)
                val checklistName = checklist?.name ?: context.getString(R.string.app_name)
                showItemNotification(context, checklistId, fillId, itemId, checklistName, item.text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing item repeat reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showItemNotification(
        context: Context,
        checklistId: Long,
        fillId: Long,
        itemId: String,
        checklistName: String,
        itemText: String
    ) {
        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHECKLIST
            putExtra(EXTRA_NAVIGATE_CHECKLIST_ID, checklistId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(
                ReminderScheduler.itemReminderRequestCode(fillId, itemId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )!!
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_checkbox_checked)
            .setContentTitle(checklistName.take(200))
            .setContentText(itemText.take(200))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_checkbox_checked)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.reminder_notification_tap))
                    .build()
            )
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // Use a unique notification ID so item notifications don't overwrite checklist notifications
        notificationManager.notify(
            ReminderScheduler.itemReminderRequestCode(fillId, itemId),
            notification
        )
    }

    private fun showNotification(
        context: Context,
        checklistId: Long,
        checklistName: String,
        uncheckedCount: Int
    ) {
        val body = if (uncheckedCount == 0) {
            context.getString(R.string.reminder_all_completed)
        } else {
            context.resources.getQuantityString(
                R.plurals.reminder_items_remaining,
                uncheckedCount,
                uncheckedCount
            )
        }

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHECKLIST
            putExtra(EXTRA_NAVIGATE_CHECKLIST_ID, checklistId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(
                ReminderScheduler.reminderRequestCode(checklistId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )!!
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_checkbox_checked)
            .setContentTitle(checklistName.take(200))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_checkbox_checked)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.reminder_notification_tap))
                    .build()
            )
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            ReminderScheduler.reminderRequestCode(checklistId),
            notification
        )
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_REMINDER_FIRE = "com.antonchuraev.aichecklists.ACTION_REMINDER_FIRE"
        const val ACTION_REPEAT_FIRE = "com.antonchuraev.aichecklists.ACTION_REPEAT_FIRE"
        const val ACTION_ITEM_REMINDER_FIRE = "com.antonchuraev.aichecklists.ACTION_ITEM_REMINDER_FIRE"
        const val ACTION_ITEM_REPEAT_FIRE = "com.antonchuraev.aichecklists.ACTION_ITEM_REPEAT_FIRE"
        const val EXTRA_CHECKLIST_ID = "checklist_id"
        const val EXTRA_FILL_ID = "fill_id"
        const val EXTRA_ITEM_ID = "item_id"
        const val ACTION_OPEN_CHECKLIST = "com.antonchuraev.aichecklists.action.OPEN_CHECKLIST"
        const val EXTRA_NAVIGATE_CHECKLIST_ID = "navigate_to_checklist"
        private const val CHANNEL_ID = "checklist_reminders"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_notification_channel_desc)
                enableLights(true)
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
