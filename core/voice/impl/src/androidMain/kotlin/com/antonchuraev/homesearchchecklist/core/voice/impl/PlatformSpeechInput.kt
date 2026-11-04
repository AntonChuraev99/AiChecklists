package com.antonchuraev.homesearchchecklist.core.voice.impl

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.antonchuraev.homesearchchecklist.core.voice.api.ListenOptions
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechErrorKind
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechInput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Platform `SpeechRecognizer`: the on-device recognizer when the device has one for the language
 * (`createOnDeviceSpeechRecognizer`, API 31+; the language check needs API 33+), otherwise the system recognition
 * service (may send audio to its provider — only [SpeechAvailability.OnDevice] may be shown as "stays on the phone").
 * When on-device refuses the language at runtime, the utterance restarts on the system recognizer and the language
 * stays there for the rest of the call.
 *
 * One recognizer per utterance, created and destroyed on the main thread as the API requires. One instance per call
 * (Koin `factory`): [finishUtterance] stops the utterance of this instance only.
 */
internal class PlatformSpeechInput(private val context: Context) : SpeechInput {

    private val main = Handler(Looper.getMainLooper())

    /** Where each language goes: decided by [availability], corrected at runtime when on-device turns it down. */
    private val modes = ConcurrentHashMap<String, SpeechAvailability>()

    /** Main thread only. */
    private var current: SpeechRecognizer? = null

    override suspend fun availability(locale: SpeechLocale): SpeechAvailability {
        val mode = withContext(Dispatchers.Main.immediate) { resolveAvailability(locale) }
        modes[locale.tag] = mode
        return mode
    }

    private suspend fun resolveAvailability(locale: SpeechLocale): SpeechAvailability {
        val system = SpeechRecognizer.isRecognitionAvailable(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            val onDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) onDeviceSupports(locale) else true
            if (onDevice) return SpeechAvailability.OnDevice
        }
        return if (system) SpeechAvailability.Network else SpeechAvailability.Unavailable
    }

    /** API 33+: the on-device recognizer has the language installed (a pending download does not count). */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun onDeviceSupports(locale: SpeechLocale): Boolean {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val support = withTimeoutOrNull(SUPPORT_CHECK_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                recognizer.checkRecognitionSupport(
                    recognizeIntent(locale, ListenOptions()),
                    context.mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                            if (continuation.isActive) continuation.resume(recognitionSupport)
                        }

                        override fun onError(error: Int) {
                            Log.w(TAG, "checkRecognitionSupport(${locale.tag}) failed: $error")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        }
        recognizer.destroy()
        if (support == null) Log.w(TAG, "checkRecognitionSupport(${locale.tag}) gave no answer: not on-device")
        val language = locale.language
        return support?.installedOnDeviceLanguages.orEmpty().any { it.substringBefore('-').lowercase() == language }
    }

    override fun listen(locale: SpeechLocale, options: ListenOptions): Flow<SpeechEvent> = callbackFlow {
        var speechStartedAt = 0L
        var onDevice = modes[locale.tag] == SpeechAvailability.OnDevice
        var recognizer: SpeechRecognizer? = null

        fun begin() {
            val created = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizer = created
            current = created
            created.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SpeechEvent.Ready)
                }

                override fun onBeginningOfSpeech() {
                    speechStartedAt = SystemClock.elapsedRealtime()
                    trySend(SpeechEvent.SpeechStarted)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SpeechEvent.Level(((rmsdB - RMS_MIN_DB) / (RMS_MAX_DB - RMS_MIN_DB)).coerceIn(0f, 1f)))
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    val kind = error.toKind()
                    // Analytics sees only the kind; several platform codes map to one (2, 1, 4, 11 -> Network).
                    Log.w(TAG, "recognizer error $error -> $kind (onDevice=$onDevice)")
                    if (onDevice && kind == SpeechErrorKind.LanguageUnavailable) {
                        // The on-device model lacks the language: the system recognizer takes this and later utterances.
                        Log.w(TAG, "on-device recognizer refused ${locale.tag} ($error), using the system recognizer")
                        modes[locale.tag] = SpeechAvailability.Network
                        onDevice = false
                        created.destroy()
                        begin()
                        return
                    }
                    trySend(SpeechEvent.Error(kind))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val text = texts.firstOrNull()
                    if (text.isNullOrBlank()) {
                        trySend(SpeechEvent.Error(SpeechErrorKind.NoMatch))
                    } else {
                        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                            ?.firstOrNull()
                            ?.takeIf { it >= 0f } // -1 = the engine did not report one
                        val speechMs = if (speechStartedAt > 0) SystemClock.elapsedRealtime() - speechStartedAt else 0L
                        trySend(SpeechEvent.Final(text, confidence, speechMs))
                    }
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            created.startListening(recognizeIntent(locale, options.copy(preferOffline = options.preferOffline && !onDevice)))
        }

        begin()
        awaitClose {
            recognizer?.let {
                it.cancel()
                it.destroy()
            }
            if (current === recognizer) current = null
        }
    }.flowOn(Dispatchers.Main.immediate)

    override fun finishUtterance() {
        main.post { current?.stopListening() }
    }

    private fun recognizeIntent(locale: SpeechLocale, options: ListenOptions): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, options.partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (options.preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            if (options.manualEndOfTurn) {
                // TalkBack: the user ends the turn with a tap; engines may still cap the silence.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, MANUAL_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, MANUAL_SILENCE_MS)
            }
            // Language detection between the call language and the extra ones exists from API 34
            // (RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && options.alsoAllow.isNotEmpty()) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    ArrayList(listOf(locale.tag) + options.alsoAllow.map { it.tag }),
                )
            }
        }

    private fun Int.toKind(): SpeechErrorKind = when (this) {
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechErrorKind.NoMatch
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorKind.SpeechTimeout
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        ERROR_SERVER_DISCONNECTED,
        -> SpeechErrorKind.Network
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorKind.Permission
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorKind.Busy
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechErrorKind.LanguageUnavailable
        SpeechRecognizer.ERROR_CLIENT -> SpeechErrorKind.Client
        else -> SpeechErrorKind.Other // ERROR_AUDIO, ERROR_TOO_MANY_REQUESTS, codes newer than this app
    }

    private companion object {
        const val TAG = "VoiceSpeechInput"

        /** `onRmsChanged` reports roughly -2..10 dB on the platform recognizers. */
        const val RMS_MIN_DB = -2f
        const val RMS_MAX_DB = 10f
        const val MANUAL_SILENCE_MS = 10_000L

        // API 31 codes, inlined so the mapping also compiles into older paths.
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13

        val SUPPORT_CHECK_TIMEOUT = 3.seconds
    }
}
