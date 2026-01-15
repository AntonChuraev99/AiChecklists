package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for the Onboarding flow.
 *
 * Tests cover:
 * 1. Initial onboarding screen is displayed
 * 2. Navigation through onboarding pages
 * 3. Skip button functionality
 * 4. Get Started button completes onboarding
 */
class OnboardingFlowTest : BaseUiTest() {

    @Test
    fun onboarding_displaysFirstPage() {
        // Given: App is launched for the first time
        waitForIdle()

        // Then: First onboarding page is displayed
        composeTestRule
            .onNodeWithText("Capture Everything")
            .assertIsDisplayed()

        // And: Continue button is displayed
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsDisplayed()

        // And: Skip button is displayed
        composeTestRule
            .onNodeWithText("Skip")
            .assertIsDisplayed()
    }

    @Test
    fun onboarding_navigatesThroughPages() {
        waitForIdle()

        // Given: First page is displayed
        composeTestRule
            .onNodeWithText("Capture Everything")
            .assertIsDisplayed()

        // When: Click Continue
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()

        waitForIdle()

        // Then: Second page is displayed
        composeTestRule
            .onNodeWithText("AI Does the Work")
            .assertIsDisplayed()

        // When: Click Continue again
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()

        waitForIdle()

        // Then: Third page is displayed with Get Started button
        composeTestRule
            .onNodeWithText("Never Miss a Thing")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Get Started")
            .assertIsDisplayed()
    }

    @Test
    fun onboarding_skipNavigatesToMainScreen() {
        waitForIdle()

        // Given: Onboarding is displayed
        composeTestRule
            .onNodeWithText("Skip")
            .assertIsDisplayed()

        // When: Click Skip
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()

        waitForIdle()

        // Then: Main screen is displayed
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()
    }

    @Test
    fun onboarding_getStartedNavigatesToMainScreen() {
        waitForIdle()

        // Navigate to the last page
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        // Given: Last page is displayed
        composeTestRule
            .onNodeWithText("Get Started")
            .assertIsDisplayed()

        // When: Click Get Started
        composeTestRule
            .onNodeWithText("Get Started")
            .performClick()

        waitForIdle()

        // Then: Main screen is displayed
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()
    }
}
