package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    // ===== Common Navigation Helpers =====

    /**
     * Skip onboarding and wait for main screen to appear.
     *
     * CRITICAL: This method waits for Splash to complete first (10-15 seconds),
     * then skips onboarding. All tests MUST use this instead of manual splash handling.
     *
     * After this call, "Create Checklist" button is visible on main screen.
     */
    protected fun skipOnboardingAndGoToMain() {
        waitForSplashToComplete()
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Create Checklist")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed
            waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Create Checklist")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /**
     * Navigate from main screen to the manual create checklist form.
     *
     * Flow: Main → Templates screen → "Create Manually" → Create form
     * After this call, "e.g., Project Tasks" placeholder is visible.
     */
    protected fun navigateToCreateForm() {
        waitForButton("Create Checklist")
        composeTestRule.onNodeWithText("Create Checklist").performClick()
        waitForIdle()
        waitForButton("Create Manually")
        composeTestRule.onNodeWithText("Create Manually").performClick()
        waitForIdle()
    }

    /**
     * Navigate from main screen to the AI Analyze screen.
     *
     * Flow: Main → Templates screen → "Create with AI" → Analyze screen
     */
    protected fun navigateToAnalyze() {
        waitForButton("Create Checklist")
        composeTestRule.onNodeWithText("Create Checklist").performClick()
        waitForIdle()
        waitForButton("Create with AI")
        composeTestRule.onNodeWithText("Create with AI").performClick()
        waitForIdle()
    }

    /**
     * Add an item to checklist on the create/edit form.
     *
     * Uses the inline "Add new item..." field + "+" button (content desc: "Add item").
     * NOT a dialog — items are added directly in the list.
     */
    protected fun addItemToChecklist(text: String) {
        composeTestRule
            .onNode(hasText("Add new item..."))
            .performTextInput(text)
        composeTestRule
            .onNode(hasContentDescription("Add item"))
            .performClick()
        waitForIdle()
    }

    /**
     * Create a checklist with given name and items, then return to main screen.
     *
     * Full flow: Main → Templates → Create Manually → fill form → Save → Main
     */
    protected fun createChecklistWithItems(name: String, vararg items: String) {
        navigateToCreateForm()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput(name)

        for (item in items) {
            addItemToChecklist(item)
        }

        composeTestRule.onNodeWithText("Save").performClick()
        waitForIdle()
    }

    /**
     * Navigate to paywall via "Get More" button.
     *
     * Note: When credits = 0, the chip shows "Get More".
     * When credits > 0, it shows "{N} credits".
     */
    protected fun navigateToPaywall() {
        waitForButton("Get More")
        composeTestRule.onNodeWithText("Get More").performClick()
        waitForIdle()
    }

    /**
     * Assert we're on main screen by checking "Create Checklist" button is visible.
     */
    protected fun assertOnMainScreen() {
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Create Checklist")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Create Checklist").assertIsDisplayed()
    }
}
