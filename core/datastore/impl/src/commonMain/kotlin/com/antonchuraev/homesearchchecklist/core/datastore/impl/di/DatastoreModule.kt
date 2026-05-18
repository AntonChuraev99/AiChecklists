package com.antonchuraev.homesearchchecklist.core.datastore.impl.di

import com.antonchuraev.homesearchchecklist.core.datastore.api.AiChatPreferencesRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.core.datastore.impl.AiChatPreferencesRepositoryImpl
import com.antonchuraev.homesearchchecklist.core.datastore.impl.LanguageRepositoryImpl
import com.antonchuraev.homesearchchecklist.core.datastore.impl.ThemeRepositoryImpl
import org.koin.dsl.module

val datastoreModule = module {
    single<ThemeRepository> { ThemeRepositoryImpl(dataStore = get()) }
    single<LanguageRepository> { LanguageRepositoryImpl(dataStore = get()) }
    single<AiChatPreferencesRepository> { AiChatPreferencesRepositoryImpl(dataStore = get()) }
}
