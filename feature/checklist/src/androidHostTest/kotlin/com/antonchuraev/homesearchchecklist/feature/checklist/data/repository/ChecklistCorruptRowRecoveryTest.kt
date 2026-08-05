package com.antonchuraev.homesearchchecklist.feature.checklist.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentCloudStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabase
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabaseConstructor
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.RoomChecklistTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduces the prod web-origin failure class: a single corrupt row in the accumulated OPFS
 * `checklists` table makes [ChecklistRepositoryImpl.checklists] throw while mapping the cursor,
 * and because all four boot flows read `repository.checklists`, the Home screen never leaves the
 * spinner.
 *
 * ⚠️ Platform note (empirically established, 2026-07-20): the JVM `BundledSQLiteDriver` used by this
 * host test coerces a NULL TEXT cell to `""` (`getText` never returns null), so a NULL `name`/`items`
 * column does NOT throw here — it only crashes on the wasmJs `sqlite-web` driver, which cannot run
 * in a host unit test. The NULL-column recovery path is therefore covered as a direct unit test of
 * the mapper (see `ChecklistRowRecoveryTest` in commonTest), not through the driver here.
 *
 * What IS host-reproducible and shares the exact same root and fix: a row whose `items` column holds
 * corrupt (non-empty, invalid) JSON. The current converter rethrows it as `ChecklistJsonDecodeException`
 * and the whole `checklists` flow dies — the same "one bad row kills every collector" symptom.
 *
 * The rows cannot be inserted through the version-18 schema (`name`/`items` are `TEXT NOT NULL`), so
 * we faithfully emulate the drifted on-disk schema: an older nullable column definition that survived
 * `fallbackToDestructiveMigration(dropAllTables = false)`. We pre-seed a file DB with the nullable
 * `checklists` table plus Room's identity bookkeeping (`room_master_table` + the schema identity hash
 * + `user_version = 18`) so Room opens it verbatim instead of recreating it.
 *
 * RED before the fix: `checklists` uses `observeChecklists()` and the corrupt-JSON row throws.
 * GREEN after the fix: it reads a nullable `ChecklistRow` and recovers each bad row with safe
 * defaults, keeping it visible so the user can still open/delete it.
 */
class ChecklistCorruptRowRecoveryTest {

    private companion object {
        /**
         * Identity hash of the CURRENT schema version (schemas/.../19.json → database.identityHash).
         *
         * Must match so Room trusts the existing (drifted) table instead of migrating or recreating
         * it — which is the whole point of this test: it exercises the READ path over a table whose
         * column definitions drifted from the entity. Seeding an OLDER version instead would make
         * Room run the migration chain and then VALIDATE the result, and validation legitimately
         * rejects the deliberate drift (`name`/`items` nullable here, NOT NULL in the entity) — the
         * test would fail on Room's schema check before ever reaching the recovery code.
         *
         * So this constant and the `PRAGMA user_version` below must be bumped together with every
         * schema version. Last bump: 18 → 19 (`isInbox`).
         */
        const val CURRENT_SCHEMA_IDENTITY_HASH = "185ed1d859f9a0adf791f47ab449925a"
        const val CURRENT_SCHEMA_VERSION = 19
    }

    private val dbFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        dbFiles.forEach { base ->
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                File(base.absolutePath + suffix).delete()
            }
        }
    }

    private fun newTempDbPath(): String {
        val f = File.createTempFile("checklist_null_row_", ".db").apply { delete() }
        dbFiles += f
        return f.absolutePath
    }

    /**
     * Writes a DB whose `checklists` table has the OLD nullable `name`/`items` definition and holds
     * three rows: one valid, one with a NULL `name`, one with a NULL `items`. Room bookkeeping is
     * seeded so the production open path (identity hash + version 18) accepts the file verbatim.
     */
    private fun seedDriftedDb(path: String) {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(path)
        try {
            // Drifted schema: name/items are NULLABLE here (unlike version-18 NOT NULL).
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `checklists` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT, `items` TEXT, " +
                    "`reminderAt` INTEGER, `repeatRule` TEXT, `repeatTimeOfDayMinutes` INTEGER, " +
                    "`repeatNextAt` INTEGER, `repeatOccurrenceCount` INTEGER NOT NULL, " +
                    "`reminderFullScreen` INTEGER NOT NULL, `separateCompleted` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, `autoDeleteCompleted` INTEGER NOT NULL, " +
                    "`viewMode` TEXT NOT NULL, `foldersEnabled` INTEGER NOT NULL, " +
                    "`cloudId` TEXT, `userId` TEXT, `updatedAt` INTEGER NOT NULL, " +
                    "`syncStatus` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    // Present so the table matches the current entity everywhere EXCEPT the one
                    // deliberate drift (nullable name/items) this test is about.
                    "`isInbox` INTEGER NOT NULL)"
            )

            // Room identity bookkeeping — makes the production open path trust this file as version 18.
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$CURRENT_SCHEMA_IDENTITY_HASH')"
            )
            connection.execSQL("PRAGMA user_version = $CURRENT_SCHEMA_VERSION")

            val cols =
                "(name, items, repeatOccurrenceCount, reminderFullScreen, separateCompleted, " +
                    "position, autoDeleteCompleted, viewMode, foldersEnabled, updatedAt, syncStatus, " +
                    "isDeleted, isInbox)"
            // Valid row.
            connection.execSQL(
                "INSERT INTO checklists $cols VALUES ('Groceries', '[]', 0, 0, 0, 0, 0, 'Standard', 0, 0, 0, 0, 0)"
            )
            // Corrupt row A: NULL name (coerced to "" by the JVM driver — see class KDoc).
            connection.execSQL(
                "INSERT INTO checklists $cols VALUES (NULL, '[]', 0, 0, 0, 1, 0, 'Standard', 0, 0, 0, 0, 0)"
            )
            // Corrupt row B: NULL items (coerced to "" by the JVM driver — see class KDoc).
            connection.execSQL(
                "INSERT INTO checklists $cols VALUES ('HasNullItems', NULL, 0, 0, 0, 2, 0, 'Standard', 0, 0, 0, 0, 0)"
            )
            // Corrupt row C: non-empty INVALID JSON in items — the host-reproducible RED trigger.
            // The current converter rethrows this as ChecklistJsonDecodeException and the flow dies.
            connection.execSQL(
                "INSERT INTO checklists $cols VALUES ('HasCorruptJson', 'not-a-json', 0, 0, 0, 3, 0, 'Standard', 0, 0, 0, 0, 0)"
            )
        } finally {
            connection.close()
        }
    }

    private fun openProdDatabase(path: String): ChecklistDatabase =
        ChecklistDatabase.getRoomDatabase(
            Room.databaseBuilder<ChecklistDatabase>(
                name = path,
                factory = { ChecklistDatabaseConstructor.initialize() },
            ).setDriver(BundledSQLiteDriver()),
        )

    private fun newRepository(db: ChecklistDatabase, logger: AppLogger) =
        ChecklistRepositoryImpl(
            checklistDao = db.checklistDao(),
            fillDao = db.checklistFillDao(),
            attachmentStorage = NoopAttachmentStorage,
            attachmentCloudStorage = NoopAttachmentCloudStorage,
            transactionRunner = RoomChecklistTransactionRunner(db),
            logger = logger,
        )

    @Test
    fun checklists_withCorruptRows_recoversWithDefaultsInsteadOfCrashing() = runTest {
        val path = newTempDbPath()
        seedDriftedDb(path)
        val logger = RecordingLogger()
        val db = openProdDatabase(path)
        try {
            // RED on current code: this collect throws ChecklistJsonDecodeException on the
            // corrupt-JSON row, killing the whole flow (Home spinner forever in prod).
            val checklists = newRepository(db, logger).checklists.first()

            assertEquals(4, checklists.size, "all four rows must remain visible (none dropped)")

            // Valid row intact.
            val valid = checklists.single { it.name == "Groceries" }
            assertEquals(emptyList(), valid.items, "valid row parses normally")

            // NULL name recovered to "" (still openable/deletable by the user).
            assertTrue(
                checklists.any { it.name == "" },
                "the NULL-name row is recovered with an empty name",
            )

            // NULL items recovered to emptyList.
            val nullItems = checklists.single { it.name == "HasNullItems" }
            assertEquals(emptyList(), nullItems.items, "the NULL-items row is recovered with empty items")

            // Corrupt-JSON items recovered to emptyList instead of throwing.
            val corruptJson = checklists.single { it.name == "HasCorruptJson" }
            assertEquals(emptyList(), corruptJson.items, "the corrupt-JSON row is recovered with empty items")

            // The corrupt-JSON recovery is logged (observability for the prod incident).
            // NULL columns are masked by the JVM driver (coerced to ""), so they do not log here;
            // that path is asserted directly in ChecklistRowRecoveryTest (commonTest).
            assertTrue(
                logger.errors.any { it.contains("checklist_row_recovered") },
                "the corrupt-JSON row must be logged as recovered; got: ${logger.errors}",
            )
        } finally {
            db.close()
        }
    }

    // ── Minimal collaborators — the `checklists` flow only touches the checklist DAO ──

    private object NoopAttachmentStorage : AttachmentStoragePort {
        override suspend fun storeAttachment(
            sourcePath: String,
            fillId: Long,
            itemId: String,
            attachmentId: String,
            originalFileName: String,
        ): String? = null

        override suspend fun deleteAttachment(path: String) {}
        override suspend fun deleteAttachmentsFor(fillId: Long, itemId: String) {}
        override suspend fun deleteAttachmentsForFill(fillId: Long) {}
        override suspend fun probeImage(path: String, mimeType: String?): Pair<Int?, Int?> = null to null
        override suspend fun sizeOf(path: String): Long = 0L
    }

    private object NoopAttachmentCloudStorage : AttachmentCloudStoragePort {
        override suspend fun upload(localPath: String, storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun download(storagePath: String, localPath: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(storagePath: String): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class RecordingLogger : AppLogger {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) { warnings += message }
        override fun error(tag: String, message: String, throwable: Throwable?) { errors += message }
    }
}
