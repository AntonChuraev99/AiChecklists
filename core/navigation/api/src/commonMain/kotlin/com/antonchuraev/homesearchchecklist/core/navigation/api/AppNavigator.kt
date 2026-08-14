package com.antonchuraev.homesearchchecklist.core.navigation.api

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import kotlinx.coroutines.flow.SharedFlow

interface AppNavigator {

    /**
     * Single source of truth for navigation state.
     *
     * NavDisplay observes this SnapshotStateList<NavKey> and renders the top entry
     * as the current screen. Mutations are synchronous — NavDisplay re-renders on
     * the next frame after any add/remove/clear operation.
     *
     * Stage 2: replaces Stage 1's StateFlow<List<AppNavRoute>> with Nav3 NavBackStack
     * (SnapshotStateList<NavKey>). The async Channel.BUFFERED race between ViewModel.init
     * and the Compose collector is eliminated — mutable state is visible immediately.
     */
    val backStack: NavBackStack<NavKey>

    /**
     * One-shot UI events (replay=0). App.kt collects these to open
     * global overlays that cannot be triggered via NavDisplay.
     */
    val events: SharedFlow<AppNavEvent>

    /** Publish ShowWidgetInstruction event so App.kt opens the overlay. */
    fun showWidgetInstruction()

    /** Request creation of a new weekly checklist. App.kt handles premium gate and navigation. */
    fun requestCreateWeeklyChecklist()

    fun onBack()

    /**
     * Declare the route a stack-CLEARING navigation ([navigateToChecklistDetail] /
     * [navigateToFillDetail] with `clearBackStack = true`) rebuilds the stack around when the stack
     * holds no top-level route at all — e.g. arriving straight from splash/onboarding, which
     * replaced it with their own single entry.
     *
     * Which route that is depends on the navigation shell (v1 is rooted at [AppNavRoute.Main], the
     * v2 shell at [AppNavRoute.Inbox]) and `core:navigation` cannot see which one is mounted, so the
     * host declares it once the arm is resolved. Default no-op body so the lightweight
     * [AppNavigator] fakes in feature tests need not implement it; the production implementation
     * falls back to [AppNavRoute.Main] until told otherwise.
     */
    fun setDefaultRootRoute(route: AppNavRoute) = Unit

    fun navigateToOnboarding()

    fun navigateToInteractiveOnboarding()

    /** Navigate to the Android-only "AI Welcome" onboarding (replaces the splash entry stack). */
    fun navigateToWelcomeOnboarding()

    /**
     * Navigate to main screen, optionally clearing all screens from back stack.
     */
    fun navigateToMainScreen(clearBackStack: Boolean = false)

    fun navigateToDebugMenu()

    fun navigateToStoreScreenshot()

    /**
     * todo change templateId to template class
     *
     * @param initialText optional prefilled item text (split into items on the create screen,
     *   e.g. from the ACTION_PROCESS_TEXT system selection menu). null = no prefill.
     */
    fun navigateToCreateChecklistScreen(templateId: Int? = null, initialText: String? = null)

    fun navigateToEditChecklist(checklistId: Long)

    fun navigateToTemplatesScreen()

    fun navigateToTemplatePreview(templateId: String)

    /**
     * @param initialText optional prefilled raw text (e.g. from ACTION_PROCESS_TEXT). When
     *   non-null the screen pre-selects RAW_TEXT and fills the input.
     * @param autoAnalyze when true AND [initialText] is non-blank, the screen runs analysis
     *   automatically once on mount (no manual Analyze tap). Used by the new-user activation
     *   hero. Default false keeps the ACTION_PROCESS_TEXT prefill-only contract.
     */
    fun navigateToAnalyzeScreen(
        checklistId: Long? = null,
        fillDefault: Boolean = false,
        initialText: String? = null,
        autoAnalyze: Boolean = false,
    )

    /**
     * Opens Analyze with the material picker ALREADY resolved to [inputKind], stamping [entrySource]
     * so `ai_analyze_started` can be attributed to the door the user came through.
     *
     * A separate method rather than two more parameters on [navigateToAnalyzeScreen] because the two
     * calls answer different questions: that one opens the material PICKER, this one opens a material
     * already chosen from a named door.
     *
     * ⚠️ ABSTRACT ON PURPOSE — do not give it a default body. It carried one briefly (delegating to
     * [navigateToAnalyzeScreen], to spare ~27 hand-written test fakes a mechanical edit) and that
     * body silently dropped BOTH arguments: an implementation that forgot to override it would send
     * the user to a generic picker and emit `ai_analyze_started` with an empty source — which is the
     * exact outage this pair of parameters was introduced to end, restored by the very convenience
     * that was meant to be free. Making it abstract moves that failure from production to the
     * compiler, and the cost is paid once, by the fakes.
     */
    fun navigateToAnalyzeWithInput(inputKind: AnalyzeInputKind, entrySource: AiEntrySource)

    fun navigateToAnalyzeResultPreview()

    /**
     * Navigate to checklist detail. If clearBackStack is true, clears back stack to main screen.
     * If focusItemId is provided, the screen will scroll to that item and briefly highlight it.
     */
    fun navigateToChecklistDetail(
        checklistId: Long,
        focusItemId: String? = null,
        clearBackStack: Boolean = false,
    )

    /**
     * Drill down into a folder ([folderId]) of checklist [checklistId]: pushes a new
     * ChecklistDetail entry scoped to that folder. Back/Up walks the folder tree via the
     * Nav3 back stack.
     *
     * Has a default body delegating to [navigateToChecklistDetail] so that lightweight
     * test fakes of [AppNavigator] need not override it; the production [AppNavigator]
     * implementation overrides it to carry [folderId] into the route.
     */
    fun navigateToFolder(checklistId: Long, folderId: String) =
        navigateToChecklistDetail(checklistId)

    /**
     * Navigate to fill detail. If clearBackStack is true, clears back stack to main screen.
     */
    fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean = false)

    fun navigateToFillsList(checklistId: Long)

    fun navigateToPaywall(source: String = "unknown")

    /** Navigate to paywall with a specific A/B variant forced (bypasses Remote Config). */
    fun navigateToPaywallVariant(source: String = "debug", forceVariant: String)

    fun navigateToSubscriptionStatus(showSuccessMessage: Boolean = false)

    fun navigateToShareChecklist(checklistId: Long)

    fun navigateToUpdateFeed()

    fun navigateToSettings()

    fun navigateToToday()

    fun navigateToCalendar()

    fun navigateToAiChat()

    fun navigateToScreenCatalog()

    fun navigateToOnboardings()

    /**
     * Show the checklist picker for an ACTION_PROCESS_TEXT flow.
     *
     * @param purpose [AddToChecklistPurpose.ADD_ITEM] (default) appends [text] as a single item and
     *   opens detail; [AddToChecklistPurpose.FILL_AI] opens Analyze in fill-mode for the chosen
     *   checklist with [text] pre-filled. Default keeps the existing "Add to checklist" call intact.
     */
    fun navigateToAddToChecklistPicker(
        text: String,
        purpose: AddToChecklistPurpose = AddToChecklistPurpose.ADD_ITEM,
    )
}
