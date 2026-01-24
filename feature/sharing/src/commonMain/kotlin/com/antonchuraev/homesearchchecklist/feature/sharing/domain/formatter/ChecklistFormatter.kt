package com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem

/**
 * Structured data for PDF generation
 */
data class PdfContent(
    val title: String,
    val subtitle: String?,
    val items: List<PdfItem>,
    val progress: String
)

data class PdfItem(
    val text: String,
    val checked: Boolean,
    val note: String?
)

/**
 * Formats checklist data for sharing in various formats
 */
class ChecklistFormatter {

    /**
     * Formats a checklist fill as plain text with checkbox symbols
     */
    fun formatAsText(checklist: Checklist, fill: ChecklistFill): String {
        val checkedCount = fill.items.count { it.checked }
        val totalCount = fill.items.size

        return buildString {
            appendLine(checklist.name)
            if (fill.name.isNotBlank() && !fill.isDefault) {
                appendLine("Fill: ${fill.name}")
            }
            appendLine("Progress: $checkedCount/$totalCount")
            appendLine()

            fill.items.forEach { item ->
                val checkbox = if (item.checked) "[x]" else "[ ]"
                appendLine("$checkbox ${item.text}")
                item.note?.let { note ->
                    appendLine("    Note: $note")
                }
            }
        }.trim()
    }

    /**
     * Formats checklist data for PDF generation
     */
    fun formatForPdf(checklist: Checklist, fill: ChecklistFill): PdfContent {
        val checkedCount = fill.items.count { it.checked }
        val totalCount = fill.items.size

        return PdfContent(
            title = checklist.name,
            subtitle = if (fill.name.isNotBlank() && !fill.isDefault) fill.name else null,
            items = fill.items.map { item ->
                PdfItem(
                    text = item.text,
                    checked = item.checked,
                    note = item.note
                )
            },
            progress = "$checkedCount/$totalCount completed"
        )
    }
}
