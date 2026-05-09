package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

/**
 * Lightweight projection used by [com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository.getAllItemRemindersForRescheduling]
 * and the Phase-2 BootCompletedReceiver to reschedule all per-item alarms after device reboot.
 *
 * Mirrors the structure of [com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo]
 * at the checklist level, but scoped to a specific fill item.
 *
 * @param checklistId  Parent checklist id — used for deep-link in the notification tap target.
 * @param fillId       Fill (instance) id — used to load and update the correct fill.
 * @param itemId       Stable string id of the [ChecklistFillItem] within the fill.
 * @param reminderAt   One-shot trigger epoch millis; null if no pending one-shot.
 * @param repeatRule   Recurring schedule; null if not recurring.
 * @param repeatNextAt Next repeat trigger epoch millis; null if no active repeat.
 * @param repeatOccurrenceCount How many times this item's repeat has already fired.
 */
data class ItemReminderInfo(
    val checklistId: Long,
    val fillId: Long,
    val itemId: String,
    val reminderAt: Long?,
    val repeatRule: ReminderRepeatRule?,
    val repeatNextAt: Long?,
    val repeatOccurrenceCount: Int
)
