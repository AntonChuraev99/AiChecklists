package com.antonchuraev.homesearchchecklist.feature.home.presentation.fills

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FillsListViewModel(
    private val checklistId: Long,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator
) : AppViewModel<FillsListState, FillsListIntent, Nothing>() {

    private val _screenState = MutableStateFlow<FillsListState>(FillsListState.Loading)
    override val screenState: StateFlow<FillsListState> = _screenState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val checklist = repository.getChecklistById(checklistId)
            if (checklist == null) {
                _screenState.value = FillsListState.NotFound
                return@launch
            }

            // Get all fills including default, sorted by date
            repository.getFillsByChecklistId(checklistId).collect { fills ->
                _screenState.value = FillsListState.Content(
                    checklist = checklist,
                    fills = fills
                )
            }
        }
    }

    override fun onIntent(intent: FillsListIntent) {
        when (intent) {
            FillsListIntent.OnBackClick -> navigator.onBack()

            is FillsListIntent.OnFillClick -> {
                navigator.navigateToFillDetail(intent.fill.id)
            }

            is FillsListIntent.OnDeleteFillClick -> {
                // Don't allow deleting default fill
                if (!intent.fill.isDefault) {
                    updateContentState { it.copy(fillToDelete = intent.fill) }
                }
            }

            FillsListIntent.OnConfirmDeleteFill -> {
                val state = _screenState.value
                if (state is FillsListState.Content && state.fillToDelete != null) {
                    deleteFill(state.fillToDelete)
                }
            }

            FillsListIntent.OnDismissDeleteDialog -> {
                updateContentState { it.copy(fillToDelete = null) }
            }
        }
    }

    private fun deleteFill(fill: ChecklistFill) {
        viewModelScope.launch {
            repository.deleteFill(fill)
            updateContentState { it.copy(fillToDelete = null) }
        }
    }

    private inline fun updateContentState(update: (FillsListState.Content) -> FillsListState.Content) {
        _screenState.update { state ->
            if (state is FillsListState.Content) update(state) else state
        }
    }
}
