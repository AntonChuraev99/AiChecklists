package com.antonchuraev.homesearchchecklist.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.settings.ui.SettingsScreenContent
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                SettingsSideEffect.NavigateBack -> onBackClick()
            }
        }
    }

    SettingsScreenContent(
        selectedTheme = state.selectedTheme,
        onThemeChange = { viewModel.sendIntent(SettingsIntent.SelectTheme(it)) },
        dynamicColorEnabled = state.dynamicColorEnabled,
        dynamicColorSupported = state.dynamicColorSupported,
        onDynamicColorChange = { viewModel.sendIntent(SettingsIntent.ToggleDynamicColor(it)) },
        onBackClick = { viewModel.sendIntent(SettingsIntent.BackClick) },
        modifier = modifier,
    )
}
