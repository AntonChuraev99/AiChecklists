package com.antonchuraev.homesearchchecklist.settings.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.desingsystem.theme.supportsDynamicColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themeRepository: ThemeRepository,
) : AppViewModel<SettingsState, SettingsIntent, SettingsSideEffect>() {

    private val _screenState = MutableStateFlow(
        SettingsState(dynamicColorSupported = supportsDynamicColor()),
    )
    override val screenState: StateFlow<SettingsState> = _screenState

    private val _sideEffect = MutableSharedFlow<SettingsSideEffect>(extraBufferCapacity = 16)
    val sideEffect: Flow<SettingsSideEffect> = _sideEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            themeRepository.themeMode.collect { mode ->
                _screenState.value = _screenState.value.copy(
                    selectedTheme = mode,
                    isLoading = false,
                )
            }
        }
        viewModelScope.launch {
            themeRepository.dynamicColor.collect { enabled ->
                _screenState.value = _screenState.value.copy(
                    dynamicColorEnabled = enabled,
                )
            }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTheme -> persistTheme(intent.mode)
            is SettingsIntent.ToggleDynamicColor -> persistDynamicColor(intent.enabled)
            SettingsIntent.BackClick -> viewModelScope.launch {
                _sideEffect.emit(SettingsSideEffect.NavigateBack)
            }
        }
    }

    private fun persistTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
            // State update comes reactively from the Flow collector above.
        }
    }

    private fun persistDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            themeRepository.setDynamicColor(enabled)
            // State update comes reactively from the Flow collector above.
        }
    }
}
