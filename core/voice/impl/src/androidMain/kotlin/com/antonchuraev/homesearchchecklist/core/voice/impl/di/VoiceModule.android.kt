package com.antonchuraev.homesearchchecklist.core.voice.impl.di

import android.content.Context
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioFocusController
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRouteMonitor
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInDetector
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechInput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutput
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEngine
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceNetworkMonitor
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnGateway
import com.antonchuraev.homesearchchecklist.core.voice.impl.AndroidAudioFocus
import com.antonchuraev.homesearchchecklist.core.voice.impl.AndroidAudioRouteMonitor
import com.antonchuraev.homesearchchecklist.core.voice.impl.AndroidBargeInDetector
import com.antonchuraev.homesearchchecklist.core.voice.impl.AndroidVoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.impl.AndroidVoiceNetworkMonitor
import com.antonchuraev.homesearchchecklist.core.voice.impl.DefaultVoiceCallEngine
import com.antonchuraev.homesearchchecklist.core.voice.impl.PlatformSpeechInput
import com.antonchuraev.homesearchchecklist.core.voice.impl.PlatformSpeechOutput
import com.antonchuraev.homesearchchecklist.core.voice.impl.VoiceCallDependencies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android audio of the voice call. `Context` is the application context bound by the app's `platformModule`
 * (`single { AppContextHolder.context }`). Per-call objects are `factory`, see [voiceModule].
 */
internal actual fun platformVoiceModule(): Module = module {
    factory<SpeechInput> { PlatformSpeechInput(get<Context>()) }
    factory<SpeechOutput> { PlatformSpeechOutput(get<Context>()) }
    factory<AudioFocusController> { AndroidAudioFocus(get<Context>()) }
    single<AudioRouteMonitor> { AndroidAudioRouteMonitor(get<Context>()) }
    single<BargeInDetector> { AndroidBargeInDetector(get<Context>()) }
    single<VoiceCapabilities> { AndroidVoiceCapabilities(get<Context>()) }
    single<VoiceNetworkMonitor> { AndroidVoiceNetworkMonitor(get<Context>()) }

    // Parameters (matched by type): VoiceTurnGateway and VoiceSessionGateway, required; a Flow<Boolean> "the device is
    // online", optional (without it the call never goes offline by itself: a failed turn still retries while
    // Reconnecting). Each create() resolves a new recognizer, TTS engine and focus request for that call.
    factory<VoiceCallEngine.Factory> { params ->
        val turns = params.get<VoiceTurnGateway>()
        val sessions = params.get<VoiceSessionGateway>()

        @Suppress("UNCHECKED_CAST")
        // Default to the platform monitor, not `flowOf(true)`: a caller that forgets the parameter would otherwise
        // silently lose Unavailable(Offline) on start and Reconnecting while idle.
        val isOnline = params.getOrNull<Flow<*>>() as Flow<Boolean>? ?: get<VoiceNetworkMonitor>().isOnline
        VoiceCallEngine.Factory { scope, config ->
            DefaultVoiceCallEngine(
                scope = scope,
                config = config,
                deps = VoiceCallDependencies(
                    speechInput = get(),
                    speechOutput = get(),
                    audioFocus = get(),
                    audioRoute = get(),
                    bargeIn = get(),
                    capabilities = get(),
                    turns = turns,
                    sessions = sessions,
                    isOnline = isOnline,
                ),
            )
        }
    }
}
