package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.getDatabaseBuilder
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabase
import com.antonchuraev.homesearchchecklist.feature.checklist.data.repository.ChecklistRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository

/** Room 3.0-backed ChecklistRepository — works on Android, iOS, and wasmJs (OPFS). */
private val database: ChecklistDatabase by lazy {
    ChecklistDatabase.getRoomDatabase(
        getDatabaseBuilder<ChecklistDatabase>("ChecklistDatabase")
    )
}

internal fun createChecklistRepository(attachmentStorage: AttachmentStoragePort): ChecklistRepository =
    ChecklistRepositoryImpl(
        checklistDao = database.checklistDao(),
        fillDao = database.checklistFillDao(),
        attachmentStorage = attachmentStorage,
    )
