package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for the Main Screen flow.
 *
 * Tests cover:
 * 1. Empty state display
 * 2. Checklist list display
 * 3. Credits chip display
 * 4. Navigation to different screens
 */
class MainScreenFlowTest : BaseUiTest() {

    private fun skipOnboardingAndGoToMain() {
        waitForIdle()
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed
        }
    }

    @Test
    fun mainScreen_displaysEmptyState() {
        skipOnboardingAndGoToMain()

        // Then: Main screen is displayed
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()

        // And: Empty state message is displayed
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // And: Create Checklist button is displayed
        composeTestRule
            .onNodeWithText("Create Checklist")
            .assertIsDisplayed()

        // And: AI Analysis button is displayed
        composeTestRule
            .onNodeWithText("AI Analysis")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_displaysCreditsChip() {
        skipOnboardingAndGoToMain()

        // Then: Credits chip should be displayed in toolbar
        // It shows "X credits" format
        composeTestRule
            .onNodeWithText("credits", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_navigatesToAiAnalysis() {
        skipOnboardingAndGoToMain()

        // When: Click AI Analysis button
        composeTestRule
            .onNodeWithText("AI Analysis")
            .performClick()

        waitForIdle()

        // Then: Analyze screen is displayed
        composeTestRule
            .onNodeWithText("AI Analysis")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_createdChecklistAppears() {
        skipOnboardingAndGoToMain()

        // Create a checklist first
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Shopping List")

        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Buy groceries")

        // Save item
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Then: Checklist should appear in the list
        composeTestRule
            .onNodeWithText("Shopping List")
            .assertIsDisplayed()

        // And: Progress indicator should be shown (0/1)
        composeTestRule
            .onNodeWithText("0/1")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_clickChecklistNavigatesToDetail() {
        skipOnboardingAndGoToMain()

        // Create a checklist first
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Detail Test")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Click on the checklist
        composeTestRule
            .onNodeWithText("Detail Test")
            .performClick()

        waitForIdle()

        // Should navigate to detail screen
        composeTestRule
            .onNodeWithText("Detail Test")
            .assertIsDisplayed()
    }
}
