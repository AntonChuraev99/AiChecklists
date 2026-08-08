package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.attachment_deleted_snackbar
import aichecklists.core.designsystem.generated.resources.attachment_load_error
import aichecklists.core.designsystem.generated.resources.attachment_premium_limit_reached_snackbar
import aichecklists.core.designsystem.generated.resources.attachment_size_too_large_snackbar
import aichecklists.core.designsystem.generated.resources.calendar_app_not_found
import aichecklists.core.designsystem.generated.resources.error_create_checklist_failed
import aichecklists.core.designsystem.generated.resources.error_save_failed
import aichecklists.core.designsystem.generated.resources.inbox_task_update_failed
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
import aichecklists.core.designsystem.generated.resources.fill_error_name_required
import aichecklists.core.designsystem.generated.resources.inbox_checklist_delete_failed
import aichecklists.core.designsystem.generated.resources.inbox_checklist_deleted_message
import aichecklists.core.designsystem.generated.resources.inbox_checklist_rename_failed
import aichecklists.core.designsystem.generated.resources.inbox_reminder_permission_denied
import aichecklists.core.designsystem.generated.resources.inbox_reminder_time_in_past
import aichecklists.core.designsystem.generated.resources.inbox_task_add_failed
import aichecklists.core.designsystem.generated.resources.inbox_task_delete_failed
import aichecklists.core.designsystem.generated.resources.inbox_task_deleted_message
import aichecklists.core.designsystem.generated.resources.inbox_task_move_failed
import aichecklists.core.designsystem.generated.resources.inbox_task_moved_message
import aichecklists.core.designsystem.generated.resources.main_error_description
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayOptions
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxDisplayPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.InboxSort
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.buildCalendarEvent
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.ItemCreateReminderPreset
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.TaskDraft
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.cleared
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.resolveReminderAtNow
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.withImportantToggled
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.withPreset
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.resolveAttachmentLocalPath
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "InboxViewModel"

/**
 * Backs the v2 Inbox tab — the treatment arm's quick-capture home.
 *
 * Two responsibilities that do not exist anywhere else in the app:
 * 1. Guarantee the system Inbox row exists before anything tries to capture into it
 *    ([EnsureInboxUseCase]) — the screen must work on a first launch with no user action.
 * 2. Present the Inbox AND every project as one swipeable pager, so "capture now, sort later" and
 *    "add straight to a project" are the same gesture.
 *
 * Every write goes through the template+fill PAIR (see `.claude/rules/checklist-domain.md`): items
 * live inside `Checklist.items` (the template, read by the edit screen) and are mirrored into the
 * default `ChecklistFill` (read by the detail screen). Writing only one side is the recurring desync
 * bug in this repo — a move is therefore FOUR writes inside ONE coroutine, never two coroutines,
 * or one half syncs to Firestore and the other does not and the task duplicates across devices.
 */
class InboxViewModel(
    private val repository: ChecklistRepository,
    private val ensureInbox: EnsureInboxUseCase,
    // Checking a task must cancel that task's alarm here exactly as it does on the detail screen —
    // otherwise a reminder still fires for a task the user completed in the Inbox.
    private val reminderScheduler: ChecklistReminderScheduler,
    private val displayPrefs: InboxDisplayPrefsRepository,
    private val navigator: AppNavigator,
    private val analytics: AnalyticsTracker,
    // The item sheet shown here is the detail screen's own `ItemDetailsSheet`, so this screen needs
    // the same three collaborators that sheet's rows write through: the premium gate behind the
    // reminder / attachment quotas, the attachment file store, and the calendar export.
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val attachmentStorage: AttachmentStoragePort,
    private val calendarEventLauncher: CalendarEventLauncher,
    private val logger: AppLogger,
) : AppViewModel<InboxScreenState, InboxIntent, InboxSideEffect>() {

    /**
     * null = no pager yet: either still loading OR the system Inbox row does not exist (see
     * [observePages]). A non-null value ALWAYS starts with the Inbox page, so it is never empty.
     */
    private val _pages = MutableStateFlow<List<InboxPage>?>(null)
    private val _selectedPage = MutableStateFlow(0)
    private val _draft = MutableStateFlow(TaskDraft())

    /**
     * Everything about the open item sheet and the surfaces it raises, held as ONE value.
     *
     * The v2 sheet used to be two booleans (`sheetForTaskId` + `movePickerOpen`); the v1 sheet it was
     * replaced with opens five more surfaces (reminder, note, custom picker, permission sheet,
     * attachment viewer) and every one of them is meaningless while no sheet is open. Sibling flows
     * would make "reminder tab selected with no sheet" representable and reachable through any missed
     * transition — the same reason [ListMenuState] is grouped.
     */
    private data class ItemSheetState(
        val taskId: String? = null,
        val movePickerOpen: Boolean = false,
        val textEditing: Boolean = false,
        val textDraft: String = "",
        val noteDraft: String? = null,
        val reminder: InboxItemReminderUi? = null,
        val customPicker: InboxCustomPickerUi? = null,
        val notificationPermissionOpen: Boolean = false,
        val attachmentViewerFor: String? = null,
        val pendingAttachmentTaskId: String? = null,
        val triggerImagePicker: Boolean = false,
        val triggerFilePicker: Boolean = false,
    )

    private val _itemSheet = MutableStateFlow(ItemSheetState())

    /**
     * Latest premium/limits snapshot. Its own flow rather than a field inside [_itemSheet] so a write
     * path can read the CURRENT value synchronously (`_userLimits.value`) without going through the
     * combined screen state, which is what the detail screen learned to do after its first emission
     * kept racing the Loading → Content transition.
     */
    private val _userLimits = MutableStateFlow<UserLimits?>(null)

    /**
     * The toolbar overflow's three mutually-exclusive surfaces, held as ONE value.
     *
     * Grouped rather than kept as three sibling flows for two reasons. Kotlin's typed `combine` tops
     * out at five sources, and — more importantly — these three can never be open at once: the menu
     * closes when it launches a dialog. Three independent booleans make the illegal state
     * ("menu and delete dialog both up") representable and reachable through any missed transition.
     */
    private data class ListMenuState(
        val menuOpen: Boolean = false,
        val renameDraft: String? = null,
        val deleteConfirmationOpen: Boolean = false,
    )

    private val _listMenu = MutableStateFlow(ListMenuState())
    private val _displayOptionsOpen = MutableStateFlow(false)

    private val _sideEffect = MutableSharedFlow<InboxSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<InboxSideEffect> = _sideEffect.asSharedFlow()

    /**
     * Display options applied to the pager's pages, plus the open/closed state of the sheet that
     * edits them. Combined here so the sorted/filtered pages and the sheet share one source.
     */
    private val displayState = combine(
        displayPrefs.observeDisplayOptions(),
        _displayOptionsOpen,
    ) { options, sheetOpen -> options to sheetOpen }

    override val screenState: StateFlow<InboxScreenState> = combine(
        // Pages arrive from the repository in TEMPLATE order; the display options are applied here,
        // at the edge, so every consumer downstream (the count in the toolbar, the task sheet, the
        // move targets) sees exactly what the list shows. Sorting inside the list composable instead
        // would make the toolbar count disagree with the visible rows whenever completed tasks are
        // hidden.
        combine(_pages, displayState) { pages, (options, sheetOpen) ->
            Triple(pages?.map { it.applyDisplayOptions(options) }, options, sheetOpen)
        },
        _selectedPage,
        _draft,
        // Paired so the whole state still fits typed `combine`'s five-source ceiling; the sheet and
        // the limits that gate two of its rows belong to one interaction, so pairing costs no clarity.
        combine(_itemSheet, _userLimits) { sheet, limits -> sheet to limits },
        _listMenu,
    ) { (pages, displayOptions, displayOptionsOpen), selected, draft, (itemSheet, userLimits), listMenu ->
        if (pages == null) {
            InboxScreenState.Loading
        } else {
            // The pager can shrink under us (a project deleted on another device), so the stored
            // index is clamped here rather than trusted — an out-of-range page would blank the tab
            // row and silently retarget quick-add at nothing.
            val safeSelected = selected.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            val current = pages.getOrNull(safeSelected)
            InboxScreenState.Content(
                pages = pages,
                selectedPage = safeSelected,
                draft = draft,
                sheetForTaskId = itemSheet.taskId,
                movePickerOpen = itemSheet.movePickerOpen,
                moveTargets = pages.filter { !it.isInbox && it.checklistId != current?.checklistId },
                sheetTextEditing = itemSheet.textEditing,
                sheetTextDraft = itemSheet.textDraft,
                noteDraft = itemSheet.noteDraft,
                reminderSheet = itemSheet.reminder,
                customPicker = itemSheet.customPicker,
                notificationPermissionOpen = itemSheet.notificationPermissionOpen,
                attachmentViewerFor = itemSheet.attachmentViewerFor,
                triggerImagePicker = itemSheet.triggerImagePicker,
                triggerFilePicker = itemSheet.triggerFilePicker,
                pendingAttachmentTaskId = itemSheet.pendingAttachmentTaskId,
                isPremium = userLimits?.isPremium == true,
                maxAttachmentsPerItem = userLimits?.maxAttachmentsPerItem
                    ?: InboxScreenState.FREE_ATTACHMENTS_FALLBACK,
                // Forced shut on the Inbox page. The system Inbox cannot be renamed or deleted, so
                // every entry of this menu would be inert there — and the page can change under an
                // OPEN menu (a swipe, or a project deleted on another device shifting the pager),
                // which would otherwise leave a live "Delete checklist" pointed at the Inbox.
                listMenuOpen = listMenu.menuOpen && current?.isInbox == false,
                renameDraft = listMenu.renameDraft.takeIf { current?.isInbox == false },
                deleteConfirmationOpen = listMenu.deleteConfirmationOpen && current?.isInbox == false,
                displayOptions = displayOptions,
                displayOptionsOpen = displayOptionsOpen,
            )
        }
    }.defaultStateIn(InboxScreenState.Loading)

    init {
        viewModelScope.launch {
            // getString is suspend and the DOMAIN layer must never touch Compose Resources, so the
            // Inbox title is resolved here and passed down. A literal in the use case would ship
            // one language to every user.
            if (ensureInbox(getString(Res.string.inbox_checklist_name)) == null) {
                // EnsureInboxUseCase already logged the cause; its contract explicitly hands the
                // user-facing message to the caller. Without the row the pager never leaves Loading
                // (see observePages), so this snackbar is the ONLY thing that tells the user why the
                // tab stays empty — dropping it would turn a failed write into a silent spinner.
                emitMessage(Res.string.error_create_checklist_failed)
            }
        }
        observePages()
        observeUserLimits()
    }

    /**
     * Keeps [_userLimits] fresh for the two sheet rows that are quota-gated (reminder, attachments).
     *
     * `catch` rather than an unguarded collect: this flow reads Remote Config, the paywall SDK and the
     * checklist count, so it can fail on a cold start with no network. A crash there would take the
     * whole home tab down over an OPTIONAL gate, and a silent stop would leave every gate reading
     * "free forever" with no trace — so the failure is logged and the last known value kept.
     */
    private fun observeUserLimits() {
        viewModelScope.launch {
            getUserLimitsUseCase()
                .catch { e -> logger.error(TAG, "user limits stream failed: ${e.message}", e) }
                .collect { limits -> _userLimits.value = limits }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePages() {
        // Explicit nullable element type so both flatMapLatest branches coerce to it: null = "no
        // Inbox row", a list = the pager's pages with the Inbox at index 0.
        val pagesFlow: Flow<List<InboxPage>?> = combine(
            repository.observeInbox(),
            repository.projects,
        ) { inbox, projects ->
            // NOT `listOfNotNull(inbox) + projects`: that promotes a real PROJECT into slot 0
            // whenever the Inbox row is absent — the frames before ensureInbox commits it, and
            // PERMANENTLY if the insert failed. Everything downstream trusts pages[0] to be the
            // Inbox (tab label, quick-add target, the source="inbox" analytics param, the
            // move-target filter), so a capture the user believes lands in the Inbox would silently
            // land in their first project. No Inbox row → no Content at all.
            inbox?.let { listOf(it) + projects }
        }.flatMapLatest { lists ->
            if (lists == null) {
                flowOf<List<InboxPage>?>(null)
            } else {
                // Same shape as MainScreenViewModel.checklistsWithProgress: one child flow per
                // checklist so a single fill edit re-emits only its own page. `lists` always holds
                // at least the Inbox, so combine() always has a source (an empty combine never
                // emits at all).
                combine(
                    lists.map { checklist ->
                        repository.getDefaultFillByChecklistId(checklist.id)
                            .map { fill -> checklist.toPage(fill) }
                    }
                ) { pages -> pages.toList() }
            }
        }

        viewModelScope.launch {
            pagesFlow.catch { e ->
                logger.error(TAG, "inbox pages stream failed: ${e.message}", e)
                // null, not emptyList: an empty Content would claim "your inbox is empty" (a lie)
                // and break the pages[0]-is-the-Inbox invariant. The screen therefore keeps its
                // spinner, which is exactly why the failure also has to reach the snackbar.
                emit(null)
                emitMessage(Res.string.main_error_description)
            }.collect { pages ->
                _pages.value = pages
            }
        }
    }

    override fun onIntent(intent: InboxIntent) {
        when (intent) {
            is InboxIntent.OnPageSelected -> _selectedPage.value = intent.index
            is InboxIntent.OnQuickAddTextChanged ->
                _draft.value = _draft.value.copy(text = intent.text)
            is InboxIntent.OnCreateChipAction -> applyCreateChip(intent.action)
            InboxIntent.OnQuickAddSubmit -> addTask()

            // Analytics ONLY — raising the dock is the host's job (see InboxRoute).
            //
            // The emit moved here from the shell's "+" FAB handler, which is being deleted along with
            // the FAB. Same EVENT name on purpose: `nav_create_fab_tapped` counts "the user reached
            // for the manual create affordance", and renaming it when that affordance changed shape
            // would split one series into two that cannot be summed. Only the SOURCE moves, from
            // "fab" to "inline_row" — which is exactly what that param was added for.
            InboxIntent.OnAddTaskRowClick -> analytics.event(
                AnalyticsEvents.Nav.CREATE_FAB_TAPPED,
                mapOf(AnalyticsParams.SOURCE to SOURCE_INLINE_ROW),
            )
            is InboxIntent.OnTaskCheckedChanged -> setTaskChecked(intent.taskId, intent.checked)
            // A fresh ItemSheetState, not a copy: every sub-surface (note draft, reminder tab, custom
            // picker) belongs to the PREVIOUS task, and carrying one over would show the last task's
            // note draft over this one.
            is InboxIntent.OnTaskDetailsClick -> _itemSheet.value = ItemSheetState(taskId = intent.taskId)
            InboxIntent.OnTaskSheetDismiss -> closeSheets()
            InboxIntent.OnMovePickerOpen -> _itemSheet.update { it.copy(movePickerOpen = true) }
            InboxIntent.OnMovePickerDismiss -> _itemSheet.update { it.copy(movePickerOpen = false) }
            is InboxIntent.OnMoveToProject -> moveTask(intent.targetChecklistId)
            InboxIntent.OnToggleImportant -> toggleImportant()
            InboxIntent.OnDeleteTask -> deleteTask()

            // ── Item sheet: inline rename ────────────────────────────────────────────────
            InboxIntent.OnTaskTextEditStart -> startTaskTextEdit()
            is InboxIntent.OnTaskTextDraftChanged -> _itemSheet.update { it.copy(textDraft = intent.text) }
            InboxIntent.OnTaskTextEditConfirm -> confirmTaskTextEdit()

            // ── Item sheet: note ─────────────────────────────────────────────────────────
            InboxIntent.OnTaskNoteClick -> openNoteDialog()
            is InboxIntent.OnTaskNoteDraftChanged -> _itemSheet.update { it.copy(noteDraft = intent.text) }
            InboxIntent.OnTaskNoteSave -> saveNote()
            InboxIntent.OnTaskNoteDismiss -> _itemSheet.update { it.copy(noteDraft = null) }

            // ── Item sheet: reminder / repeat ────────────────────────────────────────────
            InboxIntent.OnTaskReminderClick -> openReminderSheet()
            is InboxIntent.OnReminderTabSelected -> selectReminderTab(intent.tab)
            is InboxIntent.OnReminderPresetSelected -> {
                // Guarded exactly like the detail screen: a preset resolved before the sheet was
                // opened can already be in the past by the time it is tapped, and AlarmManager fires
                // a past trigger immediately — a reminder that "rings" the instant you set it.
                if (intent.triggerAtMillis <= Clock.System.now().toEpochMilliseconds()) {
                    logger.warning(TAG, "reminder preset ${intent.triggerAtMillis} is in the past — ignored")
                } else {
                    saveItemReminder(intent.triggerAtMillis, repeatRule = null, repeatTimeOfDayMinutes = null)
                }
            }

            InboxIntent.OnReminderRemove, InboxIntent.OnRepeatRemove -> removeItemReminder()
            InboxIntent.OnReminderSheetDismiss -> _itemSheet.update {
                it.copy(reminder = null)
            }

            is InboxIntent.OnReminderFullScreenToggled -> _itemSheet.update {
                it.copy(reminder = it.reminder?.copy(fullScreen = intent.enabled))
            }

            InboxIntent.OnReminderAddToCalendar -> addOpenTaskToCalendar()
            InboxIntent.OnReminderUpgradeClick -> {
                closeSheets()
                navigator.navigateToPaywall(source = PAYWALL_SOURCE_ITEM_REMINDER)
            }

            is InboxIntent.OnRepeatTypeSelected -> selectRepeatType(intent.type)
            is InboxIntent.OnSmartPresetSelected -> updateRepeatConfig { intent.config }
            is InboxIntent.OnRepeatIntervalChanged -> updateRepeatConfig {
                it.copy(interval = intent.interval.coerceIn(1, 99), isCustom = true)
            }

            is InboxIntent.OnWeekDayToggled -> updateRepeatConfig { config ->
                val days = config.weekDays.toMutableSet()
                if (!days.add(intent.dayNumber)) days.remove(intent.dayNumber)
                config.copy(weekDays = days, isCustom = true)
            }

            is InboxIntent.OnResetChecksToggled -> updateRepeatConfig { it.copy(resetChecks = intent.enabled) }
            is InboxIntent.OnRepeatTimeChanged -> updateRepeatConfig {
                it.copy(timeHour = intent.hour, timeMinute = intent.minute)
            }

            InboxIntent.OnEndConditionClick -> _itemSheet.update {
                it.copy(reminder = it.reminder?.copy(showEndConditionPicker = true))
            }

            is InboxIntent.OnEndConditionSelected -> {
                updateRepeatConfig { it.copy(endCondition = intent.condition) }
                _itemSheet.update { it.copy(reminder = it.reminder?.copy(showEndConditionPicker = false)) }
            }

            InboxIntent.OnEndConditionDismiss -> _itemSheet.update {
                it.copy(reminder = it.reminder?.copy(showEndConditionPicker = false))
            }

            InboxIntent.OnRepeatSave -> saveRepeatSchedule()

            // ── Item sheet: custom date/time picker ──────────────────────────────────────
            InboxIntent.OnCustomDateRequested -> openCustomPicker()
            is InboxIntent.OnCustomDateSelected -> selectCustomDate(intent.dateMillis)
            is InboxIntent.OnCustomTimeChanged -> updateCustomTimeInPast(intent.hour, intent.minute)
            is InboxIntent.OnCustomTimeSelected -> commitCustomDateTime(intent.hour, intent.minute)
            InboxIntent.OnCustomPickerDismiss -> _itemSheet.update { it.copy(customPicker = null) }

            // ── Item sheet: notification permission ──────────────────────────────────────
            is InboxIntent.OnNotificationPermissionResult -> {
                _itemSheet.update { it.copy(notificationPermissionOpen = false) }
                if (!intent.granted) {
                    // Not a silent close: without the permission every alarm this sheet schedules is
                    // posted and dropped by the system, so the user has to be told the bell will not
                    // ring rather than left believing the reminder is armed.
                    logger.warning(TAG, "notification permission denied — item reminders will not be shown")
                    emitMessage(Res.string.inbox_reminder_permission_denied)
                }
            }

            InboxIntent.OnNotificationPermissionSkip -> {
                _itemSheet.update { it.copy(notificationPermissionOpen = false) }
                emitMessage(Res.string.inbox_reminder_permission_denied)
            }

            // ── Item sheet: attachments ──────────────────────────────────────────────────
            InboxIntent.OnAddImageAttachment -> requestAttachment(isImage = true)
            InboxIntent.OnAddFileAttachment -> requestAttachment(isImage = false)
            InboxIntent.OnImagePickerLaunched -> _itemSheet.update { it.copy(triggerImagePicker = false) }
            InboxIntent.OnFilePickerLaunched -> _itemSheet.update { it.copy(triggerFilePicker = false) }
            is InboxIntent.OnAttachmentPicked -> storePickedAttachment(intent)
            is InboxIntent.OnAttachmentClick -> _itemSheet.update {
                it.copy(attachmentViewerFor = intent.attachmentId)
            }

            InboxIntent.OnAttachmentViewerClose -> _itemSheet.update { it.copy(attachmentViewerFor = null) }
            is InboxIntent.OnAttachmentDelete -> deleteAttachment(intent.attachmentId)
            is InboxIntent.OnAttachmentOpenExternally -> openAttachmentExternally(intent.attachmentId)
            is InboxIntent.OnOpenProject -> {
                closeSheets()
                _listMenu.value = ListMenuState()
                navigator.navigateToChecklistDetail(intent.checklistId)
            }

            InboxIntent.OnDisplayOptionsClick -> _displayOptionsOpen.value = true
            InboxIntent.OnDisplayOptionsDismiss -> _displayOptionsOpen.value = false

            // Each write goes straight to DataStore and comes back through observeDisplayOptions —
            // the sheet has no local copy to fall out of sync with, and the list re-renders under the
            // open sheet so the change is visible while choosing.
            is InboxIntent.OnLayoutSelected -> persistDisplayOption("layout=${intent.layout}") {
                displayPrefs.setLayout(intent.layout)
            }

            is InboxIntent.OnSortSelected -> persistDisplayOption("sort=${intent.sort}") {
                displayPrefs.setSort(intent.sort)
            }

            is InboxIntent.OnShowCompletedChanged -> persistDisplayOption("showCompleted=${intent.show}") {
                displayPrefs.setShowCompleted(intent.show)
            }

            InboxIntent.OnListMenuOpen -> _listMenu.value = ListMenuState(menuOpen = true)
            InboxIntent.OnListMenuDismiss -> _listMenu.value = ListMenuState()

            InboxIntent.OnOpenCurrentChecklist -> {
                val page = currentPage()
                if (page == null) {
                    // Cannot happen while the menu is reachable (it only renders over a page), but a
                    // silent return here would be a dead menu row — log it rather than swallow it.
                    logger.warning(TAG, "open-current skipped: no settled page")
                } else {
                    closeSheets()
                    _listMenu.value = ListMenuState()
                    navigator.navigateToChecklistDetail(page.checklistId)
                }
            }

            // Seeded from the CURRENT page rather than from an empty string: renaming is almost
            // always an edit of the existing name, and an empty field would make the user retype it.
            InboxIntent.OnRenameChecklistClick -> _listMenu.value = ListMenuState(
                renameDraft = currentPage()?.title.orEmpty(),
            )

            is InboxIntent.OnRenameDraftChanged -> _listMenu.update { it.copy(renameDraft = intent.text) }
            InboxIntent.OnConfirmRenameChecklist -> renameChecklist()
            InboxIntent.OnDismissRenameChecklist -> _listMenu.value = ListMenuState()

            InboxIntent.OnDeleteChecklistClick ->
                _listMenu.value = ListMenuState(deleteConfirmationOpen = true)

            InboxIntent.OnConfirmDeleteChecklist -> deleteChecklist()
            InboxIntent.OnDismissDeleteChecklist -> _listMenu.value = ListMenuState()
        }
    }

    /**
     * Projects one page through the display options: hide completed, then reorder.
     *
     * A projection only — nothing here writes back. [InboxSort.MANUAL] returns the list untouched
     * BECAUSE that is already the stored template order; re-sorting by anything else and persisting
     * it would rewrite the order the detail screen, the widget and MCP all read.
     *
     * `sortedBy`/`sortedByDescending` are stable in Kotlin, so ties inside NAME and PRIORITY keep
     * manual order rather than shuffling between emissions.
     */
    private fun InboxPage.applyDisplayOptions(options: InboxDisplayOptions): InboxPage {
        val visible = if (options.showCompleted) tasks else tasks.filterNot { it.checked }
        val ordered = when (options.sort) {
            InboxSort.MANUAL -> visible
            // Case-insensitive: an A/a split reads as a broken sort, not as a rule.
            InboxSort.NAME -> visible.sortedBy { it.text.lowercase() }
            InboxSort.PRIORITY -> visible.sortedByDescending { it.priority }
        }
        return copy(tasks = ordered)
    }

    /** The page the pager is settled on, clamped exactly as [screenState] clamps it. */
    private fun currentPage(): InboxPage? {
        val pages = _pages.value ?: return null
        return pages.getOrNull(_selectedPage.value.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    }

    // ── Writes ───────────────────────────────────────────────────────────────────────────────

    /**
     * Persists one display option, wrapped exactly like every other write in this class.
     *
     * Not a bare `viewModelScope.launch { … }`: [InboxDisplayPrefsRepository] delegates straight to
     * DataStore's `edit {}`, which catches nothing, so an IOException (full disk, corrupted
     * preferences file, OPFS quota on web) escapes into the scope and — on Android — reaches the
     * platform's uncaught handler. Crashing the app on a settings tap is not an acceptable failure
     * mode for a display preference.
     *
     * The message is not optional either: the sheet renders the STORED value, so a write that failed
     * shows up as the radio button snapping back with no explanation.
     *
     * @param description what was being written, for the log line only — never user-facing.
     */
    private fun persistDisplayOption(description: String, write: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { write() }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "display option write failed ($description): ${e.message}", e)
                emitMessage(Res.string.error_save_failed)
            }
        }
    }

    /**
     * Applies a capture-dock chip to the draft.
     *
     * Only the chips this screen can honour arrive here: [TaskCreateChipsRow] is asked to hide
     * "Pick time…" and "Repeat", which need a date picker and the repeat sheet (plus its free-tier
     * gate) that live on the detail screen. The two are still handled — with a log rather than a
     * silent `else -> {}` — because a chip row is shared code and the next host may enable them.
     */
    private fun applyCreateChip(action: GistiItemCreateAction) {
        when (action) {
            GistiItemCreateAction.REMIND_1H ->
                _draft.value = _draft.value.withPreset(ItemCreateReminderPreset.ONE_HOUR)
            GistiItemCreateAction.REMIND_TOMORROW_MORNING ->
                _draft.value = _draft.value.withPreset(ItemCreateReminderPreset.TOMORROW_MORNING)
            GistiItemCreateAction.REMIND_TONIGHT ->
                _draft.value = _draft.value.withPreset(ItemCreateReminderPreset.TONIGHT)
            GistiItemCreateAction.IMPORTANT ->
                _draft.value = _draft.value.withImportantToggled()
            GistiItemCreateAction.REMIND_PICK, GistiItemCreateAction.REPEAT ->
                logger.warning(TAG, "capture chip $action has no host on the Inbox tab")
        }
    }

    /**
     * Appends the trimmed quick-add text to the CURRENTLY VISIBLE page's checklist.
     *
     * Targeting the visible page is what makes the pager pay for itself: page 0 is "capture into the
     * Inbox", pages 1..n are "quick-add to this project", with no extra UI.
     */
    private fun addTask() {
        val content = screenState.value as? InboxScreenState.Content ?: return
        val draft = content.draft
        val text = draft.text.trim()
        val page = content.pages.getOrNull(content.selectedPage)
        if (text.isEmpty() || page == null) {
            // Defensive only: the Add affordance is disabled while the text is blank, and the screen
            // hides the whole quick-add row while there are no pages, so neither half can normally be
            // reached. Still logged rather than a bare `return` — an invisible drop looks like a freeze.
            logger.warning(TAG, "quick-add skipped: blank=${text.isEmpty()} page=${page?.checklistId}")
            return
        }

        viewModelScope.launch {
            runCatching {
                val checklist = repository.getChecklistById(page.checklistId)
                    ?: error("Checklist ${page.checklistId} not found")
                val fill = repository.getDefaultFillByChecklistId(page.checklistId).first()
                    ?: error("No default fill for checklist ${page.checklistId}")

                // Template item FIRST so the fill row can carry its stable id from birth — the link
                // is what keeps move/delete/edit from having to guess by text later.
                val weekday = weekdayFor(checklist)
                val priority = if (draft.important) 1 else 0
                // Resolved HERE, not when the chip was tapped: a dock left open across 18:00 would
                // otherwise write a "Tonight" that is already in the past.
                val reminderAt = draft.resolveReminderAtNow()
                // Priority rides BOTH halves of the pair: the template feeds the edit screen and the
                // fill feeds every list, and a star on only one of them is the template↔fill desync
                // this domain keeps re-learning (rule `checklist-domain`).
                val templateItem = ChecklistItem(text = text, weekday = weekday, priority = priority)
                // reminderAt goes through withReminderAt, not the constructor: ChecklistFillItem's
                // full parameter list is a PRIVATE constructor (the public one deliberately exposes
                // only the birth fields), so the reminder is applied as an explicit transition.
                val fillItem = ChecklistFillItem(
                    text = text,
                    checked = false,
                    note = null,
                    weekday = weekday,
                    priority = priority,
                    templateItemId = templateItem.id,
                ).let { item -> reminderAt?.let(item::withReminderAt) ?: item }
                repository.updateFill(fill.copy(items = fill.items + fillItem))
                repository.updateChecklistTemplate(checklist.copy(items = checklist.items + templateItem))
                // Persisting reminderAt is only half a reminder — without the alarm the row shows a
                // bell that never rings, which is worse than no chip at all.
                reminderAt?.let { at ->
                    reminderScheduler.scheduleItemReminder(page.checklistId, fill.id, fillItem.id, at)
                }
            }.onSuccess {
                _draft.value = _draft.value.cleared()
                analytics.event(
                    AnalyticsEvents.Inbox.QUICK_ADDED,
                    mapOf(AnalyticsParams.SOURCE to if (page.isInbox) SOURCE_INBOX else SOURCE_PROJECT),
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "quick-add failed for checklist ${page.checklistId}: ${e.message}", e)
                emitMessage(Res.string.inbox_task_add_failed)
            }
        }
    }

    /**
     * Mirrors `ChecklistDetailViewModel.updateItemChecked` action for action — deliberately, because
     * in the v2 arm the Inbox is the HOME tab and therefore the app's primary check surface. Every
     * step that path takes and this one skipped would show up as a behaviour difference between the
     * arms rather than as a product effect:
     * - the CHECKED / UNCHECKED (+ FILL_COMPLETED) events are the core engagement metric; emitting
     *   them only from the detail screen would make the treatment arm read as "engages less" purely
     *   as an instrumentation artefact ([trackChecked]);
     * - a checked item's alarm has to be cancelled here too, or a reminder fires for a task the user
     *   already completed;
     * - `autoDeleteCompleted` is a per-CHECKLIST setting, so the same tap must remove the item when
     *   done from the Inbox exactly as it does from the detail screen ([deleteCheckedItem]).
     *
     * Checked state itself is FILL-only data (the template's `checked` is vestigial), so the plain
     * branch legitimately writes one side; the auto-delete branch writes the template+fill PAIR.
     */
    private fun setTaskChecked(taskId: String, checked: Boolean) {
        val content = screenState.value as? InboxScreenState.Content ?: return
        val page = content.pages.getOrNull(content.selectedPage) ?: return

        viewModelScope.launch {
            runCatching {
                val checklist = repository.getChecklistById(page.checklistId)
                    ?: error("Checklist ${page.checklistId} not found")
                val fill = repository.getDefaultFillByChecklistId(page.checklistId).first()
                    ?: error("No default fill for checklist ${page.checklistId}")
                val target = fill.items.firstOrNull { it.id == taskId }
                    ?: error("Task $taskId not found in fill ${fill.id}")

                // Cancel before the write, like the detail screen: the alarm was registered against
                // (checklistId, fillId, itemId) and all three are known here.
                if (checked && target.hasActiveReminder) {
                    reminderScheduler.cancelItemReminder(page.checklistId, fill.id, taskId)
                    reminderScheduler.cancelItemRepeat(page.checklistId, fill.id, taskId)
                }

                if (checked && checklist.autoDeleteCompleted) {
                    deleteCheckedItem(checklist, fill, target)
                } else {
                    val updatedItems = fill.items.map { item ->
                        if (item.id == taskId) {
                            val base = item.withChecked(checked)
                            // Clear the reminder fields together with the cancel above, so a stale
                            // reminder chip can never outlive the alarm it describes.
                            if (checked && item.hasActiveReminder) base.withReminderCleared() else base
                        } else {
                            item
                        }
                    }
                    repository.updateFill(fill.copy(items = updatedItems))
                    trackChecked(page.checklistId, checked, updatedItems)
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "check toggle failed for task $taskId: ${e.message}", e)
                // The checkbox springs back on its own (the flow re-emits the stored value), which
                // reads as "my tap did nothing" — the write failure needs to say so out loud.
                emitMessage(Res.string.inbox_task_update_failed)
            }
        }
    }

    /**
     * `autoDeleteCompleted` branch of [setTaskChecked]: the checked row leaves BOTH sides.
     *
     * Template-row matching is the detail screen's: the stable link first, text only as the legacy
     * fallback (matching by text alone would take out a same-text sibling). When neither matches, the
     * template row is left in place and logged — same accepted outcome as the detail screen, so the
     * two surfaces cannot disagree about the same data. (Note the leftover row is not inert:
     * `ChecklistRepositoryImpl.updateChecklist`, the edit-screen save, rebuilds the default fill from
     * the template and would re-materialise the task.)
     */
    private suspend fun deleteCheckedItem(
        checklist: Checklist,
        fill: ChecklistFill,
        target: ChecklistFillItem,
    ) {
        val removedTemplateId = target.templateItemId
            ?: checklist.items.firstOrNull { !it.isFolder && it.text == target.text }?.id
        if (removedTemplateId == null) {
            logger.warning(
                TAG,
                "auto-delete of task ${target.id}: no template row matched — template left intact",
            )
        }
        repository.updateFill(fill.copy(items = fill.items.filterNot { it.id == target.id }))
        repository.updateChecklistTemplate(
            checklist.copy(items = checklist.items.filterNot { it.id == removedTemplateId })
        )
        analytics.event(
            AnalyticsEvents.Item.AUTO_DELETED,
            mapOf(
                AnalyticsParams.CHECKLIST_ID to checklist.id.toString(),
                AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
            ),
        )
    }

    /**
     * Same events, same param shapes, as `ChecklistDetailViewModel.updateItemChecked`.
     *
     * [AnalyticsParams.PROGRESS] is the percentage of checked rows across the WHOLE fill (folder
     * mirror rows included, exactly as there) — a different denominator here would silently split
     * one metric into two incomparable series.
     */
    private fun trackChecked(checklistId: Long, checked: Boolean, items: List<ChecklistFillItem>) {
        val totalItems = items.size
        val checkedCount = items.count { it.checked }
        val progress = if (totalItems > 0) "${checkedCount * 100 / totalItems}" else "0"
        analytics.event(
            if (checked) AnalyticsEvents.Item.CHECKED else AnalyticsEvents.Item.UNCHECKED,
            mapOf(
                AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                AnalyticsParams.PROGRESS to progress,
                AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
            ),
        )
        if (checked && totalItems > 0 && checkedCount == totalItems) {
            analytics.event(
                AnalyticsEvents.Checklist.FILL_COMPLETED,
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    AnalyticsParams.ITEM_COUNT to totalItems.toString(),
                    AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
                ),
            )
        }
    }

    /**
     * Triage: pull the task out of the source checklist and append it to [targetChecklistId].
     *
     * FOUR writes — source fill, source template, target fill, target template — deliberately in ONE
     * coroutine. Splitting them lets a sync push observe half the move and duplicate the task across
     * devices. The moved fill row is REUSED (only its template link and, for a Weekly target, its
     * weekday are repointed) so note, priority, attachments and checked state survive the move
     * instead of being silently dropped.
     *
     * The move ABORTS (no write at all, move-failed message) when the source template row cannot be
     * identified — see the elvis chain below for why an orphan there is worse than a failed move.
     * Unlike [deleteTask], which tolerates the same case: there the user has no other way to get rid
     * of the row, whereas a half-moved task silently ends up in two projects.
     */
    private fun moveTask(targetChecklistId: Long) {
        val content = screenState.value as? InboxScreenState.Content ?: return
        val taskId = content.sheetForTaskId
        val sourcePage = content.pages.getOrNull(content.selectedPage)
        if (taskId == null || sourcePage == null) {
            logger.warning(TAG, "move skipped: taskId=$taskId page=${sourcePage?.checklistId}")
            return
        }

        viewModelScope.launch {
            runCatching {
                val source = repository.getChecklistById(sourcePage.checklistId)
                    ?: error("Source checklist ${sourcePage.checklistId} not found")
                val target = repository.getChecklistById(targetChecklistId)
                    ?: error("Target checklist $targetChecklistId not found")
                val sourceFill = repository.getDefaultFillByChecklistId(sourcePage.checklistId).first()
                    ?: error("No default fill for checklist ${sourcePage.checklistId}")
                val targetFill = repository.getDefaultFillByChecklistId(targetChecklistId).first()
                    ?: error("No default fill for checklist $targetChecklistId")
                val moved = sourceFill.items.firstOrNull { it.id == taskId }
                    ?: error("Task $taskId not found in fill ${sourceFill.id}")

                if (moved.hasActiveReminder) {
                    // Known limitation, logged rather than hidden: the alarm was registered against
                    // the SOURCE fill id, so it still deep-links there until the user re-saves the
                    // reminder from the detail screen. Dropping the reminder instead would be worse.
                    logger.warning(
                        TAG,
                        "moved task $taskId keeps a reminder registered on source fill ${sourceFill.id}",
                    )
                }

                // A Weekly-view target groups its rows by ISO weekday, so a weekday-null row there is
                // rendered by NO day section (see [weekdayFor]). Preserve the day the task already
                // had — a Weekly→Weekly move keeps it — and only fall back to today when it has none.
                val targetWeekday = moved.weekday ?: weekdayFor(target)

                val newTemplateItem = ChecklistItem(
                    text = moved.text,
                    weekday = targetWeekday,
                    priority = moved.priority,
                )
                val movedFillItem = moved
                    .withTemplateItemId(newTemplateItem.id)
                    .withWeekday(targetWeekday)
                val removedTemplateId = moved.templateItemId
                    // Do NOT fall back to deleting by text alone: that would take out a same-text
                    // sibling. Matching a non-folder row of the same text IS the legacy link.
                    ?: source.items.firstOrNull { !it.isFolder && it.text == moved.text }?.id
                    // Nothing matched → abort BEFORE any write. Leaving the orphan is NOT recoverable:
                    // `ChecklistRepositoryImpl.updateChecklist` (the edit-screen save) rebuilds the
                    // default fill FROM the template, so the next save of the source project would
                    // re-materialise the moved task there and it would exist in BOTH projects. Failing
                    // the move keeps both sides consistent and surfaces the move-failed message.
                    ?: error("No template row matches task $taskId in checklist ${source.id}")

                repository.updateFill(sourceFill.copy(items = sourceFill.items.filterNot { it.id == taskId }))
                repository.updateChecklistTemplate(
                    source.copy(items = source.items.filterNot { it.id == removedTemplateId })
                )
                repository.updateFill(targetFill.copy(items = targetFill.items + movedFillItem))
                repository.updateChecklistTemplate(target.copy(items = target.items + newTemplateItem))
            }.onSuccess {
                closeSheets()
                analytics.event(AnalyticsEvents.Inbox.TASK_MOVED)
                emitMessage(Res.string.inbox_task_moved_message)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "move of task $taskId to $targetChecklistId failed: ${e.message}", e)
                closeSheets()
                emitMessage(Res.string.inbox_task_move_failed)
            }
        }
    }

    /** Deletes the task from BOTH the fill and the template — a fill-only delete resurrects it. */
    private fun deleteTask() {
        val content = screenState.value as? InboxScreenState.Content ?: return
        val taskId = content.sheetForTaskId
        val page = content.pages.getOrNull(content.selectedPage)
        if (taskId == null || page == null) {
            logger.warning(TAG, "delete skipped: taskId=$taskId page=${page?.checklistId}")
            return
        }

        viewModelScope.launch {
            runCatching {
                val checklist = repository.getChecklistById(page.checklistId)
                    ?: error("Checklist ${page.checklistId} not found")
                val fill = repository.getDefaultFillByChecklistId(page.checklistId).first()
                    ?: error("No default fill for checklist ${page.checklistId}")
                val target = fill.items.firstOrNull { it.id == taskId }
                    ?: error("Task $taskId not found in fill ${fill.id}")

                val removedTemplateId = target.templateItemId
                    ?: checklist.items.firstOrNull { !it.isFolder && it.text == target.text }?.id
                repository.updateFill(fill.copy(items = fill.items.filterNot { it.id == taskId }))
                repository.updateChecklistTemplate(
                    checklist.copy(items = checklist.items.filterNot { it.id == removedTemplateId })
                )
            }.onSuccess {
                closeSheets()
                analytics.event(AnalyticsEvents.Inbox.TASK_DELETED)
                emitMessage(Res.string.inbox_task_deleted_message)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "delete of task $taskId failed: ${e.message}", e)
                closeSheets()
                emitMessage(Res.string.inbox_task_delete_failed)
            }
        }
    }

    // ── Toolbar overflow: whole-checklist actions ────────────────────────────────────────────

    /**
     * Renames the CURRENT page's checklist.
     *
     * Guarded against the system Inbox at three layers — the menu is not offered there
     * ([screenState] forces it shut), and [decideRename] re-checks — because the page under an open
     * dialog can change: a sync deleting the project the user is renaming shifts the pager, and
     * without the re-read the confirm would rename whatever slid into the slot.
     *
     * Each outcome of [decideRename] gets its OWN reaction. One branch covering both a blank draft
     * and a vanished target is what told the user "Enter a name" about a name they had entered.
     */
    private fun renameChecklist() {
        val page = currentPage()
        when (val decision = decideRename(_listMenu.value.renameDraft, page)) {
            RenameDecision.BlankName -> {
                // Blank input is a user action like any other: closing the dialog with no word about
                // it reads as "the app ate my rename". The message names the reason.
                _listMenu.value = ListMenuState()
                emitMessage(Res.string.fill_error_name_required)
            }

            RenameDecision.InvalidTarget -> {
                // Nothing else records this one: the write never starts, so there is no failed
                // repository call to log later, and the user is only told the rename did not happen.
                logger.warning(
                    TAG,
                    "rename skipped: page=${page?.checklistId} isInbox=${page?.isInbox}",
                )
                _listMenu.value = ListMenuState()
                emitMessage(Res.string.inbox_checklist_rename_failed)
            }

            // Nothing to write and nothing went wrong — just close the dialog.
            RenameDecision.Unchanged -> _listMenu.value = ListMenuState()

            is RenameDecision.Rename -> viewModelScope.launch {
                runCatching {
                    val checklist = repository.getChecklistById(decision.checklistId)
                        ?: error("Checklist ${decision.checklistId} not found")
                    // updateChecklist, not updateChecklistTemplate: the name lives on the checklist
                    // ROW, and the template writer only carries the item list — it would drop the
                    // rename.
                    repository.updateChecklist(checklist.copy(name = decision.name))
                }.onSuccess {
                    _listMenu.value = ListMenuState()
                    // No snackbar: the toolbar title is the confirmation, and it updates in the same
                    // frame. A message on top of a visible change is noise.
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    logger.error(
                        TAG,
                        "rename of checklist ${decision.checklistId} failed: ${e.message}",
                        e,
                    )
                    _listMenu.value = ListMenuState()
                    emitMessage(Res.string.inbox_checklist_rename_failed)
                }
            }
        }
    }

    /**
     * Deletes the CURRENT page's checklist, alarms first.
     *
     * The two `cancel*` calls are not optional bookkeeping: alarms are held by the platform
     * scheduler keyed on the checklist id, so deleting the row without cancelling leaves a reminder
     * that still fires and deep-links into a checklist that no longer exists. Same order as
     * `ChecklistDetailViewModel.deleteChecklist`.
     *
     * No navigation afterwards — unlike the detail screen, the user is standing on a pager that
     * simply loses a page; [screenState] clamps the index and the anchor in the UI re-anchors.
     */
    private fun deleteChecklist() {
        val page = currentPage()
        if (page == null || page.isInbox) {
            logger.warning(TAG, "checklist delete skipped: page=${page?.checklistId} isInbox=${page?.isInbox}")
            _listMenu.value = ListMenuState()
            // The user has already confirmed a destructive action in a dialog; closing it with
            // nothing deleted and nothing said reads as "it worked" until they notice the checklist
            // is still there.
            emitMessage(Res.string.inbox_checklist_delete_failed)
            return
        }

        viewModelScope.launch {
            runCatching {
                val checklist = repository.getChecklistById(page.checklistId)
                    ?: error("Checklist ${page.checklistId} not found")
                reminderScheduler.cancelReminder(checklist.id)
                reminderScheduler.cancelRepeat(checklist.id)
                repository.deleteChecklist(checklist)
                checklist
            }.onSuccess { checklist ->
                _listMenu.value = ListMenuState()
                analytics.event(
                    AnalyticsEvents.Checklist.DELETED,
                    mapOf(
                        AnalyticsParams.CHECKLIST_ID to checklist.id.toString(),
                        AnalyticsParams.ITEM_COUNT to page.tasks.size.toString(),
                        AnalyticsParams.SOURCE to "inbox_overflow",
                    ),
                )
                emitMessage(Res.string.inbox_checklist_deleted_message)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "delete of checklist ${page.checklistId} failed: ${e.message}", e)
                _listMenu.value = ListMenuState()
                emitMessage(Res.string.inbox_checklist_delete_failed)
            }
        }
    }

    /**
     * Importance uses [ChecklistRepository.togglePriority], which already dual-writes fill+template
     * atomically — re-implementing the pair here would be a second, divergent copy of that logic.
     *
     * The sheet is left OPEN. It used to close, which made sense when it held four rows and starring
     * was most of what it did; now it is the same sheet the detail screen shows, and that one stays
     * open so the star flips under the user's finger. Closing here would make one composable behave
     * differently depending on which screen opened it.
     */
    private fun toggleImportant() {
        withOpenTask("importance toggle") { target ->
            // togglePriority returns a Result instead of throwing, so its failure would slip past
            // withOpenTask's catch — the star would simply never appear with nothing said.
            repository.togglePriority(target.fill.id, target.item.id).onFailure { e ->
                logger.error(TAG, "importance toggle failed for task ${target.item.id}: ${e.message}", e)
                emitMessage(Res.string.inbox_task_update_failed)
            }
        }
    }

    // ── Item sheet: the surfaces lifted from the checklist detail screen ─────────────────────

    /**
     * The stored row the open item sheet points at, resolved all the way down to the fill item.
     *
     * The page is found by SEARCHING every page for the task id instead of trusting `selectedPage`:
     * a sync that adds or removes a project shifts the pager under the open sheet, and a write routed
     * by index would then land on a different project's checklist. The id is unique across pages, so
     * the search is exact.
     */
    private data class TaskTarget(
        val page: InboxPage,
        val checklist: Checklist,
        val fill: ChecklistFill,
        val item: ChecklistFillItem,
    )

    /** The open sheet's row as the pager currently holds it — no repository round trip. */
    private fun openTaskInState(): InboxTask? {
        val taskId = _itemSheet.value.taskId ?: return null
        return _pages.value?.firstNotNullOfOrNull { page ->
            page.tasks.firstOrNull { it.fillItemId == taskId }
        }
    }

    /**
     * Runs [block] against the stored row behind the open sheet, inside ONE coroutine.
     *
     * Every failure mode reports: no sheet and no row are logged, and anything that reaches the user
     * as "my tap did nothing" also emits the update-failed message. [action] names the caller in the
     * log line only — never user-facing.
     */
    private fun withOpenTask(action: String, block: suspend (TaskTarget) -> Unit) {
        val taskId = _itemSheet.value.taskId
        if (taskId == null) {
            logger.warning(TAG, "$action skipped: no item sheet open")
            return
        }
        val page = _pages.value?.firstOrNull { page -> page.tasks.any { it.fillItemId == taskId } }
        if (page == null) {
            logger.warning(TAG, "$action skipped: task $taskId is on no page")
            emitMessage(Res.string.inbox_task_update_failed)
            return
        }

        viewModelScope.launch {
            runCatching {
                val checklist = repository.getChecklistById(page.checklistId)
                    ?: error("Checklist ${page.checklistId} not found")
                val fill = repository.getDefaultFillByChecklistId(page.checklistId).first()
                    ?: error("No default fill for checklist ${page.checklistId}")
                val item = fill.items.firstOrNull { it.id == taskId }
                    ?: error("Task $taskId not found in fill ${fill.id}")
                block(TaskTarget(page, checklist, fill, item))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "$action failed for task $taskId: ${e.message}", e)
                emitMessage(Res.string.inbox_task_update_failed)
            }
        }
    }

    // ── Inline rename ────────────────────────────────────────────────────────────────────────

    private fun startTaskTextEdit() {
        val task = openTaskInState()
        if (task == null) {
            logger.warning(TAG, "text edit skipped: no row behind the open sheet")
            return
        }
        _itemSheet.update { it.copy(textEditing = true, textDraft = task.text) }
    }

    /**
     * Commits the inline rename to BOTH halves of the pair (rule `checklist-domain`): the fill feeds
     * every list, the template feeds the edit screen, and a rename on one side is the desync this
     * domain keeps re-learning.
     *
     * Fires twice by design — the sheet commits on Save AND on blur — so the `textEditing` check is a
     * double-fire guard, not a user-facing skip.
     */
    private fun confirmTaskTextEdit() {
        val sheet = _itemSheet.value
        if (!sheet.textEditing) {
            logger.debug(TAG, "rename confirm ignored: edit mode already closed (Save + blur both fired)")
            return
        }
        val newText = sheet.textDraft.trim()
        val current = openTaskInState()
        // Blank input or an unchanged name: close edit mode and write nothing. Both are the user
        // deciding not to rename, not a failure — the headline snapping back to the stored text is
        // the feedback, exactly as on the detail screen.
        if (newText.isEmpty() || current == null || current.text == newText) {
            _itemSheet.update { it.copy(textEditing = false, textDraft = "") }
            return
        }

        _itemSheet.update { it.copy(textEditing = false, textDraft = "") }
        withOpenTask("rename task") { target ->
            val oldText = target.item.text
            repository.updateFill(
                target.fill.copy(
                    items = target.fill.items.map { item ->
                        if (item.id == target.item.id) item.withText(newText) else item
                    },
                )
            )
            // Match the template row by the stable link first — the TEXT is what is changing here, so
            // a text-keyed match would either miss or hit a same-text sibling. The old text stays as
            // the legacy fallback for fill rows written before the link existed.
            val templateItems = target.checklist.items.map { templateItem ->
                val matches = target.item.templateItemId?.let { templateItem.id == it }
                    ?: (templateItem.text == oldText && !templateItem.isFolder)
                if (matches) templateItem.withText(newText) else templateItem
            }
            repository.updateChecklistTemplate(target.checklist.copy(items = templateItems))
            // Literal, not an AnalyticsEvents constant: `item_text_edited` has none — the detail
            // screen emits the same literal, and the two must stay one series.
            analytics.event(
                "item_text_edited",
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to target.page.checklistId.toString(),
                    AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
                ),
            )
        }
    }

    // ── Note ─────────────────────────────────────────────────────────────────────────────────

    private fun openNoteDialog() {
        val task = openTaskInState()
        if (task == null) {
            logger.warning(TAG, "note skipped: no row behind the open sheet")
            return
        }
        _itemSheet.update { it.copy(noteDraft = task.item.note.orEmpty()) }
    }

    /** Notes are FILL-only data (the template carries no note), so this legitimately writes one side. */
    private fun saveNote() {
        val draft = _itemSheet.value.noteDraft
        if (draft == null) {
            logger.warning(TAG, "note save skipped: dialog is not open")
            return
        }
        _itemSheet.update { it.copy(noteDraft = null) }
        withOpenTask("save note") { target ->
            repository.updateFill(
                target.fill.copy(
                    items = target.fill.items.map { item ->
                        if (item.id == target.item.id) item.withNote(draft.takeIf { it.isNotBlank() }) else item
                    },
                )
            )
        }
    }

    // ── Reminder / repeat ────────────────────────────────────────────────────────────────────

    /**
     * Opens the shared reminder sheet over the task, running the same two gates the detail screen runs.
     *
     * The free-tier check mirrors `ChecklistDetailViewModel.handleItemReminderClick` — including its
     * hardcoded "one active reminder" ceiling — deliberately: the two sheets are now the same
     * composable, and gating them differently would let a free user arm from the Inbox a reminder the
     * detail screen refuses. (That the ceiling is a constant rather than `UserLimits`
     * is a pre-existing v1 issue; fixing it belongs in one place, for both call sites.)
     */
    private fun openReminderSheet() {
        val task = openTaskInState()
        if (task == null) {
            logger.warning(TAG, "reminder skipped: no row behind the open sheet")
            return
        }
        viewModelScope.launch {
            val item = task.item
            val isPremium = _userLimits.value?.isPremium == true
            val atLimit = runCatching {
                !isPremium && !item.hasActiveReminder && repository.countActiveReminders() >= FREE_ACTIVE_REMINDERS
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                // Counting failed — open the sheet UNLOCKED rather than locking a paying behaviour
                // behind a read error, and record why the gate could not be evaluated.
                logger.error(TAG, "reminder gate: countActiveReminders failed: ${e.message}", e)
                false
            }

            if (atLimit) {
                _itemSheet.update {
                    it.copy(reminder = InboxItemReminderUi(locked = true, fullScreen = item.reminderFullScreen))
                }
                return@launch
            }

            // Reminder fields exist only on the fill row, so an item with a repeat and no one-shot
            // opens straight on the tab that describes it.
            val tab = if (item.repeatRule != null && item.reminderAt == null) ReminderTab.REPEAT else ReminderTab.ONCE
            _itemSheet.update {
                it.copy(
                    reminder = InboxItemReminderUi(
                        tab = tab,
                        fullScreen = item.reminderFullScreen,
                        pendingRepeatConfig = repeatConfigOf(item),
                        repeatRuleSummary = repeatConfigOf(item)?.let(::buildRepeatSummary),
                    ),
                    // Android 13+: without the permission every alarm scheduled below is posted and
                    // silently dropped. Asking first is what keeps "reminder set" from being a lie.
                    notificationPermissionOpen = !reminderScheduler.hasNotificationPermission(),
                )
            }
        }
    }

    private fun selectReminderTab(tab: ReminderTab) {
        val item = openTaskInState()?.item
        _itemSheet.update { sheet ->
            val reminder = sheet.reminder ?: return@update sheet
            sheet.copy(
                reminder = reminder.copy(
                    tab = tab,
                    // Seed the repeat editor from the stored rule the first time the tab is opened;
                    // an already-edited config is left alone so switching tabs never loses input.
                    pendingRepeatConfig = if (tab == ReminderTab.REPEAT) {
                        reminder.pendingRepeatConfig ?: item?.let(::repeatConfigOf) ?: PendingRepeatConfig()
                    } else {
                        reminder.pendingRepeatConfig
                    },
                ),
            )
        }
    }

    private fun selectRepeatType(type: RepeatType) = updateRepeatConfig {
        // Same reset as the detail screen: a type switch drops the interval and weekday selection
        // that belonged to the previous type, which would otherwise survive as an invisible rule.
        it.copy(type = type, isCustom = false, interval = 1, weekDays = emptySet())
    }

    private inline fun updateRepeatConfig(update: (PendingRepeatConfig) -> PendingRepeatConfig) {
        _itemSheet.update { sheet ->
            val reminder = sheet.reminder ?: return@update sheet
            sheet.copy(
                reminder = reminder.copy(
                    pendingRepeatConfig = update(reminder.pendingRepeatConfig ?: PendingRepeatConfig()),
                ),
            )
        }
    }

    private fun saveRepeatSchedule() {
        val config = _itemSheet.value.reminder?.pendingRepeatConfig
        if (config == null) {
            logger.warning(TAG, "repeat save skipped: no pending config")
            emitMessage(Res.string.inbox_task_update_failed)
            return
        }
        saveItemReminder(
            reminderAt = null,
            repeatRule = config.toRule(),
            repeatTimeOfDayMinutes = config.timeHour * 60 + config.timeMinute,
        )
    }

    /**
     * Persists a one-shot and/or repeating reminder onto the fill row AND registers the alarm.
     *
     * Reminder fields live only on [ChecklistFillItem] — no template write here (verified against
     * `ChecklistDetailViewModel.saveItemReminder`, which this mirrors step for step). The two halves
     * that MUST stay together are the write and the schedule: persisting without scheduling is a bell
     * that never rings, and scheduling without persisting is a notification for a reminder the UI
     * says does not exist.
     */
    private fun saveItemReminder(
        reminderAt: Long?,
        repeatRule: ReminderRepeatRule?,
        repeatTimeOfDayMinutes: Int?,
    ) {
        val fullScreen = _itemSheet.value.reminder?.fullScreen == true
        withOpenTask("save reminder") { target ->
            val checklistId = target.page.checklistId
            val fillId = target.fill.id
            val itemId = target.item.id

            // Cancel first, both kinds: the row may be switching from a one-shot to a repeat, and a
            // stale alarm of the other kind would keep firing against the same item.
            if (target.item.hasActiveReminder) {
                reminderScheduler.cancelItemReminder(checklistId, fillId, itemId)
                reminderScheduler.cancelItemRepeat(checklistId, fillId, itemId)
            }

            val updatedItem = if (repeatRule != null && repeatTimeOfDayMinutes != null) {
                target.item
                    .withRepeatRule(
                        repeatRule,
                        repeatTimeOfDayMinutes,
                        firstRepeatTriggerAt(
                            timeOfDayMinutes = repeatTimeOfDayMinutes,
                            now = Clock.System.now(),
                            timeZone = TimeZone.currentSystemDefault(),
                        ),
                    )
                    .withReminderAt(reminderAt)
            } else {
                target.item.withReminderCleared().withReminderAt(reminderAt)
            }.withReminderFullScreen(fullScreen)

            repository.updateFill(
                target.fill.copy(items = target.fill.items.map { if (it.id == itemId) updatedItem else it })
            )

            if (reminderAt != null) {
                reminderScheduler.scheduleItemReminder(checklistId, fillId, itemId, reminderAt)
            }
            val nextAt = updatedItem.repeatNextAt
            if (repeatRule != null && nextAt != null) {
                reminderScheduler.scheduleItemRepeat(checklistId, fillId, itemId, nextAt)
            }

            _itemSheet.update { it.copy(reminder = null, customPicker = null) }
            analytics.event(
                AnalyticsEvents.Reminder.ITEM_SET,
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    "has_repeat" to (repeatRule != null).toString(),
                    AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
                ),
            )
        }
    }

    private fun removeItemReminder() {
        withOpenTask("remove reminder") { target ->
            // Cancel both regardless of which was active — the same defensive pair the detail screen
            // uses, because a row can hold a one-shot and a repeat at once.
            reminderScheduler.cancelItemReminder(target.page.checklistId, target.fill.id, target.item.id)
            reminderScheduler.cancelItemRepeat(target.page.checklistId, target.fill.id, target.item.id)
            repository.updateFill(
                target.fill.copy(
                    items = target.fill.items.map { item ->
                        if (item.id == target.item.id) item.withReminderCleared() else item
                    },
                )
            )
            _itemSheet.update { it.copy(reminder = null, customPicker = null) }
            analytics.event(
                AnalyticsEvents.Reminder.ITEM_REMOVED,
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to target.page.checklistId.toString(),
                    AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
                ),
            )
        }
    }

    /**
     * Exports the open task's reminder to the device calendar.
     *
     * SYNCHRONOUS on purpose — no `viewModelScope.launch` around [CalendarEventLauncher.addEvent].
     * On web the launcher is `window.open`, which the browser blocks unless it runs inside the
     * click's own call stack; everything it needs is already in [_pages], so nothing has to suspend.
     */
    private fun addOpenTaskToCalendar() {
        val item = openTaskInState()?.item
        if (item == null) {
            logger.warning(TAG, "calendar export skipped: no row behind the open sheet")
            emitMessage(Res.string.inbox_task_update_failed)
            return
        }
        val launched = calendarEventLauncher.addEvent(
            buildCalendarEvent(
                title = item.text,
                startMillis = item.reminderAt ?: item.repeatNextAt,
                rule = item.repeatRule,
            )
        )
        if (!launched) {
            logger.warning(TAG, "calendar export: no handler for the insert-event intent")
            emitMessage(Res.string.calendar_app_not_found)
        } else {
            analytics.event(
                "add_to_calendar",
                mapOf(
                    "recurring" to (item.repeatRule != null).toString(),
                    "has_time" to ((item.reminderAt ?: item.repeatNextAt) != null).toString(),
                    "level" to "item",
                    AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
                ),
            )
        }
    }

    // ── Custom date + time picker ────────────────────────────────────────────────────────────

    private fun openCustomPicker() {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        // The Material date picker works in UTC millis, so "today" has to be expressed as UTC
        // midnight or the day before becomes selectable east of Greenwich.
        val todayUtcMidnight = LocalDateTime(today, LocalTime(0, 0))
            .toInstant(TimeZone.UTC).toEpochMilliseconds()
        // [reminder] is deliberately KEPT while the picker is up — the screen hides the sheet behind
        // the picker instead. Nulling it (what the detail screen does) drops the full-screen-delivery
        // toggle the user may have just flipped, so the reminder saved from the picker would silently
        // ignore it; keeping it also means dismissing the picker returns to the sheet.
        _itemSheet.update {
            it.copy(customPicker = InboxCustomPickerUi(minDateMillis = todayUtcMidnight))
        }
    }

    private fun selectCustomDate(dateMillis: Long) {
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = Clock.System.now().toLocalDateTime(tz)
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        // Picking today pre-selects the next full hour; any other day starts at 09:00.
        val initialHour = if (selectedDate == nowLocal.date) (nowLocal.hour + 1).coerceAtMost(23) else 9
        _itemSheet.update {
            it.copy(
                customPicker = it.customPicker?.copy(
                    dateMillis = dateMillis,
                    initialHour = initialHour,
                    timeInPast = false,
                ),
            )
        }
    }

    private fun updateCustomTimeInPast(hour: Int, minute: Int) {
        val dateMillis = _itemSheet.value.customPicker?.dateMillis
        if (dateMillis == null) {
            // Unreachable through the UI (the picker asks for a day before a time), so this is a
            // contract violation worth a line rather than a quiet return.
            logger.warning(TAG, "custom time changed with no date chosen — in-past hint skipped")
            return
        }
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = Clock.System.now().toLocalDateTime(tz)
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val inPast = selectedDate == nowLocal.date && LocalTime(hour, minute) <= nowLocal.time
        _itemSheet.update { it.copy(customPicker = it.customPicker?.copy(timeInPast = inPast)) }
    }

    private fun commitCustomDateTime(hour: Int, minute: Int) {
        val dateMillis = _itemSheet.value.customPicker?.dateMillis
        if (dateMillis == null) {
            logger.warning(TAG, "custom reminder skipped: no date chosen")
            emitMessage(Res.string.inbox_task_update_failed)
            return
        }
        val date = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val triggerAt = LocalDateTime(date, LocalTime(hour, minute))
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        if (triggerAt <= Clock.System.now().toEpochMilliseconds()) {
            // The picker already renders an in-past warning, so this is the last line of defence:
            // AlarmManager fires a past trigger immediately, which reads as a broken reminder.
            logger.warning(TAG, "custom reminder $triggerAt is in the past — not scheduled")
            emitMessage(Res.string.inbox_reminder_time_in_past)
            return
        }
        _itemSheet.update { it.copy(customPicker = null) }
        saveItemReminder(triggerAt, repeatRule = null, repeatTimeOfDayMinutes = null)
    }

    // ── Attachments ──────────────────────────────────────────────────────────────────────────

    /**
     * Quota check, then hand off to the platform picker through a one-shot trigger flag.
     *
     * At the limit the user gets the premium snackbar, never a picker that opens and then refuses to
     * save — the quota is the reason the tap cannot succeed, so it is the thing to say out loud.
     */
    private fun requestAttachment(isImage: Boolean) {
        val task = openTaskInState()
        if (task == null) {
            logger.warning(TAG, "attach skipped: no row behind the open sheet")
            return
        }
        val limits = _userLimits.value
        val blocked = if (limits != null) {
            !limits.canAddAttachment(task.item.attachments.size)
        } else {
            // Limits have not arrived yet — fall back to the same static free-tier default the detail
            // screen falls back to, so the two sheets cannot disagree on that first frame.
            task.item.attachments.size >= ChecklistDetailViewModel.FREE_ATTACHMENT_LIMIT_PER_ITEM
        }
        if (blocked) {
            logger.warning(
                TAG,
                "attach blocked by quota (have=${task.item.attachments.size}, " +
                    "max=${limits?.maxAttachmentsPerItem}, premium=${limits?.isPremium})",
            )
            emitMessage(Res.string.attachment_premium_limit_reached_snackbar)
            return
        }
        _itemSheet.update {
            it.copy(
                pendingAttachmentTaskId = task.fillItemId,
                triggerImagePicker = isImage,
                triggerFilePicker = !isImage,
            )
        }
    }

    /**
     * Copies the picked file into app storage and attaches it to the row it was picked for.
     *
     * Targets `intent.taskId` rather than the open sheet: the picker is another app, so by the time
     * the result lands the sheet may be gone (process backgrounded, user dismissed it).
     */
    private fun storePickedAttachment(intent: InboxIntent.OnAttachmentPicked) {
        val page = _pages.value?.firstOrNull { page -> page.tasks.any { it.fillItemId == intent.taskId } }
        if (page == null) {
            logger.warning(TAG, "attachment dropped: task ${intent.taskId} is on no page")
            emitMessage(Res.string.attachment_load_error)
            return
        }

        // Cleared HERE, not in a trailing block: every early exit below (`return@launch`) would skip
        // a clear placed after the work, and a stale pending id makes the NEXT picker result land on
        // the previous row.
        _itemSheet.update { it.copy(pendingAttachmentTaskId = null) }

        viewModelScope.launch {
            runCatching {
                val fill = repository.getDefaultFillByChecklistId(page.checklistId).first()
                    ?: error("No default fill for checklist ${page.checklistId}")
                val attachmentId = Attachment.generateId()
                val storedPath = attachmentStorage.storeAttachment(
                    sourcePath = intent.sourcePath,
                    fillId = fill.id,
                    itemId = intent.taskId,
                    attachmentId = attachmentId,
                    originalFileName = intent.fileName,
                )
                if (storedPath == null) {
                    logger.warning(TAG, "attachment ${intent.fileName}: storeAttachment returned null")
                    emitMessage(Res.string.attachment_load_error)
                    return@launch
                }

                val sizeBytes = attachmentStorage.sizeOf(storedPath)
                if (sizeBytes > ChecklistDetailViewModel.MAX_ATTACHMENT_SIZE_BYTES) {
                    // Delete the copy we just made — leaving it would consume the quota of a file the
                    // user is being told was rejected.
                    attachmentStorage.deleteAttachment(storedPath)
                    logger.warning(TAG, "attachment ${intent.fileName}: $sizeBytes bytes over the limit")
                    emitMessage(Res.string.attachment_size_too_large_snackbar)
                    return@launch
                }

                val (width, height) = attachmentStorage.probeImage(storedPath, intent.mimeType)
                repository.addAttachment(
                    fill.id,
                    intent.taskId,
                    Attachment(
                        id = attachmentId,
                        path = storedPath,
                        fileName = intent.fileName,
                        mimeType = intent.mimeType,
                        sizeBytes = sizeBytes,
                        createdAt = currentTimeMillis(),
                        width = width,
                        height = height,
                    ),
                )
                // review-rules:allow-observed-event — `attachment_added` is NOT in CsatManager's
                // observed set, so it cannot shift CSAT survey eligibility.
                analytics.event(
                    AnalyticsEvents.Attachment.ADDED,
                    mapOf<String, Any>(
                        AnalyticsParams.SOURCE to SOURCE_INBOX_TAB,
                        AnalyticsParams.MIME_TYPE to (intent.mimeType ?: "unknown"),
                        AnalyticsParams.SIZE_BYTES to sizeBytes,
                    ),
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "attachment ${intent.fileName} failed: ${e.message}", e)
                emitMessage(Res.string.attachment_load_error)
            }
        }
    }

    private fun deleteAttachment(attachmentId: String) {
        withOpenTask("delete attachment") { target ->
            // Close the viewer BEFORE the write when this was the last file, so it cannot flash empty
            // while the repository Flow catches up.
            if (target.item.attachments.size <= 1) {
                _itemSheet.update { it.copy(attachmentViewerFor = null) }
            }
            repository.removeAttachment(target.fill.id, target.item.id, attachmentId)
            emitMessage(Res.string.attachment_deleted_snackbar)
        }
    }

    /**
     * Hands a stored attachment to the platform viewer.
     *
     * The path is resolved to the form THIS platform can read (`opfs://…` on web for files synced
     * from Android); the raw synced path opens to an error there.
     */
    private fun openAttachmentExternally(attachmentId: String) {
        val attachment = openTaskInState()?.item?.attachments?.firstOrNull { it.id == attachmentId }
        if (attachment == null) {
            logger.warning(TAG, "open-externally skipped: attachment $attachmentId not on the open row")
            emitMessage(Res.string.attachment_load_error)
            return
        }
        viewModelScope.launch {
            _sideEffect.emit(
                InboxSideEffect.OpenAttachmentExternally(
                    path = resolveAttachmentLocalPath(attachment.path, attachment.storagePath),
                    mimeType = attachment.mimeType,
                )
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private fun closeSheets() {
        _itemSheet.value = ItemSheetState()
    }

    /** Resolves [resource] off the UI thread and pushes it to the screen's snackbar host. */
    private fun emitMessage(resource: StringResource) {
        viewModelScope.launch {
            val text = getString(resource)
            // A MutableSharedFlow with no replay DROPS emissions while nobody collects, and one
            // message is produced from `init` — the ensureInbox failure — which can beat the screen's
            // collector to the channel. Waiting for the first subscriber makes that message
            // undroppable without replaying stale ones to every later collector.
            _sideEffect.subscriptionCount.first { it > 0 }
            _sideEffect.emit(InboxSideEffect.ShowMessage(text))
        }
    }

    /**
     * ISO weekday (1=Mon..7=Sun) to stamp on a row written into [checklist]: today for a Weekly-view
     * checklist, null for a Standard one.
     *
     * `WeeklyChecklistDetailContent` renders one section per ISO day and filters
     * `items.weekday == weekday`, so a weekday-NULL row inside a Weekly checklist is drawn by NO
     * section: it exists in the template and the fill, shows up in this pager, and is invisible and
     * unmanageable the moment the user opens that project. Defaulting to today matches the Weekly
     * screen's own per-day add row rather than hiding Weekly projects from the pager and the move
     * picker (which would silently drop whole projects out of the Inbox tab).
     *
     * Both sides of the pair must get the SAME value — the template feeds the edit screen, the fill
     * feeds the detail screen, and a weekday on only one of them is the same invisibility bug one
     * screen later.
     */
    private fun weekdayFor(checklist: Checklist): Int? =
        if (checklist.viewMode == ChecklistViewMode.Weekly) {
            Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
        } else {
            null
        }

    /**
     * Projects one checklist + its default fill into a pager page.
     *
     * FOLDER template nodes are mirrored into the fill like any other row (see
     * `ChecklistRepositoryImpl.addChecklist`), so they are filtered out here — the Inbox is a flat
     * task list and a folder rendered as a checkable task would be nonsense.
     */
    private fun Checklist.toPage(fill: ChecklistFill?): InboxPage {
        val folderTemplateIds = items.filter { it.isFolder }.map { it.id }.toSet()
        return InboxPage(
            checklistId = id,
            title = name,
            isInbox = isInbox,
            tasks = fill?.items.orEmpty()
                .filterNot { item -> item.templateItemId?.let { it in folderTemplateIds } == true }
                .map(::InboxTask),
        )
    }

    private companion object {
        /** Wire values of [AnalyticsParams.SOURCE] on `inbox_quick_added`. */
        const val SOURCE_INBOX = "inbox"
        const val SOURCE_PROJECT = "project"

        /**
         * Free-tier ceiling on rows carrying an active reminder.
         *
         * Deliberately the same hardcoded 1 as `ChecklistDetailViewModel.handleItemReminderClick`.
         * The Inbox and the detail screen now show the SAME sheet, so a different ceiling here would
         * let a free user arm from one surface what the other refuses. It is a mirror, and mirrors
         * drift — but a divergent gate on the same composable is the worse failure, and the fix
         * belongs in one shared gate for both call sites.
         */
        const val FREE_ACTIVE_REMINDERS = 1

        /** Paywall attribution for the locked banner inside the item reminder sheet. */
        const val PAYWALL_SOURCE_ITEM_REMINDER = "inbox_item_reminder_limit"

        /**
         * Stamped on every emit this screen shares with the control arm (`item_checked`,
         * `item_unchecked`, `item_auto_deleted`, `fill_completed`).
         *
         * Two jobs, and both matter:
         *  * analytics keeps receiving the events, so checks performed here are comparable with the
         *    control arm's — omitting them would make the treatment arm look less engaged;
         *  * `CsatManager` skips them when SCORING, because this tab performs the same actions one
         *    screen earlier than control, and a faster-accumulating score would make the survey (and
         *    the Play in-app review behind it) fire at different rates in the two arms.
         *
         * Applied for EVERY page of the pager, project pages included: the cheapness comes from the
         * tab being the home screen, not from which checklist the row belongs to.
         */
        const val SOURCE_INBOX_TAB = "inbox_tab"

        /**
         * Wire value of [AnalyticsParams.SOURCE] on `nav_create_fab_tapped` since the floating "+"
         * was replaced by the inline row at the end of the list.
         *
         * The old value `"fab"` is NOT reused: the two are the same intent reached through different
         * affordances, and keeping them apart is the only way to read whether moving capture into the
         * list changed how often people start one. Both values live under one event, so the total is
         * still comparable across the change.
         *
         * Duplicated as a literal in `TodayViewModel` (the Calendar tab lost the same FAB and reports
         * the same source); a shared constant would have to live in `core:common:api` next to the
         * event name, which is where it belongs if a third surface ever appears.
         */
        const val SOURCE_INLINE_ROW = "inline_row"
    }
}

/**
 * What confirming the rename dialog should do. One value per outcome, because the three ways it can
 * fail need three different reactions and merging any two of them lies to the user — the shipped
 * version reported "Enter a name" when the name was fine and the TARGET had gone.
 *
 * Extracted from [InboxViewModel] rather than inlined so the distinction is pinned by a test: the
 * ViewModel itself cannot be built on a JVM host (its `init` resolves the Inbox title through
 * Compose Resources), so a guard left inside it is unreachable from `commonTest`.
 */
internal sealed interface RenameDecision {
    /** The user's own input is empty or whitespace — actionable, and their doing. */
    data object BlankName : RenameDecision

    /**
     * The input was fine and there is nothing to apply it to: no settled page, or the system Inbox
     * (which has no rename) slid into the slot while the dialog was open — a sync deleting the
     * project being renamed shifts the whole pager.
     */
    data object InvalidTarget : RenameDecision

    /** Same name as before. Not a failure: close the dialog and write nothing. */
    data object Unchanged : RenameDecision

    /** @param name already trimmed — the stored name must not carry the user's stray spaces. */
    data class Rename(val checklistId: Long, val name: String) : RenameDecision
}

internal fun decideRename(draft: String?, page: InboxPage?): RenameDecision {
    val trimmed = draft?.trim()
    // Blank first: with no name to apply, WHICH page is selected cannot matter, and "Enter a name"
    // is the one message the user can act on.
    if (trimmed.isNullOrEmpty()) return RenameDecision.BlankName
    if (page == null || page.isInbox) return RenameDecision.InvalidTarget
    if (trimmed == page.title) return RenameDecision.Unchanged
    return RenameDecision.Rename(checklistId = page.checklistId, name = trimmed)
}
