package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for sharing and exporting checklists.
 *
 * Share screen shows:
 * - Title: "Share Checklist"
 * - Formats: "Plain Text", "PDF Document"
 * - "Share" button (disabled until format selected)
 *
 * Tests cover:
 * 1. Navigate to share screen from checklist detail
 * 2. Export as plain text
 * 3. Export as PDF document
 */
class SharingExportFlowTest : BaseUiTest() {

    private fun createAndOpenChecklist(): String {
        val checklistName = "Share Test Checklist"
        createChecklistWithItems(checklistName, "Item to share")

        // Open checklist detail
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(checklistName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        return checklistName
    }

    @Test
    fun sharing_navigateToShareScreen() {
        skipOnboardingAndGoToMain()

        createAndOpenChecklist()

        // Click share button (icon in toolbar with content description "Share")
        composeTestRule
            .onNode(hasContentDescription("Share"))
            .performClick()
        waitForIdle()

        // Share screen should be displayed (use unique title to avoid multi-node match)
        composeTestRule
            .onNodeWithText("Share Checklist")
            .assertIsDisplayed()

        // Format options should be visible
        composeTestRule
            .onNodeWithText("Plain Text")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("PDF Document")
            .assertIsDisplayed()
    }

    @Test
    fun sharing_exportAsPlainText() {
        skipOnboardingAndGoToMain()

        createAndOpenChecklist()

        // Click share icon
        composeTestRule
            .onNode(hasContentDescription("Share"))
            .performClick()
        waitForIdle()

        // Select "Plain Text" format
        composeTestRule
            .onNodeWithText("Plain Text")
            .performClick()
        waitForIdle()

        // Share intent triggered (can't test actual share dialog)
        waitForIdle()
    }

    @Test
    fun sharing_exportAsPdf() {
        skipOnboardingAndGoToMain()

        createAndOpenChecklist()

        // Click share icon
        composeTestRule
            .onNode(hasContentDescription("Share"))
            .performClick()
        waitForIdle()

        // Select "PDF Document" format
        composeTestRule
            .onNodeWithText("PDF Document")
            .performClick()
        waitForIdle()

        // PDF generation started
        waitForIdle()
    }
}
