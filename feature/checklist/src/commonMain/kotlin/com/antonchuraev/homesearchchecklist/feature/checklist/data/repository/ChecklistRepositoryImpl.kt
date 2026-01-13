package com.antonchuraev.homesearchchecklist.feature.checklist.data.repository

import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toDomain
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.toEntity
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChecklistRepositoryImpl(
    private val dao: ChecklistDao
) : ChecklistRepository {

    override val checklists: Flow<List<Checklist>> = dao.observeChecklists().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun addChecklist(checklist: Checklist) {
        dao.insert(checklist.toEntity())
    }

    override suspend fun updateChecklist(checklist: Checklist) {
        dao.insert(checklist.toEntity())
    }

    override suspend fun deleteChecklist(checklist: Checklist) {
        dao.deleteById(checklist.id)
    }

    override suspend fun getChecklistById(id: Long): Checklist? {
        return dao.getById(id)?.toDomain()
    }
}

