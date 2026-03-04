package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import kotlinx.coroutines.flow.Flow

data class ChecklistReminderInfo(val id: Long, val name: String, val reminderAt: Long)

data class ChecklistRecurringInfo(
    val id: Long,
    val name: String,
    val reminderAt: Long,
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

    @Query("UPDATE checklists SET reminderAt = :reminderAt WHERE id = :id")
    suspend fun updateReminder(id: Long, reminderAt: Long?)

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

    @Query("SELECT COUNT(*) FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
    suspend fun countActiveReminders(nowMillis: Long): Int

    @Query("SELECT id, name, reminderAt FROM checklists WHERE reminderAt IS NOT NULL AND reminderAt > :nowMillis")
    suspend fun getActiveReminders(nowMillis: Long): List<ChecklistReminderInfo>

    // ─── Recurring reminder atomic operations ───

    @Query("""
        UPDATE checklists
        SET reminderAt = :nextReminderAt, repeatOccurrenceCount = :newCount
        WHERE id = :id
    """)
    suspend fun advanceRecurringReminder(id: Long, nextReminderAt: Long?, newCount: Int)

    @Query("""
        UPDATE checklists
        SET reminderAt = NULL, repeatRule = NULL, repeatOccurrenceCount = 0
        WHERE id = :id
    """)
    suspend fun clearRecurringReminder(id: Long)

    @Query("""
        UPDATE checklists
        SET reminderAt = :reminderAt, repeatRule = :repeatRuleJson, repeatOccurrenceCount = 0
        WHERE id = :id
    """)
    suspend fun setReminderWithRule(id: Long, reminderAt: Long?, repeatRuleJson: String?)

    @Query("UPDATE checklists SET repeatRule = :ruleJson WHERE id = :id")
    suspend fun setRepeatRule(id: Long, ruleJson: String?)

    @Query("SELECT COUNT(*) FROM checklists WHERE repeatRule IS NOT NULL AND reminderAt IS NOT NULL")
    suspend fun countRecurringReminders(): Int

    @Query("""
        SELECT id, name, reminderAt, repeatRule, repeatOccurrenceCount
        FROM checklists
        WHERE reminderAt IS NOT NULL
          AND repeatRule IS NOT NULL
          AND reminderAt <= :nowMillis
    """)
    suspend fun getPastDueRecurringReminders(nowMillis: Long): List<ChecklistRecurringInfo>
}

