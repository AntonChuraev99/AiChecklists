package com.antonchuraev.homesearchchecklist.settings.di

import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}
