package com.antonchuraev.homesearchchecklist.core.common.impl.di

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppDispatcherProvider
import com.antonchuraev.homesearchchecklist.core.common.impl.createLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module

val commonCoreModule = module {
    single<AppLogger> { createLogger() }
    single<AppDispatcherProvider> { AppDispatcherProvider.DEFAULT }
    single<CoroutineScope> { CoroutineScope(Dispatchers.IO) }
}
