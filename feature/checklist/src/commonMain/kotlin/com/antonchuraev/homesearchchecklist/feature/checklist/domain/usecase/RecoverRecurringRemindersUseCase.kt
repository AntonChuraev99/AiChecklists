package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.computeNextOccurrence
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler

/**
 * Recovers repeat schedules that were missed while the device was off.
 *
 * AlarmManager alarms are lost on reboot. For one-shot reminders,
 * [ChecklistReminderScheduler.rescheduleAllActiveReminders] handles rescheduling
 * (they are still in the future). Repeat schedules may have their
 * [repeatNextAt] in the past if they were due while the device was off.
 *
 * This use case finds all past-due repeat schedules and advances them
 * to the next future occurrence using [computeNextOccurrence].
 */
class RecoverRecurringRemindersUseCase(
    private val repository: ChecklistRepository,
    private val scheduler: ChecklistReminderScheduler
) {
    suspend operator fun invoke(nowMillis: Long) {
        val pastDue = repository.getPastDueRepeatSchedules(nowMillis)

        pastDue.forEach { info ->
            val rule = info.repeatRule ?: return@forEach

            val nextMillis = rule.computeNextOccurrence(
                currentTriggerMillis = info.repeatNextAt,
                currentCount = info.repeatOccurrenceCount,
                nowMillis = nowMillis
            )

            if (nextMillis != null) {
                repository.advanceRepeatSchedule(
                    checklistId = info.id,
                    nextAt = nextMillis,
                    newCount = info.repeatOccurrenceCount + 1
                )
                scheduler.scheduleRepeat(info.id, nextMillis)
            } else {
                // End condition reached while device was off
                repository.clearRepeatSchedule(info.id)
            }
        }
    }
}
