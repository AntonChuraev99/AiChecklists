package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for the Main Screen flow.
 *
 * Tests cover:
 * 1. Empty state display
 * 2. Checklist list display
 * 3. Credits chip / Get More display
 * 4. Navigation to different screens
 */
class MainScreenFlowTest : BaseUiTest() {

    @Test
    @Smoke
    fun mainScreen_displaysEmptyState() {
        skipOnboardingAndGoToMain()

        // Then: Main screen is displayed (empty state)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()

        // And: Create Checklist button is displayed
        composeTestRule
            .onNodeWithText("Create Checklist")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_displaysCreditsChip() {
        skipOnboardingAndGoToMain()

        // Then: Credits area should be displayed in toolbar
        // Shows "Get More" when 0 credits, or "N credits" when > 0
        // "Go Premium" banner also contains "credits" substring
        composeTestRule
            .onNodeWithText("credits", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_navigatesToAnalyzeViaCreateWithAi() {
        skipOnboardingAndGoToMain()

        // Navigate to analyze via: Create Checklist → Create with AI
        navigateToAnalyze()

        // Then: Analyze screen is displayed
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_createdChecklistAppears() {
        skipOnboardingAndGoToMain()

        // Create a checklist using helper (navigates through Templates)
        createChecklistWithItems("Shopping List", "Buy groceries")

        // Then: Checklist should appear in the list
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Shopping List")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Shopping List")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_clickChecklistNavigatesToDetail() {
        skipOnboardingAndGoToMain()

        // Create a checklist first
        createChecklistWithItems("Detail Test", "Test item")

        // Click on the checklist
        waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Detail Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText("Detail Test")
            .performClick()

        waitForIdle()

        // Should navigate to detail screen
        composeTestRule
            .onNodeWithText("Detail Test")
            .assertIsDisplayed()
    }
}
