package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

enum class SyncStatus(val value: Int) {
    SYNCED(0),
    PENDING_UPLOAD(1),
    PENDING_DELETE(2);

    companion object {
        fun fromValue(value: Int) = entries.first { it.value == value }
    }
}
