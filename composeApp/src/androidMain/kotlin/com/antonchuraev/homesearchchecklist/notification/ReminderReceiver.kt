package com.antonchuraev.homesearchchecklist.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.antonchuraev.aichecklists.R
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
        if (checklistId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository: ChecklistRepository =
                    GlobalContext.getOrNull()?.get() ?: return@launch

                val checklist = repository.getChecklistById(checklistId) ?: return@launch
                val defaultFill = repository.getDefaultFillOneShot(checklistId)
                val uncheckedCount = defaultFill?.items?.count { !it.checked } ?: 0

                repository.setReminder(checklistId, null)

                showNotification(context, checklistId, checklist.name, uncheckedCount)
            } catch (_: Exception) {
                // Do not crash the receiver
            } finally {
                pendingResult.finish()
            }
        }
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
                ReminderScheduler.checklistIdToRequestCode(checklistId),
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
            ReminderScheduler.checklistIdToRequestCode(checklistId),
            notification
        )
    }

    companion object {
        const val ACTION_FIRE = "com.antonchuraev.aichecklists.ACTION_REMINDER_FIRE"
        const val EXTRA_CHECKLIST_ID = "checklist_id"
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
