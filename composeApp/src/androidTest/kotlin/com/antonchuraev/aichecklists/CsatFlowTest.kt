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
 * 2. Emoji selection changes UI state
 * 3. Feedback form for negative/neutral ratings (😞/😐)
 * 4. Thank you + review prompt for positive rating (😊)
 * 5. Dismiss behavior
 *
 * NOTE: Tests clear app data between runs (via Test Orchestrator), so each test
 * starts fresh with csat_action_count = 0.
 */
class CsatFlowTest : BaseUiTest() {

    /**
     * Helper to trigger CSAT by creating 2 checklists.
     * CSAT appears after 2nd action with a 3-second delay.
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
     * CSAT has a 3-second delay after triggering, so we wait up to 5 seconds.
     */
    private fun waitForCsatToAppear(timeoutMillis: Long = 6000) {
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
    fun csat_negativeRatingShowsFeedbackForm() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Tap "Not Good" emoji
        composeTestRule
            .onNodeWithText("Not Good")
            .performClick()

        waitForIdle()

        // Then: Feedback form appears
        composeTestRule
            .onNodeWithText("Tell us more (optional)...")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Submit")
            .assertIsDisplayed()
    }

    @Test
    fun csat_neutralRatingShowsFeedbackForm() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Tap "It's Okay" emoji
        composeTestRule
            .onNodeWithText("It's Okay")
            .performClick()

        waitForIdle()

        // Then: Feedback form appears
        composeTestRule
            .onNodeWithText("Tell us more (optional)...")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Submit")
            .assertIsDisplayed()
    }

    @Test
    fun csat_positiveRatingShowsThankYouAndReviewPrompt() {
        skipOnboardingAndGoToMain()
        triggerCsatBySaving2Checklists()
        waitForCsatToAppear()

        // When: Tap "Love It!" emoji
        composeTestRule
            .onNodeWithText("Love It!")
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
    fun csat_doesNotAppearAfterSingleAction() {
        skipOnboardingAndGoToMain()

        // When: Create only 1 checklist (below MIN_ACTIONS threshold)
        createChecklistWithItems("Single Checklist", "Item 1")
        assertOnMainScreen()

        // Then: CSAT does NOT appear (wait a bit to make sure)
        // Wait for potential delay (3s CSAT delay + buffer)
        Thread.sleep(4000)
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
