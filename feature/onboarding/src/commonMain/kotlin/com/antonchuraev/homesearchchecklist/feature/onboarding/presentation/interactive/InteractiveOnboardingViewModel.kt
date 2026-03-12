package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.formatter.ChecklistFormatter
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class InteractiveOnboardingViewModel(
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
        analyticsTracker.event("onboarding_started", mapOf("variant" to "interactive"))
    }

    override fun onIntent(intent: InteractiveOnboardingIntent) {
        when (intent) {
            is InteractiveOnboardingIntent.OnCategorySelected -> handleCategorySelected(intent.category)
            is InteractiveOnboardingIntent.OnStyleSelected -> handleStyleSelected(intent.style)
            is InteractiveOnboardingIntent.OnTemplateSelected -> handleTemplateSelected(intent.template)
            is InteractiveOnboardingIntent.OnToggleItem -> handleToggleItem(intent.index)
            is InteractiveOnboardingIntent.OnChecklistNameChanged -> handleChecklistNameChanged(intent.name)
            InteractiveOnboardingIntent.OnToggleSeparateCompleted -> _screenState.update { it.copy(separateCompleted = !it.separateCompleted) }
            InteractiveOnboardingIntent.OnToggleAutoDeleteCompleted -> _screenState.update { it.copy(autoDeleteCompleted = !it.autoDeleteCompleted) }
            InteractiveOnboardingIntent.OnContinueFromCustomize -> handleContinueFromCustomize()
            InteractiveOnboardingIntent.OnCreatingComplete -> handleCreatingComplete()
            InteractiveOnboardingIntent.OnSaveChecklist -> handleSaveChecklist()
            InteractiveOnboardingIntent.OnSkip -> handleSkip()
            InteractiveOnboardingIntent.OnBack -> handleBack()
            is InteractiveOnboardingIntent.OnReminderPresetSelected -> handleReminderPreset(intent.preset)
            InteractiveOnboardingIntent.OnWidgetInstructionDone -> handleWidgetDone()
            InteractiveOnboardingIntent.OnShareCompleted -> handleShareCompleted()
            InteractiveOnboardingIntent.OnDiscoverMoreContinue -> handleDiscoverMoreContinue()
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
        _screenState.update { it.copy(currentStep = InteractiveOnboardingStep.ChecklistPreview) }
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
                    it.copy(currentStep = InteractiveOnboardingStep.Customize)
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

    private fun handleReminderPreset(preset: ReminderPreset) {
        val checklistId = _screenState.value.createdChecklistId ?: return
        viewModelScope.launch {
            try {
                val now = Clock.System.now()
                val tz = TimeZone.currentSystemDefault()
                val today = now.toLocalDateTime(tz)

                when (preset) {
                    ReminderPreset.TONIGHT -> {
                        // One-shot: today at 21:00
                        val todayAt21 = LocalDateTime(
                            today.date, LocalTime(21, 0)
                        )
                        val triggerMillis = todayAt21.toInstant(tz).toEpochMilliseconds()
                        val adjustedTrigger = if (triggerMillis <= now.toEpochMilliseconds()) {
                            triggerMillis + 24 * 60 * 60 * 1000L // Next day if past 9 PM
                        } else triggerMillis
                        checklistRepository.setReminder(checklistId, adjustedTrigger)
                        reminderScheduler.scheduleReminder(checklistId, adjustedTrigger)
                    }
                    ReminderPreset.DAILY -> {
                        // Recurring: daily at 9:00 AM
                        val rule = ReminderRepeatRule(type = RepeatType.DAILY)
                        val timeMinutes = 9 * 60 // 9:00 AM
                        val tomorrowAt9 = LocalDateTime(
                            today.date, LocalTime(9, 0)
                        )
                        var triggerMillis = tomorrowAt9.toInstant(tz).toEpochMilliseconds()
                        if (triggerMillis <= now.toEpochMilliseconds()) {
                            triggerMillis += 24 * 60 * 60 * 1000L
                        }
                        checklistRepository.setRepeatSchedule(checklistId, rule, timeMinutes, triggerMillis)
                        reminderScheduler.scheduleRepeat(checklistId, triggerMillis)
                    }
                    ReminderPreset.WEEKLY -> {
                        // Recurring: weekly on Monday at 9:00 AM
                        val rule = ReminderRepeatRule(
                            type = RepeatType.WEEKLY,
                            weekDays = setOf(1) // Monday (ISO day number)
                        )
                        val timeMinutes = 9 * 60
                        val nextMonday = run {
                            // DayOfWeek: MONDAY=0..SUNDAY=6 in kotlinx-datetime
                            val dayOfWeek = today.dayOfWeek.ordinal // 0=Monday
                            val daysUntilMonday = if (dayOfWeek == 0) 7 else (7 - dayOfWeek)
                            val mondayDate = today.date.plus(DatePeriod(days = daysUntilMonday))
                            LocalDateTime(mondayDate, LocalTime(9, 0))
                        }
                        val triggerMillis = nextMonday.toInstant(tz).toEpochMilliseconds()
                        checklistRepository.setRepeatSchedule(checklistId, rule, timeMinutes, triggerMillis)
                        reminderScheduler.scheduleRepeat(checklistId, triggerMillis)
                    }
                }

                _screenState.update {
                    it.copy(discoverMore = it.discoverMore.copy(reminderCompleted = true))
                }
                trackStep("discover_more_reminder", "preset" to preset.name)
            } catch (e: Exception) {
                analyticsTracker.event(
                    "onboarding_reminder_error",
                    mapOf("error" to e.message.orEmpty())
                )
            }
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
}
