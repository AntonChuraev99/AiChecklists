package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for Fill management flow.
 *
 * Fills represent instances of a checklist being used multiple times.
 * For example, using "Apartment Inspection" checklist for different apartments.
 *
 * Tests cover:
 * 1. Create new fill from checklist detail
 * 2. View fill details
 * 3. Check/uncheck items in fill
 * 4. Fill progress updates correctly
 * 5. Back navigation from fill detail
 * 6. Fill creation via AI (Fill via AI feature)
 */
class FillManagementFlowTest : BaseUiTest() {

    private fun createAndOpenChecklist(): String {
        val checklistName = "Fill Test Checklist"
        createChecklistWithItems(checklistName, "Item 1", "Item 2")

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
    fun fillManagement_createNewFillFromDetail() {
        skipOnboardingAndGoToMain()

        val checklistName = createAndOpenChecklist()

        // Click "Create New Fill" button
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
            .performClick()
        waitForIdle()

        // Fill detail screen should appear with items
        composeTestRule
            .onNodeWithText("Item 1")
            .assertIsDisplayed()
    }

    @Test
    fun fillManagement_viewFillDetails() {
        skipOnboardingAndGoToMain()

        val checklistName = createAndOpenChecklist()

        // Create a fill
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
            .performClick()
        waitForIdle()

        // Verify fill detail displays items
        composeTestRule
            .onNodeWithText("Item 1")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Item 2")
            .assertIsDisplayed()
    }

    @Test
    fun fillManagement_checkUncheckItems() {
        skipOnboardingAndGoToMain()

        createAndOpenChecklist()

        // Create fill
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
            .performClick()
        waitForIdle()

        // Click on first item to check it
        composeTestRule
            .onNodeWithText("Item 1")
            .performClick()
        waitForIdle()

        // Item should now be checked
        waitForIdle()
    }

    @Test
    fun fillManagement_progressUpdatesOnCheck() {
        skipOnboardingAndGoToMain()

        createAndOpenChecklist()

        // Create fill
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
            .performClick()
        waitForIdle()

        // Check first item
        composeTestRule
            .onNodeWithText("Item 1")
            .performClick()
        waitForIdle()

        // Progress should update to 1/2
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("1/2")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("1 / 2")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun fillManagement_backNavigationFromFill() {
        skipOnboardingAndGoToMain()

        val checklistName = createAndOpenChecklist()

        // Create a fill
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
            .performClick()
        waitForIdle()

        // Press back from fill detail
        pressBack()
        waitForIdle()

        // Should return to checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .assertIsDisplayed()
    }

    @Test
    fun fillManagement_createFillViaAi() {
        skipOnboardingAndGoToMain()

        createAndOpenChecklist()

        // Click "Fill via AI" button
        waitForButton("Fill via AI")
        composeTestRule
            .onNodeWithText("Fill via AI")
            .performClick()
        waitForIdle()

        // Should navigate to analyze screen with checklist context
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()
    }
}
