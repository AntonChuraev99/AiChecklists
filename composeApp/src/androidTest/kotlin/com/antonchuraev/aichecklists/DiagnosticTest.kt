package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import java.io.File

/**
 * Diagnostic test to understand what happens after Splash -> Skip -> ?
 *
 * This test captures screenshots and UI dumps at each step to diagnose
 * why navigation from Onboarding to Main screen is failing.
 */
class DiagnosticTest : BaseUiTest() {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun takeScreenshot(name: String) {
        val screenshotFile = File("/sdcard/Pictures/diagnostic_$name.png")
        device.takeScreenshot(screenshotFile)
        println("📸 Screenshot saved: $name")
    }

    private fun dumpUiHierarchy(label: String) {
        println("🔍 UI Hierarchy at: $label")
        println("=".repeat(60))
        println("Available text nodes:")
        // This will print to logcat
        println("(Check logcat for detailed hierarchy)")
    }

    @Test
    fun diagnostic_captureNavigationFlow() {
        println("\n========================================")
        println("🔬 DIAGNOSTIC TEST START")
        println("========================================\n")

        // Step 1: Wait for Splash to complete
        println("⏳ Step 1: Waiting for Splash to complete...")
        waitForSplashToComplete()
        takeScreenshot("01_after_splash")
        println("✅ Splash completed\n")

        // Step 2: Verify Onboarding is displayed
        println("🔍 Step 2: Checking Onboarding screen...")
        try {
            composeTestRule
                .onNodeWithText("Create via AI")
                .assertIsDisplayed()
            println("✅ Onboarding screen visible")
        } catch (e: AssertionError) {
            println("❌ ERROR: Onboarding NOT visible!")
            println("Exception: ${e.message}")
            takeScreenshot("02_error_no_onboarding")
            throw e
        }

        // Step 3: Check if Skip button is visible
        println("\n🔍 Step 3: Checking Skip button...")
        try {
            composeTestRule
                .onNodeWithText("Skip")
                .assertIsDisplayed()
            println("✅ Skip button visible")
            takeScreenshot("03_before_skip_click")
        } catch (e: AssertionError) {
            println("❌ ERROR: Skip button NOT visible!")
            println("Exception: ${e.message}")
            takeScreenshot("03_error_no_skip_button")
            throw e
        }

        // Step 4: Click Skip
        println("\n👆 Step 4: Clicking Skip button...")
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()
        println("✅ Skip clicked")

        // Step 5: Wait for navigation (various durations)
        println("\n⏳ Step 5: Waiting for navigation...")

        // Wait 1 second
        Thread.sleep(1000)
        takeScreenshot("04_one_second_after_skip")
        println("📊 After 1 second:")

        // Check what's visible
        val hasMainScreen = try {
            composeTestRule.onNodeWithText("My Checklists").assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }

        val hasOnboarding = try {
            composeTestRule.onNodeWithText("Create via AI").assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }

        println("  - Main screen visible: $hasMainScreen")
        println("  - Onboarding visible: $hasOnboarding")

        // Wait another 2 seconds
        Thread.sleep(2000)
        takeScreenshot("05_three_seconds_after_skip")
        println("\n📊 After 3 seconds total:")

        val hasMainScreen2 = try {
            composeTestRule.onNodeWithText("My Checklists").assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }

        println("  - Main screen visible: $hasMainScreen2")

        // Wait another 5 seconds
        Thread.sleep(5000)
        takeScreenshot("06_eight_seconds_after_skip")
        println("\n📊 After 8 seconds total:")

        val hasMainScreen3 = try {
            composeTestRule.onNodeWithText("My Checklists").assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }

        println("  - Main screen visible: $hasMainScreen3")

        // Step 6: Final check with waitUntil
        println("\n⏳ Step 6: Using waitUntil for Main screen...")
        try {
            waitUntil(15000) {
                composeTestRule.onAllNodesWithText("My Checklists")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            println("✅ Main screen appeared!")
            takeScreenshot("07_main_screen_appeared")
        } catch (e: Exception) {
            println("❌ ERROR: Main screen never appeared after 15 seconds!")
            takeScreenshot("07_error_no_main_screen")
            throw AssertionError("Main screen did not appear within 15 seconds after Skip")
        }

        println("\n========================================")
        println("✅ DIAGNOSTIC TEST COMPLETED")
        println("========================================\n")
    }
}
