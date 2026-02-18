package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyzeResultPreviewViewModel(
    private val appNavigator: AppNavigator,
    private val checklistRepository: ChecklistRepository,
    private val analyticsTracker: AnalyticsTracker
) : AppViewModel<AnalyzeResultPreviewScreenState, AnalyzeResultPreviewScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(AnalyzeResultPreviewScreenState())
    override val screenState: StateFlow<AnalyzeResultPreviewScreenState> = _screenState.asStateFlow()

    private var targetChecklistId: Long? = null

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

        _screenState.update {
            it.copy(
                isLoading = false,
                checklistName = data.suggestedName,
                editableItems = if (data.isFillMode && data.fillDefaultItems != null) {
                    data.fillDefaultItems.map { item -> item.text }
                } else {
                    data.items.map { item -> item.text }
                },
                summary = data.summary,
                isFillMode = data.isFillMode,
                fillDefault = data.fillDefault,
                fillDefaultItems = data.fillDefaultItems ?: emptyList(),
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
            val newFillItems = state.fillDefaultItems.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
            state.copy(editableItems = newItems, fillDefaultItems = newFillItems)
        }
    }

    private fun updateNewItemText(text: String) {
        _screenState.update { it.copy(newItemText = text) }
    }

    private fun addItem() {
        val text = _screenState.value.newItemText.trim()
        if (text.isNotEmpty()) {
            _screenState.update { state ->
                val newFillItem = ChecklistFillItem(text = text, checked = false, note = null)
                // New items appear at the TOP of the list
                state.copy(
                    editableItems = listOf(text) + state.editableItems,
                    fillDefaultItems = if (state.isFillMode) {
                        listOf(newFillItem) + state.fillDefaultItems
                    } else {
                        state.fillDefaultItems
                    },
                    newItemText = ""
                )
            }
        }
    }

    private fun createChecklist() {
        val state = _screenState.value

        if (state.fillDefault) {
            applyToDefaultFill()
            return
        }

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
                    // Create a fill for existing checklist using fillDefaultItems
                    // which keep checked states and notes in sync with editableItems
                    val fillItems = state.fillDefaultItems.ifEmpty {
                        state.editableItems.map { text ->
                            ChecklistFillItem(text = text, checked = false, note = null)
                        }
                    }

                    val newFill = ChecklistFill(
                        checklistId = targetChecklistId!!,
                        name = state.checklistName,
                        items = fillItems,
                        createdAt = currentTimeMillis()
                    )

                    val fillId = checklistRepository.addFill(newFill)
                    analyticsTracker.event("fill_created", mapOf(
                        "source" to "ai",
                        "item_count" to state.editableItems.size
                    ))
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
                    analyticsTracker.event("checklist_created", mapOf(
                        "source" to "ai",
                        "item_count" to state.editableItems.size
                    ))
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

    private fun applyToDefaultFill() {
        viewModelScope.launch {
            _screenState.update { it.copy(isCreating = true) }
            try {
                val checklistId = targetChecklistId ?: run {
                    _screenState.update { it.copy(isCreating = false, error = "Checklist not found") }
                    return@launch
                }

                val defaultFill = checklistRepository
                    .getDefaultFillByChecklistId(checklistId)
                    .first()

                if (defaultFill == null) {
                    _screenState.update { it.copy(isCreating = false, error = "Default fill not found") }
                    return@launch
                }

                val fillDefaultItems = _screenState.value.fillDefaultItems
                val updatedItems = defaultFill.items.mapIndexed { index, existingItem ->
                    val aiResult = fillDefaultItems.getOrNull(index)
                    if (aiResult != null) {
                        existingItem
                            .withChecked(aiResult.checked)
                            .let { item ->
                                aiResult.note?.let { note -> item.withNote(note) } ?: item
                            }
                    } else {
                        existingItem
                    }
                }

                val updatedFill = defaultFill.copy(items = updatedItems)
                checklistRepository.updateFill(updatedFill)

                analyticsTracker.event("default_fill_updated")

                AnalyzeResultHolder.clear()
                appNavigator.navigateToChecklistDetail(checklistId)
            } catch (e: Exception) {
                _screenState.update { it.copy(isCreating = false, error = e.message) }
            }
        }
    }

    private fun dismissError() {
        _screenState.update { it.copy(error = null) }
    }
}
