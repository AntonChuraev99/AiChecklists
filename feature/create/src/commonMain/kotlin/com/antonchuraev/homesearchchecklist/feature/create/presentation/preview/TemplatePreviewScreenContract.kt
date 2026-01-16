package com.antonchuraev.homesearchchecklist.feature.create.presentation.preview

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate

data class TemplatePreviewScreenState(
    val isLoading: Boolean = true,
    val template: ChecklistTemplate? = null,
    val editableItems: List<String> = emptyList(),
    val newItemText: String = "",
    val isCreating: Boolean = false,
    val error: String? = null
) : State

sealed interface TemplatePreviewScreenIntent : Intent {
    data object OnBackClick : TemplatePreviewScreenIntent
    data class OnRemoveItem(val index: Int) : TemplatePreviewScreenIntent
    data class OnNewItemTextChange(val text: String) : TemplatePreviewScreenIntent
    data object OnAddItem : TemplatePreviewScreenIntent
    data object OnCreateChecklist : TemplatePreviewScreenIntent
    data object OnDismissError : TemplatePreviewScreenIntent
}
