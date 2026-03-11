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
import com.antonchuraev.homesearchchecklist.feature.user.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InteractiveOnboardingViewModel(
    private val navigator: AppNavigator,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val templatesRepository: TemplatesRepository,
    private val checklistRepository: ChecklistRepository,
    private val analyticsTracker: AnalyticsTracker
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
            InteractiveOnboardingIntent.OnContinueFromCustomize -> handleContinueFromCustomize()
            InteractiveOnboardingIntent.OnCreatingComplete -> handleCreatingComplete()
            InteractiveOnboardingIntent.OnSaveChecklist -> handleSaveChecklist()
            InteractiveOnboardingIntent.OnSkip -> handleSkip()
            InteractiveOnboardingIntent.OnBack -> handleBack()
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
        _screenState.update {
            it.copy(
                selectedStyle = style,
                currentStep = InteractiveOnboardingStep.TemplateSelection
            )
        }
        trackStep("style_selected", "style" to style.name)
    }

    private fun handleTemplateSelected(template: ChecklistTemplate) {
        val style = _screenState.value.selectedStyle ?: OrganizingStyle.DETAILED
        val category = _screenState.value.selectedCategory
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
        category: OnboardingCategory?
    ): List<String> {
        return when (style) {
            OrganizingStyle.MINIMALIST -> items.take(5)
            OrganizingStyle.DETAILED -> items
            OrganizingStyle.CHAOTIC -> items + getExtraItems(category)
        }
    }

    private fun getExtraItems(category: OnboardingCategory?): List<String> {
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
            null -> emptyList()
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
        val enabledItems = state.customizedItems.filter { it.isEnabled }
        if (enabledItems.isEmpty() || state.isCreatingChecklist) return

        viewModelScope.launch {
            _screenState.update { it.copy(isCreatingChecklist = true) }
            try {
                val checklist = Checklist(
                    name = state.checklistName.ifBlank {
                        state.selectedTemplate?.name ?: "My Checklist"
                    },
                    items = enabledItems.map { ChecklistItem(text = it.text) }
                )
                checklistRepository.addChecklist(checklist)
                _screenState.update {
                    it.copy(
                        isCreatingChecklist = false,
                        checklistCreated = true,
                        currentStep = InteractiveOnboardingStep.Paywall
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
        viewModelScope.launch {
            analyticsTracker.event(
                "onboarding_skipped",
                mapOf("variant" to "interactive", "step" to state.currentStep.name)
            )
            completeOnboarding()
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
                _screenState.update {
                    it.copy(
                        currentStep = InteractiveOnboardingStep.TemplateSelection,
                        selectedTemplate = null,
                        customizedItems = emptyList(),
                        checklistName = ""
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
            InteractiveOnboardingStep.Paywall -> {
                _screenState.update {
                    it.copy(currentStep = InteractiveOnboardingStep.ChecklistPreview)
                }
            }
        }
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
