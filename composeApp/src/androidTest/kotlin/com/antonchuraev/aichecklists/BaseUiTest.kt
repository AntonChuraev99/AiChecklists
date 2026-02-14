package com.antonchuraev.aichecklists

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.antonchuraev.homesearchchecklist.MainActivity
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Base class for UI tests providing common setup and utilities.
 *
 * IMPORTANT ASYNC PATTERNS:
 * -------------------------
 * E2E tests interact with async systems (Room DB, StateFlow, Compose recomposition).
 * Improper timing causes flaky tests. Follow these patterns:
 *
 * 1. After any UI action: waitForIdle()
 *    composeTestRule.onNodeWithText("Delete").performClick()
 *    waitForIdle()  // Wait for state update + recomposition
 *
 * 2. After direct repository calls: runBlocking + waitForIdle()
 *    runBlocking { repository.insert(checklist) }  // Wait for DB write
 *    waitForIdle()  // Wait for Flow emission + UI update
 *
 * 3. For state-dependent assertions: waitUntil()
 *    waitUntil(3000) {
 *        composeTestRule.onAllNodesWithText("Shopping List")
 *            .fetchSemanticsNodes().isNotEmpty()
 *    }
 *
 * 4. Screen transitions: wait for old gone, new visible
 *    waitUntilScreenGone("Templates")
 *    waitUntilScreenVisible("Checklist Details")
 *
 * NEVER use Thread.sleep() - it's brittle and unreliable
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ===== Basic Wait Helpers =====

    /**
     * Wait for the app to be idle before assertions.
     * Use after: clicks, text input, navigation.
     */
    protected fun waitForIdle() {
        composeTestRule.waitForIdle()
    }

    /**
     * Wait for a specific condition with timeout.
     * Use for: state-dependent assertions, element appearance/disappearance.
     */
    protected fun waitUntil(timeoutMillis: Long = 5000, condition: () -> Boolean) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
    }

    // ===== Screen Transition Helpers =====

    /**
     * Wait for Splash screen to complete before proceeding with tests.
     *
     * CRITICAL: Splash screen makes blocking network calls (Firebase, RevenueCat)
     * that take 10-15 seconds. Tests MUST call this before any assertions.
     *
     * Without this, tests will timeout trying to find UI elements that haven't
     * loaded yet because the app is stuck on "Getting things ready..." screen.
     */
    protected fun waitForSplashToComplete(timeoutMillis: Long = 20000) {
        waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText("Getting things ready...")
                .fetchSemanticsNodes().isEmpty()
        }
        waitForIdle()
    }

    /**
     * Wait for screen to disappear (after navigation).
     * Example: waitUntilScreenGone("Templates")
     */
    protected fun waitUntilScreenGone(screenText: String, timeoutMillis: Long = 3000) {
        waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(screenText)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Wait for screen to become visible (after navigation).
     * Example: waitUntilScreenVisible("Checklist Details")
     */
    protected fun waitUntilScreenVisible(screenText: String, timeoutMillis: Long = 3000) {
        waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(screenText)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Wait for a button to appear and become clickable.
     * Use before performClick() to ensure button is ready.
     * Example: waitForButton("AI Analysis")
     * Example with substring: waitForButton("credits", substring = true)
     */
    protected fun waitForButton(
        buttonText: String,
        substring: Boolean = false,
        timeoutMillis: Long = 10000
    ) {
        waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(buttonText, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
    }

    // ===== Device Interaction =====

    /**
     * Press the device back button.
     */
    protected fun pressBack() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
    }

    // ===== MockK Helpers (Optional - add when needed) =====
    //
    // Uncomment and implement if you need to mock services:
    //
    // private val mockAiAnalyzer = mockk<AiAnalyzer>(relaxed = true)
    // private val mockPaywallRepository = mockk<PaywallRepository>(relaxed = true)
    //
    // @Before
    // fun setupMocks() {
    //     stopKoin()
    //     startKoin {
    //         androidContext(composeTestRule.activity.applicationContext)
    //         modules(
    //             module {
    //                 single<AiAnalyzer> { mockAiAnalyzer }
    //                 single<PaywallRepository> { mockPaywallRepository }
    //             }
    //         )
    //     }
    // }
    //
    // @After
    // fun teardownKoin() {
    //     stopKoin()
    // }
    //
    // protected fun givenAiAnalysisReturns(result: AnalyzeResult) {
    //     coEvery { mockAiAnalyzer.analyze(any(), any()) } returns Result.success(result)
    // }
}
