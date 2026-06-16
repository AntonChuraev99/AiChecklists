package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.getDatabaseBuilder
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabase
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.RoomChecklistTransactionRunner
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
        transactionRunner = RoomChecklistTransactionRunner(database),
    )

/**
 * Returns the singleton [ChecklistDatabase] instance.
 * Exposed so other feature DI modules (e.g. aiChatFeatureModule) can obtain
 * DAOs from the same database without creating a second connection.
 * [attachmentStorage] is accepted to ensure the database is only built after
 * the storage dependency is ready (mirrors [createChecklistRepository] contract).
 */
internal fun getChecklistDatabase(@Suppress("UNUSED_PARAMETER") attachmentStorage: AttachmentStoragePort): ChecklistDatabase =
    database
