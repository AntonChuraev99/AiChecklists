package com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler

interface ChecklistReminderScheduler {
    fun schedule(checklistId: Long, triggerAtMillis: Long)
    fun cancel(checklistId: Long)
    suspend fun rescheduleAllActive()

    /** Returns true if exact alarms can be scheduled (always true below API 31). */
    fun canScheduleExactAlarms(): Boolean = true

    /** Opens system settings for granting exact alarm permission. No-op below API 31. */
    fun openExactAlarmSettings() {}
}
