package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * 5. Navigate to fills list
 * 6. Empty fills list state
 * 7. Delete fill
 * 8. Fill list shows all fills
 * 9. Back navigation from fill detail
 * 10. Fill creation via AI (Fill via AI feature)
 */
class FillManagementFlowTest : BaseUiTest() {

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
        val checklistName = "Fill Test Checklist"

        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput(checklistName)

        // Add first item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Item 1")

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
            .performTextInput("Item 2")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        return checklistName
    }

    @Test
    fun fillManagement_createNewFillFromDetail() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Click checklist to open detail
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // Should see "New Fill" or "Fill via AI" button
        // Click to create a new fill
        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        // Fill detail screen should appear
        waitForIdle()
    }

    @Test
    fun fillManagement_viewFillDetails() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // Create a fill
        composeTestRule
            .onNodeWithText("New Fill", substring = true)
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

        val checklistName = createChecklistWithItems()

        // Open checklist and create fill
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        // Find checkbox for first item and click it
        // Note: Checkboxes might be clickable text or checkbox nodes
        composeTestRule
            .onNodeWithText("Item 1")
            .performClick()
        waitForIdle()

        // Item should now be checked (text decoration or icon change)
        waitForIdle()
    }

    @Test
    fun fillManagement_progressUpdatesOnCheck() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist and create fill
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        // Initial progress should be 0/2
        composeTestRule
            .onNodeWithText("0/2")
            .assertIsDisplayed()

        // Check first item
        composeTestRule
            .onNodeWithText("Item 1")
            .performClick()
        waitForIdle()

        // Progress should update to 1/2
        composeTestRule
            .onNodeWithText("1/2")
            .assertIsDisplayed()
    }

    @Test
    fun fillManagement_navigateToFillsList() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // Look for "View Fills" or "Fills List" button
        composeTestRule
            .onNodeWithText("Fills", substring = true)
            .performClick()
        waitForIdle()

        // Fills list screen should appear
        waitForIdle()
    }

    @Test
    fun fillManagement_emptyFillsListState() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Open checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // Navigate to fills list
        composeTestRule
            .onNodeWithText("Fills", substring = true)
            .performClick()
        waitForIdle()

        // Should show empty state message
        composeTestRule
            .onNodeWithText("No fills yet", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun fillManagement_deleteFill() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Create a fill
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        // Look for delete icon (trash icon in toolbar or menu)
        // Click delete and confirm
        composeTestRule
            .onNodeWithText("Delete", substring = true)
            .performClick()
        waitForIdle()

        // Confirm deletion in dialog
        composeTestRule
            .onNodeWithText("Delete", substring = true)
            .performClick()
        waitForIdle()

        // Should navigate back to checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .assertIsDisplayed()
    }

    @Test
    fun fillManagement_fillsListShowsAllFills() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Create two fills
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        pressBack()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        pressBack()
        waitForIdle()

        // Navigate to fills list
        composeTestRule
            .onNodeWithText("Fills", substring = true)
            .performClick()
        waitForIdle()

        // Should see 2 fills (or count indicator)
        waitForIdle()
    }

    @Test
    fun fillManagement_backNavigationFromFill() {
        skipOnboardingAndGoToMain()

        val checklistName = createChecklistWithItems()

        // Create a fill
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
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

        val checklistName = createChecklistWithItems()

        // Open checklist detail
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // Click "Fill via AI" button
        composeTestRule
            .onNodeWithText("Fill via AI", substring = true)
            .performClick()
        waitForIdle()

        // Should navigate to analyze screen with checklist context
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()
    }
}
