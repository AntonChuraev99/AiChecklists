package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.data.local.room.ChecklistDao
import com.antonchuraev.homesearchchecklist.data.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.data.repository.CreateChecklistBottomSheetRepository
import com.antonchuraev.homesearchchecklist.screens.create.CreateChecklistViewModel
import com.antonchuraev.homesearchchecklist.viewmodels.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Главный модуль Koin для DI
 */
val appModule = module {
    includes(platformModule())

    // Repositories
    single { CreateChecklistBottomSheetRepository() }
    single { ChecklistRepository(get<ChecklistDao>()) }

    // ViewModels для экранов
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::DebugViewModel)
    viewModelOf(::FutureTabViewModel)
    viewModelOf(::CreateChecklistViewModel)
}

expect fun platformModule(): Module

