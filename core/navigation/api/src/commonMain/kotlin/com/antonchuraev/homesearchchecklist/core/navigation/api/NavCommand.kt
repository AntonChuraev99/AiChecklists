package com.antonchuraev.homesearchchecklist.core.navigation.api

/**
 * One-shot navigation commands emitted by [AppNavigator] and consumed by App.kt.
 *
 * NavController never leaves the Compose layer — App.kt collects this Channel-based
 * flow and translates each command into a NavController call. This eliminates the
 * singleton-with-lateinit-NavController anti-pattern and removes the race condition
 * where ViewModel.init fires before App.kt's LaunchedEffect installs the controller.
 *
 * Channel.BUFFERED semantics: commands emitted before the collector starts are queued
 * and delivered in order once collection begins.
 */
sealed interface NavCommand {
    data object Back : NavCommand

    data object ToOnboarding : NavCommand

    data object ToInteractiveOnboarding : NavCommand

    data class ToMainScreen(val clearBackStack: Boolean = false) : NavCommand

    data object ToDebugMenu : NavCommand

    data object ToStoreScreenshot : NavCommand

    data class ToCreateChecklistScreen(val templateId: Int?) : NavCommand

    data class ToEditChecklist(val checklistId: Long) : NavCommand

    data object ToTemplatesScreen : NavCommand

    data class ToTemplatePreview(val templateId: String) : NavCommand

    data class ToAnalyzeScreen(
        val checklistId: Long?,
        val fillDefault: Boolean,
    ) : NavCommand

    data object ToAnalyzeResultPreview : NavCommand

    data class ToChecklistDetail(
        val checklistId: Long,
        val clearBackStack: Boolean = false,
    ) : NavCommand

    data class ToFillDetail(
        val fillId: Long,
        val clearBackStack: Boolean = false,
    ) : NavCommand

    data class ToFillsList(val checklistId: Long) : NavCommand

    data class ToPaywall(val source: String) : NavCommand

    data class ToPaywallVariant(val source: String, val forceVariant: String) : NavCommand

    data class ToSubscriptionStatus(val showSuccessMessage: Boolean) : NavCommand

    data class ToShareChecklist(val checklistId: Long) : NavCommand

    data object ToUpdateFeed : NavCommand

    data object ToSettings : NavCommand

    data object ToToday : NavCommand

    data object ToCalendar : NavCommand

    data object ToScreenCatalog : NavCommand
}
