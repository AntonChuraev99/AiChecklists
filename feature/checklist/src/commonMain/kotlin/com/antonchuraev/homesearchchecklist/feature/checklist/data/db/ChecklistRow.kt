package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist

/**
 * Crash-tolerant read projection of the `checklists` table.
 *
 * Unlike [ChecklistEntity], the JSON/text columns that a corrupt row can hold are read as **raw,
 * nullable** values (`name`, `items`, `viewMode`, `repeatRule` as `String?`) so the Room cursor
 * mapping never (a) assigns a NULL cell into a non-null field, nor (b) invokes a TypeConverter that
 * can throw while the flow is being collected. Both are what killed the whole `checklists` flow on
 * the prod web origin (a `NullPointerException` with `message == null` on the wasmJs `sqlite-web`
 * driver, and a `ChecklistJsonDecodeException` on any invalid `items` JSON), leaving every reader —
 * all four Home boot flows — stuck on an infinite spinner.
 *
 * The recovery from raw values to a domain [Checklist] happens in [toChecklistSafe], where each
 * bad field degrades to a safe default and is logged, but the row is **always** returned so it stays
 * visible and the user can still open or delete it.
 */
data class ChecklistRow(
    val id: Long,
    val name: String?,
    // Raw JSON — decoded defensively in toChecklistSafe(), never by a cursor-time TypeConverter.
    val items: String?,
    val reminderAt: Long?,
    // Raw JSON for ReminderRepeatRule — decoded defensively in toChecklistSafe().
    val repeatRule: String?,
    val repeatTimeOfDayMinutes: Int?,
    val repeatNextAt: Long?,
    val repeatOccurrenceCount: Int,
    val reminderFullScreen: Boolean,
    val separateCompleted: Boolean,
    val position: Int,
    val autoDeleteCompleted: Boolean,
    // Raw viewMode name — mapped via the existing null-safe converter in toChecklistSafe().
    val viewMode: String?,
    val foldersEnabled: Boolean,
    val cloudId: String?,
    val userId: String?,
    val updatedAt: Long,
    val syncStatus: Int,
    val isDeleted: Boolean,
    /**
     * System-Inbox marker — see [Checklist.isInbox].
     *
     * Non-null like every other scalar column: it is `INTEGER NOT NULL DEFAULT 0`, so the cursor can
     * never hand back NULL here and the crash-tolerance rationale above does not apply.
     *
     * This projection — NOT [ChecklistEntity] — backs the public `checklists` flow, so forgetting
     * the field here compiles clean and leaves `isInbox` permanently false across the entire UI
     * while the DB column is perfectly correct. Keep it wired in [toChecklistSafe].
     */
    val isInbox: Boolean,
)

internal const val CHECKLIST_ROW_TAG = "ChecklistRepository"

/**
 * Maps a raw [ChecklistRow] into a domain [Checklist], recovering any corrupt field to a safe
 * default and logging the recovery for prod observability. Never throws and never drops the row.
 *
 * - `name == null` → `""` (logged as a warning).
 * - `items` null/blank → `emptyList()`; invalid JSON → `emptyList()` (logged as an error with the
 *   throwable so it reaches Crashlytics / console.error).
 * - `viewMode` / `repeatRule` go through the existing null-safe converters ([ChecklistItemConverters]
 *   / [ReminderConverters]), which already fall back to a default on a parse failure.
 *
 * Top-level `internal` (not a private member of the repository) so it is unit-testable off-device
 * with a directly constructed [ChecklistRow] — the NULL-column path is otherwise masked on the JVM
 * host, where the bundled SQLite driver coerces a NULL TEXT cell to `""`.
 */
internal fun ChecklistRow.toChecklistSafe(
    itemConverters: ChecklistItemConverters,
    reminderConverters: ReminderConverters,
    logger: AppLogger,
): Checklist {
    val nameWasNull = name == null
    var itemsFailure: Throwable? = null

    val rawItems = items
    val safeItems = if (rawItems == null || rawItems.isEmpty()) {
        emptyList()
    } else {
        // rawItems is smart-cast to non-null String here (the `== null` disjunct is false).
        runCatching { itemConverters.fromString(rawItems) }
            .getOrElse { e ->
                itemsFailure = e
                emptyList()
            }
    }

    // Log only when something was actually recovered, so a clean row stays silent.
    if (nameWasNull || items == null || itemsFailure != null) {
        val message = "checklist_row_recovered id=$id name_null=$nameWasNull " +
            "items_null=${items == null} items_len=${items?.length ?: -1}"
        if (itemsFailure != null) {
            logger.error(CHECKLIST_ROW_TAG, message, itemsFailure)
        } else {
            logger.warning(CHECKLIST_ROW_TAG, message)
        }
    }

    return Checklist(
        id = id,
        name = name ?: "",
        items = safeItems,
        reminderAt = reminderAt,
        repeatRule = reminderConverters.repeatRuleFromString(repeatRule),
        repeatTimeOfDayMinutes = repeatTimeOfDayMinutes,
        repeatNextAt = repeatNextAt,
        repeatOccurrenceCount = repeatOccurrenceCount,
        reminderFullScreen = reminderFullScreen,
        separateCompleted = separateCompleted,
        position = position,
        autoDeleteCompleted = autoDeleteCompleted,
        viewMode = itemConverters.viewModeFromString(viewMode),
        foldersEnabled = foldersEnabled,
        cloudId = cloudId,
        userId = userId,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        isDeleted = isDeleted,
        isInbox = isInbox,
    )
}
