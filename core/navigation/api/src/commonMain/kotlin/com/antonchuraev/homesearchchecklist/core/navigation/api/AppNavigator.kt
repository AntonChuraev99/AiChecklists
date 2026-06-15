package com.antonchuraev.homesearchchecklist.core.navigation.api

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
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

    fun navigateToOnboarding()

    fun navigateToInteractiveOnboarding()

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
     *   non-null the screen pre-selects RAW_TEXT and fills the input WITHOUT auto-analyzing.
     */
    fun navigateToAnalyzeScreen(
        checklistId: Long? = null,
        fillDefault: Boolean = false,
        initialText: String? = null,
    )

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
