package com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SyncRepository {

    val syncState: StateFlow<SyncState>

    fun observeCloudChecklistIds(): Flow<AppResult<List<String>>>

    fun observeCloudChecklist(cloudId: String): Flow<AppResult<Checklist>>

    suspend fun pushPendingChanges(): AppResult<Unit>

    suspend fun initialUpload(): AppResult<Unit>

    suspend fun pullAndMerge(): AppResult<Unit>

    suspend fun startListening()

    suspend fun stopListening()
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Error(val message: String) : SyncState
    data object Disabled : SyncState
}
