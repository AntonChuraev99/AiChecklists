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
    val error: String? = null,
    // Gate: false when free user has hit the checklist creation limit
    val canCreateChecklist: Boolean = true,
    // Gate: false when free user has hit the weekly checklist limit (separate from the
    // overall limit — see UserLimits.canCreateWeeklyChecklist)
    val canCreateWeeklyChecklist: Boolean = true
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
    data object OnCreateWeeklyClick : TemplatesScreenIntent
}
