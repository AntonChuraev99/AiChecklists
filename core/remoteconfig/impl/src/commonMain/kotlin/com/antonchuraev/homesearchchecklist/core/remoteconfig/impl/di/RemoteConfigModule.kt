package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl.di

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.core.remoteconfig.impl.createRemoteConfigProvider
import org.koin.dsl.module

val remoteConfigModule = module {
    single<RemoteConfigProvider> { createRemoteConfigProvider() }
}
