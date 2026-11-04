package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities

/**
 * [VoiceCapabilities] of a platform without voice calls (web, iOS): the call is Android only. Every answer is the
 * honest "no", so an entry point gated on it stays hidden and nothing tries to open a microphone.
 */
object UnavailableVoiceCapabilities : VoiceCapabilities {
    override val isCallSupported: Boolean get() = false

    override suspend fun recognizer(locale: SpeechLocale): SpeechAvailability = SpeechAvailability.Unavailable

    override val touchExplorationEnabled: Boolean get() = false

    override fun hasRecordPermission(): Boolean = false
}
