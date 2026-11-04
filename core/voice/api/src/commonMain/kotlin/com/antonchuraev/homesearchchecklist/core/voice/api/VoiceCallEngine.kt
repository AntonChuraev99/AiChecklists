package com.antonchuraev.homesearchchecklist.core.voice.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The call state machine (port of AiTeacher domain/voice/VoiceCallEngine.kt). One engine per call, living in the
 * scope it was created with: cancelling the scope stops TTS, closes the microphone and stops the heartbeat.
 * Commands never throw; what the UI needs is in [state], analytics facts in [events].
 */
interface VoiceCallEngine {
    /** Everything the call screen draws. */
    val state: StateFlow<CallEngineState>

    /** One-shot facts for analytics, each delivered once; never carry the text of an utterance. */
    val events: Flow<CallEngineEvent>

    /** Orb level `0..1` (input while listening, output while speaking); read in the draw phase only. */
    val levels: StateFlow<Float>

    /** Sends a user or lifecycle command; a command that does not fit the current phase is ignored. */
    fun dispatch(command: CallCommand)

    /** Makes one engine per call (new recognizer, TTS and focus request each time). */
    fun interface Factory {
        fun create(scope: CoroutineScope, config: CallConfig): VoiceCallEngine
    }
}

/**
 * Settings of one call, fixed when it starts.
 *
 * @property listenLocale the language the recognizer listens in and the agent speaks.
 * @property uiLocale the app language (BCP 47), sent with every turn; `null` = same as [listenLocale].
 * @property alsoAllow extra languages the recognizer may detect (API 34+ only).
 * @property manualEndOfTurn TalkBack: the user ends each utterance with a tap.
 * @property bargeInSpeakerEnabled voice barge-in on the speaker (needs an echo canceller); in headphones it is always on.
 * @property streamEnabled read streamed sentences (client flag; the session may still turn streaming off).
 * @property softLimitSec the call ends after this many seconds; `null` = the server quota only.
 * @property backgroundEndSec back from the background later than this ends the call.
 */
data class CallConfig(
    val entry: VoiceCallEntry,
    val listenLocale: SpeechLocale,
    val uiLocale: String?,
    val alsoAllow: List<SpeechLocale> = emptyList(),
    val manualEndOfTurn: Boolean,
    val bargeInSpeakerEnabled: Boolean,
    val streamEnabled: Boolean,
    val rate: SpeechRate,
    val softLimitSec: Int?,
    val backgroundEndSec: Int,
)

/** What the call is doing now; shared by the engine and the UI. */
sealed interface CallPhase {
    data object Idle : CallPhase

    /** Checking the recognizer, the voice and opening the server session. */
    data object Connecting : CallPhase

    /** @property hearing speech is coming in; [afterInterrupt] the short window after a barge-in. */
    data class Listening(val hearing: Boolean, val afterInterrupt: Boolean) : CallPhase

    /**
     * The turn is with the agent; [delay] grows with the wait. [tool] is the server tool the agent is running now
     * (e.g. `create_checklist`, from [VoiceTurnEvent.Status.toolName]), `null` while it only thinks.
     */
    data class Thinking(val delay: ThinkingDelay, val tool: String? = null) : CallPhase

    /** The agent's reply is being spoken. */
    data object Speaking : CallPhase

    /** The microphone is muted by the user; the call stays open. */
    data object Muted : CallPhase

    /** The network is gone; the call ends when [secondsLeft] runs out. */
    data class Reconnecting(val secondsLeft: Int) : CallPhase

    /** Audio stopped until the user resumes (app in the background, focus taken by a phone call). */
    data class Paused(val reason: PauseReason) : CallPhase
}

/** Why the call is no longer running; set together with the last [CallPhase]. */
sealed interface CallStop {
    /** The minutes ran out, before the call or [duringCall]. */
    data class LimitReached(val quota: VoiceQuota, val duringCall: Boolean) : CallStop

    /** The call could not start or go on on this device or account. */
    data class Unavailable(val reason: UnavailableReason) : CallStop

    /** The call was lost (no connection, repeated errors). */
    data class Dropped(val cause: DropCause) : CallStop

    /** The call ended normally. */
    data class Ended(val reason: CallEndReason) : CallStop
}

/** How long the agent has been thinking. */
enum class ThinkingDelay {
    Normal,

    /** More than 6 s. */
    Slow,

    /** More than 20 s. */
    VerySlow,
}

/** Why the call is paused. */
enum class PauseReason {
    /** The call screen went to the background. */
    Background,

    /** Audio focus lost to the system (a phone call, an alarm). */
    SystemInterruption,
}

/** Why the call is unavailable. */
enum class UnavailableReason { NoRecognizer, NoVoice, FlagOff, Offline, NotEntitled, OutOfCredits }

/** Why the call was dropped. */
enum class DropCause { ConnectTimeout, Offline, Error }

/** How the user cut the agent off. */
enum class BargeInMethod(val wireValue: String) {
    Tap("tap"),
    Voice("voice"),
    ;

    companion object {
        fun ofWire(value: String?): BargeInMethod? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * The agent's current reply. [text] is everything handed to TTS so far, [spokenChars] how much of it was spoken
 * (the caption shows only that part).
 */
data class AgentUtterance(
    val clientTurnId: ClientTurnId,
    val turnId: TurnId?,
    val text: String,
    val spokenChars: Int,
    val interrupted: Boolean,
)

/**
 * The state of the call.
 *
 * @property heard the user's current utterance (partial until [heardFinal]).
 * @property agent the reply being spoken or the last one.
 * @property actions everything the agent did in the user's data during this call, oldest first (cards on the screen).
 * @property lastMinute less than a minute of the quota is left.
 */
data class CallEngineState(
    val phase: CallPhase = CallPhase.Idle,
    val stop: CallStop? = null,
    val sessionId: VoiceSessionId? = null,
    val quota: VoiceQuota? = null,
    val elapsedSec: Int = 0,
    val heard: String? = null,
    val heardFinal: Boolean = false,
    val agent: AgentUtterance? = null,
    val actions: List<VoiceAgentAction> = emptyList(),
    val muted: Boolean = false,
    val rate: SpeechRate = SpeechRate.Normal,
    val stt: SpeechAvailability = SpeechAvailability.Unavailable,
    val turns: Int = 0,
    val interruptions: Int = 0,
    val errors: Int = 0,
    val lastMinute: Boolean = false,
)

/** User and lifecycle commands of the call. */
sealed interface CallCommand {
    /** Start the call (checks the permission first). */
    data object Start : CallCommand

    /** The microphone permission was granted after [Start] asked for it. */
    data object PermissionGranted : CallCommand

    /** Cut the agent off (a tap on the orb or the user's voice). */
    data class Interrupt(val method: BargeInMethod) : CallCommand

    /** End the current utterance now (tap while listening). */
    data object FinishUtterance : CallCommand

    /** Drop the turn the agent is thinking about and listen again. */
    data object CancelThinking : CallCommand

    data class SetMuted(val muted: Boolean) : CallCommand

    /** Speak the last reply again. */
    data object Repeat : CallCommand

    data class SetRate(val rate: SpeechRate) : CallCommand

    /** The call screen stopped (ON_STOP): pause. */
    data object ScreenStopped : CallCommand

    /** The call screen is visible again (ON_START). */
    data object ScreenStarted : CallCommand

    /** Resume a paused call. */
    data object Resume : CallCommand

    /** End the call. */
    data object HangUp : CallCommand
}

/** Analytics facts of the call; never carry the text of an utterance. */
sealed interface CallEngineEvent {
    data class Started(val stt: SpeechAvailability, val quota: VoiceQuota, val stream: Boolean) : CallEngineEvent

    /** @property outcome `ok` / `empty` / `no_match` / `timeout`. */
    data class TurnCaptured(val speechMs: Long, val chars: Int, val outcome: String) : CallEngineEvent

    /** @property actions how many actions the agent performed in this turn. */
    data class TurnReplied(
        val eosToFirstAudioMs: Long?,
        val serverFirstSayMs: Long?,
        val replyChars: Int,
        val source: SpokenSource?,
        val truncated: Boolean,
        val replayed: Boolean,
        val actions: Int,
    ) : CallEngineEvent

    data class BargeIn(val method: BargeInMethod, val spokenChars: Int, val replyChars: Int) : CallEngineEvent

    data class Paused(val reason: PauseReason) : CallEngineEvent

    data class LimitReached(val plan: VoicePlan, val duringCall: Boolean) : CallEngineEvent

    /** @property stage `stt` / `llm` / `tts` / `network` / `session`; [code] is [VoiceCallError.code] or a platform code. */
    data class Error(val stage: String, val code: String) : CallEngineEvent

    data class Ended(val reason: CallEndReason, val durationSec: Int, val turns: Int, val interruptions: Int, val errors: Int) :
        CallEngineEvent
}
