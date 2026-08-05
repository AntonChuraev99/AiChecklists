package com.antonchuraev.homesearchchecklist.core.navigation.impl

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavBackStack
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage 2: tests verify NavBackStack mutation order directly.
 * No more Channel/commands flow — all navigation state is synchronous
 * SnapshotStateList<NavKey> mutation observable by NavDisplay.
 *
 * AppNavigatorImpl.init {} seeds backStack with [AppNavRoute.Splash] at DI time
 * because NavDisplay requires a non-empty stack at first composition. All tests
 * here account for that seed in expected lists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigatorImplTest {

    // ---------------------------------------------------------------------------
    // Test 0: fresh navigator starts with [Splash] (init-seeded)
    // ---------------------------------------------------------------------------

    @Test
    fun freshNavigator_startsWithSplashOnly() {
        val nav = AppNavigatorImpl()
        assertEquals(listOf<NavKey>(AppNavRoute.Splash), nav.backStack.toList())
    }

    // ---------------------------------------------------------------------------
    // Test 1: navigateToOnboarding clears stack and sets only Onboarding
    // (popUpTo<Splash> inclusive = true semantic — wipes Splash too)
    // ---------------------------------------------------------------------------

    @Test
    fun navigateToOnboarding_resetsStackWithOnboardingOnly() {
        val nav = AppNavigatorImpl()
        // Seed with some state first
        nav.navigateToMainScreen()
        nav.navigateToTemplatesScreen()
        nav.navigateToOnboarding()
        assertEquals(listOf<NavKey>(AppNavRoute.Onboarding), nav.backStack.toList())
    }

    // ---------------------------------------------------------------------------
    // Test 2: navigateToMainScreen with clearBackStack replaces stack
    // ---------------------------------------------------------------------------

    @Test
    fun navigateToMainScreen_clearBackStack_replacesStack() {
        val nav = AppNavigatorImpl()
        nav.navigateToOnboarding()
        nav.navigateToMainScreen(clearBackStack = true)
        assertEquals(listOf<NavKey>(AppNavRoute.Main), nav.backStack.toList())
    }

    // ---------------------------------------------------------------------------
    // Test 3: Sequential push chain preserves insertion order
    // ---------------------------------------------------------------------------

    @Test
    fun navigateChain_pushesInOrder() {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()
        nav.navigateToChecklistDetail(checklistId = 42L)
        nav.navigateToTemplatesScreen()
        assertEquals(
            listOf<NavKey>(
                AppNavRoute.Splash,
                AppNavRoute.Main,
                AppNavRoute.ChecklistDetail(42L),
                AppNavRoute.CreateChecklistRoute.Templates,
            ),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 4: onBack removes top entry
    // ---------------------------------------------------------------------------

    @Test
    fun onBack_dropsTop() {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()
        nav.navigateToTemplatesScreen()
        nav.onBack()
        assertEquals(
            listOf<NavKey>(AppNavRoute.Splash, AppNavRoute.Main),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 5: launchSingleTop drawer destinations do not duplicate
    // ---------------------------------------------------------------------------

    @Test
    fun launchSingleTop_drawerDestinations_doNotDuplicate() {
        val nav = AppNavigatorImpl()
        nav.navigateToToday()
        nav.navigateToToday()
        nav.navigateToToday()
        assertEquals(
            listOf<NavKey>(AppNavRoute.Splash, AppNavRoute.Today),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 6: navigateToSubscriptionStatus pops ALL Paywall entries
    // ---------------------------------------------------------------------------

    @Test
    fun subscriptionStatus_popsAllPaywall() {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()
        nav.navigateToPaywall("test")
        nav.navigateToPaywallVariant(source = "debug", forceVariant = "timeline")
        nav.navigateToSubscriptionStatus(showSuccessMessage = true)
        assertEquals(
            listOf<NavKey>(
                AppNavRoute.Splash,
                AppNavRoute.Main,
                AppNavRoute.SubscriptionStatus(showSuccessMessage = true),
            ),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 7: navigateToChecklistDetail with clearBackStack keeps Main on stack
    // ---------------------------------------------------------------------------

    @Test
    fun checklistDetail_clearBackStack_keepsMain() {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()
        nav.navigateToTemplatesScreen()
        nav.navigateToChecklistDetail(checklistId = 5L, clearBackStack = true)
        assertEquals(
            listOf<NavKey>(
                AppNavRoute.Splash,
                AppNavRoute.Main,
                AppNavRoute.ChecklistDetail(5L),
            ),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 8: events SharedFlow delivers ShowWidgetInstruction to active collector
    // ---------------------------------------------------------------------------

    @Test
    fun events_showWidgetInstruction_deliveredToActiveCollector() = runTest {
        val nav = AppNavigatorImpl()
        val received = mutableListOf<AppNavEvent>()
        val job = launch { nav.events.toList(received) }
        advanceUntilIdle()
        nav.showWidgetInstruction()
        advanceUntilIdle()
        assertEquals(1, received.size)
        assertEquals(AppNavEvent.ShowWidgetInstruction, received[0])
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 9: navigateToChecklistDetail carries focusItemId in route
    // ---------------------------------------------------------------------------

    @Test
    fun checklistDetailWithFocusItemId_carriedInRoute() {
        val nav = AppNavigatorImpl()
        nav.navigateToChecklistDetail(checklistId = 55L, focusItemId = "item-abc")
        val top = nav.backStack.last() as AppNavRoute.ChecklistDetail
        assertEquals(55L, top.checklistId)
        assertEquals("item-abc", top.focusItemId)
    }

    // ---------------------------------------------------------------------------
    // Test 10: clearBackStack collapses onto the v2 root (Inbox), NOT onto Main
    //
    // The v2 shell's stack holds no Main at all. The Main-only search this replaced
    // fell through to the seed branch and rewrote [0] to Main, so creating a
    // checklist from a template rendered the v1 home screen inside the v2 chrome.
    // ---------------------------------------------------------------------------

    @Test
    fun checklistDetail_clearBackStack_v2Stack_collapsesToInbox() {
        val nav = AppNavigatorImpl()
        // The stack the v2 shell builds: Inbox root, tab on top, then the create flow.
        nav.backStack[0] = AppNavRoute.Inbox
        nav.backStack.add(AppNavRoute.Projects)
        nav.backStack.add(AppNavRoute.CreateChecklistRoute.Templates)
        nav.backStack.add(AppNavRoute.CreateChecklistRoute.TemplatePreview("weekly"))

        nav.navigateToChecklistDetail(checklistId = 7L, clearBackStack = true)

        assertEquals(
            listOf<NavKey>(AppNavRoute.Inbox, AppNavRoute.ChecklistDetail(7L)),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 11: the v1 stack still collapses onto Main even with a Calendar entry
    // above it — Calendar is a v2 tab AND a v1 drawer destination, and only the
    // FIRST top-level route may win, or v1 would stop clearing to home.
    // ---------------------------------------------------------------------------

    @Test
    fun fillDetail_clearBackStack_v1StackWithCalendar_stillCollapsesToMain() {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()
        nav.navigateToCalendar()
        nav.navigateToTemplatesScreen()

        nav.navigateToFillDetail(fillId = 3L, clearBackStack = true)

        assertEquals(
            listOf<NavKey>(AppNavRoute.Splash, AppNavRoute.Main, AppNavRoute.FillDetail(3L)),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 12: no top-level route in the stack (straight from onboarding) seeds the
    // declared root — Main until the host declares otherwise, Inbox once it has.
    // ---------------------------------------------------------------------------

    @Test
    fun checklistDetail_clearBackStack_noTopLevelRoute_seedsMainByDefault() {
        val nav = AppNavigatorImpl()
        nav.navigateToOnboarding()

        nav.navigateToChecklistDetail(checklistId = 9L, clearBackStack = true)

        assertEquals(
            listOf<NavKey>(AppNavRoute.Main, AppNavRoute.ChecklistDetail(9L)),
            nav.backStack.toList(),
        )
    }

    @Test
    fun checklistDetail_clearBackStack_noTopLevelRoute_honoursDeclaredRoot() {
        val nav = AppNavigatorImpl()
        nav.setDefaultRootRoute(AppNavRoute.Inbox)
        nav.navigateToOnboarding()

        nav.navigateToChecklistDetail(checklistId = 9L, clearBackStack = true)

        assertEquals(
            listOf<NavKey>(AppNavRoute.Inbox, AppNavRoute.ChecklistDetail(9L)),
            nav.backStack.toList(),
        )
    }

    // ---------------------------------------------------------------------------
    // Test 13: the OTHER stack-clearing door — navigateToMainScreen(clearBackStack)
    // must be root-agnostic too, or it reproduces the popToRootThenPush bug: deleting
    // a checklist or finishing create/analyze rewrote [0] to Main and dropped the v2
    // user onto the v1 home screen inside the v2 chrome.
    // ---------------------------------------------------------------------------

    @Test
    fun navigateToMainScreen_clearBackStack_v2Stack_collapsesToInbox() {
        val nav = AppNavigatorImpl()
        nav.setDefaultRootRoute(AppNavRoute.Inbox)
        // The stack the v2 shell builds: Inbox root, tab on top, then a pushed detail screen.
        nav.backStack[0] = AppNavRoute.Inbox
        nav.backStack.add(AppNavRoute.Projects)
        nav.backStack.add(AppNavRoute.ChecklistDetail(11L))

        nav.navigateToMainScreen(clearBackStack = true)

        assertEquals(listOf<NavKey>(AppNavRoute.Inbox), nav.backStack.toList())
    }

    // ---------------------------------------------------------------------------
    // Test 14: the v1 stack still collapses to exactly [Main] — Splash below it and a
    // Calendar entry above it both go, because Calendar is a v2 tab AND a v1 drawer
    // destination and only the FIRST top-level route may win.
    // ---------------------------------------------------------------------------

    @Test
    fun navigateToMainScreen_clearBackStack_v1StackWithCalendar_leavesMainOnly() {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()
        nav.navigateToCalendar()
        nav.navigateToChecklistDetail(checklistId = 4L)

        nav.navigateToMainScreen(clearBackStack = true)

        assertEquals(listOf<NavKey>(AppNavRoute.Main), nav.backStack.toList())
    }

    // ---------------------------------------------------------------------------
    // Test 15: rootless stack seeds the declared root. Splash-only is the cold-start
    // shape (SplashViewModel calls this the moment onboarding is already passed), so
    // the undeclared case must stay on Main — the arm is not latched that early.
    // ---------------------------------------------------------------------------

    @Test
    fun navigateToMainScreen_clearBackStack_splashOnlyStack_landsOnMainByDefault() {
        val nav = AppNavigatorImpl()

        nav.navigateToMainScreen(clearBackStack = true)

        assertEquals(listOf<NavKey>(AppNavRoute.Main), nav.backStack.toList())
    }

    @Test
    fun navigateToMainScreen_clearBackStack_noTopLevelRoute_honoursDeclaredRoot() {
        val nav = AppNavigatorImpl()
        nav.setDefaultRootRoute(AppNavRoute.Inbox)
        nav.navigateToOnboarding()

        nav.navigateToMainScreen(clearBackStack = true)

        assertEquals(listOf<NavKey>(AppNavRoute.Inbox), nav.backStack.toList())
    }
}
