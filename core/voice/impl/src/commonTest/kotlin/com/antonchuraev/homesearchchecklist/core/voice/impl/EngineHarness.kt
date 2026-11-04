package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.CallCommand
import com.antonchuraev.homesearchchecklist.core.voice.api.CallConfig
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSegment
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSource
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSummary
import com.antonchuraev.homesearchchecklist.core.voice.api.TurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallEntry
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnRequest
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeAudioFocusController
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeAudioRouteMonitor
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeBargeInDetector
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeSpeechInput
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeSpeechOutput
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeVoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeVoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.FakeVoiceTurnGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

// Test bench of DefaultVoiceCallEngine: the engine lives in `backgroundScope` (its clock and collectors never end),
// virtual time drives every timer, `nowMs` is the scheduler time. Drive it with `runCurrent()` / `advanceTimeBy()`.

val EN_US = SpeechLocale("en-US")

fun callConfig(
    listenLocale: SpeechLocale = EN_US,
    alsoAllow: List<SpeechLocale> = emptyList(),
    manualEndOfTurn: Boolean = false,
    bargeInSpeakerEnabled: Boolean = true,
    softLimitSec: Int? = null,
    backgroundEndSec: Int = 120,
) = CallConfig(
    entry = VoiceCallEntry("chat"),
    listenLocale = listenLocale,
    uiLocale = null,
    alsoAllow = alsoAllow,
    manualEndOfTurn = manualEndOfTurn,
    bargeInSpeakerEnabled = bargeInSpeakerEnabled,
    streamEnabled = true,
    rate = SpeechRate.Normal,
    softLimitSec = softLimitSec,
    backgroundEndSec = backgroundEndSec,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EngineHarness(private val test: TestScope, config: CallConfig = callConfig()) {
    val input = FakeSpeechInput()
    val output = FakeSpeechOutput()
    val focus = FakeAudioFocusController()
    val route = FakeAudioRouteMonitor()
    val barge = FakeBargeInDetector()
    val capabilities = FakeVoiceCapabilities()
    val turns = FakeVoiceTurnGateway()
    val sessions = FakeVoiceSessionGateway()
    val online = MutableStateFlow(true)
    private var ids = 0

    internal val engine = DefaultVoiceCallEngine(
        scope = test.backgroundScope,
        config = config,
        deps = VoiceCallDependencies(
            speechInput = input,
            speechOutput = output,
            audioFocus = focus,
            audioRoute = route,
            bargeIn = barge,
            capabilities = capabilities,
            turns = turns,
            sessions = sessions,
            isOnline = online,
            nowMs = { test.testScheduler.currentTime },
            newId = { "id${++ids}" },
        ),
    )

    val events = mutableListOf<CallEngineEvent>()
    val scripted = ScriptedTurns()

    init {
        turns.reply = { scripted.flow() }
        test.backgroundScope.launch { engine.events.collect { events += it } }
    }

    val request: VoiceTurnRequest get() = turns.requests.last()

    /** Sends events to the newest turn and lets the engine react. */
    fun reply(vararg events: VoiceTurnEvent) {
        scripted.send(*events)
        test.runCurrent()
    }

    val state: CallEngineState get() = engine.state.value
    val phase: CallPhase get() = state.phase

    /** Start and settle into Listening. */
    fun connect() {
        engine.dispatch(CallCommand.Start)
        test.runCurrent()
    }

    fun dispatch(command: CallCommand) {
        engine.dispatch(command)
        test.runCurrent()
    }

    /** The user says [text] (one utterance). */
    fun say(text: String) {
        input.say(text)
        test.runCurrent()
    }

    /** Finishes every utterance handed to TTS so far, in order. */
    fun finishSpeaking() {
        output.spoken.map { it.utteranceId }.distinct().forEach { output.finish(it) }
        test.runCurrent()
    }

    inline fun <reified T : CallEngineEvent> eventsOf(): List<T> = events.filterIsInstance<T>()
}

/**
 * Turns answered by the test: every request gets its own channel ([channel] by request index), the flow ends after
 * its Done or Failed like the real gateway's.
 */
class ScriptedTurns {
    private val channels = mutableListOf<Channel<VoiceTurnEvent>>()

    fun flow(): Flow<VoiceTurnEvent> {
        val channel = Channel<VoiceTurnEvent>(Channel.UNLIMITED)
        channels += channel
        return channel.receiveAsFlow().transformWhile {
            emit(it)
            it !is VoiceTurnEvent.Done && it !is VoiceTurnEvent.Failed
        }
    }

    fun channel(index: Int = channels.lastIndex): Channel<VoiceTurnEvent> = channels[index]

    fun send(vararg events: VoiceTurnEvent, index: Int = channels.lastIndex) {
        events.forEach { channel(index).trySend(it) }
    }
}

fun replyEvents(turnId: String, vararg sentences: String, lang: String = "en"): List<VoiceTurnEvent> =
    sentences.mapIndexed { seq, text -> VoiceTurnEvent.Say(TurnId(turnId), seq, listOf(SpokenSegment(text, lang))) } +
        VoiceTurnEvent.Done(
            turnId = TurnId(turnId),
            replayed = false,
            spoken = SpokenSummary(sentences.size, truncated = false, source = SpokenSource.Model, text = sentences.joinToString(" ")),
        )

fun say(turnId: String, seq: Int, text: String, lang: String = "en") =
    VoiceTurnEvent.Say(TurnId(turnId), seq, listOf(SpokenSegment(text, lang)))

fun done(turnId: String, text: String, actions: List<VoiceAgentAction> = emptyList()) = VoiceTurnEvent.Done(
    turnId = TurnId(turnId),
    replayed = false,
    spoken = SpokenSummary(sentences = 1, truncated = false, source = SpokenSource.Model, text = text),
    actions = actions,
)
