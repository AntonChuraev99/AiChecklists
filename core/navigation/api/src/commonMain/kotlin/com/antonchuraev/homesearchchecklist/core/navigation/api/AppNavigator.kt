package com.antonchuraev.homesearchchecklist.core.navigation.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface AppNavigator {

    /**
     * Cold flow of one-shot navigation commands. App.kt collects this and
     * translates each command into a NavController call. NavController never
     * leaves the Compose layer.
     *
     * Backed by a Channel.BUFFERED so commands emitted before collection starts
     * are queued and delivered in order — no race between ViewModel.init and
     * the Compose LaunchedEffect that sets up the collector.
     */
    val commands: Flow<NavCommand>

    /**
     * One-shot UI events (replay=0). App.kt collects these to open
     * global overlays that cannot be triggered via NavController.
     */
    val events: SharedFlow<AppNavEvent>

    /** Publish ShowWidgetInstruction event so App.kt opens the overlay. */
    fun showWidgetInstruction()

    fun onBack()

    fun navigateToOnboarding()

    fun navigateToInteractiveOnboarding()

    /**
     * Navigate to main screen, optionally clearing all screens from back stack.
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

    /** Navigate to paywall with a specific A/B variant forced (bypasses Remote Config). */
    fun navigateToPaywallVariant(source: String = "debug", forceVariant: String)

    fun navigateToSubscriptionStatus(showSuccessMessage: Boolean = false)

    fun navigateToShareChecklist(checklistId: Long)

    fun navigateToUpdateFeed()

    fun navigateToSettings()

    fun navigateToScreenCatalog()
}
