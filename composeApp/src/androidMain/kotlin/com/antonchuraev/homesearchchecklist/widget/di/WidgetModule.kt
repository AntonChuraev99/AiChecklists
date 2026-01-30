package com.antonchuraev.homesearchchecklist.widget.di

import com.antonchuraev.homesearchchecklist.widget.data.WidgetRepository
import com.antonchuraev.homesearchchecklist.widget.data.WidgetStateManager
import org.koin.dsl.module

/**
 * Koin module for widget dependencies.
 * Provides WidgetRepository and WidgetStateManager as singletons.
 */
val widgetModule = module {
    single { WidgetRepository(get(), get()) }
    single { WidgetStateManager(get()) }
}
