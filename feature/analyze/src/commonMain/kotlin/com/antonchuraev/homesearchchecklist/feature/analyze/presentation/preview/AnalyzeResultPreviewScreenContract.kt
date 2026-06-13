package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

data class AnalyzeResultPreviewScreenState(
    val isLoading: Boolean = true,
    val checklistName: String = "",
    val editableItems: List<String> = emptyList(),
    val newItemText: String = "",
    val summary: String? = null,
    val isFillMode: Boolean = false,
    val fillDefault: Boolean = false,
    val fillDefaultItems: List<ChecklistFillItem> = emptyList(),
    val targetChecklistName: String? = null,
    val isCreating: Boolean = false,
    val error: String? = null,
    /**
     * True when the AI returned a folder structure. In this mode the preview shows the tree
     * read-only ([structuredItems]) and creates the checklist with foldersEnabled = true.
     * Per-item text edit / add / remove are disabled to keep parentId/type intact — the user
     * restructures after creation (folder move/rename on the detail screen).
     */
    val hasFolders: Boolean = false,
    /**
     * Flat list of items WITH their tree links (parentId/type), preserved verbatim from the AI
     * result. Used for both the indented read-only preview and for creating the checklist when
     * [hasFolders] is true. Empty for the flat (legacy) path.
     */
    val structuredItems: List<ChecklistItem> = emptyList()
) : State

sealed interface AnalyzeResultPreviewScreenIntent : Intent {
    data object OnBackClick : AnalyzeResultPreviewScreenIntent
    data class OnChecklistNameChanged(val name: String) : AnalyzeResultPreviewScreenIntent
    data class OnRemoveItem(val index: Int) : AnalyzeResultPreviewScreenIntent
    data class OnNewItemTextChange(val text: String) : AnalyzeResultPreviewScreenIntent
    data object OnAddItem : AnalyzeResultPreviewScreenIntent
    data object OnCreateChecklist : AnalyzeResultPreviewScreenIntent
    data object OnDismissError : AnalyzeResultPreviewScreenIntent
}
