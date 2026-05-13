package com.antonchuraev.homesearchchecklist.feature.home.di

import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreenViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::MainScreenViewModel)
    viewModelOf(::TodayViewModel)
    viewModel { (checklistId: Long) ->
        ChecklistDetailViewModel(checklistId, get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { (fillId: Long) ->
        FillDetailViewModel(fillId, get(), get(), get())
    }
    viewModel { (checklistId: Long) ->
        FillsListViewModel(checklistId, get(), get())
    }
}

