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

    private var templates: List<ChecklistTemplate> = emptyList()

    init {
        viewModelScope.launch {
            templates = templatesRepository.getTemplates()
        }
        analyticsTracker.event("onboarding_started", mapOf("variant" to "interactive"))
    }

    override fun onIntent(intent: InteractiveOnboardingIntent) {
        when (intent) {
            InteractiveOnboardingIntent.OnGetStarted -> handleGetStarted()
            is InteractiveOnboardingIntent.OnCategorySelected -> handleCategorySelected(intent.category)
            InteractiveOnboardingIntent.OnSaveChecklist -> handleSaveChecklist()
            InteractiveOnboardingIntent.OnSkip -> handleSkip()
            InteractiveOnboardingIntent.OnBack -> handleBack()
        }
    }

    private fun handleGetStarted() {
        trackStep("welcome_completed")
        _screenState.update { it.copy(currentStep = InteractiveOnboardingStep.CategorySelection) }
    }

    private fun handleCategorySelected(category: OnboardingCategory) {
        _screenState.update { it.copy(selectedCategory = category) }
        trackStep("category_selected", "category" to category.name)
        matchTemplate(category)
    }

    private fun matchTemplate(category: OnboardingCategory) {
        val template = templates.firstOrNull { it.id == category.preferredTemplateId }
            ?: templates.firstOrNull { category.templateCategories.contains(it.category) }
            ?: buildGenericFallback(category)

        _screenState.update {
            it.copy(
                matchedTemplate = template,
                currentStep = InteractiveOnboardingStep.ChecklistPreview
            )
        }
    }

    private fun buildGenericFallback(category: OnboardingCategory): ChecklistTemplate {
        return ChecklistTemplate(
            id = "onboarding_fallback_${category.name.lowercase()}",
            name = "My Checklist",
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

    private fun handleSaveChecklist() {
        val template = _screenState.value.matchedTemplate ?: return
        if (_screenState.value.isCreatingChecklist) return

        viewModelScope.launch {
            _screenState.update { it.copy(isCreatingChecklist = true) }
            try {
                val checklist = Checklist(
                    name = template.name,
                    items = template.items.map { ChecklistItem(text = it) }
                )
                checklistRepository.addChecklist(checklist)
                _screenState.update {
                    it.copy(
                        isCreatingChecklist = false,
                        checklistCreated = true,
                        currentStep = InteractiveOnboardingStep.Paywall
                    )
                }
                trackStep("checklist_created", "template" to template.id)
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
        val currentStep = _screenState.value.currentStep
        when (currentStep) {
            InteractiveOnboardingStep.CategorySelection -> {
                _screenState.update { it.copy(currentStep = InteractiveOnboardingStep.Welcome) }
            }
            InteractiveOnboardingStep.ChecklistPreview -> {
                _screenState.update {
                    it.copy(
                        currentStep = InteractiveOnboardingStep.CategorySelection,
                        matchedTemplate = null,
                        selectedCategory = null
                    )
                }
            }
            InteractiveOnboardingStep.Paywall -> {
                _screenState.update {
                    it.copy(currentStep = InteractiveOnboardingStep.ChecklistPreview)
                }
            }
            InteractiveOnboardingStep.Welcome -> {
                // No-op: first screen, nothing to go back to
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

    private fun trackStep(stepName: String, vararg params: Pair<String, String>) {
        val baseParams = mutableMapOf("variant" to "interactive", "step" to stepName)
        params.forEach { baseParams[it.first] = it.second }
        analyticsTracker.event("onboarding_step_completed", baseParams)
    }
}
