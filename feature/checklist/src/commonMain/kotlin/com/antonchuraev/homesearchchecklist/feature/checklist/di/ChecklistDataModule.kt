package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.getDatabaseBuilder
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabase
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.RoomChecklistTransactionRunner
import com.antonchuraev.homesearchchecklist.feature.checklist.data.repository.ChecklistRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import org.koin.mp.KoinPlatform

/**
 * Room 3.0-backed ChecklistRepository — works on Android, iOS, and wasmJs (OPFS).
 *
 * The lazy is only ever first touched from a Koin definition (see ChecklistFeatureModule), so Koin
 * is started and AppLogger resolvable when the driver-fallback callback runs. The report becomes a
 * Crashlytics non-fatal: it is how we count devices running on OS SQLite (Crashlytics 9775ecb7).
 */
private val database: ChecklistDatabase by lazy {
    ChecklistDatabase.getRoomDatabase(
        getDatabaseBuilder<ChecklistDatabase>("ChecklistDatabase") { cause ->
            KoinPlatform.getKoinOrNull()?.getOrNull<AppLogger>()?.error(
                DATABASE_TAG,
                "Bundled SQLite (libsqliteJni.so) is not loadable, falling back to OS SQLite " +
                    "(AndroidSQLiteDriver): ${cause.message}",
                cause,
            )
        }
    )
}

private const val DATABASE_TAG = "Database"

internal fun createChecklistRepository(
    attachmentStorage: AttachmentStoragePort,
    attachmentCloudStorage: AttachmentCloudStoragePort,
    logger: AppLogger,
): ChecklistRepository =
    ChecklistRepositoryImpl(
        checklistDao = database.checklistDao(),
        fillDao = database.checklistFillDao(),
        attachmentStorage = attachmentStorage,
        attachmentCloudStorage = attachmentCloudStorage,
        transactionRunner = RoomChecklistTransactionRunner(database),
        logger = logger,
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
