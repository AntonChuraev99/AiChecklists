package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and observing the user's chosen theme mode.
 *
 * The implementation stores the preference in DataStore.
 * The default value before any explicit selection is [AppThemeMode.System].
 */
interface ThemeRepository {
    /** Emits the current theme mode and subsequent updates. Never throws. */
    val themeMode: Flow<AppThemeMode>

    /** Persist [mode] as the user's active theme preference. */
    suspend fun setThemeMode(mode: AppThemeMode)
}
