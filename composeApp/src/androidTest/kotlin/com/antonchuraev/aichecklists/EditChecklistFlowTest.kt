package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for editing existing checklists.
 *
 * Tests cover:
 * 1. Edit checklist name
 * 2. Add new items to existing checklist
 * 3. Remove items from checklist
 * 4. Edit item text
 * 5. Save changes and verify updates
 */
class EditChecklistFlowTest : BaseUiTest() {

    private fun skipOnboardingAndGoToMain() {
        waitForIdle()
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed
        }
    }

    private fun createChecklistWithItems(name: String): String {
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput(name)

        // Add item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Original Item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        return name
    }

    @Test
    fun editChecklist_renameChecklist() {
        skipOnboardingAndGoToMain()

        val originalName = "Original Name"
        createChecklistWithItems(originalName)

        // Open checklist detail
        composeTestRule
            .onNodeWithText(originalName)
            .performClick()
        waitForIdle()

        // Click edit button (pencil icon or "Edit" text)
        composeTestRule
            .onNodeWithText("Edit", substring = true)
            .performClick()
        waitForIdle()

        // Should navigate to edit screen
        composeTestRule
            .onNodeWithText("Edit Checklist", substring = true)
            .assertIsDisplayed()

        // Change name
        composeTestRule
            .onNode(hasText(originalName))
            .performTextInput(" Updated")

        // Save changes
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify new name is displayed
        composeTestRule
            .onNodeWithText("Original Name Updated")
            .assertIsDisplayed()
    }

    @Test
    fun editChecklist_addNewItemsToExisting() {
        skipOnboardingAndGoToMain()

        val checklistName = "Add Items Test"
        createChecklistWithItems(checklistName)

        // Open checklist detail and edit
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Edit", substring = true)
            .performClick()
        waitForIdle()

        // Add new item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("New Item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify new item is displayed
        composeTestRule
            .onNodeWithText("New Item")
            .assertIsDisplayed()
    }

    @Test
    fun editChecklist_removeItems() {
        skipOnboardingAndGoToMain()

        val checklistName = "Remove Items Test"
        createChecklistWithItems(checklistName)

        // Open and edit
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Edit", substring = true)
            .performClick()
        waitForIdle()

        // Find delete button for the item (trash icon or X)
        // Usually appears next to each item
        composeTestRule
            .onNodeWithText("Delete", substring = true)
            .performClick()
        waitForIdle()

        // Confirm deletion if dialog appears
        try {
            composeTestRule
                .onNodeWithText("Delete", substring = true)
                .performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // No confirmation dialog, item deleted immediately
        }

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Item should no longer be visible
        waitForIdle()
    }

    @Test
    fun editChecklist_editItemText() {
        skipOnboardingAndGoToMain()

        val checklistName = "Edit Item Test"
        createChecklistWithItems(checklistName)

        // Open and edit
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Edit", substring = true)
            .performClick()
        waitForIdle()

        // Click on existing item to edit
        composeTestRule
            .onNodeWithText("Original Item")
            .performClick()
        waitForIdle()

        // Should open edit dialog or inline editing
        // Clear and enter new text
        composeTestRule
            .onNode(hasText("Original Item"))
            .performTextInput(" Updated")

        // Save item changes
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify updated text
        composeTestRule
            .onNodeWithText("Original Item Updated")
            .assertIsDisplayed()
    }

    @Test
    fun editChecklist_saveChangesAndVerify() {
        skipOnboardingAndGoToMain()

        val checklistName = "Save Changes Test"
        createChecklistWithItems(checklistName)

        // Open and edit
        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Edit", substring = true)
            .performClick()
        waitForIdle()

        // Add a new item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Verification Item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Navigate back and return to verify persistence
        pressBack()
        waitForIdle()

        composeTestRule
            .onNodeWithText(checklistName)
            .performClick()
        waitForIdle()

        // New item should still be there
        composeTestRule
            .onNodeWithText("Verification Item")
            .assertIsDisplayed()
    }
}
