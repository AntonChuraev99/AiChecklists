package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.AgentUtterance
import com.antonchuraev.homesearchchecklist.core.voice.api.BargeInMethod
import com.antonchuraev.homesearchchecklist.core.voice.api.CallCommand
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.CallStop
import com.antonchuraev.homesearchchecklist.core.voice.api.ClientTurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechAvailability
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechLocale
import com.antonchuraev.homesearchchecklist.core.voice.api.UnavailableReason
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCapabilities
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceQuota
import com.antonchuraev.homesearchchecklist.feature.voicecall.fakes.FakeVoiceCallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCallViewModelTest {

    private class FakeCapabilities(var permission: Boolean = true, var supported: Boolean = true) : VoiceCapabilities {
        override val isCallSupported: Boolean get() = supported
        override suspend fun recognizer(locale: SpeechLocale) = SpeechAvailability.OnDevice
        override val touchExplorationEnabled: Boolean = false
        override fun hasRecordPermission(): Boolean = permission
    }

    private class FakeAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) = Unit
        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) {
            events += name to params
        }
    }

    private object NoLog : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private val factory = FakeVoiceCallEngine.Factory()
    private val capabilities = FakeCapabilities()
    private val analytics = FakeAnalytics()
    private val effects = mutableListOf<VoiceCallEffect>()
    private lateinit var vm: VoiceCallViewModel
    private lateinit var effectScope: CoroutineScope

    private val engine get() = factory.last
    private val listening = CallPhase.Listening(hearing = false, afterInterrupt = false)
    private val trip = VoiceAgentAction(title = "Trip", kind = "checklist_created", targetId = "7")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        effectScope = CoroutineScope(Dispatchers.Main)
        vm = VoiceCallViewModel(
            newEngineFactory = { factory },
            capabilities = capabilities,
            analytics = analytics,
            logger = NoLog,
            languageTag = { "ru" },
            formatLocalTime = { "03:00" },
        )
        vm.effects.onEach { effects += it }.launchIn(effectScope)
    }

    @AfterTest
    fun tearDown() {
        effectScope.cancel()
        Dispatchers.resetMain()
    }

    private fun startCall() = vm.onAction(VoiceCallAction.Start(surface = "home"))

    @Test
    fun start_withPermission_createsAnEngine_startsIt_andShowsConnecting() {
        startCall()

        assertEquals(listOf<CallCommand>(CallCommand.Start), engine.commands)
        assertEquals(SpeechLocale("ru-RU"), engine.config.listenLocale)
        assertEquals("ru", engine.config.uiLocale)
        assertEquals(VoiceCallPhaseUi.Connecting, assertIs<VoiceCallUiState.InCall>(vm.uiState.value).phase)
        assertEquals(AnalyticsEvents.VoiceCall.TAPPED, analytics.events.first().first)
    }

    @Test
    fun doubleTapOnTheEntry_startsOneCall() {
        startCall()
        startCall()
        assertEquals(1, factory.created.size)
    }

    @Test
    fun start_withoutPermission_asksThroughThePermissionScreen_oneDialogInFlight() {
        capabilities.permission = false
        startCall()
        assertEquals(VoiceCallUiState.Permission(blocked = false), vm.uiState.value)

        vm.onAction(VoiceCallAction.AllowMicClicked)
        vm.onAction(VoiceCallAction.AllowMicClicked)

        assertEquals(VoiceCallUiState.Permission(blocked = false, requesting = true), vm.uiState.value)
        assertEquals(listOf<VoiceCallEffect>(VoiceCallEffect.RequestMicPermission), effects)
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun permissionGranted_startsTheCall() {
        capabilities.permission = false
        startCall()
        vm.onAction(VoiceCallAction.AllowMicClicked)
        capabilities.permission = true
        vm.onAction(VoiceCallAction.MicPermissionResult(granted = true, canAskAgain = true))

        assertEquals(listOf<CallCommand>(CallCommand.Start), engine.commands)
        assertIs<VoiceCallUiState.InCall>(vm.uiState.value)
    }

    @Test
    fun permissionDeniedForGood_blocksAndThenOpensSettings_returnWithPermissionStarts() {
        capabilities.permission = false
        startCall()
        vm.onAction(VoiceCallAction.AllowMicClicked)
        vm.onAction(VoiceCallAction.MicPermissionResult(granted = false, canAskAgain = false))
        assertEquals(VoiceCallUiState.Permission(blocked = true), vm.uiState.value)

        vm.onAction(VoiceCallAction.AllowMicClicked)
        assertEquals(VoiceCallEffect.OpenAppSettings, effects.last())

        capabilities.permission = true
        vm.onAction(VoiceCallAction.ScreenStarted(micGranted = true))
        assertEquals(1, factory.created.size)
    }

    @Test
    fun notNow_closes() {
        capabilities.permission = false
        startCall()
        vm.onAction(VoiceCallAction.NotNowClicked)
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
    }

    @Test
    fun engineState_isMappedOntoTheOverlay() {
        startCall()
        engine.set(CallEngineState(phase = listening, quota = VoiceQuota(VoicePlan.Free, 300, 300, null)))
        val ui = assertIs<VoiceCallUiState.InCall>(vm.uiState.value)
        assertEquals(VoiceCallPhaseUi.Listening(hearing = false), ui.phase)
        assertEquals(300, ui.timer.remainingSec)
    }

    @Test
    fun orbWhileSpeaking_interruptsByTap() {
        startCall()
        engine.set(CallEngineState(phase = CallPhase.Speaking))
        vm.onAction(VoiceCallAction.OrbClicked)
        assertEquals(CallCommand.Interrupt(BargeInMethod.Tap), engine.commands.last())
    }

    @Test
    fun micAndCaptionsAndRate_reachTheEngineOrTheUi() {
        startCall()
        engine.set(CallEngineState(phase = listening))
        vm.onAction(VoiceCallAction.MicToggled)
        assertEquals(CallCommand.SetMuted(true), engine.commands.last())

        vm.onAction(VoiceCallAction.CaptionsToggled)
        assertEquals(false, assertIs<VoiceCallUiState.InCall>(vm.uiState.value).captionsOn)
    }

    @Test
    fun endAndBack_inCall_hangUp_shortCallWithNothingCreated_closes() {
        startCall()
        engine.onCommand = { command ->
            if (command == CallCommand.HangUp) engine.update { it.copy(phase = CallPhase.Idle, stop = CallStop.Ended(CallEndReason.HangUp)) }
        }
        engine.set(CallEngineState(phase = listening, elapsedSec = 4))
        vm.onAction(VoiceCallAction.BackPressed)

        assertEquals(CallCommand.HangUp, engine.commands.last())
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
    }

    @Test
    fun actionCardInCall_endsTheCall_closes_andOpensTheChecklistWithTheDuration() {
        startCall()
        engine.set(CallEngineState(phase = CallPhase.Speaking, elapsedSec = 75, actions = listOf(trip)))

        vm.onAction(VoiceCallAction.ActionCardClicked("a0"))

        assertEquals(CallCommand.HangUp, engine.commands.last())
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
        assertEquals(VoiceCallEffect.OpenChecklist("7", callDurationSec = 75), effects.last())
    }

    @Test
    fun actionCardOnTheSummary_opensWithoutSnackbarDuration() {
        startCall()
        engine.set(CallEngineState(stop = CallStop.Ended(CallEndReason.HangUp), elapsedSec = 75, actions = listOf(trip)))
        assertIs<VoiceCallUiState.Summary>(vm.uiState.value)

        vm.onAction(VoiceCallAction.ActionCardClicked("a0"))

        assertEquals(VoiceCallEffect.OpenChecklist("7", callDurationSec = null), effects.last())
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
    }

    @Test
    fun bannerPremium_endsTheCallAndOpensThePaywallWithTheWarningSource() {
        startCall()
        engine.set(CallEngineState(phase = listening))
        vm.onAction(VoiceCallAction.BannerPremiumClicked)

        assertEquals(CallCommand.HangUp, engine.commands.last())
        assertEquals(VoiceCallEffect.OpenPaywall(VoiceCallPaywallSource.WARNING), effects.last())
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
    }

    @Test
    fun limitScreen_tryPremium_opensThePaywallWithTheLimitSource() {
        startCall()
        engine.set(CallEngineState(stop = CallStop.LimitReached(VoiceQuota(VoicePlan.Free, 0, 0, null), duringCall = false)))
        vm.onAction(VoiceCallAction.TryPremiumClicked)
        assertEquals(VoiceCallEffect.OpenPaywall(VoiceCallPaywallSource.LIMIT), effects.last())
    }

    @Test
    fun failure_retry_startsANewEngine_andReportsTheFailureOnce() {
        startCall()
        engine.set(CallEngineState(stop = CallStop.Unavailable(UnavailableReason.Offline)))
        engine.set(CallEngineState(stop = CallStop.Unavailable(UnavailableReason.Offline), elapsedSec = 0, errors = 1))
        assertEquals(VoiceCallUiState.Failure(VoiceCallFailureUi.Offline), vm.uiState.value)
        assertEquals(1, analytics.events.count { it.first == AnalyticsEvents.VoiceCall.FAILED })

        vm.onAction(VoiceCallAction.RetryClicked)

        assertEquals(2, factory.created.size)
        assertIs<VoiceCallUiState.InCall>(vm.uiState.value)
    }

    @Test
    fun openChat_fromAFailure_closesAndOpensTheChatSheet() {
        startCall()
        engine.set(CallEngineState(stop = CallStop.Unavailable(UnavailableReason.NoRecognizer)))
        vm.onAction(VoiceCallAction.OpenChatClicked)
        assertEquals(VoiceCallEffect.OpenChatSheet, effects.last())
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
    }

    @Test
    fun noVoice_opensTheTtsSettings() {
        startCall()
        engine.set(CallEngineState(stop = CallStop.Unavailable(UnavailableReason.NoVoice)))
        vm.onAction(VoiceCallAction.OpenVoiceSettingsClicked)
        assertEquals(VoiceCallEffect.OpenTtsSettings, effects.last())
    }

    @Test
    fun lifecycle_reachesTheEngine() {
        startCall()
        engine.set(CallEngineState(phase = listening))
        vm.onAction(VoiceCallAction.ScreenStopped)
        vm.onAction(VoiceCallAction.ScreenStarted(micGranted = true))
        assertEquals(listOf(CallCommand.Start, CallCommand.ScreenStopped, CallCommand.ScreenStarted), engine.commands)
    }

    @Test
    fun engineEvents_becomeVoiceCallAnalytics_withoutUtteranceText() {
        startCall()
        engine.emit(CallEngineEvent.Started(SpeechAvailability.OnDevice, VoiceQuota(VoicePlan.Free, 300, 300, null), stream = false))
        engine.emit(CallEngineEvent.Ended(CallEndReason.HangUp, durationSec = 42, turns = 3, interruptions = 1, errors = 0))

        val started = analytics.events.first { it.first == AnalyticsEvents.VoiceCall.STARTED }.second
        assertEquals(mapOf("surface" to "home", "plan" to "free", "seconds_left" to 300, "stt" to "on_device"), started)
        val ended = analytics.events.first { it.first == AnalyticsEvents.VoiceCall.ENDED }.second
        assertEquals("hangup", ended["reason"])
        assertEquals(42, ended["duration_sec"])
    }

    @Test
    fun closedOverlay_ignoresLaterEngineUpdates() {
        startCall()
        engine.set(CallEngineState(stop = CallStop.Ended(CallEndReason.HangUp), elapsedSec = 30))
        vm.onAction(VoiceCallAction.CloseClicked)
        // The settled session end arrives after the user closed the summary.
        engine.update { it.copy(quota = VoiceQuota(VoicePlan.Free, 100, 100, null)) }
        assertEquals(VoiceCallUiState.Closed, vm.uiState.value)
    }

    @Test
    fun speakingCaption_isTheEngineReply() {
        startCall()
        engine.set(
            CallEngineState(
                phase = CallPhase.Speaking,
                agent = AgentUtterance(ClientTurnId("t"), null, "Hello.", spokenChars = 3, interrupted = false),
            ),
        )
        assertEquals(VoiceReplyUi("Hello.", 3), assertIs<VoiceCallUiState.InCall>(vm.uiState.value).transcript.reply)
    }
}
