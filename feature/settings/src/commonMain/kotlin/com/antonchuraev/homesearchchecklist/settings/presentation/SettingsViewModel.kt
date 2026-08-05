package com.antonchuraev.homesearchchecklist.settings.presentation

import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppViewModel
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
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
    private val languageRepository: LanguageRepository,
    private val navVariantResolver: NavExperimentResolver,
    private val analyticsTracker: AnalyticsTracker,
) : AppViewModel<SettingsState, SettingsIntent, SettingsSideEffect>() {

    private val _screenState = MutableStateFlow(
        SettingsState(
            dynamicColorSupported = supportsDynamicColor(),
            // Seeded from the per-process cache rather than collected: the resolver deliberately
            // exposes no flow, because the shell must never change under a live screen (see
            // NavExperimentResolver). The switch below is the only writer and re-reads the resolver
            // after every toggle, so a one-time read here cannot drift.
            classicNavigationEnabled = navVariantResolver.currentArm() == NavVariant.CONTROL,
        ),
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
        viewModelScope.launch {
            languageRepository.language.collect { language ->
                _screenState.value = _screenState.value.copy(
                    selectedLanguage = language,
                )
            }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTheme -> persistTheme(intent.mode)
            is SettingsIntent.ToggleDynamicColor -> persistDynamicColor(intent.enabled)
            is SettingsIntent.SelectLanguage -> persistLanguage(intent.language)
            is SettingsIntent.ToggleClassicNavigation -> persistNavigationVariant(intent.enabled)
            SettingsIntent.BackClick -> viewModelScope.launch {
                _sideEffect.emit(SettingsSideEffect.NavigateBack)
            }
        }
    }

    /**
     * Switches the navigation shell.
     *
     * Write first, then read the resolver back — unlike theme/language, which have a flow to collect
     * from (the resolver deliberately has none: an observable shell could swap itself under a live
     * screen). The resolver advances its cached variant only once the write has landed, so
     * `currentArm()` is the persisted truth and not an echo of what was asked for. Mirroring the
     * request straight into state, as this did before, made a failed write look applied: the switch
     * stayed on and the setting was gone on the next launch.
     *
     * Everything downstream therefore hangs off the read-back:
     * - `nav_variant_selected` counts APPLIED switches. Emitted before the write it counted intents,
     *   so the metric over-reported the opt-out rate by however often the write failed.
     * - the re-root side effect fires only on success — rebuilding the stack for a shell the app is
     *   not going to keep leaves the host rooted for the wrong one.
     * - a failure is SAID OUT LOUD. Reverting the switch and stopping there is a silent early exit
     *   on a UX path: the control springs back on its own and nothing explains why.
     */
    private fun persistNavigationVariant(classicEnabled: Boolean) {
        val requested = if (classicEnabled) NavVariant.CONTROL else NavVariant.V2
        viewModelScope.launch {
            navVariantResolver.setVariant(requested)
            val applied = navVariantResolver.currentArm()
            // On a failed write this snaps the switch back to what is actually stored, so the screen
            // never claims a choice the app did not keep. The resolver logged the cause.
            _screenState.value = _screenState.value.copy(
                classicNavigationEnabled = applied == NavVariant.CONTROL,
            )
            if (applied == requested) {
                analyticsTracker.event(
                    AnalyticsEvents.Settings.NAV_VARIANT_SELECTED,
                    mapOf(
                        AnalyticsParams.NAV_ARM to if (classicEnabled) "control" else "v2",
                        AnalyticsParams.SOURCE to "settings",
                    ),
                )
                _sideEffect.emit(SettingsSideEffect.NavigationVariantChanged)
            } else {
                _sideEffect.emit(SettingsSideEffect.NavigationVariantSaveFailed)
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

    private fun persistLanguage(language: AppLanguage) {
        // Explicit user selection only (not the reactive collector) — this is the sole
        // signal of in-app language adoption; the Hindi UI launch shipped with none.
        analyticsTracker.event(
            AnalyticsEvents.Settings.LANGUAGE_SELECTED,
            mapOf(
                AnalyticsParams.LANGUAGE to (language.tag ?: "system"),
                AnalyticsParams.SOURCE to "settings",
            ),
        )
        viewModelScope.launch {
            languageRepository.setLanguage(language)
            // State update comes reactively from the Flow collector above.
        }
    }
}
