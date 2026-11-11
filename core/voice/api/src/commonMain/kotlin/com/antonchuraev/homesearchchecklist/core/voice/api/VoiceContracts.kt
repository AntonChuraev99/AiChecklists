package com.antonchuraev.homesearchchecklist.core.voice.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Platform and transport boundaries of the voice call (port of AiTeacher domain/voice/VoiceContracts.kt).
// Audio implementations: :core:voice:impl androidMain. Transport (VoiceTurnGateway, VoiceSessionGateway): the
// feature layer over chat_agent. Test fakes: :core:voice:impl commonTest (voice/fakes).

/**
 * Platform speech recognizer. One instance per call: [finishUtterance] stops the utterance of this instance only.
 *
 * Invariant: one recognizer per utterance, created and destroyed on the main thread; the call never collects
 * [listen] together with [BargeInDetector.speechStarts] (one microphone at a time).
 */
interface SpeechInput {
    /**
     * Where recognition of [locale] runs on this device. Also decides the mode of the next [listen] of that
     * language (on-device or the system service); [listen] corrects it at runtime when on-device turns it down.
     */
    suspend fun availability(locale: SpeechLocale): SpeechAvailability

    /**
     * One utterance: collecting opens the microphone, the flow ends after its [SpeechEvent.Final] or
     * [SpeechEvent.Error]; cancelling the collection cancels the recognition.
     */
    fun listen(locale: SpeechLocale, options: ListenOptions = ListenOptions()): Flow<SpeechEvent>

    /** Ends the current utterance now (a tap, or [ListenOptions.manualEndOfTurn]). */
    fun finishUtterance()
}

/**
 * Platform TTS. One instance per call: [release] shuts the engine down, a later [prepare] binds a new one.
 * Spoken with the assistant audio usage; segments queue one after another, each in its own voice.
 */
interface SpeechOutput {
    /** Binds the engine and checks the voices of [locales]. */
    suspend fun prepare(locales: Set<SpeechLocale>): SpeechOutputStatus

    /** Queues [segments] as one utterance [utteranceId]; progress comes through [events]. */
    fun speak(utteranceId: String, segments: List<SpeechSegmentOut>, rate: SpeechRate)

    /** Stops speaking and drops the queue. */
    fun stop()

    /** Progress of every [speak]: Started, Range (spoken characters, for the caption and the cut point), Done/Error. */
    val events: Flow<SpeechOutputEvent>

    /** Output level in `0..1` for the orb while speaking. */
    val level: StateFlow<Float>

    /** Shuts the engine down; the instance may be prepared again. */
    fun release()
}

/**
 * The call's audio focus (`AUDIOFOCUS_GAIN_TRANSIENT` on Android, from start to end of the call). [changes] reports
 * interruptions only: a transient loss caused by the call's own recognizer is not one (see [lendToRecognizer]).
 * One instance per call; main thread only.
 */
interface AudioFocusController {
    /** Requests the focus; `false` when the system refused it (a phone call holds it). */
    fun acquire(): Boolean

    /** Abandons the focus request. */
    fun release()

    /**
     * The platform recognizer is about to open. It requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` itself, so until the
     * matching [recognizerClosed] — and for the first transient loss after it, since the recognizer service may ask
     * for the focus only after the client closed it — a transient loss is the call's own microphone: [changes] does
     * not report it and the request stays registered, so the system gives the focus back when the recognizer abandons
     * it. A permanent loss is still reported. Calls nest: every [lendToRecognizer] has one [recognizerClosed].
     */
    fun lendToRecognizer()

    /**
     * The recognizer of [lendToRecognizer] closed. When the focus went away while lent and has not come back, it is
     * requested again: a refusal (the focus is locked by a phone call that came while the recognizer held it) is
     * reported as [FocusChange.LossTransient], and the system's [FocusChange.Gain] follows when the call ends.
     */
    fun recognizerClosed()

    /** Interruptions and returns of the focus, already classified (the recognizer's own losses filtered out). */
    val changes: Flow<FocusChange>
}

/** Where the agent's voice goes now; decides whether voice barge-in is safe. Process-wide. */
interface AudioRouteMonitor {
    /** The current output route; headphones of any kind win over the speaker. */
    val route: StateFlow<AudioRoute>

    /** The device has an acoustic echo canceller: voice barge-in on the speaker needs it. */
    val echoCancelerAvailable: Boolean
}

/**
 * Voice barge-in while the agent speaks: collecting opens the microphone (voice-communication source with echo
 * cancellation and noise suppression), each emission is a start of speech. Without the permission or a working
 * recorder the flow completes without emitting (tap barge-in still works). Never collected together with
 * [SpeechInput.listen].
 */
interface BargeInDetector {
    fun speechStarts(config: VadConfig = VadConfig()): Flow<Unit>
}

/** What the device can do for a call before one starts (entry points, gates). */
interface VoiceCapabilities {
    /**
     * The platform runs voice calls at all. `false` on web and iOS: the entry point is hidden there, the call is
     * Android only.
     */
    val isCallSupported: Boolean

    /** A quick per-language answer; the call itself asks [SpeechInput.availability] for the exact language. */
    suspend fun recognizer(locale: SpeechLocale): SpeechAvailability

    /** TalkBack is on: manual end of turn, no voice barge-in. */
    val touchExplorationEnabled: Boolean

    /** The microphone permission is granted. */
    fun hasRecordPermission(): Boolean
}

/**
 * One voice turn of the agent: what the user said goes to the agent (in Gisti — a `chat_agent` turn with its tools),
 * the reply comes back as checked sentences to speak, plus what the agent did in the user's data.
 *
 * Implemented by the feature layer over the agent transport. The implementation keeps the conversation of
 * [VoiceTurnRequest.sessionId] (history sent to the agent), and must be idempotent by [VoiceTurnRequest.clientTurnId]:
 * the engine retries a failed turn with the same id, and the agent's tools must not run twice.
 */
interface VoiceTurnGateway {
    /**
     * Runs the turn. Emits any number of [VoiceTurnEvent.Status] and [VoiceTurnEvent.Say] (when
     * [VoiceTurnRequest.stream]), then exactly one [VoiceTurnEvent.Done] or [VoiceTurnEvent.Failed], and completes.
     * Never throws: a failure is the final [VoiceTurnEvent.Failed]. Cancelling the collection abandons the turn.
     */
    fun turn(request: VoiceTurnRequest): Flow<VoiceTurnEvent>
}

/**
 * The server session of a call: minutes, heartbeat, kill switch. The server clock counts the minutes; the client
 * only reports. Every call returns a [VoiceResult], never throws.
 */
interface VoiceSessionGateway {
    /** Opens the session; [VoiceSessionStart.LimitReached] / [VoiceSessionStart.Disabled] mean the call does not start. */
    suspend fun start(r: VoiceSessionStartRequest): VoiceResult<VoiceSessionStart>

    /** Keeps the session alive every `heartbeatSec`; [VoiceHeartbeat.ended] = the server closed it (minutes ran out). */
    suspend fun heartbeat(id: VoiceSessionId, elapsedSec: Int): VoiceResult<VoiceHeartbeat>

    /** Closes the session. Idempotent by session id: a repeated end returns the same answer. */
    suspend fun end(r: VoiceSessionEndRequest): VoiceResult<VoiceSessionEnd>

    /** Minutes left, for the entry point before a call. */
    suspend fun quota(): VoiceResult<VoiceQuota>
}
