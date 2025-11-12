package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.viewmodels.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Главный модуль Koin для DI
 */
val appModule = module {
    
    // ViewModels для экранов
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::DebugViewModel)
    viewModelOf(::HomeTabViewModel)
    viewModelOf(::FutureTabViewModel)
}

