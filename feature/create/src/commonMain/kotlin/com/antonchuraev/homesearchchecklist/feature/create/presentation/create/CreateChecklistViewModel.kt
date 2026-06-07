package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateChecklistViewModel(
    private val editChecklistId: Long?,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val analyticsTracker: AnalyticsTracker,
    private val getUserLimitsUseCase: GetUserLimitsUseCase
) : AppViewModel<CreateChecklistState, CreateChecklistIntent, Nothing>() {

    private val _screenState = MutableStateFlow(CreateChecklistState(
        isEditMode = editChecklistId != null,
        editChecklistId = editChecklistId
    ))
    override val screenState: StateFlow<CreateChecklistState> = _screenState.asStateFlow()

    init {
        if (editChecklistId != null) {
            loadChecklist(editChecklistId)
        } else {
            // Only observe limits in create mode — edit mode is never gated
            observeUserLimits()
        }
    }

    private fun observeUserLimits() {
        viewModelScope.launch {
            getUserLimitsUseCase().collect { limits ->
                _screenState.update { it.copy(canCreateChecklist = limits.canCreateChecklist) }
            }
        }
    }

    private fun loadChecklist(checklistId: Long) {
        viewModelScope.launch {
            val checklist = checklistRepository.getChecklistById(checklistId)
            if (checklist != null) {
                _screenState.update {
                    it.copy(
                        name = checklist.name,
                        items = checklist.items
                    )
                }
            }
        }
    }

    override fun onIntent(intent: CreateChecklistIntent) {
        when (intent) {
            CreateChecklistIntent.OnBackClick -> appNavigator.onBack()
            CreateChecklistIntent.OnSaveClick -> onSaveClick()
            CreateChecklistIntent.OnChooseTemplateClick -> appNavigator.navigateToTemplatesScreen()
            is CreateChecklistIntent.OnNameChange -> _screenState.update {
                it.copy(name = intent.name, nameError = null)
            }
            is CreateChecklistIntent.OnNewItemTextChange -> _screenState.update {
                it.copy(newItemText = intent.text)
            }
            CreateChecklistIntent.OnAddItemFromInput -> addItemFromInput()
            is CreateChecklistIntent.OnDeleteItem -> _screenState.update {
                it.copy(items = it.items - intent.item)
            }
            is CreateChecklistIntent.OnStartItemEdit -> startEdit(intent.itemId)
            is CreateChecklistIntent.OnItemEditTextChange -> _screenState.update {
                it.copy(editingItemText = intent.text)
            }
            CreateChecklistIntent.OnConfirmItemEdit -> commitPendingEdit()
            CreateChecklistIntent.OnCancelItemEdit -> _screenState.update {
                it.copy(editingItemId = null, editingItemText = "")
            }
        }
    }

    private fun startEdit(itemId: String) {
        // Called before starting a new edit to avoid losing in-flight text
        commitPendingEdit()
        val text = _screenState.value.items.find { it.id == itemId }?.text.orEmpty()
        _screenState.update { it.copy(editingItemId = itemId, editingItemText = text) }
    }

    private fun commitPendingEdit() {
        val state = _screenState.value
        val id = state.editingItemId ?: return
        val trimmed = state.editingItemText.trim()
        if (trimmed.isNotBlank()) {
            _screenState.update {
                it.copy(
                    items = it.items.map { item ->
                        if (item.id == id) item.withText(trimmed) else item
                    },
                    editingItemId = null,
                    editingItemText = ""
                )
            }
        } else {
            _screenState.update { it.copy(editingItemId = null, editingItemText = "") }
        }
    }

    private fun addItemFromInput() {
        val text = _screenState.value.newItemText.trim()
        if (text.isNotBlank()) {
            _screenState.update {
                // New items appear at the TOP of the list
                it.copy(
                    items = listOf(ChecklistItem(text, false)) + it.items,
                    newItemText = ""
                )
            }
        }
    }

    private fun onSaveClick() {
        val currentState = _screenState.value

        // Gate: redirect free users at the checklist limit to paywall (create mode only)
        if (!currentState.isEditMode && !currentState.canCreateChecklist) {
            appNavigator.navigateToPaywall(source = "checklist_limit")
            return
        }

        commitPendingEdit()

        // Auto-add unsaved text from input field before saving
        val unsavedText = _screenState.value.newItemText.trim()
        if (unsavedText.isNotBlank()) {
            addItemFromInput()
        }

        val latestState = _screenState.value

        if (latestState.name.isBlank()) {
            viewModelScope.launch {
                _screenState.update { it.copy(nameError = getString(Res.string.create_error_name_required)) }
            }
            return
        }

        viewModelScope.launch {
            if (latestState.isEditMode && latestState.editChecklistId != null) {
                checklistRepository.updateChecklist(
                    Checklist(
                        id = latestState.editChecklistId,
                        name = latestState.name.trim(),
                        items = latestState.items
                    )
                )
                appNavigator.onBack()
            } else {
                checklistRepository.addChecklist(
                    Checklist(name = latestState.name.trim(), items = latestState.items)
                )
                analyticsTracker.event("checklist_created", mapOf(
                    "source" to "manual",
                    "item_count" to latestState.items.size
                ))
                appNavigator.navigateToMainScreen(clearBackStack = true)
            }
        }
    }
}
