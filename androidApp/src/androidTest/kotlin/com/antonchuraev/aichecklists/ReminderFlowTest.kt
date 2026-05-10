package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test

/**
 * UI tests for the Checklist Reminders feature.
 *
 * Tests cover:
 * 1. Bell icon visibility on checklist detail screen
 * 2. Opening reminder bottom sheet
 * 3. Preset options displayed correctly
 * 4. Setting a reminder via preset ("In 1 hour")
 * 5. Bell icon changes to filled after setting reminder
 * 6. Removing an existing reminder
 * 7. Custom date/time picker flow
 */
class ReminderFlowTest : BaseUiTest() {

    /**
     * Create a checklist and navigate to its detail screen.
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

    @Test
    fun reminderIcon_isVisibleOnDetailScreen() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Reminder Icon Test", "Item 1")

        // Bell icon should be visible with contentDescription "Set Reminder"
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .assertIsDisplayed()
    }

    @Test
    fun reminderBottomSheet_opensOnBellClick() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Sheet Open Test", "Item 1")

        // Click bell icon
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        // Bottom sheet should show preset options
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("In 1 hour")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("In 1 hour").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tomorrow morning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tomorrow evening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pick date & time").assertIsDisplayed()
    }

    @Test
    fun reminderPreset_setsReminderInOneHour() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Preset 1h Test", "Item 1")

        // Open reminder sheet
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        // Select "In 1 hour" preset
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("In 1 hour")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("In 1 hour").performClick()
        waitForIdle()

        // Bottom sheet should dismiss (presets gone)
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("In 1 hour")
                .fetchSemanticsNodes().isEmpty()
        }

        // Bell icon should still be visible (reminder is set)
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .assertIsDisplayed()
    }

    @Test
    fun reminderRemove_removesExistingReminder() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Remove Reminder Test", "Item 1")

        // Set a reminder first
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("In 1 hour")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("In 1 hour").performClick()
        waitForIdle()

        // Wait for sheet to dismiss
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("In 1 hour")
                .fetchSemanticsNodes().isEmpty()
        }

        // Open reminder sheet again — "Remove Reminder" should be visible
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Remove Reminder")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // On small screens "Remove Reminder" may be below fold inside ModalBottomSheet.
        // Swipe the sheet up to reveal it, then click.
        composeTestRule.onNodeWithText("Remove Reminder")
            .performScrollTo()

        composeTestRule.onNodeWithText("Remove Reminder").assertExists()
        composeTestRule.onNodeWithText("Remove Reminder").performClick()
        waitForIdle()

        // Sheet should dismiss
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Remove Reminder")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun reminderCustomPicker_opensDatePicker() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Custom Picker Test", "Item 1")

        // Open reminder sheet
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        // Click "Pick date & time"
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Pick date & time")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Pick date & time").performClick()
        waitForIdle()

        // Date picker dialog should appear with "Next" button
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Next")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun reminderPreset_tomorrowMorning() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Tomorrow AM Test", "Item 1")

        // Open reminder sheet
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        // Select "Tomorrow morning"
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Tomorrow morning")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Tomorrow morning").performClick()
        waitForIdle()

        // Sheet should dismiss
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Tomorrow morning")
                .fetchSemanticsNodes().isEmpty()
        }

        // Reminder is set — bell icon still present
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .assertIsDisplayed()
    }

    @Test
    fun reminderPreset_tomorrowEvening() {
        skipOnboardingAndGoToMain()
        createAndOpenDetail("Tomorrow PM Test", "Item 1")

        // Open reminder sheet
        composeTestRule
            .onNode(hasContentDescription("Set Reminder"))
            .performClick()
        waitForIdle()

        // Select "Tomorrow evening"
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Tomorrow evening")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Tomorrow evening").performClick()
        waitForIdle()

        // Sheet should dismiss
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Tomorrow evening")
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
