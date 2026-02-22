package com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler

interface ChecklistReminderScheduler {
    fun schedule(checklistId: Long, triggerAtMillis: Long)
    fun cancel(checklistId: Long)
    suspend fun rescheduleAllActive()
}
