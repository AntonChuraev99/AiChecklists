package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    val reminderAt: Long? = null,
    val repeatRule: ReminderRepeatRule? = null,
    val repeatOccurrenceCount: Int = 0,
    val separateCompleted: Boolean = false,
    val position: Int = 0,
    val autoDeleteCompleted: Boolean = false
)

fun ChecklistEntity.toDomain() = Checklist(
    id = id,
    name = name,
    items = items,
    reminderAt = reminderAt,
    repeatRule = repeatRule,
    repeatOccurrenceCount = repeatOccurrenceCount,
    separateCompleted = separateCompleted,
    position = position,
    autoDeleteCompleted = autoDeleteCompleted
)

fun Checklist.toEntity() = ChecklistEntity(
    id = id,
    name = name,
    items = items,
    reminderAt = reminderAt,
    repeatRule = repeatRule,
    repeatOccurrenceCount = repeatOccurrenceCount,
    separateCompleted = separateCompleted,
    position = position,
    autoDeleteCompleted = autoDeleteCompleted
)

