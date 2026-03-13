package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_cooking
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_education
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_events
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_finance
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_fitness
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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
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
    val wasTemplateStepSkipped: Boolean = false,
    val createdChecklistId: Long? = null,
    val preview: PreviewState = PreviewState(),
    val discoverMore: DiscoverMoreState = DiscoverMoreState(),
    val error: String? = null
) : State

data class PreviewState(
    val items: List<PreviewChecklistItem> = emptyList(),
    val originalItemCount: Int = 0
)

data class PreviewChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false
)

data class DiscoverMoreState(
    val reminderCompleted: Boolean = false,
    val widgetCompleted: Boolean = false,
    val shareCompleted: Boolean = false,
    val shareText: String? = null,
    // Reminder sheet state
    val showReminderSheet: Boolean = false,
    val activeReminderTab: ReminderTab = ReminderTab.ONCE,
    val currentReminder: Long? = null,
    val currentRepeatRule: ReminderRepeatRule? = null,
    val pendingRepeatConfig: PendingRepeatConfig? = null,
    val showEndConditionPicker: Boolean = false,
    val repeatRuleSummary: String? = null,
    // Custom date/time picker
    val showCustomPicker: Boolean = false,
    val customPickerDateMillis: Long? = null,
    val customPickerMinDateMillis: Long = 0L,
    val customPickerInitialHour: Int = 9,
    val isCustomTimeInPast: Boolean = false,
)

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
    DiscoverMore,
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
    TRAVEL(Res.string.onboarding_interactive_category_travel, "\u2708\uFE0F", listOf("travel"), "travel_packing"),
    HOME(Res.string.onboarding_interactive_category_home, "\uD83C\uDFE0", listOf("real_estate", "home"), "moving_house"),
    SHOPPING(Res.string.onboarding_interactive_category_shopping, "\uD83D\uDED2", listOf("shopping"), "grocery_essentials"),
    WORK(Res.string.onboarding_interactive_category_work, "\uD83D\uDCBC", listOf("work"), "meeting_prep"),
    HEALTH(Res.string.onboarding_interactive_category_health, "\uD83D\uDCAA", listOf("health"), "doctor_visit"),
    EDUCATION(Res.string.onboarding_interactive_category_education, "\uD83D\uDCDA", listOf("education"), "study_plan"),
    FITNESS(Res.string.onboarding_interactive_category_fitness, "\uD83C\uDFCB\uFE0F", listOf("fitness"), "gym_workout"),
    COOKING(Res.string.onboarding_interactive_category_cooking, "\uD83C\uDF73", listOf("cooking"), "meal_prep"),
    FINANCE(Res.string.onboarding_interactive_category_finance, "\uD83D\uDCB0", listOf("finance"), "monthly_budget"),
    EVENTS(Res.string.onboarding_interactive_category_events, "\uD83C\uDF89", listOf("events"), "party_planning")
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
    data class OnPreviewItemToggle(val itemId: String) : InteractiveOnboardingIntent
    data object OnSkip : InteractiveOnboardingIntent
    data object OnBack : InteractiveOnboardingIntent

    // Discover More step
    data object OnWidgetInstructionDone : InteractiveOnboardingIntent
    data object OnShareCompleted : InteractiveOnboardingIntent
    data object OnDiscoverMoreContinue : InteractiveOnboardingIntent

    // Reminders (shared ReminderSheet)
    data object OnReminderClick : InteractiveOnboardingIntent
    data class OnReminderPresetSelected(val triggerAtMillis: Long) : InteractiveOnboardingIntent
    data object OnCustomDateRequested : InteractiveOnboardingIntent
    data class OnDateSelected(val dateMillis: Long) : InteractiveOnboardingIntent
    data class OnTimeSelected(val hour: Int, val minute: Int) : InteractiveOnboardingIntent
    data class OnCustomTimeChanged(val hour: Int, val minute: Int) : InteractiveOnboardingIntent
    data object OnRemoveReminder : InteractiveOnboardingIntent
    data object OnDismissReminderUI : InteractiveOnboardingIntent
    data class OnReminderTabSelected(val tab: ReminderTab) : InteractiveOnboardingIntent

    // Repeat schedule
    data class OnRepeatTypeSelected(val type: RepeatType) : InteractiveOnboardingIntent
    data class OnSmartPresetSelected(val config: PendingRepeatConfig) : InteractiveOnboardingIntent
    data class OnRepeatIntervalChanged(val interval: Int) : InteractiveOnboardingIntent
    data class OnWeekDayToggled(val dayNumber: Int) : InteractiveOnboardingIntent
    data class OnResetChecksToggled(val enabled: Boolean) : InteractiveOnboardingIntent
    data class OnRepeatTimeChanged(val hour: Int, val minute: Int) : InteractiveOnboardingIntent
    data object OnSaveRepeatSchedule : InteractiveOnboardingIntent
    data object OnRemoveRepeatSchedule : InteractiveOnboardingIntent

    // End condition
    data object OnEndConditionClick : InteractiveOnboardingIntent
    data class OnEndConditionSelected(val condition: RepeatEndCondition) : InteractiveOnboardingIntent
    data object OnDismissEndConditionPicker : InteractiveOnboardingIntent
}
