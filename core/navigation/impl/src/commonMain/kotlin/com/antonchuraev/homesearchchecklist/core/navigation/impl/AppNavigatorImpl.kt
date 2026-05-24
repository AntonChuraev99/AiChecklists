package com.antonchuraev.homesearchchecklist.core.navigation.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
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
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
    }

    override fun navigateToOnboarding() {
        // popUpTo<Splash> inclusive — reset stack with only Onboarding
        backStack.clear()
        backStack.add(AppNavRoute.Onboarding)
    }

    override fun navigateToInteractiveOnboarding() {
        backStack.clear()
        backStack.add(AppNavRoute.InteractiveOnboarding)
    }

    override fun navigateToMainScreen(clearBackStack: Boolean) {
        if (clearBackStack) backStack.clear()
        backStack.add(AppNavRoute.Main)
    }

    override fun navigateToDebugMenu() = push(AppNavRoute.Debug)

    override fun navigateToStoreScreenshot() = push(AppNavRoute.StoreScreenshot)

    override fun navigateToCreateChecklistScreen(templateId: Int?) =
        push(AppNavRoute.CreateChecklistRoute.CreateChecklist(templateId = templateId))

    override fun navigateToEditChecklist(checklistId: Long) =
        push(AppNavRoute.CreateChecklistRoute.CreateChecklist(editChecklistId = checklistId))

    override fun navigateToTemplatesScreen() = push(AppNavRoute.CreateChecklistRoute.Templates)

    override fun navigateToTemplatePreview(templateId: String) =
        push(AppNavRoute.CreateChecklistRoute.TemplatePreview(templateId))

    override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) =
        push(AppNavRoute.Analyze(checklistId, fillDefault))

    override fun navigateToAnalyzeResultPreview() = push(AppNavRoute.AnalyzeResultPreview)

    override fun navigateToChecklistDetail(
        checklistId: Long,
        focusItemId: String?,
        clearBackStack: Boolean,
    ) {
        val route = AppNavRoute.ChecklistDetail(checklistId, focusItemId)
        if (clearBackStack) {
            // Mirrors Nav2: popUpTo(Main, inclusive = false) — keep Main, drop above, then push
            val mainIdx = backStack.indexOfFirst { it is AppNavRoute.Main }
            if (mainIdx >= 0) {
                while (backStack.size > mainIdx + 1) backStack.removeAt(backStack.size - 1)
            } else {
                backStack.clear()
            }
        }
        backStack.add(route)
    }

    override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) {
        val route = AppNavRoute.FillDetail(fillId)
        if (clearBackStack) {
            val mainIdx = backStack.indexOfFirst { it is AppNavRoute.Main }
            if (mainIdx >= 0) {
                while (backStack.size > mainIdx + 1) backStack.removeAt(backStack.size - 1)
            } else {
                backStack.clear()
            }
        }
        backStack.add(route)
    }

    override fun navigateToFillsList(checklistId: Long) = push(AppNavRoute.FillsList(checklistId))

    override fun navigateToPaywall(source: String) = push(AppNavRoute.Paywall(source))

    override fun navigateToPaywallVariant(source: String, forceVariant: String) =
        push(AppNavRoute.Paywall(source = source, forceVariant = forceVariant))

    override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) {
        // Mirrors Nav2: popUpTo<Paywall> inclusive — removes all Paywall entries
        backStack.removeAll { it is AppNavRoute.Paywall }
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
