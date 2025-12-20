package com.antonchuraev.homesearchchecklist.feature.checklist

import com.antonchuraev.homesearchchecklist.core.common.api.Checklist
import com.antonchuraev.homesearchchecklist.core.database.ChecklistDao
import com.antonchuraev.homesearchchecklist.core.database.toDomain
import com.antonchuraev.homesearchchecklist.core.database.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChecklistRepository(
    private val dao: ChecklistDao
) {
    val checklists: Flow<List<Checklist>> = dao.observeChecklists().map { list -> list.map { it.toDomain() } }

    suspend fun addChecklist(checklist: Checklist) {
        dao.insert(checklist.toEntity())
    }

    suspend fun deleteChecklist(checklist: Checklist) {
        dao.deleteById(checklist.id)
    }
}

