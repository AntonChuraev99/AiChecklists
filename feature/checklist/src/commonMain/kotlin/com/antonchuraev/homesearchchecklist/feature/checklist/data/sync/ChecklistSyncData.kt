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
    val reminderFullScreen: Boolean = false,
    val separateCompleted: Boolean = false,
    val position: Int = 0,
    val autoDeleteCompleted: Boolean = false,
    val viewMode: String = "Standard",
    val foldersEnabled: Boolean = false,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    /**
     * System-Inbox marker (v2 nav arm). Defaulted so cloud documents written before this field
     * existed — and by any client that still does not know it (older builds, the MCP worker) — keep
     * decoding. The local merge treats the flag as write-once for exactly that reason; see
     * `SyncRepositoryImpl.mergeRemoteChecklist`.
     */
    val isInbox: Boolean = false,
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
