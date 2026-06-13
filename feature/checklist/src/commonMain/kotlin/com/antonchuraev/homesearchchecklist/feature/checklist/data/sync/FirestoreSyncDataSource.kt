package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface FirestoreSyncDataSource {

    fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>>

    fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>>

    suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit>

    suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit>

    suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit>

    suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>>

    /**
     * Real-time listener on the user's account doc `users/{userId}` (the credit doc,
     * keyed by the device-registration id — NOT the Firebase Auth uid used for
     * checklist sync). Emits the server-authoritative ai_credits / is_premium on every
     * change so the locally cached balance stays live and shared across devices.
     * Emits null when the doc doesn't exist yet.
     */
    fun observeUserDoc(userId: String): Flow<AppResult<UserDocSyncData?>>

    /**
     * One-shot lookup of the CANONICAL credit-doc id for a Google-linked user: the id of
     * the `users` doc whose `google_uid` field equals [googleUid] (the Firebase Auth uid).
     * Returns null when no such doc exists.
     *
     * Used by self-healing convergence (`UserCreditsSync`) to re-point a device that linked
     * Google before the USER_ID_KEY switch shipped — it still references its own device-id
     * credit doc (no `google_uid`), so credits never became shared. Resolving the canonical
     * doc lets the device converge onto the shared balance.
     */
    suspend fun findUserIdByGoogleUid(googleUid: String): AppResult<String?>
}

@Serializable
data class UserDocSyncData(
    @SerialName("ai_credits") val aiCredits: Int = 0,
    @SerialName("is_premium") val isPremium: Boolean = false,
)
