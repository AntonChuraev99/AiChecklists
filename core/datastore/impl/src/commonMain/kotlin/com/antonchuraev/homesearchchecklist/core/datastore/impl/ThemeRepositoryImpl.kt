package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_DYNAMIC_COLOR = "dynamic_color"
private const val DEFAULT_DYNAMIC_COLOR = false
private val DEFAULT_THEME_MODE = AppThemeMode.Light

class ThemeRepositoryImpl(
    private val dataStore: AppDatastore,
) : ThemeRepository {

    override val themeMode: Flow<AppThemeMode> =
        dataStore.observeString(KEY_THEME_MODE, DEFAULT_THEME_MODE.name)
            .map { stored ->
                AppThemeMode.entries.firstOrNull { it.name == stored } ?: DEFAULT_THEME_MODE
            }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.saveString(KEY_THEME_MODE, mode.name)
    }

    override val dynamicColor: Flow<Boolean> =
        dataStore.observeBoolean(KEY_DYNAMIC_COLOR, DEFAULT_DYNAMIC_COLOR)

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.saveBoolean(KEY_DYNAMIC_COLOR, enabled)
    }
}
