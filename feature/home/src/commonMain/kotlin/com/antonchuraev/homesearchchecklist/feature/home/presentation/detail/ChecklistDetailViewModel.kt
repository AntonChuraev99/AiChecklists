package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.datetime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChecklistDetailViewModel(
    private val checklistId: Long,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val reminderScheduler: ChecklistReminderScheduler,
    private val datastore: AppDatastore
) : AppViewModel<ChecklistDetailState, ChecklistDetailIntent, Nothing>() {

    private val _screenState = MutableStateFlow<ChecklistDetailState>(ChecklistDetailState.Loading)
    override val screenState: StateFlow<ChecklistDetailState> = _screenState.asStateFlow()

    private var loadDataJob: Job? = null

    /** True when user navigated to exact alarm settings; used to detect return. */
    private var wentToExactAlarmSettings = false

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
                userLimits = null,
                separateCompleted = checklist.separateCompleted,
                autoDeleteCompleted = checklist.autoDeleteCompleted
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
            is ChecklistDetailIntent.OnItemCheckedChange -> updateItemChecked(intent.itemId, intent.checked)
            is ChecklistDetailIntent.OnAddNoteClick -> openNoteDialog(intent.itemId)
            is ChecklistDetailIntent.OnAddItem -> addItem(intent.text)
            is ChecklistDetailIntent.OnNoteChanged -> updateContentState { it.copy(editingNote = intent.note) }
            ChecklistDetailIntent.OnSaveNote -> saveNote()
            ChecklistDetailIntent.OnDismissNoteDialog -> updateContentState { it.copy(noteDialogItemId = null, editingNote = "") }
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

            // Item reorder and delete
            is ChecklistDetailIntent.OnFinalizeReorder -> finalizeReorder(intent.orderedItemIds)
            is ChecklistDetailIntent.OnDeleteItemClick -> {
                updateContentState { it.copy(itemPendingDeleteId = intent.itemId, showDeleteItemConfirmation = true) }
            }
            ChecklistDetailIntent.OnConfirmDeleteItem -> confirmDeleteItem()
            ChecklistDetailIntent.OnDismissDeleteItemDialog -> {
                updateContentState { it.copy(itemPendingDeleteId = null, showDeleteItemConfirmation = false) }
            }

            // Overflow menu
            ChecklistDetailIntent.OnOverflowMenuClick -> {
                updateContentState { it.copy(showOverflowSheet = true) }
                analyticsTracker.event("overflow_menu_opened")
            }
            ChecklistDetailIntent.OnDismissOverflowSheet -> updateContentState { it.copy(showOverflowSheet = false) }
            ChecklistDetailIntent.OnToggleSeparateCompleted -> toggleSeparateCompleted()
            ChecklistDetailIntent.OnToggleAutoDeleteCompleted -> toggleAutoDeleteCompleted()
            ChecklistDetailIntent.OnDeleteCompletedItems -> deleteCompletedItems()

            // Notification permission
            is ChecklistDetailIntent.OnNotificationPermissionResult -> handleNotificationPermissionResult()
            ChecklistDetailIntent.OnNotificationPermissionSkip -> handleNotificationPermissionSkip()
            ChecklistDetailIntent.OnDismissNotificationPermissionSheet -> handleNotificationPermissionSkip()

            // Reminders
            ChecklistDetailIntent.OnReminderClick -> handleReminderClick()
            is ChecklistDetailIntent.OnReminderPresetSelected -> {
                val now = Clock.System.now().toEpochMilliseconds()
                if (intent.triggerAtMillis <= now) return
                saveReminder(intent.triggerAtMillis)
                updateContentState { it.copy(showReminderSheet = false) }
            }
            ChecklistDetailIntent.OnCustomDateRequested -> {
                val tz = TimeZone.currentSystemDefault()
                val todayDate = Clock.System.now().toLocalDateTime(tz).date
                val todayUtcMidnight = LocalDateTime(todayDate, LocalTime(0, 0))
                    .toInstant(TimeZone.UTC).toEpochMilliseconds()
                updateContentState {
                    it.copy(
                        showReminderSheet = false,
                        showCustomPicker = true,
                        customPickerDateMillis = null,
                        customPickerMinDateMillis = todayUtcMidnight,
                        customPickerInitialHour = 9,
                        isCustomTimeInPast = false
                    )
                }
            }
            is ChecklistDetailIntent.OnDateSelected -> {
                val tz = TimeZone.currentSystemDefault()
                val nowLocal = Clock.System.now().toLocalDateTime(tz)
                val selectedDate = Instant.fromEpochMilliseconds(intent.dateMillis)
                    .toLocalDateTime(TimeZone.UTC).date
                val isToday = selectedDate == nowLocal.date
                val initialHour = if (isToday) (nowLocal.hour + 1).coerceAtMost(23) else 9
                updateContentState {
                    it.copy(
                        customPickerDateMillis = intent.dateMillis,
                        customPickerInitialHour = initialHour,
                        isCustomTimeInPast = false
                    )
                }
            }
            is ChecklistDetailIntent.OnCustomTimeChanged -> {
                val state = (_screenState.value as? ChecklistDetailState.Content) ?: return
                val dateMillis = state.customPickerDateMillis ?: return
                val tz = TimeZone.currentSystemDefault()
                val nowLocal = Clock.System.now().toLocalDateTime(tz)
                val selectedDate = Instant.fromEpochMilliseconds(dateMillis)
                    .toLocalDateTime(TimeZone.UTC).date
                val isToday = selectedDate == nowLocal.date
                val isInPast = isToday &&
                    LocalTime(intent.hour, intent.minute) <= nowLocal.time
                updateContentState { it.copy(isCustomTimeInPast = isInPast) }
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

            // Exact alarm permission
            ChecklistDetailIntent.OnExactAlarmOpenSettings -> handleExactAlarmOpenSettings()
            ChecklistDetailIntent.OnExactAlarmSkip -> handleExactAlarmSkip()
            is ChecklistDetailIntent.OnExactAlarmDontShowChanged -> {
                updateContentState { it.copy(exactAlarmDontShowAgain = intent.checked) }
            }
            ChecklistDetailIntent.OnDismissExactAlarmSheet -> handleExactAlarmSkip()
            // Analytics-only intents
            is ChecklistDetailIntent.OnCompletedSectionToggle -> {
                val eventName = if (intent.expanded) "completed_section_expanded" else "completed_section_collapsed"
                analyticsTracker.event(eventName, mapOf("completed_count" to intent.completedCount.toString()))
            }
            ChecklistDetailIntent.OnQuickAddOpened -> {
                analyticsTracker.event("quick_add_opened")
            }
            is ChecklistDetailIntent.OnQuickAddCancelled -> {
                analyticsTracker.event("quick_add_cancelled", mapOf("had_text" to intent.hadText.toString()))
            }

            ChecklistDetailIntent.OnReturnedFromSettings -> handleReturnedFromSettings()
            ChecklistDetailIntent.OnSnackbarDismissed -> {
                updateContentState { it.copy(snackbarMessage = null) }
            }
        }
    }

    private fun openNoteDialog(itemId: String) {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content && state.defaultFill != null) {
            val currentNote = state.defaultFill.items.firstOrNull { it.id == itemId }?.note.orEmpty()
            updateContentState { it.copy(noteDialogItemId = itemId, editingNote = currentNote) }
        }
    }

    private fun updateItemChecked(itemId: String, checked: Boolean) {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content || state.defaultFill == null) return

        // Auto-delete: when checking an item and autoDeleteCompleted is on, remove it
        if (checked && state.autoDeleteCompleted) {
            val itemToDelete = state.defaultFill.items.firstOrNull { it.id == itemId } ?: return
            val updatedFillItems = state.defaultFill.items.filter { it.id != itemId }
            val updatedFill = state.defaultFill.copy(items = updatedFillItems)

            val updatedChecklistItems = state.checklist.items.filter { it.text != itemToDelete.text }
            val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)
            updateContentState { it.copy(checklist = updatedChecklist) }

            viewModelScope.launch {
                repository.updateFill(updatedFill)
                repository.updateChecklistTemplate(updatedChecklist)
                analyticsTracker.event("item_auto_deleted", mapOf(
                    "checklist_id" to checklistId.toString()
                ))
            }
            return
        }

        val updatedItems = state.defaultFill.items.map { item ->
            if (item.id == itemId) item.withChecked(checked) else item
        }

        val updatedFill = state.defaultFill.copy(items = updatedItems)

        viewModelScope.launch {
            repository.updateFill(updatedFill)
        }
    }

    private fun saveNote() {
        val state = _screenState.value
        if (state !is ChecklistDetailState.Content || state.defaultFill == null) return
        val itemId = state.noteDialogItemId ?: return

        val updatedItems = state.defaultFill.items.map { item ->
            if (item.id == itemId) {
                item.withNote(state.editingNote.takeIf { it.isNotBlank() })
            } else {
                item
            }
        }

        val updatedFill = state.defaultFill.copy(items = updatedItems)

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            updateContentState { it.copy(noteDialogItemId = null, editingNote = "") }
        }
    }

    private fun toggleSeparateCompleted() {
        val current = (_screenState.value as? ChecklistDetailState.Content)?.separateCompleted ?: false
        val newValue = !current
        updateContentState { it.copy(separateCompleted = newValue) }
        viewModelScope.launch {
            repository.setSeparateCompleted(checklistId, newValue)
        }
        analyticsTracker.event(
            "separate_completed_toggled",
            mapOf("enabled" to newValue.toString())
        )
    }

    private fun toggleAutoDeleteCompleted() {
        val current = (_screenState.value as? ChecklistDetailState.Content)?.autoDeleteCompleted ?: false
        val newValue = !current
        updateContentState { it.copy(autoDeleteCompleted = newValue) }
        viewModelScope.launch {
            repository.setAutoDeleteCompleted(checklistId, newValue)
        }
        analyticsTracker.event(
            "auto_delete_completed_toggled",
            mapOf("enabled" to newValue.toString())
        )
    }

    private fun deleteCompletedItems() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return

        val completedTexts = fill.items.filter { it.checked }.map { it.text }.toSet()
        if (completedTexts.isEmpty()) return

        val updatedFillItems = fill.items.filter { !it.checked }
        val updatedFill = fill.copy(items = updatedFillItems)

        val updatedChecklistItems = state.checklist.items.filter { it.text !in completedTexts }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        updateContentState {
            it.copy(checklist = updatedChecklist, showOverflowSheet = false)
        }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("completed_items_deleted", mapOf(
                "checklist_id" to checklistId.toString(),
                "deleted_count" to completedTexts.size.toString(),
                "remaining_count" to updatedFillItems.size.toString()
            ))
        }
    }

    private fun addItem(text: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val newFillItem = ChecklistFillItem(text = trimmed, checked = false, note = null)
        val updatedFill = fill.copy(items = fill.items + newFillItem)

        val newChecklistItem = ChecklistItem(text = trimmed)
        val updatedChecklist = state.checklist.copy(items = state.checklist.items + newChecklistItem)
        updateContentState { it.copy(checklist = updatedChecklist) }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("item_added_quick", mapOf(
                "checklist_id" to checklistId.toString(),
                "item_count" to updatedFill.items.size.toString()
            ))
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
            analyticsTracker.event("checklist_deleted", mapOf(
                "checklist_id" to state.checklist.id.toString(),
                "item_count" to (state.defaultFill?.items?.size ?: 0).toString(),
                "source" to "overflow_menu"
            ))
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

            if (!reminderScheduler.hasNotificationPermission()) {
                updateContentState { it.copy(showNotificationPermissionSheet = true) }
            } else {
                updateContentState { it.copy(showReminderSheet = true) }
            }
        }
    }

    private fun handleNotificationPermissionResult() {
        updateContentState {
            it.copy(showNotificationPermissionSheet = false, showReminderSheet = true)
        }
    }

    private fun handleNotificationPermissionSkip() {
        updateContentState {
            it.copy(showNotificationPermissionSheet = false, showReminderSheet = true)
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

            maybeShowExactAlarmInstruction()
        }
    }

    private suspend fun maybeShowExactAlarmInstruction() {
        if (reminderScheduler.canScheduleExactAlarms()) return

        val suppressed = datastore.observeBoolean(PREF_EXACT_ALARM_DONT_SHOW, false).first()
        if (suppressed) return

        updateContentState { it.copy(showExactAlarmSheet = true, exactAlarmDontShowAgain = false) }
    }

    private fun handleExactAlarmOpenSettings() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            if (state.exactAlarmDontShowAgain) {
                datastore.saveBoolean(PREF_EXACT_ALARM_DONT_SHOW, true)
            }
            wentToExactAlarmSettings = true
            updateContentState { it.copy(showExactAlarmSheet = false) }
            reminderScheduler.openExactAlarmSettings()
        }
    }

    private fun handleExactAlarmSkip() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            if (state.exactAlarmDontShowAgain) {
                datastore.saveBoolean(PREF_EXACT_ALARM_DONT_SHOW, true)
            }
            updateContentState { it.copy(showExactAlarmSheet = false) }
        }
    }

    fun handleReturnedFromSettings() {
        if (!wentToExactAlarmSettings) return
        wentToExactAlarmSettings = false

        viewModelScope.launch {
            if (reminderScheduler.canScheduleExactAlarms()) {
                reminderScheduler.rescheduleAllActive()
                updateContentState { it.copy(snackbarMessage = SNACKBAR_EXACT_GRANTED) }
            } else {
                updateContentState { it.copy(snackbarMessage = SNACKBAR_EXACT_DENIED) }
            }
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

    private fun finalizeReorder(orderedItemIds: List<String>) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return

        // Build a lookup for fill items by id
        val fillItemsById = fill.items.associateBy { it.id }
        val completedItems = if (state.separateCompleted) fill.items.filter { it.checked } else emptyList()

        // Reorder unchecked items according to the provided order
        val reorderedUnchecked = orderedItemIds.mapNotNull { fillItemsById[it] }
        val newFillItems = reorderedUnchecked + completedItems
        val updatedFill = fill.copy(items = newFillItems)

        // Mirror reorder in checklist template items by matching text to fill order
        val orderedTexts = reorderedUnchecked.map { it.text }
        val templateByText = state.checklist.items.associateBy { it.text }
        val reorderedTemplateUnchecked = orderedTexts.mapNotNull { templateByText[it] }
        val completedTemplateItems = if (state.separateCompleted) {
            val uncheckedTexts = orderedTexts.toSet()
            state.checklist.items.filter { it.text !in uncheckedTexts }
        } else {
            emptyList()
        }

        val updatedChecklist = state.checklist.copy(items = reorderedTemplateUnchecked + completedTemplateItems)
        updateContentState { it.copy(checklist = updatedChecklist) }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
        }
    }

    private fun confirmDeleteItem() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val itemId = state.itemPendingDeleteId ?: return

        val itemToDelete = fill.items.firstOrNull { it.id == itemId } ?: return
        val updatedFillItems = fill.items.filter { it.id != itemId }
        val updatedFill = fill.copy(items = updatedFillItems)

        val updatedChecklistItems = state.checklist.items.filter { it.text != itemToDelete.text }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        updateContentState {
            it.copy(
                checklist = updatedChecklist,
                showDeleteItemConfirmation = false,
                itemPendingDeleteId = null
            )
        }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("item_deleted", mapOf(
                "checklist_id" to checklistId.toString(),
                "item_count" to updatedFillItems.size.toString()
            ))
        }
    }

    private inline fun updateContentState(update: (ChecklistDetailState.Content) -> ChecklistDetailState.Content) {
        _screenState.update { state ->
            if (state is ChecklistDetailState.Content) update(state) else state
        }
    }

    companion object {
        const val PREF_EXACT_ALARM_DONT_SHOW = "exact_alarm_dont_show"
        const val SNACKBAR_EXACT_GRANTED = "exact_alarm_granted"
        const val SNACKBAR_EXACT_DENIED = "exact_alarm_denied"
    }
}
