package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

/** Active one-shot reminder from the checklists table (SQL projection, no Room annotation). */
data class ChecklistReminderInfo(val id: Long, val name: String, val reminderAt: Long)

/** Active repeat schedule from the checklists table (SQL projection, no Room annotation). */
data class ChecklistRepeatInfo(
    val id: Long,
    val name: String,
    val repeatNextAt: Long,
    val repeatRule: ReminderRepeatRule?,
    val repeatOccurrenceCount: Int
)
