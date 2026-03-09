package com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler

interface ChecklistReminderScheduler {
    // One-shot reminders
    fun scheduleReminder(checklistId: Long, triggerAtMillis: Long)
    fun cancelReminder(checklistId: Long)
    suspend fun rescheduleAllActiveReminders()

    // Independent repeat schedule
    fun scheduleRepeat(checklistId: Long, triggerAtMillis: Long)
    fun cancelRepeat(checklistId: Long)
    suspend fun rescheduleAllActiveRepeats()

    /** Returns true if exact alarms can be scheduled (always true below API 31). */
    fun canScheduleExactAlarms(): Boolean = true

    /** Opens system settings for granting exact alarm permission. No-op below API 31. */
    fun openExactAlarmSettings() {}

    /** Returns true if the app can post notifications (always true below API 33). */
    fun hasNotificationPermission(): Boolean = true
}
