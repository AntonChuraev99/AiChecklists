package com.antonchuraev.homesearchchecklist.feature.home.di

import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreenViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.picker.AddToChecklistPickerViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::MainScreenViewModel)
    viewModelOf(::TodayViewModel)
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { (checklistId: Long, currentFolderId: String?) ->
        ChecklistDetailViewModel(checklistId, currentFolderId, get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { (fillId: Long) ->
        FillDetailViewModel(fillId, get(), get(), get())
    }
    viewModel { (checklistId: Long) ->
        FillsListViewModel(checklistId, get(), get())
    }
    viewModel { (text: String, purpose: AddToChecklistPurpose) ->
        AddToChecklistPickerViewModel(
            initialText = text,
            purpose = purpose,
            checklistRepository = get(),
            appNavigator = get(),
            logger = get(),
        )
    }
}

