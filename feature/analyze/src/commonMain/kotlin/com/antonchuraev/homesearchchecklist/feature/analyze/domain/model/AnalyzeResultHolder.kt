package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

/**
 * Temporary holder for passing analyze result data to preview screen.
 * This avoids complex serialization through navigation arguments.
 */
object AnalyzeResultHolder {
    private var data: AnalyzeResultData? = null

    fun set(
        items: List<ChecklistItem>,
        suggestedName: String,
        summary: String?,
        isFillMode: Boolean = false,
        fillDefault: Boolean = false,
        targetChecklistId: Long? = null,
        targetChecklistName: String? = null,
        fillDefaultItems: List<ChecklistFillItem>? = null
    ) {
        data = AnalyzeResultData(
            items = items.toMutableList(),
            suggestedName = suggestedName,
            summary = summary,
            isFillMode = isFillMode,
            fillDefault = fillDefault,
            targetChecklistId = targetChecklistId,
            targetChecklistName = targetChecklistName,
            fillDefaultItems = fillDefaultItems
        )
    }

    fun get(): AnalyzeResultData? = data

    fun clear() {
        data = null
    }
}

data class AnalyzeResultData(
    val items: MutableList<ChecklistItem>,
    val suggestedName: String,
    val summary: String?,
    val isFillMode: Boolean,
    val fillDefault: Boolean = false,
    val targetChecklistId: Long?,
    val targetChecklistName: String?,
    val fillDefaultItems: List<ChecklistFillItem>? = null
)
