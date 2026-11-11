package com.antonchuraev.homesearchchecklist.core.voice.impl.fakes

import com.antonchuraev.homesearchchecklist.core.voice.api.AudioFocusController
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRoute
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRouteMonitor
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInDetector
import com.antonchuraev.homesearchchecklist.core.voice.api.FocusChange
import com.antonchuraev.homesearchchecklist.core.voice.api.ListenOptions
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechInput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputStatus
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechSegmentOut
import com.antonchuraev.homesearchchecklist.core.voice.api.VadConfig
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.impl.RecognizerFocusLedger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

// Scriptable fakes of the platform audio boundaries (port of AiTeacher voice/fakes/FakeAudio.kt). Every fake records
// what it was asked and lets the test push what the platform would answer; none of them needs Android.
// For the DefaultVoiceCallEngine tests of this module.

/**
 * Recognizer: each [listen] reads the events the test [emit]s until a [SpeechEvent.Final] or [SpeechEvent.Error],
 * like one platform utterance. [listening] is `true` while a collector is active (the microphone is open).
 */
class FakeSpeechInput(
    var availabilityOf: (SpeechLocale) -> SpeechAvailability = { SpeechAvailability.OnDevice },
) : SpeechInput {
    data class ListenCall(val locale: SpeechLocale, val options: ListenOptions)

    private val pending = Channel<SpeechEvent>(Channel.UNLIMITED)
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    val listenCalls = mutableListOf<ListenCall>()
    var finishCalls = 0
        private set

    override suspend fun availability(locale: SpeechLocale): SpeechAvailability = availabilityOf(locale)

    override fun listen(locale: SpeechLocale, options: ListenOptions): Flow<SpeechEvent> = flow {
        listenCalls += ListenCall(locale, options)
        for (event in pending) {
            emit(event)
            if (event is SpeechEvent.Final || event is SpeechEvent.Error) break
        }
    }
        .onStart { _listening.value = true }
        .onCompletion { _listening.value = false }

    override fun finishUtterance() {
        finishCalls++
    }

    /** Queues the platform's next event(s) for the current or the next utterance. */
    fun emit(vararg events: SpeechEvent) {
        events.forEach { pending.trySend(it) }
    }

    /** A whole utterance: start of speech, one partial, the final text. */
    fun say(text: String, confidence: Float? = 0.9f, speechMs: Long = 1_200) = emit(
        SpeechEvent.Ready,
        SpeechEvent.SpeechStarted,
        SpeechEvent.Partial(text),
        SpeechEvent.Final(text, confidence, speechMs),
    )
}

/** TTS: records [speak] calls; the test drives progress with [emit] or [finish]. */
class FakeSpeechOutput(
    var prepareResult: (Set<SpeechLocale>) -> SpeechOutputStatus = { SpeechOutputStatus.Ready },
) : SpeechOutput {
    data class SpeakCall(val utteranceId: String, val segments: List<SpeechSegmentOut>, val rate: SpeechRate)

    private val _events = MutableSharedFlow<SpeechOutputEvent>(extraBufferCapacity = 64)
    override val events: Flow<SpeechOutputEvent> = _events

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()

    val prepared = mutableListOf<Set<SpeechLocale>>()
    val spoken = mutableListOf<SpeakCall>()
    var stopCalls = 0
        private set
    var released = false
        private set

    /** When set, [prepare] waits for it (an engine that never binds). */
    var prepareGate: CompletableDeferred<Unit>? = null

    override suspend fun prepare(locales: Set<SpeechLocale>): SpeechOutputStatus {
        prepared += locales
        prepareGate?.await()
        return prepareResult(locales)
    }

    override fun speak(utteranceId: String, segments: List<SpeechSegmentOut>, rate: SpeechRate) {
        spoken += SpeakCall(utteranceId, segments, rate)
    }

    override fun stop() {
        stopCalls++
    }

    override fun release() {
        released = true
    }

    fun emit(event: SpeechOutputEvent) {
        check(_events.tryEmit(event)) { "TTS event buffer is full" }
    }

    /** Started, the whole range, Done of [utteranceId] (default: the last spoken one). */
    fun finish(utteranceId: String = spoken.last().utteranceId) {
        val length = spoken.last { it.utteranceId == utteranceId }.segments.sumOf { it.text.length }
        emit(SpeechOutputEvent.Started(utteranceId))
        emit(SpeechOutputEvent.Range(utteranceId, length))
        emit(SpeechOutputEvent.Done(utteranceId))
    }

    fun setLevel(value: Float) {
        _level.value = value
    }
}

/**
 * Audio focus. [held] = the call has the focus right now: an acquire grants it, a release or a reported loss takes it
 * away, a Gain gives it back. [acquireResult] is the system's answer to every request (`false` = locked by a phone call).
 */
class FakeAudioFocusController(var acquireResult: Boolean = true) : AudioFocusController {
    private val _changes = MutableSharedFlow<FocusChange>(extraBufferCapacity = 16)
    override val changes: Flow<FocusChange> = _changes

    /** The same classification as the Android controller. */
    private val ledger = RecognizerFocusLedger()

    var held = false
        private set
    var acquireCalls = 0
        private set
    var releaseCalls = 0
        private set

    /** Recognizers lent the focus and not closed yet. */
    var lent = 0
        private set

    override fun acquire(): Boolean {
        acquireCalls++
        held = acquireResult
        return acquireResult
    }

    override fun release() {
        releaseCalls++
        held = false
        ledger.released()
    }

    override fun lendToRecognizer() {
        lent++
        ledger.lend()
    }

    override fun recognizerClosed() {
        lent--
        ledger.close(reacquire = ::acquire)?.let(::emit)
    }

    /** A focus change the controller reports (already classified as an interruption or a return). */
    fun emit(change: FocusChange) {
        held = change == FocusChange.Gain
        check(_changes.tryEmit(change)) { "focus buffer is full" }
    }

    /**
     * What the system tells the controller's listener, e.g. [FocusChange.LossTransient] when the platform recognizer
     * requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`; the controller decides whether it is reported.
     */
    fun systemChange(change: FocusChange) {
        held = change == FocusChange.Gain
        ledger.onSystemChange(change)?.let(::emit)
    }
}

class FakeAudioRouteMonitor(
    route: AudioRoute = AudioRoute.Speaker,
    override var echoCancelerAvailable: Boolean = true,
) : AudioRouteMonitor {
    private val _route = MutableStateFlow(route)
    override val route: StateFlow<AudioRoute> = _route.asStateFlow()

    fun setRoute(value: AudioRoute) {
        _route.value = value
    }
}

/** Barge-in VAD: [active] is `true` while collected (AudioRecord open); [speak] reports a start of speech. */
class FakeBargeInDetector : BargeInDetector {
    private val starts = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    val configs = mutableListOf<VadConfig>()

    override fun speechStarts(config: VadConfig): Flow<Unit> = starts
        .onStart {
            configs += config
            _active.value = true
        }
        .onCompletion { _active.value = false }

    fun speak() {
        check(starts.tryEmit(Unit)) { "barge-in buffer is full" }
    }
}

class FakeVoiceCapabilities(
    var recognizerOf: (SpeechLocale) -> SpeechAvailability = { SpeechAvailability.OnDevice },
    override var touchExplorationEnabled: Boolean = false,
    var recordPermission: Boolean = true,
    override var isCallSupported: Boolean = true,
) : VoiceCapabilities {
    override suspend fun recognizer(locale: SpeechLocale): SpeechAvailability = recognizerOf(locale)

    override fun hasRecordPermission(): Boolean = recordPermission
}
