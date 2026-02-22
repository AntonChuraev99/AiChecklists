package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ChecklistReminderInfo(val id: Long, val name: String, val reminderAt: Long)

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists ORDER BY id DESC")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getById(id: Long): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observeChecklistById(id: Long): Flow<ChecklistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checklist: ChecklistEntity): Long

    @Update
    suspend fun update(checklist: ChecklistEntity)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE checklists SET reminderAt = :reminderAt WHERE id = :id")
    suspend fun updateReminder(id: Long, reminderAt: Long?)

    @Query("SELECT COUNT(*) FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
    suspend fun countActiveReminders(nowMillis: Long): Int

    @Query("SELECT id, name, reminderAt FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
    suspend fun getActiveReminders(nowMillis: Long): List<ChecklistReminderInfo>
}

