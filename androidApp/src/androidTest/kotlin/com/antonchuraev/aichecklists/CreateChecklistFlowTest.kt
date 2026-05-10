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
 * 1. Navigate to templates screen
 * 2. Navigate to create form via "Create Manually"
 * 3. Enter checklist name and add items (inline flow)
 * 4. Save checklist
 * 5. Validation errors
 * 6. Back navigation
 */
class CreateChecklistFlowTest : BaseUiTest() {

    @Test
    fun createChecklist_navigatesToTemplatesScreen() {
        skipOnboardingAndGoToMain()

        // Given: Main screen is displayed (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // When: Click Create Checklist button
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()

        waitForIdle()

        // Then: Templates screen is displayed
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        // And: Bottom action buttons are present
        composeTestRule
            .onNodeWithText("Create Manually")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Create with AI")
            .assertIsDisplayed()
    }

    @Test
    fun createChecklist_enterNameAndAddItems() {
        skipOnboardingAndGoToMain()

        // Navigate to create form via Templates → Create Manually
        navigateToCreateForm()

        // Enter checklist name
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("My Test Checklist")

        waitForIdle()

        // Add item using inline flow (type in "Add new item..." field + click "+" button)
        addItemToChecklist("First item")

        // Item should be displayed
        composeTestRule
            .onNodeWithText("First item")
            .assertIsDisplayed()
    }

    @Test
    @Smoke
    fun createChecklist_saveWithEmptyNameShowsError() {
        skipOnboardingAndGoToMain()

        // Navigate to create form
        navigateToCreateForm()

        // Try to save without entering name
        composeTestRule
            .onNodeWithText("Save")
            .performClick()

        waitForIdle()

        // Should still be on create screen (not saved)
        // The "e.g., Project Tasks" placeholder should still be visible
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .assertExists()
    }

    @Test
    fun createChecklist_saveSuccessfully() {
        skipOnboardingAndGoToMain()

        // Create a checklist using helper
        createChecklistWithItems("Test Checklist", "Test item")

        // Should navigate back to main screen
        assertOnMainScreen()

        // Created checklist should be visible
        composeTestRule
            .onNodeWithText("Test Checklist")
            .assertIsDisplayed()
    }

    @Test
    fun createChecklist_backButtonNavigatesToMain() {
        skipOnboardingAndGoToMain()

        // Navigate to templates screen
        waitForButton("Create Checklist")
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Verify templates screen
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        // Press back
        pressBack()

        waitForIdle()

        // Should be back on main screen (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }
}
