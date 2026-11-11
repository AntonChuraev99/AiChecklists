package com.antonchuraev.homesearchchecklist.feature.voicecall.di

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEngine
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnGateway
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import com.antonchuraev.homesearchchecklist.feature.voicecall.data.VoiceSessionGatewayImpl
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.VoiceCallViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.time.Instant

/**
 * Koin graph of the voice call overlay.
 *
 * Resolved elsewhere: `VoiceCallEngine.Factory` and `VoiceCapabilities` (`:core:voice:impl`
 * `voiceModule`, Android only — on web and iOS the entry is hidden and nothing resolves the factory),
 * `VoiceTurnGateway` (`:feature:aichat:impl`, a `factory`: one conversation per call),
 * [VoiceCallLanguage] (composeApp: the app language).
 */
val voiceCallFeatureModule = module {
    // One per call too: the transport is stateless, but it is cheap and keeps calls apart.
    factory<VoiceSessionGateway> {
        val userDataRepository = get<UserDataRepository>()
        VoiceSessionGatewayImpl(
            userId = { userDataRepository.getUserData().userId },
            logger = get<AppLogger>(),
        )
    }
    viewModel {
        val language = get<VoiceCallLanguage>()
        VoiceCallViewModel(
            // A fresh factory per call: Koin resolves a new turn gateway (new conversation) and session
            // transport each time; the factory then binds new recognizer / TTS / focus objects.
            newEngineFactory = {
                get<VoiceCallEngine.Factory> {
                    parametersOf(get<VoiceTurnGateway>(), get<VoiceSessionGateway>())
                }
            },
            capabilities = get(),
            analytics = get(),
            logger = get(),
            languageTag = { language.tag() },
            formatLocalTime = ::formatLocalClock,
        )
    }
}

/** The app language (`en`, `ru`, `hi`) the call listens and answers in; bound by the app. */
fun interface VoiceCallLanguage {
    fun tag(): String
}

/** `HH:mm` of [epochMs] in the device time zone (ASCII digits, as the timer). */
internal fun formatLocalClock(epochMs: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0')
}
