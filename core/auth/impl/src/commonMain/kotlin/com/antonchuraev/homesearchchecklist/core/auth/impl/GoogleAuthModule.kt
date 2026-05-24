package com.antonchuraev.homesearchchecklist.core.auth.impl

import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal expect fun platformAuthModule(): Module

val googleAuthModule = module {
    includes(platformAuthModule())
    single<GoogleAuthRepository> { GoogleAuthRepositoryImpl(get()) }
}
