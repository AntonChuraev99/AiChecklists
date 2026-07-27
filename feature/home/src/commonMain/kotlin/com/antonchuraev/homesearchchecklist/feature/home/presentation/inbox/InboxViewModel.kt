package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.error_create_checklist_failed
import aichecklists.core.designsystem.generated.resources.inbox_task_update_failed
import aichecklists.core.designsystem.generated.resources.inbox_checklist_name
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
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase.EnsureInboxUseCase
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
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

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
    private val navigator: AppNavigator,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
) : AppViewModel<InboxScreenState, InboxIntent, InboxSideEffect>() {

    /**
     * null = no pager yet: either still loading OR the system Inbox row does not exist (see
     * [observePages]). A non-null value ALWAYS starts with the Inbox page, so it is never empty.
     */
    private val _pages = MutableStateFlow<List<InboxPage>?>(null)
    private val _selectedPage = MutableStateFlow(0)
    private val _quickAddText = MutableStateFlow("")
    private val _sheetForTaskId = MutableStateFlow<String?>(null)
    private val _movePickerOpen = MutableStateFlow(false)

    private val _sideEffect = MutableSharedFlow<InboxSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<InboxSideEffect> = _sideEffect.asSharedFlow()

    override val screenState: StateFlow<InboxScreenState> = combine(
        _pages,
        _selectedPage,
        _quickAddText,
        _sheetForTaskId,
        _movePickerOpen,
    ) { pages, selected, quickAddText, sheetForTaskId, movePickerOpen ->
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
                quickAddText = quickAddText,
                sheetForTaskId = sheetForTaskId,
                movePickerOpen = movePickerOpen,
                moveTargets = pages.filter { !it.isInbox && it.checklistId != current?.checklistId },
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
            is InboxIntent.OnQuickAddTextChanged -> _quickAddText.value = intent.text
            InboxIntent.OnQuickAddSubmit -> addTask()
            is InboxIntent.OnTaskCheckedChanged -> setTaskChecked(intent.taskId, intent.checked)
            is InboxIntent.OnTaskDetailsClick -> _sheetForTaskId.value = intent.taskId
            InboxIntent.OnTaskSheetDismiss -> closeSheets()
            InboxIntent.OnMovePickerOpen -> _movePickerOpen.value = true
            InboxIntent.OnMovePickerDismiss -> _movePickerOpen.value = false
            is InboxIntent.OnMoveToProject -> moveTask(intent.targetChecklistId)
            InboxIntent.OnToggleImportant -> toggleImportant()
            InboxIntent.OnDeleteTask -> deleteTask()
            is InboxIntent.OnOpenProject -> {
                closeSheets()
                navigator.navigateToChecklistDetail(intent.checklistId)
            }
        }
    }

    // ── Writes ───────────────────────────────────────────────────────────────────────────────

    /**
     * Appends the trimmed quick-add text to the CURRENTLY VISIBLE page's checklist.
     *
     * Targeting the visible page is what makes the pager pay for itself: page 0 is "capture into the
     * Inbox", pages 1..n are "quick-add to this project", with no extra UI.
     */
    private fun addTask() {
        val content = screenState.value as? InboxScreenState.Content ?: return
        val text = content.quickAddText.trim()
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
                val templateItem = ChecklistItem(text = text, weekday = weekday)
                val fillItem = ChecklistFillItem(
                    text = text,
                    checked = false,
                    note = null,
                    weekday = weekday,
                    templateItemId = templateItem.id,
                )
                repository.updateFill(fill.copy(items = fill.items + fillItem))
                repository.updateChecklistTemplate(checklist.copy(items = checklist.items + templateItem))
            }.onSuccess {
                _quickAddText.value = ""
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
            mapOf(AnalyticsParams.CHECKLIST_ID to checklist.id.toString()),
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
            ),
        )
        if (checked && totalItems > 0 && checkedCount == totalItems) {
            analytics.event(
                AnalyticsEvents.Checklist.FILL_COMPLETED,
                mapOf(
                    AnalyticsParams.CHECKLIST_ID to checklistId.toString(),
                    AnalyticsParams.ITEM_COUNT to totalItems.toString(),
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

    /**
     * Importance uses [ChecklistRepository.togglePriority], which already dual-writes fill+template
     * atomically — re-implementing the pair here would be a second, divergent copy of that logic.
     */
    private fun toggleImportant() {
        val content = screenState.value as? InboxScreenState.Content ?: return
        val taskId = content.sheetForTaskId
        val page = content.pages.getOrNull(content.selectedPage)
        if (taskId == null || page == null) {
            logger.warning(TAG, "importance toggle skipped: taskId=$taskId page=${page?.checklistId}")
            return
        }

        viewModelScope.launch {
            val fillId = runCatching {
                repository.getDefaultFillByChecklistId(page.checklistId).first()?.id
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "importance toggle: fill lookup failed for ${page.checklistId}", e)
                null
            }
            if (fillId == null) {
                logger.error(
                    TAG,
                    "importance toggle: no default fill for checklist ${page.checklistId}",
                    IllegalStateException("missing default fill"),
                )
                // closeSheets() below dismisses the sheet either way, so without this the star simply
                // never appears and nothing explains why — a silent failure dressed as a completed tap.
                emitMessage(Res.string.inbox_task_update_failed)
                closeSheets()
                return@launch
            }
            repository.togglePriority(fillId, taskId).onFailure { e ->
                logger.error(TAG, "importance toggle failed for task $taskId: ${e.message}", e)
                emitMessage(Res.string.inbox_task_update_failed)
            }
            closeSheets()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private fun closeSheets() {
        _sheetForTaskId.value = null
        _movePickerOpen.value = false
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
                .map { item ->
                    InboxTask(
                        fillItemId = item.id,
                        templateItemId = item.templateItemId,
                        text = item.text,
                        checked = item.checked,
                        priority = item.priority,
                    )
                },
        )
    }

    private companion object {
        /** Wire values of [AnalyticsParams.SOURCE] on `inbox_quick_added`. */
        const val SOURCE_INBOX = "inbox"
        const val SOURCE_PROJECT = "project"
    }
}
