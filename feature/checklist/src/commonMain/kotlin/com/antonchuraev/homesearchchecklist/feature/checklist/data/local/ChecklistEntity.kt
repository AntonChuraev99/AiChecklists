package com.antonchuraev.homesearchchecklist.feature.checklist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>
)

fun ChecklistEntity.toDomain() = Checklist(id, name, items)
fun Checklist.toEntity() = ChecklistEntity(id, name, items)

