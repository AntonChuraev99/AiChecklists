package com.antonchuraev.homesearchchecklist.settings.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.SideEffect
import com.antonchuraev.homesearchchecklist.core.common.api.State
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode

data class SettingsState(
    val selectedTheme: AppThemeMode = AppThemeMode.Light,
    val dynamicColorEnabled: Boolean = false,
    val dynamicColorSupported: Boolean = false,
    val selectedLanguage: AppLanguage = AppLanguage.System,
    /**
     * True while the app renders the previous (v1) navigation — drawer + bottom chat dock.
     *
     * Phrased as "use the classic layout" rather than as a variant enum because that is the only
     * decision the user makes here: v2 is the product, v1 is the opt-out. A two-value enum in the
     * UI would imply two equal options and invite a picker screen for what is one switch.
     */
    val classicNavigationEnabled: Boolean = false,
    val isLoading: Boolean = true,
) : State

sealed interface SettingsIntent : Intent {
    data class SelectTheme(val mode: AppThemeMode) : SettingsIntent
    data class ToggleDynamicColor(val enabled: Boolean) : SettingsIntent
    data class SelectLanguage(val language: AppLanguage) : SettingsIntent
    data class ToggleClassicNavigation(val enabled: Boolean) : SettingsIntent
    data object BackClick : SettingsIntent
}

sealed interface SettingsSideEffect : SideEffect {
    data object NavigateBack : SettingsSideEffect

    /**
     * The navigation shell changed. The host must re-root navigation, because the two shells own
     * different back stacks: v2 is rooted at `Inbox`, v1 at `Main`, and a stack built for one is
     * not renderable by the other (an entry with no matching `entry<>` hard-crashes NavDisplay).
     */
    data object NavigationVariantChanged : SettingsSideEffect

    /**
     * The navigation shell could NOT be saved, so the switch has been put back where it was.
     *
     * A switch that snaps back on its own reads as a dead tap — the user sees the control refuse
     * them and is told nothing. The host shows a message instead.
     *
     * Carries no text: the copy is resolved with `stringResource` at the call site, which keeps the
     * ViewModel out of Compose Resources and keeps this screen's unit tests free of the resource
     * loader they have no Android context for.
     */
    data object NavigationVariantSaveFailed : SettingsSideEffect
}
