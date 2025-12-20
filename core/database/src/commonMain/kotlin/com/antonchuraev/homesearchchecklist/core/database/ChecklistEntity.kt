package com.antonchuraev.homesearchchecklist.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antonchuraev.homesearchchecklist.core.common.api.Checklist
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistItem

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>
)

fun ChecklistEntity.toDomain() = Checklist(id, name, items)
fun Checklist.toEntity() = ChecklistEntity(id, name, items)

