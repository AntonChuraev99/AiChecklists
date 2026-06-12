package com.antonchuraev.homesearchchecklist.core.navigation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * All navigation destinations in the app.
 *
 * Implements [NavKey] so that NavDisplay (Navigation 3) can use each route
 * directly as a back-stack entry without an additional serialization step.
 * The [Serializable] annotation is retained for argument passing consistency.
 *
 * Stage 2: sealed interface now extends NavKey directly.
 * Previous Nav 2 approach used @Serializable routes consumed by NavController;
 * Nav 3 NavDisplay renders entries based on type identity.
 */
@Serializable
sealed interface AppNavRoute : NavKey {
    @Serializable
    data object Splash : AppNavRoute

    @Serializable
    data object Onboarding : AppNavRoute

    @Serializable
    data object InteractiveOnboarding : AppNavRoute

    @Serializable
    data object Main : AppNavRoute

    @Serializable
    sealed interface CreateChecklistRoute : AppNavRoute {
        @Serializable
        data class CreateChecklist(
            val templateId: Int? = null,
            val editChecklistId: Long? = null,
            // Prefilled item text (e.g. from the ACTION_PROCESS_TEXT system selection menu).
            // Split into one or more items in CreateChecklistViewModel. null = no prefill.
            val initialText: String? = null,
        ) : CreateChecklistRoute

        @Serializable
        data object Templates : CreateChecklistRoute

        @Serializable
        data class TemplatePreview(val templateId: String) : CreateChecklistRoute
    }

    @Serializable
    data object Debug : AppNavRoute

    @Serializable
    data object StoreScreenshot : AppNavRoute

    @Serializable
    data class Analyze(
        val checklistId: Long? = null,
        val fillDefault: Boolean = false,
        // Prefilled raw text (e.g. from ACTION_PROCESS_TEXT). When non-null the screen
        // pre-selects RAW_TEXT input and fills inputText WITHOUT auto-running analysis
        // (protects the AI-credit budget — the user taps Analyze themselves). null = no prefill.
        val initialText: String? = null,
    ) : AppNavRoute

    @Serializable
    data object AnalyzeResultPreview : AppNavRoute

    @Serializable
    data class ChecklistDetail(
        val checklistId: Long,
        val focusItemId: String? = null,
    ) : AppNavRoute

    @Serializable
    data class FillDetail(val fillId: Long) : AppNavRoute

    @Serializable
    data class FillsList(val checklistId: Long) : AppNavRoute

    @Serializable
    data class Paywall(
        val source: String = "unknown",
        val forceVariant: String? = null,  // "timeline" | "features" | "compare" | null (uses RC)
    ) : AppNavRoute

    @Serializable
    data class SubscriptionStatus(val showSuccessMessage: Boolean = false) : AppNavRoute

    @Serializable
    data class ShareChecklist(val checklistId: Long) : AppNavRoute

    @Serializable
    data object UpdateFeed : AppNavRoute

    @Serializable
    data object Settings : AppNavRoute

    @Serializable
    data object Today : AppNavRoute

    @Serializable
    data object Calendar : AppNavRoute

    @Serializable
    data object ScreenCatalog : AppNavRoute

    @Serializable
    data object AiChat : AppNavRoute

    @Serializable
    data object Onboardings : AppNavRoute

    /**
     * Picker shown after an ACTION_PROCESS_TEXT action that needs a target checklist.
     *
     * [purpose] decides what selecting a checklist does:
     * - [AddToChecklistPurpose.ADD_ITEM] (default): appends [text] as a single item and opens
     *   its detail screen (the "Add to checklist" action).
     * - [AddToChecklistPurpose.FILL_AI]: opens Analyze in fill-mode for the chosen checklist with
     *   [text] pre-filled as raw text (the "Fill (AI)" action), letting AI fill the template.
     */
    @Serializable
    data class AddToChecklistPicker(
        val text: String,
        val purpose: AddToChecklistPurpose = AddToChecklistPurpose.ADD_ITEM,
    ) : AppNavRoute
}

/**
 * Why the [AppNavRoute.AddToChecklistPicker] was opened — drives both the on-select behavior and
 * the screen title. @Serializable so it can travel inside the serializable route.
 */
@Serializable
enum class AddToChecklistPurpose {
    /** Append the shared text as one item to the selected checklist. */
    ADD_ITEM,

    /** Open Analyze in fill-mode for the selected checklist with the shared text pre-filled. */
    FILL_AI,
}
