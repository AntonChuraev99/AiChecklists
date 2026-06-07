package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplatesViewModel(
    private val appNavigator: AppNavigator,
    private val templatesRepository: TemplatesRepository,
    private val checklistRepository: ChecklistRepository,
    private val createWeeklyChecklistUseCase: CreateWeeklyChecklistUseCase,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
) : AppViewModel<TemplatesScreenState, TemplatesScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(TemplatesScreenState())
    override val screenState: StateFlow<TemplatesScreenState> = _screenState.asStateFlow()

    init {
        loadTemplates()
        observeUserLimits()
    }

    private fun observeUserLimits() {
        viewModelScope.launch {
            getUserLimitsUseCase().collect { limits ->
                _screenState.update {
                    it.copy(
                        canCreateChecklist = limits.canCreateChecklist,
                        canCreateWeeklyChecklist = limits.canCreateWeeklyChecklist
                    )
                }
            }
        }
    }

    override fun onIntent(intent: TemplatesScreenIntent) {
        when (intent) {
            TemplatesScreenIntent.OnBackClick -> appNavigator.onBack()
            is TemplatesScreenIntent.OnTemplateClick -> navigateToTemplatePreview(intent.template)
            TemplatesScreenIntent.OnDismissPreview -> dismissPreview()
            TemplatesScreenIntent.OnCreateFromTemplate -> createFromTemplate()
            TemplatesScreenIntent.OnDismissError -> dismissError()
            is TemplatesScreenIntent.OnSearchQueryChange -> onSearchQueryChange(intent.query)
            TemplatesScreenIntent.OnToggleSearch -> toggleSearch()
            TemplatesScreenIntent.OnCreateWeeklyClick -> handleCreateWeeklyClick()
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }

            try {
                val categories = templatesRepository.getTemplatesByCategory()
                _screenState.update {
                    it.copy(isLoading = false, categories = categories, filteredCategories = categories)
                }
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load templates")
                }
            }
        }
    }

    private fun toggleSearch() {
        val currentState = _screenState.value
        if (currentState.isSearchActive) {
            // Closing search — clear query and reset filter
            _screenState.update {
                it.copy(
                    isSearchActive = false,
                    searchQuery = "",
                    filteredCategories = it.categories
                )
            }
        } else {
            _screenState.update { it.copy(isSearchActive = true) }
        }
    }

    private fun onSearchQueryChange(query: String) {
        val filteredCategories = if (query.isBlank()) {
            _screenState.value.categories
        } else {
            _screenState.value.categories.mapNotNull { category ->
                val filteredTemplates = category.templates.filter { template ->
                    template.name.contains(query, ignoreCase = true) ||
                    template.description.contains(query, ignoreCase = true) ||
                    template.items.any { it.contains(query, ignoreCase = true) }
                }
                if (filteredTemplates.isEmpty()) {
                    null
                } else {
                    category.copy(templates = filteredTemplates)
                }
            }
        }
        _screenState.update {
            it.copy(searchQuery = query, filteredCategories = filteredCategories)
        }
    }

    private fun navigateToTemplatePreview(template: ChecklistTemplate) {
        appNavigator.navigateToTemplatePreview(template.id)
    }

    private fun dismissPreview() {
        _screenState.update {
            it.copy(selectedTemplate = null, showPreviewDialog = false)
        }
    }

    private fun createFromTemplate() {
        val template = _screenState.value.selectedTemplate ?: return

        viewModelScope.launch {
            _screenState.update { it.copy(isCreating = true) }

            try {
                val checklist = Checklist(
                    name = template.name,
                    items = template.items.map { ChecklistItem(text = it, checked = false) }
                )

                val checklistId = checklistRepository.addChecklist(checklist)

                _screenState.update {
                    it.copy(isCreating = false, showPreviewDialog = false, selectedTemplate = null)
                }

                // Navigate to the created checklist, clearing back stack
                appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isCreating = false, error = e.message ?: "Failed to create checklist")
                }
            }
        }
    }

    private fun handleCreateWeeklyClick() {
        viewModelScope.launch {
            when (val result = createWeeklyChecklistUseCase()) {
                is CreateWeeklyChecklistUseCase.Result.Created ->
                    appNavigator.navigateToChecklistDetail(result.checklistId, clearBackStack = true)
                CreateWeeklyChecklistUseCase.Result.RequiresUpgrade ->
                    appNavigator.navigateToPaywall(source = "weekly_mode_limit")
            }
        }
    }

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}

