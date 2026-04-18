package com.antonchuraev.homesearchchecklist.settings.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode

data class SettingsState(
    val selectedTheme: AppThemeMode = AppThemeMode.System,
    val isLoading: Boolean = true,
) : State

sealed interface SettingsIntent : Intent {
    data class SelectTheme(val mode: AppThemeMode) : SettingsIntent
    data object BackClick : SettingsIntent
}

sealed interface SettingsSideEffect : SideEffect {
    data object NavigateBack : SettingsSideEffect
}
