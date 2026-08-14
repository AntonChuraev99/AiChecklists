package com.antonchuraev.homesearchchecklist.core.navigation.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
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

    /**
     * Root the stack-clearing helpers ([popToRootThenPush], [navigateToMainScreen]) rebuild the
     * stack around when they find no top-level route to collapse onto. Seeded with the control arm's
     * root so an unset value behaves exactly as this class did before the contract existed; the v2
     * shell overrides it via [setDefaultRootRoute].
     */
    private var defaultRootRoute: AppNavRoute = AppNavRoute.Main

    override fun setDefaultRootRoute(route: AppNavRoute) {
        defaultRootRoute = route
    }

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

    override fun navigateToWelcomeOnboarding() {
        replaceStack(AppNavRoute.WelcomeOnboarding)
    }

    override fun navigateToMainScreen(clearBackStack: Boolean) {
        if (clearBackStack) {
            // "Main screen" means the SHELL'S HOME, not literally [AppNavRoute.Main]: the v2 shell is
            // rooted at [AppNavRoute.Inbox] and holds no Main at all. Replacing the stack with Main
            // unconditionally reproduced the bug [popToRootThenPush] was fixed for through a second
            // door — deleting a checklist, or finishing create/analyze, rewrote `[0]` to the v1 home
            // screen rendered inside the v2 chrome with the tab state gone. Collapse onto the root the
            // stack already has instead; the v1 stack still ends up as exactly [Main] because Main is
            // the only top-level route it ever contains at index 0.
            //
            // No top-level route at all (splash/onboarding replaced the stack with their own single
            // entry) falls back to the host-declared root, which is [AppNavRoute.Main] until the shell
            // latches its arm — so the cold-start path lands exactly where it did before.
            replaceStack(currentRootRoute() ?: defaultRootRoute)
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

    override fun navigateToAnalyzeWithInput(inputKind: AnalyzeInputKind, entrySource: AiEntrySource) =
        push(AppNavRoute.Analyze(initialInputKind = inputKind, entrySource = entrySource))

    override fun navigateToAnalyzeResultPreview() = push(AppNavRoute.AnalyzeResultPreview)

    override fun navigateToChecklistDetail(
        checklistId: Long,
        focusItemId: String?,
        clearBackStack: Boolean,
    ) {
        val route = AppNavRoute.ChecklistDetail(checklistId, focusItemId)
        if (clearBackStack) {
            popToRootThenPush(route)
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
            popToRootThenPush(route)
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
        // Same reason the other stack-rebuilding paths are root-agnostic: seeding a literal Main here
        // would put the v1 home screen under the v2 chrome. Degenerate branch (a stack of nothing but
        // paywalls), kept because NavDisplay crashes on an empty stack.
        if (backStack.isEmpty()) backStack.add(defaultRootRoute)
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

    /**
     * Routes that can be the stack's ROOT: the v1 shell's [AppNavRoute.Main] and the four v2 shell
     * tabs. Membership, not position — [popToRootThenPush] takes the first match, and in both shells
     * that first match IS the root (v1 roots at Main, v2 at Inbox, and every other entry here is
     * pushed on top of one of those).
     */
    private fun NavKey.isTopLevelRoot(): Boolean =
        this is AppNavRoute.Main ||
            this is AppNavRoute.Inbox ||
            this is AppNavRoute.Projects ||
            this is AppNavRoute.Calendar ||
            this is AppNavRoute.Overview

    /**
     * The top-level route the stack is currently rooted at, or null when it holds none — e.g. right
     * after splash/onboarding, which replaced the stack with their own single entry.
     *
     * Takes the FIRST match for the same reason [popToRootThenPush] does: [AppNavRoute.Calendar] is a
     * v2 tab AND a v1 drawer destination, so a later match could be a v1 stack's Calendar sitting on
     * top of Main. The cast never fails — every branch of [isTopLevelRoot] is an [AppNavRoute].
     */
    private fun currentRootRoute(): AppNavRoute? =
        backStack.firstOrNull { it.isTopLevelRoot() } as? AppNavRoute

    /**
     * Pop everything above the stack's top-level root, then push [route].
     *
     * Matches ANY top-level root, not [AppNavRoute.Main] alone. The v2 shell's stack is rooted at
     * [AppNavRoute.Inbox] and contains no Main at all, so a Main-only search fell through to the
     * else-branch and REWROTE `[0]` to Main: creating a checklist from a template dropped the user
     * onto the v1 home screen rendered inside the v2 chrome, tab state gone, and BACK left them
     * there. Main is index 0 whenever it is present, so the v1 shell still collapses onto exactly
     * the entry it did before.
     */
    private fun popToRootThenPush(route: AppNavRoute) {
        val rootIdx = backStack.indexOfFirst { it.isTopLevelRoot() }
        if (rootIdx >= 0) {
            while (backStack.size > rootIdx + 1) backStack.removeAt(backStack.size - 1)
        } else {
            // No top-level route in the stack — e.g. arriving straight from onboarding/splash, which
            // replaced the stack with the onboarding route. Seed the shell's root under the pushed
            // route so back/Up from it lands on home instead of dead-ending: with a single-entry
            // stack onBack is a no-op (NavDisplay must stay non-empty), which is the "back does
            // nothing" bug.
            backStack[0] = defaultRootRoute
            while (backStack.size > 1) backStack.removeAt(backStack.size - 1)
            backStack.add(route)
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
