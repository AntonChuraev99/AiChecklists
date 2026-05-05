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
import kotlin.math.absoluteValue

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

    // ─── Per-item one-shot reminders ───

    override fun scheduleItemReminder(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {
        val pendingIntent = createItemReminderPendingIntent(checklistId, fillId, itemId)
        scheduleAlarm(triggerAtMillis, pendingIntent)
    }

    override fun cancelItemReminder(checklistId: Long, fillId: Long, itemId: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ITEM_REMINDER_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
            putExtra(ReminderReceiver.EXTRA_FILL_ID, fillId)
            putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemReminderRequestCode(fillId, itemId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    // ─── Per-item repeat schedule ───

    override fun scheduleItemRepeat(checklistId: Long, fillId: Long, itemId: String, triggerAtMillis: Long) {
        val pendingIntent = createItemRepeatPendingIntent(checklistId, fillId, itemId)
        scheduleAlarm(triggerAtMillis, pendingIntent)
    }

    override fun cancelItemRepeat(checklistId: Long, fillId: Long, itemId: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ITEM_REPEAT_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
            putExtra(ReminderReceiver.EXTRA_FILL_ID, fillId)
            putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemRepeatRequestCode(fillId, itemId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
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

    private fun createItemReminderPendingIntent(checklistId: Long, fillId: Long, itemId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ITEM_REMINDER_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
            putExtra(ReminderReceiver.EXTRA_FILL_ID, fillId)
            putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getBroadcast(
            context,
            itemReminderRequestCode(fillId, itemId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createItemRepeatPendingIntent(checklistId: Long, fillId: Long, itemId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ITEM_REPEAT_FIRE
            putExtra(ReminderReceiver.EXTRA_CHECKLIST_ID, checklistId)
            putExtra(ReminderReceiver.EXTRA_FILL_ID, fillId)
            putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getBroadcast(
            context,
            itemRepeatRequestCode(fillId, itemId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REPEAT_OFFSET = 100_000

        // Item request-code ranges:
        //   Item one-shot : abs("fillId:itemId".hashCode()) + 200_000
        //   Item repeat   : abs("fillId:itemId".hashCode()) + 300_000
        //
        // Checklist codes land near 0..Int.MAX_INT (from checklistId.toInt() fold).
        // Checklist repeat adds only +100_000, so still in the same vicinity.
        // Item codes are pushed to +200_000 / +300_000 which creates a dedicated
        // namespace far enough from the checklist range for practical collision safety.
        // Using the composite "fillId:itemId" string means different fills of the same
        // checklist with the same itemId get distinct codes.
        private const val ITEM_REMINDER_OFFSET = 200_000
        private const val ITEM_REPEAT_OFFSET = 300_000

        fun reminderRequestCode(checklistId: Long): Int {
            return (checklistId xor (checklistId ushr 32)).toInt()
        }

        fun repeatRequestCode(checklistId: Long): Int {
            return reminderRequestCode(checklistId) + REPEAT_OFFSET
        }

        fun itemReminderRequestCode(fillId: Long, itemId: String): Int {
            val composite = "$fillId:$itemId"
            return composite.hashCode().absoluteValue + ITEM_REMINDER_OFFSET
        }

        fun itemRepeatRequestCode(fillId: Long, itemId: String): Int {
            val composite = "$fillId:$itemId"
            return composite.hashCode().absoluteValue + ITEM_REPEAT_OFFSET
        }
    }
}
