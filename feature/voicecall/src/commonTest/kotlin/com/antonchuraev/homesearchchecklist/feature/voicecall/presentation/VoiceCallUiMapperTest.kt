package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import com.antonchuraev.homesearchchecklist.core.voice.api.AgentUtterance
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.CallStop
import com.antonchuraev.homesearchchecklist.core.voice.api.ClientTurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.DropCause
import com.antonchuraev.homesearchchecklist.core.voice.api.PauseReason
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.ThinkingDelay
import com.antonchuraev.homesearchchecklist.core.voice.api.UnavailableReason
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceQuota
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VoiceCallUiMapperTest {

    private fun map(state: CallEngineState, facts: VoiceCallFacts = VoiceCallFacts()) =
        VoiceCallUiMapper.map(state, facts, formatLocalTime = { "03:00" })

    private fun inCall(state: CallEngineState, facts: VoiceCallFacts = VoiceCallFacts()) =
        assertIs<VoiceCallUiState.InCall>(map(state, facts))

    private fun quota(left: Int, call: Int? = null, plan: VoicePlan = VoicePlan.Free, resetsAt: Long? = null) =
        VoiceQuota(plan = plan, secondsLeft = left, secondsTotal = left, resetsAtEpochMs = resetsAt, callSecondsLeft = call)

    private val trip = VoiceAgentAction(title = "Trip", kind = "checklist_created", targetId = "7")
    private val three = VoiceAgentAction(title = "Trip", kind = "items_added", targetId = "7", count = 3)

    private fun reply(text: String, spoken: Int, interrupted: Boolean = false) =
        AgentUtterance(ClientTurnId("t"), turnId = null, text = text, spokenChars = spoken, interrupted = interrupted)

    // ── Running ──────────────────────────────────────────────────────────────

    @Test
    fun idleWithoutStop_isConnecting_notLive() {
        val ui = inCall(CallEngineState())
        assertEquals(VoiceCallPhaseUi.Connecting, ui.phase)
        assertEquals(false, ui.timer.live)
        assertNull(ui.timer.remainingSec)
    }

    @Test
    fun listeningWithPartial_showsUserBubbleNotFinal_firstTurnHint() {
        val ui = inCall(
            CallEngineState(phase = CallPhase.Listening(hearing = true, afterInterrupt = false), heard = "add mi", heardFinal = false),
        )
        assertEquals(VoiceCallPhaseUi.Listening(hearing = true), ui.phase)
        assertEquals("add mi", ui.transcript.userText)
        assertEquals(false, ui.transcript.userFinal)
        assertEquals(true, ui.timer.live)
        assertEquals(true, ui.firstTurn)
    }

    @Test
    fun thinkingOnATool_showsToolStatus_andTheFinalUtterance() {
        val ui = inCall(
            CallEngineState(
                phase = CallPhase.Thinking(ThinkingDelay.Normal, tool = "create_checklist"),
                heard = "create a trip list",
                heardFinal = true,
                turns = 1,
            ),
        )
        assertEquals(VoiceCallPhaseUi.Thinking(VoiceThinkingDelayUi.Normal, VoiceToolStatusUi.CreatingChecklist), ui.phase)
        assertEquals("create a trip list", ui.transcript.userText)
        assertEquals(true, ui.transcript.userFinal)
        assertEquals(false, ui.firstTurn)
    }

    @Test
    fun toolNames_mapToStatuses_unknownIsPlainThinking() {
        assertEquals(VoiceToolStatusUi.AddingTasks, VoiceCallUiMapper.toolOf("add_items"))
        assertEquals(VoiceToolStatusUi.AddingTasks, VoiceCallUiMapper.toolOf("add_item"))
        assertEquals(VoiceToolStatusUi.Searching, VoiceCallUiMapper.toolOf("find_items"))
        assertEquals(VoiceToolStatusUi.Searching, VoiceCallUiMapper.toolOf("read_checklist"))
        assertNull(VoiceCallUiMapper.toolOf("delete_item"))
        assertNull(VoiceCallUiMapper.toolOf(null))
    }

    @Test
    fun slowThinking_mapsDelay() {
        val ui = inCall(CallEngineState(phase = CallPhase.Thinking(ThinkingDelay.VerySlow)))
        assertEquals(VoiceCallPhaseUi.Thinking(VoiceThinkingDelayUi.VerySlow, tool = null), ui.phase)
    }

    @Test
    fun speaking_captionFollowsSpokenChars_andTheUserBubbleIsGone() {
        val ui = inCall(
            CallEngineState(phase = CallPhase.Speaking, heard = "hi", heardFinal = true, agent = reply("Hello there.", spoken = 5)),
        )
        assertEquals(VoiceReplyUi("Hello there.", spokenChars = 5), ui.transcript.reply)
        assertNull(ui.transcript.userText)
    }

    @Test
    fun captionsOff_isCarriedFromFacts() {
        val ui = inCall(CallEngineState(phase = CallPhase.Speaking), VoiceCallFacts(captionsOn = false))
        assertEquals(false, ui.captionsOn)
    }

    @Test
    fun listeningAfterACutReply_keepsCaptionAtWhatWasHeard() {
        val ui = inCall(
            CallEngineState(
                phase = CallPhase.Listening(hearing = false, afterInterrupt = true),
                agent = reply("Hello there. More text.", spoken = 6, interrupted = true),
            ),
        )
        assertEquals(VoiceReplyUi("Hello there. More text.", spokenChars = 6), ui.transcript.reply)
    }

    @Test
    fun muted_micOffAndMutedPhase() {
        val ui = inCall(CallEngineState(phase = CallPhase.Muted, muted = true))
        assertEquals(VoiceCallPhaseUi.Muted, ui.phase)
        assertEquals(false, ui.micOn)
        assertEquals(false, ui.timer.live)
    }

    @Test
    fun reconnecting_bannerWinsOverLimitWarning() {
        val ui = inCall(CallEngineState(phase = CallPhase.Reconnecting(14), quota = quota(left = 40)))
        assertEquals(VoiceCallPhaseUi.Reconnecting(14), ui.phase)
        assertEquals(VoiceCallBannerUi.Reconnecting(14), ui.banner)
    }

    @Test
    fun pausedReasons_map() {
        assertEquals(
            VoiceCallPhaseUi.Paused(VoicePauseReasonUi.Background),
            inCall(CallEngineState(phase = CallPhase.Paused(PauseReason.Background))).phase,
        )
        assertEquals(
            VoiceCallPhaseUi.Paused(VoicePauseReasonUi.SystemInterruption),
            inCall(CallEngineState(phase = CallPhase.Paused(PauseReason.SystemInterruption))).phase,
        )
    }

    @Test
    fun timer_countsDownLocallyFromTheLastQuota_cappedByTheCallMaximum() {
        val state = CallEngineState(
            phase = CallPhase.Listening(hearing = false, afterInterrupt = false),
            elapsedSec = 40,
            quota = quota(left = 300, call = 200),
        )
        val ui = inCall(state, VoiceCallFacts(quotaAtElapsedSec = 30))
        // min(300, 200) minus the 10 s ticked since the quota arrived.
        assertEquals(190, ui.timer.remainingSec)
        assertEquals(40, ui.timer.elapsedSec)
        assertNull(ui.banner)
    }

    @Test
    fun lastMinuteOfAccountMinutes_showsLimitWarning_minutesRoundedUp() {
        val ui = inCall(
            CallEngineState(phase = CallPhase.Speaking, elapsedSec = 100, quota = quota(left = 59, call = 400)),
            VoiceCallFacts(quotaAtElapsedSec = 100),
        )
        assertEquals(VoiceCallBannerUi.LimitWarning(VoicePlanUi.Free, minutesLeft = 1), ui.banner)
        assertEquals(true, ui.timer.warning)
    }

    @Test
    fun rateAndManualEndOfTurn_map() {
        val ui = inCall(CallEngineState(rate = SpeechRate.Slower), VoiceCallFacts(manualEndOfTurn = true))
        assertEquals(VoiceSpeechRateUi.Slower, ui.rate)
        assertEquals(true, ui.manualEndOfTurn)
    }

    @Test
    fun lastActionCard_isTheNewest_withItemsCount() {
        val ui = inCall(CallEngineState(phase = CallPhase.Speaking, actions = listOf(trip, three)))
        assertEquals(
            VoiceActionUi(id = "a1", kind = VoiceActionKind.ItemsAdded, title = "Trip", itemsCount = 3, checklistId = "7"),
            ui.transcript.lastAction,
        )
    }

    @Test
    fun actionKinds_map() {
        assertEquals(VoiceActionKind.ChecklistCreated, VoiceCallUiMapper.actionUi(0, trip).kind)
        assertEquals(VoiceActionKind.TaskAdded, VoiceCallUiMapper.actionUi(0, VoiceAgentAction("Milk", "item_added")).kind)
        assertEquals(VoiceActionKind.Updated, VoiceCallUiMapper.actionUi(0, VoiceAgentAction("Milk", "updated")).kind)
        assertNull(VoiceCallUiMapper.actionUi(0, VoiceAgentAction("Milk", "item_added")).checklistId)
    }

    // ── Stopped ──────────────────────────────────────────────────────────────

    @Test
    fun limitBeforeTheCall_free_isLimitScreenWithoutActions() {
        val ui = map(CallEngineState(stop = CallStop.LimitReached(quota(left = 0), duringCall = false)))
        assertEquals(VoiceCallUiState.LimitReached(plan = VoicePlanUi.Free, resetsAtLocalTime = null, actions = emptyList()), ui)
    }

    @Test
    fun limitDuringTheCall_premium_keepsActionsAndResetTime() {
        val ui = map(
            CallEngineState(
                stop = CallStop.LimitReached(quota(left = 0, plan = VoicePlan.Premium, resetsAt = 1_000L), duringCall = true),
                actions = listOf(trip),
            ),
        )
        val limit = assertIs<VoiceCallUiState.LimitReached>(ui)
        assertEquals(VoicePlanUi.Premium, limit.plan)
        assertEquals("03:00", limit.resetsAtLocalTime)
        assertEquals(1, limit.actions.size)
    }

    @Test
    fun serverEndedWithMinutesLeft_isTheCallMaximumSummary() {
        val ui = map(
            CallEngineState(stop = CallStop.LimitReached(quota(left = 120), duringCall = true), elapsedSec = 600, quota = quota(left = 120)),
        )
        val summary = assertIs<VoiceCallUiState.Summary>(ui)
        assertEquals(VoiceCallSummaryReason.CallCap, summary.reason)
        assertEquals(600, summary.durationSec)
        assertEquals(true, summary.callAgainAvailable)
        assertEquals(120, summary.freeSecondsLeft)
    }

    private fun serverEnded(reason: VoiceSessionEndReason?, left: Int, plan: VoicePlan = VoicePlan.Premium) = map(
        CallEngineState(
            stop = CallStop.LimitReached(quota(left = left, plan = plan), duringCall = true, serverReason = reason),
            elapsedSec = 120,
            quota = quota(left = left, plan = plan),
            actions = listOf(trip),
        ),
    )

    @Test
    fun serverReasonMaxCallDuration_isTheCallMaximumSummary_evenAtZeroMinutes() {
        // At 0 minutes the old by-remaining rule would have said "minutes ran out".
        assertEquals(
            VoiceCallSummaryReason.CallCap,
            assertIs<VoiceCallUiState.Summary>(serverEnded(VoiceSessionEndReason.MaxCallDuration, left = 0)).reason,
        )
        assertEquals(
            VoiceCallSummaryReason.CallCap,
            assertIs<VoiceCallUiState.Summary>(serverEnded(VoiceSessionEndReason.MaxCallDuration, left = 1080)).reason,
        )
    }

    @Test
    fun serverReasonQuotaExhausted_isTheLimitScreen_evenWithSecondsReported() {
        // The heartbeat's minutes may lag the verdict; the server's reason wins over the number.
        val limit = assertIs<VoiceCallUiState.LimitReached>(serverEnded(VoiceSessionEndReason.QuotaExhausted, left = 30, plan = VoicePlan.Free))
        assertEquals(VoicePlanUi.Free, limit.plan)
        assertEquals(1, limit.actions.size)
        assertIs<VoiceCallUiState.LimitReached>(serverEnded(VoiceSessionEndReason.QuotaExhausted, left = 0))
    }

    @Test
    fun serverReasonUnknown_fallsBackToTheMinutesLeft() {
        assertEquals(
            VoiceCallSummaryReason.CallCap,
            assertIs<VoiceCallUiState.Summary>(serverEnded(reason = null, left = 1080)).reason,
        )
        assertIs<VoiceCallUiState.LimitReached>(serverEnded(reason = null, left = 0))
        assertEquals(
            VoiceCallSummaryReason.CallCap,
            assertIs<VoiceCallUiState.Summary>(serverEnded(VoiceSessionEndReason.ClientEnd, left = 600)).reason,
        )
    }

    @Test
    fun serverKillSwitchMidCall_isTheFlagOffFailure_withoutCallAgain() {
        // The engine stops a `disabled` heartbeat as Unavailable(FlagOff), whatever minutes are left.
        val ui = map(
            CallEngineState(stop = CallStop.Unavailable(UnavailableReason.FlagOff), elapsedSec = 120, quota = quota(left = 1080), actions = listOf(trip)),
        )
        assertEquals(VoiceCallUiState.Failure(VoiceCallFailureUi.FlagOff), ui)
    }

    @Test
    fun serverIdleClose_isTheConnectionLost_notTheCallMaximum() {
        // The engine stops an `idle` heartbeat as Dropped(Offline).
        assertEquals(
            VoiceCallUiState.Failure(VoiceCallFailureUi.Offline),
            map(CallEngineState(stop = CallStop.Dropped(DropCause.Offline), elapsedSec = 120, quota = quota(left = 1080))),
        )
        assertEquals(
            VoiceCallSummaryReason.Dropped,
            assertIs<VoiceCallUiState.Summary>(
                map(CallEngineState(stop = CallStop.Dropped(DropCause.Offline), elapsedSec = 120, quota = quota(left = 1080), actions = listOf(trip))),
            ).reason,
        )
    }

    @Test
    fun unavailableReasons_mapToFailures() {
        fun failure(reason: UnavailableReason) =
            assertIs<VoiceCallUiState.Failure>(map(CallEngineState(stop = CallStop.Unavailable(reason)))).kind
        assertEquals(VoiceCallFailureUi.NoRecognizer, failure(UnavailableReason.NoRecognizer))
        assertEquals(VoiceCallFailureUi.NoVoice, failure(UnavailableReason.NoVoice))
        assertEquals(VoiceCallFailureUi.FlagOff, failure(UnavailableReason.FlagOff))
        assertEquals(VoiceCallFailureUi.Offline, failure(UnavailableReason.Offline))
        assertEquals(VoiceCallFailureUi.Server, failure(UnavailableReason.NotEntitled))
        assertEquals(VoiceCallFailureUi.Server, failure(UnavailableReason.OutOfCredits))
    }

    @Test
    fun droppedWithNothingCreated_isAFailure_byCause() {
        assertEquals(
            VoiceCallUiState.Failure(VoiceCallFailureUi.Offline),
            map(CallEngineState(stop = CallStop.Dropped(DropCause.Offline))),
        )
        assertEquals(
            VoiceCallUiState.Failure(VoiceCallFailureUi.Server),
            map(CallEngineState(stop = CallStop.Dropped(DropCause.ConnectTimeout))),
        )
        assertEquals(
            VoiceCallUiState.Failure(VoiceCallFailureUi.Server),
            map(CallEngineState(stop = CallStop.Dropped(DropCause.Error))),
        )
    }

    @Test
    fun droppedAfterSomethingWasCreated_isTheDroppedSummary() {
        val summary = assertIs<VoiceCallUiState.Summary>(
            map(CallEngineState(stop = CallStop.Dropped(DropCause.Offline), actions = listOf(trip), elapsedSec = 75)),
        )
        assertEquals(VoiceCallSummaryReason.Dropped, summary.reason)
        assertEquals(1, summary.actions.size)
    }

    @Test
    fun shortHangUpWithNothingCreated_closesWithoutSummary() {
        assertEquals(
            VoiceCallUiState.Closed,
            map(CallEngineState(stop = CallStop.Ended(CallEndReason.HangUp), elapsedSec = 9)),
        )
    }

    @Test
    fun hangUpAfterTenSeconds_orWithAnAction_showsTheSummary() {
        assertEquals(
            VoiceCallSummaryReason.User,
            assertIs<VoiceCallUiState.Summary>(map(CallEngineState(stop = CallStop.Ended(CallEndReason.HangUp), elapsedSec = 10))).reason,
        )
        assertEquals(
            VoiceCallSummaryReason.User,
            assertIs<VoiceCallUiState.Summary>(
                map(CallEngineState(stop = CallStop.Ended(CallEndReason.HangUp), elapsedSec = 3, actions = listOf(trip))),
            ).reason,
        )
    }

    @Test
    fun backgroundTimeout_isTheBackgroundSummary() {
        val summary = assertIs<VoiceCallUiState.Summary>(
            map(CallEngineState(stop = CallStop.Ended(CallEndReason.Background), elapsedSec = 3)),
        )
        assertEquals(VoiceCallSummaryReason.Background, summary.reason)
    }

    @Test
    fun summary_noMinutesLeft_hidesCallAgain_premiumHasNoFreeLine() {
        val summary = assertIs<VoiceCallUiState.Summary>(
            map(
                CallEngineState(
                    stop = CallStop.Ended(CallEndReason.HangUp),
                    elapsedSec = 60,
                    quota = quota(left = 0, plan = VoicePlan.Premium),
                ),
            ),
        )
        assertEquals(false, summary.callAgainAvailable)
        assertNull(summary.freeSecondsLeft)
    }

    @Test
    fun spokenDurationParts_dropZeroParts() {
        assertEquals(DurationParts(minutes = 1, seconds = 48), durationParts(108))
        assertEquals(DurationParts(minutes = 2, seconds = null), durationParts(120))
        assertEquals(DurationParts(minutes = null, seconds = 7), durationParts(7))
        assertEquals(DurationParts(minutes = null, seconds = 0), durationParts(0))
    }
}
