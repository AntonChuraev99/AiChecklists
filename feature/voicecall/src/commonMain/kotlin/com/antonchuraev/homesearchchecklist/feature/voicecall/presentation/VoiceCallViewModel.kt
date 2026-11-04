package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInMethod
import com.antonchuraev.homesearchchecklist.core.voice.api.CallCommand
import com.antonchuraev.homesearchchecklist.core.voice.api.CallConfig
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.CallStop
import com.antonchuraev.homesearchchecklist.core.voice.api.PauseReason
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEngine
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEntry
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceQuota
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The voice call overlay: owns the permission step, one [VoiceCallEngine] per call, the mapping of the
 * engine's state onto [VoiceCallUiState] ([VoiceCallUiMapper]) and the call's analytics.
 *
 * Lives app-wide (the overlay sits above Nav3 in `App.kt`): a call survives navigation under it and a
 * rotation; a process death recreates this ViewModel in [VoiceCallUiState.Closed] (a killed call is
 * never restored).
 *
 * Engine lifetime: each call runs in its own child scope of [viewModelScope]. The scope is NOT cancelled
 * when the call stops — the engine still sends `voice_session end` and reads the settled minutes back
 * from it (the summary updates "Call again" with them). It is cancelled when the next call starts and
 * when the ViewModel is cleared.
 *
 * @param newEngineFactory a fresh factory per call (new turn gateway = new conversation, new session
 *   transport); resolved from Koin in the DI module.
 * @param languageTag the app language (`en`, `ru`, `hi`), read when a call starts.
 * @param formatLocalTime epoch ms → local wall-clock time, for the premium reset line.
 */
class VoiceCallViewModel(
    private val newEngineFactory: () -> VoiceCallEngine.Factory,
    private val capabilities: VoiceCapabilities,
    private val analytics: AnalyticsTracker,
    private val logger: AppLogger,
    private val languageTag: () -> String,
    private val formatLocalTime: (Long) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceCallUiState>(VoiceCallUiState.Closed)
    val uiState: StateFlow<VoiceCallUiState> = _uiState.asStateFlow()

    private val _effects = Channel<VoiceCallEffect>(Channel.BUFFERED)
    val effects: Flow<VoiceCallEffect> = _effects.receiveAsFlow()

    /** Orb level `0..1` of the running call; read in the draw phase only. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var call: RunningCall? = null
    private var surface: String = DEFAULT_SURFACE
    private var captionsOn = true

    fun onAction(action: VoiceCallAction) {
        when (action) {
            is VoiceCallAction.Start -> start(action.surface)

            VoiceCallAction.AllowMicClicked -> allowMic()
            is VoiceCallAction.MicPermissionResult -> onPermissionResult(action)
            VoiceCallAction.OpenAppSettingsClicked -> sendEffect(VoiceCallEffect.OpenAppSettings)
            VoiceCallAction.NotNowClicked -> close()

            is VoiceCallAction.ScreenStarted -> onScreenStarted(action.micGranted)
            VoiceCallAction.ScreenStopped -> dispatch(CallCommand.ScreenStopped)

            VoiceCallAction.OrbClicked -> onOrb()
            VoiceCallAction.MicToggled -> call?.let { dispatch(CallCommand.SetMuted(!it.engine.state.value.muted)) }
            VoiceCallAction.CaptionsToggled -> {
                captionsOn = !captionsOn
                call?.let { render(it) }
            }
            is VoiceCallAction.RateSelected -> dispatch(
                CallCommand.SetRate(
                    when (action.rate) {
                        VoiceSpeechRateUi.Normal -> SpeechRate.Normal
                        VoiceSpeechRateUi.Slower -> SpeechRate.Slower
                    },
                ),
            )
            VoiceCallAction.CancelTurnClicked -> dispatch(CallCommand.CancelThinking)
            VoiceCallAction.ResumeClicked -> dispatch(CallCommand.Resume)
            VoiceCallAction.EndClicked -> hangUp()
            VoiceCallAction.BackPressed -> if (_uiState.value is VoiceCallUiState.InCall) hangUp() else close()

            VoiceCallAction.BannerPremiumClicked -> leaveCallFor(VoiceCallEffect.OpenPaywall(VoiceCallPaywallSource.WARNING))
            is VoiceCallAction.ActionCardClicked -> openAction(action.actionId)

            VoiceCallAction.RetryClicked, VoiceCallAction.CallAgainClicked -> start(surface, restart = true)
            VoiceCallAction.OpenChatClicked -> leaveCallFor(VoiceCallEffect.OpenChatSheet)
            VoiceCallAction.OpenVoiceSettingsClicked -> sendEffect(VoiceCallEffect.OpenTtsSettings)
            VoiceCallAction.TryPremiumClicked -> leaveCallFor(VoiceCallEffect.OpenPaywall(VoiceCallPaywallSource.LIMIT))
            VoiceCallAction.CloseClicked -> close()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Start and permission
    // ---------------------------------------------------------------------------------------------

    /**
     * Entry tap ([restart] = false) or Retry / Call again from a final screen ([restart] = true). A tap
     * while the overlay is already up is a double tap and is ignored (the overlay is the feedback).
     */
    private fun start(surface: String, restart: Boolean = false) {
        val current = _uiState.value
        if (!restart && current != VoiceCallUiState.Closed) {
            logger.debug(TAG, "start ignored: overlay already open (${current::class.simpleName})")
            return
        }
        if (restart && (current is VoiceCallUiState.InCall || current == VoiceCallUiState.Closed)) return
        this.surface = surface
        if (!restart) analytics.event(AnalyticsEvents.VoiceCall.TAPPED, mapOf(P_SURFACE to surface))
        if (!capabilities.isCallSupported) {
            // The entry is hidden off Android; reaching this is a wiring bug — say so, do not hang.
            logger.error(TAG, "start on a platform without voice calls", IllegalStateException("voice call unsupported"))
            show(VoiceCallUiState.Failure(VoiceCallFailureUi.NoRecognizer))
            return
        }
        if (capabilities.hasRecordPermission()) beginCall() else _uiState.value = VoiceCallUiState.Permission(blocked = false)
    }

    private fun allowMic() {
        val permission = _uiState.value as? VoiceCallUiState.Permission ?: return
        when {
            permission.blocked -> sendEffect(VoiceCallEffect.OpenAppSettings)
            // One system dialog in flight; the button shows loading meanwhile.
            permission.requesting -> Unit
            else -> {
                _uiState.value = permission.copy(requesting = true)
                sendEffect(VoiceCallEffect.RequestMicPermission)
            }
        }
    }

    private fun onPermissionResult(result: VoiceCallAction.MicPermissionResult) {
        if (_uiState.value !is VoiceCallUiState.Permission) return
        val blocked = !result.granted && !result.canAskAgain
        analytics.event(AnalyticsEvents.VoiceCall.PERMISSION_RESULT, mapOf(P_GRANTED to result.granted, P_BLOCKED to blocked))
        if (result.granted) beginCall() else _uiState.value = VoiceCallUiState.Permission(blocked = blocked)
    }

    /** Back from the app settings with the permission turned on: the call starts by itself. */
    private fun onScreenStarted(micGranted: Boolean) {
        if (_uiState.value is VoiceCallUiState.Permission) {
            if (micGranted) beginCall()
            return
        }
        dispatch(CallCommand.ScreenStarted)
    }

    private fun beginCall() {
        call?.scope?.cancel()
        _level.value = 0f
        val language = languageTag().ifBlank { DEFAULT_LANGUAGE }
        val manualEndOfTurn = capabilities.touchExplorationEnabled
        val config = CallConfig(
            entry = VoiceCallEntry(surface),
            listenLocale = SpeechLocale.forLanguage(language, DEFAULT_LOCALE),
            uiLocale = language,
            manualEndOfTurn = manualEndOfTurn,
            // Still gated by the engine on an echo canceller; in headphones voice barge-in is always on.
            bargeInSpeakerEnabled = true,
            // chat_agent does not stream sentences (the session says so too).
            streamEnabled = false,
            rate = SpeechRate.Normal,
            // The server ends the call at its per-call maximum and when the minutes run out.
            softLimitSec = null,
            backgroundEndSec = BACKGROUND_END_SEC,
        )
        val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
        val engine = newEngineFactory().create(scope, config)
        val running = RunningCall(engine, scope, manualEndOfTurn)
        call = running
        render(running)
        scope.launch {
            engine.state.collect { state ->
                if (state.quota !== running.quota) {
                    running.quota = state.quota
                    running.quotaAtElapsedSec = state.elapsedSec
                }
                render(running)
            }
        }
        scope.launch { engine.events.collect { onEngineEvent(running, it) } }
        scope.launch { engine.levels.collect { if (call === running) _level.value = it } }
        engine.dispatch(CallCommand.Start)
    }

    // ---------------------------------------------------------------------------------------------
    // In call
    // ---------------------------------------------------------------------------------------------

    private fun onOrb() {
        val running = call ?: return
        when (running.engine.state.value.phase) {
            CallPhase.Speaking -> running.engine.dispatch(CallCommand.Interrupt(BargeInMethod.Tap))
            is CallPhase.Listening -> if (running.manualEndOfTurn) running.engine.dispatch(CallCommand.FinishUtterance)
            else -> Unit
        }
    }

    private fun hangUp() {
        val running = call
        if (running == null || running.engine.state.value.stop != null) {
            close()
            return
        }
        running.engine.dispatch(CallCommand.HangUp)
    }

    /**
     * D1: leaving the call for another screen ends it first. The overlay closes together with the
     * effect, so the destination is never drawn under a live call.
     */
    private fun leaveCallFor(effect: VoiceCallEffect) {
        endRunningCall()
        close()
        sendEffect(effect)
    }

    private fun openAction(actionId: String) {
        val running = call ?: return
        val state = running.engine.state.value
        val index = actionId.removePrefix(ACTION_ID_PREFIX).toIntOrNull()
        val action = index?.let { state.actions.getOrNull(it) }
        val checklistId = action?.targetId
        if (action == null || checklistId == null) {
            // Not clickable by contract (no checklist to open); a stale id is a bug worth a log line.
            logger.warning(TAG, "action card $actionId has nothing to open")
            return
        }
        val inCall = _uiState.value is VoiceCallUiState.InCall
        analytics.event(
            AnalyticsEvents.VoiceCall.ACTION_OPENED,
            mapOf(P_KIND to action.kind, P_FROM to if (inCall) FROM_IN_CALL else FROM_SUMMARY),
        )
        leaveCallFor(VoiceCallEffect.OpenChecklist(checklistId, callDurationSec = if (inCall) state.elapsedSec else null))
    }

    private fun endRunningCall() {
        val running = call ?: return
        if (running.engine.state.value.stop == null) running.engine.dispatch(CallCommand.HangUp)
    }

    private fun close() {
        call?.closed = true
        _level.value = 0f
        _uiState.value = VoiceCallUiState.Closed
    }

    private fun dispatch(command: CallCommand) {
        call?.takeIf { !it.closed }?.engine?.dispatch(command)
    }

    // ---------------------------------------------------------------------------------------------
    // State and analytics
    // ---------------------------------------------------------------------------------------------

    private fun render(running: RunningCall) {
        if (call !== running || running.closed) return
        val state = running.engine.state.value
        val ui = VoiceCallUiMapper.map(
            state = state,
            facts = VoiceCallFacts(
                captionsOn = captionsOn,
                manualEndOfTurn = running.manualEndOfTurn,
                quotaAtElapsedSec = running.quotaAtElapsedSec,
            ),
            formatLocalTime = formatLocalTime,
        )
        if (ui is VoiceCallUiState.Failure && !running.failureReported) {
            running.failureReported = true
            analytics.event(AnalyticsEvents.VoiceCall.FAILED, mapOf(P_KIND to ui.kind.name.lowercase()))
            logStop(state)
        }
        if (ui == VoiceCallUiState.Closed) running.closed = true
        show(ui)
    }

    private fun show(ui: VoiceCallUiState) {
        _uiState.value = ui
    }

    /** NotEntitled / OutOfCredits read as "Gisti isn't answering" on screen: the log keeps the cause. */
    private fun logStop(state: CallEngineState) {
        val stop = state.stop as? CallStop.Unavailable ?: return
        logger.warning(TAG, "call unavailable: ${stop.reason}")
    }

    private fun onEngineEvent(running: RunningCall, event: CallEngineEvent) {
        when (event) {
            is CallEngineEvent.Started -> analytics.event(
                AnalyticsEvents.VoiceCall.STARTED,
                mapOf(
                    P_SURFACE to surface,
                    P_PLAN to event.quota.plan.wireValue,
                    P_SECONDS_LEFT to event.quota.secondsLeft,
                    P_STT to if (event.stt == SpeechAvailability.OnDevice) STT_ON_DEVICE else STT_NETWORK,
                ),
            )
            is CallEngineEvent.TurnReplied -> analytics.event(
                AnalyticsEvents.VoiceCall.TURN,
                buildMap {
                    event.eosToFirstAudioMs?.let { put(P_LATENCY_MS, it) }
                    put(P_ACTIONS, event.actions)
                    put(P_REPLAYED, event.replayed)
                    put(P_REPLY_CHARS, event.replyChars)
                },
            )
            is CallEngineEvent.BargeIn -> analytics.event(AnalyticsEvents.VoiceCall.BARGE_IN, mapOf(P_METHOD to event.method.wireValue))
            is CallEngineEvent.Paused -> analytics.event(
                AnalyticsEvents.VoiceCall.PAUSED,
                mapOf(P_REASON to if (event.reason == PauseReason.Background) "background" else "interrupted_by_system"),
            )
            is CallEngineEvent.LimitReached -> analytics.event(
                AnalyticsEvents.VoiceCall.LIMIT_REACHED,
                mapOf(P_PLAN to event.plan.wireValue, P_DURING_CALL to event.duringCall),
            )
            is CallEngineEvent.Error -> {
                logger.warning(TAG, "call error stage=${event.stage} code=${event.code}")
                analytics.event(AnalyticsEvents.VoiceCall.ERROR, mapOf(P_STAGE to event.stage, P_CODE to event.code))
            }
            is CallEngineEvent.Ended -> analytics.event(
                AnalyticsEvents.VoiceCall.ENDED,
                mapOf(
                    P_REASON to event.reason.wireValue,
                    P_DURATION_SEC to event.durationSec,
                    P_TURNS to event.turns,
                    P_INTERRUPTIONS to event.interruptions,
                    P_ERRORS to event.errors,
                    P_ACTIONS to running.engine.state.value.actions.size,
                ),
            )
            // Per-utterance recognizer facts stay out of analytics in v1 (volume, no decision rides on it).
            is CallEngineEvent.TurnCaptured -> Unit
        }
    }

    private fun sendEffect(effect: VoiceCallEffect) {
        _effects.trySend(effect)
    }

    /** One call: its engine, its scope and what the ViewModel tracks about it. */
    private class RunningCall(val engine: VoiceCallEngine, val scope: CoroutineScope, val manualEndOfTurn: Boolean) {
        var quota: VoiceQuota? = null
        var quotaAtElapsedSec = 0
        var failureReported = false

        /** The overlay was closed: later engine updates (the settled session end) are not drawn. */
        var closed = false
    }

    companion object {
        private const val TAG = "VoiceCall"
        private const val DEFAULT_SURFACE = "unknown"
        private const val DEFAULT_LANGUAGE = "en"
        private val DEFAULT_LOCALE = SpeechLocale("en-US")
        private const val ACTION_ID_PREFIX = "a"

        /** Back from the background later than this ends the call (the overlay shows the summary). */
        const val BACKGROUND_END_SEC = 120

        private const val P_SURFACE = "surface"
        private const val P_GRANTED = "granted"
        private const val P_BLOCKED = "blocked"
        private const val P_PLAN = "plan"
        private const val P_SECONDS_LEFT = "seconds_left"
        private const val P_STT = "stt"
        private const val P_KIND = "kind"
        private const val P_LATENCY_MS = "latency_ms"
        private const val P_ACTIONS = "actions"
        private const val P_REPLAYED = "replayed"
        private const val P_REPLY_CHARS = "reply_chars"
        private const val P_METHOD = "method"
        private const val P_REASON = "reason"
        private const val P_DURING_CALL = "during_call"
        private const val P_STAGE = "stage"
        private const val P_CODE = "code"
        private const val P_DURATION_SEC = "duration_sec"
        private const val P_TURNS = "turns"
        private const val P_INTERRUPTIONS = "interruptions"
        private const val P_ERRORS = "errors"
        private const val P_FROM = "from"
        private const val STT_ON_DEVICE = "on_device"
        private const val STT_NETWORK = "network"
        private const val FROM_IN_CALL = "in_call"
        private const val FROM_SUMMARY = "summary"
    }
}
