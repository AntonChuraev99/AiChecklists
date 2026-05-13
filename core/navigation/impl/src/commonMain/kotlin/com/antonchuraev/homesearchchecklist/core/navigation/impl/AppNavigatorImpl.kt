package com.antonchuraev.homesearchchecklist.core.navigation.impl

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Channel-based AppNavigator implementation.
 *
 * NavController stays entirely in the Compose layer (App.kt). This class only
 * emits [NavCommand] values; App.kt translates them into NavController calls.
 *
 * Channel.BUFFERED guarantees that commands emitted by ViewModel.init before
 * App.kt's LaunchedEffect starts collecting are queued and delivered in order —
 * the root cause of the Splash infinite-loader race condition is eliminated
 * architecturally rather than with a tactical queue workaround.
 */
class AppNavigatorImpl : AppNavigator {

    private val _commands = Channel<NavCommand>(Channel.BUFFERED)
    override val commands: Flow<NavCommand> = _commands.receiveAsFlow()

    private val _events = MutableSharedFlow<AppNavEvent>(replay = 0, extraBufferCapacity = 1)
    override val events: SharedFlow<AppNavEvent> = _events.asSharedFlow()

    override fun showWidgetInstruction() {
        _events.tryEmit(AppNavEvent.ShowWidgetInstruction)
    }

    override fun requestCreateWeeklyChecklist() {
        _events.tryEmit(AppNavEvent.CreateWeeklyChecklistRequested)
    }

    private fun emit(command: NavCommand) {
        _commands.trySend(command)
    }

    override fun onBack() = emit(NavCommand.Back)

    override fun navigateToOnboarding() = emit(NavCommand.ToOnboarding)

    override fun navigateToInteractiveOnboarding() = emit(NavCommand.ToInteractiveOnboarding)

    override fun navigateToMainScreen(clearBackStack: Boolean) =
        emit(NavCommand.ToMainScreen(clearBackStack))

    override fun navigateToDebugMenu() = emit(NavCommand.ToDebugMenu)

    override fun navigateToStoreScreenshot() = emit(NavCommand.ToStoreScreenshot)

    override fun navigateToCreateChecklistScreen(templateId: Int?) =
        emit(NavCommand.ToCreateChecklistScreen(templateId))

    override fun navigateToEditChecklist(checklistId: Long) =
        emit(NavCommand.ToEditChecklist(checklistId))

    override fun navigateToTemplatesScreen() = emit(NavCommand.ToTemplatesScreen)

    override fun navigateToTemplatePreview(templateId: String) =
        emit(NavCommand.ToTemplatePreview(templateId))

    override fun navigateToAnalyzeScreen(checklistId: Long?, fillDefault: Boolean) =
        emit(NavCommand.ToAnalyzeScreen(checklistId, fillDefault))

    override fun navigateToAnalyzeResultPreview() = emit(NavCommand.ToAnalyzeResultPreview)

    override fun navigateToChecklistDetail(checklistId: Long, clearBackStack: Boolean) =
        emit(NavCommand.ToChecklistDetail(checklistId, clearBackStack))

    override fun navigateToFillDetail(fillId: Long, clearBackStack: Boolean) =
        emit(NavCommand.ToFillDetail(fillId, clearBackStack))

    override fun navigateToFillsList(checklistId: Long) =
        emit(NavCommand.ToFillsList(checklistId))

    override fun navigateToPaywall(source: String) = emit(NavCommand.ToPaywall(source))

    override fun navigateToPaywallVariant(source: String, forceVariant: String) =
        emit(NavCommand.ToPaywallVariant(source, forceVariant))

    override fun navigateToSubscriptionStatus(showSuccessMessage: Boolean) =
        emit(NavCommand.ToSubscriptionStatus(showSuccessMessage))

    override fun navigateToShareChecklist(checklistId: Long) =
        emit(NavCommand.ToShareChecklist(checklistId))

    override fun navigateToUpdateFeed() = emit(NavCommand.ToUpdateFeed)

    override fun navigateToSettings() = emit(NavCommand.ToSettings)

    override fun navigateToToday() = emit(NavCommand.ToToday)

    override fun navigateToCalendar() = emit(NavCommand.ToCalendar)

    override fun navigateToScreenCatalog() = emit(NavCommand.ToScreenCatalog)
}
