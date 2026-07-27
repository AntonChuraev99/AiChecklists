package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    val reminderAt: Long? = null,
    val repeatRule: ReminderRepeatRule? = null,
    val repeatTimeOfDayMinutes: Int? = null,
    val repeatNextAt: Long? = null,
    val repeatOccurrenceCount: Int = 0,
    val reminderFullScreen: Boolean = false,
    val separateCompleted: Boolean = false,
    val position: Int = 0,
    val autoDeleteCompleted: Boolean = false,
    val viewMode: ChecklistViewMode = ChecklistViewMode.Standard,
    val foldersEnabled: Boolean = false,
    val cloudId: String? = null,
    val userId: String? = null,
    val updatedAt: Long = 0L,
    val syncStatus: Int = 0,
    val isDeleted: Boolean = false,
    /**
     * System-Inbox marker for the v2 nav arm — see [Checklist.isInbox].
     *
     * Deliberately declared WITHOUT `@ColumnInfo` (like every other column in this table), so the
     * SQL column name is the property name verbatim: `isInbox`. `MIGRATION_18_19` must therefore
     * add exactly `isInbox INTEGER NOT NULL DEFAULT 0` — anything else drifts from the KSP-generated
     * schema, and because the database is built with `fallbackToDestructiveMigration(dropAllTables
     * = false)` a drifting migration does not crash, it silently wipes every local checklist.
     */
    val isInbox: Boolean = false,
)

fun ChecklistEntity.toDomain() = Checklist(
    id = id,
    name = name,
    items = items,
    reminderAt = reminderAt,
    repeatRule = repeatRule,
    repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
    repeatNextAt = repeatNextAt,
    repeatOccurrenceCount = repeatOccurrenceCount,
    reminderFullScreen = reminderFullScreen,
    separateCompleted = separateCompleted,
    position = position,
    autoDeleteCompleted = autoDeleteCompleted,
    viewMode = viewMode,
    foldersEnabled = foldersEnabled,
    cloudId = cloudId,
    userId = userId,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    isDeleted = isDeleted,
    isInbox = isInbox,
)

fun Checklist.toEntity() = ChecklistEntity(
    id = id,
    name = name,
    items = items,
    reminderAt = reminderAt,
    repeatRule = repeatRule,
    repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
    repeatNextAt = repeatNextAt,
    repeatOccurrenceCount = repeatOccurrenceCount,
    reminderFullScreen = reminderFullScreen,
    separateCompleted = separateCompleted,
    position = position,
    autoDeleteCompleted = autoDeleteCompleted,
    viewMode = viewMode,
    foldersEnabled = foldersEnabled,
    cloudId = cloudId,
    userId = userId,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    isDeleted = isDeleted,
    isInbox = isInbox,
)
