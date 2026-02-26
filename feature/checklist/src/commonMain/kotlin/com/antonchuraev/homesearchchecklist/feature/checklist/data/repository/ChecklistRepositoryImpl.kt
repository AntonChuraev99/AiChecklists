package com.antonchuraev.homesearchchecklist.feature.checklist.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toDomain
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChecklistRepositoryImpl(
    private val checklistDao: ChecklistDao,
    private val fillDao: ChecklistFillDao
) : ChecklistRepository {

    // Checklists (templates)
    override val checklists: Flow<List<Checklist>> = checklistDao.observeChecklists().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun addChecklist(checklist: Checklist): Long {
        val checklistId = checklistDao.insert(checklist.toEntity())

        // Create default fill automatically
        val defaultFill = ChecklistFill(
            checklistId = checklistId,
            name = "",
            items = checklist.items.map { item ->
                ChecklistFillItem(
                    text = item.text,
                    checked = false,
                    note = null
                )
            },
            createdAt = currentTimeMillis(),
            isDefault = true
        )
        fillDao.insert(defaultFill.toEntity())

        return checklistId
    }

    override suspend fun updateChecklist(checklist: Checklist) {
        checklistDao.update(checklist.toEntity())

        // Sync default fill items with updated checklist items
        val defaultFillEntity = fillDao.getDefaultFillByChecklistId(checklist.id)
        if (defaultFillEntity != null) {
            val existingItemsMap = defaultFillEntity.items.associateBy { it.text }
            val updatedItems = checklist.items.map { checklistItem ->
                val existingItem = existingItemsMap[checklistItem.text]
                ChecklistFillItem(
                    text = checklistItem.text,
                    checked = existingItem?.checked ?: false,
                    note = existingItem?.note
                )
            }
            fillDao.insert(defaultFillEntity.copy(items = updatedItems))
        }
    }

    override suspend fun deleteChecklist(checklist: Checklist) {
        checklistDao.deleteById(checklist.id)
    }

    override suspend fun getChecklistById(id: Long): Checklist? {
        return checklistDao.getById(id)?.toDomain()
    }

    // Display preferences
    override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {
        checklistDao.setSeparateCompleted(checklistId, value)
    }

    // Reminders
    override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {
        checklistDao.updateReminder(checklistId, reminderAt)
    }

    override suspend fun countActiveReminders(): Int {
        return checklistDao.countActiveReminders(currentTimeMillis())
    }

    override suspend fun getActiveReminders(): List<ChecklistReminderInfo> {
        return checklistDao.getActiveReminders(currentTimeMillis())
    }

    override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? {
        return fillDao.getDefaultFillByChecklistId(checklistId)?.toDomain()
    }

    // Fills (instances)
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> {
        return fillDao.observeFillsByChecklistId(checklistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> {
        return fillDao.observeDefaultFillByChecklistId(checklistId).map { it?.toDomain() }
    }

    override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> {
        return fillDao.observeAdditionalFillsByChecklistId(checklistId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFillById(id: Long): ChecklistFill? {
        return fillDao.getById(id)?.toDomain()
    }

    override suspend fun getFillCountByChecklistId(checklistId: Long): Int {
        return fillDao.getCountByChecklistId(checklistId)
    }

    override suspend fun addFill(fill: ChecklistFill): Long {
        return fillDao.insert(fill.toEntity())
    }

    override suspend fun updateFill(fill: ChecklistFill) {
        fillDao.insert(fill.toEntity())
    }

    override suspend fun deleteFill(fill: ChecklistFill) {
        fillDao.deleteById(fill.id)
    }
}

