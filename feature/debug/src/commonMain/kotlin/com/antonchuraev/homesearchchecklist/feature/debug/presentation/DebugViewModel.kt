package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugViewModel(
    private val appNavigator: AppNavigator
) : AppViewModel<DebugScreenState, DebugScreenIntent, Nothing>() {

    private val _screenState = MutableStateFlow(DebugScreenState())
    override val screenState: StateFlow<DebugScreenState> = _screenState.asStateFlow()

    override fun onIntent(intent: DebugScreenIntent) {
        when (intent) {
            DebugScreenIntent.OnBackClick -> appNavigator.onBack()
            DebugScreenIntent.ShowInfoDialog -> _screenState.value =
                _screenState.value.copy(showInfoDialog = true)

            DebugScreenIntent.HideInfoDialog -> _screenState.value =
                _screenState.value.copy(showInfoDialog = false)

            DebugScreenIntent.ResetOnboarding -> resetOnboarding()
            DebugScreenIntent.ClearData -> clearData()
            DebugScreenIntent.CreateTestChecklists -> createTestChecklists()
        }
    }

    private fun resetOnboarding() {
        TODO()
    }

    private fun clearData() {
        TODO()
    }

    private fun createTestChecklists() {
        TODO()
    }
}
