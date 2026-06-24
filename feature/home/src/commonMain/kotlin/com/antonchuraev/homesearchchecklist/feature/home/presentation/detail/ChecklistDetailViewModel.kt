package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.buildCalendarEvent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree.ChecklistTree
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.combinePickerResults
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.resolvePresetName
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.*

class ChecklistDetailViewModel(
    private val checklistId: Long,
    // Folder drill-down level. null = checklist root. Set per back-stack entry (each folder open
    // pushes a fresh ChecklistDetail route + a fresh ViewModel keyed by checklist+folder).
    private val currentFolderId: String? = null,
    private val repository: ChecklistRepository,
    private val navigator: AppNavigator,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val reminderScheduler: ChecklistReminderScheduler,
    private val datastore: AppDatastore,
    private val smartDateParser: SmartDateParser,
    private val attachmentStorage: AttachmentStoragePort,
    private val calendarEventLauncher: CalendarEventLauncher,
    private val logger: AppLogger,
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

    /**
     * Backing flow for the inline add-item text field. Owned by the ViewModel so we can
     * debounce without touching Compose state. The screen mirrors [ChecklistDetailState.Content.pendingItemInput]
     * for display and fires [ChecklistDetailIntent.OnItemInputChanged] on each keystroke.
     */
    private val _pendingItemInput = MutableStateFlow("")

    /** Guard against rapid double-tap on the add button. */
    private var isAddingItem = false

    private var loadDataJob: Job? = null

    /** True when user navigated to exact alarm settings; used to detect return. */
    private var wentToExactAlarmSettings = false

    private var pendingUndoJob: Job? = null

    /**
     * Guards against a double back-pop when the folder level we are standing on disappears.
     * Set once — either by [confirmFolderDelete] when the user deletes the current folder
     * locally (it pops synchronously to avoid a ghost frame), or by [loadData] when the same
     * folder is removed/flattened from another device or a parent back-stack entry and the Room
     * flow re-emits without it. Without this both paths could fire navigator.onBack() twice.
     */
    private var poppedMissingLevel = false

    init {
        loadData()
        observeInputForParsing()
    }

    /**
     * Debounced Smart Add parser. Mirrors [_pendingItemInput] into
     * [ChecklistDetailState.Content.pendingItemInput] and runs the parser 200ms after the
     * last keystroke, storing the result in [ChecklistDetailState.Content.parsedToken].
     *
     * Using a dedicated flow + debounce avoids invoking the regex parser on every character
     * and prevents unnecessary Compose recompositions from transient intermediate states.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeInputForParsing() {
        viewModelScope.launch {
            _pendingItemInput
                .debounce(200)
                .collect { text ->
                    val token = if (text.isNotBlank()) {
                        val now = Clock.System.now().toEpochMilliseconds()
                        smartDateParser.parse(text, now, TimeZone.currentSystemDefault())
                    } else {
                        null
                    }
                    updateContentState { it.copy(parsedToken = token) }
                }
        }
    }

    private fun loadData() {
        loadDataJob = viewModelScope.launch {
            combine(
                repository.observeChecklistById(checklistId),
                repository.getDefaultFillByChecklistId(checklistId),
                repository.getAdditionalFillsByChecklistId(checklistId)
            ) { checklist, defaultFill, additionalFills ->
                Triple(checklist, defaultFill, additionalFills)
            }.collect { (checklist, defaultFill, additionalFills) ->
                if (checklist == null || defaultFill == null) {
                    _screenState.value = ChecklistDetailState.NotFound
                    return@collect
                }

                // If the folder level we're standing on was deleted or flattened elsewhere (sync
                // from another device, or a cascade delete from a parent back-stack entry), its
                // node is gone from the template. Pop out instead of rendering a "ghost level"
                // (an empty list titled with the whole checklist's name). Guarded so we never
                // double-pop with confirmFolderDelete's synchronous local pop.
                val fid = currentFolderId
                if (fid != null && !poppedMissingLevel) {
                    val folderStillExists = checklist.foldersEnabled &&
                        checklist.items.any { it.id == fid && it.type == ChecklistNodeType.FOLDER }
                    if (!folderStillExists) {
                        poppedMissingLevel = true
                        navigator.onBack()
                        return@collect
                    }
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
        val sortedFill = defaultFill?.withSortedItems()
        val folderState = buildFolderState(checklist, sortedFill)
        val currentState = _screenState.value
        _screenState.value = if (currentState is ChecklistDetailState.Content) {
            // Keep `checklist` in sync with Room — name/items/reminders edited from
            // EditChecklistScreen or other writers must propagate live. The toggle
            // flags (separateCompleted/autoDeleteCompleted) are intentionally NOT
            // re-derived here because each toggle handler already updates them
            // optimistically and persists asynchronously; overwriting them on every
            // Flow emission would flicker the switches during in-flight writes.
            currentState.copy(
                checklist = checklist,
                defaultFill = sortedFill,
                additionalFillsCount = additionalFillsCount,
                foldersEnabled = folderState.foldersEnabled,
                currentFolderId = folderState.currentFolderId,
                currentFolderTitle = folderState.currentFolderTitle,
                folders = folderState.folders,
                visibleFillItemIds = folderState.visibleFillItemIds,
                levelNodes = folderState.levelNodes,
            )
        } else {
            // Pull the latest userLimits from its independent flow — it may have
            // already emitted while state was still Loading.
            ChecklistDetailState.Content(
                checklist = checklist,
                defaultFill = sortedFill,
                additionalFillsCount = additionalFillsCount,
                userLimits = _userLimits.value,
                separateCompleted = checklist.separateCompleted,
                autoDeleteCompleted = checklist.autoDeleteCompleted,
                foldersEnabled = folderState.foldersEnabled,
                currentFolderId = folderState.currentFolderId,
                currentFolderTitle = folderState.currentFolderTitle,
                folders = folderState.folders,
                visibleFillItemIds = folderState.visibleFillItemIds,
                levelNodes = folderState.levelNodes,
            )
        }
    }

    /**
     * Snapshot of the folder-tree fields for the current drill-down level, derived purely
     * from the template [Checklist.items] (+ fill items, for folder progress).
     *
     * When [Checklist.foldersEnabled] is false the tree is collapsed to "flat list" semantics:
     * no folders, no filtering ([FolderStateSnapshot.visibleFillItemIds] == null → the screen
     * renders every fill item, exactly as before this feature).
     */
    private fun buildFolderState(
        checklist: Checklist,
        fill: ChecklistFill?,
    ): FolderStateSnapshot {
        if (!checklist.foldersEnabled) {
            return FolderStateSnapshot(
                foldersEnabled = false,
                currentFolderId = null,
                currentFolderTitle = null,
                folders = emptyList(),
                visibleFillItemIds = null,
                levelNodes = emptyList(),
            )
        }

        val items = checklist.items
        val fillItems = fill?.items.orEmpty()

        // Children of the current level in TEMPLATE order (folders + leaves intermixed). This single
        // ordered list backs both the folder rows and the mixed reorderable list (levelNodes), so a
        // folder keeps its real position relative to sibling items.
        val levelChildren = ChecklistTree.childrenOf(items, currentFolderId)

        // The fill-item id linked to each leaf template node (stable templateItemId link).
        val fillIdByTemplateId: Map<String, String> = buildMap {
            for (fillItem in fillItems) {
                val templateId = fillItem.templateItemId ?: continue
                // First linked row wins (defensive against duplicate links in corrupted data).
                // NB: Map.putIfAbsent is JVM-only — not available in Kotlin/Wasm common stdlib.
                if (!containsKey(templateId)) put(templateId, fillItem.id)
            }
        }

        val folderModels = mutableListOf<FolderUiModel>()
        val levelNodes = mutableListOf<LevelNode>()
        val visibleIds = LinkedHashSet<String>()

        for (child in levelChildren) {
            if (child.type == ChecklistNodeType.FOLDER) {
                val progress = ChecklistTree.folderProgress(items, fillItems, child.id)
                // A folder owns a flat fill row (created in createFolder, linked by templateItemId);
                // its reminder fields live there exactly like a leaf's. Legacy folders without a row
                // simply read hasReminder = false until a reminder is set (the row is created lazily).
                val hasReminder = fillItems
                    .firstOrNull { it.templateItemId == child.id }
                    ?.hasActiveReminder == true
                val model = FolderUiModel(
                    id = child.id,
                    name = child.text,
                    checked = progress.checked,
                    total = progress.total,
                    hasReminder = hasReminder,
                )
                folderModels += model
                levelNodes += LevelNode.Folder(model)
            } else {
                // Leaf — surface only if it has a fill row at this level (its checked/note/attachment
                // state lives there). A template leaf without a fill row is not rendered.
                val fillId = fillIdByTemplateId[child.id] ?: continue
                visibleIds += fillId
                levelNodes += LevelNode.Leaf(fillId)
            }
        }

        // Legacy fill rows WITHOUT a template link only surface at the root level so they never
        // silently disappear inside a folder. Append them after the linked children (they have no
        // template node to interleave with) so they stay visible and reorderable.
        if (currentFolderId == null) {
            for (fillItem in fillItems) {
                if (fillItem.templateItemId == null) {
                    visibleIds += fillItem.id
                    levelNodes += LevelNode.Leaf(fillItem.id)
                }
            }
        }

        // Current folder title from the template node (last element of its ancestor path = the node).
        val title = currentFolderId?.let { id ->
            ChecklistTree.ancestorPath(items, id).lastOrNull()?.text
        }

        return FolderStateSnapshot(
            foldersEnabled = true,
            currentFolderId = currentFolderId,
            currentFolderTitle = title,
            folders = folderModels,
            visibleFillItemIds = visibleIds,
            levelNodes = levelNodes,
        )
    }

    /** Internal carrier for the folder-tree fields derived in [buildFolderState]. */
    private data class FolderStateSnapshot(
        val foldersEnabled: Boolean,
        val currentFolderId: String?,
        val currentFolderTitle: String?,
        val folders: List<FolderUiModel>,
        val visibleFillItemIds: Set<String>?,
        val levelNodes: List<LevelNode>,
    )

    /**
     * Returns a copy of this fill with items sorted by priority DESC then by
     * their original list position ASC (index in the list = position proxy).
     *
     * Starred items (priority = 1) float to the top; within each priority group
     * the original insertion order is preserved. This sort is applied at the
     * state-mapping layer only — the persisted fill order is not changed, so
     * drag-to-reorder still works correctly (reorder writes orderedItemIds back
     * to persistence and the next Room emission re-sorts for display).
     */
    private fun ChecklistFill.withSortedItems(): ChecklistFill {
        val sorted = items
            .mapIndexed { index, item -> item to index }
            .sortedWith(compareByDescending<Pair<ChecklistFillItem, Int>> { it.first.priority }
                .thenBy { it.second })
            .map { it.first }
        return if (sorted == items) this else copy(items = sorted)
    }

    override fun onIntent(intent: ChecklistDetailIntent) {
        when (intent) {
            ChecklistDetailIntent.OnBackClick -> navigator.onBack()
            ChecklistDetailIntent.OnEditChecklistClick -> navigator.navigateToEditChecklist(checklistId)
            ChecklistDetailIntent.OnShareClick -> navigator.navigateToShareChecklist(checklistId)
            ChecklistDetailIntent.OnDeleteChecklistClick -> updateContentState { it.copy(showDeleteConfirmation = true) }
            ChecklistDetailIntent.OnConfirmDeleteChecklist -> deleteChecklist()
            ChecklistDetailIntent.OnDismissDeleteConfirmation -> updateContentState { it.copy(showDeleteConfirmation = false) }
            ChecklistDetailIntent.OnDeleteCorruptedChecklist -> deleteCorruptedChecklist()
            is ChecklistDetailIntent.OnItemCheckedChange -> updateItemChecked(intent.itemId, intent.checked)
            is ChecklistDetailIntent.OnAddNoteClick -> openNoteDialog(intent.itemId)
            is ChecklistDetailIntent.OnItemInputChanged -> handleItemInputChanged(intent.text)
            ChecklistDetailIntent.OnAddItemWithParse -> addItemWithParse()
            is ChecklistDetailIntent.OnOpenFolder -> navigator.navigateToFolder(checklistId, intent.folderId)
            ChecklistDetailIntent.OnCreateFolder -> createFolder()

            // ── Folder node actions (Phase 4) ──
            is ChecklistDetailIntent.OnFolderLongPress ->
                // Open the sheet in view mode (clear any stale inline-rename draft from a prior open).
                updateContentState {
                    it.copy(
                        folderActionsSheetFor = intent.folderId,
                        folderRenameForId = null,
                        folderRenameDraft = "",
                    )
                }
            ChecklistDetailIntent.OnDismissFolderActions ->
                // Closing the sheet also leaves inline-rename mode so the next open starts clean.
                updateContentState {
                    it.copy(
                        folderActionsSheetFor = null,
                        folderRenameForId = null,
                        folderRenameDraft = "",
                    )
                }

            is ChecklistDetailIntent.OnRenameFolder -> startFolderRename(intent.folderId)
            is ChecklistDetailIntent.OnFolderRenameDraftChange ->
                updateContentState { it.copy(folderRenameDraft = intent.text) }
            ChecklistDetailIntent.OnConfirmRenameFolder -> confirmFolderRename()
            ChecklistDetailIntent.OnDismissRenameFolder ->
                updateContentState { it.copy(folderRenameForId = null, folderRenameDraft = "") }

            is ChecklistDetailIntent.OnMoveNodeRequested -> openMoveSheet(intent.nodeId)
            is ChecklistDetailIntent.OnMoveNodeToFolder -> moveNodeToFolder(intent.nodeId, intent.targetFolderId)
            ChecklistDetailIntent.OnDismissMoveSheet ->
                updateContentState { it.copy(moveSheetForNodeId = null, moveTargets = emptyList()) }

            is ChecklistDetailIntent.OnFolderReminderClick -> handleFolderReminderClick(intent.folderId)
            is ChecklistDetailIntent.OnDeleteFolder -> requestFolderDelete(intent.folderId)
            ChecklistDetailIntent.OnConfirmDeleteFolder -> confirmFolderDelete()
            ChecklistDetailIntent.OnDismissDeleteFolder ->
                updateContentState { it.copy(pendingFolderDeleteId = null, pendingFolderDeleteCount = 0) }
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
                analyticsTracker.event(AnalyticsEvents.DetailUi.OVERFLOW_MENU_OPENED)
            }
            ChecklistDetailIntent.OnDismissOverflowSheet -> updateContentState { it.copy(showOverflowSheet = false) }
            ChecklistDetailIntent.OnToggleSeparateCompleted -> toggleSeparateCompleted()
            ChecklistDetailIntent.OnToggleAutoDeleteCompleted -> toggleAutoDeleteCompleted()
            ChecklistDetailIntent.OnToggleFoldersEnabled -> toggleFoldersEnabled()
            ChecklistDetailIntent.OnConfirmDisableFolders -> confirmDisableFolders()
            ChecklistDetailIntent.OnDismissDisableFolders ->
                updateContentState { it.copy(showFlattenFoldersConfirm = false) }
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
                        // Capture which scope opened the picker (item vs checklist) BEFORE
                        // clearing the item sheet, so OnTimeSelected routes the save correctly.
                        // null itemReminderSheetFor => checklist-level picker.
                        customPickerItemId = it.itemReminderSheetFor,
                        itemReminderSheetFor = null,
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
                val content = (_screenState.value as? ChecklistDetailState.Content) ?: return
                val dateMillis = content.customPickerDateMillis ?: return
                val triggerAt = combinePickerResults(dateMillis, intent.hour, intent.minute)
                val now = Clock.System.now().toEpochMilliseconds()
                if (triggerAt <= now) return
                // Route to the scope that opened the picker: a per-item reminder when an
                // itemId was captured, otherwise the checklist-level reminder. Without this
                // branch a custom date+time chosen from the item sheet was saved on the whole
                // checklist (the shared picker carries no scope of its own).
                val itemId = content.customPickerItemId
                if (itemId != null) {
                    saveItemReminder(itemId, triggerAt, repeatRule = null, repeatTimeOfDayMinutes = null)
                } else {
                    saveReminder(triggerAt)
                }
                updateContentState {
                    it.copy(showCustomPicker = false, customPickerDateMillis = null, customPickerItemId = null)
                }
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
                        customPickerItemId = null,
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
                val eventName = if (intent.expanded) {
                    AnalyticsEvents.DetailUi.COMPLETED_SECTION_EXPANDED
                } else {
                    AnalyticsEvents.DetailUi.COMPLETED_SECTION_COLLAPSED
                }
                analyticsTracker.event(eventName, mapOf(AnalyticsParams.COMPLETED_COUNT to intent.completedCount.toString()))
            }
            ChecklistDetailIntent.OnQuickAddOpened -> {
                analyticsTracker.event(AnalyticsEvents.DetailUi.QUICK_ADD_OPENED)
            }
            is ChecklistDetailIntent.OnQuickAddCancelled -> {
                analyticsTracker.event(AnalyticsEvents.DetailUi.QUICK_ADD_CANCELLED, mapOf(AnalyticsParams.HAD_TEXT to intent.hadText.toString()))
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
            ChecklistDetailIntent.OnDismissItemDetailsSheet -> updateContentState {
                it.copy(
                    itemDetailsSheetFor = null,
                    editingItemTextFor = null,
                    editingItemTextDraft = ""
                )
            }
            is ChecklistDetailIntent.OnDeleteItemFromSheet -> deleteItemFromSheet(intent.itemId)

            // Inline text edit inside ItemDetailsSheet
            is ChecklistDetailIntent.OnStartItemTextEdit -> startItemTextEdit(intent.itemId)
            is ChecklistDetailIntent.OnItemTextDraftChange -> updateItemTextDraft(intent.text)
            ChecklistDetailIntent.OnConfirmItemTextEdit -> confirmItemTextEdit()
            ChecklistDetailIntent.OnCancelItemTextEdit -> cancelItemTextEdit()

            // Reminder paywall upgrade from locked banner
            ChecklistDetailIntent.OnReminderUpgradeClick -> {
                updateContentState { it.copy(showReminderSheet = false, reminderSheetLocked = false) }
                navigator.navigateToPaywall(source = "detail_reminder_limit")
            }
            ChecklistDetailIntent.OnItemReminderUpgradeClick -> {
                updateContentState { it.copy(itemReminderSheetFor = null, itemReminderSheetLocked = false) }
                navigator.navigateToPaywall(source = "detail_item_reminder_limit")
            }

            // Attachments
            is ChecklistDetailIntent.OnAddImageAttachment -> handleAddAttachment(intent.itemId, isImage = true)
            is ChecklistDetailIntent.OnAddFileAttachment -> handleAddAttachment(intent.itemId, isImage = false)
            is ChecklistDetailIntent.OnAttachmentPicked -> handleAttachmentPicked(intent)
            is ChecklistDetailIntent.OnAttachmentClick -> handleOpenViewer(intent.attachmentId)
            ChecklistDetailIntent.OnCloseAttachmentViewer -> updateContentState { it.copy(attachmentViewerState = null) }
            is ChecklistDetailIntent.OnDeleteAttachment -> handleDeleteAttachment(intent.itemId, intent.attachmentId)
            is ChecklistDetailIntent.OnOpenAttachmentExternally -> handleOpenExternally(intent.attachmentId)
            ChecklistDetailIntent.OnImagePickerLaunched -> updateContentState { it.copy(triggerImagePicker = false) }
            ChecklistDetailIntent.OnFilePickerLaunched -> updateContentState { it.copy(triggerFilePicker = false) }
            ChecklistDetailIntent.OnOpenExternallyDispatched -> updateContentState {
                it.copy(pendingOpenExternallyPath = null, pendingOpenExternallyMimeType = null)
            }

            // Priority
            is ChecklistDetailIntent.OnToggleItemPriority -> toggleItemPriority(intent.itemId)

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

            // Calendar export — handlers are SYNCHRONOUS (no viewModelScope.launch around the
            // launcher call) so the launch happens inside the click call stack (Web popup-safety).
            ChecklistDetailIntent.OnAddToCalendar -> handleAddToCalendar()
            is ChecklistDetailIntent.OnAddItemToCalendar -> handleAddItemToCalendar(intent.itemId)
        }
    }

    /**
     * Exports the checklist-level reminder/repeat to the device calendar (one-way).
     *
     * Synchronous on purpose: [CalendarEventLauncher.addEvent] must run inside the user-gesture
     * call stack (the Web actual uses `window.open`, which the browser blocks after a suspend).
     * All inputs are read from already-loaded [ChecklistDetailState.Content] — no repository/suspend
     * calls. The only suspending step is resolving the snackbar string, which happens AFTER the
     * launch via [showCalendarSnackbar] (a string-key marker resolved in the Composable).
     */
    private fun handleAddToCalendar() {
        val content = _screenState.value as? ChecklistDetailState.Content ?: return
        val checklist = content.checklist
        // Prefer the one-shot reminder; fall back to the next recurring fire time. May be null —
        // then the calendar opens with no pre-set time for the user to pick.
        val start = checklist.reminderAt ?: checklist.repeatNextAt
        val event = buildCalendarEvent(
            title = checklist.name,
            startMillis = start,
            rule = checklist.repeatRule,
        )
        val launched = calendarEventLauncher.addEvent(event)
        if (!launched) {
            showCalendarSnackbar(SNACKBAR_CALENDAR_APP_NOT_FOUND)
        } else {
            analyticsTracker.event(
                "add_to_calendar",
                mapOf(
                    "recurring" to (checklist.repeatRule != null).toString(),
                    "has_time" to (start != null).toString(),
                    "level" to "checklist",
                ),
            )
        }
    }

    /**
     * Exports a single item's reminder/repeat to the device calendar (one-way).
     * Same synchronous contract as [handleAddToCalendar]; the item is looked up in the already
     * loaded default fill by [itemId]. With no reminder set, the event is exported without a
     * pre-set time (the user picks it in the calendar app).
     */
    private fun handleAddItemToCalendar(itemId: String) {
        val content = _screenState.value as? ChecklistDetailState.Content ?: return
        val item = content.defaultFill?.items?.firstOrNull { it.id == itemId } ?: return
        val start = item.reminderAt ?: item.repeatNextAt
        val event = buildCalendarEvent(
            title = item.text,
            startMillis = start,
            rule = item.repeatRule,
        )
        val launched = calendarEventLauncher.addEvent(event)
        if (!launched) {
            showCalendarSnackbar(SNACKBAR_CALENDAR_APP_NOT_FOUND)
        } else {
            analyticsTracker.event(
                "add_to_calendar",
                mapOf(
                    "recurring" to (item.repeatRule != null).toString(),
                    "has_time" to (start != null).toString(),
                    "level" to "item",
                ),
            )
        }
    }

    /**
     * Posts a calendar-feedback snackbar. [messageKey] is a string-key marker (the codebase's
     * snackbar convention — resolved to a real string in the Composable's `when(message)` block),
     * so this never touches Compose Resources / suspend `getString` and stays callable from the
     * synchronous calendar handlers.
     */
    private fun showCalendarSnackbar(messageKey: String) {
        updateContentState { it.copy(snackbarMessage = messageKey) }
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

            // Drop the linked template item — match by the stable link, falling back to text for
            // legacy fill rows. Text fallback would also delete a same-text sibling; the link avoids that.
            val updatedChecklistItems = state.checklist.items.filterNot { templateItem ->
                if (itemToDelete.templateItemId != null) {
                    templateItem.id == itemToDelete.templateItemId
                } else {
                    templateItem.text == itemToDelete.text
                }
            }
            val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)
            updateContentState { it.copy(checklist = updatedChecklist) }

            viewModelScope.launch {
                if (itemToDelete.hasActiveReminder) {
                    reminderScheduler.cancelItemReminder(checklistId, state.defaultFill.id, itemId)
                    reminderScheduler.cancelItemRepeat(checklistId, state.defaultFill.id, itemId)
                }
                repository.updateFill(updatedFill)
                repository.updateChecklistTemplate(updatedChecklist)
                analyticsTracker.event(AnalyticsEvents.Item.AUTO_DELETED, mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString()
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

        val eventName = if (checked) AnalyticsEvents.Item.CHECKED else AnalyticsEvents.Item.UNCHECKED
        val totalItems = updatedItems.size
        val checkedCount = updatedItems.count { it.checked }
        analyticsTracker.event(eventName, mapOf(
            AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
            AnalyticsParams.PROGRESS to if (totalItems > 0) "${checkedCount * 100 / totalItems}" else "0"
        ))

        if (checked && totalItems > 0 && checkedCount == totalItems) {
            analyticsTracker.event(AnalyticsEvents.Checklist.FILL_COMPLETED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                AnalyticsParams.ITEM_COUNT to totalItems.toString()
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

    /**
     * Toggles the per-checklist folders feature.
     *
     * Folders and Weekly view are mutually exclusive (both are alternative groupings of the same
     * flat list), so enabling folders is a no-op while [Checklist.viewMode] == Weekly. The
     * Composable also disables the switch in that case; this guard is the belt-and-suspenders
     * backstop against a stale tap.
     *
     * - Enable (currently off, not Weekly): flips [Checklist.foldersEnabled] on. No structural change.
     * - Disable with folder nodes present: opens the flatten-confirm dialog
     *   ([confirmDisableFolders] does the actual flatten) — folders are destructive to remove.
     * - Disable with NO folder nodes: flips it straight off (nothing to flatten).
     */
    private fun toggleFoldersEnabled() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val enabling = !state.foldersEnabled

        // Mutual exclusion: never enable folders on a Weekly checklist.
        if (enabling && state.checklist.viewMode == ChecklistViewMode.Weekly) {
            updateContentState { it.copy(showOverflowSheet = false) }
            return
        }

        if (enabling) {
            updateContentState { it.copy(foldersEnabled = true) }
            viewModelScope.launch {
                repository.setFoldersEnabled(checklistId, true)
            }
            analyticsTracker.event("folders_enabled_toggled", mapOf("enabled" to "true"))
            return
        }

        // Disabling: confirm first if there are folders to flatten.
        val hasFolders = state.checklist.items.any { it.type == ChecklistNodeType.FOLDER }
        if (hasFolders) {
            updateContentState { it.copy(showOverflowSheet = false, showFlattenFoldersConfirm = true) }
        } else {
            updateContentState { it.copy(foldersEnabled = false) }
            viewModelScope.launch {
                repository.setFoldersEnabled(checklistId, false)
            }
            analyticsTracker.event("folders_enabled_toggled", mapOf("enabled" to "false"))
        }
    }

    /**
     * Confirms disabling folders: FLATTENS the tree, then turns the feature off.
     *
     * Flatten keeps every checkable leaf at the top level and discards the folder containers:
     * - Template: drop all FOLDER nodes; re-parent every surviving (leaf) node to root (parentId=null).
     * - Fill: drop the placeholder fill rows linked to the removed folders (matched by the stable
     *   [ChecklistFillItem.templateItemId]); leaf fill rows are untouched (their checked/note/
     *   attachment state is kept).
     * - Alarms: cancel any folder reminder (Phase 5) living on a removed folder's fill row, using
     *   the same mechanism as the cascade delete ([ChecklistReminderScheduler.cancelItemReminder] +
     *   [cancelItemRepeat], keyed by the folder's FILL id). Leaf reminders are preserved — leaves
     *   survive the flatten.
     *
     * If the user is currently INSIDE a folder (this VM is scoped to a folder id) the level is gone
     * after flatten, so we pop back to the root view.
     *
     * Persists via the existing surface only: [ChecklistRepository.updateFill] +
     * [updateChecklistTemplate] for the structural change, then [setFoldersEnabled] for the flag.
     */
    private fun confirmDisableFolders() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill

        val folderIds = state.checklist.items
            .filter { it.type == ChecklistNodeType.FOLDER }
            .map { it.id }
            .toSet()

        // Template: remove folders, flatten survivors to root.
        val flattenedItems = state.checklist.items
            .filterNot { it.id in folderIds }
            .map { if (it.parentId != null) it.withParentId(null) else it }
        val updatedChecklist = state.checklist.copy(items = flattenedItems, foldersEnabled = false)

        // Fill: cancel folder-reminder alarms, then drop the folders' placeholder rows.
        val fillItemsToCancel = fill?.items.orEmpty().filter { fillItem ->
            val templateId = fillItem.templateItemId
            templateId != null && templateId in folderIds && fillItem.hasActiveReminder
        }
        val updatedFill = fill?.let { f ->
            val keptItems = f.items.filterNot { fillItem ->
                val templateId = fillItem.templateItemId
                templateId != null && templateId in folderIds
            }
            f.copy(items = keptItems)
        }

        val viewingFolder = currentFolderId != null

        updateContentState {
            val snapshot = buildFolderState(updatedChecklist, updatedFill)
            it.copy(
                checklist = updatedChecklist,
                defaultFill = updatedFill ?: it.defaultFill,
                foldersEnabled = false,
                currentFolderId = snapshot.currentFolderId,
                currentFolderTitle = snapshot.currentFolderTitle,
                folders = snapshot.folders,
                visibleFillItemIds = snapshot.visibleFillItemIds,
                levelNodes = snapshot.levelNodes,
                showFlattenFoldersConfirm = false,
            )
        }

        viewModelScope.launch {
            // Cancel alarms for folders that carried a reminder (keyed by the folder's FILL id).
            if (fill != null) {
                for (folderRow in fillItemsToCancel) {
                    reminderScheduler.cancelItemReminder(checklistId, fill.id, folderRow.id)
                    reminderScheduler.cancelItemRepeat(checklistId, fill.id, folderRow.id)
                }
            }
            if (updatedFill != null) repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            repository.setFoldersEnabled(checklistId, false)
            analyticsTracker.event(
                "folders_flattened",
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    "removed_folders" to folderIds.size.toString(),
                ),
            )
            if (viewingFolder) navigator.onBack()
        }
    }

    private fun deleteCompletedItems() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return

        val completedItems = fill.items.filter { it.checked }
        if (completedItems.isEmpty()) return

        // Identify the template items to drop. Prefer the stable link; for legacy fill rows
        // without a link, fall back to text. Splitting the sets keeps the text fallback from
        // also deleting a same-text sibling whose fill item was NOT checked.
        val completedLinkIds = completedItems.mapNotNull { it.templateItemId }.toSet()
        val completedLegacyTexts = completedItems.filter { it.templateItemId == null }.map { it.text }.toSet()

        val updatedFillItems = fill.items.filter { !it.checked }
        val updatedFill = fill.copy(items = updatedFillItems)

        val updatedChecklistItems = state.checklist.items.filterNot { templateItem ->
            templateItem.id in completedLinkIds || templateItem.text in completedLegacyTexts
        }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        updateContentState {
            it.copy(checklist = updatedChecklist, showOverflowSheet = false)
        }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event(AnalyticsEvents.Item.COMPLETED_ITEMS_DELETED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                "deleted_count" to completedItems.size.toString(),
                "remaining_count" to updatedFillItems.size.toString()
            ))
        }
    }

    private fun handleItemInputChanged(text: String) {
        // Mirror the raw text into state immediately (for the TextField to display) and
        // into the backing flow (which feeds the debounced parser).
        _pendingItemInput.value = text
        updateContentState { it.copy(pendingItemInput = text) }
    }

    /**
     * Submits the current [ChecklistDetailState.Content.pendingItemInput].
     *
     * If [ChecklistDetailState.Content.parsedToken] is non-null:
     *   - Strips [ParsedDateToken.originalSubstring] from the input text
     *   - Creates a new [ChecklistFillItem] with the reminder fields from the token
     *   - If stripping leaves a blank text, the add is skipped (no empty items with reminders)
     *
     * If parsedToken is null — plain add (same as the former OnAddItem behaviour).
     *
     * Applies a double-fire guard to prevent duplicate items from rapid taps.
     */
    private fun addItemWithParse() {
        if (isAddingItem) return
        isAddingItem = true

        val state = _screenState.value as? ChecklistDetailState.Content ?: run {
            isAddingItem = false
            return
        }
        val fill = state.defaultFill ?: run { isAddingItem = false; return }
        val rawText = state.pendingItemInput
        val token = state.parsedToken

        val itemText: String
        val reminderAt: Long?
        val repeatRule: ReminderRepeatRule?
        val repeatTimeOfDayMinutes: Int?

        if (token != null) {
            // Strip the matched date/time substring and trim surrounding whitespace
            val stripped = (rawText.removeRange(token.startIndex, token.endIndex)).trim()
            val isStrandedPrep = stripped.lowercase() in STRANDED_TIME_PREPOSITIONS
            when {
                stripped.isBlank() -> {
                    // Only the trigger phrase was typed — show hint, do not clear input
                    updateContentState { it.copy(snackbarMessage = SNACKBAR_SMART_ADD_HINT_ADD_TEXT) }
                    isAddingItem = false
                    return
                }
                isStrandedPrep -> {
                    // A time preposition is left over ("в", "at") — user started typing a time but
                    // didn't finish. Show hint without clearing input so they can complete it.
                    updateContentState { it.copy(snackbarMessage = SNACKBAR_SMART_ADD_HINT_ADD_TIME) }
                    isAddingItem = false
                    return
                }
            }
            itemText = stripped
            reminderAt = token.reminderAt
            repeatRule = token.repeatRule
            repeatTimeOfDayMinutes = token.timeOfDayMinutes
        } else {
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) { isAddingItem = false; return }
            itemText = trimmed
            reminderAt = null
            repeatRule = null
            repeatTimeOfDayMinutes = null
        }

        // Clear input state immediately (optimistic UX)
        _pendingItemInput.value = ""
        updateContentState { it.copy(pendingItemInput = "", parsedToken = null) }

        // Create the template item first so the new fill item can be linked to it from birth.
        // parentId = currentFolderId so inline Smart-Add drops the item into the folder the user
        // is currently viewing (root when currentFolderId == null).
        val newChecklistItem = ChecklistItem(text = itemText, parentId = currentFolderId)

        val newFillItem = if (token != null) {
            val base = ChecklistFillItem(
                text = itemText,
                checked = false,
                note = null,
                templateItemId = newChecklistItem.id,
            )
            val withRepeat = if (repeatRule != null && repeatTimeOfDayMinutes != null) {
                // Compute first trigger time (same logic as saveItemReminder)
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
                base.withRepeatRule(repeatRule, repeatTimeOfDayMinutes, firstTriggerAt)
            } else {
                base
            }
            withRepeat.withReminderAt(reminderAt)
        } else {
            ChecklistFillItem(
                text = itemText,
                checked = false,
                note = null,
                templateItemId = newChecklistItem.id,
            )
        }

        val updatedFill = fill.copy(items = fill.items + newFillItem)
        val updatedChecklist = state.checklist.copy(items = state.checklist.items + newChecklistItem)
        // Refresh folder visibility too so a just-added item shows immediately in folder mode
        // (otherwise it'd be absent from visibleFillItemIds / the reorderable list until the next
        // Room emission).
        updateContentState {
            val snapshot = buildFolderState(updatedChecklist, updatedFill)
            it.copy(
                checklist = updatedChecklist,
                visibleFillItemIds = snapshot.visibleFillItemIds,
                levelNodes = snapshot.levelNodes,
            )
        }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)

            // Schedule alarms for the new item if reminder was parsed
            if (reminderAt != null) {
                reminderScheduler.scheduleItemReminder(checklistId, fill.id, newFillItem.id, reminderAt)
            }
            val nextAt = newFillItem.repeatNextAt
            if (repeatRule != null && nextAt != null) {
                reminderScheduler.scheduleItemRepeat(checklistId, fill.id, newFillItem.id, nextAt)
            }

            val hasReminder = reminderAt != null || repeatRule != null
            analyticsTracker.event(AnalyticsEvents.Item.ADDED_QUICK, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                AnalyticsParams.ITEM_COUNT to updatedFill.items.size.toString(),
                "has_smart_reminder" to hasReminder.toString()
            ))

            isAddingItem = false
        }
    }

    /**
     * Creates a new empty FOLDER node under the current level.
     *
     * Folders live ONLY on the template tree; we still add a linked fill row (a leaf-less
     * placeholder carrying [ChecklistFillItem.templateItemId]) so the fill knows about the node —
     * needed later for folder-scoped reminders/progress and so reconciliation never orphans it.
     * The fill row is never rendered as a checkable item (the screen filters folders out by type).
     *
     * Persistence mirrors the Smart-Add path: [ChecklistRepository.updateChecklistTemplate]
     * (template only — NOT updateChecklist, which would regenerate fill ids) + [updateFill].
     *
     * Id strategy: [ChecklistItem] owns its id (timestamp + random; the same generator the rest of
     * this VM relies on), so we create the node first, then reuse `newFolder.id` for the fill link.
     */
    private fun createFolder() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return

        viewModelScope.launch {
            val folderName = getString(Res.string.folder_default_name)
            val newFolder = ChecklistItem(
                text = folderName,
                type = ChecklistNodeType.FOLDER,
                parentId = currentFolderId,
            )
            val folderFillRow = ChecklistFillItem(
                text = folderName,
                checked = false,
                note = null,
                templateItemId = newFolder.id,
            )

            val updatedChecklist = state.checklist.copy(items = state.checklist.items + newFolder)
            val updatedFill = fill.copy(items = fill.items + folderFillRow)

            // Optimistically reflect the new folder; buildFolderState re-derives the folder list
            // from the updated template + fill so the new row shows immediately.
            _screenState.update {
                if (it is ChecklistDetailState.Content) {
                    val snapshot = buildFolderState(updatedChecklist, updatedFill)
                    it.copy(
                        checklist = updatedChecklist,
                        defaultFill = updatedFill,
                        folders = snapshot.folders,
                        visibleFillItemIds = snapshot.visibleFillItemIds,
                        levelNodes = snapshot.levelNodes,
                    )
                } else {
                    it
                }
            }

            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)

            analyticsTracker.event(
                "folder_created",
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    "depth" to (if (currentFolderId == null) "root" else "nested"),
                )
            )
        }
    }

    // ── Folder node actions (Phase 4): rename / move / cascade-delete ─────────
    //
    // All three operate ONLY through the existing repository surface
    // (updateChecklistTemplate + updateFill) plus pure ChecklistTree helpers — no new
    // repository/DAO methods. Folders live on the template; fills stay flat and link back
    // via ChecklistFillItem.templateItemId.

    private fun startFolderRename(folderId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val folder = state.checklist.items.firstOrNull {
            it.id == folderId && it.type == ChecklistNodeType.FOLDER
        } ?: return
        // The rename happens INLINE inside the open folder actions sheet (mirrors how the leaf
        // ItemDetailsSheet edits its title), so the sheet must stay open. The headline swaps to a
        // TextField because folderRenameForId now matches folderActionsSheetFor.
        updateContentState {
            it.copy(
                folderRenameForId = folderId,
                folderRenameDraft = folder.text,
            )
        }
    }

    /**
     * Commits a folder rename: the folder name IS the template node's text, so we update the
     * FOLDER node ([ChecklistItem.withText]) and the linked fill row text (matched by the stable
     * [ChecklistFillItem.templateItemId]) so both stores stay consistent — mirroring how
     * [confirmItemTextEdit] keeps a leaf's fill/template text in sync.
     *
     * Blank or unchanged text just leaves inline-edit mode without a write (no empty folder names).
     * The actions sheet stays open in both cases — the new name shows in the headline, exactly like
     * the leaf details sheet after a text edit.
     */
    private fun confirmFolderRename() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val folderId = state.folderRenameForId ?: return  // double-fire guard
        val fill = state.defaultFill ?: return
        val newName = state.folderRenameDraft.trim()

        val folder = state.checklist.items.firstOrNull {
            it.id == folderId && it.type == ChecklistNodeType.FOLDER
        }
        if (folder == null || newName.isBlank() || folder.text == newName) {
            updateContentState {
                it.copy(folderRenameForId = null, folderRenameDraft = "")
            }
            return
        }

        val updatedChecklistItems = state.checklist.items.map { item ->
            if (item.id == folderId) item.withText(newName) else item
        }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        // Keep the linked fill row's text aligned (folders carry a placeholder fill row created
        // alongside the node — see createFolder). Match by the stable link only; folders are never
        // text-keyed.
        val updatedFillItems = fill.items.map { fillItem ->
            if (fillItem.templateItemId == folderId) fillItem.withText(newName) else fillItem
        }
        val updatedFill = fill.copy(items = updatedFillItems)

        // Optimistic update; buildFolderState re-derives folder rows (names) from the new template.
        // The actions sheet stays open (folderActionsSheetFor untouched) so the renamed headline
        // shows in place — leaving edit mode only (folderRenameForId cleared).
        updateContentState {
            val snapshot = buildFolderState(updatedChecklist, updatedFill)
            it.copy(
                checklist = updatedChecklist,
                defaultFill = updatedFill,
                folders = snapshot.folders,
                // Refresh the mixed reorderable list too so the renamed folder card updates in
                // place (its LevelNode.Folder carries the name).
                levelNodes = snapshot.levelNodes,
                currentFolderTitle = snapshot.currentFolderTitle,
                folderRenameForId = null,
                folderRenameDraft = "",
            )
        }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event(
                "folder_renamed",
                mapOf(AnalyticsParams.CHECKLIST_ID to checklistId.toString()),
            )
        }
    }

    /**
     * Opens the "Move to…" sheet for [nodeId] (folder OR leaf, identified by its template id),
     * building a flattened, depth-indented [MoveTargetUiModel] list of every folder in the
     * checklist plus a synthetic root row.
     *
     * Legality per target comes from [ChecklistTree.canMove] (disables the node itself and all its
     * descendants → no cycles); the node's current parent is flagged so its row reads "Current
     * location" and is non-actionable.
     */
    private fun openMoveSheet(nodeId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val items = state.checklist.items
        val node = items.firstOrNull { it.id == nodeId } ?: return
        val currentParentId = node.parentId

        val targets = buildMoveTargets(items, nodeId, currentParentId)
        updateContentState { it.copy(moveSheetForNodeId = nodeId, moveTargets = targets) }
    }

    /**
     * Pure builder for the move-destination list: a pre-order (depth-first) walk of the folder
     * subtree under each root folder, prefixed by a synthetic root ("Home") row. Extracted so it
     * can be unit-tested without UI.
     */
    private fun buildMoveTargets(
        items: List<ChecklistItem>,
        nodeId: String,
        currentParentId: String?,
    ): List<MoveTargetUiModel> {
        val result = ArrayList<MoveTargetUiModel>()

        // Synthetic root row (parentId == null).
        result.add(
            MoveTargetUiModel(
                id = null,
                name = "", // resolved to a string resource in the Composable (move_to_root)
                depth = 0,
                enabled = ChecklistTree.canMove(items, nodeId, null) && currentParentId != null,
                isCurrentParent = currentParentId == null,
            )
        )

        fun walk(parentId: String?, depth: Int) {
            ChecklistTree.childrenOf(items, parentId)
                .filter { it.type == ChecklistNodeType.FOLDER }
                .forEach { folder ->
                    val isCurrent = folder.id == currentParentId
                    result.add(
                        MoveTargetUiModel(
                            id = folder.id,
                            name = folder.text,
                            depth = depth,
                            // Legal target AND not where it already lives.
                            enabled = ChecklistTree.canMove(items, nodeId, folder.id) && !isCurrent,
                            isCurrentParent = isCurrent,
                        )
                    )
                    walk(folder.id, depth + 1)
                }
        }
        walk(null, 1)
        return result
    }

    /**
     * Re-parents [nodeId] under [targetFolderId] (null = checklist root) by rewriting the template
     * node's [ChecklistItem.parentId] ([ChecklistItem.withParentId]) — fills stay flat, so no fill
     * write is needed for a move. Guarded by [ChecklistTree.canMove] so a stale UI tap can never
     * create a cycle.
     *
     * Note: the moved node is identified by its TEMPLATE id. Both folders and leaves move the same
     * way (a leaf's parentId changes which folder level it renders under; its fill row is unchanged
     * and re-bucketed by buildFolderState via the stable template link).
     */
    private fun moveNodeToFolder(nodeId: String, targetFolderId: String?) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val items = state.checklist.items
        val node = items.firstOrNull { it.id == nodeId } ?: return

        // Defensive: ignore illegal targets and no-op moves (already there).
        if (!ChecklistTree.canMove(items, nodeId, targetFolderId)) {
            updateContentState {
                it.copy(moveSheetForNodeId = null, moveTargets = emptyList(), folderActionsSheetFor = null)
            }
            return
        }
        if (node.parentId == targetFolderId) {
            updateContentState {
                it.copy(moveSheetForNodeId = null, moveTargets = emptyList(), folderActionsSheetFor = null)
            }
            return
        }

        val updatedChecklistItems = items.map { item ->
            if (item.id == nodeId) item.withParentId(targetFolderId) else item
        }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)
        val fill = state.defaultFill

        // Re-derive the current level's folders + visible leaves so a moved node leaves/enters the
        // current view immediately (e.g. moving a leaf out of the open folder removes it on the spot).
        updateContentState {
            val snapshot = buildFolderState(updatedChecklist, fill)
            it.copy(
                checklist = updatedChecklist,
                folders = snapshot.folders,
                visibleFillItemIds = snapshot.visibleFillItemIds,
                levelNodes = snapshot.levelNodes,
                moveSheetForNodeId = null,
                moveTargets = emptyList(),
                folderActionsSheetFor = null,
            )
        }

        viewModelScope.launch {
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event(
                "node_moved",
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    "is_folder" to (node.type == ChecklistNodeType.FOLDER).toString(),
                    "to_root" to (targetFolderId == null).toString(),
                ),
            )
        }
    }

    /**
     * Opens the delete-folder confirm dialog, precomputing the cascade size (descendant nodes:
     * leaves + sub-folders) so the dialog can warn "This will delete N items inside". Closes the
     * actions sheet behind it.
     */
    private fun requestFolderDelete(folderId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val descendantCount = ChecklistTree.descendantIds(state.checklist.items, folderId).size
        updateContentState {
            it.copy(
                folderActionsSheetFor = null,
                pendingFolderDeleteId = folderId,
                pendingFolderDeleteCount = descendantCount,
            )
        }
    }

    /**
     * Cascading folder delete. Removes the folder node and ALL descendants (via
     * [ChecklistTree.cascadeDeleteIds]) from the template, and the linked fill rows (matched by the
     * stable [ChecklistFillItem.templateItemId]) from the fill.
     *
     * Before persisting, every deleted node that still has an active reminder gets its alarms
     * cancelled — both descendant LEAVES and the deleted FOLDER itself (a folder reminder lives on
     * its own fill row, Phase 5). Reuses the exact mechanism in [deleteItemFromSheet] /
     * [swipeDeleteItem]: [ChecklistReminderScheduler.cancelItemReminder] +
     * [ChecklistReminderScheduler.cancelItemRepeat], keyed by (checklistId, fillId, FILL-item id).
     * The fill id of each node is resolved through its templateItemId link.
     *
     * If the user is currently INSIDE the folder being deleted (or one of its descendants), we walk
     * back out first so they don't sit on a now-missing level (the back-stack entry was scoped to
     * that folder id).
     */
    private fun confirmFolderDelete() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val folderId = state.pendingFolderDeleteId ?: return  // double-fire guard
        val fill = state.defaultFill ?: return

        val removeIds = ChecklistTree.cascadeDeleteIds(state.checklist.items, folderId)

        // Resolve the fill rows to cancel alarms for: every deleted node with an active reminder.
        // removeIds = the folder node ITSELF + all descendants (cascadeDeleteIds), so this set
        // includes the folder's own fill row when the folder has a reminder (Phase 5) as well as
        // any descendant leaf reminders — both keyed by the row's FILL id below. Rows without a
        // reminder are naturally skipped (hasActiveReminder == false).
        val fillItemsToCancel = fill.items.filter { fillItem ->
            val templateId = fillItem.templateItemId
            templateId != null && templateId in removeIds && fillItem.hasActiveReminder
        }

        val updatedChecklistItems = state.checklist.items.filterNot { it.id in removeIds }
        val updatedChecklist = state.checklist.copy(items = updatedChecklistItems)

        // Drop every fill row whose template link is in the cascade set. Legacy fill rows without a
        // link are never inside a folder (they only surface at root), so they are untouched.
        val updatedFillItems = fill.items.filterNot { fillItem ->
            val templateId = fillItem.templateItemId
            templateId != null && templateId in removeIds
        }
        val updatedFill = fill.copy(items = updatedFillItems)

        // If the level we're standing on is being removed (the current folder, or one of its
        // ancestors, is in the cascade set), leave it SYNCHRONOUSLY — before any state update — so
        // we never render a ghost/broken level (the "error screen" symptom). The parent level's
        // ViewModel re-derives from Room once the writes below land. Otherwise update in place.
        val viewingDeletedSubtree = currentFolderId != null && currentFolderId in removeIds
        if (viewingDeletedSubtree) {
            poppedMissingLevel = true
            updateContentState { it.copy(pendingFolderDeleteId = null, pendingFolderDeleteCount = 0) }
            navigator.onBack()
        } else {
            updateContentState {
                val snapshot = buildFolderState(updatedChecklist, updatedFill)
                it.copy(
                    checklist = updatedChecklist,
                    defaultFill = updatedFill,
                    folders = snapshot.folders,
                    visibleFillItemIds = snapshot.visibleFillItemIds,
                    levelNodes = snapshot.levelNodes,
                    pendingFolderDeleteId = null,
                    pendingFolderDeleteCount = 0,
                )
            }
        }

        viewModelScope.launch {
            // Cancel alarms for every deleted leaf with an active reminder (same calls as the
            // single-item sheet delete), keyed by the leaf's FILL id.
            for (leaf in fillItemsToCancel) {
                reminderScheduler.cancelItemReminder(checklistId, fill.id, leaf.id)
                reminderScheduler.cancelItemRepeat(checklistId, fill.id, leaf.id)
            }
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event(
                "folder_deleted",
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    "cascade_count" to removeIds.size.toString(),
                ),
            )
        }
    }

    /**
     * Opens the reminder sheet for a FOLDER node, reusing the leaf per-item reminder flow.
     *
     * A folder carries a flat fill row (created in [createFolder], linked by
     * [ChecklistFillItem.templateItemId]); its reminder fields live there just like a leaf's. So we
     * resolve the folder's FILL-item id from [folderId] (its TEMPLATE id) and delegate straight to
     * [handleItemReminderClick] — no duplicate sheet/scheduler logic, and save/remove run through
     * the existing [saveItemReminder] / [removeItemReminder] keyed by that fill id.
     *
     * Legacy folders created before reminders existed may lack a fill row; we create it lazily
     * (mirroring [createFolder]) and persist before opening the sheet, so the reminder has somewhere
     * to live. If the folder template node itself is gone (stale UI tap), we surface a snackbar
     * rather than silently doing nothing.
     */
    private fun handleFolderReminderClick(folderId: String) {
        viewModelScope.launch {
            val state = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val fill = state.defaultFill ?: return@launch

            val folder = state.checklist.items.firstOrNull {
                it.id == folderId && it.type == ChecklistNodeType.FOLDER
            }
            if (folder == null) {
                updateContentState {
                    it.copy(
                        folderActionsSheetFor = null,
                        snackbarMessage = SNACKBAR_FOLDER_REMINDER_UNAVAILABLE,
                    )
                }
                return@launch
            }

            val existingRow = fill.items.firstOrNull { it.templateItemId == folderId }
            val folderFillItemId: String = if (existingRow != null) {
                existingRow.id
            } else {
                // Lazily back-fill the folder's fill row (legacy folders) so the reminder fields
                // have a home. Same shape as createFolder's placeholder row.
                val newRow = ChecklistFillItem(
                    text = folder.text,
                    checked = false,
                    note = null,
                    templateItemId = folderId,
                )
                val updatedFill = fill.copy(items = fill.items + newRow)
                updateContentState { it.copy(defaultFill = updatedFill) }
                repository.updateFill(updatedFill)
                newRow.id
            }

            // Close the actions sheet, then reuse the leaf reminder entry point. It re-reads
            // defaultFill (now containing the row) and opens the shared ReminderSheet for this id.
            updateContentState { it.copy(folderActionsSheetFor = null) }
            handleItemReminderClick(folderFillItemId)
        }
    }

    private fun addItemToDay(weekday: Int, text: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Create the template item first so the new fill item is linked to it from birth.
        val newChecklistItem = ChecklistItem(text = trimmed, weekday = weekday)
        val newFillItem = ChecklistFillItem(
            text = trimmed,
            checked = false,
            note = null,
            weekday = weekday,
            templateItemId = newChecklistItem.id,
        )
        val updatedFill = fill.copy(items = fill.items + newFillItem)
        val updatedChecklist = state.checklist.copy(items = state.checklist.items + newChecklistItem)
        updateContentState { it.copy(checklist = updatedChecklist) }

        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event(AnalyticsEvents.Item.WEEKLY_ADDED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
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
                // Match by the stable link; for legacy fill rows fall back to text + current
                // weekday (the pre-link key, kept to disambiguate same-text items across days).
                val matches = if (movedFillItem.templateItemId != null) {
                    templateItem.id == movedFillItem.templateItemId
                } else {
                    templateItem.text == movedFillItem.text && templateItem.weekday == movedFillItem.weekday
                }
                if (matches) {
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
            analyticsTracker.event(AnalyticsEvents.Item.WEEKLY_MOVED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
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
            viewModelScope.launch {
                updateContentState { it.copy(fillNameError = getString(Res.string.fill_error_name_required)) }
            }
            return
        }

        updateContentState { it.copy(isCreatingFill = true, fillNameError = null) }

        viewModelScope.launch {
            val fillItems = state.checklist.items.map { item ->
                ChecklistFillItem(
                    text = item.text,
                    checked = false,
                    note = null,
                    templateItemId = item.id,
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
            analyticsTracker.event(AnalyticsEvents.Checklist.DELETED, mapOf(
                AnalyticsParams.CHECKLIST_ID to state.checklist.id.toString(),
                AnalyticsParams.ITEM_COUNT to (state.defaultFill?.items?.size ?: 0).toString(),
                AnalyticsParams.SOURCE to "overflow_menu"
            ))
            // The whole checklist is gone — pop ALL of its levels (root + any folder drill-down
            // entries) back to the main list. navigator.onBack() only popped one entry, leaving the
            // user on the deleted checklist's parent folder level when invoked from inside a folder.
            navigator.navigateToMainScreen(clearBackStack = true)
        }
    }

    /**
     * Deletes a checklist straight from the [ChecklistDetailState.NotFound] error screen.
     *
     * Unlike [deleteChecklist] this does NOT require a loaded [ChecklistDetailState.Content] —
     * a broken checklist (e.g. a restored row whose default fill never synced) never reaches
     * Content, so its delete action would otherwise be unreachable. We resolve the row by
     * [checklistId] and reuse the normal soft-delete path, so the deletion is marked
     * PENDING_DELETE and later propagates to Firestore as a tombstone; a raw DAO delete here
     * would let the checklist be resurrected on the next pull. If the template row itself is
     * already gone there is nothing to soft-delete — we still cancel alarms and navigate back.
     */
    private fun deleteCorruptedChecklist() {
        loadDataJob?.cancel()
        viewModelScope.launch {
            // Best-effort: a broken row may still have alarms scheduled against its id.
            reminderScheduler.cancelReminder(checklistId)
            reminderScheduler.cancelRepeat(checklistId)

            val checklist = repository.getChecklistById(checklistId)
            if (checklist != null) {
                repository.deleteChecklist(checklist)
            }
            analyticsTracker.event(AnalyticsEvents.Checklist.DELETED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                AnalyticsParams.SOURCE to "not_found_screen",
                // false surfaces the rare "template row already gone" case (possible orphaned
                // fills) without adding an AppLogger dependency to this VM.
                "template_found" to (checklist != null).toString()
            ))
            // Same as deleteChecklist: clear the whole checklist's stack back to the main list.
            navigator.navigateToMainScreen(clearBackStack = true)
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

            // New repeat schedule — check free user limit (RC-driven via UserLimits).
            val limits = awaitUserLimits()
            val activeCount = repository.countActiveRepeatSchedules()
            // limits == null only on a rare 2s RC/DataStore stall — don't block, let the user proceed
            // (mirrors awaitUserLimits' intent of never falsely flipping a user to the gated path).
            val canCreate = limits?.canCreateRecurringReminder(activeCount) ?: true
            if (!canCreate) {
                analyticsTracker.event(AnalyticsEvents.Reminder.RECURRING_LIMIT_HIT)
                navigator.navigateToPaywall(source = "detail_recurring_limit")
            } else {
                updateContentState {
                    it.copy(activeReminderTab = ReminderTab.REPEAT, pendingRepeatConfig = PendingRepeatConfig())
                }
            }
        }
    }

    private fun handleNotificationPermissionResult() {
        reopenReminderSheetAfterPermission()
    }

    private fun handleNotificationPermissionSkip() {
        reopenReminderSheetAfterPermission()
    }

    /**
     * Re-opens the correct reminder sheet after the notification-permission sheet is
     * dismissed (granted or skipped). The permission sheet is shared by the
     * checklist-level flow ([handleReminderClick]) and the per-item flow
     * ([handleItemReminderClick]); only `itemReminderSheetFor` distinguishes them.
     *
     * For the item flow the per-item sheet is already open via `itemReminderSheetFor`,
     * so we must NOT also raise the checklist-level sheet — doing so would stack a second
     * sheet and let a reminder be saved on the whole checklist instead of the item.
     */
    private fun reopenReminderSheetAfterPermission() {
        val isItemFlow = (_screenState.value as? ChecklistDetailState.Content)?.itemReminderSheetFor != null
        updateContentState {
            it.copy(showNotificationPermissionSheet = false, showReminderSheet = !isItemFlow)
        }
        if (!isItemFlow &&
            (_screenState.value as? ChecklistDetailState.Content)?.activeReminderTab == ReminderTab.REPEAT
        ) {
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

            analyticsTracker.event(AnalyticsEvents.Reminder.SET, mapOf(
                AnalyticsParams.CHECKLIST_ID to state.checklist.id.toString()
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
                analyticsTracker.event(AnalyticsEvents.Reminder.RECURRING_CANCELLED, mapOf(
                    AnalyticsParams.CHECKLIST_ID to state.checklist.id.toString(),
                    "total_occurrences" to state.checklist.repeatOccurrenceCount.toString()
                ))
            } else {
                analyticsTracker.event(AnalyticsEvents.Reminder.CANCELLED, mapOf(
                    AnalyticsParams.CHECKLIST_ID to state.checklist.id.toString()
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

            analyticsTracker.event(AnalyticsEvents.Reminder.REPEAT_SCHEDULE_SET, buildMap {
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
            analyticsTracker.event(AnalyticsEvents.Reminder.REPEAT_SCHEDULE_CANCELLED, mapOf(
                AnalyticsParams.CHECKLIST_ID to state.checklist.id.toString(),
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

        // The reorder UI only ever carries the ids VISIBLE at the current level: folder mode filters
        // the list down to a single folder level, and separateCompleted hides checked items. We
        // therefore re-sequence ONLY those ids, each landing back into one of the slots the visible
        // nodes already occupied, and leave every other fill/template node exactly where it is —
        // nodes in sibling folders, off-level nodes, root items (when standing inside a folder), and
        // checked items. Rebuilding the whole fill/template from just the visible slice silently
        // dropped — and synced to every device — all off-level data (the critical data-loss bug).
        //
        // The list is MIXED: a leaf node is identified by its FILL-item id, a folder node by its
        // TEMPLATE id (folders live on the template). These id spaces never collide, so each ordered
        // id resolves to at most one fill row + at most one template node:
        //   • leaf  → fill row = fillItemsById[id];  template = via templateItemId link (text fallback)
        //   • folder→ template = templateById[id];   fill row  = its placeholder row (templateItemId link)
        val fillItemsById = fill.items.associateBy { it.id }
        val fillByTemplateId = fill.items
            .filter { it.templateItemId != null }
            .associateBy { it.templateItemId!! }
        val templateById = state.checklist.items.associateBy { it.id }
        val templateByText = state.checklist.items.associateBy { it.text }

        // Build the ordered fill rows + template nodes that take part in the reorder, in the visible
        // order, each resolved as above. distinctBy{id} guards against any accidental duplicate.
        val orderedFillRows = ArrayList<ChecklistFillItem>(orderedItemIds.size)
        val orderedTemplateNodes = ArrayList<ChecklistItem>(orderedItemIds.size)
        for (id in orderedItemIds) {
            val leafFill = fillItemsById[id]
            if (leafFill != null) {
                // Leaf node (id is a fill-item id).
                orderedFillRows += leafFill
                (leafFill.templateItemId?.let { templateById[it] } ?: templateByText[leafFill.text])
                    ?.let { orderedTemplateNodes += it }
            } else {
                // Folder node (id is a template id). Reposition the folder template node directly and
                // its placeholder fill row (if present — legacy folders may lack one).
                templateById[id]?.let { orderedTemplateNodes += it }
                fillByTemplateId[id]?.let { orderedFillRows += it }
            }
        }

        // Splice the fill rows back into the slots their members already occupied.
        val reorderedFillQueue = ArrayDeque(orderedFillRows.distinctBy { it.id })
        val reorderedFillIds = reorderedFillQueue.map { it.id }.toSet()
        val newFillItems = fill.items.map { item ->
            if (item.id in reorderedFillIds && reorderedFillQueue.isNotEmpty()) {
                reorderedFillQueue.removeFirst()
            } else {
                item
            }
        }
        val updatedFill = fill.copy(items = newFillItems)

        // Splice the template nodes the same way. Only nodes that took part in the reorder are
        // repositioned; all others — off-level nodes, folder subtrees, checked items — keep their
        // original slot, so no template structure (folders or nested items) is lost.
        val reorderedTemplateQueue = ArrayDeque(orderedTemplateNodes.distinctBy { it.id })
        val reorderedTemplateIds = reorderedTemplateQueue.map { it.id }.toSet()
        val newTemplateItems = state.checklist.items.map { templateItem ->
            if (templateItem.id in reorderedTemplateIds && reorderedTemplateQueue.isNotEmpty()) {
                reorderedTemplateQueue.removeFirst()
            } else {
                templateItem
            }
        }
        val updatedChecklist = state.checklist.copy(items = newTemplateItems)
        updateContentState { it.copy(checklist = updatedChecklist) }

        viewModelScope.launch {
            // Persist the reordered fill + template ATOMICALLY (one transaction, one shared
            // updatedAt). Using the dedicated reorderItems() — instead of updateFill() +
            // updateChecklistTemplate() — closes a sync race: those two separate writes (the
            // first of which dirtied the parent checklist with the OLD items before the second)
            // let a triggered push upload the stale half and stamp it newer, whose real-time
            // listener echo then clobbered this just-made local order on the next merge.
            repository.reorderItems(updatedFill, updatedChecklist)
        }
    }

    private fun swipeDeleteItem(itemId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return
        val itemIndex = fill.items.indexOfFirst { it.id == itemId }
        if (itemIndex == -1) return
        val item = fill.items[itemIndex]

        // Locate the template item by the stable link, falling back to text for legacy fill rows.
        val checklistIndex = state.checklist.items.indexOfFirst { templateItem ->
            if (item.templateItemId != null) {
                templateItem.id == item.templateItemId
            } else {
                templateItem.text == item.text
            }
        }

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
            analyticsTracker.event(AnalyticsEvents.Item.DELETED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                "method" to "swipe",
                AnalyticsParams.ITEM_COUNT to updatedFillItems.size.toString()
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

        // The deleted template item is gone, so we recreate it (fresh id). If a template item
        // is recreated, re-link the restored fill item to that new id so the pair stays
        // connected — otherwise the fill item would keep pointing at the deleted template id and
        // become an orphan on the next updateChecklist() reconcile.
        val recreatedTemplateItem = if (undo.originalChecklistIndex >= 0) {
            ChecklistItem(text = undo.checklistItemText)
        } else {
            null
        }
        val restoredFillItem = if (recreatedTemplateItem != null) {
            undo.fillItem.withTemplateItemId(recreatedTemplateItem.id)
        } else {
            undo.fillItem
        }

        val restoredFillItems = fill.items.toMutableList().apply {
            add(undo.originalFillIndex.coerceAtMost(size), restoredFillItem)
        }
        val restoredFill = fill.copy(items = restoredFillItems)

        val restoredChecklistItems = state.checklist.items.toMutableList().apply {
            if (recreatedTemplateItem != null) {
                add(undo.originalChecklistIndex.coerceAtMost(size), recreatedTemplateItem)
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
            analyticsTracker.event(AnalyticsEvents.Item.UNDO_DELETE, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString()
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

        // Drop the linked template item — match by the stable link, falling back to text for
        // legacy fill rows. Text fallback would also remove a same-text sibling; the link avoids that.
        val updatedChecklistItems = state.checklist.items.filterNot { templateItem ->
            if (item.templateItemId != null) {
                templateItem.id == item.templateItemId
            } else {
                templateItem.text == item.text
            }
        }
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
            analyticsTracker.event(AnalyticsEvents.Item.DELETED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                "method" to "sheet",
                AnalyticsParams.ITEM_COUNT to updatedFillItems.size.toString()
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

            analyticsTracker.event(AnalyticsEvents.Reminder.ITEM_SET, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
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

            analyticsTracker.event(AnalyticsEvents.Reminder.ITEM_REMOVED, mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString()
            ))
        }
    }

    // ─── Priority handler ──────────────────────────────────────────────────

    /**
     * Toggles the priority of a fill item between 0 (normal) and 1 (starred).
     *
     * Delegates to [ChecklistRepository.togglePriority] which performs the dual
     * update (fill + template) atomically. On failure the error is logged and the
     * UI is left unchanged — no destructive state change on error (same pattern
     * as per-item reminders).
     *
     * The sheet is kept open so the user sees the icon update immediately via
     * the Flow-driven state refresh from Room.
     */
    private fun toggleItemPriority(itemId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val fill = state.defaultFill ?: return

        viewModelScope.launch {
            val result = repository.togglePriority(fill.id, itemId)
            result.onFailure { e ->
                analyticsTracker.event(
                    "item_priority_toggle_error",
                    mapOf("item_id" to itemId, "error" to (e.message ?: "unknown"))
                )
            }
        }
    }

    private fun startItemTextEdit(itemId: String) {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val item = state.defaultFill?.items?.firstOrNull { it.id == itemId } ?: return
        updateContentState {
            it.copy(
                editingItemTextFor = itemId,
                editingItemTextDraft = item.text
            )
        }
    }

    private fun updateItemTextDraft(text: String) {
        updateContentState { it.copy(editingItemTextDraft = text) }
    }

    private fun cancelItemTextEdit() {
        updateContentState { it.copy(editingItemTextFor = null, editingItemTextDraft = "") }
    }

    private fun confirmItemTextEdit() {
        val state = _screenState.value as? ChecklistDetailState.Content ?: return
        val itemId = state.editingItemTextFor ?: return  // double-fire guard
        val newText = state.editingItemTextDraft.trim()
        val fill = state.defaultFill ?: return

        // Blank text — cancel, not commit
        if (newText.isBlank()) {
            cancelItemTextEdit()
            return
        }

        val currentItem = fill.items.firstOrNull { it.id == itemId }
        if (currentItem == null || currentItem.text == newText) {
            // No-op — just close edit mode
            cancelItemTextEdit()
            return
        }

        val updatedFillItems = fill.items.map { item ->
            if (item.id == itemId) item.withText(newText) else item
        }
        val updatedFill = fill.copy(items = updatedFillItems)

        // Match the template item by the stable link first (text is changing here, so a
        // text-keyed match would fail or mis-target a same-text sibling), falling back to the
        // old text for legacy fill rows that predate the link.
        val oldText = currentItem.text
        val updatedTemplateItems = state.checklist.items.map { templateItem ->
            val matches = if (currentItem.templateItemId != null) {
                templateItem.id == currentItem.templateItemId
            } else {
                templateItem.text == oldText
            }
            if (matches) templateItem.withText(newText) else templateItem
        }
        val updatedChecklist = state.checklist.copy(items = updatedTemplateItems)

        // Optimistic state update
        updateContentState {
            it.copy(
                defaultFill = updatedFill,
                checklist = updatedChecklist,
                editingItemTextFor = null,
                editingItemTextDraft = ""
            )
        }

        // Persist
        viewModelScope.launch {
            repository.updateFill(updatedFill)
            repository.updateChecklistTemplate(updatedChecklist)
            analyticsTracker.event(
                "item_text_edited",
                mapOf("checklist_id" to checklistId.toString())
            )
        }
    }

    // ── Attachment handlers ──────────────────────────────────────────────────────

    private fun handleAddAttachment(itemId: String, isImage: Boolean) {
        logger.debug(TAG, "addAttachment: itemId=$itemId isImage=$isImage")
        val content = _screenState.value as? ChecklistDetailState.Content ?: run {
            logger.warning(TAG, "addAttachment: no Content state — bail")
            return
        }
        val item = content.defaultFill?.items?.firstOrNull { it.id == itemId } ?: run {
            logger.warning(TAG, "addAttachment: item $itemId not in defaultFill — bail")
            return
        }
        val limits = content.userLimits
        val blocked = if (limits != null) {
            !limits.canAddAttachment(item.attachments.size)
        } else {
            // userLimits not loaded yet — fall back to the static free-tier default.
            item.attachments.size >= FREE_ATTACHMENT_LIMIT_PER_ITEM
        }
        if (blocked) {
            logger.warning(
                TAG,
                "addAttachment: blocked by limit (size=${item.attachments.size}, " +
                    "limits=${limits?.maxAttachmentsPerItem}, premium=${limits?.isPremium})",
            )
            updateContentState { it.copy(snackbarMessage = SNACKBAR_ATTACHMENT_PREMIUM_LIMIT) }
            return
        }
        logger.debug(TAG, "addAttachment: launching picker for $itemId")
        updateContentState {
            it.copy(
                pendingAttachmentItemId = itemId,
                triggerImagePicker = isImage,
                triggerFilePicker = !isImage,
            )
        }
    }

    private fun handleAttachmentPicked(intent: ChecklistDetailIntent.OnAttachmentPicked) =
        viewModelScope.launch {
            logger.debug(
                TAG,
                "picked: itemId=${intent.itemId} source=${intent.sourcePath} " +
                    "name=${intent.fileName} mime=${intent.mimeType}",
            )
            val content = _screenState.value as? ChecklistDetailState.Content ?: run {
                logger.warning(TAG, "picked: no Content state — bail")
                return@launch
            }
            val fillId = content.defaultFill?.id ?: run {
                logger.warning(TAG, "picked: defaultFill is null — bail (itemId=${intent.itemId})")
                return@launch
            }

            val attachmentId = Attachment.generateId()
            val storedPath = attachmentStorage.storeAttachment(
                sourcePath = intent.sourcePath,
                fillId = fillId,
                itemId = intent.itemId,
                attachmentId = attachmentId,
                originalFileName = intent.fileName,
            )
            logger.debug(TAG, "picked: storeAttachment -> ${storedPath ?: "NULL"}")
            if (storedPath == null) {
                logger.warning(TAG, "picked: storeAttachment returned null — load error")
                updateContentState {
                    it.copy(
                        pendingAttachmentItemId = null,
                        snackbarMessage = SNACKBAR_ATTACHMENT_LOAD_ERROR,
                    )
                }
                return@launch
            }

            val sizeBytes = attachmentStorage.sizeOf(storedPath)
            logger.debug(TAG, "picked: size=$sizeBytes")
            if (sizeBytes > MAX_ATTACHMENT_SIZE_BYTES) {
                logger.warning(TAG, "picked: too large $sizeBytes > $MAX_ATTACHMENT_SIZE_BYTES")
                attachmentStorage.deleteAttachment(storedPath)
                updateContentState {
                    it.copy(
                        pendingAttachmentItemId = null,
                        snackbarMessage = SNACKBAR_ATTACHMENT_TOO_LARGE,
                    )
                }
                return@launch
            }

            val (w, h) = attachmentStorage.probeImage(storedPath, intent.mimeType)
            val attachment = Attachment(
                id = attachmentId,
                path = storedPath,
                fileName = intent.fileName,
                mimeType = intent.mimeType,
                sizeBytes = sizeBytes,
                createdAt = currentTimeMillis(),
                width = w,
                height = h,
            )
            repository.addAttachment(fillId, intent.itemId, attachment)
            logger.debug(
                TAG,
                "picked: addAttachment done fill=$fillId item=${intent.itemId} att=$attachmentId",
            )
            updateContentState { it.copy(pendingAttachmentItemId = null) }
            // Repository emits a Flow update; combine() in loadData() picks up the new fill state.
        }

    private fun handleOpenViewer(attachmentId: String) {
        val content = _screenState.value as? ChecklistDetailState.Content ?: return
        val item = content.defaultFill?.items?.firstOrNull { fillItem ->
            fillItem.attachments.any { it.id == attachmentId }
        } ?: return
        updateContentState {
            it.copy(
                attachmentViewerState = AttachmentViewerState(
                    itemId = item.id,
                    initialAttachmentId = attachmentId,
                )
            )
        }
    }

    private fun handleDeleteAttachment(itemId: String, attachmentId: String) =
        viewModelScope.launch {
            val content = _screenState.value as? ChecklistDetailState.Content ?: return@launch
            val fillId = content.defaultFill?.id ?: return@launch
            // Check whether the viewer was showing this item's last attachment — close it
            // proactively so the UI doesn't flash an empty viewer while waiting for the
            // Flow update from the repository.
            val item = content.defaultFill.items.firstOrNull { it.id == itemId }
            val wasLastAttachment = item != null && item.attachments.size <= 1
            if (wasLastAttachment) {
                updateContentState { it.copy(attachmentViewerState = null) }
            }
            repository.removeAttachment(fillId, itemId, attachmentId)
            // Repository handles file deletion and DB persistence; Flow update follows.
            updateContentState { it.copy(snackbarMessage = SNACKBAR_ATTACHMENT_DELETED) }
        }

    private fun handleOpenExternally(attachmentId: String) {
        val attachment = (_screenState.value as? ChecklistDetailState.Content)
            ?.defaultFill?.items?.flatMap { it.attachments }
            ?.firstOrNull { it.id == attachmentId }
            ?: return
        updateContentState {
            it.copy(
                pendingOpenExternallyPath = attachment.path,
                pendingOpenExternallyMimeType = attachment.mimeType,
            )
        }
    }

    // ── Shared helpers ───────────────────────────────────────────────────────────

    private inline fun updateContentState(update: (ChecklistDetailState.Content) -> ChecklistDetailState.Content) {
        _screenState.update { state ->
            if (state is ChecklistDetailState.Content) update(state) else state
        }
    }

    companion object {
        /** Log tag for the attachment add + display path (diagnostic observability). */
        private const val TAG = "Attachments"

        const val PREF_EXACT_ALARM_DONT_SHOW = "exact_alarm_dont_show"
        const val SNACKBAR_EXACT_GRANTED = "exact_alarm_granted"
        const val SNACKBAR_EXACT_DENIED = "exact_alarm_denied"
        /**
         * Smart Add hint: user typed only the trigger phrase (no item text), or all non-trigger
         * text was stripped to blank. Tell them to add actual task text.
         */
        const val SNACKBAR_SMART_ADD_HINT_ADD_TEXT = "smart_add_hint_add_text"
        /**
         * Smart Add hint: after stripping the trigger phrase, a lone time-preposition remains
         * (e.g. "в" / "at" — mirrors RuDateLexicon.timePrepositions + EnDateLexicon.timePrepositions).
         * The user likely started typing a time but did not finish.
         */
        const val SNACKBAR_SMART_ADD_HINT_ADD_TIME = "smart_add_hint_add_time"
        /**
         * Stranded time-preposition tokens that trigger the "finish the time" hint.
         * Mirrors RuDateLexicon.timePrepositions + EnDateLexicon.timePrepositions; kept here
         * because those objects are `internal` to feature:checklist.
         */
        val STRANDED_TIME_PREPOSITIONS = setOf("в", "во", "at")

        // ── Attachment limits and snackbar keys ──────────────────────────────
        /**
         * Fallback max attachments per item for free-tier users, used only when userLimits is
         * not yet loaded. Source of truth = RC max_attachments_per_item_free via UserLimits.
         */
        const val FREE_ATTACHMENT_LIMIT_PER_ITEM = 3

        /** Max file size accepted during store (10 MB). Oversized files are deleted immediately. */
        const val MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024L * 1024L

        /** Free-tier limit reached: upgrade prompt. Resolved to a string in the Composable. */
        const val SNACKBAR_ATTACHMENT_PREMIUM_LIMIT = "attachment_premium_limit"

        /** File could not be copied to storage (content:// read error, disk full, etc.). */
        const val SNACKBAR_ATTACHMENT_LOAD_ERROR = "attachment_load_error"

        /** File exceeds MAX_ATTACHMENT_SIZE_BYTES. */
        const val SNACKBAR_ATTACHMENT_TOO_LARGE = "attachment_too_large"

        /** Attachment was successfully deleted (used as confirmation snackbar). */
        const val SNACKBAR_ATTACHMENT_DELETED = "attachment_deleted"

        /**
         * The folder a reminder was requested for no longer exists (stale UI tap after it was
         * deleted/moved elsewhere). Surfaced instead of silently doing nothing.
         */
        const val SNACKBAR_FOLDER_REMINDER_UNAVAILABLE = "folder_reminder_unavailable"

        /**
         * No app handled the calendar ACTION_INSERT (or the Web popup was blocked) — the launcher
         * returned false. Resolved to
         * [aichecklists.core.designsystem.generated.resources.Res.string.calendar_app_not_found].
         */
        const val SNACKBAR_CALENDAR_APP_NOT_FOUND = "calendar_app_not_found"
    }
}
