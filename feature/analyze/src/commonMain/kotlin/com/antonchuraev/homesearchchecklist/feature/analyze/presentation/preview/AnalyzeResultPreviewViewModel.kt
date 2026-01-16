package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResultHolder
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyzeResultPreviewViewModel(
    private val appNavigator: AppNavigator,
    private val checklistRepository: ChecklistRepository
) : AppViewModel<AnalyzeResultPreviewScreenState, AnalyzeResultPreviewScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(AnalyzeResultPreviewScreenState())
    override val screenState: StateFlow<AnalyzeResultPreviewScreenState> = _screenState.asStateFlow()

    private var targetChecklistId: Long? = null
    private var originalItemsCheckedState: Map<Int, Boolean> = emptyMap()

    init {
        loadData()
    }

    override fun onIntent(intent: AnalyzeResultPreviewScreenIntent) {
        when (intent) {
            AnalyzeResultPreviewScreenIntent.OnBackClick -> {
                AnalyzeResultHolder.clear()
                appNavigator.onBack()
            }
            is AnalyzeResultPreviewScreenIntent.OnChecklistNameChanged -> updateChecklistName(intent.name)
            is AnalyzeResultPreviewScreenIntent.OnRemoveItem -> removeItem(intent.index)
            is AnalyzeResultPreviewScreenIntent.OnNewItemTextChange -> updateNewItemText(intent.text)
            AnalyzeResultPreviewScreenIntent.OnAddItem -> addItem()
            AnalyzeResultPreviewScreenIntent.OnCreateChecklist -> createChecklist()
            AnalyzeResultPreviewScreenIntent.OnDismissError -> dismissError()
        }
    }

    private fun loadData() {
        val data = AnalyzeResultHolder.get()
        if (data == null) {
            _screenState.update { it.copy(isLoading = false, error = "No data available") }
            return
        }

        targetChecklistId = data.targetChecklistId
        originalItemsCheckedState = data.items.mapIndexed { index, item -> index to item.checked }.toMap()

        _screenState.update {
            it.copy(
                isLoading = false,
                checklistName = data.suggestedName,
                editableItems = data.items.map { item -> item.text },
                summary = data.summary,
                isFillMode = data.isFillMode,
                targetChecklistName = data.targetChecklistName
            )
        }
    }

    private fun updateChecklistName(name: String) {
        _screenState.update { it.copy(checklistName = name) }
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

        if (state.editableItems.isEmpty()) {
            _screenState.update { it.copy(error = "Add at least one item") }
            return
        }

        if (state.checklistName.isBlank()) {
            _screenState.update { it.copy(error = "Enter a checklist name") }
            return
        }

        viewModelScope.launch {
            _screenState.update { it.copy(isCreating = true) }

            try {
                if (state.isFillMode && targetChecklistId != null) {
                    // Create a fill for existing checklist
                    val fillItems = state.editableItems.mapIndexed { index, text ->
                        ChecklistFillItem(
                            text = text,
                            checked = originalItemsCheckedState[index] ?: false,
                            note = null
                        )
                    }

                    val newFill = ChecklistFill(
                        checklistId = targetChecklistId!!,
                        name = state.checklistName,
                        items = fillItems,
                        createdAt = currentTimeMillis()
                    )

                    val fillId = checklistRepository.addFill(newFill)
                    _screenState.update { it.copy(isCreating = false) }
                    AnalyzeResultHolder.clear()
                    appNavigator.navigateToFillDetail(fillId, clearBackStack = true)
                } else {
                    // Create new checklist
                    val checklist = Checklist(
                        name = state.checklistName,
                        items = state.editableItems.map { ChecklistItem(text = it, checked = false) }
                    )

                    val checklistId = checklistRepository.addChecklist(checklist)
                    _screenState.update { it.copy(isCreating = false) }
                    AnalyzeResultHolder.clear()
                    appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
                }
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
