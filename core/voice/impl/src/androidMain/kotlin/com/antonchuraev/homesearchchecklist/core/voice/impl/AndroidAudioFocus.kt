package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioFocusController
import com.antonchuraev.homesearchchecklist.core.voice.api.FocusChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * `AUDIOFOCUS_GAIN_TRANSIENT` for the length of a call: music pauses while the agent talks and listens. A ducking
 * loss (a navigation prompt) is ignored; a transient or permanent loss pauses the call, except the transient loss
 * caused by the call's own recognizer ([lendToRecognizer], [RecognizerFocusLedger]).
 *
 * One instance per call (Koin `factory`). Main thread only: the listener has no handler, so AudioManager calls it on
 * the main looper, and the call machine runs there too. API 26+ uses an [AudioFocusRequest] with `USAGE_ASSISTANT`;
 * API 24-25 (minSdk) the legacy stream request on `STREAM_MUSIC`, which the assistant usage maps to.
 */
internal class AndroidAudioFocus(context: Context) : AudioFocusController {

    private val manager = context.getSystemService(AudioManager::class.java)

    private val _changes = MutableSharedFlow<FocusChange>(extraBufferCapacity = 16)
    override val changes: Flow<FocusChange> = _changes

    private val ledger = RecognizerFocusLedger()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val system = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusChange.Gain
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LossTransient
            AudioManager.AUDIOFOCUS_LOSS -> FocusChange.Loss
            else -> null // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: keep talking
        }
        system?.let(ledger::onSystemChange)?.let(::report)
    }

    /** API 26+ only; `null` below. */
    private val request: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) buildRequest() else null

    /** The request is registered with the system (granted once and not abandoned since). */
    private var held = false

    @TargetApi(Build.VERSION_CODES.O)
    private fun buildRequest(): AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener(listener)
        .build()

    override fun acquire(): Boolean {
        val audio = manager ?: return false.also { Log.w(TAG, "AudioManager unavailable") }
        val result = if (request != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // A refused request leaves the registered one in the focus stack (the Gain after a phone call goes to it), so
        // a refusal does not clear [held]: release() must still abandon it.
        if (granted) held = true else Log.w(TAG, "audio focus refused ($result)")
        return granted
    }

    override fun release() {
        ledger.released()
        if (!held) return
        held = false
        val audio = manager ?: return
        if (request != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audio.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(listener)
        }
    }

    override fun lendToRecognizer() {
        ledger.lend()
    }

    override fun recognizerClosed() {
        ledger.close(reacquire = { held && acquire() })?.let(::report)
    }

    private fun report(change: FocusChange) {
        if (!_changes.tryEmit(change)) Log.e(TAG, "focus change $change dropped: buffer full")
    }

    private companion object {
        const val TAG = "VoiceAudioFocus"
    }
}
