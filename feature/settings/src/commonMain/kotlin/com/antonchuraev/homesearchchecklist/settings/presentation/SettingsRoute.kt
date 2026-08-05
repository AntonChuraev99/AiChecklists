package com.antonchuraev.homesearchchecklist.settings.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.error_save_failed
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.settings.ui.SettingsScreenContent
import org.jetbrains.compose.resources.stringResource
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
    /**
     * Fired after the navigation shell was switched. The host must RE-ROOT navigation: the two
     * shells own different back stacks (v2 is rooted at `Inbox`, v1 at `Main`), and a stack built
     * for one is not renderable by the other — an entry with no matching `entry<>` hard-crashes
     * NavDisplay.
     *
     * Defaulted to a no-op so a host that predates the switch still compiles; such a host simply
     * applies the new shell on the next launch.
     */
    onNavigationVariantChanged: () -> Unit = {},
    drawerState: DrawerState? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    // Owned here, not in the content composable, so the collector below and the host share one
    // instance — a host recreated on state change drops the message that is already in flight.
    val snackbarHostState = remember { SnackbarHostState() }
    // Resolved out here because stringResource is @Composable and the collector is not.
    val navVariantSaveFailedMessage = stringResource(Res.string.error_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                SettingsSideEffect.NavigateBack -> onBackClick()
                SettingsSideEffect.NavigationVariantChanged -> onNavigationVariantChanged()
                SettingsSideEffect.NavigationVariantSaveFailed ->
                    snackbarHostState.showSnackbar(navVariantSaveFailedMessage)
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
        classicNavigationEnabled = state.classicNavigationEnabled,
        onClassicNavigationChange = {
            viewModel.sendIntent(SettingsIntent.ToggleClassicNavigation(it))
        },
        snackbarHostState = snackbarHostState,
        drawerState = drawerState,
        modifier = modifier,
    )
}
