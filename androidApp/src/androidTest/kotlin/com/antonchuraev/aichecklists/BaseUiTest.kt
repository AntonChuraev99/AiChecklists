package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

    /**
     * Re-open the Analyze screen's source picker after a material has already been chosen.
     *
     * Choosing a material COLLAPSES the six-pill grid to a single "current material" pill, so a test
     * that switches material twice cannot simply tap the second pill — it is not on screen. The
     * collapsed pill is addressed by its accessible name rather than by its visible word, because that
     * word's semantics node is deliberately cleared (the override already announces the material, and
     * two nodes would make a screen reader say it twice).
     *
     * @param currentMaterial the label the picker is collapsed ON, e.g. [ANALYZE_SOURCE_PHOTO].
     */
    protected fun reopenAnalyzeSourcePicker(currentMaterial: String) {
        composeTestRule
            .onNodeWithContentDescription("Change source: $currentMaterial")
            .performClick()
        waitForIdle()
    }

    companion object {
        /**
         * The Analyze screen's source heading — `analyze_select_source_short` in
         * `core/designsystem/.../strings.xml`.
         *
         * Named here rather than spelled out at each of the six call sites, because it is the anchor
         * for "we have arrived on Analyze with nothing chosen yet" and it moved once already: it used
         * to be `analyze_select_source` ("What would you like to analyze?"), which the compact-picker
         * redesign deleted. Six literals went stale in one edit and nothing caught it — these tests
         * run only under `connectedAndroidTest`, so the whole host suite stayed green. One constant
         * makes the next copy change a one-line fix instead of a six-file search.
         *
         * ⚠️ The heading is rendered ONLY while `selectedInputType == null`. Every use of it must be a
         * screen entered with no material preselected (Templates → "Create with AI", "Fill via AI",
         * the debug catalog's `catalog_analyze_empty`) — never a door from the v2 capture dock, which
         * opens ON a material and shows the collapsed pill instead.
         */
        const val ANALYZE_SOURCE_HEADING = "Choose a source"

        // ── The six material labels the Analyze picker offers ────────────────────────────────────
        // SHORT forms, and that is load-bearing rather than a copy preference: the pills are
        // equal-width, so the widest label alone decides how many fit abreast, and the long forms
        // ("Text File", "Web Link", "Paste Text") measured the compact grid down to one column. Those
        // three long strings are what these tests used to tap, which is why they are named here — one
        // place to change when the copy moves, instead of eight literals across two files.
        const val ANALYZE_SOURCE_PHOTO = "Photo"
        const val ANALYZE_SOURCE_PDF = "PDF"

        /** `analyze_source_text_file_short` — was "Text File". */
        const val ANALYZE_SOURCE_FILE = "File"

        /** `analyze_source_link_short` — was "Web Link". */
        const val ANALYZE_SOURCE_LINK = "Link"

        /** `analyze_source_text_short` — was "Paste Text". */
        const val ANALYZE_SOURCE_TEXT = "Text"
        const val ANALYZE_SOURCE_VOICE = "Voice"
    }
}
