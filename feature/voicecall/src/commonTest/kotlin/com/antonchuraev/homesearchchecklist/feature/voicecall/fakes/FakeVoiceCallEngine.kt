package com.antonchuraev.homesearchchecklist.feature.voicecall.fakes

import com.antonchuraev.homesearchchecklist.core.voice.api.CallCommand
import com.antonchuraev.homesearchchecklist.core.voice.api.CallConfig
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * A [VoiceCallEngine] the test drives by hand: it records every command and changes its state only when
 * the test says so ([set] / [update]) — the ViewModel's mapping and effects are tested against the
 * engine's CONTRACT, not against the real state machine (that one has its own tests in core/voice).
 */
class FakeVoiceCallEngine(val config: CallConfig, val scope: CoroutineScope) : VoiceCallEngine {
    private val _state = MutableStateFlow(CallEngineState())
    override val state: StateFlow<CallEngineState> = _state

    private val eventChannel = Channel<CallEngineEvent>(Channel.UNLIMITED)
    override val events: Flow<CallEngineEvent> = eventChannel.receiveAsFlow()

    override val levels = MutableStateFlow(0f)

    val commands = mutableListOf<CallCommand>()

    /** What the engine does with a command, besides recording it; default: nothing. */
    var onCommand: (CallCommand) -> Unit = {}

    override fun dispatch(command: CallCommand) {
        commands += command
        onCommand(command)
    }

    fun set(state: CallEngineState) {
        _state.value = state
    }

    fun update(change: (CallEngineState) -> CallEngineState) = _state.update(change)

    fun emit(event: CallEngineEvent) {
        eventChannel.trySend(event)
    }

    /** One fresh fake per `create`, as the real factory makes one engine per call. */
    class Factory : VoiceCallEngine.Factory {
        val created = mutableListOf<FakeVoiceCallEngine>()
        val last: FakeVoiceCallEngine get() = created.last()

        override fun create(scope: CoroutineScope, config: CallConfig): VoiceCallEngine =
            FakeVoiceCallEngine(config, scope).also { created += it }
    }
}
