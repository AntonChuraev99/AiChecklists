package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import com.antonchuraev.homesearchchecklist.core.voice.api.CallEndReason
import com.antonchuraev.homesearchchecklist.core.voice.api.CallEngineState
import com.antonchuraev.homesearchchecklist.core.voice.api.CallPhase
import com.antonchuraev.homesearchchecklist.core.voice.api.CallStop
import com.antonchuraev.homesearchchecklist.core.voice.api.DropCause
import com.antonchuraev.homesearchchecklist.core.voice.api.PauseReason
import com.antonchuraev.homesearchchecklist.core.voice.api.SpeechRate
import com.antonchuraev.homesearchchecklist.core.voice.api.ThinkingDelay
import com.antonchuraev.homesearchchecklist.core.voice.api.UnavailableReason
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndReason

/**
 * What the ViewModel knows about the running call besides the engine's state.
 *
 * @property captionsOn the user's captions toggle (UI-only, not the engine's).
 * @property manualEndOfTurn TalkBack mode of this call ([com.antonchuraev.homesearchchecklist.core.voice.api.CallConfig]).
 * @property quotaAtElapsedSec the call's elapsed seconds when the current [CallEngineState.quota] arrived:
 *   the timer counts down locally from it until the next heartbeat corrects it.
 */
data class VoiceCallFacts(
    val captionsOn: Boolean = true,
    val manualEndOfTurn: Boolean = false,
    val quotaAtElapsedSec: Int = 0,
)

/**
 * The engine's [CallEngineState] → the overlay's [VoiceCallUiState] — one pure function per row of the
 * FEATURE_SPEC matrix, so every state the screen can draw is reachable from a test without an engine.
 *
 * Not mapped here (the ViewModel owns them): [VoiceCallUiState.Closed] before a call and
 * [VoiceCallUiState.Permission].
 */
object VoiceCallUiMapper {

    /** A hang-up shorter than this with nothing created skips the summary (straight to Closed). */
    const val SHORT_CALL_SEC = 10

    /**
     * @param formatLocalTime epoch ms → local wall-clock time ("03:00") for the premium reset line.
     */
    fun map(
        state: CallEngineState,
        facts: VoiceCallFacts,
        formatLocalTime: (Long) -> String,
    ): VoiceCallUiState {
        val actions = state.actions.mapIndexed(::actionUi)
        return when (val stop = state.stop) {
            null -> inCall(state, facts, actions)
            is CallStop.LimitReached -> limit(stop, state, actions, formatLocalTime)
            is CallStop.Unavailable -> VoiceCallUiState.Failure(failureOf(stop.reason))
            is CallStop.Dropped -> dropped(stop.cause, state, actions)
            is CallStop.Ended -> ended(stop.reason, state, actions)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------------

    private fun inCall(state: CallEngineState, facts: VoiceCallFacts, actions: List<VoiceActionUi>): VoiceCallUiState.InCall {
        val phase = phaseOf(state.phase)
        val tick = (state.elapsedSec - facts.quotaAtElapsedSec).coerceAtLeast(0)
        val quota = state.quota
        val accountLeft = quota?.let { (it.secondsLeft - tick).coerceAtLeast(0) }
        val callLeft = quota?.callSecondsLeft?.let { (it - tick).coerceAtLeast(0) }
        val remaining = when {
            accountLeft == null -> null
            callLeft == null -> accountLeft
            else -> minOf(accountLeft, callLeft)
        }
        val banner = when {
            phase is VoiceCallPhaseUi.Reconnecting -> VoiceCallBannerUi.Reconnecting(phase.secondsLeft)
            quota != null && accountLeft != null && accountLeft <= CallTimerUi.WARNING_THRESHOLD_SEC ->
                VoiceCallBannerUi.LimitWarning(planOf(quota.plan), minutesLeftRoundedUp(accountLeft))
            else -> null
        }
        return VoiceCallUiState.InCall(
            phase = phase,
            timer = CallTimerUi(
                elapsedSec = state.elapsedSec,
                remainingSec = remaining,
                live = phase is VoiceCallPhaseUi.Listening || phase is VoiceCallPhaseUi.Thinking || phase == VoiceCallPhaseUi.Speaking,
            ),
            banner = banner,
            transcript = transcriptOf(state, phase, actions),
            micOn = !state.muted,
            captionsOn = facts.captionsOn,
            rate = when (state.rate) {
                SpeechRate.Normal -> VoiceSpeechRateUi.Normal
                SpeechRate.Slower -> VoiceSpeechRateUi.Slower
            },
            firstTurn = state.turns == 0,
            // The engine cuts the reply on a tap whenever it is speaking; voice barge-in is its own matter.
            interruptible = true,
            manualEndOfTurn = facts.manualEndOfTurn,
        )
    }

    /** Idle without a stop = the call was asked to start and the engine has not moved yet. */
    fun phaseOf(phase: CallPhase): VoiceCallPhaseUi = when (phase) {
        CallPhase.Idle, CallPhase.Connecting -> VoiceCallPhaseUi.Connecting
        is CallPhase.Listening -> VoiceCallPhaseUi.Listening(hearing = phase.hearing)
        is CallPhase.Thinking -> VoiceCallPhaseUi.Thinking(
            delay = when (phase.delay) {
                ThinkingDelay.Normal -> VoiceThinkingDelayUi.Normal
                ThinkingDelay.Slow -> VoiceThinkingDelayUi.Slow
                ThinkingDelay.VerySlow -> VoiceThinkingDelayUi.VerySlow
            },
            tool = toolOf(phase.tool),
        )
        CallPhase.Speaking -> VoiceCallPhaseUi.Speaking
        CallPhase.Muted -> VoiceCallPhaseUi.Muted
        is CallPhase.Reconnecting -> VoiceCallPhaseUi.Reconnecting(phase.secondsLeft)
        is CallPhase.Paused -> VoiceCallPhaseUi.Paused(
            when (phase.reason) {
                PauseReason.Background -> VoicePauseReasonUi.Background
                PauseReason.SystemInterruption -> VoicePauseReasonUi.SystemInterruption
            },
        )
    }

    /**
     * `chat_agent` tool name (server `CHAT_AGENT_TOOL_NAMES`) → the localized status the client shows.
     * Tools without a status of their own (complete, delete, reminders, rename, move) read as plain
     * "Thinking…": the card that follows says what was done.
     */
    fun toolOf(tool: String?): VoiceToolStatusUi? = when (tool) {
        "create_checklist" -> VoiceToolStatusUi.CreatingChecklist
        "add_item", "add_items" -> VoiceToolStatusUi.AddingTasks
        "find_items", "read_checklist" -> VoiceToolStatusUi.Searching
        else -> null
    }

    /**
     * The user's bubble while they speak (partial) and while Gisti thinks (final); Gisti's caption while
     * it speaks, and after it stops until the user starts the next utterance (cut at what was heard).
     */
    private fun transcriptOf(state: CallEngineState, phase: VoiceCallPhaseUi, actions: List<VoiceActionUi>): VoiceTranscriptUi {
        val heard = state.heard?.takeIf { it.isNotBlank() }
        val showUser = heard != null && (phase is VoiceCallPhaseUi.Listening || phase is VoiceCallPhaseUi.Thinking)
        val agent = state.agent
        val showReply = agent != null && agent.text.isNotBlank() && when (phase) {
            VoiceCallPhaseUi.Speaking -> true
            is VoiceCallPhaseUi.Listening -> !phase.hearing && !showUser
            VoiceCallPhaseUi.Muted -> true
            else -> false
        }
        val reply = if (showReply && agent != null) {
            val spoken = if (phase == VoiceCallPhaseUi.Speaking || agent.interrupted) agent.spokenChars else agent.text.length
            VoiceReplyUi(text = agent.text, spokenChars = spoken)
        } else {
            null
        }
        return VoiceTranscriptUi(
            userText = if (showUser) heard else null,
            userFinal = showUser && state.heardFinal,
            reply = reply,
            lastAction = actions.lastOrNull(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Stopped
    // ---------------------------------------------------------------------------------------------

    /**
     * The server's reason decides when it gave one; the kill switch and the idle close never get here
     * (the engine stops those as Unavailable / Dropped). Without a known reason the minutes left decide:
     * minutes still on the account read as the per-call maximum.
     */
    private fun limit(
        stop: CallStop.LimitReached,
        state: CallEngineState,
        actions: List<VoiceActionUi>,
        formatLocalTime: (Long) -> String,
    ): VoiceCallUiState {
        val quota = stop.quota
        val callCap = when (stop.serverReason) {
            VoiceSessionEndReason.MaxCallDuration -> true
            VoiceSessionEndReason.QuotaExhausted -> false
            else -> quota.secondsLeft > 0
        }
        return if (callCap) {
            summary(VoiceCallSummaryReason.CallCap, state, actions)
        } else {
            VoiceCallUiState.LimitReached(
                plan = planOf(quota.plan),
                resetsAtLocalTime = quota.resetsAtEpochMs?.takeIf { quota.plan == VoicePlan.Premium }?.let(formatLocalTime),
                actions = actions,
            )
        }
    }

    private fun dropped(cause: DropCause, state: CallEngineState, actions: List<VoiceActionUi>): VoiceCallUiState =
        when {
            actions.isNotEmpty() -> summary(VoiceCallSummaryReason.Dropped, state, actions)
            cause == DropCause.Offline -> VoiceCallUiState.Failure(VoiceCallFailureUi.Offline)
            else -> VoiceCallUiState.Failure(VoiceCallFailureUi.Server)
        }

    private fun ended(reason: CallEndReason, state: CallEngineState, actions: List<VoiceActionUi>): VoiceCallUiState =
        when (reason) {
            CallEndReason.HangUp, CallEndReason.SystemInterruption ->
                if (actions.isEmpty() && state.elapsedSec < SHORT_CALL_SEC) {
                    VoiceCallUiState.Closed
                } else {
                    summary(VoiceCallSummaryReason.User, state, actions)
                }
            CallEndReason.Background -> summary(VoiceCallSummaryReason.Background, state, actions)
            CallEndReason.Limit -> summary(VoiceCallSummaryReason.CallCap, state, actions)
            CallEndReason.Offline -> dropped(DropCause.Offline, state, actions)
            CallEndReason.Error -> dropped(DropCause.Error, state, actions)
        }

    private fun summary(reason: VoiceCallSummaryReason, state: CallEngineState, actions: List<VoiceActionUi>): VoiceCallUiState.Summary {
        val quota = state.quota
        return VoiceCallUiState.Summary(
            reason = reason,
            durationSec = state.elapsedSec,
            actions = actions,
            // Unknown minutes (no session answer) leave the button: the server refuses a start without them.
            callAgainAvailable = quota == null || quota.secondsLeft > 0,
            freeSecondsLeft = quota?.takeIf { it.plan == VoicePlan.Free }?.secondsLeft,
        )
    }

    fun failureOf(reason: UnavailableReason): VoiceCallFailureUi = when (reason) {
        UnavailableReason.NoRecognizer -> VoiceCallFailureUi.NoRecognizer
        UnavailableReason.NoVoice -> VoiceCallFailureUi.NoVoice
        UnavailableReason.FlagOff -> VoiceCallFailureUi.FlagOff
        UnavailableReason.Offline -> VoiceCallFailureUi.Offline
        // FEATURE_SPEC decision: an account the server will not serve and credits that ran out read as
        // "Gisti isn't answering" (the ViewModel logs the cause).
        UnavailableReason.NotEntitled, UnavailableReason.OutOfCredits -> VoiceCallFailureUi.Server
    }

    private fun planOf(plan: VoicePlan): VoicePlanUi = when (plan) {
        VoicePlan.Free -> VoicePlanUi.Free
        VoicePlan.Premium -> VoicePlanUi.Premium
    }

    /** Wire kinds of `VoiceAgentAction.kind` (set by the turn gateway in feature/aichat). */
    fun actionUi(index: Int, action: VoiceAgentAction): VoiceActionUi = VoiceActionUi(
        id = "a$index",
        kind = when (action.kind) {
            KIND_CHECKLIST_CREATED -> VoiceActionKind.ChecklistCreated
            KIND_ITEM_ADDED -> VoiceActionKind.TaskAdded
            KIND_ITEMS_ADDED -> VoiceActionKind.ItemsAdded
            else -> VoiceActionKind.Updated
        },
        title = action.title,
        itemsCount = action.count?.takeIf { action.kind == KIND_ITEMS_ADDED },
        checklistId = action.targetId,
    )

    private const val KIND_CHECKLIST_CREATED = "checklist_created"
    private const val KIND_ITEM_ADDED = "item_added"
    private const val KIND_ITEMS_ADDED = "items_added"
}
