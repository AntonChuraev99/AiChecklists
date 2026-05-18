package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_LANGUAGE = "language"
private val DEFAULT_LANGUAGE = AppLanguage.System

class LanguageRepositoryImpl(
    private val dataStore: AppDatastore,
) : LanguageRepository {

    override val language: Flow<AppLanguage> =
        dataStore.observeString(KEY_LANGUAGE, DEFAULT_LANGUAGE.name)
            .map { stored ->
                AppLanguage.entries.firstOrNull { it.name == stored } ?: DEFAULT_LANGUAGE
            }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.saveString(KEY_LANGUAGE, language.name)
    }
}
