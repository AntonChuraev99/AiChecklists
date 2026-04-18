package com.antonchuraev.homesearchchecklist.core.navigation.api

import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharedFlow

interface AppNavigator {

    /**
     * One-shot navigation events (replay=0). App.kt collects these to open
     * global overlays that cannot be triggered via NavController.
     */
    val events: SharedFlow<AppNavEvent>

    /** Publish ShowWidgetInstruction event so App.kt opens the overlay. */
    fun showWidgetInstruction()

    fun installNavController(navController: NavController)

    fun onBack()

    fun navigateToOnboarding()

    fun navigateToInteractiveOnboarding()

    /**
     * Navigate to main screen, clearing all screens from back stack.
     */
    fun navigateToMainScreen(clearBackStack: Boolean = false)

    fun navigateToDebugMenu()

    fun navigateToStoreScreenshot()

    /**
     * todo change templateId to template class
     */
    fun navigateToCreateChecklistScreen(templateId: Int?)

    fun navigateToEditChecklist(checklistId: Long)

    fun navigateToTemplatesScreen()

    fun navigateToTemplatePreview(templateId: String)

    fun navigateToAnalyzeScreen(checklistId: Long? = null, fillDefault: Boolean = false)

    fun navigateToAnalyzeResultPreview()

    /**
     * Navigate to checklist detail. If clearBackStack is true, clears back stack to main screen.
     */
    fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean = false)

    /**
     * Navigate to fill detail. If clearBackStack is true, clears back stack to main screen.
     */
    fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean = false)

    fun navigateToFillsList(checklistId: Long)

    fun navigateToPaywall(source: String = "unknown")

    fun navigateToSubscriptionStatus(showSuccessMessage: Boolean = false)

    fun navigateToShareChecklist(checklistId: Long)

    fun navigateToUpdateFeed()
}

