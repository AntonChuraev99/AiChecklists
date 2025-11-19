package com.antonchuraev.homesearchchecklist.data.repository

import com.antonchuraev.homesearchchecklist.data.local.room.ChecklistDao
import com.antonchuraev.homesearchchecklist.data.local.room.toDomain
import com.antonchuraev.homesearchchecklist.data.local.room.toEntity
import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChecklistRepository(
    private val checklistDao: ChecklistDao
) {

    val checklists: Flow<List<Checklist>> = checklistDao
        .observeChecklists()
        .map { list -> list.map { it.toDomain() } }

    suspend fun addChecklist(checklist: Checklist) {
        checklistDao.insert(checklist.toEntity())
    }

    suspend fun deleteChecklist(checklist: Checklist) {
        checklistDao.deleteById(checklist.id)
    }
}