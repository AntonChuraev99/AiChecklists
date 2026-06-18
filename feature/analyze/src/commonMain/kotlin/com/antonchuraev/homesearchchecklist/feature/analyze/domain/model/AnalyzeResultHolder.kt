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
        fillDefaultItems: List<ChecklistFillItem>? = null,
        hasFolders: Boolean = false,
        fromActivation: Boolean = false
    ) {
        data = AnalyzeResultData(
            items = items.toMutableList(),
            suggestedName = suggestedName,
            summary = summary,
            isFillMode = isFillMode,
            fillDefault = fillDefault,
            targetChecklistId = targetChecklistId,
            targetChecklistName = targetChecklistName,
            fillDefaultItems = fillDefaultItems,
            hasFolders = hasFolders,
            fromActivation = fromActivation
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
    val fillDefaultItems: List<ChecklistFillItem>? = null,
    /**
     * True when [items] carry an AI-detected folder structure (parentId/type). The preview
     * shows the tree read-only and creates the checklist with foldersEnabled = true. Always
     * false in fill mode and for flat responses.
     */
    val hasFolders: Boolean = false,
    /**
     * True when this analysis was kicked off by the new-user activation hero (chip / typed
     * topic with autoAnalyze). The preview VM fires the activation funnel
     * (FIRST_AI_CHECKLIST_CREATED + reminder opt-in) on confirm, so the activation analytics
     * keep flowing even though creation now goes through the Analyze path (not the chat
     * dispatcher). Always false for the normal Analyze / ACTION_PROCESS_TEXT flows.
     */
    val fromActivation: Boolean = false
)
