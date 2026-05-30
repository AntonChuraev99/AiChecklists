package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists WHERE isDeleted = 0 ORDER BY position ASC")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: Long): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observeChecklistById(id: Long): Flow<ChecklistEntity?>

    // ─── Sync ───

    @Query("SELECT * FROM checklists WHERE syncStatus != 0")
    suspend fun getPendingSync(): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE cloudId = :cloudId")
    suspend fun getByCloudId(cloudId: String): ChecklistEntity?

    /**
     * Cloud IDs of all locally-synced checklists (syncStatus == SYNCED).
     * Used by pull reconciliation to detect rows that were hard-deleted in
     * Firestore on another device: SYNCED locally but absent from the cloud
     * fetch => stale => remove locally. Deliberately excludes PENDING_UPLOAD(1)
     * and PENDING_DELETE(2) so local-only changes are never reconciled away.
     */
    @Query("SELECT cloudId FROM checklists WHERE syncStatus = 0 AND cloudId IS NOT NULL")
    suspend fun getSyncedCloudIds(): List<String>

    @Query("UPDATE checklists SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: Int)

    @Query("UPDATE checklists SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: Long, status: Int = 0, updatedAt: Long)

    @Query("UPDATE checklists SET userId = :userId WHERE userId IS NULL")
    suspend fun assignUserIdToAll(userId: String)

    @Query("SELECT * FROM checklists WHERE isDeleted = 0 ORDER BY position ASC")
    suspend fun getAllActive(): List<ChecklistEntity>

    @Query("UPDATE checklists SET isDeleted = 1, syncStatus = 2, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

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

    @Query("SELECT * FROM checklists WHERE isDeleted = 0 ORDER BY position ASC")
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
