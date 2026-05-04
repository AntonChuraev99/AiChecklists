package com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository

import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import kotlinx.coroutines.flow.Flow

interface ChecklistRepository {
    // Checklists (templates)
    val checklists: Flow<List<Checklist>>
    suspend fun addChecklist(checklist: Checklist): Long
    suspend fun updateChecklist(checklist: Checklist)
    suspend fun updateChecklistTemplate(checklist: Checklist)
    suspend fun deleteChecklist(checklist: Checklist)
    suspend fun getChecklistById(id: Long): Checklist?
    suspend fun reorderChecklists(orderedIds: List<Long>)

    // Display preferences
    suspend fun setSeparateCompleted(checklistId: Long, value: Boolean)
    suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean)

    // One-shot reminders (independent of repeat)
    suspend fun setReminder(checklistId: Long, reminderAt: Long?)
    suspend fun countActiveReminders(): Int
    suspend fun getActiveReminders(): List<ChecklistReminderInfo>
    suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill?

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
