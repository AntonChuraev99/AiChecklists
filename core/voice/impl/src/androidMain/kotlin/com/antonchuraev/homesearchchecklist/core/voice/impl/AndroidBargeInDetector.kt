package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInDetector
import com.antonchuraev.homesearchchecklist.core.voice.api.VadConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Voice barge-in while the agent speaks: `AudioRecord(VOICE_COMMUNICATION, 16 kHz, mono, PCM16)` with the platform
 * echo canceller and noise suppressor when the device has them, and the [EnergyVad] over 20 ms frames. The microphone
 * is open exactly while the flow is collected; the call machine never collects it together with the recognizer.
 *
 * Without the permission or a working AudioRecord the flow completes without emitting: tap barge-in still works.
 * Stateless between collections (Koin `single`).
 */
internal class AndroidBargeInDetector(private val context: Context) : BargeInDetector {

    @SuppressLint("MissingPermission") // checked right before opening the recorder
    override fun speechStarts(config: VadConfig): Flow<Unit> = flow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no RECORD_AUDIO: voice barge-in is off")
            return@flow
        }
        val frameSamples = SAMPLE_RATE * config.frameMs / MILLIS_PER_SECOND
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord buffer size unavailable ($minBuffer)")
            return@flow
        }
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frameSamples * 2 * BUFFER_FRAMES),
            )
        }.onFailure { Log.w(TAG, "AudioRecord could not be created", it) }.getOrNull() ?: return@flow
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord did not initialize")
            recorder.release()
            return@flow
        }
        val session = recorder.audioSessionId
        val echo = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.apply { enabled = true } else null
        val noise = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.apply { enabled = true } else null
        val vad = EnergyVad(config)
        val frame = ShortArray(frameSamples)
        // try/finally (not catch): the recorder must be released on cancel too — the recognizer opens the microphone
        // right after this flow ends.
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(frame, 0, frame.size)
                if (read < 0) {
                    Log.w(TAG, "AudioRecord.read failed: $read")
                    break
                }
                if (read > 0 && vad.offer(frame, read)) emit(Unit)
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            echo?.release()
            noise?.release()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "VoiceBargeIn"
        const val SAMPLE_RATE = 16_000
        const val MILLIS_PER_SECOND = 1000
        const val BUFFER_FRAMES = 4
    }
}
