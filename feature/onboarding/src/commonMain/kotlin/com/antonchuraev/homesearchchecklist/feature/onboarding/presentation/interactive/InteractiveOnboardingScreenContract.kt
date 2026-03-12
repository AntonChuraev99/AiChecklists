package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_education
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_health
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_home
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_shopping
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_travel
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_work
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_chaotic
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_chaotic_desc
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_detailed
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_detailed_desc
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_minimalist
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_minimalist_desc
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import org.jetbrains.compose.resources.StringResource

data class InteractiveOnboardingState(
    val currentStep: InteractiveOnboardingStep = InteractiveOnboardingStep.CategorySelection,
    val selectedCategory: OnboardingCategory? = null,
    val selectedStyle: OrganizingStyle? = null,
    val availableTemplates: List<ChecklistTemplate> = emptyList(),
    val selectedTemplate: ChecklistTemplate? = null,
    val customizedItems: List<CustomizableItem> = emptyList(),
    val checklistName: String = "",
    val separateCompleted: Boolean = false,
    val autoDeleteCompleted: Boolean = false,
    val isCreatingChecklist: Boolean = false,
    val checklistCreated: Boolean = false,
    val error: String? = null
) : State

data class CustomizableItem(
    val text: String,
    val isEnabled: Boolean = true
)

enum class InteractiveOnboardingStep {
    CategorySelection,
    StyleSelection,
    TemplateSelection,
    Customize,
    Creating,
    ChecklistPreview,
    Paywall
}

enum class OrganizingStyle(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val emoji: String
) {
    MINIMALIST(
        Res.string.onboarding_interactive_style_minimalist,
        Res.string.onboarding_interactive_style_minimalist_desc,
        "\u2728" // ✨
    ),
    DETAILED(
        Res.string.onboarding_interactive_style_detailed,
        Res.string.onboarding_interactive_style_detailed_desc,
        "\uD83D\uDCCB" // 📋
    ),
    CHAOTIC(
        Res.string.onboarding_interactive_style_chaotic,
        Res.string.onboarding_interactive_style_chaotic_desc,
        "\uD83C\uDF2A\uFE0F" // 🌪️
    )
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
    data class OnCategorySelected(val category: OnboardingCategory) : InteractiveOnboardingIntent
    data class OnStyleSelected(val style: OrganizingStyle) : InteractiveOnboardingIntent
    data class OnTemplateSelected(val template: ChecklistTemplate) : InteractiveOnboardingIntent
    data class OnToggleItem(val index: Int) : InteractiveOnboardingIntent
    data class OnChecklistNameChanged(val name: String) : InteractiveOnboardingIntent
    data object OnToggleSeparateCompleted : InteractiveOnboardingIntent
    data object OnToggleAutoDeleteCompleted : InteractiveOnboardingIntent
    data object OnContinueFromCustomize : InteractiveOnboardingIntent
    data object OnCreatingComplete : InteractiveOnboardingIntent
    data object OnSaveChecklist : InteractiveOnboardingIntent
    data object OnSkip : InteractiveOnboardingIntent
    data object OnBack : InteractiveOnboardingIntent
}
