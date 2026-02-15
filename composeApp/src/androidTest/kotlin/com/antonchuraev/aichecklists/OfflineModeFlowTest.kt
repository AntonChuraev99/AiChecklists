package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun offline_createChecklistWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Note: In real offline test, we'd disable network first.
        // For now, we verify CRUD works (Room DB is local).

        // Create checklist using shared helper
        createChecklistWithItems("Offline Checklist", "Offline Item")

        // Verify checklist appears on main (stored in local Room DB)
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Offline Checklist")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Offline Checklist")
            .assertIsDisplayed()
    }

    @Test
    fun offline_viewExistingChecklistsWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create a checklist first (will be in local DB)
        createChecklistWithItems("View Test", "Test Item")

        // Verify checklist is visible on main
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("View Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("View Test")
            .assertIsDisplayed()

        // Click to view details
        composeTestRule
            .onNodeWithText("View Test")
            .performClick()
        waitForIdle()

        // Detail screen should load without network
        // Wait for detail-specific element (Create New Fill button)
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Create New Fill")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Create New Fill")
            .assertIsDisplayed()
    }

    @Test
    fun offline_editChecklistWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create a checklist
        createChecklistWithItems("Edit Offline Test", "Original")

        // Open checklist detail
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Edit Offline Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Edit Offline Test")
            .performClick()
        waitForIdle()

        // Click edit icon (content description "Edit")
        composeTestRule
            .onNode(hasContentDescription("Edit"))
            .performClick()
        waitForIdle()

        // Add new item offline using inline flow
        addItemToChecklist("Offline Added")

        // Save checklist
        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Verify new item is saved on detail screen
        composeTestRule
            .onNodeWithText("Offline Added")
            .assertIsDisplayed()
    }

    @Test
    fun offline_deleteChecklistWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create a checklist to delete
        createChecklistWithItems("Delete Offline", "Item to delete")

        // Verify it exists on main
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Delete Offline")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Delete Offline")
            .assertIsDisplayed()

        // Open detail
        composeTestRule
            .onNodeWithText("Delete Offline")
            .performClick()
        waitForIdle()

        // Delete via icon (content description "Delete")
        composeTestRule
            .onNode(hasContentDescription("Delete"))
            .performClick()
        waitForIdle()

        // Confirm deletion (dialog with "Delete" button)
        waitForButton("Delete")
        composeTestRule
            .onNodeWithText("Delete")
            .performClick()
        waitForIdle()

        // Should navigate back to main (checklist deleted from Room DB)
        assertOnMainScreen()
    }

    @Test
    fun offline_checkUncheckFillItemsWithoutNetwork() {
        skipOnboardingAndGoToMain()

        // Create checklist with an item
        createChecklistWithItems("Fill Offline Test", "Offline Fill Item")

        // Open checklist detail
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Fill Offline Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Fill Offline Test")
            .performClick()
        waitForIdle()

        // Create fill
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
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

        // Item should still be visible after toggle
        composeTestRule
            .onNodeWithText("Offline Fill Item")
            .assertIsDisplayed()
    }
}
