package com.antonchuraev.homesearchchecklist.core.common.impl.di

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppDispatchersProvider
import com.antonchuraev.homesearchchecklist.core.common.impl.createLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val commonCoreModule = module {
    single<AppLogger> { createLogger() }
    single<AppDispatchersProvider> { AppDispatchersProvider.DEFAULT }
    single<CoroutineScope> { CoroutineScope(Dispatchers.Default) }
}
