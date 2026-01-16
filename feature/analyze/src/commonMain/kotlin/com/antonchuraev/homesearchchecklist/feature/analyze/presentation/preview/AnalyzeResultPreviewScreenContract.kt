package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class AnalyzeResultPreviewScreenState(
    val isLoading: Boolean = true,
    val checklistName: String = "",
    val editableItems: List<String> = emptyList(),
    val newItemText: String = "",
    val summary: String? = null,
    val isFillMode: Boolean = false,
    val targetChecklistName: String? = null,
    val isCreating: Boolean = false,
    val error: String? = null
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
