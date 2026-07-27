package com.antonchuraev.homesearchchecklist.sync

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.ChecklistSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FillSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.GalleryTemplateItemData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.GalleryTemplateSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.UserDocSyncData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
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

    private fun checklistRef(userId: String, cloudId: String): DocumentReference {
        // A cloudId is a path SEGMENT, and Firestore silently collapses an empty one:
        // `users/{uid}/checklists/""` degrades to the 3-segment `users/{uid}/checklists`
        // (a collection), and the SDK then throws an IllegalArgumentException whose message
        // names neither the field nor the checklist. Failing here instead states the actual
        // problem. The repository resolves every identity up front (SyncRepositoryImpl
        // .ensureCloudId), so this is a contract check on callers, not flow control.
        require(cloudId.isNotBlank()) {
            "checklistRef: blank cloudId for user=$userId — the caller must resolve a cloud identity first"
        }
        return checklistsRef(userId).document(cloudId)
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

    override fun observeUserDoc(userId: String): Flow<AppResult<UserDocSyncData?>> = callbackFlow {
        val ref = firestore.collection("users").document(userId)
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(AppResult.Error(Exception(error.message ?: "Firestore snapshot error")))
                return@addSnapshotListener
            }
            val data = snapshot?.data
            trySend(
                AppResult.Success(
                    data?.let {
                        UserDocSyncData(
                            aiCredits = (it["ai_credits"] as? Number)?.toInt() ?: 0,
                            isPremium = (it["is_premium"] as? Boolean) ?: false,
                        )
                    }
                )
            )
        }
        awaitClose { listener.remove() }
    }

    override suspend fun findUserIdByGoogleUid(googleUid: String): AppResult<String?> = runCatching {
        val snapshot = firestore.collection("users")
            .whereEqualTo("google_uid", googleUid)
            .limit(1)
            .get()
            .await()
        AppResult.Success(snapshot.documents.firstOrNull()?.id)
    }.getOrElse { e ->
        AppResult.Error(Exception(e.message ?: "google_uid lookup failed", e))
    }

    override suspend fun fetchGalleryTemplate(slug: String): AppResult<GalleryTemplateSyncData?> = runCatching {
        val snapshot = firestore.collection("gallery_templates").document(slug).get().await()
        val data = snapshot.data ?: return AppResult.Success(null)
        @Suppress("UNCHECKED_CAST")
        val items = (data["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map {
            GalleryTemplateItemData(text = it["text"] as? String ?: "", note = it["note"] as? String)
        } ?: emptyList()
        AppResult.Success(
            GalleryTemplateSyncData(
                slug = data["slug"] as? String ?: slug,
                category = data["category"] as? String ?: "",
                title = data["title"] as? String ?: "",
                ordered = (data["ordered"] as? Boolean) ?: false,
                items = items,
            )
        )
    }.getOrElse { e -> AppResult.Error(Exception(e.message ?: "fetchGalleryTemplate failed", e)) }
}

// ── Serialization ────────────────────────────────────────────────────────────
//
// Hand-written maps, because the Firestore Android SDK speaks Map<String, Any?> rather than
// kotlinx-serialization (the wasmJs data source encodes ChecklistSyncData.serializer() and so
// picks up new fields for free — this one CANNOT).
//
// ⚠️ CONTRACT: every field of ChecklistSyncData / FillSyncData must appear in BOTH directions —
// toMap() (write) AND toChecklistSyncData()/toFillSyncData() (read). Adding a field to the sync
// model without touching this file compiles cleanly and silently drops the field on Android: it
// never leaves the device, and the next pull overwrites the local row with the default. That is
// exactly how `reminderFullScreen` regressed in 1.17.16. These are internal (not private members)
// so AndroidFirestoreSyncDataSourceMappingTest can round-trip them off-device.

internal fun FillSyncData.toMap(): Map<String, Any?> = mapOf(
    "cloudId" to cloudId,
    "name" to name,
    "itemsJson" to itemsJson,
    "coverImagePath" to coverImagePath,
    "createdAt" to createdAt,
    "isDefault" to isDefault,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
)

internal fun ChecklistSyncData.toMap(): Map<String, Any?> = mapOf(
    "cloudId" to cloudId,
    "name" to name,
    "itemsJson" to itemsJson,
    "reminderAt" to reminderAt,
    "repeatRule" to repeatRule,
    "repeatTimeOfDayMinutes" to repeatTimeOfDayMinutes,
    "repeatNextAt" to repeatNextAt,
    "repeatOccurrenceCount" to repeatOccurrenceCount,
    "reminderFullScreen" to reminderFullScreen,
    "separateCompleted" to separateCompleted,
    "position" to position,
    "autoDeleteCompleted" to autoDeleteCompleted,
    "viewMode" to viewMode,
    "foldersEnabled" to foldersEnabled,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
    "isInbox" to isInbox,
    "fills" to fills.map { it.toMap() },
)

internal fun Map<String, Any?>.toFillSyncData(): FillSyncData = FillSyncData(
    cloudId = this["cloudId"] as? String ?: "",
    name = this["name"] as? String ?: "",
    itemsJson = this["itemsJson"] as? String ?: "[]",
    coverImagePath = this["coverImagePath"] as? String,
    createdAt = (this["createdAt"] as? Long) ?: 0L,
    isDefault = (this["isDefault"] as? Boolean) ?: false,
    updatedAt = this["updatedAt"].asEpochMillis(),
    isDeleted = (this["isDeleted"] as? Boolean) ?: false,
)

internal fun Map<String, Any?>.toChecklistSyncData(documentId: String): ChecklistSyncData {
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
        reminderFullScreen = (this["reminderFullScreen"] as? Boolean) ?: false,
        separateCompleted = (this["separateCompleted"] as? Boolean) ?: false,
        position = ((this["position"] as? Long) ?: 0L).toInt(),
        autoDeleteCompleted = (this["autoDeleteCompleted"] as? Boolean) ?: false,
        viewMode = this["viewMode"] as? String ?: "Standard",
        foldersEnabled = (this["foldersEnabled"] as? Boolean) ?: false,
        updatedAt = this["updatedAt"].asEpochMillis(),
        isDeleted = (this["isDeleted"] as? Boolean) ?: false,
        isInbox = (this["isInbox"] as? Boolean) ?: false,
        fills = fillsList,
    )
}

/**
 * Reads a millis-since-epoch value that may arrive either as a raw [Long]
 * (written by Android/iOS, which serialize `updatedAt` as client-side millis)
 * OR as a Firestore [Timestamp] (written by the web client, whose init.js
 * forces `updatedAt = serverTimestamp()`).
 *
 * Without this normalization, `this["updatedAt"] as? Long` on a Timestamp field
 * yields null → 0L, so SyncRepositoryImpl.mergeRemoteChecklist treats every
 * web edit as older than the local copy and SKIPs it — web→Android sync then
 * silently fails. Tolerant to both types so legacy Timestamp documents already
 * in the cloud are read correctly too.
 */
private fun Any?.asEpochMillis(): Long = when (this) {
    is Long -> this
    is Double -> this.toLong()
    is Timestamp -> this.toDate().time
    is Number -> this.toLong()
    else -> 0L
}
