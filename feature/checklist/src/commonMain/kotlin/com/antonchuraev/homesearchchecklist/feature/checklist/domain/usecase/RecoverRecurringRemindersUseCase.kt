package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.computeNextOccurrence
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler

/**
 * Recovers recurring reminders that were missed while the device was off.
 *
 * AlarmManager alarms are lost on reboot. For one-shot reminders,
 * [ChecklistReminderScheduler.rescheduleAllActive] handles rescheduling
 * (they are still in the future). Recurring reminders may have their
 * [reminderAt] in the past if they were due while the device was off.
 *
 * This use case finds all past-due recurring reminders and advances them
 * to the next future occurrence using [computeNextOccurrence].
 */
class RecoverRecurringRemindersUseCase(
    private val repository: ChecklistRepository,
    private val scheduler: ChecklistReminderScheduler
) {
    suspend operator fun invoke(nowMillis: Long) {
        val pastDue = repository.getPastDueRecurringReminders(nowMillis)

        pastDue.forEach { info ->
            val rule = info.repeatRule ?: return@forEach

            val nextMillis = rule.computeNextOccurrence(
                currentTriggerMillis = info.reminderAt,
                currentCount = info.repeatOccurrenceCount,
                nowMillis = nowMillis
            )

            if (nextMillis != null) {
                repository.advanceRecurringReminder(
                    checklistId = info.id,
                    nextReminderAt = nextMillis,
                    newCount = info.repeatOccurrenceCount + 1
                )
                scheduler.schedule(info.id, nextMillis)
            } else {
                // End condition reached while device was off
                repository.clearRecurringReminder(info.id)
            }
        }
    }
}
