package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.feature.checklist.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.CreateChecklistViewModel
import com.antonchuraev.homesearchchecklist.feature.checklist.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val checklistFeatureModule = module {
    single { ChecklistRepository(get()) }
    viewModelOf(::MainViewModel)
    viewModelOf(::CreateChecklistViewModel)
}

