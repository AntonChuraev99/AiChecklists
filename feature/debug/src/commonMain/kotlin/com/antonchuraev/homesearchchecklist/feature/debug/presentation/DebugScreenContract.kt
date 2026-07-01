package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class DebugScreenState(
    val showInfoDialog: Boolean = false,
    val isRestoreCreditsLoading: Boolean = false,
    val restoreCreditsResult: RestoreCreditsResult? = null,
    val showCancelReasonSheet: Boolean = false
) : State

sealed interface RestoreCreditsResult {
    data class Success(val credits: Int) : RestoreCreditsResult
    data class Error(val message: String) : RestoreCreditsResult
}

sealed interface DebugScreenIntent : Intent {
    data object OnBackClick : DebugScreenIntent
    data object ShowInfoDialog : DebugScreenIntent
    data object HideInfoDialog : DebugScreenIntent
    data object ResetOnboarding : DebugScreenIntent
    data object ClearData : DebugScreenIntent
    data object CreateTestChecklists : DebugScreenIntent
    data object OpenStoreScreenshot : DebugScreenIntent
    data object TestRestoreCredits : DebugScreenIntent
    data object DismissRestoreCreditsResult : DebugScreenIntent
    data object OpenInteractiveOnboarding : DebugScreenIntent
    data object OpenScreenCatalog : DebugScreenIntent
    data object OpenOnboardings : DebugScreenIntent
    data object ShowCancelReasonSheet : DebugScreenIntent
    data object HideCancelReasonSheet : DebugScreenIntent
}

