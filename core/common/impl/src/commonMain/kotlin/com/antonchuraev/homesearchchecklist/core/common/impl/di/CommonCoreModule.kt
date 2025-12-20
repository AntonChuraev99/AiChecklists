package com.antonchuraev.homesearchchecklist.core.common.impl.di

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppDispatcherProvider
import com.antonchuraev.homesearchchecklist.core.common.impl.createLogger
import org.koin.dsl.module

val commonCoreModule = module {
    single<AppLogger> { createLogger() }
    single<AppDispatcherProvider> { AppDispatcherProvider.DEFAULT }
}
