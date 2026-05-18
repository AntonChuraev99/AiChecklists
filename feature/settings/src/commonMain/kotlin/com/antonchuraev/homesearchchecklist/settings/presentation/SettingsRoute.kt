package com.antonchuraev.homesearchchecklist.settings.presentation

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.settings.ui.SettingsScreenContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings screen. Reachable from the app drawer (item "Settings").
 * When `drawerState` is provided, a hamburger affordance replaces the
 * back-arrow in the TopAppBar — satisfies MD3 "Drawer Affordance Scope"
 * rule (every in-app destination reachable from a drawer must expose
 * the drawer). When called without a drawer (e.g. deep link), falls back
 * to the standard back-arrow.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    drawerState: DrawerState? = null,
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
        selectedLanguage = state.selectedLanguage,
        onLanguageChange = { viewModel.sendIntent(SettingsIntent.SelectLanguage(it)) },
        onBackClick = { viewModel.sendIntent(SettingsIntent.BackClick) },
        drawerState = drawerState,
        modifier = modifier,
    )
}
