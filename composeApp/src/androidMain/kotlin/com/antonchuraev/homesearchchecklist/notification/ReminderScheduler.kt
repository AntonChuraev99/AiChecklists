package com.antonchuraev.homesearchchecklist.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler

class ReminderScheduler(
    private val context: Context,
    private val repository: ChecklistRepository
) : ChecklistReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ─── One-shot reminders ───

    override fun scheduleReminder(checklistId: Long, triggerAtMillis: Long) {
        val pendingIntent = createReminderPendingIntent(checklistId)
        scheduleAlarm(triggerAtMillis, pendingIntent)
    }

    override fun cancelReminder(checklistId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    override suspend fun rescheduleAllActiveReminders() {
        val reminders = repository.getActiveReminders()
        reminders.forEach { reminder ->
            scheduleReminder(reminder.id, reminder.reminderAt)
        }
    }

    // ─── Independent repeat schedule ───

    override fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long) {
        val pendingIntent = createRepeatPendingIntent(checklistId)
        scheduleAlarm(triggerAtMillis, pendingIntent)
    }

    override fun cancelRepeat(checklistId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REPEAT_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            repeatRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    override suspend fun rescheduleAllActiveRepeats() {
        val repeats = repository.getActiveRepeatSchedules()
        repeats.forEach { repeat ->
            scheduleRepeat(repeat.id, repeat.repeatNextAt)
        }
    }

    // ─── Permissions ───

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ─── Internal helpers ───

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun createReminderPendingIntent(checklistId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createRepeatPendingIntent(checklistId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REPEAT_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
        }
        return PendingIntent.getBroadcast(
            context,
            repeatRequestCode(checklistId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REPEAT_OFFSET = 100_000

        fun reminderRequestCode(checklistId: Long): Int {
            return (checklistId xor (checklistId ushr 32)).toInt()
        }

        fun repeatRequestCode(checklistId: Long): Int {
            return reminderRequestCode(checklistId) + REPEAT_OFFSET
        }
    }
}
