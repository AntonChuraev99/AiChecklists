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
    @Smoke
    fun onboarding_displaysFirstPage() {
        // Given: App is launched for the first time
        waitForSplashToComplete()

        // Then: First onboarding page is displayed
        composeTestRule
            .onNodeWithText("Create via AI")
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
        waitForSplashToComplete()

        // Given: First page is displayed
        composeTestRule
            .onNodeWithText("Create via AI")
            .assertIsDisplayed()

        // When: Click Continue
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()

        waitForIdle()

        // Then: Second page is displayed
        composeTestRule
            .onNodeWithText("Fill via AI")
            .assertIsDisplayed()

        // When: Click Continue again
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()

        waitForIdle()

        // Then: Third page is displayed
        composeTestRule
            .onNodeWithText("Export & Share")
            .assertIsDisplayed()

        // When: Click Continue to go to 4th (trial) page
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()

        waitForIdle()

        // Then: Fourth page shows trial offer
        composeTestRule
            .onNodeWithText("Start your FREE trial")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun onboarding_skipNavigatesToMainScreen() {
        waitForSplashToComplete()

        // Given: Onboarding is displayed
        composeTestRule
            .onNodeWithText("Skip")
            .assertIsDisplayed()

        // When: Click Skip
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()

        waitForIdle()

        // Then: Main screen is displayed (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }

    @Test
    fun onboarding_skipFromLastPageNavigatesToMainScreen() {
        waitForSplashToComplete()

        // Navigate to the last (4th) page
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        waitForIdle()

        // Given: Last page (trial) is displayed
        composeTestRule
            .onNodeWithText("Start your FREE trial")
            .assertIsDisplayed()

        // When: Click Skip (don't start trial in tests)
        composeTestRule
            .onNodeWithText("Skip")
            .performClick()

        waitForIdle()

        // Then: Main screen is displayed (empty state)
        assertOnMainScreen()
    }
}
