package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class DebugScreenState(
    val showInfoDialog: Boolean = false
) : State

sealed interface DebugScreenIntent : Intent {
    data object OnBackClick : DebugScreenIntent
    data object ShowInfoDialog : DebugScreenIntent
    data object HideInfoDialog : DebugScreenIntent
    data object ResetOnboarding : DebugScreenIntent
    data object ClearData : DebugScreenIntent
    data object CreateTestChecklists : DebugScreenIntent
}

