package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * End-to-end UI tests covering complete user journeys.
 *
 * Tests cover:
 * 1. Complete onboarding to checklist creation flow
 * 2. Create, view, and interact with checklist
 * 3. Full app navigation flow
 */
class EndToEndFlowTest : BaseUiTest() {

    @Test
    @Smoke
    fun completeUserJourney_onboardingToChecklistCreation() {
        waitForSplashToComplete()

        // Step 1: Complete onboarding
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

        composeTestRule
            .onNodeWithText("Get Started")
            .performClick()
        waitForIdle()

        // Step 2: Verify main screen
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // Step 3: Create a checklist
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        // Enter checklist name
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("My First Checklist")

        // Add first item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Task 1")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Add second item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Task 2")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify items are displayed
        composeTestRule
            .onNodeWithText("Task 1")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Task 2")
            .assertIsDisplayed()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Step 4: Verify checklist appears on main screen
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("My First Checklist")
            .assertIsDisplayed()

        // Verify progress indicator
        composeTestRule
            .onNodeWithText("0/2")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun navigationFlow_mainToAnalyzeAndBack() {
        waitForSplashToComplete()

        // Skip onboarding
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding already completed
        }

        // Verify main screen (empty state after clearPackageData)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // Navigate to AI Analysis
        waitForButton("AI Analysis")
        composeTestRule
            .onNodeWithText("AI Analysis")
            .performClick()
        waitForIdle()

        // Verify analyze screen
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()

        // Select input type
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // Verify input area
        composeTestRule
            .onNodeWithText("Your text")
            .assertIsDisplayed()

        // Go back
        pressBack()
        waitForIdle()

        // Verify back on main screen (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun creditsFlow_viewCreditsAndPaywall() {
        waitForSplashToComplete()

        // Skip onboarding
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding already completed
        }

        // Verify main screen (empty state after clearPackageData)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // Click on credits chip
        waitForButton("credits", substring = true)
        composeTestRule
            .onNodeWithText("credits", substring = true)
            .performClick()
        waitForIdle()

        // Verify paywall screen (for non-premium user)
        composeTestRule
            .onNodeWithText("Unlock Your Full Potential")
            .assertIsDisplayed()

        // Verify premium features are listed
        composeTestRule
            .onNodeWithText("Daily AI credits refill", substring = true)
            .assertIsDisplayed()

        // Go back
        pressBack()
        waitForIdle()

        // Verify back on main screen (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun checklistDetailFlow_viewAndInteract() {
        waitForSplashToComplete()

        // Skip onboarding
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding already completed
        }

        // Create a checklist first
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Detail Test Checklist")

        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Check this item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Click on the checklist to view details
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

        // Verify back on main screen (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }
}
