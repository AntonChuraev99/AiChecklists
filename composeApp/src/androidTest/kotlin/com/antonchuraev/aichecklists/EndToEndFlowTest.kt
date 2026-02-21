package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * End-to-end UI tests covering complete user journeys.
 *
 * Tests cover:
 * 1. Complete onboarding to checklist creation flow
 * 2. Navigation: main → analyze → back
 * 3. Credits flow: main → paywall → back
 * 4. Checklist detail flow: create → detail → back
 */
class EndToEndFlowTest : BaseUiTest() {

    @Test
    @Smoke
    fun completeUserJourney_onboardingToChecklistCreation() {
        waitForSplashToComplete()

        // Step 1: Complete onboarding (4 pages)
        composeTestRule
            .onNodeWithText("Create via AI")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Fill via AI")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Export & Share")
            .assertIsDisplayed()

        // Continue to 4th page (trial)
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        // Skip trial to go to main screen
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()
        waitForIdle()

        // Step 2: Verify main screen (empty state)
        assertOnMainScreen()

        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // Step 3: Create a checklist using helpers
        createChecklistWithItems("My First Checklist", "Task 1", "Task 2")

        // Step 4: Verify checklist appears on main screen
        assertOnMainScreen()

        composeTestRule
            .onNodeWithText("My First Checklist")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun navigationFlow_mainToAnalyzeAndBack() {
        skipOnboardingAndGoToMain()

        // Navigate to analyze via Create Checklist → Create with AI
        navigateToAnalyze()

        // Verify analyze screen
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()

        // Go back twice (analyze → templates → main)
        pressBack()
        waitForIdle()
        pressBack()
        waitForIdle()

        // Verify back on main screen (empty state)
        assertOnMainScreen()
    }

    @Test
    @Smoke
    fun creditsFlow_viewCreditsAndPaywall() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall via Get More
        navigateToPaywall()

        // Verify paywall screen (trial offer for non-premium user)
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3-Day Free Trial")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("3-Day Free Trial")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Start Free Trial")
            .assertIsDisplayed()

        // Dismiss paywall via Skip
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()
        waitForIdle()

        // Verify back on main screen
        assertOnMainScreen()
    }

    @Test
    @Smoke
    fun checklistDetailFlow_viewAndInteract() {
        skipOnboardingAndGoToMain()

        // Create a checklist
        createChecklistWithItems("Detail Test Checklist", "Check this item")

        // Click on the checklist to view details
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Detail Test Checklist")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Detail Test Checklist")
            .performClick()
        waitForIdle()

        // Verify detail screen
        composeTestRule
            .onNodeWithText("Detail Test Checklist")
            .assertIsDisplayed()

        // Go back to main
        pressBack()
        waitForIdle()

        // Verify back on main screen
        assertOnMainScreen()
    }
}
