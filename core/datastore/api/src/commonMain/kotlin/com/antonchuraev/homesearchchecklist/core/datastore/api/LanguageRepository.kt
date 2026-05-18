package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and observing the user's chosen UI language.
 *
 * The implementation stores the preference in DataStore as the enum [AppLanguage.name].
 * Default for new installs is [AppLanguage.System] — meaning the app follows the
 * device locale until the user explicitly overrides.
 */
interface LanguageRepository {
    /** Emits the current language and subsequent updates. Never throws. */
    val language: Flow<AppLanguage>

    /** Persist [language] as the user's active UI language. */
    suspend fun setLanguage(language: AppLanguage)
}
