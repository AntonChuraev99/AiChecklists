package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory

data class TemplatesScreenState(
    val isLoading: Boolean = true,
    val categories: List<TemplateCategory> = emptyList(),
    val filteredCategories: List<TemplateCategory> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedTemplate: ChecklistTemplate? = null,
    val showPreviewDialog: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null
) : State

sealed interface TemplatesScreenIntent : Intent {
    data object OnBackClick : TemplatesScreenIntent
    data class OnTemplateClick(val template: ChecklistTemplate) : TemplatesScreenIntent
    data object OnDismissPreview : TemplatesScreenIntent
    data object OnCreateFromTemplate : TemplatesScreenIntent
    data object OnDismissError : TemplatesScreenIntent
    data class OnSearchQueryChange(val query: String) : TemplatesScreenIntent
    data object OnToggleSearch : TemplatesScreenIntent

    // Bottom action buttons
    data object OnCreateManuallyClick : TemplatesScreenIntent
    data object OnCreateWithAiClick : TemplatesScreenIntent
    data object OnCreateWeeklyClick : TemplatesScreenIntent
}
