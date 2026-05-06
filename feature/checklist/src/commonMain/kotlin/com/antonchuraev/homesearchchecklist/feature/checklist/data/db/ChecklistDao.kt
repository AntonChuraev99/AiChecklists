package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import kotlinx.coroutines.flow.Flow

data class ChecklistReminderInfo(val id: Long, val name: String, val reminderAt: Long)

data class ChecklistRepeatInfo(
    val id: Long,
    val name: String,
    val repeatNextAt: Long,
    val repeatRule: ReminderRepeatRule?,
    val repeatOccurrenceCount: Int
)

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists ORDER BY position ASC")
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

    @Query("UPDATE checklists SET separateCompleted = :value WHERE id = :id")
    suspend fun setSeparateCompleted(id: Long, value: Boolean)

    @Query("UPDATE checklists SET autoDeleteCompleted = :value WHERE id = :id")
    suspend fun setAutoDeleteCompleted(id: Long, value: Boolean)

    @Query("UPDATE checklists SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE checklists SET position = position + 1")
    suspend fun incrementAllPositions()

    @Query("SELECT * FROM checklists ORDER BY position ASC")
    suspend fun getAllOrderedByPosition(): List<ChecklistEntity>

    // ─── One-shot reminders ───

    @Query("UPDATE checklists SET reminderAt = :reminderAt WHERE id = :id")
    suspend fun updateReminder(id: Long, reminderAt: Long?)

    @Query("SELECT COUNT(*) FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
    suspend fun countActiveReminders(nowMillis: Long): Int

    @Query("SELECT id, name, reminderAt FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
    suspend fun getActiveReminders(nowMillis: Long): List<ChecklistReminderInfo>

    // ─── Independent repeat schedule ───

    @Query("""
        UPDATE checklists
        SET repeatRule = :repeatRuleJson,
            repeatTimeOfDayMinutes = :timeMinutes,
            repeatNextAt = :nextAt,
            repeatOccurrenceCount = 0
        WHERE id = :id
    """)
    suspend fun setRepeatSchedule(id: Long, repeatRuleJson: String?, timeMinutes: Int?, nextAt: Long?)

    @Query("""
        UPDATE checklists
        SET repeatNextAt = :nextAt, repeatOccurrenceCount = :newCount
        WHERE id = :id
    """)
    suspend fun advanceRepeatSchedule(id: Long, nextAt: Long?, newCount: Int)

    @Query("""
        UPDATE checklists
        SET repeatRule = NULL, repeatTimeOfDayMinutes = NULL, repeatNextAt = NULL, repeatOccurrenceCount = 0
        WHERE id = :id
    """)
    suspend fun clearRepeatSchedule(id: Long)

    @Query("SELECT COUNT(*) FROM checklists WHERE repeatRule IS NOT NULL AND repeatNextAt IS NOT NULL")
    suspend fun countActiveRepeatSchedules(): Int

    @Query("""
        SELECT id, name, repeatNextAt, repeatRule, repeatOccurrenceCount
        FROM checklists
        WHERE repeatNextAt IS NOT NULL
          AND repeatRule IS NOT NULL
          AND repeatNextAt > :nowMillis
    """)
    suspend fun getActiveRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo>

    @Query("""
        SELECT id, name, repeatNextAt, repeatRule, repeatOccurrenceCount
        FROM checklists
        WHERE repeatNextAt IS NOT NULL
          AND repeatRule IS NOT NULL
          AND repeatNextAt <= :nowMillis
    """)
    suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo>

    // ─── Weekly mode ───

    @Query("SELECT COUNT(*) FROM checklists WHERE viewMode = 'Weekly'")
    suspend fun getWeeklyChecklistCount(): Int

    @Query("SELECT COUNT(*) FROM checklists WHERE viewMode = 'Weekly'")
    fun observeWeeklyChecklistCount(): Flow<Int>
}
