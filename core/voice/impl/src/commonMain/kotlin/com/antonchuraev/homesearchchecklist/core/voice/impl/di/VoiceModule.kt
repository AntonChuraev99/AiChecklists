package com.antonchuraev.homesearchchecklist.core.voice.impl.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin graph of the voice call (registered in `appModule`).
 *
 * Android ([platformVoiceModule] in androidMain):
 * - `factory` — `SpeechInput`, `SpeechOutput`, `AudioFocusController`: every call gets its own recognizer, TTS engine
 *   and focus request, and releases them when it ends (resolve them per call inside the engine factory, never hold
 *   one in a `single`).
 * - `single` — `AudioRouteMonitor` (one process-wide device callback), `BargeInDetector` (stateless, opens the
 *   microphone per collection), `VoiceCapabilities`.
 * - `factory` — `VoiceCallEngine.Factory`, resolved with parameters matched by type:
 *   `get<VoiceCallEngine.Factory> { parametersOf(turnGateway, sessionGateway) }`, optionally plus a
 *   `Flow<Boolean>` "online". Every `create(scope, config)` makes a `DefaultVoiceCallEngine` with a new
 *   `SpeechInput`, `SpeechOutput` and `AudioFocusController`.
 *
 * Web and iOS: only `VoiceCapabilities` = [com.antonchuraev.homesearchchecklist.core.voice.impl.UnavailableVoiceCapabilities].
 * Nothing else is bound there — the call is Android only, so resolving `SpeechInput` or the engine factory on those
 * platforms is a bug.
 *
 * Not bound here: `VoiceTurnGateway`, `VoiceSessionGateway` — the transport over chat_agent, implemented by the feature
 * layer and passed as the factory's parameters.
 */
val voiceModule: Module = module {
    includes(platformVoiceModule())
}

/** Platform bindings of [voiceModule]. */
internal expect fun platformVoiceModule(): Module
