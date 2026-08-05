package com.antonchuraev.homesearchchecklist.feature.home.di

import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreenViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.projects.ProjectsViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.picker.AddToChecklistPickerViewModel
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::MainScreenViewModel)
    viewModelOf(::TodayViewModel)
    // v2 Inbox tab. Constructor-injected only (no runtime parameters), so viewModelOf resolves every
    // dependency (repository, use case, reminder scheduler, display prefs, navigator, analytics,
    // logger) from the aggregated appModule. Registered unconditionally: the entry is always present
    // in App.kt's
    // entryProvider (a route with no matching entry hard-crashes NavDisplay after a process death
    // that outlives an arm flip), and it simply never resolves in the control arm.
    viewModelOf(::InboxViewModel)
    // v2 Projects tab. Registered unconditionally for the same reason as InboxViewModel: the entry
    // is always present in App.kt's entryProvider, and it simply never resolves on the classic layout.
    viewModelOf(::ProjectsViewModel)
    viewModel { CalendarViewModel(get(), get(), get()) }
    viewModel { (checklistId: Long, currentFolderId: String?) ->
        // Last get() = the app-wide CoroutineScope (core:common:impl) — confirmFolderDelete needs a
        // scope that outlives this entry, because it pops itself before the delete is persisted.
        ChecklistDetailViewModel(checklistId, currentFolderId, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
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

