package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_checklist_groceries
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_checklist_todo
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_checklist_trip
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_checklist_workout
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_chip_groceries
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_chip_todo
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_chip_trip
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_chip_workout
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_default_item_1
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_default_item_2
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_default_item_3
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_default_item_4
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_groceries_1
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_groceries_2
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_groceries_3
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_groceries_4
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_groceries_5
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_groceries_6
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_todo_1
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_todo_2
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_todo_3
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_todo_4
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_todo_5
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_trip_1
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_trip_2
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_trip_3
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_trip_4
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_trip_5
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_trip_6
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_workout_1
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_workout_2
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_workout_3
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_workout_4
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_items_workout_5
import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import org.jetbrains.compose.resources.StringResource

/**
 * "AI Welcome" onboarding ("Toki" style) — a calm, 4-step intro that frames the app as an AI
 * assistant that does the work for you, ending with a mandatory first-checklist creation step
 * (closes the largest activation leak: users who finish onboarding without ever creating a list).
 *
 * Navigation is button-driven (no swipe pager) so the back/skip semantics are explicit and
 * identical on every platform — see [WelcomeOnboardingStep] for the order.
 */
data class WelcomeOnboardingState(
    val currentStep: WelcomeOnboardingStep = WelcomeOnboardingStep.Welcome,
    val inputText: String = "",
    val selectedTemplateKey: String? = null,
    val isCreating: Boolean = false,
    val error: String? = null,
) : State

/**
 * The four screens, in forward order. [ordinal] drives the progress bar and the
 * forward/back slide direction in the animated transition.
 */
enum class WelcomeOnboardingStep {
    Welcome,
    Capture,
    Value,
    FirstChecklist,
}

/**
 * Starter suggestions shown as tappable chips on the final [WelcomeOnboardingStep.FirstChecklist]
 * step. Each carries its own [labelRes] (chip text), [checklistNameRes] (the name used when the chip
 * is the chosen seed for the created checklist), and [itemRes] (the hardcoded starter items the
 * created checklist is pre-filled with — NO AI, since Create/Analyze are out of reach here). Kept
 * self-contained — these are onboarding-only starters, intentionally NOT the full template catalog
 * (which would re-introduce the heavy interactive flow this lightweight onboarding replaces).
 *
 * The ViewModel resolves [itemRes]/[DEFAULT_STARTER_ITEMS] to text via the suspend `getString`
 * (Compose Resources must never be touched in the domain/ViewModel-as-non-Composable other than via
 * `getString`); this enum only holds the resource keys.
 */
enum class WelcomeStarterTemplate(
    val key: String,
    val labelRes: StringResource,
    val checklistNameRes: StringResource,
    val itemRes: List<StringResource>,
) {
    GROCERIES(
        key = "groceries",
        labelRes = Res.string.onboarding_welcome_chip_groceries,
        checklistNameRes = Res.string.onboarding_welcome_checklist_groceries,
        itemRes = listOf(
            Res.string.onboarding_welcome_items_groceries_1,
            Res.string.onboarding_welcome_items_groceries_2,
            Res.string.onboarding_welcome_items_groceries_3,
            Res.string.onboarding_welcome_items_groceries_4,
            Res.string.onboarding_welcome_items_groceries_5,
            Res.string.onboarding_welcome_items_groceries_6,
        ),
    ),
    TRIP(
        key = "trip",
        labelRes = Res.string.onboarding_welcome_chip_trip,
        checklistNameRes = Res.string.onboarding_welcome_checklist_trip,
        itemRes = listOf(
            Res.string.onboarding_welcome_items_trip_1,
            Res.string.onboarding_welcome_items_trip_2,
            Res.string.onboarding_welcome_items_trip_3,
            Res.string.onboarding_welcome_items_trip_4,
            Res.string.onboarding_welcome_items_trip_5,
            Res.string.onboarding_welcome_items_trip_6,
        ),
    ),
    WORKOUT(
        key = "workout",
        labelRes = Res.string.onboarding_welcome_chip_workout,
        checklistNameRes = Res.string.onboarding_welcome_checklist_workout,
        itemRes = listOf(
            Res.string.onboarding_welcome_items_workout_1,
            Res.string.onboarding_welcome_items_workout_2,
            Res.string.onboarding_welcome_items_workout_3,
            Res.string.onboarding_welcome_items_workout_4,
            Res.string.onboarding_welcome_items_workout_5,
        ),
    ),
    TODO(
        key = "todo",
        labelRes = Res.string.onboarding_welcome_chip_todo,
        checklistNameRes = Res.string.onboarding_welcome_checklist_todo,
        itemRes = listOf(
            Res.string.onboarding_welcome_items_todo_1,
            Res.string.onboarding_welcome_items_todo_2,
            Res.string.onboarding_welcome_items_todo_3,
            Res.string.onboarding_welcome_items_todo_4,
            Res.string.onboarding_welcome_items_todo_5,
        ),
    ),
    ;

    companion object {
        fun fromKey(key: String?): WelcomeStarterTemplate? = entries.firstOrNull { it.key == key }

        /**
         * Items used for the "empty input, no chip" branch — getting-started tips (mirrors the
         * splash auto-seed spirit) so the mandatory final step always lands on a non-empty,
         * self-explanatory checklist instead of a blank one.
         */
        val DEFAULT_STARTER_ITEMS: List<StringResource> = listOf(
            Res.string.onboarding_welcome_default_item_1,
            Res.string.onboarding_welcome_default_item_2,
            Res.string.onboarding_welcome_default_item_3,
            Res.string.onboarding_welcome_default_item_4,
        )
    }
}

/**
 * Resolves a [StringResource] to text. The production binding is the suspend Compose Resources
 * `getString`, which touches `android.content.res.Resources` and therefore throws in plain JVM
 * host unit tests (same limitation the splash starter-checklist path hits). Injecting it lets the
 * three-branch first-checklist resolution be unit-tested deterministically without a real
 * resource table — tests pass a fake that returns the key name.
 */
fun interface WelcomeStringResolver {
    suspend fun resolve(res: StringResource): String
}

sealed interface WelcomeOnboardingIntent : Intent {
    data object OnNext : WelcomeOnboardingIntent
    data object OnBack : WelcomeOnboardingIntent
    data object OnSkip : WelcomeOnboardingIntent
    data class OnInputChanged(val text: String) : WelcomeOnboardingIntent
    data class OnTemplateSelected(val key: String) : WelcomeOnboardingIntent
    data object OnCreateFirstChecklist : WelcomeOnboardingIntent

    /**
     * "More ways to start" card on the final step — opens the Analyze hub (Photo/PDF/voice/link)
     * so the user can seed the first checklist from richer input. Completes onboarding first, then
     * hands off (mirrors the AI-Analyze branch of [OnCreateFirstChecklist]).
     */
    data object OnMoreWaysToStart : WelcomeOnboardingIntent
}

sealed interface WelcomeOnboardingSideEffect : SideEffect {
    /**
     * Resolve [messageKey] to a localized string in the Screen (Compose Resources must not be
     * touched in the ViewModel/domain) and show it in a snackbar.
     */
    data class ShowSnackbar(val messageKey: String) : WelcomeOnboardingSideEffect
}
