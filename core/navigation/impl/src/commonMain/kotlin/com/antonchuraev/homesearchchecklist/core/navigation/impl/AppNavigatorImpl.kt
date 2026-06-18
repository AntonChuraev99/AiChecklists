package com.antonchuraev.homesearchchecklist.core.navigation.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Direct NavBackStack mutation — no async Channel, no NavController.handle() translation.
 *
 * State changes are synchronous and observable by NavDisplay on the next Compose frame.
 * The Splash race condition is eliminated architecturally: ViewModel.init mutations land
 * before App.kt's first composition reads backStack.
 *
 * Stage 2: replaces Stage 1's StateFlow<List<AppNavRoute>> + Channel.BUFFERED with
 * Nav3 SnapshotStateList<NavKey> wrapped as NavBackStack.
 */
class AppNavigatorImpl : AppNavigator {

    /**
     * Nav3 back stack. NavDisplay observes this as a SnapshotStateList<NavKey> and
     * re-renders on each mutation (add/remove/clear).
     *
     * Seeded with [AppNavRoute.Splash] in [init] because NavDisplay requires a
     * non-empty stack at first composition — a LaunchedEffect-based seed runs
     * AFTER composition and triggers `IllegalArgumentException: NavDisplay backstack
     * cannot be empty`. DI-time init guarantees Splash is present before any
     * Composable reads the stack.
     */
    override val backStack: NavBackStack<NavKey> = NavBackStack()

    init {
        backStack.add(AppNavRoute.Splash)
    }

    private val _events = MutableSharedFlow<AppNavEvent>(replay = 0, extraBufferCapacity = 1)
    override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

    override fun showWidgetInstruction() {
        _events.tryEmit(AppNavEvent.ShowWidgetInstruction)
    }

    override fun requestCreateWeeklyChecklist() {
        _events.tryEmit(AppNavEvent.CreateWeeklyChecklistRequested)
    }

    override fun onBack() {
        // Guard size > 1 (not isNotEmpty) — NavDisplay requires non-empty stack at all
        // times, not just first composition. Dropping the last entry crashes NavDisplay
        // on next recomposition with "backstack cannot be empty". When on root,
        // browser/OS back should be no-op (Compose handles app-exit on Android, browser
        // navigates above on wasmJs).
        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
    }

    override fun navigateToOnboarding() {
        replaceStack(AppNavRoute.Onboarding)
    }

    override fun navigateToInteractiveOnboarding() {
        replaceStack(AppNavRoute.InteractiveOnboarding)
    }

    override fun navigateToMainScreen(clearBackStack: Boolean) {
        if (clearBackStack) {
            replaceStack(AppNavRoute.Main)
        } else {
            backStack.add(AppNavRoute.Main)
        }
    }

    override fun navigateToDebugMenu() = push(AppNavRoute.Debug)

    override fun navigateToStoreScreenshot() = push(AppNavRoute.StoreScreenshot)

    override fun navigateToCreateChecklistScreen(templateId: Int?, initialText: String?) =
        push(
            AppNavRoute.CreateChecklistRoute.CreateChecklist(
                templateId = templateId,
                initialText = initialText,
            )
        )

    override fun navigateToEditChecklist(checklistId: Long) =
        push(AppNavRoute.CreateChecklistRoute.CreateChecklist(editChecklistId = checklistId))

    override fun navigateToTemplatesScreen() = push(AppNavRoute.CreateChecklistRoute.Templates)

    override fun navigateToTemplatePreview(templateId: String) =
        push(AppNavRoute.CreateChecklistRoute.TemplatePreview(templateId))

    override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean, initialText: String?, autoAnalyze: Boolean) =
        push(AppNavRoute.Analyze(checklistId, fillDefault, initialText, autoAnalyze))

    override fun navigateToAnalyzeResultPreview() = push(AppNavRoute.AnalyzeResultPreview)

    override fun navigateToChecklistDetail(
        checklistId: Long,
        focusItemId: String?,
        clearBackStack: Boolean,
    ) {
        val route = AppNavRoute.ChecklistDetail(checklistId, focusItemId)
        if (clearBackStack) {
            popToMainThenPush(route)
        } else {
            backStack.add(route)
        }
    }

    override fun navigateToFolder(checklistId: Long, folderId: String) {
        // Plain push (never clears) so Up/back returns to the parent folder level.
        backStack.add(AppNavRoute.ChecklistDetail(checklistId, currentFolderId = folderId))
    }

    override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {
        val route = AppNavRoute.FillDetail(fillId)
        if (clearBackStack) {
            popToMainThenPush(route)
        } else {
            backStack.add(route)
        }
    }

    override fun navigateToFillsList(checklistId: Long) = push(AppNavRoute.FillsList(checklistId))

    override fun navigateToPaywall(source: String) = push(AppNavRoute.Paywall(source))

    override fun navigateToPaywallVariant(source: String, forceVariant: String) =
        push(AppNavRoute.Paywall(source = source, forceVariant = forceVariant))

    override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {
        backStack.removeAll { it is AppNavRoute.Paywall }
        if (backStack.isEmpty()) backStack.add(AppNavRoute.Main)
        backStack.add(AppNavRoute.SubscriptionStatus(showSuccessMessage))
    }

    override fun navigateToShareChecklist(checklistId: Long) =
        push(AppNavRoute.ShareChecklist(checklistId))

    override fun navigateToUpdateFeed() = pushLaunchSingleTop(AppNavRoute.UpdateFeed)

    override fun navigateToSettings() = pushLaunchSingleTop(AppNavRoute.Settings)

    override fun navigateToToday() = pushLaunchSingleTop(AppNavRoute.Today)

    override fun navigateToCalendar() = pushLaunchSingleTop(AppNavRoute.Calendar)

    override fun navigateToAiChat() = pushLaunchSingleTop(AppNavRoute.AiChat)

    override fun navigateToScreenCatalog() = push(AppNavRoute.ScreenCatalog)

    override fun navigateToOnboardings() = push(AppNavRoute.Onboardings)

    override fun navigateToAddToChecklistPicker(text: String, purpose: AddToChecklistPurpose) =
        push(AppNavRoute.AddToChecklistPicker(text, purpose))

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Atomically replace the entire stack with a single route.
     * Never leaves the stack empty — sets [0] first, then trims.
     * Fixes race where NavDisplay sees empty stack between clear() and add().
     */
    private fun replaceStack(route: AppNavRoute) {
        backStack[0] = route
        while (backStack.size > 1) backStack.removeAt(backStack.size - 1)
    }

    private fun popToMainThenPush(route: AppNavRoute) {
        val mainIdx = backStack.indexOfFirst { it is AppNavRoute.Main }
        if (mainIdx >= 0) {
            while (backStack.size > mainIdx + 1) backStack.removeAt(backStack.size - 1)
        } else {
            replaceStack(route)
            return
        }
        backStack.add(route)
    }

    private fun push(route: AppNavRoute) {
        backStack.add(route)
    }

    /**
     * Mirrors launchSingleTop — if the same route is already on top, do not duplicate it.
     */
    private fun pushLaunchSingleTop(route: AppNavRoute) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }
}
