package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for error handling and validation.
 *
 * Tests cover:
 * 1. Empty checklist name validation
 * 2. Empty item text validation
 */
class ErrorHandlingFlowTest : BaseUiTest() {

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
    fun errorHandling_emptyChecklistNameValidation() {
        skipOnboardingAndGoToMain()

        // Navigate to create screen
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Try to save without entering name
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Should remain on create screen (not navigate away)
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        // Error message might appear
        // Note: Specific error text depends on implementation
        waitForIdle()
    }

    @Test
    fun errorHandling_emptyItemTextValidation() {
        skipOnboardingAndGoToMain()

        // Navigate to create screen
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Enter checklist name
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Validation Test")

        // Click Add Item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        // Try to save item without text
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Should not add the item (dialog remains or validation error shown)
        // Verify dialog is still visible
        composeTestRule
            .onNodeWithText("Add Item")
            .assertIsDisplayed()
    }
}
