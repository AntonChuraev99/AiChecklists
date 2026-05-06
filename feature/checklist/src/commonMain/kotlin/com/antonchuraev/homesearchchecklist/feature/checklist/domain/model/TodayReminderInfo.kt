package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

/**
 * A single reminder entry visible on the Today screen.
 *
 * Produced by [com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository.observeRemindersInRange].
 * The Today ViewModel maps these to [com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayReminderItem]
 * for display.
 *
 * Two variants:
 * - [ChecklistLevel] — the checklist itself has a one-shot or recurring reminder that fires today.
 * - [ItemLevel] — a per-item reminder within a default fill fires today.
 *
 * @param reminderAt Epoch millis of the trigger (one-shot or next recurring occurrence).
 */
sealed interface TodayReminderInfo {

    val checklistId: Long
    val checklistName: String
    val reminderAt: Long
    val isRecurring: Boolean

    data class ChecklistLevel(
        override val checklistId: Long,
        override val checklistName: String,
        override val reminderAt: Long,
        override val isRecurring: Boolean,
    ) : TodayReminderInfo

    data class ItemLevel(
        override val checklistId: Long,
        override val checklistName: String,
        val fillId: Long,
        val itemId: String,
        val itemText: String,
        override val reminderAt: Long,
        override val isRecurring: Boolean,
    ) : TodayReminderInfo
}
