package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.AgentUtterance
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioFocusController
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRoute
import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRouteMonitor
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInDetector
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInMethod
import com.antonchuraev.homesearchchecklist.core.voice.api.CallCommand
import com.antonchuraev.homesearchchecklist.core.voice.api.CallConfig
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.CallStop
import com.antonchuraev.homesearchchecklist.core.voice.api.ClientTurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.DropCause
import com.antonchuraev.homesearchchecklist.core.voice.api.FocusChange
import com.antonchuraev.homesearchchecklist.core.voice.api.InterruptedTurn
import com.antonchuraev.homesearchchecklist.core.voice.api.ListenOptions
import com.antonchuraev.homesearchchecklist.core.voice.api.PauseReason
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechErrorKind
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechInput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutput
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputStatus
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechSegmentOut
import com.antonchuraev.homesearchchecklist.core.voice.api.ThinkingDelay
import com.antonchuraev.homesearchchecklist.core.voice.api.TurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.UnavailableReason
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEngine
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallError
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceResult
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndRequest
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStart
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStartRequest
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What the call machine talks to. One bundle per call: [speechInput], [speechOutput] and [audioFocus] hold platform
 * objects of this call, the rest is shared.
 *
 * @property turns the agent turn transport of the feature layer (idempotent by client turn id).
 * @property sessions the server session of the feature layer (minutes, heartbeat, kill switch).
 * @property isOnline the device has a network; drives Reconnecting. A source that is always `true` keeps the call
 *   working without one: a failed turn still retries every [DefaultVoiceCallEngine] reconnect spacing.
 * @property nowMs a monotonic clock in milliseconds (latency facts and the background timeout, never wall time).
 * @property newId uuid v4 of client turn ids and voice sessions.
 */
internal class VoiceCallDependencies(
    val speechInput: SpeechInput,
    val speechOutput: SpeechOutput,
    val audioFocus: AudioFocusController,
    val audioRoute: AudioRouteMonitor,
    val bargeIn: BargeInDetector,
    val capabilities: VoiceCapabilities,
    val turns: VoiceTurnGateway,
    val sessions: VoiceSessionGateway,
    val isOnline: Flow<Boolean> = flowOf(true),
    val nowMs: () -> Long = monotonicMillis(),
    val newId: () -> String = ::randomUuid,
)

private fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomUuid(): String = Uuid.random().toString()

/**
 * The call machine (port of AiTeacher `DefaultVoiceCallEngine`): platform STT -> one agent turn of [VoiceTurnGateway]
 * -> checked sentences -> platform TTS, with the session minutes of [VoiceSessionGateway].
 *
 * Threading: [scope] must be confined to one thread (the main dispatcher in the app, the test dispatcher in tests);
 * every field below is touched only from it, platform callbacks arrive through flows collected in [scope].
 *
 * Invariants:
 * - the recognizer and the barge-in microphone never run at the same time: listening starts only after the barge-in
 *   collector has finished (`micRelease.join()`);
 * - every recognizer utterance is lent the audio focus ([AudioFocusController.lendToRecognizer]): the platform
 *   recognizer takes `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` itself, and that loss must not pause the call;
 * - a [PauseReason.SystemInterruption] keeps the focus request registered, so the system's Gain resumes the call;
 * - a turn that was cut (barge-in, cancel, pause) is never spoken again, but its collection is not cancelled: the
 *   server finishes it, its [VoiceTurnEvent.Done] still counts and its actions still reach [CallEngineState.actions],
 *   and the cut goes with the next turn or the session end as [InterruptedTurn];
 * - the caption ([AgentUtterance.text]) holds only what was handed to TTS.
 */
internal class DefaultVoiceCallEngine(
    private val scope: CoroutineScope,
    private val config: CallConfig,
    private val deps: VoiceCallDependencies,
) : VoiceCallEngine {

    private val _state = MutableStateFlow(CallEngineState(rate = config.rate))
    override val state: StateFlow<CallEngineState> = _state.asStateFlow()

    private val eventChannel = Channel<CallEngineEvent>(Channel.UNLIMITED)
    override val events: Flow<CallEngineEvent> = eventChannel.receiveAsFlow()

    private val _levels = MutableStateFlow(0f)
    override val levels: StateFlow<Float> = _levels.asStateFlow()

    private val listenLocale: SpeechLocale = config.listenLocale
    private val alsoAllow: List<SpeechLocale> = config.alsoAllow.filter { it != listenLocale }

    private var stream = false
    private var heartbeatSec = DEFAULT_HEARTBEAT_SEC
    private var online = true
    private var connected = false
    private var ended = false
    private var awaitingPermission = false
    private var pauseOnConnect = false
    private var startedSessionId: VoiceSessionId? = null
    private var pausedAtMs: Long? = null
    private var limitPending: CallStop? = null
    private var consecutiveErrors = 0
    private var consecutiveSttErrors = 0

    private var currentTurn: TurnRun? = null
    private var playback: Playback? = null
    private var lastSentences: List<List<SpeechSegmentOut>> = emptyList()
    private var pendingInterrupted: InterruptedTurn? = null
    private var eosAtMs: Long? = null

    private var connectJob: Job? = null
    private var listenJob: Job? = null
    private var bargeJob: Job? = null
    private var micRelease: Job? = null
    private var levelJob: Job? = null
    private var clockJob: Job? = null
    private var thinkingJob: Job? = null
    private var reconnectJob: Job? = null
    private var afterInterruptJob: Job? = null
    private val collectorJobs = mutableListOf<Job>()

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            // The screen is gone: nothing may keep talking or holding the microphone (the server closes the session
            // after 45 s without a heartbeat).
            deps.speechOutput.stop()
            deps.speechOutput.release()
            deps.audioFocus.release()
        }
    }

    override fun dispatch(command: CallCommand) {
        if (ended) return
        when (command) {
            CallCommand.Start -> start()
            CallCommand.PermissionGranted -> if (awaitingPermission) start()
            is CallCommand.Interrupt -> interrupt(command.method)
            CallCommand.FinishUtterance -> if (_state.value.phase is CallPhase.Listening) deps.speechInput.finishUtterance()
            CallCommand.CancelThinking -> if (_state.value.phase is CallPhase.Thinking) cancelThinking()
            is CallCommand.SetMuted -> setMuted(command.muted)
            CallCommand.Repeat -> repeat()
            is CallCommand.SetRate -> _state.update { it.copy(rate = command.rate) }
            CallCommand.ScreenStopped -> screenStopped()
            CallCommand.ScreenStarted -> screenStarted()
            CallCommand.Resume -> resume()
            CallCommand.HangUp -> endCall(CallEndReason.HangUp, CallStop.Ended(CallEndReason.HangUp))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Start
    // ---------------------------------------------------------------------------------------------

    private fun start() {
        val current = _state.value
        if (current.phase != CallPhase.Idle || current.stop != null || connectJob != null) return
        if (!deps.capabilities.hasRecordPermission()) {
            // The screen asks for the permission and sends PermissionGranted.
            awaitingPermission = true
            return
        }
        awaitingPermission = false
        setPhase(CallPhase.Connecting)
        val sessionId = VoiceSessionId(deps.newId())
        connectJob = scope.launch {
            val finished = withTimeoutOrNull(CONNECT_TIMEOUT) { connect(sessionId) }
            if (finished == null) failConnect(CallStop.Dropped(DropCause.ConnectTimeout), "connect_timeout")
        }
    }

    private suspend fun connect(sessionId: VoiceSessionId) = coroutineScope {
        val isOnline = withTimeoutOrNull(ONLINE_PROBE) { deps.isOnline.first() } ?: true
        if (!isOnline) {
            failConnect(CallStop.Unavailable(UnavailableReason.Offline), code = null)
            return@coroutineScope
        }
        val stt = async { deps.speechInput.availability(listenLocale) }
        val tts = async { deps.speechOutput.prepare(voicesToPrepare()) }
        val session = async {
            val request = VoiceSessionStartRequest(
                sessionId = sessionId,
                entry = config.entry,
                listenLocale = listenLocale,
                sttOnDevice = stt.await() == SpeechAvailability.OnDevice,
            )
            deps.sessions.start(request).also { result ->
                if (result is VoiceResult.Success && result.value is VoiceSessionStart.Started) startedSessionId = sessionId
            }
        }
        val sttAvailability = stt.await()
        val ttsStatus = tts.await()
        val sessionResult = session.await()
        when {
            sttAvailability == SpeechAvailability.Unavailable ->
                failConnect(CallStop.Unavailable(UnavailableReason.NoRecognizer), code = null)
            ttsStatus !is SpeechOutputStatus.Ready -> failConnect(CallStop.Unavailable(UnavailableReason.NoVoice), code = null)
            sessionResult is VoiceResult.Failure -> failConnect(sessionFailureStop(sessionResult.error), sessionResult.error.code)
            sessionResult is VoiceResult.Success -> when (val start = sessionResult.value) {
                VoiceSessionStart.Disabled -> failConnect(CallStop.Unavailable(UnavailableReason.FlagOff), code = null)
                is VoiceSessionStart.LimitReached -> {
                    failConnect(CallStop.LimitReached(start.quota, duringCall = false), code = null)
                    emit(CallEngineEvent.LimitReached(start.quota.plan, duringCall = false))
                }
                is VoiceSessionStart.Started -> onConnected(start, sttAvailability)
            }
        }
    }

    /** The voice of the call and of every language a reply may switch to. */
    private fun voicesToPrepare(): Set<SpeechLocale> = buildSet {
        add(listenLocale)
        addAll(alsoAllow)
    }

    private fun sessionFailureStop(error: VoiceCallError): CallStop = when {
        error is VoiceCallError.NotEntitled -> CallStop.Unavailable(UnavailableReason.NotEntitled)
        error is VoiceCallError.OutOfCredits -> CallStop.Unavailable(UnavailableReason.OutOfCredits)
        !online -> CallStop.Dropped(DropCause.Offline)
        else -> CallStop.Dropped(DropCause.Error)
    }

    /** The call never got going: no `Ended`; a session that did start is closed quietly. */
    private fun failConnect(stop: CallStop, code: String?) {
        if (ended) return
        ended = true
        startedSessionId?.let { id ->
            scope.launch {
                deps.sessions.end(
                    VoiceSessionEndRequest(id, CallEndReason.Error, elapsedSec = 0, turns = 0, interruptions = 0, interrupted = null),
                )
            }
        }
        if (stop is CallStop.Dropped && code != null) emit(CallEngineEvent.Error(stage = "session", code = code))
        _state.update { it.copy(phase = CallPhase.Idle, stop = stop) }
        deps.speechOutput.release()
        connectJob?.cancel() // HangUp while connecting; a no-op when called from the connect coroutine at its end
    }

    private fun onConnected(start: VoiceSessionStart.Started, stt: SpeechAvailability) {
        if (ended) return
        connected = true
        stream = config.streamEnabled && start.stream
        heartbeatSec = start.heartbeatSec.coerceAtLeast(MIN_HEARTBEAT_SEC)
        _state.update {
            it.copy(
                sessionId = start.sessionId,
                quota = start.quota,
                stt = stt,
                lastMinute = start.quota.secondsLeft <= LAST_MINUTE_SEC,
            )
        }
        emit(CallEngineEvent.Started(stt, start.quota, stream))
        startCollectors()
        if (pauseOnConnect) {
            pausedAtMs = deps.nowMs()
            setPhase(CallPhase.Paused(PauseReason.Background))
            emit(CallEngineEvent.Paused(PauseReason.Background))
            return
        }
        deps.audioFocus.acquire()
        startClock()
        startListening(afterInterrupt = false)
    }

    private fun startCollectors() {
        collectorJobs += scope.launch { deps.speechOutput.events.collect(::onTtsEvent) }
        collectorJobs += scope.launch { deps.audioFocus.changes.collect(::onFocus) }
        collectorJobs += scope.launch {
            deps.isOnline.collect { isOnline ->
                online = isOnline
                if (!isOnline) onOffline()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Clock, heartbeat, limits
    // ---------------------------------------------------------------------------------------------

    private fun startClock() {
        clockJob?.cancel()
        clockJob = scope.launch {
            while (isActive) {
                delay(1.seconds)
                val elapsed = _state.value.elapsedSec + 1
                _state.update { it.copy(elapsedSec = elapsed) }
                if (elapsed % heartbeatSec == 0) launch { heartbeat(elapsed) }
                val soft = config.softLimitSec
                if (soft != null && elapsed >= soft) requestLimitEnd(CallStop.Ended(CallEndReason.Limit))
            }
        }
    }

    private suspend fun heartbeat(elapsed: Int) {
        val sessionId = _state.value.sessionId ?: return
        when (val result = deps.sessions.heartbeat(sessionId, elapsed)) {
            is VoiceResult.Success -> {
                val quota = result.value.quota
                _state.update { it.copy(quota = quota, lastMinute = quota.secondsLeft <= LAST_MINUTE_SEC) }
                if (result.value.ended || quota.secondsLeft <= 0) requestLimitEnd(CallStop.LimitReached(quota, duringCall = true))
            }
            // A lost heartbeat is not charged; the next one or the end call settles the minutes.
            is VoiceResult.Failure -> emit(CallEngineEvent.Error(stage = "session", code = result.error.code))
        }
    }

    /** The agent finishes the sentence being spoken, then the call ends (heartbeat `ended`, [CallConfig.softLimitSec]). */
    private fun requestLimitEnd(stop: CallStop) {
        if (ended || limitPending != null) return
        limitPending = stop
        if (_state.value.phase == CallPhase.Speaking) return
        finishLimit()
    }

    private fun finishLimit() {
        val stop = limitPending ?: return
        endCall(CallEndReason.Limit, stop)
    }

    // ---------------------------------------------------------------------------------------------
    // Listening
    // ---------------------------------------------------------------------------------------------

    private fun startListening(afterInterrupt: Boolean) {
        if (ended) return
        if (limitPending != null) {
            finishLimit()
            return
        }
        cancelReconnect()
        cancelListening()
        stopBargeIn()
        stopOutputLevel()
        if (!online) {
            waitForNetwork()
            return
        }
        if (_state.value.muted) {
            _state.update { it.copy(phase = CallPhase.Muted, heard = null, heardFinal = false) }
            return
        }
        _state.update {
            it.copy(phase = CallPhase.Listening(hearing = false, afterInterrupt = afterInterrupt), heard = null, heardFinal = false)
        }
        _levels.value = 0f
        afterInterruptJob?.cancel()
        if (afterInterrupt) {
            afterInterruptJob = scope.launch {
                delay(AFTER_INTERRUPT_WINDOW)
                _state.update { s ->
                    val phase = s.phase
                    if (phase is CallPhase.Listening && phase.afterInterrupt) s.copy(phase = phase.copy(afterInterrupt = false)) else s
                }
            }
        }
        val waitForMic = micRelease
        val options = ListenOptions(manualEndOfTurn = config.manualEndOfTurn, alsoAllow = alsoAllow)
        listenJob = scope.launch {
            waitForMic?.join() // the barge-in recorder is released before the recognizer opens the microphone
            // The recognizer takes the focus for its utterance itself: that loss is not an interruption. No suspension
            // between the two calls, so every lend has its close, on cancellation too.
            deps.audioFocus.lendToRecognizer()
            deps.speechInput.listen(listenLocale, options)
                .onCompletion { deps.audioFocus.recognizerClosed() }
                .collect(::onSpeech)
        }
    }

    private fun cancelListening() {
        listenJob?.cancel()
        listenJob = null
        afterInterruptJob?.cancel()
    }

    private fun onSpeech(event: SpeechEvent) {
        if (ended) return
        when (event) {
            SpeechEvent.Ready -> Unit
            SpeechEvent.SpeechStarted -> setHearing()
            is SpeechEvent.Level -> if (_state.value.phase is CallPhase.Listening) _levels.value = event.normalized.coerceIn(0f, 1f)
            is SpeechEvent.Partial -> {
                setHearing()
                _state.update { it.copy(heard = event.text, heardFinal = false) }
            }
            is SpeechEvent.Final -> onFinal(event)
            is SpeechEvent.Error -> onSpeechError(event.kind)
        }
    }

    private fun setHearing() = _state.update { s ->
        val phase = s.phase
        if (phase is CallPhase.Listening && !phase.hearing) s.copy(phase = phase.copy(hearing = true)) else s
    }

    private fun onFinal(final: SpeechEvent.Final) {
        val text = final.text.trim().take(MAX_TURN_CHARS)
        if (text.isEmpty()) {
            emit(CallEngineEvent.TurnCaptured(final.speechMs, chars = 0, outcome = "empty"))
            startListening(afterInterrupt = false)
            return
        }
        consecutiveSttErrors = 0
        eosAtMs = deps.nowMs()
        listenJob = null
        _levels.value = 0f
        emit(CallEngineEvent.TurnCaptured(final.speechMs, text.length, outcome = "ok"))
        _state.update { it.copy(heard = text, heardFinal = true) }
        sendTurn(text, final.confidence)
    }

    private fun onSpeechError(kind: SpeechErrorKind) {
        when (kind) {
            SpeechErrorKind.NoMatch, SpeechErrorKind.SpeechTimeout -> {
                // The user was silent: the recognizer listened to the end, so the errors before it are not "in a row"
                // any more. Silence never ends the call.
                consecutiveSttErrors = 0
                val outcome = if (kind == SpeechErrorKind.NoMatch) "no_match" else "timeout"
                emit(CallEngineEvent.TurnCaptured(speechMs = 0, chars = 0, outcome = outcome))
                startListening(afterInterrupt = false)
            }
            SpeechErrorKind.LanguageUnavailable -> {
                emit(CallEngineEvent.Error(stage = "stt", code = kind.code()))
                endCall(CallEndReason.Error, CallStop.Unavailable(UnavailableReason.NoRecognizer))
            }
            SpeechErrorKind.Permission -> {
                emit(CallEngineEvent.Error(stage = "stt", code = kind.code()))
                endCall(CallEndReason.Error, CallStop.Dropped(DropCause.Error))
            }
            SpeechErrorKind.Network, SpeechErrorKind.Busy, SpeechErrorKind.Client, SpeechErrorKind.Other -> {
                emit(CallEngineEvent.Error(stage = "stt", code = kind.code()))
                _state.update { it.copy(errors = it.errors + 1) }
                consecutiveSttErrors++
                if (consecutiveSttErrors >= MAX_CONSECUTIVE_ERRORS) {
                    endCall(CallEndReason.Error, CallStop.Dropped(if (online) DropCause.Error else DropCause.Offline))
                    return
                }
                listenJob = scope.launch {
                    delay(STT_RETRY_DELAY)
                    startListening(afterInterrupt = false)
                }
            }
        }
    }

    private fun setMuted(muted: Boolean) {
        _state.update { it.copy(muted = muted) }
        val phase = _state.value.phase
        if (muted) {
            stopBargeIn()
            if (phase is CallPhase.Listening) {
                cancelListening()
                _state.update { it.copy(phase = CallPhase.Muted, heard = null, heardFinal = false) }
                _levels.value = 0f
            }
        } else if (phase == CallPhase.Muted) {
            startListening(afterInterrupt = false)
        } else if (phase == CallPhase.Speaking) {
            startBargeIn()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Turns
    // ---------------------------------------------------------------------------------------------

    private fun sendTurn(text: String, confidence: Float?) {
        cancelListening()
        stopBargeIn()
        val now = deps.nowMs()
        val run = TurnRun(ClientTurnId(deps.newId()), firstSentAt = now, eosAt = eosAtMs ?: now)
        eosAtMs = null
        val interrupted = pendingInterrupted
        pendingInterrupted = null
        currentTurn = run
        _levels.value = 0f
        setPhase(CallPhase.Thinking(ThinkingDelay.Normal))
        startThinkingTimer(run)
        val sessionId = _state.value.sessionId ?: return
        // Built once: every retry of this turn sends the same request, with the same client turn id.
        val request = VoiceTurnRequest(
            clientTurnId = run.clientTurnId,
            sessionId = sessionId,
            text = text,
            listenLocale = listenLocale,
            uiLocale = config.uiLocale,
            sttConfidence = confidence,
            sttOnDevice = _state.value.stt == SpeechAvailability.OnDevice,
            interrupted = interrupted,
            stream = stream,
        )
        run.job = scope.launch { execute(run, request) }
    }

    private fun startThinkingTimer(run: TurnRun) {
        thinkingJob?.cancel()
        thinkingJob = scope.launch {
            delay(SLOW_THINKING)
            if (currentTurn === run) updateThinking { it.copy(delay = ThinkingDelay.Slow) }
            delay(VERY_SLOW_THINKING - SLOW_THINKING)
            if (currentTurn === run) updateThinking { it.copy(delay = ThinkingDelay.VerySlow) }
        }
    }

    /** Changes the Thinking phase in place (delay or tool); any other phase is left as it is. */
    private fun updateThinking(change: (CallPhase.Thinking) -> CallPhase.Thinking) = _state.update { s ->
        val phase = s.phase
        if (phase is CallPhase.Thinking) s.copy(phase = change(phase)) else s
    }

    /** Runs [run] until it is answered, given up or cut; retries keep the same client turn id (idempotency). */
    private suspend fun execute(run: TurnRun, request: VoiceTurnRequest) {
        while (true) {
            var failure: VoiceCallError? = null
            deps.turns.turn(request).collect { event ->
                if (event is VoiceTurnEvent.Failed) failure = event.error else onTurnEvent(run, event)
            }
            val error = failure ?: return
            if (run.discarded || ended) return
            when {
                error is VoiceCallError.TurnInProgress && deps.nowMs() - run.firstSentAt < TURN_IN_PROGRESS_WINDOW.inWholeMilliseconds ->
                    delay(TURN_IN_PROGRESS_RETRY)
                error is VoiceCallError.TurnInProgress -> {
                    turnError(run, error)
                    return
                }
                error.isRetryable -> {
                    emit(CallEngineEvent.Error(stage = if (online) "llm" else "network", code = error.code))
                    if (reconnectJob == null) startReconnecting(retrying = true)
                    delay(RECONNECT_RETRY_SPACING)
                    deps.isOnline.first { it }
                }
                else -> {
                    onTurnRejected(run, error)
                    return
                }
            }
        }
    }

    private fun onTurnEvent(run: TurnRun, event: VoiceTurnEvent) {
        if (!run.discarded && (_state.value.phase is CallPhase.Paused || ended)) {
            // Paused or ended while the reply was on its way: it is not spoken, but the server did answer it.
            run.discarded = true
            if (event is VoiceTurnEvent.Say) pendingInterrupted = InterruptedTurn(run.clientTurnId, spokenChars = 0)
        }
        if (run.discarded) {
            if (event is VoiceTurnEvent.Done) {
                // The agent's tools ran on the server whether the reply was heard or not: the cards stay true.
                _state.update { it.copy(turns = it.turns + 1, actions = it.actions + event.actions) }
            }
            return
        }
        when (event) {
            // The latest tool call of this turn; a status without one (back to thinking) clears it.
            is VoiceTurnEvent.Status -> if (currentTurn === run) updateThinking { it.copy(tool = event.toolName) }
            is VoiceTurnEvent.Say -> onSay(run, event)
            is VoiceTurnEvent.Done -> onDone(run, event)
            is VoiceTurnEvent.Failed -> Unit // handled by execute
        }
    }

    private fun onSay(run: TurnRun, say: VoiceTurnEvent.Say) {
        if (run.firstSayAt == null) {
            run.firstSayAt = deps.nowMs()
            replyArrived()
        }
        val segments = say.segments
            .filter { it.text.isNotBlank() }
            .map { SpeechSegmentOut(it.text, SpeechLocale.forLanguage(it.lang, listenLocale)) }
        if (segments.isEmpty()) return
        val target = run.playback ?: Playback(run, run.clientTurnId.value).also {
            run.playback = it
            playback = it
        }
        enqueue(target, segments, say.turnId)
    }

    private fun onDone(run: TurnRun, done: VoiceTurnEvent.Done) {
        replyArrived()
        consecutiveErrors = 0
        run.done = done
        _state.update { it.copy(turns = it.turns + 1, actions = it.actions + done.actions) }
        if (run.playback == null) {
            // A replay or a turn without stream carries no `say`: speak the final checked text.
            val sentences = done.spoken?.text?.let(SpokenText::sentences).orEmpty()
            if (sentences.isNotEmpty()) {
                val target = Playback(run, run.clientTurnId.value).also {
                    run.playback = it
                    playback = it
                }
                sentences.forEach { enqueue(target, listOf(SpeechSegmentOut(it, listenLocale)), done.turnId) }
            }
        }
        val target = run.playback
        target?.sourceDone = true
        maybeReport(run)
        if (target == null || target.pending.isEmpty()) afterReply()
    }

    private fun replyArrived() {
        updateThinking { it.copy(tool = null) }
        thinkingJob?.cancel()
        cancelReconnect()
    }

    /** A turn that will not be answered (non-retryable). */
    private fun onTurnRejected(run: TurnRun, error: VoiceCallError) {
        when (error) {
            is VoiceCallError.NotEntitled -> {
                emit(CallEngineEvent.Error(stage = "llm", code = error.code))
                endCall(CallEndReason.Error, CallStop.Unavailable(UnavailableReason.NotEntitled))
            }
            VoiceCallError.OutOfCredits -> {
                emit(CallEngineEvent.Error(stage = "llm", code = error.code))
                endCall(CallEndReason.Error, CallStop.Unavailable(UnavailableReason.OutOfCredits))
            }
            else -> turnError(run, error)
        }
    }

    private fun turnError(run: TurnRun, error: VoiceCallError) {
        countError(error)
        consecutiveErrors++
        if (currentTurn === run) currentTurn = null
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            endCall(CallEndReason.Error, CallStop.Dropped(DropCause.Error))
        } else {
            startListening(afterInterrupt = false)
        }
    }

    private fun countError(error: VoiceCallError) {
        _state.update { it.copy(errors = it.errors + 1) }
        emit(CallEngineEvent.Error(stage = if (error is VoiceCallError.Unreachable) "network" else "llm", code = error.code))
    }

    private fun cancelThinking() {
        val run = currentTurn ?: return
        run.discarded = true
        currentTurn = null
        thinkingJob?.cancel()
        cancelReconnect()
        pendingInterrupted = InterruptedTurn(run.clientTurnId, spokenChars = 0)
        startListening(afterInterrupt = false)
    }

    // ---------------------------------------------------------------------------------------------
    // Speaking
    // ---------------------------------------------------------------------------------------------

    private fun enqueue(target: Playback, segments: List<SpeechSegmentOut>, turnId: TurnId?) {
        val utteranceId = "${target.idPrefix}#${target.seq++}"
        val sentence = SpokenText.joinSegments(segments.map { it.text })
        if (target.text.isNotEmpty()) target.text.append(' ')
        target.starts[utteranceId] = target.text.length
        target.lengths[utteranceId] = sentence.length
        target.text.append(sentence)
        target.pending += utteranceId
        target.sentences += segments
        target.turnId = turnId ?: target.turnId
        lastSentences = target.sentences.toList()
        deps.speechOutput.speak(utteranceId, segments, _state.value.rate)
        _state.update {
            it.copy(
                phase = CallPhase.Speaking,
                agent = AgentUtterance(
                    clientTurnId = target.run?.clientTurnId ?: ClientTurnId(target.idPrefix),
                    turnId = target.turnId,
                    text = target.text.toString(),
                    spokenChars = target.spokenChars,
                    interrupted = false,
                ),
            )
        }
        startBargeIn()
        startOutputLevel()
    }

    private fun onTtsEvent(event: SpeechOutputEvent) {
        val target = playback ?: return
        val start = target.starts[event.utteranceId] ?: return // an utterance of a cut or finished reply
        val length = target.lengths.getValue(event.utteranceId)
        when (event) {
            is SpeechOutputEvent.Started -> {
                if (target.firstAudioAt == null) target.firstAudioAt = deps.nowMs()
                target.run?.let(::maybeReport)
            }
            is SpeechOutputEvent.Range -> {
                target.spokenChars = max(target.spokenChars, start + event.endChar.coerceIn(0, length))
                updateCaption(target)
            }
            is SpeechOutputEvent.Done -> {
                target.spokenChars = max(target.spokenChars, start + length)
                target.pending -= event.utteranceId
                updateCaption(target)
                utteranceFinished(target)
            }
            is SpeechOutputEvent.Error -> {
                emit(CallEngineEvent.Error(stage = "tts", code = event.code.toString()))
                _state.update { it.copy(errors = it.errors + 1) }
                target.pending -= event.utteranceId
                utteranceFinished(target)
            }
        }
    }

    private fun updateCaption(target: Playback) = _state.update { s ->
        val agent = s.agent ?: return@update s
        s.copy(agent = agent.copy(spokenChars = target.spokenChars))
    }

    private fun utteranceFinished(target: Playback) {
        target.run?.let(::maybeReport)
        if (limitPending != null) {
            finishLimit()
            return
        }
        if (target.pending.isEmpty() && target.sourceDone) afterReply()
    }

    private fun afterReply() {
        if (ended) return
        playback = null
        if (currentTurn?.done != null) currentTurn = null
        stopBargeIn()
        stopOutputLevel()
        if (limitPending != null) {
            finishLimit()
            return
        }
        if (_state.value.phase is CallPhase.Paused) return
        startListening(afterInterrupt = false)
    }

    /** `TurnReplied` once the reply is in and its first audio started (or there is nothing to speak). */
    private fun maybeReport(run: TurnRun) {
        if (run.reported) return
        val done = run.done ?: return
        val target = run.playback
        val audioAt = target?.firstAudioAt
        if (target != null && audioAt == null && target.pending.isNotEmpty()) return
        run.reported = true
        emit(
            CallEngineEvent.TurnReplied(
                eosToFirstAudioMs = audioAt?.minus(run.eosAt),
                serverFirstSayMs = run.firstSayAt?.minus(run.firstSentAt),
                replyChars = target?.text?.length ?: 0,
                source = done.spoken?.source,
                truncated = done.spoken?.truncated ?: false,
                replayed = done.replayed,
                actions = done.actions.size,
            ),
        )
    }

    private fun interrupt(method: BargeInMethod) {
        when (_state.value.phase) {
            CallPhase.Speaking -> {
                cutPlayback(method)
                startListening(afterInterrupt = true)
            }
            is CallPhase.Thinking -> cancelThinking()
            else -> Unit
        }
    }

    /**
     * Stops TTS mid-reply. A server reply is not spoken further and goes as [InterruptedTurn] with the next request;
     * [method] `null` = not a barge-in (a pause, the end of the call), so nothing is counted.
     */
    private fun cutPlayback(method: BargeInMethod?) {
        val target = playback ?: return
        playback = null
        deps.speechOutput.stop()
        stopBargeIn()
        stopOutputLevel()
        val spoken = target.spokenChars
        target.run?.let { run ->
            run.discarded = true
            if (currentTurn === run) currentTurn = null
            pendingInterrupted = InterruptedTurn(run.clientTurnId, spoken)
        }
        _state.update {
            it.copy(
                agent = it.agent?.copy(spokenChars = spoken, interrupted = true),
                interruptions = if (method != null) it.interruptions + 1 else it.interruptions,
            )
        }
        if (method != null) emit(CallEngineEvent.BargeIn(method, spoken, replyChars = target.text.length))
    }

    private fun repeat() {
        val phase = _state.value.phase
        if (phase !is CallPhase.Listening && phase != CallPhase.Muted) return
        val sentences = lastSentences
        if (sentences.isEmpty()) return
        cancelListening()
        val previous = _state.value.agent
        val target = Playback(run = null, idPrefix = "repeat-${deps.newId()}")
        target.turnId = previous?.turnId
        playback = target
        sentences.forEach { enqueue(target, it, previous?.turnId) }
        target.sourceDone = true
    }

    private fun bargeInAllowed(): Boolean {
        if (config.manualEndOfTurn || _state.value.muted) return false
        return when (deps.audioRoute.route.value) {
            AudioRoute.WiredHeadset, AudioRoute.Bluetooth -> true
            AudioRoute.Speaker, AudioRoute.Earpiece -> deps.audioRoute.echoCancelerAvailable && config.bargeInSpeakerEnabled
        }
    }

    private fun startBargeIn() {
        if (bargeJob?.isActive == true || !bargeInAllowed()) return
        val waitForMic = micRelease
        bargeJob = scope.launch {
            waitForMic?.join()
            deps.bargeIn.speechStarts().collect {
                if (_state.value.phase == CallPhase.Speaking) interrupt(BargeInMethod.Voice)
            }
        }
    }

    /** Cancels the barge-in collector and keeps its job: the recognizer joins it before opening the microphone. */
    private fun stopBargeIn() {
        val job = bargeJob ?: return
        bargeJob = null
        job.cancel()
        micRelease = job
    }

    private fun startOutputLevel() {
        if (levelJob?.isActive == true) return
        levelJob = scope.launch {
            deps.speechOutput.level.collect { if (_state.value.phase == CallPhase.Speaking) _levels.value = it.coerceIn(0f, 1f) }
        }
    }

    private fun stopOutputLevel() {
        levelJob?.cancel()
        levelJob = null
    }

    // ---------------------------------------------------------------------------------------------
    // Network
    // ---------------------------------------------------------------------------------------------

    private fun onOffline() {
        val phase = _state.value.phase
        val turnInFlight = currentTurn?.let { !it.discarded && it.done == null } == true
        if (turnInFlight || reconnectJob != null) return
        if (phase is CallPhase.Listening || phase == CallPhase.Muted) waitForNetwork()
    }

    /** No turn in flight and no network: Reconnecting until it is back, then listen again. */
    private fun waitForNetwork() {
        cancelListening()
        if (reconnectJob == null) startReconnecting(retrying = false)
    }

    /**
     * Reconnecting for [RECONNECT_WINDOW]: a turn retries itself ([retrying]); without one the call listens again when
     * the network is back. The window running out drops the call.
     */
    private fun startReconnecting(retrying: Boolean) {
        val total = RECONNECT_WINDOW.inWholeSeconds.toInt()
        setPhase(CallPhase.Reconnecting(total))
        thinkingJob?.cancel()
        reconnectJob = scope.launch {
            val countdown = launch {
                for (left in total - 1 downTo 0) {
                    delay(1.seconds)
                    if (_state.value.phase is CallPhase.Reconnecting) setPhase(CallPhase.Reconnecting(left))
                }
                reconnectJob = null
                currentTurn?.let { run ->
                    run.discarded = true
                    run.job?.cancel()
                }
                endCall(
                    if (online) CallEndReason.Error else CallEndReason.Offline,
                    CallStop.Dropped(if (online) DropCause.Error else DropCause.Offline),
                )
            }
            if (!retrying) {
                deps.isOnline.first { it }
                countdown.cancel()
                online = true
                reconnectJob = null
                startListening(afterInterrupt = false)
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ---------------------------------------------------------------------------------------------
    // Pause, focus, resume
    // ---------------------------------------------------------------------------------------------

    private fun pause(reason: PauseReason) {
        cutPlayback(method = null)
        cancelListening()
        stopBargeIn()
        stopOutputLevel()
        cancelReconnect()
        thinkingJob?.cancel()
        clockJob?.cancel()
        if (pausedAtMs == null) pausedAtMs = deps.nowMs()
        // An interruption keeps the request registered: abandoning it unregisters the listener, and the Gain that
        // resumes the call (onFocus) would never come.
        if (reason != PauseReason.SystemInterruption) deps.audioFocus.release()
        _levels.value = 0f
        setPhase(CallPhase.Paused(reason))
        emit(CallEngineEvent.Paused(reason))
    }

    private fun screenStopped() {
        when (val phase = _state.value.phase) {
            CallPhase.Idle -> Unit
            CallPhase.Connecting -> pauseOnConnect = true
            is CallPhase.Paused -> if (phase.reason != PauseReason.Background) {
                if (pausedAtMs == null) pausedAtMs = deps.nowMs()
                clockJob?.cancel()
                deps.audioFocus.release()
                setPhase(CallPhase.Paused(PauseReason.Background))
                emit(CallEngineEvent.Paused(PauseReason.Background))
            }
            else -> pause(PauseReason.Background)
        }
    }

    private fun screenStarted() {
        pauseOnConnect = false
        val phase = _state.value.phase
        if (phase is CallPhase.Paused && phase.reason == PauseReason.Background && backgroundExpired()) {
            endCall(CallEndReason.Background, CallStop.Ended(CallEndReason.Background))
        }
    }

    private fun backgroundExpired(): Boolean {
        val pausedAt = pausedAtMs ?: return false
        return deps.nowMs() - pausedAt > config.backgroundEndSec * MILLIS_PER_SECOND
    }

    private fun resume() {
        val phase = _state.value.phase as? CallPhase.Paused ?: return
        if (phase.reason == PauseReason.Background && backgroundExpired()) {
            endCall(CallEndReason.Background, CallStop.Ended(CallEndReason.Background))
            return
        }
        val pausedFor = pausedAtMs?.let { deps.nowMs() - it }
        if (pausedFor != null && pausedFor >= SESSION_IDLE_TIMEOUT.inWholeMilliseconds) {
            // The server closed the session after 45 s without a heartbeat: start a new one.
            setPhase(CallPhase.Connecting)
            scope.launch { if (restartSession()) continueAfterPause() }
        } else {
            continueAfterPause()
        }
    }

    private fun continueAfterPause() {
        if (ended) return
        pausedAtMs = null
        deps.audioFocus.acquire()
        startClock()
        startListening(afterInterrupt = false)
    }

    private suspend fun restartSession(): Boolean {
        val request = VoiceSessionStartRequest(
            sessionId = VoiceSessionId(deps.newId()),
            entry = config.entry,
            listenLocale = listenLocale,
            sttOnDevice = _state.value.stt == SpeechAvailability.OnDevice,
        )
        return when (val result = deps.sessions.start(request)) {
            is VoiceResult.Success -> when (val start = result.value) {
                is VoiceSessionStart.Started -> {
                    heartbeatSec = start.heartbeatSec.coerceAtLeast(MIN_HEARTBEAT_SEC)
                    stream = config.streamEnabled && start.stream
                    _state.update {
                        it.copy(sessionId = start.sessionId, quota = start.quota, lastMinute = start.quota.secondsLeft <= LAST_MINUTE_SEC)
                    }
                    true
                }
                is VoiceSessionStart.LimitReached -> {
                    endCall(CallEndReason.Limit, CallStop.LimitReached(start.quota, duringCall = true))
                    false
                }
                VoiceSessionStart.Disabled -> {
                    endCall(CallEndReason.Error, CallStop.Unavailable(UnavailableReason.FlagOff))
                    false
                }
            }
            is VoiceResult.Failure -> {
                emit(CallEngineEvent.Error(stage = "session", code = result.error.code))
                endCall(
                    if (online) CallEndReason.Error else CallEndReason.Offline,
                    CallStop.Dropped(if (online) DropCause.Error else DropCause.Offline),
                )
                false
            }
        }
    }

    private fun onFocus(change: FocusChange) {
        val phase = _state.value.phase
        when (change) {
            FocusChange.LossTransient, FocusChange.Loss ->
                if (phase !is CallPhase.Paused && phase != CallPhase.Connecting && phase != CallPhase.Idle) {
                    pause(PauseReason.SystemInterruption)
                }
            FocusChange.Gain -> if (phase is CallPhase.Paused && phase.reason == PauseReason.SystemInterruption) resume()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // End
    // ---------------------------------------------------------------------------------------------

    private fun endCall(reason: CallEndReason, stop: CallStop) {
        if (ended) return
        if (!connected) {
            failConnect(stop, code = null)
            return
        }
        ended = true
        cutPlayback(method = null)
        listOf(connectJob, listenJob, bargeJob, levelJob, clockJob, thinkingJob, reconnectJob, afterInterruptJob)
            .forEach { it?.cancel() }
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        currentTurn?.discarded = true
        deps.speechOutput.stop()
        deps.audioFocus.release()
        val current = _state.value
        _levels.value = 0f
        _state.update { it.copy(phase = CallPhase.Idle, stop = stop) }
        emit(CallEngineEvent.Ended(reason, current.elapsedSec, current.turns, current.interruptions, current.errors))
        if (stop is CallStop.LimitReached) emit(CallEngineEvent.LimitReached(stop.quota.plan, stop.duringCall))
        deps.speechOutput.release()
        val sessionId = current.sessionId ?: return
        val request = VoiceSessionEndRequest(
            sessionId = sessionId,
            reason = reason,
            elapsedSec = current.elapsedSec,
            turns = current.turns,
            interruptions = current.interruptions,
            interrupted = pendingInterrupted,
        )
        pendingInterrupted = null
        scope.launch { closeSession(request) }
    }

    /** Settles the minutes; the quota the server sends back replaces the one of the last heartbeat. */
    private suspend fun closeSession(request: VoiceSessionEndRequest) {
        when (val result = deps.sessions.end(request)) {
            is VoiceResult.Success -> result.value.quota?.let { quota -> _state.update { it.copy(quota = quota) } }
            is VoiceResult.Failure -> emit(CallEngineEvent.Error(stage = "session", code = result.error.code))
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setPhase(phase: CallPhase) = _state.update { it.copy(phase = phase) }

    private fun emit(event: CallEngineEvent) {
        eventChannel.trySend(event)
    }

    /** One voice turn; its request is built once and reused for every retry. */
    private class TurnRun(val clientTurnId: ClientTurnId, val firstSentAt: Long, val eosAt: Long) {
        var job: Job? = null
        var firstSayAt: Long? = null
        var playback: Playback? = null
        var done: VoiceTurnEvent.Done? = null
        var discarded = false
        var reported = false
    }

    /** What TTS is speaking: sentences of a turn ([run]) or a client text; ids are `"<idPrefix>#<seq>"`. */
    private class Playback(val run: TurnRun?, val idPrefix: String) {
        val text = StringBuilder()
        val starts = HashMap<String, Int>()
        val lengths = HashMap<String, Int>()
        val pending = LinkedHashSet<String>()
        val sentences = mutableListOf<List<SpeechSegmentOut>>()
        var turnId: TurnId? = null
        var seq = 0
        var spokenChars = 0
        var sourceDone = false
        var firstAudioAt: Long? = null
    }

    private companion object {
        /** Connect (recognizer, voice and session checks together) gives up after this: `Dropped(ConnectTimeout)`. */
        val CONNECT_TIMEOUT: Duration = 10.seconds

        /** How long connect waits for the first network reading; no reading = online. */
        val ONLINE_PROBE: Duration = 1.seconds

        /** Reconnecting lasts this long, then the call drops. */
        val RECONNECT_WINDOW: Duration = 20.seconds

        /** A retryable turn failure is retried this often while Reconnecting (and only once the network is back). */
        val RECONNECT_RETRY_SPACING: Duration = 3.seconds

        /** A turn the server is still running ([VoiceCallError.TurnInProgress]) is asked again this often… */
        val TURN_IN_PROGRESS_RETRY: Duration = 3.seconds

        /** …for at most this long after it was first sent, then it counts as a failed turn. */
        val TURN_IN_PROGRESS_WINDOW: Duration = 90.seconds

        /** Thinking turns [ThinkingDelay.Slow] after this. */
        val SLOW_THINKING: Duration = 6.seconds

        /** Thinking turns [ThinkingDelay.VerySlow] after this. */
        val VERY_SLOW_THINKING: Duration = 20.seconds

        /** `Listening.afterInterrupt` stays on for this long after a barge-in. */
        val AFTER_INTERRUPT_WINDOW: Duration = 3.seconds

        /** A failed recognizer utterance (network, busy, client) is restarted after this. */
        val STT_RETRY_DELAY: Duration = 1.seconds

        /** The server closes a session without a heartbeat after this: a longer pause starts a new one on Resume. */
        val SESSION_IDLE_TIMEOUT: Duration = 45.seconds

        /** Heartbeat interval until the session says otherwise. */
        const val DEFAULT_HEARTBEAT_SEC = 15

        /** The session's heartbeat interval is never taken below this. */
        const val MIN_HEARTBEAT_SEC = 5

        /** Less quota than this flags [CallEngineState.lastMinute]. */
        const val LAST_MINUTE_SEC = 60

        /** This many failed turns (or recognizer errors) in a row end the call. */
        const val MAX_CONSECUTIVE_ERRORS = 3

        /** A recognized utterance is cut to this many characters before it is sent. */
        const val MAX_TURN_CHARS = 1000

        const val MILLIS_PER_SECOND = 1000L
    }
}

private fun SpeechErrorKind.code(): String = when (this) {
    SpeechErrorKind.NoMatch -> "no_match"
    SpeechErrorKind.SpeechTimeout -> "speech_timeout"
    SpeechErrorKind.Network -> "network"
    SpeechErrorKind.Permission -> "permission"
    SpeechErrorKind.Busy -> "busy"
    SpeechErrorKind.LanguageUnavailable -> "language_unavailable"
    SpeechErrorKind.Client -> "client"
    SpeechErrorKind.Other -> "other"
}
