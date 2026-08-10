package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.ChecklistSource
import com.antonchuraev.homesearchchecklist.core.common.api.CreateFormVariant
import com.antonchuraev.homesearchchecklist.core.common.api.CreatedListKind
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val LOG_TAG = "CreateProject"

/** Paywall `source` values, kept next to the call sites that emit them. */
private const val PAYWALL_SOURCE_CHECKLIST_LIMIT = "checklist_limit"
private const val PAYWALL_SOURCE_WEEKLY_LIMIT = "weekly_limit"
private const val PAYWALL_SOURCE_RECURRING_LIMIT = "create_recurring_limit"

/**
 * Free-tier ceiling for checklist-level ONE-SHOT reminders.
 *
 * Not from [UserLimits]: no Remote Config key describes this ceiling anywhere in the app — the
 * checklist-detail flow enforces the same "one free one-shot reminder" rule with the same literal.
 * Duplicating it here keeps both entry points gating identically, which is the property that
 * matters; unifying them belongs in `UserLimits` and is tracked in the round report, not solved by
 * inventing a second, different policy on this screen.
 */
private const val FREE_ONE_SHOT_REMINDER_LIMIT = 1

/** Fallback time of day for a staged repeat whose config never reached the time picker. */
private const val DEFAULT_REPEAT_MINUTES = 9 * 60

class CreateChecklistViewModel(
    private val editChecklistId: Long?,
    private val initialText: String? = null,
    private val checklistRepository: ChecklistRepository,
    private val appNavigator: AppNavigator,
    private val analyticsTracker: AnalyticsTracker,
    private val getUserLimitsUseCase: GetUserLimitsUseCase,
    private val reminderScheduler: ChecklistReminderScheduler,
    private val logger: AppLogger,
    /**
     * v2 nav arm ("Projects"): render and behave as the redesigned project form.
     *
     * Default `false` = the CONTROL arm, whose screen must stay what it was before the redesign.
     * Three behaviours branch on it and nothing else does:
     *  - where a new task lands (v2 appends at the end, under the inline row; v1 prepended);
     *  - which blank-name copy is produced (`create_error_project_name_required` vs
     *    `create_error_name_required` — "project" vs "checklist");
     *  - where creation lands (v2 opens the new project, v1 resets the stack to the app root).
     *
     * Everything else the v2 form adds (settings, Weekly, staged reminder) is simply never sent by
     * the v1 screen, so it needs no gate of its own.
     */
    private val useProjectForm: Boolean = false,
    /**
     * Weekly lists are NOT a flag on the standard create path: [CreateWeeklyChecklistUseCase]
     * enforces its own free-tier gate and writes `items = emptyList()` + `viewMode = Weekly`.
     * Branching by hand here would drift from the "My Week" entry point that already uses it.
     *
     * Defaulted from the two dependencies it is composed of so the existing test call sites (which
     * pass the other arguments by name) keep compiling; Koin injects the shared instance.
     */
    private val createWeeklyChecklistUseCase: CreateWeeklyChecklistUseCase =
        CreateWeeklyChecklistUseCase(checklistRepository, getUserLimitsUseCase),
) : AppViewModel<CreateChecklistState, CreateChecklistIntent, Nothing>() {

    private val _screenState = MutableStateFlow(CreateChecklistState(
        isEditMode = editChecklistId != null,
        editChecklistId = editChecklistId,
        // Prefill items from shared/selected text (ACTION_PROCESS_TEXT "New checklist" action).
        // Only applies in create mode — edit mode loads from the persisted checklist.
        items = if (editChecklistId == null) splitIntoItems(initialText) else emptyList()
    ))
    override val screenState: StateFlow<CreateChecklistState> = _screenState.asStateFlow()

    /**
     * The row as it was loaded in edit mode.
     *
     * Kept so saving can `copy()` it instead of rebuilding a bare `Checklist(id, name, items)`:
     * `updateChecklist` writes the whole entity, so a rebuilt object silently wipes every field the
     * form does not show — `cloudId`, `reminderAt`, `repeatRule`, `position`, `isInbox`.
     */
    private var loadedChecklist: Checklist? = null

    /** Latest emission of [GetUserLimitsUseCase]; null until the first one arrives. */
    private var latestLimits: UserLimits? = null

    /**
     * Which form this instance is rendering, as the [AnalyticsParams.FORM_VARIANT] wire value.
     *
     * Derived from [useProjectForm] — the same flag the UI branches on — so the reported arm cannot
     * disagree with the form the user actually saw. Deliberately NOT read from the sticky `nav_arm`
     * user-property: that one is written once per process and goes stale as soon as the shell is
     * switched in Settings, which would attribute creates to the arm the user just left.
     */
    private val formVariant: String =
        if (useProjectForm) CreateFormVariant.V2.wire else CreateFormVariant.CLASSIC.wire

    init {
        if (editChecklistId != null) {
            loadChecklist(editChecklistId)
        } else {
            // Only observe limits in create mode — edit mode is never gated
            observeUserLimits()
        }
    }

    /**
     * Splits prefilled [text] into checklist items: one item per non-blank line.
     * Single-line text becomes a single item. Returns empty for null/blank input.
     */
    private fun splitIntoItems(text: String?): List<ChecklistItem> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ChecklistItem(it, false) }
    }

    private fun observeUserLimits() {
        viewModelScope.launch {
            getUserLimitsUseCase().collect { limits ->
                latestLimits = limits
                _screenState.update {
                    it.copy(
                        canCreateChecklist = limits.canCreateChecklist,
                        // Published, never hardcoded: the banner names the ceiling Remote Config is
                        // actually serving. A constant here is the `FREE_CHECKLIST_LIMIT = 4` bug.
                        maxChecklists = limits.maxChecklists,
                        canCreateWeekly = limits.canCreateWeeklyChecklist,
                    )
                }
            }
        }
    }

    private fun loadChecklist(checklistId: Long) {
        viewModelScope.launch {
            val checklist = checklistRepository.getChecklistById(checklistId)
            if (checklist != null) {
                loadedChecklist = checklist
                _screenState.update {
                    it.copy(
                        name = checklist.name,
                        items = checklist.items,
                        weeklyMode = checklist.viewMode == ChecklistViewMode.Weekly,
                        foldersEnabled = checklist.foldersEnabled,
                        separateCompleted = checklist.separateCompleted,
                        autoDeleteCompleted = checklist.autoDeleteCompleted,
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
                it.copy(name = intent.name, nameError = null, nameInvalid = false)
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

            CreateChecklistIntent.OnNameFocusConsumed -> _screenState.update {
                it.copy(nameFocusConsumed = true)
            }
            is CreateChecklistIntent.OnToggleMoreOptions -> _screenState.update {
                // Flips what the user is LOOKING at, which the screen reports: the default depends on
                // window size and is not knowable here.
                it.copy(moreOptionsExpanded = !intent.currentlyExpanded)
            }

            is CreateChecklistIntent.OnWeeklyToggled -> onWeeklyToggled(intent.enabled)
            CreateChecklistIntent.OnWeeklySwitchConfirm -> _screenState.update {
                // A Weekly list is created empty by CreateWeeklyChecklistUseCase — dropping the
                // drafted tasks here is what the confirmation just warned about.
                it.copy(
                    weeklyMode = true,
                    weeklySwitchConfirmOpen = false,
                    items = emptyList(),
                    newItemText = "",
                    editingItemId = null,
                    editingItemText = "",
                    foldersEnabled = false,
                )
            }
            CreateChecklistIntent.OnWeeklySwitchDismiss -> _screenState.update {
                it.copy(weeklySwitchConfirmOpen = false)
            }
            CreateChecklistIntent.OnWeeklyLockClick ->
                appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_WEEKLY_LIMIT)

            is CreateChecklistIntent.OnFoldersToggled -> _screenState.update {
                it.copy(foldersEnabled = intent.enabled)
            }
            is CreateChecklistIntent.OnSeparateCompletedToggled -> _screenState.update {
                it.copy(separateCompleted = intent.enabled)
            }
            is CreateChecklistIntent.OnAutoDeleteToggled -> _screenState.update {
                it.copy(autoDeleteCompleted = intent.enabled)
            }
            CreateChecklistIntent.OnLimitBannerUpgradeClick -> {
                trackChecklistLimitHit(AnalyticsEvents.Checklist.LIMIT_SOURCE_CREATE_BANNER)
                appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_CHECKLIST_LIMIT)
            }

            // ── Reminder ────────────────────────────────────────────────────────────────────────
            CreateChecklistIntent.OnReminderClick -> onReminderClick()
            is CreateChecklistIntent.OnReminderTabSelected -> onReminderTabSelected(intent.tab)
            is CreateChecklistIntent.OnReminderPresetSelected -> stageOneShotReminder(intent.triggerAtMillis)
            CreateChecklistIntent.OnCustomDateRequested -> openCustomPicker()
            is CreateChecklistIntent.OnCustomDateSelected -> onCustomDateSelected(intent.dateMillis)
            is CreateChecklistIntent.OnCustomTimeChanged -> onCustomTimeChanged(intent.hour, intent.minute)
            is CreateChecklistIntent.OnCustomTimeSelected -> onCustomTimeSelected(intent.hour, intent.minute)
            CreateChecklistIntent.OnRemoveReminder -> _screenState.update {
                it.copy(reminderAt = null, reminderSheetOpen = false)
            }
            CreateChecklistIntent.OnDismissReminderUI -> _screenState.update {
                it.copy(
                    reminderSheetOpen = false,
                    reminderSheetLocked = false,
                    showCustomPicker = false,
                    customPickerDateMillis = null,
                    pendingRepeatConfig = null,
                    showEndConditionPicker = false,
                )
            }

            is CreateChecklistIntent.OnRepeatTypeSelected -> updatePendingRepeatConfig {
                it.copy(type = intent.type, isCustom = false, interval = 1, weekDays = emptySet())
            }
            is CreateChecklistIntent.OnSmartPresetSelected -> updatePendingRepeatConfig { intent.config }
            is CreateChecklistIntent.OnRepeatIntervalChanged -> updatePendingRepeatConfig {
                it.copy(interval = intent.interval.coerceIn(1, 99), isCustom = true)
            }
            is CreateChecklistIntent.OnWeekDayToggled -> updatePendingRepeatConfig { config ->
                val days = if (intent.dayNumber in config.weekDays) {
                    config.weekDays - intent.dayNumber
                } else {
                    config.weekDays + intent.dayNumber
                }
                config.copy(weekDays = days, isCustom = true)
            }
            is CreateChecklistIntent.OnResetChecksToggled -> updatePendingRepeatConfig {
                it.copy(resetChecks = intent.enabled)
            }
            is CreateChecklistIntent.OnRepeatTimeChanged -> updatePendingRepeatConfig {
                it.copy(timeHour = intent.hour, timeMinute = intent.minute)
            }
            CreateChecklistIntent.OnSaveRepeat -> stageRepeatSchedule()
            CreateChecklistIntent.OnRemoveRepeat -> _screenState.update {
                it.copy(
                    repeatRule = null,
                    repeatTimeOfDayMinutes = null,
                    pendingRepeatConfig = null,
                    reminderSheetOpen = false,
                )
            }

            CreateChecklistIntent.OnEndConditionClick -> _screenState.update {
                it.copy(showEndConditionPicker = true)
            }
            is CreateChecklistIntent.OnEndConditionSelected -> {
                updatePendingRepeatConfig { it.copy(endCondition = intent.condition) }
                _screenState.update { it.copy(showEndConditionPicker = false) }
            }
            CreateChecklistIntent.OnDismissEndConditionPicker -> _screenState.update {
                it.copy(showEndConditionPicker = false)
            }
            CreateChecklistIntent.OnReminderUpgradeClick -> {
                _screenState.update { it.copy(reminderSheetOpen = false, reminderSheetLocked = false) }
                appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_RECURRING_LIMIT)
            }
        }
    }

    private fun onWeeklyToggled(enabled: Boolean) {
        if (!enabled) {
            _screenState.update { it.copy(weeklyMode = false, weeklySwitchConfirmOpen = false) }
            return
        }
        val state = _screenState.value
        if (!state.canCreateWeekly) {
            // The switch is drawn as a padlock in this case; a tap that silently refused would be
            // the "no feedback" bug, so route to the paywall instead.
            appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_WEEKLY_LIMIT)
            return
        }
        val hasDraftedTasks = state.items.isNotEmpty() || state.newItemText.isNotBlank()
        if (hasDraftedTasks) {
            _screenState.update { it.copy(weeklySwitchConfirmOpen = true) }
        } else {
            _screenState.update { it.copy(weeklyMode = true, foldersEnabled = false) }
        }
    }

    // ── Reminder: staged, never persisted while the form is open ────────────────────────────────

    /**
     * Opens the shared reminder sheet, locked when the free one-shot quota is already spent.
     *
     * The lock is decided BEFORE the sheet appears, not when Save is tapped: the sheet's own locked
     * banner explains the ceiling and offers the upgrade, whereas a refusal at save time would be a
     * dead tap on a control the user was allowed to touch.
     */
    private fun onReminderClick() {
        viewModelScope.launch {
            val isPremium = latestLimits?.isPremium ?: false
            val locked = try {
                !isPremium && checklistRepository.countActiveReminders() >= FREE_ONE_SHOT_REMINDER_LIMIT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never block on a read failure: a broken count must not silently take away a
                // feature the user is entitled to.
                logger.error(LOG_TAG, "active reminder count failed: ${e.message}", e)
                false
            }
            _screenState.update {
                it.copy(
                    reminderSheetOpen = true,
                    reminderSheetLocked = locked,
                    activeReminderTab = if (it.repeatRule != null && it.reminderAt == null) {
                        ReminderTab.REPEAT
                    } else {
                        ReminderTab.ONCE
                    },
                )
            }
        }
    }

    /**
     * Switching to REPEAT seeds the editable config — and gates it against the RC-driven recurring
     * quota first, the same way the checklist-detail flow does.
     */
    private fun onReminderTabSelected(tab: ReminderTab) {
        if (tab != ReminderTab.REPEAT) {
            _screenState.update { it.copy(activeReminderTab = tab) }
            return
        }
        if (_screenState.value.pendingRepeatConfig != null) {
            _screenState.update { it.copy(activeReminderTab = ReminderTab.REPEAT) }
            return
        }
        viewModelScope.launch {
            val limits = latestLimits
            val activeCount = try {
                checklistRepository.countActiveRepeatSchedules()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(LOG_TAG, "active repeat count failed: ${e.message}", e)
                0
            }
            // limits == null only while the first emission is in flight — don't block, mirroring
            // GetUserLimitsUseCase's "never falsely gate" intent.
            val canCreate = limits?.canCreateRecurringReminder(activeCount) ?: true
            if (!canCreate) {
                // Sourced, unlike the checklist-detail emit sites of the same event: an unqualified
                // `recurring_limit_hit` cannot say WHERE the quota bit, so the create screen's share
                // of it was invisible.
                analyticsTracker.event(AnalyticsEvents.Reminder.RECURRING_LIMIT_HIT, mapOf(
                    AnalyticsParams.SOURCE to AnalyticsEvents.Reminder.LIMIT_SOURCE_CREATE_PROJECT,
                    AnalyticsParams.FORM_VARIANT to formVariant,
                ))
                _screenState.update { it.copy(reminderSheetOpen = false) }
                appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_RECURRING_LIMIT)
                return@launch
            }
            _screenState.update {
                it.copy(activeReminderTab = ReminderTab.REPEAT, pendingRepeatConfig = PendingRepeatConfig())
            }
        }
    }

    private fun stageOneShotReminder(triggerAtMillis: Long) {
        if (triggerAtMillis <= currentTimeMillis()) {
            // Only reachable if a preset is tapped exactly as it expires; say so rather than
            // swallowing the tap.
            logger.warning(LOG_TAG, "reminder preset in the past ignored: $triggerAtMillis")
            return
        }
        _screenState.update {
            it.copy(reminderAt = triggerAtMillis, reminderSheetOpen = false, showCustomPicker = false)
        }
    }

    private fun openCustomPicker() {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(tz).date
        // The Material date picker works in UTC millis, so its floor is today's UTC midnight.
        val todayUtcMidnight = LocalDateTime(today, LocalTime(0, 0))
            .toInstant(TimeZone.UTC).toEpochMilliseconds()
        _screenState.update {
            it.copy(
                reminderSheetOpen = false,
                showCustomPicker = true,
                customPickerDateMillis = null,
                customPickerMinDateMillis = todayUtcMidnight,
                customPickerInitialHour = 9,
                isCustomTimeInPast = false,
            )
        }
    }

    private fun onCustomDateSelected(dateMillis: Long) {
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(tz)
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val isToday = selectedDate == nowLocal.date
        _screenState.update {
            it.copy(
                customPickerDateMillis = dateMillis,
                customPickerInitialHour = if (isToday) (nowLocal.hour + 1).coerceAtMost(23) else 9,
                isCustomTimeInPast = false,
            )
        }
    }

    private fun onCustomTimeChanged(hour: Int, minute: Int) {
        val dateMillis = _screenState.value.customPickerDateMillis ?: return
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(tz)
        val selectedDate = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val isInPast = selectedDate == nowLocal.date && LocalTime(hour, minute) <= nowLocal.time
        _screenState.update { it.copy(isCustomTimeInPast = isInPast) }
    }

    private fun onCustomTimeSelected(hour: Int, minute: Int) {
        val dateMillis = _screenState.value.customPickerDateMillis ?: return
        val tz = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
        val triggerAt = LocalDateTime(date, LocalTime(hour, minute)).toInstant(tz).toEpochMilliseconds()
        if (triggerAt <= currentTimeMillis()) {
            // The picker already renders `isCustomTimeInPast`, so the user has the explanation on
            // screen; refusing here only stops an unfireable alarm from being staged.
            logger.warning(LOG_TAG, "custom reminder time in the past ignored: $triggerAt")
            return
        }
        _screenState.update {
            it.copy(
                reminderAt = triggerAt,
                showCustomPicker = false,
                customPickerDateMillis = null,
                isCustomTimeInPast = false,
            )
        }
    }

    private fun stageRepeatSchedule() {
        val config = _screenState.value.pendingRepeatConfig ?: return
        _screenState.update {
            it.copy(
                repeatRule = config.toRule(),
                repeatTimeOfDayMinutes = config.timeHour * 60 + config.timeMinute,
                pendingRepeatConfig = null,
                reminderSheetOpen = false,
            )
        }
    }

    private inline fun updatePendingRepeatConfig(update: (PendingRepeatConfig) -> PendingRepeatConfig) {
        _screenState.update {
            it.copy(pendingRepeatConfig = update(it.pendingRepeatConfig ?: PendingRepeatConfig()))
        }
    }

    /**
     * Persists + schedules whatever the form staged, against the id `addChecklist` just returned.
     *
     * Deliberately AFTER creation: `ReminderScheduler` keys its alarms by checklist id, which does
     * not exist while the form is open (DESIGN_SPEC §11 pitfall 9). A failure here must not lose the
     * project the user just created, so it is logged as an error (Crashlytics) rather than thrown —
     * the project is real either way, and its reminder is visible (and re-settable) on the detail
     * screen the user lands on.
     */
    private suspend fun applyStagedReminder(checklistId: Long, state: CreateChecklistState) {
        val once = state.reminderAt
        val repeat = state.repeatRule
        if (once == null && repeat == null) return
        try {
            if (once != null) {
                checklistRepository.setReminder(checklistId, once)
                reminderScheduler.scheduleReminder(checklistId, once)
            }
            if (repeat != null) {
                val minutes = state.repeatTimeOfDayMinutes ?: DEFAULT_REPEAT_MINUTES
                val firstTriggerAt = firstRepeatTriggerAt(minutes)
                checklistRepository.setRepeatSchedule(checklistId, repeat, minutes, firstTriggerAt)
                reminderScheduler.scheduleRepeat(checklistId, firstTriggerAt)
                analyticsTracker.event(AnalyticsEvents.Reminder.REPEAT_SCHEDULE_SET, mapOf(
                    "type" to repeat.type.name,
                    "interval" to repeat.interval.toString(),
                    AnalyticsParams.SOURCE to AnalyticsEvents.Reminder.LIMIT_SOURCE_CREATE_PROJECT,
                    AnalyticsParams.FORM_VARIANT to formVariant,
                ))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(LOG_TAG, "staged reminder not applied to checklist $checklistId: ${e.message}", e)
        }
    }

    /** Today at [timeOfDayMinutes] if that is still ahead, otherwise the same time tomorrow. */
    private fun firstRepeatTriggerAt(timeOfDayMinutes: Int): Long {
        val tz = TimeZone.currentSystemDefault()
        val nowMillis = currentTimeMillis()
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date
        val time = LocalTime(timeOfDayMinutes / 60, timeOfDayMinutes % 60)
        val todayTrigger = LocalDateTime(today, time).toInstant(tz).toEpochMilliseconds()
        if (todayTrigger > nowMillis) return todayTrigger
        return LocalDateTime(today.plus(1, DateTimeUnit.DAY), time).toInstant(tz).toEpochMilliseconds()
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
                val item = ChecklistItem(text, false)
                it.copy(
                    // v2 APPENDS: the inline "add a task" row sits at the END of the list (same as
                    // the checklist-detail screen), so a prepending model would push the row the
                    // user is typing into away from their finger and persist the list reversed.
                    // v1 prepended and must keep doing so — its input sits ABOVE the list.
                    items = if (useProjectForm) it.items + item else listOf(item) + it.items,
                    newItemText = ""
                )
            }
        }
    }

    private fun onSaveClick() {
        val currentState = _screenState.value

        // Gate: redirect free users at the checklist limit to paywall (create mode only)
        if (!currentState.isEditMode && !currentState.canCreateChecklist) {
            trackChecklistLimitHit(AnalyticsEvents.Checklist.LIMIT_SOURCE_CREATE_SAVE)
            appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_CHECKLIST_LIMIT)
            return
        }
        if (currentState.isSubmitting) {
            // Unreachable from the UI (AppButton swallows taps while `loading`); logged rather than
            // returned silently so a programmatic re-send is diagnosable instead of invisible.
            logger.warning(LOG_TAG, "duplicate submit ignored while a create is in flight")
            return
        }

        commitPendingEdit()

        // Auto-add unsaved text from input field before saving. Skipped in Weekly mode, where the
        // task section is not on screen at all and the list is created empty by definition.
        if (!_screenState.value.weeklyMode) {
            val unsavedText = _screenState.value.newItemText.trim()
            if (unsavedText.isNotBlank()) {
                addItemFromInput()
            }
        }

        val latestState = _screenState.value

        if (latestState.name.trim().isBlank()) {
            // Both flags go up SYNCHRONOUSLY, before the (suspending) copy lookup: the highlight,
            // the scroll back to the name field and the refocus are what tell the user where to
            // look, and they must happen even on the frame where the string is still resolving.
            _screenState.update {
                it.copy(nameInvalid = true, nameErrorFocusSignal = it.nameErrorFocusSignal + 1)
            }
            viewModelScope.launch {
                val key = if (useProjectForm) {
                    Res.string.create_error_project_name_required
                } else {
                    Res.string.create_error_name_required
                }
                val message = try {
                    getString(key)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Compose Resources resolve on every real target (Android / wasmJs / iOS) and can
                    // only fail in a plain JVM host test with no Android resource environment.
                    // Logged rather than swallowed, and the response does not depend on it: the field
                    // is already highlighted (nameInvalid) and scrolled into view.
                    logger.warning(LOG_TAG, "blank-name error copy unresolved: ${e.message}")
                    null
                }
                _screenState.update { it.copy(nameError = message) }
            }
            return
        }

        _screenState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            try {
                when {
                    latestState.isEditMode && latestState.editChecklistId != null ->
                        saveEdit(latestState)
                    latestState.weeklyMode -> createWeeklyProject(latestState)
                    else -> createProject(latestState)
                }
            } finally {
                // Not a catch: a failure must still surface as a crash/Crashlytics record rather
                // than be swallowed here. This only makes the CTA tappable again if it does.
                _screenState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private suspend fun saveEdit(state: CreateChecklistState) {
        val id = requireNotNull(state.editChecklistId)
        val loaded = loadedChecklist
        val updated = when {
            // Normal path: the form was seeded from this row, so its settings fields are the row's
            // own values (possibly edited) and are safe to write back.
            loaded != null -> loaded.copy(
                name = state.name.trim(),
                items = state.items,
                viewMode = if (state.weeklyMode) ChecklistViewMode.Weekly else ChecklistViewMode.Standard,
                foldersEnabled = state.foldersEnabled && !state.weeklyMode,
                separateCompleted = state.separateCompleted,
                autoDeleteCompleted = state.autoDeleteCompleted,
            )
            // Save tapped before the async load landed. `updateChecklist` writes the WHOLE entity,
            // so re-read it and touch ONLY what the user demonstrably typed: the settings fields in
            // state are still their defaults here, and copying them over would silently downgrade a
            // Weekly list to Standard and clear its folders.
            else -> checklistRepository.getChecklistById(id)?.copy(
                name = state.name.trim(),
                items = state.items,
            )
        } ?: run {
            // The row is genuinely gone (deleted from another surface while the form was open).
            logger.error(LOG_TAG, "editing checklist $id that no longer exists — writing a bare row")
            Checklist(id = id, name = state.name.trim(), items = state.items)
        }
        checklistRepository.updateChecklist(updated)
        appNavigator.onBack()
    }

    /**
     * The free project ceiling was reached — reported wherever the user meets it.
     *
     * Both affordances are the SAME ceiling but not the same act: the banner is a v2-only surface
     * the user chose to tap, the Save gate is a refusal in both arms. They shared one
     * `paywall_opened{source=checklist_limit}` and neither carried the arm, so the v2 banner was the
     * one create-screen path the rest of this instrumentation still could not see.
     */
    private fun trackChecklistLimitHit(source: String) {
        analyticsTracker.event(AnalyticsEvents.Checklist.LIMIT_HIT, mapOf(
            AnalyticsParams.SOURCE to source,
            AnalyticsParams.FORM_VARIANT to formVariant,
        ))
    }

    /**
     * The reported [AnalyticsParams.LIST_KIND] of a persisted row, mapped from the mode it was
     * written with.
     *
     * An exhaustive `when` rather than a literal per branch: a third [ChecklistViewMode] then fails
     * to compile here instead of silently reporting itself as `standard` forever — which is exactly
     * what [CreatedListKind]'s own contract promises.
     */
    private fun ChecklistViewMode.toCreatedListKind(): CreatedListKind = when (this) {
        ChecklistViewMode.Standard -> CreatedListKind.STANDARD
        ChecklistViewMode.Weekly -> CreatedListKind.WEEKLY
    }

    private suspend fun createProject(state: CreateChecklistState) {
        val checklist = Checklist(
            name = state.name.trim(),
            items = state.items,
            foldersEnabled = state.foldersEnabled,
            separateCompleted = state.separateCompleted,
            autoDeleteCompleted = state.autoDeleteCompleted,
        )
        val checklistId = checklistRepository.addChecklist(checklist)
        // SOURCE stays MANUAL on purpose — it is the reference value of every live create
        // dashboard, and re-pointing it would trade one blind spot for a broken series. The arm and
        // the list kind arrive as their own dimensions instead; the kind is read off the row that
        // was actually written, not asserted by the branch that got here.
        analyticsTracker.event(AnalyticsEvents.Checklist.CREATED, mapOf(
            AnalyticsParams.SOURCE to ChecklistSource.MANUAL.wire,
            AnalyticsParams.ITEM_COUNT to state.items.size,
            AnalyticsParams.FORM_VARIANT to formVariant,
            AnalyticsParams.LIST_KIND to checklist.viewMode.toCreatedListKind().wire,
        ))
        applyStagedReminder(checklistId, state)
        landOnCreatedProject(checklistId)
    }

    private suspend fun createWeeklyProject(state: CreateChecklistState) {
        when (val result = createWeeklyChecklistUseCase(state.name.trim())) {
            is CreateWeeklyChecklistUseCase.Result.Created -> {
                // A Weekly project is `source = manual, item_count = 0` — byte-identical to an
                // ordinary empty project until LIST_KIND says otherwise. Not reported as
                // ChecklistSource.WEEKLY: that value belongs to the "My Week" entry point, and
                // reusing it here would make the two entry points indistinguishable while also
                // draining the `manual` series the create dashboards are keyed on.
                //
                // The mode is named rather than read back: `CreateWeeklyChecklistUseCase` is the
                // single writer of `viewMode = Weekly` and returns only the new id. Re-reading the
                // row for a analytics param would add a DB round-trip and a nullable that the event
                // has no sensible answer for.
                analyticsTracker.event(AnalyticsEvents.Checklist.CREATED, mapOf(
                    AnalyticsParams.SOURCE to ChecklistSource.MANUAL.wire,
                    AnalyticsParams.ITEM_COUNT to 0,
                    AnalyticsParams.FORM_VARIANT to formVariant,
                    AnalyticsParams.LIST_KIND to ChecklistViewMode.Weekly.toCreatedListKind().wire,
                ))
                applyStagedReminder(result.checklistId, state)
                landOnCreatedProject(result.checklistId)
            }
            // The weekly quota can be spent between opening the form and submitting it; the
            // use case is the single source of truth for that gate.
            CreateWeeklyChecklistUseCase.Result.RequiresUpgrade ->
                appNavigator.navigateToPaywall(source = PAYWALL_SOURCE_WEEKLY_LIMIT)
        }
    }

    /**
     * v2 lands in the project that was just created; v1 keeps resetting the stack to the app root.
     *
     * `clearBackStack` pops back to the shell's root route before pushing, so the form is off the
     * stack (Back returns to the tab, not into a spent draft) — and, unlike
     * `navigateToMainScreen(clearBackStack = true)`, it does not throw a v2 user out of the tab they
     * started in. Arriving on the new project's detail IS the success confirmation, which is why
     * there is no snackbar. The control arm must NOT get this: its landing screen is part of the
     * baseline the experiment measures against.
     */
    private fun landOnCreatedProject(checklistId: Long) {
        if (useProjectForm) {
            appNavigator.navigateToChecklistDetail(checklistId = checklistId, clearBackStack = true)
        } else {
            appNavigator.navigateToMainScreen(clearBackStack = true)
        }
    }
}
