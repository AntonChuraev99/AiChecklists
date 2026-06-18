package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem

/**
 * Soft recommendation for how many items a freshly generated checklist should start with. NOT a
 * hard cap: when the AI returns more than this, the preview includes the first
 * [MAX_RECOMMENDED_ITEMS] by default and surfaces the remainder in an expandable "show more"
 * section so nothing is lost and the user can add any back. Defined once here and reused by the VM,
 * the UI and the tests. Applies only to the FLAT (folders-off) path.
 */
const val MAX_RECOMMENDED_ITEMS = 10

data class AnalyzeResultPreviewScreenState(
    val isLoading: Boolean = true,
    val checklistName: String = "",
    /**
     * Items that WILL be created (the included set), in display order. For the flat path this
     * starts at the first [MAX_RECOMMENDED_ITEMS] of the generated list (soft cap); the rest live
     * in [overflowItems] until the user adds them. In folder mode this mirrors the structured
     * leaves but creation uses [structuredItems] instead.
     */
    val editableItems: List<String> = emptyList(),
    /**
     * Generated items held back by the soft cap (everything beyond [MAX_RECOMMENDED_ITEMS]), in
     * order. Shown in a collapsed "show N more" section; adding moves an item into [editableItems].
     * Always empty in folder mode and whenever the AI returned ≤ [MAX_RECOMMENDED_ITEMS] items.
     */
    val overflowItems: List<String> = emptyList(),
    /** Whether the collapsed "show N more" overflow section is expanded. */
    val isOverflowExpanded: Boolean = false,
    val newItemText: String = "",
    val summary: String? = null,
    val isFillMode: Boolean = false,
    val fillDefault: Boolean = false,
    val fillDefaultItems: List<ChecklistFillItem> = emptyList(),
    val targetChecklistName: String? = null,
    val isCreating: Boolean = false,
    val error: String? = null,
    /**
     * True when the AI returned a folder structure, i.e. there is something to fold. Drives ONLY
     * the visibility of the "Use folders" toggle — when the AI returned a flat list there is
     * nothing to organize, so the toggle is hidden. Whether folders are actually used is
     * [useFolders]. Always false in fill mode.
     */
    val aiReturnedFolders: Boolean = false,
    /**
     * User's choice for the "Use folders" toggle. DEFAULT OFF: a generated checklist is created
     * flat unless the user opts in. When true the preview renders the read-only tree
     * ([structuredItems]) and creates with `foldersEnabled = true` (the soft cap is skipped — the
     * structure already organizes many items). When false the preview renders the editable flat
     * list ([editableItems] + [overflowItems]) and creates a flat checklist. Always false in fill
     * mode and when [aiReturnedFolders] is false.
     */
    val useFolders: Boolean = false,
    /**
     * Flat list of items WITH their tree links (parentId/type), preserved verbatim from the AI
     * result. Used for the indented read-only preview and for creating the checklist when
     * [useFolders] is true. Empty for a flat (legacy) AI response.
     */
    val structuredItems: List<ChecklistItem> = emptyList()
) : State {
    /**
     * True when the soft cap actually held something back, so the UI shows the recommendation
     * line + the "show N more" section. Only meaningful on the flat path.
     */
    val hasOverflow: Boolean get() = overflowItems.isNotEmpty()
}

sealed interface AnalyzeResultPreviewScreenIntent : Intent {
    data object OnBackClick : AnalyzeResultPreviewScreenIntent
    data class OnChecklistNameChanged(val name: String) : AnalyzeResultPreviewScreenIntent
    data class OnRemoveItem(val index: Int) : AnalyzeResultPreviewScreenIntent
    data class OnNewItemTextChange(val text: String) : AnalyzeResultPreviewScreenIntent
    data object OnAddItem : AnalyzeResultPreviewScreenIntent
    data object OnCreateChecklist : AnalyzeResultPreviewScreenIntent
    data object OnDismissError : AnalyzeResultPreviewScreenIntent

    /** Toggle "Use folders" (only available when [AnalyzeResultPreviewScreenState.aiReturnedFolders]). */
    data class OnUseFoldersChanged(val enabled: Boolean) : AnalyzeResultPreviewScreenIntent

    /** Expand/collapse the soft-cap "show N more" overflow section. */
    data object OnToggleOverflowExpanded : AnalyzeResultPreviewScreenIntent

    /** Add a single held-back overflow item (by its index in [overflowItems]) into the included set. */
    data class OnAddOverflowItem(val index: Int) : AnalyzeResultPreviewScreenIntent

    /** Add every held-back overflow item into the included set (include all). */
    data object OnAddAllOverflowItems : AnalyzeResultPreviewScreenIntent
}
