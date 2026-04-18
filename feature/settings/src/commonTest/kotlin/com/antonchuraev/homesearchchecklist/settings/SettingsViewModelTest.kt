package com.antonchuraev.homesearchchecklist.settings

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepository: FakeThemeRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeThemeRepository()
        viewModel = SettingsViewModel(fakeRepository)
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

        assertEquals(AppThemeMode.Light, fakeRepository.lastSaved)
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
}

private class FakeThemeRepository : ThemeRepository {
    private val _flow = MutableStateFlow(AppThemeMode.System)
    var lastSaved: AppThemeMode? = null

    override val themeMode: Flow<AppThemeMode> = _flow

    override suspend fun setThemeMode(mode: AppThemeMode) {
        lastSaved = mode
        _flow.value = mode
    }

    fun emitTheme(mode: AppThemeMode) {
        _flow.value = mode
    }
}
