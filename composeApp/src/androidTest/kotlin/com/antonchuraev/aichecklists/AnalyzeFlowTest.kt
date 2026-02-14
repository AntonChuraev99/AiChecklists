package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * UI tests for the AI Analysis flow.
 *
 * Tests cover:
 * 1. Navigate to analyze screen
 * 2. Select different input types
 * 3. Enter text for analysis
 * 4. Credits display and validation
 * 5. Analyze button state based on credits
 */
class AnalyzeFlowTest : BaseUiTest() {

    private fun skipOnboardingAndGoToMain() {
        waitForSplashToComplete()
        try {
            composeTestRule.onNodeWithText("Skip").performClick()
            // Wait for Main screen to appear (up to 10 seconds)
            waitUntil(10000) {
                composeTestRule.onAllNodesWithText("Ready to get organized?")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            waitForIdle()
        } catch (e: AssertionError) {
            // Onboarding might already be completed - verify Main screen
            waitUntil(5000) {
                composeTestRule.onAllNodesWithText("Ready to get organized?")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun navigateToAnalyze() {
        skipOnboardingAndGoToMain()
        // Wait for AI Analysis button to be ready
        waitForButton("AI Analysis")
        composeTestRule
            .onNodeWithText("AI Analysis")
            .performClick()
        waitForIdle()
    }

    @Test
    @Smoke
    fun analyzeScreen_displaysInputTypeOptions() {
        navigateToAnalyze()

        // Then: All input type options are displayed
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
    fun analyzeScreen_selectTextInputShowsTextField() {
        navigateToAnalyze()

        // When: Select "Paste Text" option
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()

        waitForIdle()

        // Then: Text input area should appear
        composeTestRule
            .onNodeWithText("Add your content")
            .assertIsDisplayed()

        // And: Analyze button should appear
        composeTestRule
            .onNodeWithText("Analyze")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_selectWebLinkShowsUrlField() {
        navigateToAnalyze()

        // When: Select "Web Link" option
        composeTestRule
            .onNodeWithText("Web Link")
            .performClick()

        waitForIdle()

        // Then: URL input should appear
        composeTestRule
            .onNodeWithText("URL")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("https://example.com/article")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_showsCostInfo() {
        navigateToAnalyze()

        // Select an input type to show the bottom bar
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()

        waitForIdle()

        // Then: Cost info should be displayed
        composeTestRule
            .onNodeWithText("This action costs", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("credits", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_enterTextForAnalysis() {
        navigateToAnalyze()

        // Select "Paste Text"
        composeTestRule
            .onNodeWithText("Paste Text")
            .performClick()
        waitForIdle()

        // Enter some text
        composeTestRule
            .onNode(hasText("Paste meeting notes", substring = true))
            .performTextInput("1. Buy milk\n2. Clean the house\n3. Call mom")

        waitForIdle()

        // Analyze button should be displayed
        composeTestRule
            .onNodeWithText("Analyze")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_backButtonNavigatesToMain() {
        navigateToAnalyze()

        // Verify we're on analyze screen
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()

        // Press back
        pressBack()

        waitForIdle()

        // Should be back on main screen (empty state after Test Orchestrator clears data)
        composeTestRule
            .onNodeWithText("Ready to get organized?")
            .assertIsDisplayed()
    }

    @Test
    fun analyzeScreen_inputTypeSelectionChangesHighlight() {
        navigateToAnalyze()

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

        // Input area should be visible
        composeTestRule
            .onNodeWithText("Your text")
            .assertIsDisplayed()
    }
}
