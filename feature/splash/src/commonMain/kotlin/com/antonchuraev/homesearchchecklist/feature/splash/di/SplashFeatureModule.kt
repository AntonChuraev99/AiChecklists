package com.antonchuraev.homesearchchecklist.feature.splash.di

import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val splashFeatureModule = module {
    viewModelOf(::SplashViewModel)
}


