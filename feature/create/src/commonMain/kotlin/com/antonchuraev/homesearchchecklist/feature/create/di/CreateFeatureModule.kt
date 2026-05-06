package com.antonchuraev.homesearchchecklist.feature.create.di

import com.antonchuraev.homesearchchecklist.feature.create.data.repository.TemplatesRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistViewModel
import com.antonchuraev.homesearchchecklist.feature.create.presentation.preview.TemplatePreviewViewModel
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesViewModel
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase.GetUserLimitsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val createFeatureModule = module {
    // Repository
    singleOf(::TemplatesRepositoryImpl) bind TemplatesRepository::class

    // Use cases
    factoryOf(::CreateWeeklyChecklistUseCase)

    // ViewModels
    viewModel { params ->
        CreateChecklistViewModel(
            editChecklistId = params.getOrNull(),
            checklistRepository = get(),
            appNavigator = get(),
            analyticsTracker = get(),
            getUserLimitsUseCase = get()
        )
    }
    viewModelOf(::TemplatesViewModel)
    viewModelOf(::TemplatePreviewViewModel)
}

