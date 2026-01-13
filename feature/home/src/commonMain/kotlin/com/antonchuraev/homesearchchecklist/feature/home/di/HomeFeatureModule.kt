package com.antonchuraev.homesearchchecklist.feature.home.di

import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreenViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::MainScreenViewModel)
    viewModel { (checklistId: Long) ->
        ChecklistDetailViewModel(checklistId, get(), get())
    }
}

