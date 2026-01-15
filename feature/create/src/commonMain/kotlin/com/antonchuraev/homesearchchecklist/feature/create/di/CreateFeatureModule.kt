package com.antonchuraev.homesearchchecklist.feature.create.di

import com.antonchuraev.homesearchchecklist.feature.create.data.repository.TemplatesRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.create.domain.repository.TemplatesRepository
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistViewModel
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val createFeatureModule = module {
    // Repository
    singleOf(::TemplatesRepositoryImpl) bind TemplatesRepository::class

    // ViewModels
    viewModelOf(::CreateChecklistViewModel)
    viewModelOf(::TemplatesViewModel)
}

