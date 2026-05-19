package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.preview

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Converts a [ToolCall] into a human-readable preview string for [ChatPreviewCard].
 *
 * Rendering is intentionally simple in Phase A: no locale-aware formatting,
 * timestamps shown as HH:mm local time. Layer 2/3 may enrich this.
 */
interface ToolCallPreviewRenderer {
    fun render(toolCall: ToolCall): String
}

internal class ToolCallPreviewRendererImpl : ToolCallPreviewRenderer {

    override fun render(toolCall: ToolCall): String = when (toolCall) {
        is ToolCall.AddItem -> buildString {
            append("• ${toolCall.itemText}")
            toolCall.checklistHint?.let { append(" → $it") }
        }

        is ToolCall.DeleteItem -> buildString {
            append("• ${toolCall.itemText}")
            toolCall.checklistHint?.let { append(" (in $it)") }
        }

        is ToolCall.CompleteItem -> buildString {
            append("• ${toolCall.itemText}")
            toolCall.checklistHint?.let { append(" (in $it)") }
        }

        is ToolCall.CreateChecklist -> buildString {
            append(toolCall.name)
            if (toolCall.initialItems.isNotEmpty()) {
                append("\n")
                append(toolCall.initialItems.joinToString("\n") { "• $it" })
            }
        }

        is ToolCall.SetItemReminder -> buildString {
            append("• ${toolCall.itemText}")
            toolCall.checklistHint?.let { append(" (in $it)") }
            append(" → ${formatTimestamp(toolCall.at)}")
        }

        is ToolCall.MoveAllReminders -> buildString {
            append("${formatDay(toolCall.fromDayStartMs)} → ${formatDay(toolCall.toDayStartMs)}")
        }

        // FindItemsQuery renders inline (no preview card), this is a safety fallback
        is ToolCall.FindItemsQuery -> ""

        is ToolCall.CreateChecklistFromAttachment -> buildString {
            val count = toolCall.attachments.size
            append("Create checklist from ")
            append(if (count == 1) toolCall.attachments.first().fileName else "$count files")
        }

        is ToolCall.AttachToItem -> buildString {
            val count = toolCall.attachments.size
            append("Attach ")
            append(if (count == 1) toolCall.attachments.first().fileName else "$count files")
            append(" to • ${toolCall.itemText}")
            toolCall.checklistHint?.let { append(" (in $it)") }
        }
    }

    // ─── Timestamp helpers (kotlinx-datetime, KMP-safe) ──────────────────────

    private fun formatTimestamp(epochMs: Long): String {
        val tz = TimeZone.currentSystemDefault()
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val h = dt.hour.toString().padStart(2, '0')
        val m = dt.minute.toString().padStart(2, '0')
        val dayName = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        val monthName = dt.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        return "$dayName, $monthName ${dt.dayOfMonth} at $h:$m"
    }

    private fun formatDay(epochMs: Long): String {
        val tz = TimeZone.currentSystemDefault()
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val dayName = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        val monthName = dt.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }
        return "$dayName, $monthName ${dt.dayOfMonth}"
    }
}
