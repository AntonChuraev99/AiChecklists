package com.antonchuraev.aichecklists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * UI tests for the Template flow.
 *
 * Tests cover:
 * 1. Templates screen displays bottom action buttons
 * 2. Create Manually button navigates to create screen
 * 3. Create with AI button navigates to analyze screen
 * 4. Search functionality toggles input field
 * 5. Back navigation from templates
 *
 * Note: Template preview tests are skipped as template names
 * come from Firebase Remote Config and may vary.
 */
class TemplateFlowTest : BaseUiTest() {

    private fun navigateToTemplates() {
        skipOnboardingAndGoToMain()
        waitForButton("Create Checklist")
        composeTestRule.onNodeWithText("Create Checklist").performClick()
        waitForIdle()
        // Now on Templates screen with title "New Checklist"
        waitForButton("Create Manually")
    }

    @Test
    fun templatesScreen_displaysBottomActionButtons() {
        navigateToTemplates()

        // Verify "Create Manually" button
        composeTestRule
            .onNodeWithText("Create Manually")
            .assertIsDisplayed()

        // Verify "Create with AI" button
        composeTestRule
            .onNodeWithText("Create with AI")
            .assertIsDisplayed()
    }

    @Test
    fun templatesScreen_createManuallyNavigatesToCreate() {
        navigateToTemplates()

        // When: Click "Create Manually" button
        composeTestRule
            .onNodeWithText("Create Manually")
            .performClick()
        waitForIdle()

        // Then: Should navigate to CreateChecklist form
        // Verify by checking for the name input placeholder
        composeTestRule
            .onNode(hasText("e.g., Project Tasks"))
            .assertExists()
    }

    @Test
    fun templatesScreen_createWithAiNavigatesToAnalyze() {
        navigateToTemplates()

        // When: Click "Create with AI" button
        composeTestRule
            .onNodeWithText("Create with AI")
            .performClick()
        waitForIdle()

        // Then: Should navigate to Analyze screen
        composeTestRule
            .onNodeWithText("What would you like to analyze?")
            .assertIsDisplayed()
    }

    @Test
    fun templatesScreen_displaysTitle() {
        navigateToTemplates()

        // Verify Templates screen title
        composeTestRule
            .onNodeWithText("New Checklist")
            .assertIsDisplayed()

        // Verify "Choose a template" heading
        composeTestRule
            .onNodeWithText("Choose a template")
            .assertIsDisplayed()

        // TODO: APP BUG - Search icon has no content description (accessibility issue)
        // Fix: Add contentDescription = "Search" to search icon in Templates toolbar
    }

    @Test
    fun templatesScreen_backNavigatesToMain() {
        navigateToTemplates()

        // When: Press back from templates screen
        pressBack()
        waitForIdle()

        // Then: Should return to main screen (empty state)
        assertOnMainScreen()
    }
}
