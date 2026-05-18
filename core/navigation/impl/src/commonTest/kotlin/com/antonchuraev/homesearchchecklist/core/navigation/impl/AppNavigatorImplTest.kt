package com.antonchuraev.homesearchchecklist.core.navigation.impl

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TDD Step 0: smoke check — env must be broken first, then real tests
// smoke is removed after confirming it fails as expected

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigatorImplTest {

    // ---------------------------------------------------------------------------
    // Smoke check (Step 0). Uncomment temporarily, run once, then re-comment.
    // ---------------------------------------------------------------------------
    // Smoke check passed: env confirmed working (2026-04-27). Removed after Step 0.

    // ---------------------------------------------------------------------------
    // Test 1: Commands buffered before collector subscribes are delivered
    // ---------------------------------------------------------------------------

    /**
     * Channel.BUFFERED semantics: commands emitted before the collector starts
     * must be queued and delivered in order once collection begins.
     * This is the core architectural invariant that eliminates the Splash race condition.
     */
    @Test
    fun commands_emittedBeforeCollectorSubscribes_areDelivered() = runTest {
        val nav = AppNavigatorImpl()
        nav.navigateToOnboarding()
        nav.navigateToMainScreen(clearBackStack = true)

        val received = mutableListOf<NavCommand>()
        val job = launch { nav.commands.toList(received) }
        advanceUntilIdle()

        assertEquals(2, received.size)
        assertEquals(NavCommand.ToOnboarding, received[0])
        assertEquals(NavCommand.ToMainScreen(clearBackStack = true), received[1])
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 2: FIFO order preserved
    // ---------------------------------------------------------------------------

    /**
     * Channel guarantees FIFO delivery. Commands must arrive in the exact order
     * they were emitted — no reordering, no skipping.
     */
    @Test
    fun commands_multipleEmits_deliveredInFifoOrder() = runTest {
        val nav = AppNavigatorImpl()
        val received = mutableListOf<NavCommand>()
        val job = launch { nav.commands.toList(received) }

        nav.navigateToPaywall(source = "test")
        nav.navigateToSettings()
        nav.onBack()
        advanceUntilIdle()

        assertEquals(3, received.size)
        assertEquals(NavCommand.ToPaywall("test"), received[0])
        assertEquals(NavCommand.ToSettings, received[1])
        assertEquals(NavCommand.Back, received[2])
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Parameterized commands carry their arguments through Channel
    // ---------------------------------------------------------------------------

    /**
     * Data class NavCommands must preserve all constructor arguments round-trip
     * through the Channel without mutation or truncation.
     */
    @Test
    fun commands_parameterizedVariants_carryArgumentsUnchanged() = runTest {
        val nav = AppNavigatorImpl()
        nav.navigateToChecklistDetail(checklistId = 42L, clearBackStack = true)
        nav.navigateToPaywallVariant(source = "debug", forceVariant = "timeline")
        nav.navigateToFillDetail(fillId = 99L, clearBackStack = false)
        nav.navigateToCreateChecklistScreen(templateId = 7)

        val received = mutableListOf<NavCommand>()
        val job = launch { nav.commands.toList(received) }
        advanceUntilIdle()

        assertEquals(NavCommand.ToChecklistDetail(checklistId = 42L, focusItemId = null, clearBackStack = true), received[0])
        assertEquals(NavCommand.ToPaywallVariant(source = "debug", forceVariant = "timeline"), received[1])
        assertEquals(NavCommand.ToFillDetail(fillId = 99L, clearBackStack = false), received[2])
        assertEquals(NavCommand.ToCreateChecklistScreen(templateId = 7), received[3])
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 4: events SharedFlow delivers ShowWidgetInstruction
    // ---------------------------------------------------------------------------

    /**
     * events is a SharedFlow(replay=0). The subscriber must be alive at emit time.
     * This test verifies the event path is wired correctly (tryEmit succeeds via extraBufferCapacity=1).
     */
    @Test
    fun events_showWidgetInstruction_deliveredToActiveCollector() = runTest {
        val nav = AppNavigatorImpl()
        val received = mutableListOf<AppNavEvent>()
        val job = launch { nav.events.toList(received) }

        // Subscriber must be alive before emit (replay=0 + extraBufferCapacity=1)
        advanceUntilIdle()
        nav.showWidgetInstruction()
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(AppNavEvent.ShowWidgetInstruction, received[0])
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 5: Single-consumer invariant — Channel.receiveAsFlow() does not replay
    // ---------------------------------------------------------------------------

    /**
     * Channel.receiveAsFlow() is single-consumer: once a command is consumed by
     * the first collector, it must NOT be re-delivered to a second collector.
     * This mirrors the App.kt architecture where exactly one collector exists.
     */
    @Test
    fun commands_firstCollectorConsumes_secondCollectorReceivesNothing() = runTest {
        val nav = AppNavigatorImpl()
        nav.navigateToOnboarding()

        // First collector drains the command
        val first = mutableListOf<NavCommand>()
        val firstJob = launch { nav.commands.toList(first) }
        advanceUntilIdle()
        firstJob.cancel()

        // Second collector starts after — no replay expected
        val second = mutableListOf<NavCommand>()
        val secondJob = launch { nav.commands.toList(second) }
        advanceUntilIdle()

        assertEquals(1, first.size, "first collector must receive the command")
        assertEquals(0, second.size, "second collector must not receive already-consumed commands")
        secondJob.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 6: All 22 NavCommand variants emit successfully (no trySend overflow)
    // ---------------------------------------------------------------------------

    /**
     * Channel.BUFFERED capacity is at least 64. All 22 non-Back nav commands plus
     * Back itself should fit without overflow. This guards against accidental capacity
     * exhaustion if new NavCommand variants are added without a corresponding increase
     * in Channel capacity.
     */
    @Test
    fun commands_allNavCommandVariants_emitWithoutLoss() = runTest {
        val nav = AppNavigatorImpl()
        val received = mutableListOf<NavCommand>()
        val job = launch { nav.commands.toList(received) }

        // Emit all variants
        nav.onBack()
        nav.navigateToOnboarding()
        nav.navigateToInteractiveOnboarding()
        nav.navigateToMainScreen(clearBackStack = false)
        nav.navigateToMainScreen(clearBackStack = true)
        nav.navigateToDebugMenu()
        nav.navigateToStoreScreenshot()
        nav.navigateToCreateChecklistScreen(templateId = null)
        nav.navigateToCreateChecklistScreen(templateId = 1)
        nav.navigateToEditChecklist(checklistId = 1L)
        nav.navigateToTemplatesScreen()
        nav.navigateToTemplatePreview(templateId = "abc")
        nav.navigateToAnalyzeScreen(checklistId = null, fillDefault = false)
        nav.navigateToAnalyzeScreen(checklistId = 5L, fillDefault = true)
        nav.navigateToAnalyzeResultPreview()
        nav.navigateToChecklistDetail(checklistId = 1L)
        nav.navigateToFillDetail(fillId = 1L)
        nav.navigateToFillsList(checklistId = 1L)
        nav.navigateToPaywall(source = "x")
        nav.navigateToPaywallVariant(source = "debug", forceVariant = "timeline")
        nav.navigateToSubscriptionStatus(showSuccessMessage = false)
        nav.navigateToSubscriptionStatus(showSuccessMessage = true)
        nav.navigateToShareChecklist(checklistId = 1L)
        nav.navigateToUpdateFeed()
        nav.navigateToSettings()
        nav.navigateToScreenCatalog()

        advanceUntilIdle()

        // 26 total calls above
        assertEquals(26, received.size, "all emitted commands must be received without loss")

        // Spot-check a few specific types to verify correct variant mapping
        assertTrue(received.any { it is NavCommand.Back })
        assertTrue(received.any { it == NavCommand.ToOnboarding })
        assertTrue(received.any { it == NavCommand.ToSettings })
        assertTrue(received.any { it == NavCommand.ToScreenCatalog })
        assertTrue(received.any { it is NavCommand.ToPaywallVariant })
        assertTrue(received.any { it == NavCommand.ToMainScreen(clearBackStack = true) })

        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 7: Default parameter values are preserved through Channel
    // ---------------------------------------------------------------------------

    /**
     * NavCommand data classes use default parameter values. Verify that calling
     * navigate functions without optional args produces commands with correct defaults.
     */
    @Test
    fun commands_defaultParameters_preservedThroughChannel() = runTest {
        val nav = AppNavigatorImpl()
        nav.navigateToMainScreen()                          // clearBackStack = false
        nav.navigateToChecklistDetail(checklistId = 10L)    // clearBackStack = false
        nav.navigateToFillDetail(fillId = 20L)              // clearBackStack = false
        nav.navigateToPaywall()                             // source = "unknown"
        nav.navigateToSubscriptionStatus()                  // showSuccessMessage = false
        nav.navigateToAnalyzeScreen()                       // checklistId = null, fillDefault = false

        val received = mutableListOf<NavCommand>()
        val job = launch { nav.commands.toList(received) }
        advanceUntilIdle()

        assertEquals(NavCommand.ToMainScreen(clearBackStack = false), received[0])
        assertEquals(NavCommand.ToChecklistDetail(checklistId = 10L, focusItemId = null, clearBackStack = false), received[1])
        assertEquals(NavCommand.ToFillDetail(fillId = 20L, clearBackStack = false), received[2])
        assertEquals(NavCommand.ToPaywall(source = "unknown"), received[3])
        assertEquals(NavCommand.ToSubscriptionStatus(showSuccessMessage = false), received[4])
        assertEquals(NavCommand.ToAnalyzeScreen(checklistId = null, fillDefault = false), received[5])
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 8: events SharedFlow does NOT replay to late subscribers (replay=0)
    // ---------------------------------------------------------------------------

    /**
     * events SharedFlow is configured with replay=0. A subscriber that connects
     * AFTER an event is emitted must NOT receive that event. This is critical to
     * prevent the widget instruction overlay from re-opening on each recomposition.
     */
    @Test
    fun events_lateSubscriber_doesNotReceivePriorEvent() = runTest {
        val nav = AppNavigatorImpl()

        // Emit before any subscriber
        nav.showWidgetInstruction()
        advanceUntilIdle()

        // Subscribe late — replay=0 means nothing buffered
        val received = mutableListOf<AppNavEvent>()
        val job = launch { nav.events.toList(received) }
        advanceUntilIdle()

        assertEquals(0, received.size, "replay=0 must not deliver prior events to late subscribers")
        job.cancel()
    }

    // ---------------------------------------------------------------------------
    // Test 9: navigateToChecklistDetail with focusItemId carries it through Channel
    // ---------------------------------------------------------------------------

    /**
     * When navigating to checklist detail with a focusItemId (from Calendar reminder deeplink),
     * the focusItemId must be preserved round-trip through the Channel so App.kt
     * can pass it to ChecklistDetailScreen for scroll-and-highlight.
     */
    @Test
    fun commands_checklistDetailWithFocusItemId_carriedThroughChannel() = runTest {
        val nav = AppNavigatorImpl()
        nav.navigateToChecklistDetail(checklistId = 55L, focusItemId = "item-abc")

        val received = mutableListOf<NavCommand>()
        val job = launch { nav.commands.toList(received) }
        advanceUntilIdle()

        assertEquals(1, received.size)
        val cmd = received[0] as NavCommand.ToChecklistDetail
        assertEquals(55L, cmd.checklistId)
        assertEquals("item-abc", cmd.focusItemId)
        assertEquals(false, cmd.clearBackStack)
        job.cancel()
    }
}
