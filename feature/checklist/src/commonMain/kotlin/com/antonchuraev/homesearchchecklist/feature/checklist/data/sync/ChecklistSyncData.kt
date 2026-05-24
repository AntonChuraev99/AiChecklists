package com.antonchuraev.homesearchchecklist.feature.checklist.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class ChecklistSyncData(
    val cloudId: String,
    val name: String,
    val itemsJson: String,
    val reminderAt: Long? = null,
    val repeatRule: String? = null,
    val repeatTimeOfDayMinutes: Int? = null,
    val repeatNextAt: Long? = null,
    val repeatOccurrenceCount: Int = 0,
    val separateCompleted: Boolean = false,
    val position: Int = 0,
    val autoDeleteCompleted: Boolean = false,
    val viewMode: String = "Standard",
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val fills: List<FillSyncData> = emptyList(),
)

@Serializable
data class FillSyncData(
    val cloudId: String,
    val name: String,
    val itemsJson: String,
    val coverImagePath: String? = null,
    val createdAt: Long = 0L,
    val isDefault: Boolean = false,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
)
