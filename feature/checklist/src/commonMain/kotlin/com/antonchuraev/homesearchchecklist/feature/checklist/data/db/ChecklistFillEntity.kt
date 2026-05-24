package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
    val createdAt: Long,
    val isDefault: Boolean = false,
    val cloudId: String? = null,
    val userId: String? = null,
    val updatedAt: Long = 0L,
    val syncStatus: Int = 0,
    val isDeleted: Boolean = false,
)

fun ChecklistFillEntity.toDomain() = ChecklistFill(
    id = id,
    checklistId = checklistId,
    name = name,
    coverImagePath = coverImagePath,
    items = items,
    createdAt = createdAt,
    isDefault = isDefault,
    cloudId = cloudId,
    userId = userId,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    isDeleted = isDeleted,
)

fun ChecklistFill.toEntity() = ChecklistFillEntity(
    id = id,
    checklistId = checklistId,
    name = name,
    coverImagePath = coverImagePath,
    items = items,
    createdAt = createdAt,
    isDefault = isDefault,
    cloudId = cloudId,
    userId = userId,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
    isDeleted = isDeleted,
)
