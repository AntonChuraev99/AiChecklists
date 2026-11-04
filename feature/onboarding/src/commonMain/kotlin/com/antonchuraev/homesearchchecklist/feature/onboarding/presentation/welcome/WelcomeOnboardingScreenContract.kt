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
import org.jetbrains.compose.resources.StringResource

/**
 * Starter suggestions shown as tappable chips on the [OnboardingStep.TryIt] step; one tap creates
 * and opens the preset list. Each carries its own [labelRes] (chip text), [checklistNameRes] (the
 * created checklist's name), and [itemRes] (the hardcoded starter items it is pre-filled with — NO
 * AI, no credits). Kept
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
         * Getting-started tips: appended to the v2 Inbox on the Skip / Chat / Call exits (so the
         * user never lands on an empty Inbox) and used as the items of "Save as a plain list".
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
 * sample / chip / plain-list creation be unit-tested deterministically without a real resource
 * table — tests pass a fake that returns the key name.
 */
fun interface WelcomeStringResolver {
    suspend fun resolve(res: StringResource): String
}
