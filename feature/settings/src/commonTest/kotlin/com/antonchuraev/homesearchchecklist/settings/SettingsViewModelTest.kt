package com.antonchuraev.homesearchchecklist.settings

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsIntent
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsSideEffect
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepository: FakeThemeRepository
    private lateinit var fakeLanguageRepository: FakeLanguageRepository
    private lateinit var fakeAnalytics: FakeAnalyticsTracker
    private lateinit var fakeNavResolver: FakeNavVariantResolver
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeThemeRepository()
        fakeLanguageRepository = FakeLanguageRepository()
        fakeAnalytics = FakeAnalyticsTracker()
        fakeNavResolver = FakeNavVariantResolver()
        viewModel = SettingsViewModel(
            fakeRepository,
            fakeLanguageRepository,
            fakeNavResolver,
            fakeAnalytics,
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectLanguage_emitsLanguageSelectedEvent() = runTest {
        viewModel.sendIntent(SettingsIntent.SelectLanguage(AppLanguage.Hindi))
        advanceUntilIdle()

        val event = fakeAnalytics.events.last()
        assertEquals(AnalyticsEvents.Settings.LANGUAGE_SELECTED, event.first)
        assertEquals("hi", event.second[AnalyticsParams.LANGUAGE])
        assertEquals("settings", event.second[AnalyticsParams.SOURCE])
    }

    @Test
    fun toggleClassicNavigation_persistsVariantAndSignalsHostToReRoot() = runTest {
        val effects = mutableListOf<SettingsSideEffect>()
        val job = launch { viewModel.sideEffect.collect { effects += it } }
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = true))
        advanceUntilIdle()

        assertEquals(NavVariant.CONTROL, fakeNavResolver.variant)
        assertTrue(viewModel.screenState.value.classicNavigationEnabled)
        // Without this the host keeps a back stack rooted for the shell the user just left, and the
        // first navigation afterwards renders a route the new shell's chrome cannot address.
        assertTrue(effects.contains(SettingsSideEffect.NavigationVariantChanged))
        job.cancel()
    }

    @Test
    fun toggleClassicNavigation_off_returnsToV2() = runTest {
        viewModel.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = true))
        advanceUntilIdle()
        viewModel.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = false))
        advanceUntilIdle()

        assertEquals(NavVariant.V2, fakeNavResolver.variant)
        assertFalse(viewModel.screenState.value.classicNavigationEnabled)
    }

    @Test
    fun toggleClassicNavigation_emitsVariantSelectedEvent() = runTest {
        viewModel.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = true))
        advanceUntilIdle()

        val event = fakeAnalytics.events.last()
        assertEquals(AnalyticsEvents.Settings.NAV_VARIANT_SELECTED, event.first)
        // The value CHOSEN, not the one previously in effect — this event is the only signal of an
        // opt-out, and reporting the old value would invert it.
        assertEquals("control", event.second[AnalyticsParams.NAV_ARM])
        assertEquals("settings", event.second[AnalyticsParams.SOURCE])
    }

    /**
     * A write that does not land must leave no trace of having landed.
     *
     * All three assertions are the same bug seen from three sides: the switch would stay on and then
     * revert on the next launch; the host would re-root onto a shell the app is not keeping; and
     * `nav_variant_selected` — the only signal of how many users opt out — would count an intent the
     * app dropped.
     */
    @Test
    fun toggleClassicNavigation_persistFails_revertsSwitchAndReportsNothing() = runTest {
        val resolver = FakeNavVariantResolver(persistSucceeds = false)
        val analytics = FakeAnalyticsTracker()
        val vm = SettingsViewModel(fakeRepository, fakeLanguageRepository, resolver, analytics)
        val effects = mutableListOf<SettingsSideEffect>()
        val job = launch { vm.sideEffect.collect { effects += it } }
        advanceUntilIdle()

        vm.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = true))
        advanceUntilIdle()

        assertFalse(vm.screenState.value.classicNavigationEnabled)
        assertFalse(effects.contains(SettingsSideEffect.NavigationVariantChanged))
        assertTrue(
            analytics.events.none { it.first == AnalyticsEvents.Settings.NAV_VARIANT_SELECTED },
        )
        job.cancel()
    }

    /**
     * Reverting the switch is not enough on its own — a control that springs back and says nothing
     * reads as a dead tap, which is the silent early exit the project rules call a bug. The user is
     * told the change was not saved.
     */
    @Test
    fun toggleClassicNavigation_persistFails_tellsTheUser() = runTest {
        val resolver = FakeNavVariantResolver(persistSucceeds = false)
        val vm = SettingsViewModel(fakeRepository, fakeLanguageRepository, resolver, fakeAnalytics)
        val effects = mutableListOf<SettingsSideEffect>()
        val job = launch { vm.sideEffect.collect { effects += it } }
        advanceUntilIdle()

        vm.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = true))
        advanceUntilIdle()

        assertTrue(effects.contains(SettingsSideEffect.NavigationVariantSaveFailed))
        job.cancel()
    }

    /** The mirror image: a switch that DID save must not also claim it failed. */
    @Test
    fun toggleClassicNavigation_persistSucceeds_reportsNoFailure() = runTest {
        val effects = mutableListOf<SettingsSideEffect>()
        val job = launch { viewModel.sideEffect.collect { effects += it } }
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.ToggleClassicNavigation(enabled = true))
        advanceUntilIdle()

        assertFalse(effects.contains(SettingsSideEffect.NavigationVariantSaveFailed))
        job.cancel()
    }

    @Test
    fun init_loadsCurrentThemeFromRepository() = runTest {
        fakeRepository.emitTheme(AppThemeMode.Dark)
        advanceUntilIdle()

        val state = viewModel.screenState.value
        assertEquals(AppThemeMode.Dark, state.selectedTheme)
        assertFalse(state.isLoading)
    }

    @Test
    fun changeTheme_emitsNewState() = runTest {
        fakeRepository.emitTheme(AppThemeMode.System)
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.SelectTheme(AppThemeMode.Dark))
        advanceUntilIdle()

        assertEquals(AppThemeMode.Dark, viewModel.screenState.value.selectedTheme)
    }

    @Test
    fun changeTheme_persistsToRepository() = runTest {
        fakeRepository.emitTheme(AppThemeMode.System)
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.SelectTheme(AppThemeMode.Light))
        advanceUntilIdle()

        assertEquals(AppThemeMode.Light, fakeRepository.lastSavedTheme)
    }

    @Test
    fun backClick_emitsNavigateBackSideEffect() = runTest {
        var receivedEffect: SettingsSideEffect? = null
        val job = launch {
            receivedEffect = viewModel.sideEffect.first()
        }

        viewModel.sendIntent(SettingsIntent.BackClick)
        advanceUntilIdle()
        job.cancel()

        assertEquals(SettingsSideEffect.NavigateBack, receivedEffect)
    }

    @Test
    fun init_loadsCurrentDynamicColorFromRepository() = runTest {
        fakeRepository.emitDynamicColor(false)
        advanceUntilIdle()

        assertFalse(viewModel.screenState.value.dynamicColorEnabled)
    }

    @Test
    fun toggleDynamicColor_persistsAndReflectsInState() = runTest {
        fakeRepository.emitDynamicColor(true)
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.ToggleDynamicColor(false))
        advanceUntilIdle()

        assertEquals(false, fakeRepository.lastSavedDynamicColor)
        assertFalse(viewModel.screenState.value.dynamicColorEnabled)
    }

    @Test
    fun toggleDynamicColor_canBeReEnabled() = runTest {
        fakeRepository.emitDynamicColor(false)
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.ToggleDynamicColor(true))
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value.dynamicColorEnabled)
        assertEquals(true, fakeRepository.lastSavedDynamicColor)
    }

    // -------------------------------------------------------------------------
    // Language tests
    // -------------------------------------------------------------------------

    @Test
    fun init_loadsCurrentLanguageFromRepository() = runTest {
        fakeLanguageRepository.emitLanguage(AppLanguage.Russian)
        advanceUntilIdle()

        assertEquals(AppLanguage.Russian, viewModel.screenState.value.selectedLanguage)
    }

    @Test
    fun selectLanguage_persistsToRepository() = runTest {
        fakeLanguageRepository.emitLanguage(AppLanguage.System)
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.SelectLanguage(AppLanguage.English))
        advanceUntilIdle()

        assertEquals(AppLanguage.English, fakeLanguageRepository.lastSavedLanguage)
    }

    @Test
    fun selectLanguage_emitsNewState() = runTest {
        fakeLanguageRepository.emitLanguage(AppLanguage.System)
        advanceUntilIdle()

        viewModel.sendIntent(SettingsIntent.SelectLanguage(AppLanguage.Russian))
        advanceUntilIdle()

        assertEquals(AppLanguage.Russian, viewModel.screenState.value.selectedLanguage)
    }
}

private class FakeThemeRepository : ThemeRepository {
    private val _themeFlow = MutableStateFlow(AppThemeMode.System)
    private val _dynamicColorFlow = MutableStateFlow(true)
    var lastSavedTheme: AppThemeMode? = null
    var lastSavedDynamicColor: Boolean? = null

    override val themeMode: Flow<AppThemeMode> = _themeFlow
    override val dynamicColor: Flow<Boolean> = _dynamicColorFlow

    override suspend fun setThemeMode(mode: AppThemeMode) {
        lastSavedTheme = mode
        _themeFlow.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        lastSavedDynamicColor = enabled
        _dynamicColorFlow.value = enabled
    }

    fun emitTheme(mode: AppThemeMode) {
        _themeFlow.value = mode
    }

    fun emitDynamicColor(enabled: Boolean) {
        _dynamicColorFlow.value = enabled
    }
}

private class FakeLanguageRepository : LanguageRepository {
    private val _languageFlow = MutableStateFlow(AppLanguage.System)
    var lastSavedLanguage: AppLanguage? = null

    override val language: Flow<AppLanguage> = _languageFlow

    override suspend fun setLanguage(language: AppLanguage) {
        lastSavedLanguage = language
        _languageFlow.value = language
    }

    fun emitLanguage(language: AppLanguage) {
        _languageFlow.value = language
    }
}

private class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun setUserId(userId: String) {}
    override fun setUserProperties(properties: Map<String, Any>) {}
    override fun screenView(name: String) {}
    override fun event(name: String, params: Map<String, Any>) {
        events += name to params
    }
}

/** Starts on the product default (v2), i.e. the classic-layout switch reads off. */
private class FakeNavVariantResolver(private val persistSucceeds: Boolean = true) :
    NavExperimentResolver {
    var variant: NavVariant = NavVariant.V2
    override fun currentArm(): NavVariant = variant
    override suspend fun ensureResolved(): NavVariant = variant

    /** Like the real resolver: the readable variant advances only once the write has landed. */
    override suspend fun setVariant(variant: NavVariant) {
        if (persistSucceeds) this.variant = variant
    }

    override suspend fun clearVariant() {
        if (persistSucceeds) variant = NavVariant.V2
    }

    override fun isArmAssigned(): Boolean = true
}
