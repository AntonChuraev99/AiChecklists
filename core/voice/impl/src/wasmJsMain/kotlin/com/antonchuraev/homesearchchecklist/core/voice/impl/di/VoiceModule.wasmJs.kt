package com.antonchuraev.homesearchchecklist.core.voice.impl.di

import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceNetworkMonitor
import com.antonchuraev.homesearchchecklist.core.voice.impl.UnavailableVoiceCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** Web: no voice call, only the "unavailable" capabilities so the entry point can hide itself. */
internal actual fun platformVoiceModule(): Module = module {
    single<VoiceCapabilities> { UnavailableVoiceCapabilities }
    single<VoiceNetworkMonitor> { AlwaysOnlineVoiceNetworkMonitor }
}

/** No call on this platform, so nothing watches the network: always online. */
private object AlwaysOnlineVoiceNetworkMonitor : VoiceNetworkMonitor {
    override val isOnline: Flow<Boolean> = flowOf(true)
}
