package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for the CSAT (Customer Satisfaction) survey flow.
 *
 * CSAT shows after >= 2 "meaningful actions" (creating checklist, creating fill).
 * Tests cover:
 * 1. CSAT bottom sheet appears after 2nd checklist creation
 * 2. Feedback chips + text form for negative/neutral ratings (😞/😐)
 * 3. Feedback chips → Submit → ThankYou + review prompt for positive rating (😊)
 * 4. Chip multi-select toggle behavior
 * 5. Switching rating resets chips
 * 6. Dismiss behavior
 *
 * NOTE: Tests clear app data between runs (via Test Orchestrator), so each test
 * starts fresh with csat_action_count = 0.
 */
class CsatFlowTest : BaseUiTest() {

    /**
     * Helper to trigger CSAT by creating 2 checklists.
     * CSAT appears after 2nd action with a 5-second delay.
     */
    private fun triggerCsatBySaving2Checklists() {
        // First checklist — increments counter to 1
        createChecklistWithItems("First Checklist", "Item 1")
        assertOnMainScreen()
        waitForIdle()

        // Second checklist — increments counter to 2, triggers CSAT
        createChecklistWithItems("Second Checklist", "Item 2")
        assertOnMainScreen()
    }

    /**
     * Wait for CSAT bottom sheet to appear.
     * CSAT has a 5-second delay after triggering, so we wait up to 8 seconds.
     */
    private fun waitForCsatToAppear(timeoutMillis: Long = 8000) {
        waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText("How do you like Gisti?")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Wait for CSAT bottom sheet to disappear.
     */
    private fun waitForCsatToDismiss(timeoutMillis: Long = 5000) {
        waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText("How do you like Gisti?")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    @Smoke
    fun csat_appearsAfterSecondChecklistCreation() {
        skipOnboardingAndGoToMain()

        // When: Create 2 checklists (triggers CSAT)
        triggerCsatBySaving2Checklists()

        // Then: CSAT bottom sheet appears
        waitForCsatToAppear()

        composeTestRule
            .onNodeWithText("How do you like Gisti?")
            .assertIsDisplayed()

        // And: All 3 emojis are visible
        composeTestRule
            .onNodeWithText("Not Good")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("It's Okay")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Love It!")
            .assertIsDisplayed()
    }

    @Test
    fun csat_negativeRatingShowsChipsAndFeedbackForm() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Tap "Not Good" emoji
        composeTestRule
            .onNodeWithText("Not Good")
            .performClick()

        waitForIdle()

        // Then: "Not Good" chips section appears with problem-focused title
        composeTestRule
            .onNodeWithText("What's the biggest problem?")
            .assertIsDisplayed()

        // And: Not Good chips are visible (problems)
        composeTestRule
            .onNodeWithText("Buggy")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Slow")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Too Expensive")
            .assertIsDisplayed()

        // And: Feedback text field and submit button are shown
        composeTestRule
            .onNodeWithText("Tell us more (optional)...")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Submit")
            .assertIsDisplayed()
    }

    @Test
    fun csat_neutralRatingShowsChipsAndFeedbackForm() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Tap "It's Okay" emoji
        composeTestRule
            .onNodeWithText("It's Okay")
            .performClick()

        waitForIdle()

        // Then: "Okay" chips section appears with improvement-focused title
        composeTestRule
            .onNodeWithText("What could be better?")
            .assertIsDisplayed()

        // And: Okay chips are visible (improvements, different from Not Good)
        composeTestRule
            .onNodeWithText("More Features")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Faster")
            .assertIsDisplayed()

        // And: Feedback form appears
        composeTestRule
            .onNodeWithText("Tell us more (optional)...")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Submit")
            .assertIsDisplayed()
    }

    @Test
    fun csat_positiveRatingShowsChipsThenThankYou() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Tap "Love It!" emoji
        composeTestRule
            .onNodeWithText("Love It!")
            .performClick()

        waitForIdle()

        // Then: Positive chips section appears (not ThankYou yet)
        composeTestRule
            .onNodeWithText("What do you like most?")
            .assertIsDisplayed()

        // And: Positive chips are visible
        composeTestRule
            .onNodeWithText("AI Quality")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Easy to Use")
            .assertIsDisplayed()

        // And: Submit button is shown
        composeTestRule
            .onNodeWithText("Submit")
            .assertIsDisplayed()

        // When: Tap Submit
        composeTestRule
            .onNodeWithText("Submit")
            .performClick()

        waitForIdle()

        // Then: Thank you message appears
        composeTestRule
            .onNodeWithText("Thank you for your feedback!")
            .assertIsDisplayed()

        // And: Review prompt is shown
        composeTestRule
            .onNodeWithText("Rate on Google Play")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Maybe later")
            .assertIsDisplayed()
    }

    @Test
    fun csat_submitFeedbackCloseSheet() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // Select negative rating
        composeTestRule
            .onNodeWithText("Not Good")
            .performClick()

        waitForIdle()

        // Enter optional feedback
        composeTestRule
            .onNode(hasText("Tell us more (optional)..."))
            .performTextInput("The app could be better")

        waitForIdle()

        // When: Submit
        composeTestRule
            .onNodeWithText("Submit")
            .performClick()

        // Then: Sheet is dismissed
        waitForCsatToDismiss()

        // And: We're back on main screen
        assertOnMainScreen()
    }

    @Test
    fun csat_maybeLaterClosesSheet() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // Select positive rating
        composeTestRule
            .onNodeWithText("Love It!")
            .performClick()

        waitForIdle()

        // Submit to proceed to ThankYou screen
        composeTestRule
            .onNodeWithText("Submit")
            .performClick()

        waitForIdle()

        // When: Tap "Maybe later"
        composeTestRule
            .onNodeWithText("Maybe later")
            .performClick()

        // Then: Sheet is dismissed
        waitForCsatToDismiss()

        // And: We're back on main screen
        assertOnMainScreen()
    }

    @Test
    fun csat_dismissWithoutSelectionClosesSheet() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Press back to dismiss (without selecting an emoji)
        pressBack()

        // Then: Sheet is dismissed
        waitForCsatToDismiss()

        // And: We're back on main screen
        assertOnMainScreen()
    }

    @Test
    fun csat_chipSelectionToggles() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // Select negative rating to show chips
        composeTestRule
            .onNodeWithText("Not Good")
            .performClick()

        waitForIdle()

        // When: Tap "Buggy" chip to select it
        composeTestRule
            .onNodeWithText("Buggy")
            .performClick()

        waitForIdle()

        // And: Tap "Slow" chip to select it
        composeTestRule
            .onNodeWithText("Slow")
            .performClick()

        waitForIdle()

        // Then: Both chips are still displayed (multi-select)
        composeTestRule
            .onNodeWithText("Buggy")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Slow")
            .assertIsDisplayed()

        // When: Submit with selected chips
        composeTestRule
            .onNodeWithText("Submit")
            .performClick()

        // Then: Sheet is dismissed
        waitForCsatToDismiss()
        assertOnMainScreen()
    }

    @Test
    fun csat_switchingRatingResetsChips() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // Select negative rating and see Not Good chips
        composeTestRule
            .onNodeWithText("Not Good")
            .performClick()

        waitForIdle()

        composeTestRule
            .onNodeWithText("What's the biggest problem?")
            .assertIsDisplayed()

        // When: Switch to positive rating
        composeTestRule
            .onNodeWithText("Love It!")
            .performClick()

        waitForIdle()

        // Then: Positive chips section appears (negative replaced)
        composeTestRule
            .onNodeWithText("What do you like most?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("AI Quality")
            .assertIsDisplayed()
    }

    @Test
    fun csat_doesNotAppearAfterSingleAction() {
        skipOnboardingAndGoToMain()

        // When: Create only 1 checklist (below MIN_ACTIONS threshold)
        createChecklistWithItems("Single Checklist", "Item 1")
        assertOnMainScreen()

        // Then: CSAT does NOT appear (wait a bit to make sure)
        // Wait for potential delay (5s CSAT delay + buffer)
        Thread.sleep(6000)
        waitForIdle()

        // CSAT should not be visible
        composeTestRule
            .onAllNodesWithText("How do you like Gisti?")
            .fetchSemanticsNodes()
            .isEmpty()
            .let { isEmpty ->
                assert(isEmpty) { "CSAT should not appear after single action" }
            }
    }
}
