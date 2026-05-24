package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.ChecklistSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FillSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of [FirestoreSyncDataSource] using the Firebase Android SDK.
 *
 * Collection layout:
 *   users/{userId}/checklists/{cloudId}
 *
 * Fills are stored as an array field inside the checklist document so that all fill data
 * travels with the checklist in a single Firestore read/write (no extra round-trips).
 */
class AndroidFirestoreSyncDataSource : FirestoreSyncDataSource {

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun checklistsRef(userId: String) =
        firestore.collection("users").document(userId).collection("checklists")

    private fun checklistRef(userId: String, cloudId: String) =
        checklistsRef(userId).document(cloudId)

    // ── Serialization ────────────────────────────────────────────────────────

    private fun FillSyncData.toMap(): Map<String, Any?> = mapOf(
        "cloudId" to cloudId,
        "name" to name,
        "itemsJson" to itemsJson,
        "coverImagePath" to coverImagePath,
        "createdAt" to createdAt,
        "isDefault" to isDefault,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted,
    )

    private fun ChecklistSyncData.toMap(): Map<String, Any?> = mapOf(
        "cloudId" to cloudId,
        "name" to name,
        "itemsJson" to itemsJson,
        "reminderAt" to reminderAt,
        "repeatRule" to repeatRule,
        "repeatTimeOfDayMinutes" to repeatTimeOfDayMinutes,
        "repeatNextAt" to repeatNextAt,
        "repeatOccurrenceCount" to repeatOccurrenceCount,
        "separateCompleted" to separateCompleted,
        "position" to position,
        "autoDeleteCompleted" to autoDeleteCompleted,
        "viewMode" to viewMode,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted,
        "fills" to fills.map { it.toMap() },
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toFillSyncData(): FillSyncData = FillSyncData(
        cloudId = this["cloudId"] as? String ?: "",
        name = this["name"] as? String ?: "",
        itemsJson = this["itemsJson"] as? String ?: "[]",
        coverImagePath = this["coverImagePath"] as? String,
        createdAt = (this["createdAt"] as? Long) ?: 0L,
        isDefault = (this["isDefault"] as? Boolean) ?: false,
        updatedAt = (this["updatedAt"] as? Long) ?: 0L,
        isDeleted = (this["isDeleted"] as? Boolean) ?: false,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toChecklistSyncData(documentId: String): ChecklistSyncData {
        val fillsList = (this["fills"] as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?.map { it.toFillSyncData() }
            ?: emptyList()

        return ChecklistSyncData(
            cloudId = documentId,
            name = this["name"] as? String ?: "",
            itemsJson = this["itemsJson"] as? String ?: "[]",
            reminderAt = this["reminderAt"] as? Long,
            repeatRule = this["repeatRule"] as? String,
            repeatTimeOfDayMinutes = (this["repeatTimeOfDayMinutes"] as? Long)?.toInt(),
            repeatNextAt = this["repeatNextAt"] as? Long,
            repeatOccurrenceCount = ((this["repeatOccurrenceCount"] as? Long) ?: 0L).toInt(),
            separateCompleted = (this["separateCompleted"] as? Boolean) ?: false,
            position = ((this["position"] as? Long) ?: 0L).toInt(),
            autoDeleteCompleted = (this["autoDeleteCompleted"] as? Boolean) ?: false,
            viewMode = this["viewMode"] as? String ?: "Standard",
            updatedAt = (this["updatedAt"] as? Long) ?: 0L,
            isDeleted = (this["isDeleted"] as? Boolean) ?: false,
            fills = fillsList,
        )
    }

    // ── Interface implementation ─────────────────────────────────────────────

    override fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>> = callbackFlow {
        val ref = checklistsRef(userId)
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(AppResult.Error(Exception(error.message ?: "Firestore snapshot error")))
                return@addSnapshotListener
            }
            val ids = snapshot?.documents?.map { it.id } ?: emptyList()
            trySend(AppResult.Success(ids))
        }
        awaitClose { listener.remove() }
    }

    override fun observeChecklist(
        userId: String,
        cloudId: String,
    ): Flow<AppResult<ChecklistSyncData>> = callbackFlow {
        val ref = checklistRef(userId, cloudId)
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(AppResult.Error(Exception(error.message ?: "Firestore snapshot error")))
                return@addSnapshotListener
            }
            val data = snapshot?.data
            if (data == null) {
                trySend(AppResult.Error(Exception("Checklist $cloudId not found")))
                return@addSnapshotListener
            }
            trySend(AppResult.Success(data.toChecklistSyncData(documentId = cloudId)))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun uploadChecklist(
        userId: String,
        data: ChecklistSyncData,
    ): AppResult<Unit> = runCatching {
        checklistRef(userId, data.cloudId)
            .set(data.toMap(), SetOptions.merge())
            .await()
        AppResult.Success(Unit)
    }.getOrElse { e ->
        AppResult.Error(Exception(e.message ?: "Upload failed", e))
    }

    override suspend fun deleteChecklist(
        userId: String,
        cloudId: String,
    ): AppResult<Unit> = runCatching {
        checklistRef(userId, cloudId).delete().await()
        AppResult.Success(Unit)
    }.getOrElse { e ->
        AppResult.Error(Exception(e.message ?: "Delete failed", e))
    }

    override suspend fun uploadBatch(
        userId: String,
        checklists: List<ChecklistSyncData>,
    ): AppResult<Unit> = runCatching {
        val batch = firestore.batch()
        for (data in checklists) {
            val ref = checklistRef(userId, data.cloudId)
            batch.set(ref, data.toMap(), SetOptions.merge())
        }
        batch.commit().await()
        AppResult.Success(Unit)
    }.getOrElse { e ->
        AppResult.Error(Exception(e.message ?: "Batch upload failed", e))
    }

    override suspend fun fetchAllChecklists(
        userId: String,
    ): AppResult<List<ChecklistSyncData>> = runCatching {
        val snapshot = checklistsRef(userId).get().await()
        val checklists = snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data.toChecklistSyncData(documentId = doc.id)
        }
        AppResult.Success(checklists)
    }.getOrElse { e ->
        AppResult.Error(Exception(e.message ?: "Fetch failed", e))
    }
}
