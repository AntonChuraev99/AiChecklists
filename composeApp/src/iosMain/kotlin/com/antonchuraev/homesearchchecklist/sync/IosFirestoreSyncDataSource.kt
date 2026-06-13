package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.ChecklistSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.UserDocSyncData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class IosFirestoreSyncDataSource : FirestoreSyncDataSource {
    override fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>> =
        flowOf(AppResult.Error(Exception("iOS Firestore not implemented")))

    override fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>> =
        flowOf(AppResult.Error(Exception("iOS Firestore not implemented")))

    override suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit> =
        AppResult.Error(Exception("iOS Firestore not implemented"))

    override suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit> =
        AppResult.Error(Exception("iOS Firestore not implemented"))

    override suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit> =
        AppResult.Error(Exception("iOS Firestore not implemented"))

    override suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>> =
        AppResult.Error(Exception("iOS Firestore not implemented"))

    override fun observeUserDoc(userId: String): Flow<AppResult<UserDocSyncData?>> =
        flowOf(AppResult.Error(Exception("iOS Firestore not implemented")))

    override suspend fun findUserIdByGoogleUid(googleUid: String): AppResult<String?> =
        AppResult.Error(Exception("iOS Firestore not implemented"))
}
