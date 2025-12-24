package com.antonchuraev.homesearchchecklist.feature.home.di

import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreenViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::MainScreenViewModel)
}

