package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val checklist = repository.getChecklistById(checklistId)
            if (checklist == null) {
                _screenState.value = ChecklistDetailState.NotFound
                return@launch
            }

            repository.getFillsByChecklistId(checklistId).collect { fills ->
                _screenState.value = ChecklistDetailState.Content(
                    checklist = checklist,
                    fills = fills
                )
            }
        }
    }

    override fun onIntent(intent: ChecklistDetailIntent) {
        when (intent) {
            ChecklistDetailIntent.OnBackClick -> navigator.onBack()

            ChecklistDetailIntent.OnEditChecklistClick -> {
                // Navigate to edit checklist (could reuse CreateChecklistScreen)
            }

            ChecklistDetailIntent.OnDeleteChecklistClick -> {
                updateContentState { it.copy(showDeleteConfirmation = true) }
            }

            ChecklistDetailIntent.OnConfirmDeleteChecklist -> deleteChecklist()

            ChecklistDetailIntent.OnDismissDeleteConfirmation -> {
                updateContentState { it.copy(showDeleteConfirmation = false) }
            }

            is ChecklistDetailIntent.OnFillClick -> {
                navigator.navigateToFillDetail(intent.fill.id)
            }

            is ChecklistDetailIntent.OnDeleteFillClick -> {
                deleteFill(intent.fill)
            }

            ChecklistDetailIntent.OnAddFillClick -> {
                updateContentState { it.copy(showAddFillDialog = true, newFillName = "") }
            }

            ChecklistDetailIntent.OnAddFillViaAiClick -> {
                // Navigate to AI analyze screen with checklist context
                navigator.navigateToAnalyzeScreen()
            }

            ChecklistDetailIntent.OnDismissAddFillDialog -> {
                updateContentState { it.copy(showAddFillDialog = false) }
            }

            is ChecklistDetailIntent.OnNewFillNameChanged -> {
                updateContentState { it.copy(newFillName = intent.name) }
            }

            ChecklistDetailIntent.OnConfirmAddFill -> createNewFill()
        }
    }

    private fun createNewFill() {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content) return

        val name = state.newFillName.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            val fillItems = state.checklist.items.map { item ->
                ChecklistFillItem(
                    text = item.text,
                    checked = false,
                    note = null
                )
            }

            val newFill = ChecklistFill(
                checklistId = checklistId,
                name = name,
                items = fillItems,
                createdAt = currentTimeMillis()
            )

            val fillId = repository.addFill(newFill)
            updateContentState { it.copy(showAddFillDialog = false) }

            navigator.navigateToFillDetail(fillId)
        }
    }

    private fun deleteFill(fill: ChecklistFill) {
        viewModelScope.launch {
            repository.deleteFill(fill)
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
