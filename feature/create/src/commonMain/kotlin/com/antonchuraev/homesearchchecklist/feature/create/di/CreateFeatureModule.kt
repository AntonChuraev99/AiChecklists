package com.antonchuraev.homesearchchecklist.feature.create.di

import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val createFeatureModule = module {
    viewModelOf(::CreateChecklistViewModel)
}

