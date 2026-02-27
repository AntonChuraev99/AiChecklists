package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>,
    val reminderAt: Long? = null,
    val separateCompleted: Boolean = false,
    val position: Int = 0
)

fun ChecklistEntity.toDomain() = Checklist(id, name, items, reminderAt, separateCompleted, position)
fun Checklist.toEntity() = ChecklistEntity(id, name, items, reminderAt, separateCompleted, position)

