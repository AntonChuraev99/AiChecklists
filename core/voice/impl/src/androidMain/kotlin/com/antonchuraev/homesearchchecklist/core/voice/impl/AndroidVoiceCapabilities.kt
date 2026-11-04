package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import android.view.accessibility.AccessibilityManager
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the device can do for a call before one starts (entry points, gates). The per-language answer is a quick one:
 * on-device when the device has an on-device recognizer at all; the call itself asks [PlatformSpeechInput] for the
 * exact language.
 */
internal class AndroidVoiceCapabilities(private val context: Context) : VoiceCapabilities {

    override val isCallSupported: Boolean get() = true

    override suspend fun recognizer(locale: SpeechLocale): SpeechAvailability = withContext(Dispatchers.Main.immediate) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechAvailability.OnDevice
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechAvailability.Network
            else -> SpeechAvailability.Unavailable
        }
    }

    override val touchExplorationEnabled: Boolean
        get() = context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

    override fun hasRecordPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
