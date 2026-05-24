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
}
