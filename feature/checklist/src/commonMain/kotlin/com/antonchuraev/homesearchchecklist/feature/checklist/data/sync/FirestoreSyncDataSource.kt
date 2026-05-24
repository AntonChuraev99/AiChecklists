package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import kotlinx.coroutines.flow.Flow

interface FirestoreSyncDataSource {

    fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>>

    fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>>

    suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit>

    suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit>

    suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit>

    suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>>
}
