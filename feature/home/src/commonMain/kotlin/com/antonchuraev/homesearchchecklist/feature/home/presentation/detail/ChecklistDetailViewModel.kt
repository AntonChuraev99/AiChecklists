package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.datetime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChecklistDetailViewModel(
    private val checklistId: Long,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val reminderScheduler: ChecklistReminderScheduler
) : AppViewModel<ChecklistDetailState, ChecklistDetailIntent, Nothing>() {

    private val _screenState = MutableStateFlow<ChecklistDetailState>(ChecklistDetailState.Loading)
    override val screenState: StateFlow<ChecklistDetailState> = _screenState.asStateFlow()

    private var loadDataJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        loadDataJob = viewModelScope.launch {
            val checklist = repository.getChecklistById(checklistId)
            if (checklist == null) {
                _screenState.value = ChecklistDetailState.NotFound
                return@launch
            }

            combine(
                repository.getDefaultFillByChecklistId(checklistId),
                repository.getAdditionalFillsByChecklistId(checklistId)
            ) { defaultFill, additionalFills ->
                defaultFill to additionalFills
            }.collect { (defaultFill, additionalFills) ->
                if (defaultFill == null) {
                    _screenState.value = ChecklistDetailState.NotFound
                    return@collect
                }

                updateOrCreateContentState(checklist, defaultFill, additionalFills.size)
            }
        }

        viewModelScope.launch {
            getUserLimitsUseCase().collect { userLimits ->
                updateContentState { it.copy(userLimits = userLimits) }
            }
        }
    }

    private fun updateOrCreateContentState(
        checklist: Checklist,
        defaultFill: ChecklistFill?,
        additionalFillsCount: Int
    ) {
        val currentState = _screenState.value
        _screenState.value = if (currentState is ChecklistDetailState.Content) {
            currentState.copy(
                defaultFill = defaultFill,
                additionalFillsCount = additionalFillsCount
            )
        } else {
            ChecklistDetailState.Content(
                checklist = checklist,
                defaultFill = defaultFill,
                additionalFillsCount = additionalFillsCount,
                userLimits = null
            )
        }
    }

    override fun onIntent(intent: ChecklistDetailIntent) {
        when (intent) {
            ChecklistDetailIntent.OnBackClick -> navigator.onBack()
            ChecklistDetailIntent.OnEditChecklistClick -> navigator.navigateToEditChecklist(checklistId)
            ChecklistDetailIntent.OnShareClick -> navigator.navigateToShareChecklist(checklistId)
            ChecklistDetailIntent.OnDeleteChecklistClick -> updateContentState { it.copy(showDeleteConfirmation = true) }
            ChecklistDetailIntent.OnConfirmDeleteChecklist -> deleteChecklist()
            ChecklistDetailIntent.OnDismissDeleteConfirmation -> updateContentState { it.copy(showDeleteConfirmation = false) }
            is ChecklistDetailIntent.OnItemCheckedChange -> updateItemChecked(intent.index, intent.checked)
            is ChecklistDetailIntent.OnAddNoteClick -> openNoteDialog(intent.index)
            is ChecklistDetailIntent.OnNoteChanged -> updateContentState { it.copy(editingNote = intent.note) }
            ChecklistDetailIntent.OnSaveNote -> saveNote()
            ChecklistDetailIntent.OnDismissNoteDialog -> updateContentState { it.copy(noteDialogItemIndex = null, editingNote = "") }
            ChecklistDetailIntent.OnViewAllFillsClick -> navigator.navigateToFillsList(checklistId)
            ChecklistDetailIntent.OnAddFillClick -> handleAddFillClick()
            ChecklistDetailIntent.OnAddFillViaAiClick -> handleAddFillViaAiClick()
            ChecklistDetailIntent.OnFillTargetSheetDismiss -> updateContentState { it.copy(showFillTargetSheet = false) }
            ChecklistDetailIntent.OnFillMainChecklistSelected -> handleFillMainChecklistSelected()
            ChecklistDetailIntent.OnCreateNewFillSelected -> handleCreateNewFillSelected()
            ChecklistDetailIntent.OnDismissAddFillDialog -> updateContentState { it.copy(showAddFillDialog = false) }
            is ChecklistDetailIntent.OnNewFillNameChanged -> updateContentState { it.copy(newFillName = intent.name, fillNameError = null) }
            ChecklistDetailIntent.OnConfirmAddFill -> createNewFill()
            ChecklistDetailIntent.OnDismissFillLimitDialog -> updateContentState { it.copy(showFillLimitDialog = false) }
            ChecklistDetailIntent.OnUpgradeToPremiumClick -> {
                updateContentState { it.copy(showFillLimitDialog = false) }
                navigator.navigateToPaywall(source = "detail_fill_limit")
            }

            // Reminders
            ChecklistDetailIntent.OnReminderClick -> handleReminderClick()
            is ChecklistDetailIntent.OnReminderPresetSelected -> {
                val now = Clock.System.now().toEpochMilliseconds()
                if (intent.triggerAtMillis <= now) return
                saveReminder(intent.triggerAtMillis)
                updateContentState { it.copy(showReminderSheet = false) }
            }
            ChecklistDetailIntent.OnCustomDateRequested -> {
                updateContentState {
                    it.copy(showReminderSheet = false, showCustomPicker = true, customPickerDateMillis = null)
                }
            }
            is ChecklistDetailIntent.OnDateSelected -> {
                updateContentState { it.copy(customPickerDateMillis = intent.dateMillis) }
            }
            is ChecklistDetailIntent.OnTimeSelected -> {
                val dateMillis = (_screenState.value as? ChecklistDetailState.Content)?.customPickerDateMillis ?: return
                val triggerAt = combinePickerResults(dateMillis, intent.hour, intent.minute)
                val now = Clock.System.now().toEpochMilliseconds()
                if (triggerAt <= now) return
                saveReminder(triggerAt)
                updateContentState { it.copy(showCustomPicker = false, customPickerDateMillis = null) }
            }
            ChecklistDetailIntent.OnRemoveReminder -> {
                removeReminder()
                updateContentState { it.copy(showReminderSheet = false) }
            }
            ChecklistDetailIntent.OnDismissReminderUI -> {
                updateContentState {
                    it.copy(showReminderSheet = false, showCustomPicker = false, customPickerDateMillis = null)
                }
            }
        }
    }

    private fun openNoteDialog(itemIndex: Int) {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content && state.defaultFill != null) {
            val currentNote = state.defaultFill.items.getOrNull(itemIndex)?.note.orEmpty()
            updateContentState { it.copy(noteDialogItemIndex = itemIndex, editingNote = currentNote) }
        }
    }

    private fun updateItemChecked(index: Int, checked: Boolean) {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content || state.defaultFill == null) return

        val updatedItems = state.defaultFill.items.mapIndexed { i, item ->
            if (i == index) item.withChecked(checked) else item
        }

        val updatedFill = state.defaultFill.copy(items = updatedItems)

        viewModelScope.launch {
            repository.updateFill(updatedFill)
        }
    }

    private fun saveNote() {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content || state.defaultFill == null) return
        val itemIndex = state.noteDialogItemIndex ?: return

        val updatedItems = state.defaultFill.items.mapIndexed { i, item ->
            if (i == itemIndex) {
                item.withNote(state.editingNote.takeIf { it.isNotBlank() })
            } else {
                item
            }
        }

        val updatedFill = state.defaultFill.copy(items = updatedItems)

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            updateContentState { it.copy(noteDialogItemIndex = null, editingNote = "") }
        }
    }

    private fun handleAddFillClick() {
        withFillLimitCheck {
            updateContentState { it.copy(showAddFillDialog = true, newFillName = "") }
        }
    }

    private fun handleAddFillViaAiClick() {
        updateContentState { it.copy(showFillTargetSheet = true) }
    }

    private fun handleFillMainChecklistSelected() {
        updateContentState { it.copy(showFillTargetSheet = false) }
        navigator.navigateToAnalyzeScreen(checklistId, fillDefault = true)
    }

    private fun handleCreateNewFillSelected() {
        updateContentState { it.copy(showFillTargetSheet = false) }
        withFillLimitCheck {
            navigator.navigateToAnalyzeScreen(checklistId, fillDefault = false)
        }
    }

    private inline fun withFillLimitCheck(onAllowed: () -> Unit) {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content) return

        val limits = state.userLimits
        val totalFills = state.additionalFillsCount + 1
        if (limits != null && !limits.canCreateFill(totalFills)) {
            updateContentState { it.copy(showFillLimitDialog = true) }
        } else {
            onAllowed()
        }
    }

    private fun createNewFill() {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content) return
        if (state.isCreatingFill) return

        val name = state.newFillName.trim()
        if (name.isEmpty()) {
            updateContentState { it.copy(fillNameError = "Введите название") }
            return
        }

        updateContentState { it.copy(isCreatingFill = true, fillNameError = null) }

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
                createdAt = currentTimeMillis(),
                isDefault = false
            )

            val fillId = repository.addFill(newFill)
            updateContentState { it.copy(showAddFillDialog = false, isCreatingFill = false) }

            navigator.navigateToFillDetail(fillId)
        }
    }

    private fun deleteChecklist() {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content) return

        updateContentState { it.copy(showDeleteConfirmation = false) }
        loadDataJob?.cancel()

        viewModelScope.launch {
            reminderScheduler.cancel(state.checklist.id)
            repository.deleteChecklist(state.checklist)
            analyticsTracker.event("checklist_deleted")
            navigator.onBack()
        }
    }

    private fun handleReminderClick() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val isPremium = state.userLimits?.isPremium ?: false
            val currentChecklistHasReminder = state.checklist.reminderAt != null

            if (!isPremium && !currentChecklistHasReminder) {
                val activeCount = repository.countActiveReminders()
                if (activeCount >= 1) {
                    navigator.navigateToPaywall(source = "detail_reminder_limit")
                    return@launch
                }
            }
            updateContentState { it.copy(showReminderSheet = true) }
        }
    }

    private fun saveReminder(triggerAtMillis: Long) {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            repository.setReminder(state.checklist.id, triggerAtMillis)
            reminderScheduler.schedule(state.checklist.id, triggerAtMillis)
            updateContentState {
                it.copy(checklist = it.checklist.copy(reminderAt = triggerAtMillis))
            }
            analyticsTracker.event("reminder_set", mapOf("checklist_id" to state.checklist.id.toString()))
        }
    }

    private fun removeReminder() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            repository.setReminder(state.checklist.id, null)
            reminderScheduler.cancel(state.checklist.id)
            updateContentState {
                it.copy(checklist = it.checklist.copy(reminderAt = null))
            }
            analyticsTracker.event("reminder_cancelled", mapOf("checklist_id" to state.checklist.id.toString()))
        }
    }

    private inline fun updateContentState(update: (ChecklistDetailState.Content) -> ChecklistDetailState.Content) {
        _screenState.update { state ->
            if (state is ChecklistDetailState.Content) update(state) else state
        }
    }
}
