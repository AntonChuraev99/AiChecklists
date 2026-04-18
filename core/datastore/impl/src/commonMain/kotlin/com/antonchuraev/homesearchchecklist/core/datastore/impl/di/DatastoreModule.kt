package com.antonchuraev.homesearchchecklist.core.datastore.impl.di

import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.core.datastore.impl.ThemeRepositoryImpl
import org.koin.dsl.module

val datastoreModule = module {
    single<ThemeRepository> { ThemeRepositoryImpl(dataStore = get()) }
}
