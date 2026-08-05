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

    /** "AI Welcome" onboarding ("Toki" style) — Android-only 4-step intro (see GetOnboardingVariantUseCase). */
    @Serializable
    data object WelcomeOnboarding : AppNavRoute

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
        // When true AND [initialText] is non-blank, analysis runs automatically once on mount
        // (no manual Analyze tap). Used by the new-user activation hero where the chip tap /
        // typed topic IS the explicit intent to generate. Default false preserves the
        // ACTION_PROCESS_TEXT contract (prefill only, user taps Analyze).
        val autoAnalyze: Boolean = false,
    ) : AppNavRoute

    @Serializable
    data object AnalyzeResultPreview : AppNavRoute

    @Serializable
    data class ChecklistDetail(
        val checklistId: Long,
        val focusItemId: String? = null,
        // Folder drill-down: id of the FOLDER node whose children are shown. null = checklist root.
        // Each drill-down pushes a new ChecklistDetail entry with this set, so Nav3 back/Up
        // walks the folder hierarchy via the back stack (no custom breadcrumb needed).
        val currentFolderId: String? = null,
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

    /**
     * "Gisti MCP" info screen (remote MCP server). Pushed from the navigation drawer as a
     * read-and-return detail page (back-arrow, keeps the shell underneath) — not a top-level
     * drawer tab, so it is intentionally absent from the shell's selected-destination set.
     */
    @Serializable
    data object Mcp : AppNavRoute

    @Serializable
    data object Onboardings : AppNavRoute

    /**
     * v2-nav-arm-only tab destination: the quick-capture "Inbox" home.
     *
     * Only ever pushed while `NavVariant.V2` is active — the control arm's back stack is rooted at
     * [Main] and never contains this key. The matching `entry<>` is nevertheless registered
     * UNCONDITIONALLY in App.kt: a `rememberSaveable`d back stack can survive process death across
     * an arm flip, and a route with no matching entry hard-crashes NavDisplay.
     */
    @Serializable
    data object Inbox : AppNavRoute

    /**
     * v2-only tab destination: the flat list of checklists ("Projects").
     *
     * A route of its own rather than reusing [Main], even though both list the same checklists.
     * [Main] is the v1 HOME screen — cards with cover images, progress bars, edit mode, the chat dock
     * and the activation hero — and it must keep rendering byte-identically for anyone who switched
     * back to the classic layout. Pointing the v2 tab at it meant every change to the tab was a
     * change to the v1 home screen; this key is what lets the two diverge.
     *
     * Registered UNCONDITIONALLY in App.kt for the same reason as [Inbox]: a `rememberSaveable`d
     * back stack can outlive a switch to the classic layout, and a route with no matching entry
     * hard-crashes NavDisplay.
     */
    @Serializable
    data object Projects : AppNavRoute

    /**
     * v2-nav-arm-only tab destination: "Overview", the standalone screen that re-hosts everything
     * the navigation drawer holds in the control arm (the drawer itself is unreachable in v2 because
     * the shell passes `drawerState = null` to every screen).
     *
     * There is deliberately NO `Projects` sibling — the Projects tab reuses [Main] so that entry,
     * its `ListDetailSceneStrategy.listPane` metadata and the two-pane behaviour stay byte-identical
     * in both arms, and `AnalyticsScreens.MAIN` keeps one continuous historical series.
     */
    @Serializable
    data object Overview : AppNavRoute

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
