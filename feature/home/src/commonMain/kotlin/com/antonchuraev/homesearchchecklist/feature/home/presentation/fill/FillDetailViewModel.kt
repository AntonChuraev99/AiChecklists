package com.antonchuraev.homesearchchecklist.feature.home.presentation.fill

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FillDetailViewModel(
    private val fillId: Long,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator
) : AppViewModel<FillDetailState, FillDetailIntent, Nothing>() {

    private val _screenState = MutableStateFlow<FillDetailState>(FillDetailState.Loading)
    override val screenState: StateFlow<FillDetailState> = _screenState.asStateFlow()

    init {
        loadFill()
    }

    private fun loadFill() {
        viewModelScope.launch {
            val fill = repository.getFillById(fillId)
            if (fill != null) {
                _screenState.value = FillDetailState.Content(
                    fill = fill,
                    editingName = fill.name
                )
            } else {
                _screenState.value = FillDetailState.NotFound
            }
        }
    }

    override fun onIntent(intent: FillDetailIntent) {
        when (intent) {
            FillDetailIntent.OnBackClick -> navigator.onBack()

            FillDetailIntent.OnEditClick -> {
                updateContentState { it.copy(isEditing = true) }
            }

            FillDetailIntent.OnSaveClick -> saveChanges()

            FillDetailIntent.OnCancelEditClick -> {
                val state = _screenState.value
                if (state is FillDetailState.Content) {
                    _screenState.value = state.copy(
                        isEditing = false,
                        editingName = state.fill.name
                    )
                }
            }

            is FillDetailIntent.OnNameChanged -> {
                updateContentState { it.copy(editingName = intent.name) }
            }

            FillDetailIntent.OnDeleteClick -> {
                updateContentState { it.copy(showDeleteConfirmation = true) }
            }

            FillDetailIntent.OnConfirmDelete -> deleteFill()

            FillDetailIntent.OnDismissDeleteConfirmation -> {
                updateContentState { it.copy(showDeleteConfirmation = false) }
            }

            is FillDetailIntent.OnItemCheckedChange -> updateItemChecked(intent.itemIndex, intent.checked)

            is FillDetailIntent.OnAddNoteClick -> {
                val state = _screenState.value
                if (state is FillDetailState.Content) {
                    val currentNote = state.fill.items.getOrNull(intent.itemIndex)?.note ?: ""
                    updateContentState {
                        it.copy(
                            noteDialogItemIndex = intent.itemIndex,
                            editingNote = currentNote
                        )
                    }
                }
            }

            is FillDetailIntent.OnNoteChanged -> {
                updateContentState { it.copy(editingNote = intent.note) }
            }

            FillDetailIntent.OnSaveNote -> saveNote()

            FillDetailIntent.OnDismissNoteDialog -> {
                updateContentState { it.copy(noteDialogItemIndex = null, editingNote = "") }
            }

            FillDetailIntent.OnChangeCoverClick -> {
                // Will be implemented with photo picker
            }

            FillDetailIntent.OnRemoveCoverClick -> removeCover()
        }
    }

    private fun updateItemChecked(itemIndex: Int, checked: Boolean) {
        val state = _screenState.value
        if (state is FillDetailState.Content) {
            val updatedItems = state.fill.items.toMutableList()
            if (itemIndex in updatedItems.indices) {
                updatedItems[itemIndex] = updatedItems[itemIndex].copy(checked = checked)
                val updatedFill = state.fill.copy(items = updatedItems)
                _screenState.value = state.copy(fill = updatedFill)

                viewModelScope.launch {
                    repository.updateFill(updatedFill)
                }
            }
        }
    }

    private fun saveChanges() {
        val state = _screenState.value
        if (state is FillDetailState.Content) {
            val updatedFill = state.fill.copy(name = state.editingName)
            _screenState.value = state.copy(
                fill = updatedFill,
                isEditing = false
            )

            viewModelScope.launch {
                repository.updateFill(updatedFill)
            }
        }
    }

    private fun saveNote() {
        val state = _screenState.value
        if (state is FillDetailState.Content && state.noteDialogItemIndex != null) {
            val itemIndex = state.noteDialogItemIndex
            val updatedItems = state.fill.items.toMutableList()
            if (itemIndex in updatedItems.indices) {
                val noteText = state.editingNote.trim().takeIf { it.isNotEmpty() }
                updatedItems[itemIndex] = updatedItems[itemIndex].copy(note = noteText)
                val updatedFill = state.fill.copy(items = updatedItems)
                _screenState.value = state.copy(
                    fill = updatedFill,
                    noteDialogItemIndex = null,
                    editingNote = ""
                )

                viewModelScope.launch {
                    repository.updateFill(updatedFill)
                }
            }
        }
    }

    private fun removeCover() {
        val state = _screenState.value
        if (state is FillDetailState.Content) {
            val updatedFill = state.fill.copy(coverImagePath = null)
            _screenState.value = state.copy(fill = updatedFill)

            viewModelScope.launch {
                repository.updateFill(updatedFill)
            }
        }
    }

    private fun deleteFill() {
        val state = _screenState.value
        if (state is FillDetailState.Content) {
            viewModelScope.launch {
                repository.deleteFill(state.fill)
                navigator.onBack()
            }
        }
    }

    private inline fun updateContentState(update: (FillDetailState.Content) -> FillDetailState.Content) {
        _screenState.update { state ->
            if (state is FillDetailState.Content) update(state) else state
        }
    }
}
