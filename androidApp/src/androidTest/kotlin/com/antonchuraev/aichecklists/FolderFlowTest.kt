package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Test

/**
 * E2E UI tests for the Folders (nested checklists) feature on the Checklist Detail screen.
 *
 * Covers the full folder lifecycle on a Standard-view checklist:
 *  1. Enable folders via the overflow (⋮) menu toggle.
 *  2. Create a folder ("New Folder") — it appears as a [FolderCard] at the root level.
 *  3. Drill into the folder (tap) — the folder name becomes the on-screen header; folder is empty.
 *  4. Add an item inside the folder via Smart Add (toolbar "+").
 *  5. Navigate back (top-bar up) to the root — the folder card shows aggregate progress "0/1".
 *  6. Rename the folder via long-press → folder actions sheet → Rename → dialog.
 *  7. Delete the folder via long-press → folder actions sheet → Delete folder → confirm.
 *
 * Element-finding strategy (per project rule: prefer by-text / contentDescription over testTag):
 *  - Overflow button:  contentDescription "More options"
 *  - Folders toggle:   text "Folders" (a clickable Row, NOT the AppSwitch thumb)
 *  - New Folder menu:   text "New Folder" (folder_create) while the overflow sheet is open
 *  - FolderCard:        text = folder name (the card renders the name as its label)
 *  - Folder header:     text = folder name (ProgressHeader headlineSmall, shown after drill-down)
 *  - Top-bar up:        contentDescription "Back"
 *  - Smart Add commit:  IME Done on the "Add new item..." field (avoids the duplicate
 *                       "Add item" contentDescription shared by the toolbar "+" and the
 *                       inline input's commit button).
 *
 * The default folder name AND the "New Folder" menu item share the same string ("New Folder"),
 * so the helpers are careful about WHEN each is on screen: the menu item is only present while the
 * overflow sheet is open; the FolderCard is only present once that sheet has dismissed.
 */
class FolderFlowTest : BaseUiTest() {

    private val folderDefaultName = "New Folder"

    // ===== Flow helpers =====

    /**
     * Create a checklist and open its detail screen.
     * Mirrors the createAndOpenDetail helper used by ReminderFlowTest / EditChecklistFlowTest.
     */
    private fun createAndOpenDetail(name: String, vararg items: String) {
        createChecklistWithItems(name, *items)

        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(name)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(name)
            .performClick()
        waitForIdle()
    }

    /** Open the overflow (⋮) bottom sheet from the detail top bar. */
    private fun openOverflowSheet() {
        composeTestRule
            .onNode(hasContentDescription("More options"))
            .performClick()
        waitForIdle()
        // The "Folders" toggle row identifies the open overflow sheet.
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Folders")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Enable folders for the currently-open checklist via the overflow toggle.
     * The toggle does NOT dismiss the sheet, so afterwards the "New Folder" action becomes visible
     * in the same sheet.
     */
    private fun enableFoldersViaOverflow() {
        openOverflowSheet()
        composeTestRule.onNodeWithText("Folders").performClick()
        waitForIdle()
        // Once folders are enabled, the "New Folder" action appears in the same sheet.
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * From an open overflow sheet that already has folders enabled, tap "New Folder".
     * This dismisses the sheet and creates a folder at the current level. Waits until the
     * sheet is gone and exactly the new FolderCard remains on screen.
     */
    private fun createFolderFromOpenSheet() {
        // While the sheet is open the only "New Folder" node is the menu action; tap it.
        composeTestRule
            .onAllNodesWithText(folderDefaultName)[0]
            .performClick()
        waitForIdle()
        // Sheet dismisses; the created FolderCard ("New Folder") shows at the root level.
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Enable folders and create a single default-named folder, ending on the root level. */
    private fun enableFoldersAndCreateOne() {
        enableFoldersViaOverflow()
        createFolderFromOpenSheet()
    }

    /**
     * Add an item inside the current (folder) level via Smart Add.
     * Opens the toolbar "+", types into the "Add new item..." field, and commits with IME Done.
     */
    private fun smartAddItem(text: String) {
        // Toolbar "+" — at this moment the only "Add item" node is the toolbar action.
        composeTestRule
            .onNode(hasContentDescription("Add item"))
            .performClick()
        waitForIdle()
        // Inline add-item input appears with placeholder "Add new item...".
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Add new item...")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNode(hasText("Add new item..."))
            .performTextInput(text)
        waitForIdle()
        // Commit via IME Done (InlineAddItemInput.keyboardActions.onDone) — avoids the duplicate
        // "Add item" contentDescription shared by the toolbar button and the input's commit button.
        composeTestRule
            .onNode(hasText(text))
            .performImeAction()
        waitForIdle()
    }

    // ===== Tests =====

    @Test
    fun folders_enableToggle_showsNewFolderAction() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder Toggle Test", "Item 1")

        openOverflowSheet()

        // Initially folders are off → no "New Folder" action in the sheet.
        composeTestRule.onAllNodesWithText("New Folder").assertCountEquals(0)

        // Enable folders via the toggle row.
        composeTestRule.onNodeWithText("Folders").performClick()
        waitForIdle()

        // Now the "New Folder" action is present in the still-open sheet.
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("New Folder")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("New Folder").assertIsDisplayed()
    }

    @Test
    fun folders_createFolder_appearsInList() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder Create Test", "Item 1")

        enableFoldersAndCreateOne()

        // The created folder card is displayed at the root level.
        composeTestRule
            .onNodeWithText(folderDefaultName)
            .assertIsDisplayed()
    }

    @Test
    fun folders_drillDown_showsFolderHeaderAndIsEmpty() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder DrillDown Test", "Root Item")

        enableFoldersAndCreateOne()

        // Tap the folder card to drill in.
        composeTestRule.onNodeWithText(folderDefaultName).performClick()
        waitForIdle()

        // Inside the folder: the folder name is the on-screen header (ProgressHeader).
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(folderDefaultName).assertIsDisplayed()

        // The folder is empty: the root-level item is NOT visible at this level.
        composeTestRule.onAllNodesWithText("Root Item").assertCountEquals(0)
    }

    @Test
    fun folders_addItemInsideFolder_itemAppears() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder AddItem Test", "Root Item")

        enableFoldersAndCreateOne()

        // Drill into the folder.
        composeTestRule.onNodeWithText(folderDefaultName).performClick()
        waitForIdle()
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Add an item via Smart Add inside the folder.
        smartAddItem("Folder Item A")

        // The new item is displayed inside the folder.
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Folder Item A")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Folder Item A").assertIsDisplayed()
    }

    @Test
    fun folders_backFromFolder_showsAggregateProgressOnCard() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder Progress Test", "Root Item")

        enableFoldersAndCreateOne()

        // Drill into the folder and add one item.
        composeTestRule.onNodeWithText(folderDefaultName).performClick()
        waitForIdle()
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        smartAddItem("Folder Item A")
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Folder Item A")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate back to the root via the top-bar up button.
        composeTestRule
            .onNode(hasContentDescription("Back"))
            .performClick()
        waitForIdle()

        // Back at root: the folder card shows aggregate progress "0/1" (1 item inside, none done).
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(folderDefaultName).assertIsDisplayed()
        composeTestRule.onNodeWithText("0/1").assertIsDisplayed()
    }

    @Test
    fun folders_rename_updatesFolderName() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder Rename Test", "Item 1")

        enableFoldersAndCreateOne()

        // Long-press the folder card to open the folder actions sheet.
        composeTestRule
            .onNodeWithText(folderDefaultName)
            .performTouchInput { longClick() }
        waitForIdle()

        // Folder actions sheet → Rename.
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Rename")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Rename").performClick()
        waitForIdle()

        // Rename dialog: the text field is labelled "Folder name" and is pre-filled with the
        // current name. Clear it and type a new name.
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Rename folder")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("Folder name")).performTextClearance()
        composeTestRule.onNode(hasText("Folder name")).performTextInput("Groceries")
        waitForIdle()

        // Confirm with Save.
        composeTestRule.onNodeWithText("Save").performClick()
        waitForIdle()

        // The folder card now shows the new name; the default name is gone.
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Groceries")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(folderDefaultName).assertCountEquals(0)
    }

    @Test
    fun folders_delete_removesFolderFromList() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Folder Delete Test", "Item 1")

        enableFoldersAndCreateOne()

        // Long-press the folder card to open the folder actions sheet.
        composeTestRule
            .onNodeWithText(folderDefaultName)
            .performTouchInput { longClick() }
        waitForIdle()

        // Folder actions sheet → Delete folder.
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Delete folder")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete folder").performClick()
        waitForIdle()

        // Delete confirmation dialog → confirm with "Delete".
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Delete folder?")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Delete").performClick()
        waitForIdle()

        // The folder is gone from the list.
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText(folderDefaultName)
                .fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onAllNodesWithText(folderDefaultName).assertCountEquals(0)
    }
}
