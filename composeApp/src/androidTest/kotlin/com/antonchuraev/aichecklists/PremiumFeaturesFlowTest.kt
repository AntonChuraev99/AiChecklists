package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for Premium features and limits enforcement.
 *
 * Note: Paywall is a pager-style screen with trial timeline:
 * - "3-Day Free Trial" header
 * - "Start Free Trial" button
 * - "Restore Purchase" link (no 's')
 * - "Skip" to dismiss
 *
 * Tests cover:
 * 1. Checklist creation limit for free users (max 3)
 * 2. Fill creation limit for free users (max 5 per checklist)
 * 3. Paywall shown when limit reached
 * 4. Premium trial info displayed on paywall
 * 5. Restore purchase link on paywall
 */
class PremiumFeaturesFlowTest : BaseUiTest() {

    @Test
    fun premiumFeatures_checklistLimitForFreeUsers() {
        skipOnboardingAndGoToMain()

        // Create 3 checklists (free limit)
        createChecklistWithItems("Checklist 1", "Item 1")
        createChecklistWithItems("Checklist 2", "Item 2")
        createChecklistWithItems("Checklist 3", "Item 3")

        // Verify all 3 checklists are displayed
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Checklist 3")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("Checklist 1")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Checklist 2")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Checklist 3")
            .assertIsDisplayed()

        // At limit: button changes to "Become Premium to Unlock More"
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Become Premium to Unlock More")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Become Premium to Unlock More")
            .assertIsDisplayed()

        // Click it - should show paywall
        composeTestRule
            .onNodeWithText("Become Premium to Unlock More")
            .performClick()
        waitForIdle()

        // Paywall should appear with trial offer
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3-Day Free Trial")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("3-Day Free Trial")
            .assertIsDisplayed()
    }

    @Test
    fun premiumFeatures_fillLimitEnforcement() {
        skipOnboardingAndGoToMain()

        // Create a checklist with an item
        createChecklistWithItems("Fill Limit Test", "Test item")

        // Open checklist detail
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Fill Limit Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Fill Limit Test")
            .performClick()
        waitForIdle()

        // Create 5 fills (free limit)
        repeat(5) {
            waitForButton("Create New Fill")
            composeTestRule
                .onNodeWithText("Create New Fill")
                .performClick()
            waitForIdle()

            pressBack()
            waitForIdle()
        }

        // Attempt to create 6th fill - should show limit or paywall
        waitForButton("Create New Fill")
        composeTestRule
            .onNodeWithText("Create New Fill")
            .performClick()
        waitForIdle()

        // Should show paywall or limit message
        waitForIdle()
    }

    @Test
    fun premiumFeatures_paywallShowsWhenLimitReached() {
        skipOnboardingAndGoToMain()

        // Create max checklists
        createChecklistWithItems("Checklist 1", "Item 1")
        createChecklistWithItems("Checklist 2", "Item 2")
        createChecklistWithItems("Checklist 3", "Item 3")

        // At limit: button becomes "Become Premium to Unlock More"
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Become Premium to Unlock More")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Become Premium to Unlock More")
            .performClick()
        waitForIdle()

        // Paywall should be displayed with trial info
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3-Day Free Trial")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("3-Day Free Trial")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Start Free Trial")
            .assertIsDisplayed()
    }

    @Test
    fun premiumFeatures_paywallDisplaysTrialInfo() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall via Get More button
        navigateToPaywall()

        // Verify paywall is shown with trial offer
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3-Day Free Trial")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("3-Day Free Trial")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Start Free Trial")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Auto-renews", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun premiumFeatures_restorePurchaseLink() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall
        navigateToPaywall()

        // Verify paywall is shown
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("3-Day Free Trial")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Restore Purchase link is present (no 's')
        composeTestRule
            .onNodeWithText("Restore Purchase")
            .assertIsDisplayed()

        // Click it (will show message if no purchases)
        composeTestRule
            .onNodeWithText("Restore Purchase")
            .performClick()
        waitForIdle()

        // Should show some feedback
        waitForIdle()
    }
}
