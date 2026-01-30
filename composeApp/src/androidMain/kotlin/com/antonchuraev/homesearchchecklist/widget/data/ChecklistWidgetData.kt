package com.antonchuraev.homesearchchecklist.widget.data

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem

/**
 * Data model for displaying checklist in widget.
 * Uses items from ChecklistFill (user progress), NOT from Checklist template.
 */
data class ChecklistWidgetData(
    val checklistId: Long,
    val name: String,
    val items: List<ChecklistFillItem>,
    val fillId: Long?,
    val notFound: Boolean = false
) {
    val checkedCount: Int
        get() = items.count { it.checked }

    val totalCount: Int
        get() = items.size

    val progressText: String
        get() = "$checkedCount/$totalCount"

    companion object {
        fun notFound(checklistId: Long) = ChecklistWidgetData(
            checklistId = checklistId,
            name = "",
            items = emptyList(),
            fillId = null,
            notFound = true
        )
    }
}
