package com.antonchuraev.homesearchchecklist.settings

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
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeThemeRepository()
        fakeLanguageRepository = FakeLanguageRepository()
        viewModel = SettingsViewModel(fakeRepository, fakeLanguageRepository)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
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
