package com.antonchuraev.homesearchchecklist.core.voice.impl

import com.antonchuraev.homesearchchecklist.core.voice.api.AudioRoute
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInMethod
import com.antonchuraev.homesearchchecklist.core.voice.api.CallCommand
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.CallStop
import com.antonchuraev.homesearchchecklist.core.voice.api.DropCause
import com.antonchuraev.homesearchchecklist.core.voice.api.FocusChange
import com.antonchuraev.homesearchchecklist.core.voice.api.InterruptedTurn
import com.antonchuraev.homesearchchecklist.core.voice.api.PauseReason
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechErrorKind
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechOutputStatus
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechSegmentOut
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSegment
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSource
import com.antonchuraev.homesearchchecklist.core.voice.api.ThinkingDelay
import com.antonchuraev.homesearchchecklist.core.voice.api.TurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.UnavailableReason
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallError
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceHeartbeat
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceResult
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEnd
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStart
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnProgress
import com.antonchuraev.homesearchchecklist.core.voice.impl.fakes.VoiceFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every transition of the call machine on virtual time (port of AiTeacher DefaultVoiceCallEngineTest). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVoiceCallEngineTest {

    private val listening = CallPhase.Listening(hearing = false, afterInterrupt = false)

    // ----- Start -----

    @Test
    fun startStartsTheSessionAndListens() = runTest {
        val h = EngineHarness(this)
        h.dispatch(CallCommand.Start)

        assertEquals(1, h.sessions.starts.size)
        assertTrue(h.sessions.starts.single().sttOnDevice)
        assertEquals(setOf(SpeechLocale("en-US")), h.output.prepared.single())
        assertEquals(listening, h.phase)
        assertEquals(SpeechAvailability.OnDevice, h.state.stt)
        assertEquals(VoiceFixtures.freeQuota, h.state.quota)
        assertTrue(h.input.listening.value)
        assertTrue(h.focus.held)
        assertEquals(CallEngineEvent.Started(SpeechAvailability.OnDevice, VoiceFixtures.freeQuota, stream = true), h.events.single())
    }

    @Test
    fun startWithoutThePermissionWaitsForPermissionGranted() = runTest {
        val h = EngineHarness(this)
        h.capabilities.recordPermission = false
        h.connect()
        assertEquals(CallPhase.Idle, h.phase)
        assertTrue(h.sessions.starts.isEmpty())

        h.capabilities.recordPermission = true
        h.dispatch(CallCommand.PermissionGranted)
        assertEquals(listening, h.phase)
    }

    @Test
    fun noRecognizerNoVoiceKillSwitchAndOfflineMakeTheCallUnavailable() = runTest {
        val noStt = EngineHarness(this).apply { input.availabilityOf = { SpeechAvailability.Unavailable } }
        noStt.connect()
        assertEquals(CallStop.Unavailable(UnavailableReason.NoRecognizer), noStt.state.stop)

        val noVoice = EngineHarness(this).apply { output.prepareResult = { SpeechOutputStatus.MissingVoice(it) } }
        noVoice.connect()
        assertEquals(CallStop.Unavailable(UnavailableReason.NoVoice), noVoice.state.stop)

        val off = EngineHarness(this).apply { sessions.startResult = { VoiceResult.Success(VoiceSessionStart.Disabled) } }
        off.connect()
        assertEquals(CallStop.Unavailable(UnavailableReason.FlagOff), off.state.stop)

        val offline = EngineHarness(this).apply { online.value = false }
        offline.connect()
        assertEquals(CallStop.Unavailable(UnavailableReason.Offline), offline.state.stop)
        assertTrue(offline.sessions.starts.isEmpty())

        // A session that did start for a device that cannot talk is closed right away.
        assertEquals(CallEndReason.Error, noStt.sessions.ends.single().reason)
        assertTrue(noStt.eventsOf<CallEngineEvent.Ended>().isEmpty())
    }

    @Test
    fun sessionStartRefusedForNoCreditsIsUnavailableOutOfCredits() = runTest {
        val h = EngineHarness(this)
        h.sessions.startResult = { VoiceResult.Failure(VoiceCallError.OutOfCredits) }
        h.connect()

        assertEquals(CallStop.Unavailable(UnavailableReason.OutOfCredits), h.state.stop)
        assertFalse(h.input.listening.value)
    }

    @Test
    fun startOverTheMinutesIsLimitReachedBeforeTheCall() = runTest {
        val h = EngineHarness(this)
        h.sessions.startResult = { VoiceResult.Success(VoiceSessionStart.LimitReached(VoiceFixtures.emptyFreeQuota)) }
        h.connect()

        assertEquals(CallStop.LimitReached(VoiceFixtures.emptyFreeQuota, duringCall = false), h.state.stop)
        assertEquals(listOf<CallEngineEvent>(CallEngineEvent.LimitReached(VoicePlan.Free, duringCall = false)), h.events)
        assertFalse(h.input.listening.value)
    }

    @Test
    fun startThatDoesNotFinishIn10sDropsWithConnectTimeoutAndClosesTheStartedSession() = runTest {
        val h = EngineHarness(this)
        h.output.prepareGate = CompletableDeferred() // the TTS engine never binds; the session did start
        h.dispatch(CallCommand.Start)
        assertEquals(CallPhase.Connecting, h.phase)

        advanceTimeBy(9_999)
        assertEquals(CallPhase.Connecting, h.phase)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(CallStop.Dropped(DropCause.ConnectTimeout), h.state.stop)
        assertEquals(CallEndReason.Error, h.sessions.ends.single().reason)
        assertEquals(CallEngineEvent.Error("session", "connect_timeout"), h.events.single())
    }

    // ----- Final, Say, end of TTS -----

    @Test
    fun anUtteranceGoesAsATurnTheReplyIsSpokenAndTheCallListensAgain() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("How do I say hello?")

        assertEquals(CallPhase.Thinking(ThinkingDelay.Normal), h.phase)
        val request = h.request
        assertEquals("How do I say hello?", request.text)
        assertEquals(h.state.sessionId, request.sessionId)
        assertEquals(0.9f, request.sttConfidence)
        assertTrue(request.sttOnDevice)
        assertTrue(request.stream)
        assertNull(request.interrupted)
        assertEquals(
            CallEngineEvent.TurnCaptured(speechMs = 1_200, chars = 19, outcome = "ok"),
            h.eventsOf<CallEngineEvent.TurnCaptured>().single(),
        )

        advanceTimeBy(1_500)
        h.reply(say("t1", 0, "You say hola."))
        assertEquals(CallPhase.Speaking, h.phase)
        val utterance = h.output.spoken.single()
        assertEquals("${request.clientTurnId.value}#0", utterance.utteranceId)
        assertEquals(listOf(SpeechSegmentOut("You say hola.", SpeechLocale("en-US"))), utterance.segments)
        assertEquals("You say hola.", h.state.agent?.text)
        assertEquals(0, h.state.agent?.spokenChars)

        h.output.emit(SpeechOutputEvent.Started(utterance.utteranceId))
        h.output.emit(SpeechOutputEvent.Range(utterance.utteranceId, endChar = 7))
        runCurrent()
        assertEquals(7, h.state.agent?.spokenChars)

        h.reply(done("t1", "You say hola."))
        assertEquals(CallPhase.Speaking, h.phase) // still speaking: TTS has not finished
        assertEquals(1, h.state.turns)

        h.output.emit(SpeechOutputEvent.Done(utterance.utteranceId))
        runCurrent()
        assertEquals(listening, h.phase)
        assertTrue(h.input.listening.value)
        val replied = h.eventsOf<CallEngineEvent.TurnReplied>().single()
        assertEquals(1_500L, replied.eosToFirstAudioMs)
        assertEquals(1_500L, replied.serverFirstSayMs)
        assertEquals(13, replied.replyChars)
        assertEquals(SpokenSource.Model, replied.source)
        assertEquals(0, replied.actions)
    }

    @Test
    fun actionsOfTheAgentGoToTheStateInOrderAndAreCountedInTurnReplied() = runTest {
        val h = EngineHarness(this)
        h.connect()
        val created = VoiceAgentAction(title = "Created checklist «Trip»", kind = "checklist_created", targetId = "42")
        val added = VoiceAgentAction(title = "Added «Passport»", kind = "item_added", targetId = "42")

        h.say("Make a trip checklist with a passport")
        h.reply(say("t1", 0, "Done, it is in your list."), done("t1", "Done, it is in your list.", actions = listOf(created, added)))
        assertEquals(listOf(created, added), h.state.actions)
        h.finishSpeaking()
        assertEquals(2, h.eventsOf<CallEngineEvent.TurnReplied>().single().actions)

        // The next turn appends; a cut turn still brings its actions (the tools ran on the server).
        val renamed = VoiceAgentAction(title = "Renamed to «Rome»", kind = "checklist_renamed", targetId = "42")
        h.say("Call it Rome")
        h.reply(say("t2", 0, "Renamed it to Rome, and also…"))
        h.dispatch(CallCommand.Interrupt(BargeInMethod.Tap))
        h.reply(done("t2", "Renamed it to Rome, and also…", actions = listOf(renamed)))

        assertEquals(listOf(created, added, renamed), h.state.actions)
        assertEquals(2, h.state.turns)
    }

    @Test
    fun aSlowReplyMovesThinkingToSlowAfter6sAndVerySlowAfter20s() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Question")
        advanceTimeBy(6_001)
        assertEquals(CallPhase.Thinking(ThinkingDelay.Slow), h.phase)
        advanceTimeBy(14_000)
        assertEquals(CallPhase.Thinking(ThinkingDelay.VerySlow), h.phase)
    }

    @Test
    fun theToolTheAgentRunsShowsInThinkingAndIsClearedByTheReplyAndTheNextTurn() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Make a trip checklist")
        h.reply(VoiceTurnEvent.Status(VoiceTurnProgress.Thinking))
        assertEquals(CallPhase.Thinking(ThinkingDelay.Normal, tool = null), h.phase)

        h.reply(VoiceTurnEvent.Status(VoiceTurnProgress.Acting, toolName = "create_checklist"))
        assertEquals(CallPhase.Thinking(ThinkingDelay.Normal, tool = "create_checklist"), h.phase)
        advanceTimeBy(6_001) // the slow-thinking step keeps the tool
        assertEquals(CallPhase.Thinking(ThinkingDelay.Slow, tool = "create_checklist"), h.phase)
        h.reply(VoiceTurnEvent.Status(VoiceTurnProgress.Acting, toolName = "add_items"))
        assertEquals("add_items", (h.phase as CallPhase.Thinking).tool)

        h.reply(*replyEvents("t1", "Done.").toTypedArray())
        assertEquals(CallPhase.Speaking, h.phase)
        h.finishSpeaking()

        h.say("And another one")
        assertEquals(CallPhase.Thinking(ThinkingDelay.Normal, tool = null), h.phase)
    }

    @Test
    fun aReplyWithoutSayChunksSpeaksItsFinalTextSentenceBySentence() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Again please")
        h.reply(done("t1", "First one. Second one."))

        assertEquals(CallPhase.Speaking, h.phase)
        assertEquals(listOf("First one.", "Second one."), h.output.spoken.map { it.segments.single().text })
        h.finishSpeaking()
        assertEquals(listening, h.phase)
    }

    @Test
    fun anEmptyUtteranceOrNoMatchListensAgainWithoutATurn() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.input.emit(SpeechEvent.Error(SpeechErrorKind.NoMatch))
        runCurrent()
        h.input.emit(SpeechEvent.Final("  ", confidence = null, speechMs = 300))
        runCurrent()

        assertTrue(h.turns.requests.isEmpty())
        assertEquals(listOf("no_match", "empty"), h.eventsOf<CallEngineEvent.TurnCaptured>().map { it.outcome })
        assertEquals(listening, h.phase)
        assertEquals(3, h.input.listenCalls.size)
    }

    // ----- Interrupt -----

    @Test
    fun aTapStopsTtsListensFor3sAfterTheInterruptAndSendsTheCutWithTheNextTurn() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Tell me a story")
        val first = h.request.clientTurnId
        h.reply(say("t1", 0, "Once upon a time there was a cat."))
        val id = h.output.spoken.single().utteranceId
        h.output.emit(SpeechOutputEvent.Range(id, endChar = 9))
        runCurrent()

        h.dispatch(CallCommand.Interrupt(BargeInMethod.Tap))
        assertEquals(1, h.output.stopCalls)
        assertEquals(CallPhase.Listening(hearing = false, afterInterrupt = true), h.phase)
        assertTrue(h.state.agent!!.interrupted)
        assertEquals(9, h.state.agent!!.spokenChars)
        assertEquals(1, h.state.interruptions)
        assertEquals(
            CallEngineEvent.BargeIn(BargeInMethod.Tap, spokenChars = 9, replyChars = 33),
            h.eventsOf<CallEngineEvent.BargeIn>().single(),
        )

        // The cut reply is not spoken further, but its result is still collected to the end.
        h.reply(say("t1", 1, "The end."), done("t1", "Once upon a time there was a cat. The end."))
        assertEquals(1, h.output.spoken.size)
        assertEquals(1, h.state.turns)

        advanceTimeBy(3_001)
        assertEquals(listening, h.phase)

        h.say("What happened next?")
        assertEquals(InterruptedTurn(first, spokenChars = 9), h.request.interrupted)
    }

    @Test
    fun cancelWhileThinkingListensAndReportsTheTurnAsCutWith0Characters() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("First")
        val first = h.request.clientTurnId
        h.dispatch(CallCommand.CancelThinking)
        assertEquals(listening, h.phase)

        h.say("Second")
        assertEquals(InterruptedTurn(first, spokenChars = 0), h.request.interrupted)
        assertNotEquals(first, h.request.clientTurnId)
    }

    @Test
    fun voiceBargeInRunsInHeadphonesAndNeverTogetherWithTheRecognizer() = runTest {
        val h = EngineHarness(this)
        h.route.setRoute(AudioRoute.WiredHeadset)
        h.connect()
        assertFalse(h.barge.active.value)

        h.say("Hi")
        h.reply(say("t1", 0, "Hello there, how are you today?"))
        assertTrue(h.barge.active.value)
        assertFalse(h.input.listening.value)

        h.barge.speak()
        runCurrent()
        assertEquals(CallPhase.Listening(hearing = false, afterInterrupt = true), h.phase)
        assertFalse(h.barge.active.value)
        assertTrue(h.input.listening.value)
        assertEquals(BargeInMethod.Voice, h.eventsOf<CallEngineEvent.BargeIn>().single().method)
    }

    @Test
    fun voiceBargeInOnTheSpeakerNeedsTheEchoCancellerAndTheFlagNeverWithManualEndOfTurn() = runTest {
        fun EngineHarness.speakingWithBargeIn(): Boolean {
            connect()
            say("Hi")
            reply(say("t1", 0, "Hello."))
            return barge.active.value
        }

        assertTrue(EngineHarness(this).speakingWithBargeIn())
        assertFalse(EngineHarness(this).apply { route.echoCancelerAvailable = false }.speakingWithBargeIn())
        assertFalse(EngineHarness(this, callConfig(bargeInSpeakerEnabled = false)).speakingWithBargeIn())
        assertFalse(
            EngineHarness(this, callConfig(manualEndOfTurn = true)).apply { route.setRoute(AudioRoute.Bluetooth) }.speakingWithBargeIn(),
        )
    }

    // ----- Network -----

    @Test
    fun aLostNetworkReconnectsFor20sAndRetriesTheSameTurnId() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Question")
        val id = h.request.clientTurnId

        h.online.value = false
        runCurrent()
        h.reply(VoiceTurnEvent.Failed(VoiceCallError.Unreachable))
        assertEquals(CallPhase.Reconnecting(20), h.phase)
        advanceTimeBy(5_001)
        assertEquals(CallPhase.Reconnecting(15), h.phase)
        assertEquals(1, h.turns.requests.size)

        h.online.value = true
        runCurrent()
        assertEquals(2, h.turns.requests.size)
        assertEquals(id, h.request.clientTurnId)

        h.reply(say("t1", 0, "Here is the answer."))
        assertEquals(CallPhase.Speaking, h.phase)
        advanceTimeBy(30_000)
        assertNull(h.state.stop) // the countdown was cancelled
    }

    @Test
    fun noNetworkFor20sDropsTheCallAsOffline() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Question")
        h.online.value = false
        runCurrent()
        h.reply(VoiceTurnEvent.Failed(VoiceCallError.Unreachable))

        advanceTimeBy(20_001)
        runCurrent()
        assertEquals(CallStop.Dropped(DropCause.Offline), h.state.stop)
        assertEquals(CallEndReason.Offline, h.sessions.ends.single().reason)
        assertEquals(CallEndReason.Offline, h.eventsOf<CallEngineEvent.Ended>().single().reason)
    }

    @Test
    fun losingTheNetworkWhileListeningReconnectsAndListensAgainWhenItIsBack() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.online.value = false
        runCurrent()
        assertEquals(CallPhase.Reconnecting(20), h.phase)
        assertFalse(h.input.listening.value)

        h.online.value = true
        runCurrent()
        assertEquals(listening, h.phase)
        assertTrue(h.input.listening.value)
    }

    @Test
    fun turnInProgressIsRetriedEvery3sWithTheSameId() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Question")
        val id = h.request.clientTurnId
        h.reply(VoiceTurnEvent.Failed(VoiceCallError.TurnInProgress))
        assertTrue(h.phase is CallPhase.Thinking)

        advanceTimeBy(3_001)
        assertEquals(2, h.turns.requests.size)
        assertEquals(id, h.request.clientTurnId)
    }

    @Test
    fun turnInProgressForMoreThan90sGivesUpAndListens() = runTest {
        val h = EngineHarness(this)
        h.turns.reply = { flowOf(VoiceTurnEvent.Failed(VoiceCallError.TurnInProgress)) }
        h.connect()
        h.say("Question")
        advanceTimeBy(91_000)
        runCurrent()

        assertEquals(listening, h.phase)
        assertEquals(1, h.state.errors)
        assertEquals("turn_in_progress", h.eventsOf<CallEngineEvent.Error>().last().code)
    }

    // ----- Focus, background, resume -----

    @Test
    fun losingAudioFocusPausesTheCallAndRegainingItListensAgain() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.focus.emit(FocusChange.LossTransient)
        runCurrent()
        assertEquals(CallPhase.Paused(PauseReason.SystemInterruption), h.phase)
        assertFalse(h.input.listening.value)
        assertFalse(h.focus.held)
        // The engine keeps its request through an interruption: abandoning it would drop the listener and the Gain.
        assertEquals(0, h.focus.releaseCalls)
        val acquiresBeforeGain = h.focus.acquireCalls

        h.focus.emit(FocusChange.Gain)
        runCurrent()
        assertEquals(listening, h.phase)
        assertTrue(h.focus.held)
        // Resuming asks for the focus again (continueAfterPause), so a manual Resume after a permanent Loss works.
        assertTrue(h.focus.acquireCalls > acquiresBeforeGain)
    }

    @Test
    fun theRecognizerTakingTheFocusForItsUtteranceDoesNotPauseTheCall() = runTest {
        val h = EngineHarness(this)
        h.connect()
        assertTrue(h.input.listening.value)

        // The platform SpeechRecognizer requests AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE: the call gets LOSS_TRANSIENT.
        h.focus.systemChange(FocusChange.LossTransient)
        runCurrent()

        assertEquals(listening, h.phase)
        assertTrue(h.input.listening.value)
        assertTrue(h.eventsOf<CallEngineEvent.Paused>().isEmpty())
    }

    @Test
    fun everyUtteranceLendsTheFocusToItsRecognizerAlsoAfterAReplyAndAfterABargeIn() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.focus.systemChange(FocusChange.LossTransient)
        h.focus.systemChange(FocusChange.Gain)
        h.say("Hi")
        h.reply(*replyEvents("t1", "Hello there.").toTypedArray())
        h.finishSpeaking()
        assertEquals(listening, h.phase)
        h.focus.systemChange(FocusChange.LossTransient)
        runCurrent()
        assertEquals(listening, h.phase)
        h.focus.systemChange(FocusChange.Gain)

        h.say("And?")
        h.reply(say("t2", 0, "Well, first."))
        assertEquals(CallPhase.Speaking, h.phase)
        h.barge.speak()
        runCurrent()
        assertEquals(CallPhase.Listening(hearing = false, afterInterrupt = true), h.phase)
        h.focus.systemChange(FocusChange.LossTransient)
        runCurrent()

        assertTrue(h.phase is CallPhase.Listening)
        assertTrue(h.eventsOf<CallEngineEvent.Paused>().isEmpty())
        assertEquals(1, h.focus.acquireCalls) // the focus came back by itself every time
        h.dispatch(CallCommand.HangUp)
        assertEquals(0, h.focus.lent)
    }

    @Test
    fun aPhoneCallThatTookTheFocusFromTheRecognizerPausesTheCallWhenItClosesGainResumesIt() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.focus.systemChange(FocusChange.LossTransient) // the recognizer
        runCurrent()
        h.focus.acquireResult = false // a phone call locked the focus while the recognizer held it

        h.say("Hi")
        assertEquals(CallPhase.Paused(PauseReason.SystemInterruption), h.phase)
        assertEquals(0, h.focus.releaseCalls) // registered request: the Gain at the end of the phone call goes to it
        assertTrue(h.output.spoken.isEmpty())

        h.focus.acquireResult = true
        h.focus.systemChange(FocusChange.Gain)
        runCurrent()
        assertEquals(listening, h.phase)
        assertTrue(h.input.listening.value)
    }

    @Test
    fun aRecognizerCancelledBeforeItsFocusRequestArrivedDoesNotPauseTheCallTheNextLossDoes() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.focus.systemChange(FocusChange.LossTransient)
        h.focus.systemChange(FocusChange.Gain)
        h.say("Hi")
        h.reply(say("t1", 0, "Hello there."))
        h.barge.speak()
        runCurrent()
        assertTrue(h.phase is CallPhase.Listening)

        // Repeat cancels the recognizer; its service asks for the focus only afterwards (device: +90 ms), then gives it back.
        h.dispatch(CallCommand.Repeat)
        assertEquals(CallPhase.Speaking, h.phase)
        h.focus.systemChange(FocusChange.LossTransient)
        runCurrent()
        assertEquals(CallPhase.Speaking, h.phase)
        assertTrue(h.eventsOf<CallEngineEvent.Paused>().isEmpty())
        h.focus.systemChange(FocusChange.Gain)
        runCurrent()

        // That recognizer is accounted for: a phone call ringing now is an interruption.
        h.focus.systemChange(FocusChange.LossTransient)
        runCurrent()
        assertEquals(CallPhase.Paused(PauseReason.SystemInterruption), h.phase)
    }

    @Test
    fun aRecognizerThatFailedBeforeItsFocusRequestArrivedDoesNotPauseTheCall() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.input.emit(SpeechEvent.Error(SpeechErrorKind.Network)) // the recognizer closes at once
        runCurrent()
        h.focus.systemChange(FocusChange.LossTransient) // its service's late request
        runCurrent()

        assertTrue(h.phase is CallPhase.Listening)
        assertTrue(h.eventsOf<CallEngineEvent.Paused>().isEmpty())
    }

    @Test
    fun silenceBetweenRecognizerErrorsDoesNotEndTheCall() = runTest {
        val h = EngineHarness(this)
        h.connect()
        // Device log: every silent utterance ends in no_match, the immediate restart fails with a network code.
        repeat(3) {
            h.input.emit(SpeechEvent.Error(SpeechErrorKind.NoMatch))
            runCurrent()
            h.input.emit(SpeechEvent.Error(SpeechErrorKind.Network))
            runCurrent()
            advanceTimeBy(1_001)
        }

        assertNull(h.state.stop)
        assertEquals(listening, h.phase)
        assertEquals(3, h.eventsOf<CallEngineEvent.Error>().count { it.stage == "stt" })
    }

    @Test
    fun threeRecognizerErrorsInARowEndTheCall() = runTest {
        val h = EngineHarness(this)
        h.connect()
        repeat(3) {
            h.input.emit(SpeechEvent.Error(SpeechErrorKind.Network))
            runCurrent()
            advanceTimeBy(1_001)
        }

        assertEquals(CallStop.Dropped(DropCause.Error), h.state.stop)
        assertEquals(CallEndReason.Error, h.sessions.ends.single().reason)
    }

    @Test
    fun aPermanentLossWhileTheRecognizerListensStillPausesTheCall() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.focus.systemChange(FocusChange.Loss)
        runCurrent()
        assertEquals(CallPhase.Paused(PauseReason.SystemInterruption), h.phase)
        assertFalse(h.input.listening.value)
    }

    @Test
    fun anInterruptionKeepsTheFocusRequestSoTheSystemCanGiveTheFocusBack() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Hi")
        h.reply(say("t1", 0, "Hello there."))
        h.focus.emit(FocusChange.LossTransient)
        runCurrent()
        assertEquals(CallPhase.Paused(PauseReason.SystemInterruption), h.phase)
        assertEquals(0, h.focus.releaseCalls)

        h.dispatch(CallCommand.ScreenStopped) // the background still lets the focus go
        assertEquals(1, h.focus.releaseCalls)
    }

    @Test
    fun screenStoppedPausesTtsMicrophoneAndHeartbeatUntilResume() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Hi")
        h.reply(say("t1", 0, "Hello there."))
        h.dispatch(CallCommand.ScreenStopped)

        assertEquals(CallPhase.Paused(PauseReason.Background), h.phase)
        assertEquals(1, h.output.stopCalls)
        assertFalse(h.input.listening.value)
        assertFalse(h.barge.active.value)
        assertEquals(CallEngineEvent.Paused(PauseReason.Background), h.eventsOf<CallEngineEvent.Paused>().single())
        val elapsed = h.state.elapsedSec
        advanceTimeBy(30_000)
        assertTrue(h.sessions.heartbeats.isEmpty())
        assertEquals(elapsed, h.state.elapsedSec)

        h.dispatch(CallCommand.ScreenStarted)
        assertEquals(CallPhase.Paused(PauseReason.Background), h.phase) // "Tap to continue"
        h.dispatch(CallCommand.Resume)
        assertEquals(listening, h.phase)
        assertEquals(1, h.sessions.starts.size)
        advanceTimeBy(15_001)
        assertEquals(1, h.sessions.heartbeats.size)
    }

    @Test
    fun backAfterTheBackgroundTimeoutEndsTheCall() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.dispatch(CallCommand.ScreenStopped)
        advanceTimeBy(121_000)
        h.dispatch(CallCommand.ScreenStarted)

        assertEquals(CallStop.Ended(CallEndReason.Background), h.state.stop)
        assertEquals(CallEndReason.Background, h.sessions.ends.single().reason)
    }

    @Test
    fun resumeAfterTheServerClosedTheSessionStartsANewOne() = runTest {
        val h = EngineHarness(this)
        h.connect()
        val firstSession = h.state.sessionId
        h.dispatch(CallCommand.ScreenStopped)
        advanceTimeBy(50_000)
        h.dispatch(CallCommand.Resume)

        assertEquals(2, h.sessions.starts.size)
        assertNotEquals(firstSession, h.state.sessionId)
        assertEquals(listening, h.phase)
    }

    @Test
    fun aReplyThatArrivesWhilePausedIsCountedButNotSpoken() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Hi")
        h.dispatch(CallCommand.ScreenStopped)
        h.reply(say("t1", 0, "Hello."), done("t1", "Hello."))

        assertTrue(h.output.spoken.isEmpty())
        assertEquals(1, h.state.turns)
        h.dispatch(CallCommand.Resume)
        h.say("Again")
        assertEquals(0, h.request.interrupted?.spokenChars)
    }

    // ----- Server refusals -----

    @Test
    fun notEntitledEndsTheCallAsUnavailable() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Answer")
        h.reply(VoiceTurnEvent.Failed(VoiceCallError.NotEntitled("no_subscription")))
        assertEquals(CallStop.Unavailable(UnavailableReason.NotEntitled), h.state.stop)
    }

    @Test
    fun outOfCreditsOnATurnEndsTheCallAsUnavailableOutOfCredits() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Answer")
        h.reply(VoiceTurnEvent.Failed(VoiceCallError.OutOfCredits))

        assertEquals(CallStop.Unavailable(UnavailableReason.OutOfCredits), h.state.stop)
        assertEquals(CallEngineEvent.Error("llm", "out_of_credits"), h.eventsOf<CallEngineEvent.Error>().single())
        assertEquals(CallEndReason.Error, h.sessions.ends.single().reason)
    }

    // ----- Minutes -----

    @Test
    fun heartbeatEvery15sUpdatesTheQuotaAndFlagsTheLastMinute() = runTest {
        val h = EngineHarness(this)
        h.sessions.heartbeatResult = { _, _ -> VoiceResult.Success(VoiceHeartbeat(VoiceFixtures.freeQuota.copy(secondsLeft = 50), ended = false)) }
        h.connect()
        advanceTimeBy(15_001)

        assertEquals(15, h.sessions.heartbeats.single().second)
        assertEquals(50, h.state.quota?.secondsLeft)
        assertTrue(h.state.lastMinute)
    }

    @Test
    fun minutesRunningOutMidSentenceLetTheAgentFinishItThenTheCallEndsOnTheLimit() = runTest {
        val h = EngineHarness(this)
        val empty = VoiceFixtures.emptyFreeQuota
        h.sessions.heartbeatResult = { _, _ -> VoiceResult.Success(VoiceHeartbeat(empty, ended = true)) }
        h.connect()
        advanceTimeBy(14_000)
        h.say("Hi")
        h.reply(say("t1", 0, "Hello there."))
        advanceTimeBy(1_100)

        assertEquals(CallPhase.Speaking, h.phase)
        assertNull(h.state.stop)
        h.finishSpeaking()

        assertEquals(CallStop.LimitReached(empty, duringCall = true), h.state.stop)
        assertEquals(CallEndReason.Limit, h.sessions.ends.single().reason)
        assertEquals(CallEngineEvent.LimitReached(VoicePlan.Free, duringCall = true), h.eventsOf<CallEngineEvent.LimitReached>().single())
    }

    @Test
    fun theSoftLimitEndsTheCall() = runTest {
        val h = EngineHarness(this, callConfig(softLimitSec = 60))
        h.connect()
        advanceTimeBy(60_001)
        assertEquals(CallStop.Ended(CallEndReason.Limit), h.state.stop)
        assertEquals(60, h.sessions.ends.single().elapsedSec)
    }

    // ----- End -----

    @Test
    fun hangUpEndsTheSessionAndTakesTheQuotaTheServerSendsBack() = runTest {
        val h = EngineHarness(this)
        val after = VoiceFixtures.freeQuota.copy(secondsLeft = 295)
        h.sessions.endResult = { VoiceResult.Success(VoiceSessionEnd(quota = after, secondsCharged = it.elapsedSec)) }
        h.connect()
        advanceTimeBy(5_001) // the 5th tick is due at 5 000 ms; advanceTimeBy runs tasks strictly before the target

        h.engine.dispatch(CallCommand.HangUp)
        assertEquals(CallStop.Ended(CallEndReason.HangUp), h.state.stop)
        assertEquals(CallPhase.Idle, h.phase)
        runCurrent()

        assertEquals(after, h.state.quota)
        assertEquals(5, h.sessions.ends.single().elapsedSec)
        assertTrue(h.output.released)
        assertFalse(h.focus.held)
        assertFalse(h.input.listening.value)
        assertEquals(CallEngineEvent.Ended(CallEndReason.HangUp, 5, 0, 0, 0), h.eventsOf<CallEngineEvent.Ended>().single())
    }

    @Test
    fun aFailedSessionEndIsReportedAndTheCallStaysEnded() = runTest {
        val h = EngineHarness(this)
        h.sessions.endResult = { VoiceResult.Failure(VoiceCallError.Unavailable) }
        h.connect()
        h.dispatch(CallCommand.HangUp)

        assertEquals(CallStop.Ended(CallEndReason.HangUp), h.state.stop)
        assertEquals(CallEngineEvent.Error("session", "unavailable"), h.eventsOf<CallEngineEvent.Error>().single())
        assertEquals(VoiceFixtures.freeQuota, h.state.quota)
    }

    @Test
    fun aCutReplyGoesWithTheSessionEnd() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Story")
        val id = h.request.clientTurnId
        h.reply(say("t1", 0, "Once upon a time."))
        h.output.emit(SpeechOutputEvent.Range(h.output.spoken.single().utteranceId, endChar = 4))
        runCurrent()
        h.dispatch(CallCommand.HangUp)
        assertEquals(InterruptedTurn(id, spokenChars = 4), h.sessions.ends.single().interrupted)
    }

    // ----- Mute, repeat, rate, languages -----

    @Test
    fun muteClosesTheMicrophoneAndUnmuteListensAgain() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.dispatch(CallCommand.SetMuted(true))
        assertEquals(CallPhase.Muted, h.phase)
        assertFalse(h.input.listening.value)
        h.dispatch(CallCommand.SetMuted(false))
        assertEquals(listening, h.phase)
    }

    @Test
    fun aReplyEndsInMutedWhenTheUserMutedWhileItWasSpoken() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Hi")
        h.reply(say("t1", 0, "Hello."), done("t1", "Hello."))
        h.dispatch(CallCommand.SetMuted(true))
        h.finishSpeaking()
        assertEquals(CallPhase.Muted, h.phase)
    }

    @Test
    fun repeatSpeaksTheLastReplyAgainAndSlowerAppliesToTheNextSpeech() = runTest {
        val h = EngineHarness(this)
        h.connect()
        h.say("Hi")
        h.reply(say("t1", 0, "Hello."), done("t1", "Hello."))
        h.finishSpeaking()

        h.dispatch(CallCommand.SetRate(SpeechRate.Slower))
        h.dispatch(CallCommand.Repeat)
        assertEquals(CallPhase.Speaking, h.phase)
        assertEquals(2, h.output.spoken.size)
        assertEquals(h.output.spoken[0].segments, h.output.spoken[1].segments)
        assertEquals(SpeechRate.Slower, h.output.spoken[1].rate)
        assertEquals(1, h.turns.requests.size)
    }

    @Test
    fun theCallListensInItsLanguageAllowsTheExtraOnesAndSpeaksSegmentsInTheirOwnVoices() = runTest {
        val es = SpeechLocale("es-ES")
        val h = EngineHarness(this, callConfig(listenLocale = es, alsoAllow = listOf(EN_US)))
        h.connect()

        val listen = h.input.listenCalls.single()
        assertEquals(es, listen.locale)
        assertEquals(listOf(EN_US), listen.options.alsoAllow)
        assertEquals(setOf(es, EN_US), h.output.prepared.single())

        h.say("Hola")
        assertEquals(es, h.request.listenLocale)
        h.reply(VoiceTurnEvent.Say(TurnId("t1"), 0, listOf(SpokenSegment("Say", "en"), SpokenSegment("¿Qué tal?", "es"))))
        assertEquals(
            listOf(SpeechSegmentOut("Say", EN_US), SpeechSegmentOut("¿Qué tal?", es)),
            h.output.spoken.single().segments,
        )
        assertEquals("Say ¿Qué tal?", h.state.agent?.text)
    }

    @Test
    fun threeFailedTurnsInARowDropTheCall() = runTest {
        val h = EngineHarness(this)
        h.turns.reply = { flowOf(VoiceTurnEvent.Failed(VoiceCallError.ClientBug("bad_request"))) }
        h.connect()
        repeat(3) { h.say("Again $it") }

        assertEquals(CallStop.Dropped(DropCause.Error), h.state.stop)
        assertEquals(3, h.state.errors)
    }

    @Test
    fun manualEndOfTurnIsPassedToTheRecognizerAndFinishUtteranceEndsIt() = runTest {
        val h = EngineHarness(this, callConfig(manualEndOfTurn = true))
        h.connect()
        assertTrue(h.input.listenCalls.single().options.manualEndOfTurn)
        h.dispatch(CallCommand.FinishUtterance)
        assertEquals(1, h.input.finishCalls)
    }
}
