package com.antonchuraev.homesearchchecklist.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler

class ReminderScheduler(
    private val context: Context,
    private val repository: ChecklistRepository
) : ChecklistReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(checklistId: Long, triggerAtMillis: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            checklistIdToRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    override fun cancel(checklistId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            checklistIdToRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    override suspend fun rescheduleAllActive() {
        val reminders = repository.getActiveReminders()
        reminders.forEach { reminder ->
            schedule(reminder.id, reminder.reminderAt)
        }
    }

    companion object {
        fun checklistIdToRequestCode(checklistId: Long): Int {
            return (checklistId xor (checklistId ushr 32)).toInt()
        }
    }
}
