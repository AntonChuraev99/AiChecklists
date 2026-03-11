package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_education
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_health
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_home
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_shopping
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_travel
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_work
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import org.jetbrains.compose.resources.StringResource

data class InteractiveOnboardingState(
    val currentStep: InteractiveOnboardingStep = InteractiveOnboardingStep.Welcome,
    val selectedCategory: OnboardingCategory? = null,
    val matchedTemplate: ChecklistTemplate? = null,
    val isCreatingChecklist: Boolean = false,
    val checklistCreated: Boolean = false,
    val error: String? = null
) : State

enum class InteractiveOnboardingStep {
    Welcome,
    CategorySelection,
    ChecklistPreview,
    Paywall
}

enum class OnboardingCategory(
    val titleRes: StringResource,
    val icon: String,
    val templateCategories: List<String>,
    val preferredTemplateId: String
) {
    TRAVEL(Res.string.onboarding_interactive_category_travel, "\u2708\uFE0F", listOf("travel", "events"), "travel_packing"),
    HOME(Res.string.onboarding_interactive_category_home, "\uD83C\uDFE0", listOf("real_estate", "home"), "moving_house"),
    SHOPPING(Res.string.onboarding_interactive_category_shopping, "\uD83D\uDED2", listOf("shopping"), "grocery_essentials"),
    WORK(Res.string.onboarding_interactive_category_work, "\uD83D\uDCBC", listOf("work"), "meeting_prep"),
    HEALTH(Res.string.onboarding_interactive_category_health, "\uD83D\uDCAA", listOf("health"), "doctor_visit"),
    EDUCATION(Res.string.onboarding_interactive_category_education, "\uD83D\uDCDA", listOf("education"), "study_plan")
}

sealed interface InteractiveOnboardingIntent : Intent {
    data object OnGetStarted : InteractiveOnboardingIntent
    data class OnCategorySelected(val category: OnboardingCategory) : InteractiveOnboardingIntent
    data object OnSaveChecklist : InteractiveOnboardingIntent
    data object OnSkip : InteractiveOnboardingIntent
    data object OnBack : InteractiveOnboardingIntent
}
