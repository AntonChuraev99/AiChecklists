package com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import kotlinx.coroutines.flow.Flow

interface ChecklistRepository {
    // Checklists (templates)
    val checklists: Flow<List<Checklist>>
    suspend fun addChecklist(checklist: Checklist): Long
    suspend fun updateChecklist(checklist: Checklist)
    suspend fun updateChecklistTemplate(checklist: Checklist)
    suspend fun deleteChecklist(checklist: Checklist)
    suspend fun getChecklistById(id: Long): Checklist?

    /**
     * Reactive view of a single checklist. Emits a fresh value every time the row changes
     * (e.g. name edited from the Edit screen, reminder set, repeat schedule updated).
     *
     * Use this in screens that must reflect template changes live; for one-shot snapshots
     * call [getChecklistById] instead.
     */
    fun observeChecklistById(id: Long): Flow<Checklist?>
    suspend fun reorderChecklists(orderedIds: List<Long>)

    // Display preferences
    suspend fun setSeparateCompleted(checklistId: Long, value: Boolean)
    suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean)

    /**
     * Enables or disables the per-checklist folders feature.
     *
     * Unlike [setSeparateCompleted]/[setAutoDeleteCompleted] (which only flip a column and
     * rely on a later unrelated edit to push), this also marks the checklist dirty via
     * [ChecklistDao.touchForSync] so the setting propagates to other devices promptly.
     */
    suspend fun setFoldersEnabled(checklistId: Long, value: Boolean)

    // One-shot reminders (independent of repeat)
    suspend fun setReminder(checklistId: Long, reminderAt: Long?)
    /**
     * Counts active one-shot reminders across both checklist-level and per-item scopes.
     *
     * Used by the free-tier gate for one-shot reminders. Per-item recurring schedules
     * are also included (each item with any active reminder counts as 1).
     * Checklist-level repeat schedules are counted separately via [countActiveRepeatSchedules]
     * (they have their own gate in ChecklistDetailViewModel).
     *
     * Active = checklist [reminderAt] in the future (one-shot)
     *        + items where [reminderAt] != null OR [repeatRule] != null.
     *
     * Note: item-level counting is done in-memory by scanning default fills
     * (items are serialized JSON, not queryable SQL columns).
     */
    suspend fun countActiveReminders(): Int
    suspend fun getActiveReminders(): List<ChecklistReminderInfo>
    suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill?

    // Per-item reminder persistence
    // Items are stored as JSON inside the fill row — no dedicated column.
    // All mutations go through [updateFill]; these helpers are convenience wrappers.

    /**
     * Returns all default fills that contain at least one item with an active reminder.
     * Used by [getAllItemRemindersForRescheduling] and the BootCompletedReceiver (Phase 2).
     */
    suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo>

    // Independent repeat schedule
    suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long)
    suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int)
    suspend fun clearRepeatSchedule(checklistId: Long)
    suspend fun resetDefaultFillChecks(checklistId: Long)
    suspend fun countActiveRepeatSchedules(): Int
    suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo>
    suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo>

    // Analytics
    suspend fun getTotalAdditionalFillCount(): Int

    // Weekly mode
    suspend fun getWeeklyChecklistCount(): Int
    val weeklyChecklistCount: Flow<Int>

    /**
     * Observes all reminders (checklist-level + per-item) that fall within [fromMs]..[toMs].
     *
     * Checklist-level: [Checklist.reminderAt] or [Checklist.repeatNextAt] in range.
     * Per-item: [ChecklistFillItem.reminderAt] or [ChecklistFillItem.repeatNextAt] in range.
     *
     * Emits a new list whenever the underlying checklists or fills change.
     * Consumers use this to drive the Today screen.
     */
    fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>>

    /**
     * One-shot version of [observeRemindersInRange] for use in suspend contexts.
     */
    suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo>

    // Priority
    /**
     * Toggles the priority of a single fill item between 0 (normal) and 1 (starred).
     *
     * Performs a dual update: both the fill (for the detail screen) and the checklist template
     * (for the edit screen) are updated atomically to keep them in sync.
     *
     * Returns [Result.failure] if the fill or item is not found.
     */
    suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit>

    // Attachments
    // Items are stored as JSON inside the fill row — no dedicated SQL column.
    // Both helpers follow the dual-update pattern: fill updated via updateFill();
    // attachments do NOT propagate to the checklist template (per-fill data only).

    /**
     * Appends [attachment] to [itemId] inside fill [fillId].
     * Internally loads the fill, calls [ChecklistFillItem.withAttachmentAdded], then [updateFill].
     */
    suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment)

    /**
     * Removes the attachment identified by [attachmentId] from [itemId] inside fill [fillId].
     * Internally loads the fill, calls [ChecklistFillItem.withAttachmentRemoved], then [updateFill].
     * No-op if [attachmentId] is not found.
     */
    suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String)

    // Fills (instances)
    fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>>
    fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?>
    fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>>
    suspend fun getFillById(id: Long): ChecklistFill?
    suspend fun getFillCountByChecklistId(checklistId: Long): Int
    suspend fun addFill(fill: ChecklistFill): Long
    suspend fun updateFill(fill: ChecklistFill)
    suspend fun deleteFill(fill: ChecklistFill)
}
