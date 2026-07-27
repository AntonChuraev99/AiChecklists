package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.State

data class DebugScreenState(
    val showInfoDialog: Boolean = false,
    val isRestoreCreditsLoading: Boolean = false,
    val restoreCreditsResult: RestoreCreditsResult? = null,
    val showCancelReasonSheet: Boolean = false,
    /**
     * Persisted nav A/B arm wire value ("control" | "v2"), or null when nothing is stored and Remote
     * Config still decides. Read straight from DataStore rather than from the resolver so the row
     * shows what will apply on the NEXT launch, not the arm this process latched.
     */
    val navArm: String? = null
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

    /**
     * Force the nav A/B arm for this install, or clear the override with [arm] = null.
     *
     * Writes the value the resolver consults BEFORE Remote Config, which is what makes an override
     * possible at all: the arm is sticky and latched per process, so there is otherwise no way to see
     * the v2 shell until a console parameter exists. Takes effect on the next app start.
     */
    data class SetNavArm(val arm: String?) : DebugScreenIntent
}

