package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
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
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
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
            viewModelScope.launch {
                _screenState.update { it.copy(isLoading = false, error = getString(Res.string.error_no_data_available)) }
            }
            return
        }

        targetChecklistId = data.targetChecklistId

        // Folders only apply to the new-checklist path (never fill mode). When present we keep
        // the structured items (parentId/type) so the structure survives to creation and the
        // preview can render the tree.
        val hasFolders = data.hasFolders && !data.isFillMode

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
                targetChecklistName = data.targetChecklistName,
                hasFolders = hasFolders,
                structuredItems = if (hasFolders) data.items.toList() else emptyList()
            )
        }
    }

    private fun updateChecklistName(name: String) {
        _screenState.update { it.copy(checklistName = name) }
    }

    private fun removeItem(index: Int) {
        // In folder mode the preview is read-only: editing a flat index would desync the tree
        // (structuredItems). The user restructures after creation.
        if (_screenState.value.hasFolders) return
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
        // Adding a flat row in folder mode would land it outside the tree; disabled for v1.
        if (_screenState.value.hasFolders) return
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

        viewModelScope.launch {
            if (state.editableItems.isEmpty()) {
                _screenState.update { it.copy(error = getString(Res.string.error_add_at_least_one_item)) }
                return@launch
            }

            if (state.checklistName.isBlank()) {
                _screenState.update { it.copy(error = getString(Res.string.analyze_error_empty_name)) }
                return@launch
            }

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
                    analyticsTracker.event(AnalyticsEvents.Checklist.FILL_CREATED, mapOf(
                        "source" to "ai",
                        "item_count" to state.editableItems.size
                    ))
                    _screenState.update { it.copy(isCreating = false) }
                    AnalyzeResultHolder.clear()
                    appNavigator.navigateToFillDetail(fillId, clearBackStack = true)
                } else {
                    // Create new checklist. In folder mode use the structured items verbatim so
                    // parentId/type reach the template and set foldersEnabled; the default fill
                    // (created by addChecklist) gets a linked row for every node — folders too —
                    // so folder reminders/progress resolve. In the flat path build plain items
                    // from the (editable) text list as before.
                    val checklist = if (state.hasFolders) {
                        Checklist(
                            name = state.checklistName,
                            items = state.structuredItems,
                            foldersEnabled = true
                        )
                    } else {
                        Checklist(
                            name = state.checklistName,
                            items = state.editableItems.map { ChecklistItem(text = it, checked = false) }
                        )
                    }

                    val checklistId = checklistRepository.addChecklist(checklist)
                    analyticsTracker.event(AnalyticsEvents.Checklist.CREATED, mapOf(
                        "source" to "ai",
                        "item_count" to checklist.items.size,
                        "has_folders" to state.hasFolders
                    ))
                    _screenState.update { it.copy(isCreating = false) }
                    AnalyzeResultHolder.clear()
                    appNavigator.navigateToChecklistDetail(checklistId, clearBackStack = true)
                }
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(isCreating = false, error = e.message ?: getString(Res.string.error_create_checklist_failed))
                }
            }
        }
    }

    private fun applyToDefaultFill() {
        viewModelScope.launch {
            _screenState.update { it.copy(isCreating = true) }
            try {
                val checklistId = targetChecklistId ?: run {
                    _screenState.update { it.copy(isCreating = false, error = getString(Res.string.checklist_not_found)) }
                    return@launch
                }

                val defaultFill = checklistRepository
                    .getDefaultFillByChecklistId(checklistId)
                    .first()

                if (defaultFill == null) {
                    _screenState.update { it.copy(isCreating = false, error = getString(Res.string.error_fill_not_found)) }
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

                analyticsTracker.event(AnalyticsEvents.Checklist.DEFAULT_FILL_UPDATED)

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
