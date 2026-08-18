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

        // All SIX materials are offered, each by its own visible label. The labels are the
        // affordance — a generic "Analyze" door recorded zero photo/pdf/voice analyses in 30 days —
        // so the whole set is asserted, not a sample of it. Voice was missing here before and the
        // other three were the LONG forms the compact picker replaced.
        listOf(
            ANALYZE_SOURCE_PHOTO,
            ANALYZE_SOURCE_PDF,
            ANALYZE_SOURCE_FILE,
            ANALYZE_SOURCE_LINK,
            ANALYZE_SOURCE_TEXT,
            ANALYZE_SOURCE_VOICE,
        ).forEach { label ->
            composeTestRule
                .onNodeWithText(label)
                .assertIsDisplayed()
        }
    }

    @Test
    fun analyzeScreen_selectInputTypeShowsCostAndAnalyzeButton() {
        goToAnalyze()

        // Select the paste-text material
        composeTestRule
            .onNodeWithText(ANALYZE_SOURCE_TEXT)
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

        // Select the web-link material
        composeTestRule
            .onNodeWithText(ANALYZE_SOURCE_LINK)
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
            .onNodeWithText(ANALYZE_SOURCE_TEXT)
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
            .onNodeWithText(ANALYZE_SOURCE_TEXT)
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

        // Verify we're on analyze screen — its source heading, shown while nothing is chosen.
        composeTestRule
            .onNodeWithText(ANALYZE_SOURCE_HEADING)
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

        // Choosing a material COLLAPSES the grid to a single current-material pill, so each further
        // switch costs a tap on that pill first. That is the price the owner accepted for landing a
        // door straight on its editor; the test walks the real path rather than assuming the grid
        // stays open.
        composeTestRule
            .onNodeWithText(ANALYZE_SOURCE_PHOTO)
            .performClick()
        waitForIdle()

        reopenAnalyzeSourcePicker(ANALYZE_SOURCE_PHOTO)
        composeTestRule
            .onNodeWithText(ANALYZE_SOURCE_PDF)
            .performClick()
        waitForIdle()

        reopenAnalyzeSourcePicker(ANALYZE_SOURCE_PDF)
        composeTestRule
            .onNodeWithText(ANALYZE_SOURCE_TEXT)
            .performClick()
        waitForIdle()

        // Analyze button should be visible after selection
        composeTestRule
            .onNodeWithText("Analyze")
            .assertIsDisplayed()
    }
}
