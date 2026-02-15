package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for the AI Analysis flow.
 *
 * Note: Test Orchestrator clears app data, so credits = 0.
 * With 0 credits, input fields (text area, URL field) do NOT appear.
 * Instead, "Not enough credits" message and disabled Analyze button show.
 *
 * Tests cover:
 * 1. Navigate to analyze screen and verify input types
 * 2. Select input types and verify cost info
 * 3. Verify credits warning with 0 credits
 * 4. Back navigation
 * 5. Input type selection changes
 */
class AnalyzeFlowTest : BaseUiTest() {

    private fun goToAnalyze() {
        skipOnboardingAndGoToMain()
        navigateToAnalyze()
    }

    @Test
    @Smoke
    fun analyzeScreen_displaysInputTypeOptions() {
        goToAnalyze()

        // All input type options are displayed
        composeTestRule
            .onNodeWithText("Photo")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("PDF")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Text File")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Web Link")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Paste Text")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_selectInputTypeShowsCostAndAnalyzeButton() {
        goToAnalyze()

        // Select "Paste Text" option
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // Cost info should appear
        composeTestRule
            .onNodeWithText("This action costs", substring = true)
            .assertIsDisplayed()

        // Analyze button should appear (disabled with 0 credits)
        composeTestRule
            .onNodeWithText("Analyze")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_selectWebLinkShowsCostInfo() {
        goToAnalyze()

        // Select "Web Link" option
        composeTestRule
            .onNodeWithText("Web Link")
            .performClick()
        waitForIdle()

        // Cost info should appear
        composeTestRule
            .onNodeWithText("This action costs", substring = true)
            .assertIsDisplayed()

        // Analyze button should appear
        composeTestRule
            .onNodeWithText("Analyze")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_showsNotEnoughCreditsWarning() {
        goToAnalyze()

        // Select an input type to show the bottom bar
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // With 0 credits, "Not enough credits" warning should appear
        composeTestRule
            .onNodeWithText("Not enough credits", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_showsCostInfoWithCreditsAmount() {
        goToAnalyze()

        // Select an input type
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // Cost info should be displayed (e.g., "This action costs 30 credits")
        composeTestRule
            .onNodeWithText("This action costs", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_backNavigatesToTemplates() {
        goToAnalyze()

        // Verify we're on analyze screen
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()

        // Press back - goes from Analyze to Templates
        pressBack()
        waitForIdle()

        // Should be on Templates screen
        waitUntil(3000) {
            composeTestRule.onAllNodesWithText("Create Manually")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun analyzeScreen_inputTypeSelectionChanges() {
        goToAnalyze()

        // Select "Photo"
        composeTestRule
            .onNodeWithText("Photo")
            .performClick()
        waitForIdle()

        // Then select "PDF"
        composeTestRule
            .onNodeWithText("PDF")
            .performClick()
        waitForIdle()

        // Then select "Paste Text"
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // Analyze button should be visible after selection
        composeTestRule
            .onNodeWithText("Analyze")
            .assertIsDisplayed()
    }
}
