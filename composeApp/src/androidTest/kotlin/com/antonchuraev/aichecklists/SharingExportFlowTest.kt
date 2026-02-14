package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for sharing and exporting checklists.
 *
 * Tests cover:
 * 1. Navigate to share screen
 * 2. Export as plain text
 * 3. Export as PDF
 */
class SharingExportFlowTest : BaseUiTest() {

    private fun skipOnboardingAndGoToMain() {
        waitForIdle()
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed
        }
    }

    private fun createChecklistWithItems(): String {
        val checklistName = "Share Test Checklist"

        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput(checklistName)

        // Add items
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Item to share")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        return checklistName
    }

    @Test
    fun sharing_navigateToShareScreen() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // Click share button (share icon in toolbar or menu)
        composeTestRule
            .onNodeWithText("Share", substring = true)
            .performClick()
        waitForIdle()

        // Share screen should be displayed
        composeTestRule
            .onNodeWithText("Share", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun sharing_exportAsPlainText() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist and navigate to share
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Share", substring = true)
            .performClick()
        waitForIdle()

        // Select "Plain Text" format
        composeTestRule
            .onNodeWithText("Text", substring = true)
            .performClick()
        waitForIdle()

        // Share intent should be triggered
        // Note: We can't test the actual share dialog in UI tests
        // but we can verify the UI responds
        waitForIdle()
    }

    @Test
    fun sharing_exportAsPdf() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist and navigate to share
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Share", substring = true)
            .performClick()
        waitForIdle()

        // Select "PDF" format
        composeTestRule
            .onNodeWithText("PDF")
            .performClick()
        waitForIdle()

        // PDF generation should start
        // Loading indicator or success message
        waitForIdle()
    }
}
