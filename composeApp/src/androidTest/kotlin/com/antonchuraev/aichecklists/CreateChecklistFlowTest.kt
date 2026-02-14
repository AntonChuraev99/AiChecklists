package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for the Create Checklist flow.
 *
 * Tests cover:
 * 1. Navigate to create screen
 * 2. Enter checklist name
 * 3. Add items to checklist
 * 4. Delete items from checklist
 * 5. Save checklist
 * 6. Validation errors
 */
class CreateChecklistFlowTest : BaseUiTest() {

    private fun skipOnboardingAndGoToMain() {
        waitForSplashToComplete()
        // Skip onboarding if shown
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            // Wait for Main screen to appear (up to 10 seconds)
            waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Ready to get organized?")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed - verify Main screen
            waitUntil(5000) {
                composeTestRule.onAllNodesWithText("Ready to get organized?")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun createChecklist_navigatesToCreateScreen() {
        skipOnboardingAndGoToMain()

        // Given: Main screen is displayed (empty state after Test Orchestrator clears data)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // When: Click Create Checklist button
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()

        waitForIdle()

        // Then: Create screen is displayed
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Checklist Name")
            .assertIsDisplayed()
    }

    @Test
    fun createChecklist_enterNameAndAddItems() {
        skipOnboardingAndGoToMain()

        // Navigate to create screen
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Enter checklist name
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("My Test Checklist")

        waitForIdle()

        // Click Add Item button
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()

        waitForIdle()

        // Dialog should appear
        composeTestRule
            .onNodeWithText("Add Item")
            .assertIsDisplayed()

        // Enter item text
        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("First item")

        // Save item
        composeTestRule
            .onNodeWithText("Save")
            .performClick()

        waitForIdle()

        // Item should be displayed
        composeTestRule
            .onNodeWithText("First item")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun createChecklist_saveWithEmptyNameShowsError() {
        skipOnboardingAndGoToMain()

        // Navigate to create screen
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Try to save without entering name
        composeTestRule
            .onNodeWithText("Save")
            .performClick()

        waitForIdle()

        // Should still be on create screen (not saved)
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()
    }

    @Test
    fun createChecklist_saveSuccessfully() {
        skipOnboardingAndGoToMain()

        // Navigate to create screen
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Enter checklist name
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Test Checklist")

        // Add an item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Test item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save the checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()

        waitForIdle()

        // Should navigate back to main screen
        // After creating first checklist, main_title is shown (not empty state)
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()

        // Created checklist should be visible
        composeTestRule
            .onNodeWithText("Test Checklist")
            .assertIsDisplayed()
    }

    @Test
    fun createChecklist_backButtonNavigatesToMain() {
        skipOnboardingAndGoToMain()

        // Navigate to create screen
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Click back button (content description "Назад")
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        // Use the back action - simulate back press
        pressBack()

        waitForIdle()

        // Should be back on main screen (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }
}
