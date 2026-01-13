package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChecklistDetailViewModel(
    private val checklistId: Long,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator
) : AppViewModel<ChecklistDetailState, ChecklistDetailIntent, Nothing>() {

    private val _screenState = MutableStateFlow<ChecklistDetailState>(ChecklistDetailState.Loading)
    override val screenState: StateFlow<ChecklistDetailState> = _screenState.asStateFlow()

    init {
        loadChecklist()
    }

    private fun loadChecklist() {
        viewModelScope.launch {
            val checklist = repository.getChecklistById(checklistId)
            if (checklist != null) {
                _screenState.value = ChecklistDetailState.Content(
                    checklist = checklist,
                    editingName = checklist.name
                )
            } else {
                _screenState.value = ChecklistDetailState.NotFound
            }
        }
    }

    override fun onIntent(intent: ChecklistDetailIntent) {
        when (intent) {
            ChecklistDetailIntent.OnBackClick -> navigator.onBack()

            is ChecklistDetailIntent.OnItemCheckedChange -> updateItemChecked(intent.itemIndex, intent.checked)

            ChecklistDetailIntent.OnEditClick -> {
                updateContentState { it.copy(isEditing = true) }
            }

            ChecklistDetailIntent.OnSaveClick -> saveChanges()

            ChecklistDetailIntent.OnCancelEditClick -> {
                val state = _screenState.value
                if (state is ChecklistDetailState.Content) {
                    _screenState.value = state.copy(
                        isEditing = false,
                        editingName = state.checklist.name
                    )
                }
            }

            is ChecklistDetailIntent.OnNameChanged -> {
                updateContentState { it.copy(editingName = intent.name) }
            }

            is ChecklistDetailIntent.OnDeleteItemClick -> deleteItem(intent.itemIndex)

            ChecklistDetailIntent.OnDeleteChecklistClick -> {
                updateContentState { it.copy(showDeleteConfirmation = true) }
            }

            ChecklistDetailIntent.OnConfirmDelete -> deleteChecklist()

            ChecklistDetailIntent.OnDismissDeleteConfirmation -> {
                updateContentState { it.copy(showDeleteConfirmation = false) }
            }

            ChecklistDetailIntent.OnAddItemClick -> {
                // Will be handled by dialog in UI
            }

            is ChecklistDetailIntent.OnAddItem -> addItem(intent.text)
        }
    }

    private fun updateItemChecked(itemIndex: Int, checked: Boolean) {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content) {
            val updatedItems = state.checklist.items.toMutableList()
            if (itemIndex in updatedItems.indices) {
                updatedItems[itemIndex] = updatedItems[itemIndex].copy(checked = checked)
                val updatedChecklist = state.checklist.copy(items = updatedItems)
                _screenState.value = state.copy(checklist = updatedChecklist)

                viewModelScope.launch {
                    repository.updateChecklist(updatedChecklist)
                }
            }
        }
    }

    private fun saveChanges() {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content) {
            val updatedChecklist = state.checklist.copy(name = state.editingName)
            _screenState.value = state.copy(
                checklist = updatedChecklist,
                isEditing = false
            )

            viewModelScope.launch {
                repository.updateChecklist(updatedChecklist)
            }
        }
    }

    private fun deleteItem(itemIndex: Int) {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content) {
            val updatedItems = state.checklist.items.toMutableList()
            if (itemIndex in updatedItems.indices) {
                updatedItems.removeAt(itemIndex)
                val updatedChecklist = state.checklist.copy(items = updatedItems)
                _screenState.value = state.copy(checklist = updatedChecklist)

                viewModelScope.launch {
                    repository.updateChecklist(updatedChecklist)
                }
            }
        }
    }

    private fun addItem(text: String) {
        if (text.isBlank()) return

        val state = _screenState.value
        if (state is ChecklistDetailState.Content) {
            val newItem = ChecklistItem(text = text.trim(), checked = false)
            val updatedItems = state.checklist.items + newItem
            val updatedChecklist = state.checklist.copy(items = updatedItems)
            _screenState.value = state.copy(checklist = updatedChecklist)

            viewModelScope.launch {
                repository.updateChecklist(updatedChecklist)
            }
        }
    }

    private fun deleteChecklist() {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content) {
            viewModelScope.launch {
                repository.deleteChecklist(state.checklist)
                navigator.onBack()
            }
        }
    }

    private inline fun updateContentState(update: (ChecklistDetailState.Content) -> ChecklistDetailState.Content) {
        _screenState.update { state ->
            if (state is ChecklistDetailState.Content) update(state) else state
        }
    }
}
