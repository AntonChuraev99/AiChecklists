package com.antonchuraev.homesearchchecklist.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.domain.model.ChecklistItem

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val items: List<ChecklistItem>
)

internal fun ChecklistEntity.toDomain(): Checklist = Checklist(
    id = id,
    name = name,
    items = items
)

internal fun Checklist.toEntity(): ChecklistEntity = ChecklistEntity(
    id = id,
    name = name,
    items = items
)


