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

    @Query("SELECT * FROM checklist_fills WHERE id = :id")
    suspend fun getById(id: Long): ChecklistFillEntity?

    @Query("SELECT COUNT(*) FROM checklist_fills WHERE checklistId = :checklistId")
    suspend fun getCountByChecklistId(checklistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fill: ChecklistFillEntity): Long

    @Query("DELETE FROM checklist_fills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM checklist_fills WHERE checklistId = :checklistId")
    suspend fun deleteByChecklistId(checklistId: Long)
}
