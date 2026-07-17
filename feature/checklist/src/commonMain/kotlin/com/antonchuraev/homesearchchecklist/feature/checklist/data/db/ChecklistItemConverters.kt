package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.TypeConverter
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Thrown when a JSON-encoded column cannot be decoded back into its model.
 *
 * Room instantiates [ChecklistItemConverters] itself, so no `AppLogger` can be injected here.
 * Instead of logging, the converter enriches the failure with the one thing the raw throwable
 * lacks — WHICH column blew up — and rethrows. Whoever collects the flow logs it with context.
 *
 * The message quotes no payload: it reaches Crashlytics, and the JSON holds user-authored
 * checklist text. Note this excludes `cause.message` too — kotlinx.serialization embeds the
 * offending JSON input in its own message, so forwarding it would leak the very content this
 * avoids quoting (see `messageLeaksNoUserContent` in the tests).
 *
 * ⚠️ [cause] is still chained, because a Kotlin/Wasm NPE is only diagnosable via its stack —
 * and a chained kotlinx.serialization cause carries that JSON in ITS message. Redacting that
 * would mean dropping the stack, i.e. the whole point. Pre-existing exposure, not added here:
 * today the bare throwable already reaches the logger the same way.
 */
class ChecklistJsonDecodeException(
    column: String,
    rawLength: Int,
    cause: Throwable,
) : IllegalStateException(
    "Corrupted `$column` JSON in Room ($rawLength chars) — ${cause::class.simpleName}",
    cause,
)

class ChecklistItemConverters {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes a JSON list column, rewriting any failure into [ChecklistJsonDecodeException].
     *
     * A bare `decodeFromString` surfaces as a context-free throwable — a Kotlin/Wasm NPE arrives
     * with `message == null`, which reads as "something, somewhere, is null" and localises nothing.
     * This is exactly what happened on the prod web origin: an infinite Home spinner whose only
     * evidence was `NullPointerException: null`.
     */
    private fun <T> decodeList(
        value: String,
        column: String,
        serializer: KSerializer<T>,
    ): List<T> =
        try {
            json.decodeFromString(ListSerializer(serializer), value)
        } catch (e: Exception) {
            throw ChecklistJsonDecodeException(column, value.length, e)
        }

    @TypeConverter
    fun fromString(value: String): List<ChecklistItem> {
        if (value.isEmpty()) return emptyList()
        return decodeList(value, "checklists.items", ChecklistItem.serializer())
    }

    @TypeConverter
    fun toString(items: List<ChecklistItem>): String {
        if (items.isEmpty()) return ""
        return json.encodeToString(ListSerializer(ChecklistItem.serializer()), items)
    }

    @TypeConverter
    fun fillItemsFromString(value: String): List<ChecklistFillItem> {
        if (value.isEmpty()) return emptyList()
        return decodeList(value, "checklist_fills.items", ChecklistFillItem.serializer())
    }

    @TypeConverter
    fun fillItemsToString(items: List<ChecklistFillItem>): String {
        if (items.isEmpty()) return ""
        return json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), items)
    }

    @TypeConverter
    fun viewModeFromString(value: String?): ChecklistViewMode {
        return when (value) {
            ChecklistViewMode.Weekly.name -> ChecklistViewMode.Weekly
            else -> ChecklistViewMode.Standard
        }
    }

    @TypeConverter
    fun viewModeToString(mode: ChecklistViewMode): String {
        return mode.name
    }
}
