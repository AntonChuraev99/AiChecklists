package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct unit tests for [ChecklistRow.toChecklistSafe] — the crash-tolerant raw→domain recovery.
 *
 * This covers the NULL-column path that the integration test (`ChecklistNullRowRecoveryTest`,
 * androidHostTest) cannot exercise: the JVM bundled SQLite driver coerces a NULL TEXT cell to `""`,
 * masking the real wasmJs behaviour where a NULL cell surfaces as `null`. Constructing a
 * [ChecklistRow] directly bypasses the driver, so the null path is reachable off-device here.
 */
class ChecklistRowRecoveryTest {

    private val itemConverters = ChecklistItemConverters()
    private val reminderConverters = ReminderConverters()

    private class RecordingLogger : AppLogger {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) { warnings += message }
        override fun error(tag: String, message: String, throwable: Throwable?) { errors += message }
    }

    private fun row(
        id: Long = 1L,
        name: String? = "Groceries",
        items: String? = "",
        viewMode: String? = ChecklistViewMode.Standard.name,
        repeatRule: String? = null,
        isInbox: Boolean = false,
    ) = ChecklistRow(
        id = id,
        name = name,
        items = items,
        reminderAt = null,
        repeatRule = repeatRule,
        repeatTimeOfDayMinutes = null,
        repeatNextAt = null,
        repeatOccurrenceCount = 0,
        reminderFullScreen = false,
        separateCompleted = false,
        position = 0,
        autoDeleteCompleted = false,
        viewMode = viewMode,
        foldersEnabled = false,
        cloudId = null,
        userId = null,
        updatedAt = 0L,
        syncStatus = 0,
        isDeleted = false,
        isInbox = isInbox,
    )

    @Test
    fun toChecklistSafe_nullName_recoversToEmptyNameAndLogsWarning() {
        val logger = RecordingLogger()
        val result = row(id = 7L, name = null).toChecklistSafe(itemConverters, reminderConverters, logger)

        assertEquals("", result.name, "a NULL name degrades to an empty string")
        assertEquals(7L, result.id, "the row identity is preserved (still openable/deletable)")
        assertTrue(logger.errors.isEmpty(), "a null column is a warning, not an error")
        assertTrue(
            logger.warnings.any { it.contains("checklist_row_recovered") && it.contains("name_null=true") },
            "the recovery is logged with name_null=true; got: ${logger.warnings}",
        )
    }

    @Test
    fun toChecklistSafe_nullItems_recoversToEmptyListAndLogsWarning() {
        val logger = RecordingLogger()
        val result = row(id = 8L, items = null).toChecklistSafe(itemConverters, reminderConverters, logger)

        assertEquals(emptyList(), result.items, "NULL items degrade to an empty list")
        assertTrue(
            logger.warnings.any { it.contains("checklist_row_recovered") && it.contains("items_null=true") },
            "the recovery is logged with items_null=true; got: ${logger.warnings}",
        )
    }

    @Test
    fun toChecklistSafe_corruptItemsJson_recoversToEmptyListAndLogsErrorWithThrowable() {
        val logger = RecordingLogger()
        val result = row(items = "not-a-json").toChecklistSafe(itemConverters, reminderConverters, logger)

        assertEquals(emptyList(), result.items, "invalid items JSON degrades to an empty list, never throws")
        assertTrue(
            logger.errors.any { it.contains("checklist_row_recovered") },
            "a parse failure is logged as an error (reaches Crashlytics); got: ${logger.errors}",
        )
    }

    @Test
    fun toChecklistSafe_validRow_mapsCleanlyAndDoesNotLog() {
        val logger = RecordingLogger()
        val itemsJson = itemConverters.toString(listOf(ChecklistItem(text = "Buy milk")))
        val result = row(name = "Shopping", items = itemsJson, viewMode = ChecklistViewMode.Weekly.name)
            .toChecklistSafe(itemConverters, reminderConverters, logger)

        assertEquals("Shopping", result.name)
        assertEquals(listOf("Buy milk"), result.items.map { it.text }, "valid items parse normally")
        assertEquals(ChecklistViewMode.Weekly, result.viewMode, "viewMode maps through the null-safe converter")
        assertTrue(logger.errors.isEmpty() && logger.warnings.isEmpty(), "a clean row logs nothing")
    }

    @Test
    fun toChecklistSafe_nullViewMode_fallsBackToStandard() {
        val logger = RecordingLogger()
        val result = row(viewMode = null).toChecklistSafe(itemConverters, reminderConverters, logger)

        assertEquals(ChecklistViewMode.Standard, result.viewMode, "a null viewMode falls back to Standard")
    }

    /**
     * [ChecklistRow] — not [ChecklistEntity] — is what the public `checklists` flow reads, so a
     * missing `isInbox` in [toChecklistSafe] compiles clean and leaves the flag permanently false
     * across the ENTIRE UI while the DB column is perfectly correct: the system Inbox would show up
     * in the Projects list and eat a free-tier slot. This test is the tripwire for that omission.
     */
    @Test
    fun toChecklistSafe_carriesIsInboxThrough() {
        val logger = RecordingLogger()

        assertTrue(
            row(isInbox = true).toChecklistSafe(itemConverters, reminderConverters, logger).isInbox,
            "isInbox must survive the row→domain mapping, else the system Inbox looks like a project",
        )
        assertEquals(
            false,
            row(isInbox = false).toChecklistSafe(itemConverters, reminderConverters, logger).isInbox,
            "an ordinary checklist must never be mapped as the Inbox",
        )
    }
}
