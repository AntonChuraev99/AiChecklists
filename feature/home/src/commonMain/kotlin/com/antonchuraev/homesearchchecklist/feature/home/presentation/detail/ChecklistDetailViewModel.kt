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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.combinePickerResults
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.resolvePresetName
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits

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

    // userLimits is held in its own flow because the Loading → Content transition
    // (driven by checklist load) races with the GetUserLimits flow's first emission.
    // updateContentState{} is a no-op while state is still Loading, so writing
    // userLimits straight into screenState would lose the first emission and leave
    // every subsequent paywall gate seeing isPremium=false. Keeping it independent
    // ensures awaitUserLimits() always sees the latest value regardless of when
    // Content state was created.
    private val _userLimits = MutableStateFlow<UserLimits?>(null)

    private var loadDataJob: Job? = null

    /** True when user navigated to exact alarm settings; used to detect return. */
    private var wentToExactAlarmSettings = false

    private var pendingUndoJob: Job? = null

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
                _userLimits.value = userLimits
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
            // Pull the latest userLimits from its independent flow — it may have
            // already emitted while state was still Loading.
            ChecklistDetailState.Content(
                checklist = checklist,
                defaultFill = defaultFill,
                additionalFillsCount = additionalFillsCount,
                userLimits = _userLimits.value,
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
            is ChecklistDetailIntent.OnSwipeDeleteItem -> swipeDeleteItem(intent.itemId)
            ChecklistDetailIntent.OnUndoDeleteItem -> undoDeleteItem()

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
                    it.copy(
                        showReminderSheet = false,
                        showCustomPicker = false,
                        customPickerDateMillis = null,
                        pendingRepeatConfig = null,
                        showEndConditionPicker = false,
                        reminderSheetLocked = false,
                    )
                }
            }

            // Reminder sheet tab switching
            is ChecklistDetailIntent.OnReminderTabSelected -> handleReminderTabSelected(intent.tab)

            // Repeat schedule
            is ChecklistDetailIntent.OnRepeatTypeSelected -> handleRepeatTypeSelected(intent.type)
            is ChecklistDetailIntent.OnSmartPresetSelected -> updatePendingRepeatConfig { intent.config }
            is ChecklistDetailIntent.OnRepeatIntervalChanged -> updatePendingRepeatConfig { it.copy(interval = intent.interval.coerceIn(1, 99), isCustom = true) }
            is ChecklistDetailIntent.OnWeekDayToggled -> toggleWeekDay(intent.dayNumber)
            is ChecklistDetailIntent.OnResetChecksToggled -> updatePendingRepeatConfig { it.copy(resetChecks = intent.enabled) }
            is ChecklistDetailIntent.OnRepeatTimeChanged -> updatePendingRepeatConfig { it.copy(timeHour = intent.hour, timeMinute = intent.minute) }
            ChecklistDetailIntent.OnSaveRepeatSchedule -> saveRepeatSchedule()
            ChecklistDetailIntent.OnRemoveRepeatSchedule -> removeRepeatSchedule()

            // End condition
            ChecklistDetailIntent.OnEndConditionClick -> updateContentState { it.copy(showEndConditionPicker = true) }
            is ChecklistDetailIntent.OnEndConditionSelected -> {
                updatePendingRepeatConfig { it.copy(endCondition = intent.condition) }
                updateContentState { it.copy(showEndConditionPicker = false) }
            }
            ChecklistDetailIntent.OnDismissEndConditionPicker -> updateContentState { it.copy(showEndConditionPicker = false) }

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

            // Weekly mode
            is ChecklistDetailIntent.OnAddItemToDay -> addItemToDay(intent.weekday, intent.text)
            is ChecklistDetailIntent.OnItemLongPressForMove -> {
                updateContentState { it.copy(moveToDayItemId = intent.itemId) }
            }
            is ChecklistDetailIntent.OnMoveItemToDay -> moveItemToDay(intent.itemId, intent.targetWeekday)
            ChecklistDetailIntent.OnDismissMoveToDaySheet -> {
                updateContentState { it.copy(moveToDayItemId = null) }
            }

            // Item details sheet
            is ChecklistDetailIntent.OnItemTapForDetails -> updateContentState { it.copy(itemDetailsSheetFor = intent.itemId) }
            ChecklistDetailIntent.OnDismissItemDetailsSheet -> updateContentState { it.copy(itemDetailsSheetFor = null) }
            is ChecklistDetailIntent.OnDeleteItemFromSheet -> deleteItemFromSheet(intent.itemId)

            // Reminder paywall upgrade from locked banner
            ChecklistDetailIntent.OnReminderUpgradeClick -> {
                updateContentState { it.copy(showReminderSheet = false, reminderSheetLocked = false) }
                navigator.navigateToPaywall(source = "detail_reminder_limit")
            }
            ChecklistDetailIntent.OnItemReminderUpgradeClick -> {
                updateContentState { it.copy(itemReminderSheetFor = null, itemReminderSheetLocked = false) }
                navigator.navigateToPaywall(source = "detail_item_reminder_limit")
            }

            // Per-item reminders
            is ChecklistDetailIntent.OnItemReminderClick -> handleItemReminderClick(intent.itemId)
            is ChecklistDetailIntent.OnSaveItemReminder -> saveItemReminder(
                intent.itemId, intent.reminderAt, intent.repeatRule, intent.repeatTimeOfDayMinutes
            )
            is ChecklistDetailIntent.OnRemoveItemReminder -> removeItemReminder(intent.itemId)
            ChecklistDetailIntent.OnDismissItemReminderSheet -> {
                updateContentState {
                    it.copy(
                        itemReminderSheetFor = null,
                        itemReminderSheetLocked = false,
                        pendingRepeatConfig = null,
                        repeatRuleSummary = null,
                        showEndConditionPicker = false
                    )
                }
            }
            is ChecklistDetailIntent.OnItemReminderTabSelected -> {
                updateContentState { it.copy(activeItemReminderTab = intent.tab) }
                if (intent.tab == ReminderTab.REPEAT) {
                    val itemId = (_screenState.value as? ChecklistDetailState.Content)?.itemReminderSheetFor
                    if (itemId != null) initItemRepeatTabIfNeeded(itemId)
                }
            }
        }
    }

    private fun openNoteDialog(itemId: String) {
        val state = _screenState.value
        if (state is ChecklistDetailState.Content && state.defaultFill != null) {
            val currentNote = state.defaultFill.items.firstOrNull { it.id == itemId }?.note.orEmpty()
            updateContentState { it.copy(itemDetailsSheetFor = null, noteDialogItemId = itemId, editingNote = currentNote) }
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
                if (itemToDelete.hasActiveReminder) {
                    reminderScheduler.cancelItemReminder(checklistId, state.defaultFill.id, itemId)
                    reminderScheduler.cancelItemRepeat(checklistId, state.defaultFill.id, itemId)
                }
                repository.updateFill(updatedFill)
                repository.updateChecklistTemplate(updatedChecklist)
                analyticsTracker.event("item_auto_deleted", mapOf(
                    "checklist_id" to checklistId.toString()
                ))
            }
            return
        }

        val targetItem = state.defaultFill.items.firstOrNull { it.id == itemId }

        val updatedItems = state.defaultFill.items.map { item ->
            if (item.id == itemId) {
                val base = item.withChecked(checked)
                // Cancel item-level alarm when checking an item that has an active reminder
                if (checked && item.hasActiveReminder) base.withReminderCleared() else base
            } else {
                item
            }
        }

        val updatedFill = state.defaultFill.copy(items = updatedItems)

        // Cancel the scheduled alarms when checking an item with active reminder
        if (checked && targetItem != null && targetItem.hasActiveReminder) {
            val fillId = state.defaultFill.id
            viewModelScope.launch {
                reminderScheduler.cancelItemReminder(checklistId, fillId, itemId)
                reminderScheduler.cancelItemRepeat(checklistId, fillId, itemId)
            }
        }

        val eventName = if (checked) "item_checked" else "item_unchecked"
        val totalItems = updatedItems.size
        val checkedCount = updatedItems.count { it.checked }
        analyticsTracker.event(eventName, mapOf(
            "checklist_id" to checklistId.toString(),
            "progress" to if (totalItems > 0) "${checkedCount * 100 / totalItems}" else "0"
        ))

        if (checked && totalItems > 0 && checkedCount == totalItems) {
            analyticsTracker.event("fill_completed", mapOf(
                "checklist_id" to checklistId.toString(),
                "item_count" to totalItems.toString()
            ))
        }

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
        updateContentState {
            it.copy(
                separateCompleted = newValue,
                autoDeleteCompleted = if (newValue) false else it.autoDeleteCompleted,
            )
        }
        viewModelScope.launch {
            repository.setSeparateCompleted(checklistId, newValue)
            if (newValue) repository.setAutoDeleteCompleted(checklistId, false)
        }
        analyticsTracker.event(
            "separate_completed_toggled",
            mapOf("enabled" to newValue.toString())
        )
    }

    private fun toggleAutoDeleteCompleted() {
        val current = (_screenState.value as? ChecklistDetailState.Content)?.autoDeleteCompleted ?: false
        val newValue = !current
        updateContentState {
            it.copy(
                autoDeleteCompleted = newValue,
                separateCompleted = if (newValue) false else it.separateCompleted,
            )
        }
        viewModelScope.launch {
            repository.setAutoDeleteCompleted(checklistId, newValue)
            if (newValue) repository.setSeparateCompleted(checklistId, false)
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

    private fun addItemToDay(weekday: Int, text: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val newFillItem = ChecklistFillItem(text = trimmed, checked = false, note = null, weekday = weekday)
        val updatedFill = fill.copy(items = fill.items + newFillItem)

        val newChecklistItem = ChecklistItem(text = trimmed, weekday = weekday)
        val updatedChecklist = state.checklist.copy(items = state.checklist.items + newChecklistItem)
        updateContentState { it.copy(checklist = updatedChecklist) }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("weekly_item_added", mapOf(
                "checklist_id" to checklistId.toString(),
                "weekday" to weekday.toString()
            ))
        }
    }

    private fun moveItemToDay(itemId: String, targetWeekday: Int) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return

        val updatedFillItems = fill.items.map { item ->
            if (item.id == itemId) item.withWeekday(targetWeekday) else item
        }
        val updatedFill = fill.copy(items = updatedFillItems)

        val movedFillItem = fill.items.firstOrNull { it.id == itemId }
        val updatedChecklistItems = if (movedFillItem != null) {
            state.checklist.items.map { templateItem ->
                if (templateItem.text == movedFillItem.text && templateItem.weekday == movedFillItem.weekday) {
                    templateItem.withWeekday(targetWeekday)
                } else {
                    templateItem
                }
            }
        } else {
            state.checklist.items
        }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        updateContentState { it.copy(checklist = updatedChecklist, moveToDayItemId = null) }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("weekly_item_moved", mapOf(
                "checklist_id" to checklistId.toString(),
                "target_weekday" to targetWeekday.toString()
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
            reminderScheduler.cancelReminder(state.checklist.id)
            reminderScheduler.cancelRepeat(state.checklist.id)
            repository.deleteChecklist(state.checklist)
            analyticsTracker.event("checklist_deleted", mapOf(
                "checklist_id" to state.checklist.id.toString(),
                "item_count" to (state.defaultFill?.items?.size ?: 0).toString(),
                "source" to "overflow_menu"
            ))
            navigator.onBack()
        }
    }

    /**
     * Awaits the first non-null `userLimits` emission from the dedicated [_userLimits]
     * flow. Use this in handlers that gate on premium — never
     * `state.userLimits?.isPremium ?: false`, which silently flips to "not premium"
     * during the Loading→Content transition and pushes the user into a paywall they
     * shouldn't see. Returns null only on a 2-second timeout (DataStore / RC stall).
     */
    private suspend fun awaitUserLimits(): UserLimits? {
        return _userLimits.value ?: withTimeoutOrNull(2_000L) {
            _userLimits.filterNotNull().first()
        }
    }

    private fun handleReminderClick() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val isPremium = awaitUserLimits()?.isPremium ?: false
            val currentChecklistHasReminder = state.checklist.reminderAt != null

            // Determine default tab: if repeat is active and no one-shot reminder, open on REPEAT tab
            val defaultTab = if (state.checklist.repeatNextAt != null && state.checklist.reminderAt == null) {
                ReminderTab.REPEAT
            } else {
                ReminderTab.ONCE
            }

            val isAtLimit = !isPremium && !currentChecklistHasReminder &&
                    repository.countActiveReminders() >= 1

            if (isAtLimit) {
                // Show locked banner inside the sheet — skip notification permission check
                // since the user cannot create a reminder until they upgrade.
                updateContentState {
                    it.copy(
                        showReminderSheet = true,
                        activeReminderTab = defaultTab,
                        reminderSheetLocked = true,
                    )
                }
                return@launch
            }

            if (!reminderScheduler.hasNotificationPermission()) {
                updateContentState {
                    it.copy(showNotificationPermissionSheet = true, activeReminderTab = defaultTab, reminderSheetLocked = false)
                }
            } else {
                updateContentState {
                    it.copy(showReminderSheet = true, activeReminderTab = defaultTab, reminderSheetLocked = false)
                }
                if (defaultTab == ReminderTab.REPEAT) {
                    initRepeatTabIfNeeded()
                }
            }
        }
    }

    private fun handleReminderTabSelected(tab: ReminderTab) {
        if (tab == ReminderTab.REPEAT) {
            initRepeatTabIfNeeded()
        } else {
            updateContentState { it.copy(activeReminderTab = ReminderTab.ONCE) }
        }
    }

    private fun initRepeatTabIfNeeded() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch

            // Already has pending config — just switch tab
            if (state.pendingRepeatConfig != null) {
                updateContentState { it.copy(activeReminderTab = ReminderTab.REPEAT) }
                return@launch
            }

            val existingRule = state.checklist.repeatRule

            if (existingRule != null) {
                // Editing an existing repeat rule — no limit check needed
                val existingTimeMinutes = state.checklist.repeatTimeOfDayMinutes ?: (9 * 60)
                val config = PendingRepeatConfig(
                    type = existingRule.type,
                    interval = existingRule.interval,
                    weekDays = existingRule.weekDays ?: emptySet(),
                    endCondition = existingRule.endCondition,
                    resetChecks = existingRule.resetChecks,
                    isCustom = existingRule.interval > 1 || !existingRule.weekDays.isNullOrEmpty(),
                    timeHour = existingTimeMinutes / 60,
                    timeMinute = existingTimeMinutes % 60
                )
                updateContentState {
                    it.copy(activeReminderTab = ReminderTab.REPEAT, pendingRepeatConfig = config)
                }
                return@launch
            }

            // New repeat schedule — check free user limit
            val isPremium = awaitUserLimits()?.isPremium ?: false
            if (!isPremium) {
                val activeCount = repository.countActiveRepeatSchedules()
                if (activeCount >= MAX_FREE_REPEAT_SCHEDULES) {
                    analyticsTracker.event("recurring_limit_hit")
                    navigator.navigateToPaywall(source = "detail_recurring_limit")
                } else {
                    updateContentState {
                        it.copy(activeReminderTab = ReminderTab.REPEAT, pendingRepeatConfig = PendingRepeatConfig())
                    }
                }
            } else {
                updateContentState {
                    it.copy(activeReminderTab = ReminderTab.REPEAT, pendingRepeatConfig = PendingRepeatConfig())
                }
            }
        }
    }

    private fun handleNotificationPermissionResult() {
        updateContentState {
            it.copy(showNotificationPermissionSheet = false, showReminderSheet = true)
        }
        if ((_screenState.value as? ChecklistDetailState.Content)?.activeReminderTab == ReminderTab.REPEAT) {
            initRepeatTabIfNeeded()
        }
    }

    private fun handleNotificationPermissionSkip() {
        updateContentState {
            it.copy(showNotificationPermissionSheet = false, showReminderSheet = true)
        }
        if ((_screenState.value as? ChecklistDetailState.Content)?.activeReminderTab == ReminderTab.REPEAT) {
            initRepeatTabIfNeeded()
        }
    }

    private fun saveReminder(triggerAtMillis: Long) {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch

            repository.setReminder(state.checklist.id, triggerAtMillis)
            reminderScheduler.scheduleReminder(state.checklist.id, triggerAtMillis)

            updateContentState {
                it.copy(checklist = it.checklist.copy(reminderAt = triggerAtMillis))
            }

            analyticsTracker.event("reminder_set", mapOf(
                "checklist_id" to state.checklist.id.toString()
            ))

            maybeShowExactAlarmInstruction()
        }
    }

    private fun removeReminder() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            repository.setReminder(state.checklist.id, null)
            reminderScheduler.cancelReminder(state.checklist.id)
            updateContentState {
                it.copy(checklist = it.checklist.copy(reminderAt = null))
            }
            val repeatRule = state.checklist.repeatRule
            if (repeatRule != null) {
                analyticsTracker.event("recurring_reminder_cancelled", mapOf(
                    "checklist_id" to state.checklist.id.toString(),
                    "total_occurrences" to state.checklist.repeatOccurrenceCount.toString()
                ))
            } else {
                analyticsTracker.event("reminder_cancelled", mapOf(
                    "checklist_id" to state.checklist.id.toString()
                ))
            }
        }
    }

    private fun saveRepeatSchedule() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val config = state.pendingRepeatConfig ?: return
        val rule = config.toRule()
        val timeMinutes = config.timeHour * 60 + config.timeMinute

        viewModelScope.launch {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val today = now.toLocalDateTime(tz).date
            val triggerTime = LocalTime(config.timeHour, config.timeMinute)
            val todayTrigger = LocalDateTime(today, triggerTime).toInstant(tz).toEpochMilliseconds()

            val firstTriggerAt = if (todayTrigger > now.toEpochMilliseconds()) {
                todayTrigger
            } else {
                val tomorrow = today.plus(1, DateTimeUnit.DAY)
                LocalDateTime(tomorrow, triggerTime).toInstant(tz).toEpochMilliseconds()
            }

            repository.setRepeatSchedule(state.checklist.id, rule, timeMinutes, firstTriggerAt)
            reminderScheduler.scheduleRepeat(state.checklist.id, firstTriggerAt)

            updateContentState {
                it.copy(
                    checklist = it.checklist.copy(
                        repeatRule = rule,
                        repeatTimeOfDayMinutes = timeMinutes,
                        repeatNextAt = firstTriggerAt,
                        repeatOccurrenceCount = 0
                    ),
                    showReminderSheet = false,
                    pendingRepeatConfig = null,
                    repeatRuleSummary = buildRepeatSummary(config)
                )
            }

            analyticsTracker.event("repeat_schedule_set", buildMap {
                put("type", rule.type.name)
                put("interval", rule.interval.toString())
                put("time_of_day", "$timeMinutes")
                put("reset_checks", rule.resetChecks.toString())
                put("preset", resolvePresetName(config))
                put("is_edit", (state.checklist.repeatRule != null).toString())
                put("end_condition", rule.endCondition::class.simpleName.orEmpty())
                val days = rule.weekDays
                if (!days.isNullOrEmpty()) {
                    put("week_days", days.sorted().joinToString(","))
                }
            })
            maybeShowExactAlarmInstruction()
        }
    }

    private fun removeRepeatSchedule() {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            repository.clearRepeatSchedule(state.checklist.id)
            reminderScheduler.cancelRepeat(state.checklist.id)
            updateContentState {
                it.copy(
                    checklist = it.checklist.copy(
                        repeatRule = null,
                        repeatTimeOfDayMinutes = null,
                        repeatNextAt = null,
                        repeatOccurrenceCount = 0
                    ),
                    showReminderSheet = false,
                    pendingRepeatConfig = null,
                    repeatRuleSummary = null
                )
            }
            analyticsTracker.event("repeat_schedule_cancelled", mapOf(
                "checklist_id" to state.checklist.id.toString(),
                "total_occurrences" to state.checklist.repeatOccurrenceCount.toString()
            ))
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
                reminderScheduler.rescheduleAllActiveReminders()
                reminderScheduler.rescheduleAllActiveRepeats()
                updateContentState { it.copy(snackbarMessage = SNACKBAR_EXACT_GRANTED) }
            } else {
                updateContentState { it.copy(snackbarMessage = SNACKBAR_EXACT_DENIED) }
            }
        }
    }

    // ─── Repeat rule helpers ───────────────────────────────────────────

    private fun handleRepeatTypeSelected(type: RepeatType) {
        updatePendingRepeatConfig {
            it.copy(
                type = type,
                isCustom = false,
                interval = 1,
                weekDays = emptySet()
            )
        }
    }

    private fun toggleWeekDay(dayNumber: Int) {
        updatePendingRepeatConfig { config ->
            val updated = if (dayNumber in config.weekDays) {
                config.weekDays - dayNumber
            } else {
                config.weekDays + dayNumber
            }
            config.copy(weekDays = updated, isCustom = true)
        }
    }

    private inline fun updatePendingRepeatConfig(update: (PendingRepeatConfig) -> PendingRepeatConfig) {
        updateContentState { state ->
            val current = state.pendingRepeatConfig ?: PendingRepeatConfig()
            state.copy(pendingRepeatConfig = update(current))
        }
    }

    // ─── Reorder / delete helpers ──────────────────────────────────────

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

    private fun swipeDeleteItem(itemId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val itemIndex = fill.items.indexOfFirst { it.id == itemId }
        if (itemIndex == -1) return
        val item = fill.items[itemIndex]

        val checklistIndex = state.checklist.items.indexOfFirst { it.text == item.text }

        val updatedFillItems = fill.items.filterIndexed { i, _ -> i != itemIndex }
        val updatedFill = fill.copy(items = updatedFillItems)
        val updatedChecklist = if (checklistIndex >= 0) {
            state.checklist.copy(items = state.checklist.items.filterIndexed { i, _ -> i != checklistIndex })
        } else state.checklist

        pendingUndoJob?.cancel()

        updateContentState {
            it.copy(
                checklist = updatedChecklist,
                pendingUndoItem = UndoableDeleteItem(
                    fillItem = item,
                    checklistItemText = item.text,
                    originalFillIndex = itemIndex,
                    originalChecklistIndex = checklistIndex,
                ),
            )
        }

        viewModelScope.launch {
            // Cancel any per-item alarms before persisting deletion
            if (item.hasActiveReminder) {
                reminderScheduler.cancelItemReminder(checklistId, fill.id, itemId)
                reminderScheduler.cancelItemRepeat(checklistId, fill.id, itemId)
            }
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("item_deleted", mapOf(
                "checklist_id" to checklistId.toString(),
                "method" to "swipe",
                "item_count" to updatedFillItems.size.toString()
            ))
        }

        pendingUndoJob = viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            updateContentState { it.copy(pendingUndoItem = null) }
        }
    }

    private fun undoDeleteItem() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val undo = state.pendingUndoItem ?: return
        val fill = state.defaultFill ?: return

        pendingUndoJob?.cancel()

        val restoredFillItems = fill.items.toMutableList().apply {
            add(undo.originalFillIndex.coerceAtMost(size), undo.fillItem)
        }
        val restoredFill = fill.copy(items = restoredFillItems)

        val restoredChecklistItems = state.checklist.items.toMutableList().apply {
            if (undo.originalChecklistIndex >= 0) {
                add(undo.originalChecklistIndex.coerceAtMost(size),
                    ChecklistItem(text = undo.checklistItemText))
            }
        }
        val restoredChecklist = state.checklist.copy(items = restoredChecklistItems)

        updateContentState {
            it.copy(
                checklist = restoredChecklist,
                pendingUndoItem = null,
            )
        }

        viewModelScope.launch {
            repository.updateFill(restoredFill)
            repository.updateChecklistTemplate(restoredChecklist)
            analyticsTracker.event("item_undo_delete", mapOf(
                "checklist_id" to checklistId.toString()
            ))
        }
    }

    // ─── Item details sheet delete ────────────────────────────────────────

    private fun deleteItemFromSheet(itemId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val item = fill.items.firstOrNull { it.id == itemId } ?: return

        val updatedFillItems = fill.items.filter { it.id != itemId }
        val updatedFill = fill.copy(items = updatedFillItems)

        val updatedChecklistItems = state.checklist.items.filter { it.text != item.text }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        updateContentState {
            it.copy(
                checklist = updatedChecklist,
                itemDetailsSheetFor = null,
            )
        }

        viewModelScope.launch {
            if (item.hasActiveReminder) {
                reminderScheduler.cancelItemReminder(checklistId, fill.id, itemId)
                reminderScheduler.cancelItemRepeat(checklistId, fill.id, itemId)
            }
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event("item_deleted", mapOf(
                "checklist_id" to checklistId.toString(),
                "method" to "sheet",
                "item_count" to updatedFillItems.size.toString()
            ))
        }
    }

    // ─── Per-item reminder handlers ───────────────────────────────────────

    private fun handleItemReminderClick(itemId: String) {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val item = state.defaultFill?.items?.firstOrNull { it.id == itemId } ?: return@launch
            val isPremium = awaitUserLimits()?.isPremium ?: false

            // Close item details sheet before opening reminder sheet (Approach A)
            updateContentState { it.copy(itemDetailsSheetFor = null) }

            // Free-tier gate: only for new reminders (item doesn't already have one)
            val isAtLimit = !isPremium && !item.hasActiveReminder &&
                    repository.countActiveReminders() >= 1

            if (isAtLimit) {
                // Show locked banner inside the sheet — skip notification permission check
                // since the user cannot create a reminder until they upgrade.
                updateContentState {
                    it.copy(
                        itemReminderSheetFor = itemId,
                        activeItemReminderTab = ReminderTab.ONCE,
                        itemReminderSheetLocked = true,
                    )
                }
                return@launch
            }

            if (!reminderScheduler.hasNotificationPermission()) {
                // Reuse notification permission sheet; after granting, we reopen the item sheet
                // by storing the target itemId first so the user can retry
                updateContentState {
                    it.copy(
                        itemReminderSheetFor = itemId,
                        activeItemReminderTab = ReminderTab.ONCE,
                        itemReminderSheetLocked = false,
                        showNotificationPermissionSheet = true
                    )
                }
            } else {
                val defaultTab = if (item.repeatRule != null && item.reminderAt == null) {
                    ReminderTab.REPEAT
                } else {
                    ReminderTab.ONCE
                }
                updateContentState {
                    it.copy(
                        itemReminderSheetFor = itemId,
                        activeItemReminderTab = defaultTab,
                        itemReminderSheetLocked = false,
                    )
                }
                if (defaultTab == ReminderTab.REPEAT) {
                    initItemRepeatTabIfNeeded(itemId)
                }
            }
        }
    }

    private fun initItemRepeatTabIfNeeded(itemId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        if (state.pendingRepeatConfig != null) return
        val item = state.defaultFill?.items?.firstOrNull { it.id == itemId } ?: return
        val existingRule = item.repeatRule ?: return
        val existingTimeMinutes = item.repeatTimeOfDayMinutes ?: (9 * 60)
        val config = PendingRepeatConfig(
            type = existingRule.type,
            interval = existingRule.interval,
            weekDays = existingRule.weekDays ?: emptySet(),
            endCondition = existingRule.endCondition,
            resetChecks = existingRule.resetChecks,
            isCustom = existingRule.interval > 1 || !existingRule.weekDays.isNullOrEmpty(),
            timeHour = existingTimeMinutes / 60,
            timeMinute = existingTimeMinutes % 60
        )
        updateContentState {
            it.copy(
                pendingRepeatConfig = config,
                repeatRuleSummary = buildRepeatSummary(config)
            )
        }
    }

    private fun saveItemReminder(
        itemId: String,
        reminderAt: Long?,
        repeatRule: ReminderRepeatRule?,
        repeatTimeOfDayMinutes: Int?
    ) {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val fill = state.defaultFill ?: return@launch
            val item = fill.items.firstOrNull { it.id == itemId } ?: return@launch

            // Cancel prior schedule(s) before applying new ones (switching between types)
            if (item.hasActiveReminder) {
                reminderScheduler.cancelItemReminder(checklistId, fill.id, itemId)
                reminderScheduler.cancelItemRepeat(checklistId, fill.id, itemId)
            }

            val updatedItem: ChecklistFillItem = if (repeatRule != null && repeatTimeOfDayMinutes != null) {
                // Compute first trigger time the same way saveRepeatSchedule does
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now()
                val today = now.toLocalDateTime(tz).date
                val timeHour = repeatTimeOfDayMinutes / 60
                val timeMinute = repeatTimeOfDayMinutes % 60
                val triggerTime = LocalTime(timeHour, timeMinute)
                val todayTrigger = LocalDateTime(today, triggerTime).toInstant(tz).toEpochMilliseconds()
                val firstTriggerAt = if (todayTrigger > now.toEpochMilliseconds()) {
                    todayTrigger
                } else {
                    val tomorrow = today.plus(1, DateTimeUnit.DAY)
                    LocalDateTime(tomorrow, triggerTime).toInstant(tz).toEpochMilliseconds()
                }
                // Apply repeat rule first, then set one-shot reminderAt (null clears it)
                item.withRepeatRule(repeatRule, repeatTimeOfDayMinutes, firstTriggerAt)
                    .withReminderAt(reminderAt)
            } else {
                // One-shot only: clear any prior repeat, set reminderAt
                item.withReminderCleared().withReminderAt(reminderAt)
            }

            val updatedItems = fill.items.map { if (it.id == itemId) updatedItem else it }
            val updatedFill = fill.copy(items = updatedItems)

            // Persist — reminder fields live only on ChecklistFillItem, not template
            repository.updateFill(updatedFill)

            // Schedule alarms
            if (reminderAt != null) {
                reminderScheduler.scheduleItemReminder(checklistId, fill.id, itemId, reminderAt)
            }
            val nextAt = updatedItem.repeatNextAt
            if (repeatRule != null && nextAt != null) {
                reminderScheduler.scheduleItemRepeat(checklistId, fill.id, itemId, nextAt)
            }

            updateContentState {
                it.copy(
                    itemReminderSheetFor = null,
                    pendingRepeatConfig = null,
                    repeatRuleSummary = null
                )
            }

            analyticsTracker.event("item_reminder_set", mapOf(
                "checklist_id" to checklistId.toString(),
                "has_repeat" to (repeatRule != null).toString()
            ))

            maybeShowExactAlarmInstruction()
        }
    }

    private fun removeItemReminder(itemId: String) {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val fill = state.defaultFill ?: return@launch

            // Defensive: cancel both regardless of which was active
            reminderScheduler.cancelItemReminder(checklistId, fill.id, itemId)
            reminderScheduler.cancelItemRepeat(checklistId, fill.id, itemId)

            val updatedItems = fill.items.map { item ->
                if (item.id == itemId) item.withReminderCleared() else item
            }
            val updatedFill = fill.copy(items = updatedItems)
            repository.updateFill(updatedFill)

            updateContentState {
                it.copy(
                    itemReminderSheetFor = null,
                    pendingRepeatConfig = null,
                    repeatRuleSummary = null
                )
            }

            analyticsTracker.event("item_reminder_removed", mapOf(
                "checklist_id" to checklistId.toString()
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
        /** Max independent repeat schedules for free users. Matches Remote Config default. */
        const val MAX_FREE_REPEAT_SCHEDULES = 1
    }
}
