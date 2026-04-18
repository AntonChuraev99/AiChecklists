package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and observing the user's chosen theme mode.
 *
 * The implementation stores the preference in DataStore.
 * The default value before any explicit selection is [AppThemeMode.Light].
 */
interface ThemeRepository {
    /** Emits the current theme mode and subsequent updates. Never throws. */
    val themeMode: Flow<AppThemeMode>

    /** Persist [mode] as the user's active theme preference. */
    suspend fun setThemeMode(mode: AppThemeMode)

    /**
     * Emits whether the user opted in to Material You (dynamic color).
     *
     * Platform support (Android 12+) is orthogonal to this preference — the
     * flag is persisted regardless of platform, and the UI layer decides
     * whether to honor it via `supportsDynamicColor()`. Keeping the
     * preference storage platform-agnostic means a user who opts in on an
     * Android 12 device keeps the opt-in if they later restore onto an iOS
     * build or an older phone.
     *
     * Default for new installs: `false`. Users must explicitly opt in to
     * Material You via Settings — keeps the default brand palette stable
     * across devices until the user chooses the wallpaper-based look.
     */
    val dynamicColor: Flow<Boolean>

    /** Persist the user's Material You opt-in. */
    suspend fun setDynamicColor(enabled: Boolean)
}
