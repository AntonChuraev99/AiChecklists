package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.combinePickerResults
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.resolvePresetName
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.ChecklistFormatter
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.*

class InteractiveOnboardingViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val navigator: AppNavigator,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val templatesRepository: TemplatesRepository,
    private val checklistRepository: ChecklistRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val reminderScheduler: ChecklistReminderScheduler,
    private val checklistFormatter: ChecklistFormatter
) : AppViewModel<InteractiveOnboardingState, InteractiveOnboardingIntent, Nothing>() {

    private val _screenState = MutableStateFlow(InteractiveOnboardingState())
    override val screenState: StateFlow<InteractiveOnboardingState> = _screenState.asStateFlow()

    private var allTemplates: List<ChecklistTemplate> = emptyList()

    init {
        viewModelScope.launch {
            allTemplates = templatesRepository.getTemplates()
        }
        val alreadyTracked = savedStateHandle.get<Boolean>(KEY_STARTED_TRACKED) == true
        if (!alreadyTracked) {
            analyticsTracker.event("onboarding_started", mapOf("variant" to "interactive"))
            savedStateHandle[KEY_STARTED_TRACKED] = true
        }
        // Always track ViewModel creation for diagnostics (helps identify process death)
        analyticsTracker.event("onboarding_vm_created", mapOf(
            "variant" to "interactive",
            "is_restored" to alreadyTracked.toString()
        ))
    }

    override fun onIntent(intent: InteractiveOnboardingIntent) {
        when (intent) {
            is InteractiveOnboardingIntent.OnCategorySelected -> handleCategorySelected(intent.category)
            is InteractiveOnboardingIntent.OnStyleSelected -> handleStyleSelected(intent.style)
            is InteractiveOnboardingIntent.OnTemplateSelected -> handleTemplateSelected(intent.template)
            is InteractiveOnboardingIntent.OnToggleItem -> handleToggleItem(intent.index)
            is InteractiveOnboardingIntent.OnChecklistNameChanged -> handleChecklistNameChanged(intent.name)
            InteractiveOnboardingIntent.OnToggleSeparateCompleted -> _screenState.update {
                val newValue = !it.separateCompleted
                it.copy(
                    separateCompleted = newValue,
                    autoDeleteCompleted = if (newValue) false else it.autoDeleteCompleted,
                )
            }
            InteractiveOnboardingIntent.OnToggleAutoDeleteCompleted -> _screenState.update {
                val newValue = !it.autoDeleteCompleted
                it.copy(
                    autoDeleteCompleted = newValue,
                    separateCompleted = if (newValue) false else it.separateCompleted,
                )
            }
            InteractiveOnboardingIntent.OnContinueFromCustomize -> handleContinueFromCustomize()
            InteractiveOnboardingIntent.OnCreatingComplete -> handleCreatingComplete()
            is InteractiveOnboardingIntent.OnPreviewItemToggle -> handlePreviewItemToggle(intent.itemId)
            InteractiveOnboardingIntent.OnSaveChecklist -> handleSaveChecklist()
            InteractiveOnboardingIntent.OnSkip -> handleSkip()
            InteractiveOnboardingIntent.OnBack -> handleBack()
            InteractiveOnboardingIntent.OnWidgetInstructionDone -> handleWidgetDone()
            InteractiveOnboardingIntent.OnShareCompleted -> handleShareCompleted()
            InteractiveOnboardingIntent.OnDiscoverMoreContinue -> handleDiscoverMoreContinue()

            // Reminders (shared ReminderSheet)
            InteractiveOnboardingIntent.OnReminderClick -> updateDiscoverMore { it.copy(showReminderSheet = true) }
            is InteractiveOnboardingIntent.OnReminderPresetSelected -> {
                val now = Clock.System.now().toEpochMilliseconds()
                if (intent.triggerAtMillis <= now) return
                saveOnboardingReminder(intent.triggerAtMillis)
                updateDiscoverMore { it.copy(showReminderSheet = false) }
            }
            InteractiveOnboardingIntent.OnCustomDateRequested -> {
                val tz = TimeZone.currentSystemDefault()
                val todayDate = Clock.System.now().toLocalDateTime(tz).date
                val todayUtcMidnight = LocalDateTime(todayDate, LocalTime(0, 0))
                    .toInstant(TimeZone.UTC).toEpochMilliseconds()
                updateDiscoverMore {
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
            is InteractiveOnboardingIntent.OnDateSelected -> {
                val tz = TimeZone.currentSystemDefault()
                val nowLocal = Clock.System.now().toLocalDateTime(tz)
                val selectedDate = Instant.fromEpochMilliseconds(intent.dateMillis)
                    .toLocalDateTime(TimeZone.UTC).date
                val isToday = selectedDate == nowLocal.date
                val initialHour = if (isToday) (nowLocal.hour + 1).coerceAtMost(23) else 9
                updateDiscoverMore {
                    it.copy(
                        customPickerDateMillis = intent.dateMillis,
                        customPickerInitialHour = initialHour,
                        isCustomTimeInPast = false
                    )
                }
            }
            is InteractiveOnboardingIntent.OnCustomTimeChanged -> {
                val dm = _screenState.value.discoverMore
                val dateMillis = dm.customPickerDateMillis ?: return
                val tz = TimeZone.currentSystemDefault()
                val nowLocal = Clock.System.now().toLocalDateTime(tz)
                val selectedDate = Instant.fromEpochMilliseconds(dateMillis)
                    .toLocalDateTime(TimeZone.UTC).date
                val isToday = selectedDate == nowLocal.date
                val isInPast = isToday && LocalTime(intent.hour, intent.minute) <= nowLocal.time
                updateDiscoverMore { it.copy(isCustomTimeInPast = isInPast) }
            }
            is InteractiveOnboardingIntent.OnTimeSelected -> {
                val dateMillis = _screenState.value.discoverMore.customPickerDateMillis ?: return
                val triggerAt = combinePickerResults(dateMillis, intent.hour, intent.minute)
                val now = Clock.System.now().toEpochMilliseconds()
                if (triggerAt <= now) return
                saveOnboardingReminder(triggerAt)
                updateDiscoverMore { it.copy(showCustomPicker = false, customPickerDateMillis = null) }
            }
            InteractiveOnboardingIntent.OnRemoveReminder -> {
                removeOnboardingReminder()
                updateDiscoverMore { it.copy(showReminderSheet = false) }
            }
            InteractiveOnboardingIntent.OnDismissReminderUI -> {
                updateDiscoverMore {
                    it.copy(
                        showReminderSheet = false,
                        showCustomPicker = false,
                        customPickerDateMillis = null,
                        pendingRepeatConfig = null,
                        showEndConditionPicker = false
                    )
                }
            }
            is InteractiveOnboardingIntent.OnReminderTabSelected -> handleReminderTabSelected(intent.tab)

            // Repeat schedule
            is InteractiveOnboardingIntent.OnRepeatTypeSelected -> handleRepeatTypeSelected(intent.type)
            is InteractiveOnboardingIntent.OnSmartPresetSelected -> updatePendingRepeatConfig { intent.config }
            is InteractiveOnboardingIntent.OnRepeatIntervalChanged -> updatePendingRepeatConfig { it.copy(interval = intent.interval.coerceIn(1, 99), isCustom = true) }
            is InteractiveOnboardingIntent.OnWeekDayToggled -> toggleWeekDay(intent.dayNumber)
            is InteractiveOnboardingIntent.OnResetChecksToggled -> updatePendingRepeatConfig { it.copy(resetChecks = intent.enabled) }
            is InteractiveOnboardingIntent.OnRepeatTimeChanged -> updatePendingRepeatConfig { it.copy(timeHour = intent.hour, timeMinute = intent.minute) }
            InteractiveOnboardingIntent.OnSaveRepeatSchedule -> saveRepeatSchedule()
            InteractiveOnboardingIntent.OnRemoveRepeatSchedule -> removeRepeatSchedule()

            // End condition
            InteractiveOnboardingIntent.OnEndConditionClick -> updateDiscoverMore { it.copy(showEndConditionPicker = true) }
            is InteractiveOnboardingIntent.OnEndConditionSelected -> {
                updatePendingRepeatConfig { it.copy(endCondition = intent.condition) }
                updateDiscoverMore { it.copy(showEndConditionPicker = false) }
            }
            InteractiveOnboardingIntent.OnDismissEndConditionPicker -> updateDiscoverMore { it.copy(showEndConditionPicker = false) }
        }
    }

    private fun handleCategorySelected(category: OnboardingCategory) {
        val categoryTemplates = allTemplates.filter {
            category.templateCategories.contains(it.category)
        }.ifEmpty {
            listOf(buildGenericFallback(category))
        }

        _screenState.update {
            it.copy(
                selectedCategory = category,
                availableTemplates = categoryTemplates,
                currentStep = InteractiveOnboardingStep.StyleSelection
            )
        }
        trackStep("category_selected", "category" to category.name)
    }

    private fun handleStyleSelected(style: OrganizingStyle) {
        val currentState = _screenState.value
        val categoryTemplates = currentState.availableTemplates

        if (categoryTemplates.size == 1) {
            val template = categoryTemplates.first()
            val category = currentState.selectedCategory ?: return
            val items = applyStyleToItems(template.items, style, category)
            _screenState.update {
                it.copy(
                    selectedStyle = style,
                    selectedTemplate = template,
                    customizedItems = items.map { text -> CustomizableItem(text = text) },
                    checklistName = template.name,
                    wasTemplateStepSkipped = true,
                    currentStep = InteractiveOnboardingStep.Customize
                )
            }
            trackStep("style_selected", "style" to style.name)
            trackStep("template_auto_selected", "template" to template.id)
        } else {
            _screenState.update {
                it.copy(
                    selectedStyle = style,
                    currentStep = InteractiveOnboardingStep.TemplateSelection
                )
            }
            trackStep("style_selected", "style" to style.name)
        }
    }

    private fun handleTemplateSelected(template: ChecklistTemplate) {
        val style = _screenState.value.selectedStyle ?: OrganizingStyle.DETAILED
        val category = _screenState.value.selectedCategory ?: return
        val items = applyStyleToItems(template.items, style, category)

        _screenState.update {
            it.copy(
                selectedTemplate = template,
                customizedItems = items.map { text -> CustomizableItem(text = text) },
                checklistName = template.name,
                currentStep = InteractiveOnboardingStep.Customize
            )
        }
        trackStep("template_selected", "template" to template.id)
    }

    private fun applyStyleToItems(
        items: List<String>,
        style: OrganizingStyle,
        category: OnboardingCategory
    ): List<String> {
        return when (style) {
            OrganizingStyle.MINIMALIST -> items.take(5)
            OrganizingStyle.DETAILED -> items
            OrganizingStyle.CHAOTIC -> items + getExtraItems(category)
        }
    }

    private fun getExtraItems(category: OnboardingCategory): List<String> {
        return when (category) {
            OnboardingCategory.TRAVEL -> listOf(
                "Download entertainment for offline",
                "Emergency snack stash",
                "Photo of important documents"
            )
            OnboardingCategory.HOME -> listOf(
                "Check smoke detector batteries",
                "Find local emergency numbers",
                "Introduce yourself to neighbors"
            )
            OnboardingCategory.SHOPPING -> listOf(
                "Check what you already have at home",
                "Set a budget limit",
                "Bring reusable bags"
            )
            OnboardingCategory.WORK -> listOf(
                "Prepare a backup plan",
                "Set calendar reminders",
                "Schedule a 5-minute break"
            )
            OnboardingCategory.HEALTH -> listOf(
                "Set a follow-up reminder",
                "Check insurance coverage",
                "Note questions as they come"
            )
            OnboardingCategory.EDUCATION -> listOf(
                "Find a study buddy",
                "Set reward for completion",
                "Prepare a quiet study space"
            )
            OnboardingCategory.FITNESS -> listOf(
                "Stretch before starting",
                "Prepare a water bottle",
                "Track your progress"
            )
            OnboardingCategory.COOKING -> listOf(
                "Check pantry for ingredients",
                "Preheat oven if needed",
                "Clean as you go"
            )
            OnboardingCategory.FINANCE -> listOf(
                "Review recent transactions",
                "Set a savings target",
                "Check subscription renewals"
            )
            OnboardingCategory.EVENTS -> listOf(
                "Confirm guest count",
                "Check weather forecast",
                "Prepare a backup plan"
            )
        }
    }

    private fun handleToggleItem(index: Int) {
        _screenState.update { state ->
            val updated = state.customizedItems.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(isEnabled = !updated[index].isEnabled)
            }
            state.copy(customizedItems = updated)
        }
    }

    private fun handleChecklistNameChanged(name: String) {
        _screenState.update { it.copy(checklistName = name) }
    }

    private fun handleContinueFromCustomize() {
        trackStep("customization_completed")
        _screenState.update { it.copy(currentStep = InteractiveOnboardingStep.Creating) }
    }

    private fun handleCreatingComplete() {
        val state = _screenState.value
        val previewItems = state.customizedItems
            .filter { it.isEnabled }
            .mapIndexed { index, item ->
                PreviewChecklistItem(id = "preview_$index", text = item.text)
            }
        _screenState.update {
            it.copy(
                currentStep = InteractiveOnboardingStep.ChecklistPreview,
                preview = PreviewState(
                    items = previewItems,
                    originalItemCount = previewItems.size
                )
            )
        }
    }

    private fun handlePreviewItemToggle(itemId: String) {
        _screenState.update { state ->
            val items = state.preview.items
            val item = items.firstOrNull { it.id == itemId } ?: return@update state
            val updatedItems = if (!item.isChecked && state.autoDeleteCompleted) {
                items.filter { it.id != itemId }
            } else {
                items.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it }
            }
            state.copy(preview = state.preview.copy(items = updatedItems))
        }
        analyticsTracker.event(
            "onboarding_preview_item_toggled",
            mapOf(
                "auto_delete" to _screenState.value.autoDeleteCompleted.toString(),
                "separate_completed" to _screenState.value.separateCompleted.toString()
            )
        )
    }

    private fun handleSaveChecklist() {
        val state = _screenState.value
        if (state.createdChecklistId != null) return // Guard: already saved
        val enabledItems = state.customizedItems.filter { it.isEnabled }
        if (enabledItems.isEmpty() || state.isCreatingChecklist) return

        viewModelScope.launch {
            _screenState.update { it.copy(isCreatingChecklist = true) }
            try {
                val checklist = Checklist(
                    name = state.checklistName.ifBlank {
                        state.selectedTemplate?.name ?: "My Checklist"
                    },
                    items = enabledItems.map { ChecklistItem(text = it.text) },
                    separateCompleted = state.separateCompleted,
                    autoDeleteCompleted = state.autoDeleteCompleted
                )
                val checklistId = checklistRepository.addChecklist(checklist)

                // Prepare share text
                val savedChecklist = checklistRepository.getChecklistById(checklistId)
                val fill = checklistRepository.getDefaultFillOneShot(checklistId)
                val shareText = if (savedChecklist != null && fill != null) {
                    checklistFormatter.formatAsText(savedChecklist, fill)
                } else null

                _screenState.update {
                    it.copy(
                        isCreatingChecklist = false,
                        checklistCreated = true,
                        createdChecklistId = checklistId,
                        discoverMore = it.discoverMore.copy(shareText = shareText),
                        currentStep = InteractiveOnboardingStep.DiscoverMore
                    )
                }
                trackStep(
                    "checklist_created",
                    "template" to (state.selectedTemplate?.id ?: "unknown"),
                    "style" to (state.selectedStyle?.name ?: "unknown"),
                    "items_count" to enabledItems.size.toString()
                )
            } catch (e: Exception) {
                _screenState.update {
                    it.copy(
                        isCreatingChecklist = false,
                        currentStep = InteractiveOnboardingStep.Paywall
                    )
                }
                analyticsTracker.event(
                    "onboarding_checklist_error",
                    mapOf("error" to e.message.orEmpty())
                )
            }
        }
    }

    private fun handleSkip() {
        val state = _screenState.value
        analyticsTracker.event(
            "onboarding_skipped",
            mapOf("variant" to "interactive", "step" to state.currentStep.name)
        )
        when (state.currentStep) {
            InteractiveOnboardingStep.DiscoverMore -> {
                _screenState.update { it.copy(currentStep = InteractiveOnboardingStep.Paywall) }
            }
            else -> {
                viewModelScope.launch { completeOnboarding() }
            }
        }
    }

    private fun handleBack() {
        when (_screenState.value.currentStep) {
            InteractiveOnboardingStep.CategorySelection -> {
                // First screen, no-op
            }
            InteractiveOnboardingStep.StyleSelection -> {
                _screenState.update {
                    it.copy(
                        currentStep = InteractiveOnboardingStep.CategorySelection,
                        selectedCategory = null,
                        availableTemplates = emptyList()
                    )
                }
            }
            InteractiveOnboardingStep.TemplateSelection -> {
                _screenState.update {
                    it.copy(
                        currentStep = InteractiveOnboardingStep.StyleSelection,
                        selectedStyle = null
                    )
                }
            }
            InteractiveOnboardingStep.Customize -> {
                val wasSkipped = _screenState.value.wasTemplateStepSkipped
                _screenState.update {
                    it.copy(
                        currentStep = if (wasSkipped) {
                            InteractiveOnboardingStep.StyleSelection
                        } else {
                            InteractiveOnboardingStep.TemplateSelection
                        },
                        selectedStyle = if (wasSkipped) null else it.selectedStyle,
                        selectedTemplate = null,
                        customizedItems = emptyList(),
                        checklistName = "",
                        separateCompleted = false,
                        autoDeleteCompleted = false,
                        wasTemplateStepSkipped = false
                    )
                }
            }
            InteractiveOnboardingStep.Creating -> {
                // Can't go back from loading animation
            }
            InteractiveOnboardingStep.ChecklistPreview -> {
                _screenState.update {
                    it.copy(
                        currentStep = InteractiveOnboardingStep.Customize,
                        preview = PreviewState()
                    )
                }
            }
            InteractiveOnboardingStep.DiscoverMore -> {
                // Can't go back from discover more — checklist already saved
            }
            InteractiveOnboardingStep.Paywall -> {
                _screenState.update {
                    it.copy(currentStep = InteractiveOnboardingStep.DiscoverMore)
                }
            }
        }
    }

    // ─── Reminder helpers ─────────────────────────────────────────────

    private fun saveOnboardingReminder(triggerAtMillis: Long) {
        val checklistId = _screenState.value.createdChecklistId ?: return
        viewModelScope.launch {
            try {
                checklistRepository.setReminder(checklistId, triggerAtMillis)
                reminderScheduler.scheduleReminder(checklistId, triggerAtMillis)
                updateDiscoverMore {
                    it.copy(reminderCompleted = true, currentReminder = triggerAtMillis)
                }
                trackStep("discover_more_reminder", "type" to "once")
            } catch (e: Exception) {
                analyticsTracker.event(
                    "onboarding_reminder_error",
                    mapOf("error" to e.message.orEmpty())
                )
            }
        }
    }

    private fun removeOnboardingReminder() {
        val checklistId = _screenState.value.createdChecklistId ?: return
        viewModelScope.launch {
            checklistRepository.setReminder(checklistId, null)
            reminderScheduler.cancelReminder(checklistId)
            val dm = _screenState.value.discoverMore
            if (dm.currentRepeatRule != null) {
                checklistRepository.clearRepeatSchedule(checklistId)
                reminderScheduler.cancelRepeat(checklistId)
            }
            updateDiscoverMore {
                it.copy(
                    reminderCompleted = false,
                    currentReminder = null,
                    currentRepeatRule = null,
                    repeatRuleSummary = null
                )
            }
        }
    }

    private fun handleReminderTabSelected(tab: ReminderTab) {
        if (tab == ReminderTab.REPEAT && _screenState.value.discoverMore.pendingRepeatConfig == null) {
            updateDiscoverMore {
                it.copy(activeReminderTab = ReminderTab.REPEAT, pendingRepeatConfig = PendingRepeatConfig())
            }
        } else {
            updateDiscoverMore { it.copy(activeReminderTab = tab) }
        }
    }

    private fun handleRepeatTypeSelected(type: RepeatType) {
        updatePendingRepeatConfig {
            it.copy(type = type, isCustom = false, interval = 1, weekDays = emptySet())
        }
    }

    private fun toggleWeekDay(dayNumber: Int) {
        updatePendingRepeatConfig { config ->
            val updated = if (dayNumber in config.weekDays) config.weekDays - dayNumber else config.weekDays + dayNumber
            config.copy(weekDays = updated, isCustom = true)
        }
    }

    private fun saveRepeatSchedule() {
        val checklistId = _screenState.value.createdChecklistId ?: return
        val config = _screenState.value.discoverMore.pendingRepeatConfig ?: return
        val rule = config.toRule()
        val timeMinutes = config.timeHour * 60 + config.timeMinute

        viewModelScope.launch {
            try {
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

                checklistRepository.setRepeatSchedule(checklistId, rule, timeMinutes, firstTriggerAt)
                reminderScheduler.scheduleRepeat(checklistId, firstTriggerAt)

                updateDiscoverMore {
                    it.copy(
                        reminderCompleted = true,
                        currentRepeatRule = rule,
                        showReminderSheet = false,
                        pendingRepeatConfig = null,
                        repeatRuleSummary = buildRepeatSummary(config)
                    )
                }

                analyticsTracker.event("repeat_schedule_set", buildMap {
                    put("type", rule.type.name)
                    put("interval", rule.interval.toString())
                    put("preset", resolvePresetName(config))
                    put("source", "onboarding")
                })
            } catch (e: Exception) {
                analyticsTracker.event(
                    "onboarding_reminder_error",
                    mapOf("error" to e.message.orEmpty())
                )
            }
        }
    }

    private fun removeRepeatSchedule() {
        val checklistId = _screenState.value.createdChecklistId ?: return
        viewModelScope.launch {
            checklistRepository.clearRepeatSchedule(checklistId)
            reminderScheduler.cancelRepeat(checklistId)
            updateDiscoverMore {
                it.copy(
                    reminderCompleted = false,
                    currentRepeatRule = null,
                    showReminderSheet = false,
                    pendingRepeatConfig = null,
                    repeatRuleSummary = null
                )
            }
        }
    }

    private inline fun updateDiscoverMore(update: (DiscoverMoreState) -> DiscoverMoreState) {
        _screenState.update { it.copy(discoverMore = update(it.discoverMore)) }
    }

    private inline fun updatePendingRepeatConfig(update: (PendingRepeatConfig) -> PendingRepeatConfig) {
        updateDiscoverMore { dm ->
            val current = dm.pendingRepeatConfig ?: PendingRepeatConfig()
            dm.copy(pendingRepeatConfig = update(current))
        }
    }

    private fun handleWidgetDone() {
        _screenState.update {
            it.copy(discoverMore = it.discoverMore.copy(widgetCompleted = true))
        }
        trackStep("discover_more_widget")
    }

    private fun handleShareCompleted() {
        _screenState.update {
            it.copy(discoverMore = it.discoverMore.copy(shareCompleted = true))
        }
        trackStep("discover_more_share")
    }

    private fun handleDiscoverMoreContinue() {
        val dm = _screenState.value.discoverMore
        val completedCount = listOf(dm.reminderCompleted, dm.widgetCompleted, dm.shareCompleted).count { it }
        trackStep("discover_more_completed", "actions_done" to completedCount.toString())
        _screenState.update { it.copy(currentStep = InteractiveOnboardingStep.Paywall) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            analyticsTracker.event(
                "onboarding_completed",
                mapOf(
                    "variant" to "interactive",
                    "checklist_created" to _screenState.value.checklistCreated.toString()
                )
            )
            completeOnboardingUseCase()
            navigator.navigateToMainScreen(clearBackStack = true)
        }
    }

    private fun buildGenericFallback(category: OnboardingCategory): ChecklistTemplate {
        return ChecklistTemplate(
            id = "onboarding_fallback_${category.name.lowercase()}",
            name = "My Checklist",
            description = "A fresh start",
            icon = "checklist",
            category = category.templateCategories.first(),
            items = listOf(
                "First item to check",
                "Second item to check",
                "Third item to check",
                "Fourth item to check",
                "Fifth item to check"
            )
        )
    }

    private fun trackStep(stepName: String, vararg params: Pair<String, String>) {
        val baseParams = mutableMapOf("variant" to "interactive", "step" to stepName)
        params.forEach { baseParams[it.first] = it.second }
        analyticsTracker.event("onboarding_step_completed", baseParams)
    }

    companion object {
        private const val KEY_STARTED_TRACKED = "onboarding_started_tracked"
    }
}
