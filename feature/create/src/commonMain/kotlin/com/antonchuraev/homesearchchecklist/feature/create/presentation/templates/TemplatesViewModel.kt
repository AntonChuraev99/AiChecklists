package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplatesViewModel(
    private val appNavigator: AppNavigator,
    private val templatesRepository: TemplatesRepository,
    private val checklistRepository: ChecklistRepository
) : AppViewModel<TemplatesScreenState, TemplatesScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(TemplatesScreenState())
    override val screenState: StateFlow<TemplatesScreenState> = _screenState.asStateFlow()

    init {
        loadTemplates()
    }

    override fun onIntent(intent: TemplatesScreenIntent) {
        when (intent) {
            TemplatesScreenIntent.OnBackClick -> appNavigator.onBack()
            is TemplatesScreenIntent.OnTemplateClick -> showTemplatePreview(intent.template)
            TemplatesScreenIntent.OnDismissPreview -> dismissPreview()
            TemplatesScreenIntent.OnCreateFromTemplate -> createFromTemplate()
            TemplatesScreenIntent.OnDismissError -> dismissError()
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true, error = null) }

            try {
                val categories = templatesRepository.getTemplatesByCategory()
                _screenState.update {
                    it.copy(isLoading = false, categories = categories)
                }
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load templates")
                }
            }
        }
    }

    private fun showTemplatePreview(template: ChecklistTemplate) {
        _screenState.update {
            it.copy(selectedTemplate = template, showPreviewDialog = true)
        }
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

                // Navigate to the created checklist
                appNavigator.navigateToChecklistDetail(checklistId)
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isCreating = false, error = e.message ?: "Failed to create checklist")
                }
            }
        }
    }

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}

