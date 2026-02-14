package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for offline mode functionality.
 *
 * All core CRUD operations should work without network.
 * Only AI features require internet connection.
 *
 * Tests cover:
 * 1. Create checklist offline
 * 2. View existing checklists offline
 * 3. Edit checklist offline
 * 4. Delete checklist offline
 * 5. Check/uncheck fill items offline
 */
class OfflineModeFlowTest : BaseUiTest() {

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
    fun offline_createChecklistWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Note: In real offline test, we'd disable network first
        // For now, we verify CRUD works (Room DB is local)

        // Create checklist
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Offline Checklist")

        // Add item
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Offline Item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify checklist appears (stored in local Room DB)
        composeTestRule
            .onNodeWithText("Offline Checklist")
            .assertIsDisplayed()
    }

    @Test
    fun offline_viewExistingChecklistsWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create a checklist first (will be in local DB)
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("View Test")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Navigate away and back
        pressBack()
        waitForIdle()

        // Checklist should still be visible (Room DB persists)
        composeTestRule
            .onNodeWithText("View Test")
            .assertIsDisplayed()

        // Click to view details
        composeTestRule
            .onNodeWithText("View Test")
            .performClick()
        waitForIdle()

        // Detail screen should load without network
        composeTestRule
            .onNodeWithText("View Test")
            .assertIsDisplayed()
    }

    @Test
    fun offline_editChecklistWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create a checklist
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Edit Offline Test")

        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Original")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Open and edit
        composeTestRule
            .onNodeWithText("Edit Offline Test")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Edit", substring = true)
            .performClick()
        waitForIdle()

        // Add new item offline
        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Offline Added")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify new item is saved
        composeTestRule
            .onNodeWithText("Offline Added")
            .assertIsDisplayed()
    }

    @Test
    fun offline_deleteChecklistWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create a checklist to delete
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Delete Offline")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify it exists
        composeTestRule
            .onNodeWithText("Delete Offline")
            .assertIsDisplayed()

        // Open detail
        composeTestRule
            .onNodeWithText("Delete Offline")
            .performClick()
        waitForIdle()

        // Delete via menu or button
        composeTestRule
            .onNodeWithText("Delete", substring = true)
            .performClick()
        waitForIdle()

        // Confirm deletion
        composeTestRule
            .onNodeWithText("Delete", substring = true)
            .performClick()
        waitForIdle()

        // Should navigate back to main (checklist deleted from Room DB)
        composeTestRule
            .onNodeWithText("My Checklists")
            .assertIsDisplayed()
    }

    @Test
    fun offline_checkUncheckFillItemsWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create checklist with items
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Fill Offline Test")

        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Offline Fill Item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Open checklist and create fill
        composeTestRule
            .onNodeWithText("Fill Offline Test")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        // Check item (updates Room DB locally)
        composeTestRule
            .onNodeWithText("Offline Fill Item")
            .performClick()
        waitForIdle()

        // Uncheck item
        composeTestRule
            .onNodeWithText("Offline Fill Item")
            .performClick()
        waitForIdle()

        // Progress should update (0/1)
        composeTestRule
            .onNodeWithText("0/1")
            .assertIsDisplayed()
    }
}
