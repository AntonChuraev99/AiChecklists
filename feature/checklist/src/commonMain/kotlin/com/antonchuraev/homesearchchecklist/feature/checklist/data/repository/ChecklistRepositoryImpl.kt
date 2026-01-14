package com.antonchuraev.homesearchchecklist.feature.checklist.data.repository

import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toDomain
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
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
        return checklistDao.insert(checklist.toEntity())
    }

    override suspend fun updateChecklist(checklist: Checklist) {
        checklistDao.insert(checklist.toEntity())
    }

    override suspend fun deleteChecklist(checklist: Checklist) {
        checklistDao.deleteById(checklist.id)
    }

    override suspend fun getChecklistById(id: Long): Checklist? {
        return checklistDao.getById(id)?.toDomain()
    }

    // Fills (instances)
    override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> {
        return fillDao.observeFillsByChecklistId(checklistId).map { list ->
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

