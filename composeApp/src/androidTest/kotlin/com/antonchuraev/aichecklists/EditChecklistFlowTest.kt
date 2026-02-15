package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for editing existing checklists.
 *
 * Tests cover:
 * 1. Navigate to edit from detail (via Edit icon)
 * 2. Add new items to existing checklist
 * 3. Save changes and verify updates
 */
class EditChecklistFlowTest : BaseUiTest() {

    private fun createAndOpenForEdit(name: String, vararg items: String) {
        createChecklistWithItems(name, *items)

        // Open checklist detail
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(name)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(name)
            .performClick()
        waitForIdle()

        // Click edit icon (content description "Edit")
        composeTestRule
            .onNode(hasContentDescription("Edit"))
            .performClick()
        waitForIdle()
    }

    @Test
    fun editChecklist_navigateToEditScreen() {
        skipOnboardingAndGoToMain()

        createChecklistWithItems("Edit Nav Test", "Item 1")

        // Open checklist detail
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Edit Nav Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Edit Nav Test")
            .performClick()
        waitForIdle()

        // Click edit icon
        composeTestRule
            .onNode(hasContentDescription("Edit"))
            .performClick()
        waitForIdle()

        // Should be on edit screen with existing name
        composeTestRule
            .onNode(hasText("Edit Nav Test"))
            .assertExists()
    }

    @Test
    fun editChecklist_addNewItemsToExisting() {
        skipOnboardingAndGoToMain()

        createAndOpenForEdit("Add Items Test", "Original Item")

        // Add new item using inline flow
        addItemToChecklist("New Item")

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify new item is displayed on detail screen
        composeTestRule
            .onNodeWithText("New Item")
            .assertIsDisplayed()
    }

    @Test
    fun editChecklist_saveChangesAndVerify() {
        skipOnboardingAndGoToMain()

        createAndOpenForEdit("Save Changes Test", "Original Item")

        // Add a new item
        addItemToChecklist("Verification Item")

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Navigate back to main and reopen
        pressBack()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save Changes Test")
            .performClick()
        waitForIdle()

        // New item should still be there (persisted)
        composeTestRule
            .onNodeWithText("Verification Item")
            .assertIsDisplayed()
    }
}
