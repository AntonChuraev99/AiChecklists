package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for the Credits and Premium flow.
 *
 * Note: Paywall is a pager-style screen with trial timeline,
 * not a static benefits list. Key elements:
 * - "3 Days for Free" header
 * - "Start your FREE trial" button
 * - "Restore Purchase" link
 * - "Skip" to dismiss
 *
 * Tests cover:
 * 1. Credits / Get More display in toolbar
 * 2. Click on Get More navigates to paywall
 * 3. Paywall screen elements display
 * 4. Paywall back navigation
 * 5. Credits info on analyze screen
 * 6. Get More button state
 */
class CreditsFlowTest : BaseUiTest() {

    @Test
    fun creditsChip_displaysCreditsArea() {
        skipOnboardingAndGoToMain()

        // "Go Premium" banner with "Daily AI credits refill" is on main screen
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Go Premium")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Go Premium")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun creditsChip_clickGetMoreNavigatesToPaywall() {
        skipOnboardingAndGoToMain()

        // Click on "Get More" chip (free user with 0 credits)
        navigateToPaywall()

        // Paywall screen should be displayed with trial offer
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3 Days for Free")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("3 Days for Free")
            .assertIsDisplayed()
    }

    @Test
    fun paywallScreen_displaysTrialInfo() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall
        navigateToPaywall()

        // Trial offer should be displayed
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3 Days for Free")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("3 Days for Free")
            .assertIsDisplayed()

        // Start trial button
        composeTestRule
            .onNodeWithText("Start your FREE trial")
            .assertIsDisplayed()

        // Cancel anytime reassurance
        composeTestRule
            .onNodeWithText("Cancel anytime")
            .assertIsDisplayed()

        // Restore purchase link
        composeTestRule
            .onNodeWithText("Restore Purchase")
            .assertIsDisplayed()
    }

    @Test
    fun paywallScreen_skipReturnsToMain() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall
        navigateToPaywall()

        // Verify we're on paywall
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3 Days for Free")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Press Skip to dismiss
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()
        waitForIdle()

        // Should be back on main screen
        assertOnMainScreen()
    }

    @Test
    fun analyzeScreen_showsCostInfo() {
        skipOnboardingAndGoToMain()

        // Navigate to analyze screen via Create Checklist → Create with AI
        navigateToAnalyze()

        // Select an input type
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // Cost info should be shown (specific text to avoid multi-node match)
        composeTestRule
            .onNodeWithText("This action costs", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_getMoreButtonVisible() {
        skipOnboardingAndGoToMain()

        // Verify main screen is displayed
        assertOnMainScreen()

        // "Get More" button should be present
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Get More")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
