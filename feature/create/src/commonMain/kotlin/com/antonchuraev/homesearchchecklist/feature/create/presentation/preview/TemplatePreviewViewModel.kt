package com.antonchuraev.homesearchchecklist.feature.create.presentation.preview

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplatePreviewViewModel(
    private val templateId: String,
    private val appNavigator: AppNavigator,
    private val templatesRepository: TemplatesRepository,
    private val checklistRepository: ChecklistRepository
) : AppViewModel<TemplatePreviewScreenState, TemplatePreviewScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(TemplatePreviewScreenState())
    override val screenState: StateFlow<TemplatePreviewScreenState> = _screenState.asStateFlow()

    init {
        loadTemplate()
    }

    override fun onIntent(intent: TemplatePreviewScreenIntent) {
        when (intent) {
            TemplatePreviewScreenIntent.OnBackClick -> appNavigator.onBack()
            is TemplatePreviewScreenIntent.OnRemoveItem -> removeItem(intent.index)
            is TemplatePreviewScreenIntent.OnNewItemTextChange -> updateNewItemText(intent.text)
            TemplatePreviewScreenIntent.OnAddItem -> addItem()
            TemplatePreviewScreenIntent.OnCreateChecklist -> createChecklist()
            TemplatePreviewScreenIntent.OnDismissError -> dismissError()
        }
    }

    private fun loadTemplate() {
        viewModelScope.launch {
            _screenState.update { it.copy(isLoading = true) }

            try {
                val template = templatesRepository.getTemplateById(templateId)
                if (template != null) {
                    _screenState.update {
                        it.copy(
                            isLoading = false,
                            template = template,
                            editableItems = template.items.toList()
                        )
                    }
                } else {
                    _screenState.update {
                        it.copy(isLoading = false, error = "Template not found")
                    }
                }
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load template")
                }
            }
        }
    }

    private fun removeItem(index: Int) {
        _screenState.update { state ->
            val newItems = state.editableItems.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            state.copy(editableItems = newItems)
        }
    }

    private fun updateNewItemText(text: String) {
        _screenState.update { it.copy(newItemText = text) }
    }

    private fun addItem() {
        val text = _screenState.value.newItemText.trim()
        if (text.isNotEmpty()) {
            _screenState.update { state ->
                state.copy(
                    editableItems = state.editableItems + text,
                    newItemText = ""
                )
            }
        }
    }

    private fun createChecklist() {
        val state = _screenState.value
        val template = state.template ?: return

        if (state.editableItems.isEmpty()) {
            _screenState.update { it.copy(error = "Add at least one item") }
            return
        }

        viewModelScope.launch {
            _screenState.update { it.copy(isCreating = true) }

            try {
                val checklist = Checklist(
                    name = template.name,
                    items = state.editableItems.map { ChecklistItem(text = it, checked = false) }
                )

                val checklistId = checklistRepository.addChecklist(checklist)

                _screenState.update { it.copy(isCreating = false) }

                appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
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
