package com.antonchuraev.homesearchchecklist.feature.debug.di

import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugViewModel
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.ScreenCatalogViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val debugFeatureModule = module {
    viewModelOf(::DebugViewModel)
    viewModelOf(::ScreenCatalogViewModel)
}

