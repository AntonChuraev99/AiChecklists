package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistFillDao {
    @Query("SELECT * FROM checklist_fills WHERE checklistId = :checklistId ORDER BY createdAt DESC")
    fun observeFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFillEntity>>

    @Query("SELECT * FROM checklist_fills WHERE checklistId = :checklistId AND isDefault = 1 LIMIT 1")
    fun observeDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFillEntity?>

    @Query("SELECT * FROM checklist_fills WHERE checklistId = :checklistId AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultFillByChecklistId(checklistId: Long): ChecklistFillEntity?

    @Query("SELECT * FROM checklist_fills WHERE checklistId = :checklistId AND isDefault = 0 ORDER BY createdAt DESC")
    fun observeAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFillEntity>>

    @Query("SELECT * FROM checklist_fills WHERE id = :id")
    suspend fun getById(id: Long): ChecklistFillEntity?

    @Query("SELECT COUNT(*) FROM checklist_fills WHERE checklistId = :checklistId")
    suspend fun getCountByChecklistId(checklistId: Long): Int

    @Query("SELECT COUNT(*) FROM checklist_fills WHERE isDefault = 0")
    suspend fun getTotalAdditionalFillCount(): Int

    /** Returns all default fills across all checklists (used for per-item reminder scanning). */
    @Query("SELECT * FROM checklist_fills WHERE isDefault = 1")
    suspend fun getAllDefaultFills(): List<ChecklistFillEntity>

    /** Returns all fills (default + additional) for a checklist (used for attachment cleanup before checklist delete). */
    @Query("SELECT * FROM checklist_fills WHERE checklistId = :checklistId")
    suspend fun getAllFillsByChecklistId(checklistId: Long): List<ChecklistFillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fill: ChecklistFillEntity): Long

    @Query("DELETE FROM checklist_fills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM checklist_fills WHERE checklistId = :checklistId")
    suspend fun deleteByChecklistId(checklistId: Long)

    // ─── Sync ───

    @Query("SELECT * FROM checklist_fills WHERE syncStatus != 0")
    suspend fun getPendingSync(): List<ChecklistFillEntity>

    @Query("SELECT * FROM checklist_fills WHERE cloudId = :cloudId")
    suspend fun getByCloudId(cloudId: String): ChecklistFillEntity?

    /**
     * Cloud IDs of the SYNCED fills of one checklist. Mirror of
     * [ChecklistDao.getSyncedCloudIds] scoped to a single checklist, used by the
     * per-fill pull reconciliation: a fill that is SYNCED locally but absent from
     * the newer remote.fills was deleted on another device => stale => remove it.
     * Deliberately excludes PENDING_UPLOAD(1) and PENDING_DELETE(2) so a fill just
     * created locally (not yet in the cloud) is never reconciled away.
     */
    @Query("SELECT cloudId FROM checklist_fills WHERE checklistId = :checklistId AND syncStatus = 0 AND cloudId IS NOT NULL")
    suspend fun getSyncedFillCloudIds(checklistId: Long): List<String>

    @Query("UPDATE checklist_fills SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("UPDATE checklist_fills SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: Long, status: Int = 0, updatedAt: Long)

    @Query("UPDATE checklist_fills SET userId = :userId WHERE userId IS NULL")
    suspend fun assignUserIdToAll(userId: String)

    @Query("SELECT * FROM checklist_fills WHERE checklistId = :checklistId AND isDeleted = 0")
    suspend fun getActiveFillsByChecklistId(checklistId: Long): List<ChecklistFillEntity>
}
