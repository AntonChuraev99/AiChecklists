package com.antonchuraev.homesearchchecklist.core.auth.impl

import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformAuthModule(): Module = module {
    single<AuthProvider> { WasmGoogleAuthProvider() }
}
