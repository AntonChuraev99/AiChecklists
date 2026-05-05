package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fill: ChecklistFillEntity): Long

    @Query("DELETE FROM checklist_fills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM checklist_fills WHERE checklistId = :checklistId")
    suspend fun deleteByChecklistId(checklistId: Long)
}
