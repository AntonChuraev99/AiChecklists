package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps a server-side [AgentToolCall] (name + [JsonObject] args) to a dispatchable
 * [ToolCall], or returns null when the name is unknown or a required arg is
 * missing/blank. The agent loop turns null into an error function_response so
 * Gemini can self-correct.
 *
 * All parsing is pure and has no side effects — safe to unit test without
 * a coroutine scope or fakes.
 */
internal object AgentToolCallMapper {

    /**
     * Maps [agentToolCall] to a [ToolCall], or null when mapping is not possible.
     *
     * Mapping table:
     * - `add_item`          → [ToolCall.AddItem]      (null if item_text blank)
     * - `add_items`         → [ToolCall.AddItems]     (null if list empty/blank)
     * - `create_checklist`  → [ToolCall.CreateChecklist]  (null if name blank)
     * - `complete_item`     → [ToolCall.CompleteItem] (null if item_text blank)
     * - `delete_item`       → [ToolCall.DeleteItem]   (null if item_text blank)
     * - `set_item_reminder` → [ToolCall.SetItemReminder] (null if item_text blank or when_iso unparseable)
     * - `rename_checklist`  → [ToolCall.RenameChecklist]  (null if either arg blank)
     * - `find_items`        → [ToolCall.FindItemsQuery] (null if query blank)
     * - `read_checklist`    → [ToolCall.ReadChecklist]  (null if name blank)
     * - anything else       → null
     */
    fun map(agentToolCall: AgentToolCall): ToolCall? {
        val args = agentToolCall.args
        return when (agentToolCall.name) {
            "add_item" -> {
                val itemText = args.stringOrNull("item_text") ?: return null
                if (itemText.isBlank()) return null
                ToolCall.AddItem(
                    checklistHint = args.stringOrNull("checklist_hint"),
                    itemText = itemText,
                )
            }

            "add_items" -> {
                val items = args["item_texts"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                    ?: return null
                if (items.isEmpty()) return null
                ToolCall.AddItems(
                    checklistHint = args.stringOrNull("checklist_hint"),
                    itemTexts = items,
                )
            }

            "create_checklist" -> {
                val name = args.stringOrNull("name") ?: return null
                if (name.isBlank()) return null
                val initialItems = args["initial_items"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                    ?: emptyList()
                ToolCall.CreateChecklist(name = name, initialItems = initialItems)
            }

            "complete_item" -> {
                val itemText = args.stringOrNull("item_text") ?: return null
                if (itemText.isBlank()) return null
                ToolCall.CompleteItem(
                    checklistHint = args.stringOrNull("checklist_hint"),
                    itemText = itemText,
                )
            }

            "delete_item" -> {
                val itemText = args.stringOrNull("item_text") ?: return null
                if (itemText.isBlank()) return null
                ToolCall.DeleteItem(
                    checklistHint = args.stringOrNull("checklist_hint"),
                    itemText = itemText,
                )
            }

            "set_item_reminder" -> {
                val itemText = args.stringOrNull("item_text") ?: return null
                if (itemText.isBlank()) return null
                val whenIso = args.stringOrNull("when_iso") ?: return null
                val atMs = parseIsoToEpochMs(whenIso) ?: return null
                ToolCall.SetItemReminder(
                    checklistHint = args.stringOrNull("checklist_hint"),
                    itemText = itemText,
                    at = atMs,
                )
            }

            "rename_checklist" -> {
                val checklistHint = args.stringOrNull("checklist_hint") ?: return null
                val newName = args.stringOrNull("new_name") ?: return null
                if (checklistHint.isBlank() || newName.isBlank()) return null
                ToolCall.RenameChecklist(checklistHint = checklistHint, newName = newName)
            }

            "find_items" -> {
                val query = args.stringOrNull("query") ?: return null
                if (query.isBlank()) return null
                ToolCall.FindItemsQuery(query = query)
            }

            "read_checklist" -> {
                val name = args.stringOrNull("name") ?: return null
                if (name.isBlank()) return null
                ToolCall.ReadChecklist(name = name)
            }

            else -> null
        }
    }

    /**
     * Parses an ISO-8601 string to epoch milliseconds.
     *
     * Strategy:
     * 1. Try [Instant.parse] — handles strings with Z or UTC offset (e.g. "2026-06-01T09:00:00Z").
     * 2. Fallback: [LocalDateTime.parse] → converts to Instant using the device's default timezone.
     *    This handles server-generated strings without a timezone suffix (e.g. "2026-06-01T09:00:00").
     *
     * Returns null if both attempts throw (malformed input). Wrapped in [runCatching] so
     * invalid ISO strings don't propagate as exceptions.
     */
    internal fun parseIsoToEpochMs(iso: String): Long? {
        // Attempt 1: full instant with offset/Z
        runCatching { Instant.parse(iso) }.getOrNull()?.let { return it.toEpochMilliseconds() }
        // Attempt 2: local datetime without offset
        runCatching {
            LocalDateTime.parse(iso).toInstant(TimeZone.currentSystemDefault())
        }.getOrNull()?.let { return it.toEpochMilliseconds() }
        return null
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Returns the string value for [key] if present and non-null, otherwise null.
     * Absent key, JSON null, and blank-after-trim are handled: blank → null.
     */
    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
