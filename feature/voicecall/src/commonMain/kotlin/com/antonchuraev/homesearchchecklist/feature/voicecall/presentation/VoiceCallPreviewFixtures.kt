package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

/**
 * Fake [VoiceCallUiState] per row of the FEATURE_SPEC matrix and per frame of the DESIGN_SPEC §14
 * screenshot matrix. Shared by the `@Preview`s (androidMain) and the Roborazzi tests
 * (androidHostTest) so both draw exactly the same data. Names follow `voiceCall_<state>`.
 */
object VoiceCallPreviewFixtures {

    val tripChecklist = VoiceActionUi(
        id = "a1",
        kind = VoiceActionKind.ChecklistCreated,
        title = "Trip to Kazan",
        checklistId = "c1",
    )
    val milkTask = VoiceActionUi(id = "a2", kind = VoiceActionKind.TaskAdded, title = "Buy milk", checklistId = "inbox")
    val itemsAdded = VoiceActionUi(
        id = "a3",
        kind = VoiceActionKind.ItemsAdded,
        title = "Groceries",
        itemsCount = 5,
        checklistId = "c2",
    )

    /** Edge data: 60+ chars without an early space (ellipsis, chevron stays). */
    val longTitleAction = VoiceActionUi(
        id = "a4",
        kind = VoiceActionKind.ChecklistCreated,
        title = "Preparing to move into the new apartment on Vasilyevsky Island",
        checklistId = "c3",
    )

    /** 350 chars, the voice prompt's ceiling. */
    private const val LONG_REPLY =
        "I created the checklist Trip to Kazan with tickets, hotel, documents and a charger. " +
            "I also added a reminder to check in online the day before the flight, and put the " +
            "museum passes on the list so you do not forget them at home. Would you like me to " +
            "add the packing list for warm weather too, or keep it short for now and move on?"

    private val liveTimer = CallTimerUi(elapsedSec = 108, remainingSec = 192, live = true)

    private fun inCall(phase: VoiceCallPhaseUi, block: VoiceCallUiState.InCall.() -> VoiceCallUiState.InCall = { this }) =
        VoiceCallUiState.InCall(phase = phase, timer = liveTimer).block()

    val connecting = VoiceCallUiState.InCall(phase = VoiceCallPhaseUi.Connecting, timer = CallTimerUi())
    val listeningSilence = inCall(VoiceCallPhaseUi.Listening(hearing = false))
    val listeningHearing = inCall(VoiceCallPhaseUi.Listening(hearing = true)) {
        copy(firstTurn = false, transcript = VoiceTranscriptUi(userText = "Make a list for the trip to Kazan with tickets and"))
    }
    val listeningTalkBack = listeningSilence.copy(manualEndOfTurn = true)
    val thinkingTool = inCall(VoiceCallPhaseUi.Thinking(VoiceThinkingDelayUi.Normal, VoiceToolStatusUi.CreatingChecklist)) {
        copy(firstTurn = false, transcript = VoiceTranscriptUi(userText = "Make a list for the trip to Kazan", userFinal = true))
    }
    val thinkingSlow = thinkingTool.copy(phase = VoiceCallPhaseUi.Thinking(VoiceThinkingDelayUi.Slow))
    val thinkingVerySlow = thinkingTool.copy(phase = VoiceCallPhaseUi.Thinking(VoiceThinkingDelayUi.VerySlow))
    val speakingCaptions = inCall(VoiceCallPhaseUi.Speaking) {
        copy(firstTurn = false, transcript = VoiceTranscriptUi(reply = VoiceReplyUi(LONG_REPLY.take(120), spokenChars = 60)))
    }
    val speakingCardCaptions = speakingCaptions.copy(
        transcript = VoiceTranscriptUi(reply = VoiceReplyUi(LONG_REPLY, spokenChars = 140), lastAction = tripChecklist),
    )
    val speakingCardLongTitle = speakingCardCaptions.copy(
        transcript = speakingCardCaptions.transcript.copy(lastAction = longTitleAction),
    )
    val speakingCaptionsOff = speakingCardCaptions.copy(captionsOn = false)
    val speakingNoBargeIn = speakingCaptions.copy(interruptible = false)
    val muted = inCall(VoiceCallPhaseUi.Muted) {
        copy(micOn = false, firstTurn = false, timer = liveTimer.copy(live = false), transcript = VoiceTranscriptUi(lastAction = milkTask))
    }
    val reconnecting = inCall(VoiceCallPhaseUi.Reconnecting(secondsLeft = 14)) {
        copy(firstTurn = false, timer = liveTimer.copy(live = false), banner = VoiceCallBannerUi.Reconnecting(14))
    }
    val pausedBackground = inCall(VoiceCallPhaseUi.Paused(VoicePauseReasonUi.Background)) {
        copy(firstTurn = false, timer = liveTimer.copy(live = false))
    }
    val pausedInterruption = pausedBackground.copy(phase = VoiceCallPhaseUi.Paused(VoicePauseReasonUi.SystemInterruption))
    val limitWarningFree = inCall(VoiceCallPhaseUi.Listening(hearing = false)) {
        copy(
            firstTurn = false,
            timer = CallTimerUi(elapsedSec = 250, remainingSec = 50, live = true),
            banner = VoiceCallBannerUi.LimitWarning(VoicePlanUi.Free, minutesLeft = 1),
        )
    }
    val limitWarningPremium = limitWarningFree.copy(banner = VoiceCallBannerUi.LimitWarning(VoicePlanUi.Premium, 1))

    /** Edge data: the longest timer ("59:59 · 29:59 left"). */
    val timerMax = listeningSilence.copy(timer = CallTimerUi(elapsedSec = 3599, remainingSec = 1799, live = true))

    val permRequest = VoiceCallUiState.Permission(blocked = false)
    val permRequesting = VoiceCallUiState.Permission(blocked = false, requesting = true)
    val permBlocked = VoiceCallUiState.Permission(blocked = true)

    val errNoRecognizer = VoiceCallUiState.Failure(VoiceCallFailureUi.NoRecognizer)
    val errNoVoice = VoiceCallUiState.Failure(VoiceCallFailureUi.NoVoice)
    val errOffline = VoiceCallUiState.Failure(VoiceCallFailureUi.Offline)
    val errServer = VoiceCallUiState.Failure(VoiceCallFailureUi.Server)
    val errFlagOff = VoiceCallUiState.Failure(VoiceCallFailureUi.FlagOff)

    val summary = VoiceCallUiState.Summary(
        reason = VoiceCallSummaryReason.User,
        durationSec = 134,
        actions = listOf(tripChecklist, milkTask),
        callAgainAvailable = true,
        freeSecondsLeft = 166,
    )
    val summaryMany = summary.copy(
        actions = List(8) { i -> itemsAdded.copy(id = "m$i", title = "List ${i + 1}", itemsCount = i + 1) },
        freeSecondsLeft = null,
    )
    val summaryNothing = summary.copy(reason = VoiceCallSummaryReason.Background, actions = emptyList())
    val summaryDropped = summary.copy(reason = VoiceCallSummaryReason.Dropped)
    val summaryCallCap = summary.copy(reason = VoiceCallSummaryReason.CallCap, durationSec = 600, freeSecondsLeft = null)
    val summaryNoMinutes = summary.copy(callAgainAvailable = false, freeSecondsLeft = null)

    val limitFree = VoiceCallUiState.LimitReached(plan = VoicePlanUi.Free, actions = listOf(tripChecklist))
    val limitFreeAtStart = VoiceCallUiState.LimitReached(plan = VoicePlanUi.Free)
    val limitDaily = VoiceCallUiState.LimitReached(
        plan = VoicePlanUi.Premium,
        resetsAtLocalTime = "03:00",
        actions = listOf(tripChecklist, milkTask),
    )

    /** Every non-Closed fixture with its frame name, for a screenshot loop. */
    val all: List<Pair<String, VoiceCallUiState>> = listOf(
        "connecting" to connecting,
        "listening_silence" to listeningSilence,
        "listening_hearing" to listeningHearing,
        "listening_talkback" to listeningTalkBack,
        "thinking_tool" to thinkingTool,
        "thinking_slow" to thinkingSlow,
        "thinking_very_slow" to thinkingVerySlow,
        "speaking_captions" to speakingCaptions,
        "speaking_card_captions" to speakingCardCaptions,
        "speaking_card_long_title" to speakingCardLongTitle,
        "speaking_captions_off" to speakingCaptionsOff,
        "speaking_no_barge_in" to speakingNoBargeIn,
        "muted" to muted,
        "reconnecting" to reconnecting,
        "paused_background" to pausedBackground,
        "paused_interruption" to pausedInterruption,
        "limit_warning_free" to limitWarningFree,
        "limit_warning_premium" to limitWarningPremium,
        "timer_max" to timerMax,
        "perm_request" to permRequest,
        "perm_requesting" to permRequesting,
        "perm_blocked" to permBlocked,
        "err_no_recognizer" to errNoRecognizer,
        "err_no_voice" to errNoVoice,
        "err_offline" to errOffline,
        "err_server" to errServer,
        "err_flag_off" to errFlagOff,
        "summary" to summary,
        "summary_many" to summaryMany,
        "summary_nothing" to summaryNothing,
        "summary_dropped" to summaryDropped,
        "summary_call_cap" to summaryCallCap,
        "summary_no_minutes" to summaryNoMinutes,
        "limit_free" to limitFree,
        "limit_free_at_start" to limitFreeAtStart,
        "limit_daily" to limitDaily,
    )
}
