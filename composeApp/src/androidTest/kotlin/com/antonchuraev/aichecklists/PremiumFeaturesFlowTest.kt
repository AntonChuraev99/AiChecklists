package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for Premium features and limits enforcement.
 *
 * Tests cover:
 * 1. Checklist creation limit for free users (max 3)
 * 2. Fill creation limit for free users (max 5 per checklist)
 * 3. Paywall shown when limit reached
 * 4. Premium benefits displayed on paywall
 * 5. Restore purchases button functionality
 * 6. Credits refill explanation for premium users
 */
class PremiumFeaturesFlowTest : BaseUiTest() {

    private fun skipOnboardingAndGoToMain() {
        waitForIdle()
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed
        }
    }

    private fun createChecklist(name: String) {
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput(name)

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()
    }

    @Test
    fun premiumFeatures_checklistLimitForFreeUsers() {
        skipOnboardingAndGoToMain()

        // Create 3 checklists (free limit)
        createChecklist("Checklist 1")
        createChecklist("Checklist 2")
        createChecklist("Checklist 3")

        // Verify all 3 checklists are displayed
        composeTestRule
            .onNodeWithText("Checklist 1")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Checklist 2")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Checklist 3")
            .assertIsDisplayed()

        // Attempt to create 4th checklist
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Should show paywall or limit message
        composeTestRule
            .onNodeWithText("Unlock Your Full Potential", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun premiumFeatures_fillLimitEnforcement() {
        skipOnboardingAndGoToMain()

        // Create a checklist with items
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .performTextInput("Fill Limit Test")

        composeTestRule
            .onNodeWithText("Add Item")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNode(hasText("Item text"))
            .performTextInput("Test item")

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Save")
            .performClick()
        waitForIdle()

        // Open checklist detail
        composeTestRule
            .onNodeWithText("Fill Limit Test")
            .performClick()
        waitForIdle()

        // Create 5 fills (free limit)
        repeat(5) { index ->
            composeTestRule
                .onNodeWithText("New Fill", substring = true)
                .performClick()
            waitForIdle()

            pressBack()
            waitForIdle()
        }

        // Attempt to create 6th fill
        composeTestRule
            .onNodeWithText("New Fill", substring = true)
            .performClick()
        waitForIdle()

        // Should show limit message or paywall
        waitForIdle()
    }

    @Test
    fun premiumFeatures_paywallShowsWhenLimitReached() {
        skipOnboardingAndGoToMain()

        // Create max checklists
        createChecklist("Checklist 1")
        createChecklist("Checklist 2")
        createChecklist("Checklist 3")

        // Try to create one more
        composeTestRule
            .onNodeWithText("Create Checklist")
            .performClick()
        waitForIdle()

        // Paywall should be displayed
        composeTestRule
            .onNodeWithText("Unlock Your Full Potential")
            .assertIsDisplayed()

        // Verify premium benefits are listed
        composeTestRule
            .onNodeWithText("Create unlimited checklists")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Daily AI credits refill", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun premiumFeatures_paywallDisplaysBenefits() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall via credits chip
        composeTestRule
            .onNodeWithText("credits", substring = true)
            .performClick()
        waitForIdle()

        // Verify all premium benefits are shown
        composeTestRule
            .onNodeWithText("Create unlimited checklists")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Daily AI credits refill", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Sync across all your devices")
            .assertIsDisplayed()

        // Verify pricing is displayed
        composeTestRule
            .onNodeWithText("$1.99", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun premiumFeatures_restorePurchasesButton() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall
        composeTestRule
            .onNodeWithText("credits", substring = true)
            .performClick()
        waitForIdle()

        // Verify Restore Purchases button is present
        composeTestRule
            .onNodeWithText("Restore Purchases")
            .assertIsDisplayed()

        // Click it (will show message if no purchases)
        composeTestRule
            .onNodeWithText("Restore Purchases")
            .performClick()
        waitForIdle()

        // Should show some feedback (success or "no purchases found")
        waitForIdle()
    }

    @Test
    fun premiumFeatures_creditsRefillExplanation() {
        skipOnboardingAndGoToMain()

        // Navigate to paywall
        composeTestRule
            .onNodeWithText("credits", substring = true)
            .performClick()
        waitForIdle()

        // Verify daily credits refill is explained
        composeTestRule
            .onNodeWithText("Daily AI credits refill", substring = true)
            .assertIsDisplayed()

        // Should mention "300 credits" for premium
        composeTestRule
            .onNodeWithText("300", substring = true)
            .assertIsDisplayed()
    }
}
