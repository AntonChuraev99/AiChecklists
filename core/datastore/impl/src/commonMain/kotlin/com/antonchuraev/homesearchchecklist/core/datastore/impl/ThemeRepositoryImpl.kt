package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_THEME_MODE = "theme_mode"

class ThemeRepositoryImpl(
    private val dataStore: AppDatastore,
) : ThemeRepository {

    override val themeMode: Flow<AppThemeMode> =
        dataStore.observeString(KEY_THEME_MODE, AppThemeMode.System.name)
            .map { stored ->
                AppThemeMode.entries.firstOrNull { it.name == stored } ?: AppThemeMode.System
            }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.saveString(KEY_THEME_MODE, mode.name)
    }
}
