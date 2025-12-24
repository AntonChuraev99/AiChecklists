package com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import kotlinx.coroutines.flow.Flow

interface ChecklistRepository {
    val checklists: Flow<List<Checklist>>
    suspend fun addChecklist(checklist: Checklist)
    suspend fun deleteChecklist(checklist: Checklist)
}

