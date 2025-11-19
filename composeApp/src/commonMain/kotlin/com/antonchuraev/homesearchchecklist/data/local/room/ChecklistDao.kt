package com.antonchuraev.homesearchchecklist.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklists ORDER BY id DESC")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checklist: ChecklistEntity): Long

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteById(id: Long)
}


