package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem

@Entity(
    tableName = "checklist_fills",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("checklistId")]
)
data class ChecklistFillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val checklistId: Long,
    val name: String,
    val coverImagePath: String?,
    val items: List<ChecklistFillItem>,
    val createdAt: Long
)

fun ChecklistFillEntity.toDomain() = ChecklistFill(
    id = id,
    checklistId = checklistId,
    name = name,
    coverImagePath = coverImagePath,
    items = items,
    createdAt = createdAt
)

fun ChecklistFill.toEntity() = ChecklistFillEntity(
    id = id,
    checklistId = checklistId,
    name = name,
    coverImagePath = coverImagePath,
    items = items,
    createdAt = createdAt
)
