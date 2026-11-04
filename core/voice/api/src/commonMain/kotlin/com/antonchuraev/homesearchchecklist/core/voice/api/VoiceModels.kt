package com.antonchuraev.homesearchchecklist.core.voice.api

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Models of the voice call with the agent (port of AiTeacher domain/voice/VoiceModels.kt).
// Cascade: platform STT -> one agent turn (chat_agent with tools) -> checked sentences -> platform TTS.
// No platform types here; the platform side lives behind the interfaces of VoiceContracts.kt.

// ---------------------------------------------------------------------------------------------
// Speech in and out
// ---------------------------------------------------------------------------------------------

/** BCP 47 tag of the recognizer or a TTS voice, e.g. `en-US`. */
@JvmInline
value class SpeechLocale(val tag: String) {

    /** ISO 639-1 language of [tag], lower case (`en` of `en-US`). */
    val language: String get() = tag.substringBefore('-').substringBefore('_').lowercase()

    companion object {
        /**
         * The voice of a spoken segment tagged [iso639] (`SpokenSegment.lang`): [callLocale] when the language matches
         * it or the tag is blank/`null` (the safe side for TTS), else the usual region of that language, else the bare
         * language code (TTS engines accept it).
         */
        fun forLanguage(iso639: String?, callLocale: SpeechLocale): SpeechLocale {
            val code = iso639.orEmpty().substringBefore('-').substringBefore('_').trim().lowercase()
            if (code.isEmpty() || code == callLocale.language) return callLocale
            return SpeechLocale(DEFAULT_REGION[code] ?: code)
        }

        private val DEFAULT_REGION = mapOf(
            "en" to "en-US",
            "ru" to "ru-RU",
            "hi" to "hi-IN",
            "es" to "es-ES",
            "de" to "de-DE",
            "fr" to "fr-FR",
            "it" to "it-IT",
            "pt" to "pt-BR",
            "uk" to "uk-UA",
        )
    }
}

/** Where recognition runs: on the device (nothing leaves it), the system's network service, or nowhere. */
enum class SpeechAvailability { OnDevice, Network, Unavailable }

/**
 * Options of one utterance of [SpeechInput.listen].
 *
 * @property preferOffline ask the system recognizer to stay on the device when it can.
 * @property partialResults emit [SpeechEvent.Partial] while the user speaks.
 * @property manualEndOfTurn the user ends the utterance with a tap (TalkBack): no end-of-speech detection.
 * @property alsoAllow extra languages the recognizer may detect, used only where the platform supports it (API 34+).
 */
data class ListenOptions(
    val preferOffline: Boolean = true,
    val partialResults: Boolean = true,
    val manualEndOfTurn: Boolean = false,
    val alsoAllow: List<SpeechLocale> = emptyList(),
)

/** One utterance of [SpeechInput.listen]: ends with exactly one [Final] or [Error]. */
sealed interface SpeechEvent {
    /** The recognizer is open and listening. */
    data object Ready : SpeechEvent

    /** The user started speaking. */
    data object SpeechStarted : SpeechEvent

    /** Input level in `0..1` for the call orb. */
    data class Level(val normalized: Float) : SpeechEvent

    /** Text recognized so far (not final). */
    data class Partial(val text: String) : SpeechEvent

    /** @property confidence `null` when the engine did not report one; [speechMs] from start of speech to end. */
    data class Final(val text: String, val confidence: Float?, val speechMs: Long) : SpeechEvent

    /** The utterance failed; [kind] is the platform error code mapped to a stable kind. */
    data class Error(val kind: SpeechErrorKind) : SpeechEvent
}

/** Stable kinds of the platform recognizer errors (several platform codes map to one). */
enum class SpeechErrorKind { NoMatch, SpeechTimeout, Network, Permission, Busy, LanguageUnavailable, Client, Other }

/** TTS speed of the agent's voice. */
enum class SpeechRate(val factor: Float) {
    Normal(1.0f),
    Slower(0.8f),
}

/** One piece of a sentence spoken in its own voice (a reply may switch languages mid-sentence). */
data class SpeechSegmentOut(val text: String, val locale: SpeechLocale)

/** Result of [SpeechOutput.prepare]. */
sealed interface SpeechOutputStatus {
    data object Ready : SpeechOutputStatus

    /** TTS works, but no voice is installed for [locales] ("Install voice data"). */
    data class MissingVoice(val locales: Set<SpeechLocale>) : SpeechOutputStatus

    /** No TTS engine could be bound. */
    data object Unavailable : SpeechOutputStatus
}

/** Progress of one [SpeechOutput.speak]; [utteranceId] is `"<clientTurnId>#<seq>"`. */
sealed interface SpeechOutputEvent {
    val utteranceId: String

    data class Started(override val utteranceId: String) : SpeechOutputEvent

    /** The engine reached [endChar] (exclusive) of the utterance text: the caption shows only what was spoken. */
    data class Range(override val utteranceId: String, val endChar: Int) : SpeechOutputEvent

    data class Done(override val utteranceId: String) : SpeechOutputEvent

    /** @property code the engine's error code (`TextToSpeech.ERROR_*` on Android), for analytics. */
    data class Error(override val utteranceId: String, val code: Int) : SpeechOutputEvent
}

/** An audio focus change the call reacts to (a ducking loss is not reported: the call keeps talking). */
enum class FocusChange { Gain, LossTransient, Loss }

/** Where the agent's voice goes: voice barge-in is safe only off the speaker. */
enum class AudioRoute { Speaker, Earpiece, WiredHeadset, Bluetooth }

/** Energy VAD of voice barge-in: speech above [thresholdDbfs] for at least [minSpeechMs], frames of [frameMs]. */
data class VadConfig(val thresholdDbfs: Float = -38f, val minSpeechMs: Long = 200, val frameMs: Int = 20)

// ---------------------------------------------------------------------------------------------
// Ids
// ---------------------------------------------------------------------------------------------

/** Client-made uuid v4 of a call (the session and every voice turn of it). */
@JvmInline
value class VoiceSessionId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): VoiceSessionId = VoiceSessionId(Uuid.random().toString())
    }
}

/**
 * Client-made uuid v4 of one voice turn, reused for every retry of that turn (timeout, reconnect): the server
 * answers a repeated id with the same reply instead of running the agent (and its tools) twice.
 */
@JvmInline
value class ClientTurnId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): ClientTurnId = ClientTurnId(Uuid.random().toString())
    }
}

/** Server id of an answered turn (the chat message the reply became). */
@JvmInline
value class TurnId(val value: String)

/**
 * Where the call was started from, for analytics and the session start. A free-form surface name set by the
 * feature layer (e.g. `chat`, `home`), not an enum here: core does not know the screens.
 */
@JvmInline
value class VoiceCallEntry(val surface: String)

// ---------------------------------------------------------------------------------------------
// Turns
// ---------------------------------------------------------------------------------------------

/** The previous reply was cut after [spokenChars]; `0` = cancelled while thinking. Sent once with the next turn. */
data class InterruptedTurn(val clientTurnId: ClientTurnId, val spokenChars: Int)

/**
 * One voice turn: what the user said, sent to the agent.
 *
 * The gateway owns the conversation: it keeps (or loads) the history of [sessionId] and sends it to the agent with
 * [text]; the engine sends only the new utterance.
 *
 * @property text the recognized utterance, trimmed and non-blank.
 * @property listenLocale the language the recognizer listened in; the agent answers in it.
 * @property uiLocale the app language (BCP 47) for names of things the agent creates; `null` = same as [listenLocale].
 * @property sttConfidence recognizer confidence `0..1`, `null` when the engine gave none.
 * @property sttOnDevice the utterance was recognized on the device.
 * @property interrupted the cut previous reply, sent once with the next turn so the agent knows what was not heard.
 * @property stream read the streamed [VoiceTurnEvent.Say] chunks; `false` = the reply comes only in [VoiceTurnEvent.Done].
 */
data class VoiceTurnRequest(
    val clientTurnId: ClientTurnId,
    val sessionId: VoiceSessionId,
    val text: String,
    val listenLocale: SpeechLocale,
    val uiLocale: String?,
    val sttConfidence: Float?,
    val sttOnDevice: Boolean,
    val interrupted: InterruptedTurn?,
    val stream: Boolean,
)

/** A checked piece of a spoken sentence; [lang] is ISO 639-1, `null` = the language of the call. */
data class SpokenSegment(val text: String, val lang: String? = null)

/** Where the spoken text came from: the model, or a fixed server line (limits, errors). */
enum class SpokenSource(val wireValue: String) {
    Model("model"),
    Fixed("fixed"),
    ;

    companion object {
        fun ofWire(value: String?): SpokenSource? = entries.firstOrNull { it.wireValue == value }
    }
}

/** `spoken` of the final turn result: the whole checked reply as one text. */
data class SpokenSummary(val sentences: Int, val truncated: Boolean, val source: SpokenSource, val text: String)

/**
 * Something the agent did in the user's data during a turn (a tool call that changed state), for the call screen
 * to show as a card ("Created checklist «Trip»") and to open it.
 *
 * @property title short user-facing description, already in the UI language.
 * @property kind stable machine kind of the action, e.g. `checklist_created`, `item_added`; the UI picks an icon by it.
 * @property targetId id of the thing the action touched (checklist id), `null` when there is nothing to open.
 * @property count how many things the action touched when it is more than one (`items_added`), `null` otherwise.
 */
data class VoiceAgentAction(val title: String, val kind: String, val targetId: String? = null, val count: Int? = null)

/** What the server is doing on a turn before the first sentence. */
enum class VoiceTurnProgress {
    /** The model is thinking. */
    Thinking,

    /** The agent is running a tool (creating or changing data). */
    Acting,
}

/** The stream of one voice turn: status and [Say] chunks, then exactly one [Done] or [Failed]. */
sealed interface VoiceTurnEvent {
    /**
     * What the server is doing before the first sentence; [toolName] is the tool call it runs now (e.g.
     * `create_checklist`), `null` while the model only thinks.
     */
    data class Status(val progress: VoiceTurnProgress, val toolName: String? = null) : VoiceTurnEvent

    /** One checked sentence, [seq] from 0 in the order to speak. */
    data class Say(val turnId: TurnId, val seq: Int, val segments: List<SpokenSegment>) : VoiceTurnEvent

    /**
     * The turn is answered. A replayed turn or a turn without stream carries no [Say]: the client speaks [spoken].
     *
     * @property actions what the agent did in the user's data during this turn, in order; empty for a plain answer.
     * @property replayed the server answered a repeated [ClientTurnId] from its cache (the tools did not run again).
     */
    data class Done(
        val turnId: TurnId?,
        val replayed: Boolean,
        val spoken: SpokenSummary?,
        val actions: List<VoiceAgentAction> = emptyList(),
    ) : VoiceTurnEvent

    /** The turn failed; the engine decides on a retry by [VoiceCallError.isRetryable]. */
    data class Failed(val error: VoiceCallError) : VoiceTurnEvent
}

// ---------------------------------------------------------------------------------------------
// Errors and results of the transport
// ---------------------------------------------------------------------------------------------

/** Result of a [VoiceSessionGateway] call: never an exception. */
sealed interface VoiceResult<out T> {
    data class Success<T>(val value: T) : VoiceResult<T>

    data class Failure(val error: VoiceCallError) : VoiceResult<Nothing>
}

/** Why a turn or a session call failed, branched by the server's reason, never by its message. */
sealed interface VoiceCallError {
    /** A retry with the same [ClientTurnId] makes sense. */
    val isRetryable: Boolean

    /** Stable code for analytics (`CallEngineEvent.Error.code`). */
    val code: String

    /** No network or the request timed out: retried after the connection is back. */
    data object Unreachable : VoiceCallError {
        override val isRetryable: Boolean get() = true
        override val code: String get() = "unreachable"
    }

    /** The server or the model is temporarily down (5xx): retried. */
    data object Unavailable : VoiceCallError {
        override val isRetryable: Boolean get() = true
        override val code: String get() = "unavailable"
    }

    /** The same [ClientTurnId] is still running on the server: the engine retries it for a bounded window. */
    data object TurnInProgress : VoiceCallError {
        override val isRetryable: Boolean get() = false
        override val code: String get() = "turn_in_progress"
    }

    /** The user may not call (no account, plan without voice). Ends the call. */
    data class NotEntitled(val reason: String? = null) : VoiceCallError {
        override val isRetryable: Boolean get() = false
        override val code: String get() = "not_entitled"
    }

    /** The AI credits ran out. Ends the call with the paywall offer. */
    data object OutOfCredits : VoiceCallError {
        override val isRetryable: Boolean get() = false
        override val code: String get() = "out_of_credits"
    }

    /** Too many turns in a short time. */
    data object RateLimited : VoiceCallError {
        override val isRetryable: Boolean get() = false
        override val code: String get() = "rate_limited"
    }

    /** The server refused the request as malformed: a client bug, logged, no retry. */
    data class ClientBug(val reason: String) : VoiceCallError {
        override val isRetryable: Boolean get() = false
        override val code: String get() = reason
    }

    /** Anything else; [code] is the server's reason. */
    data class Other(override val code: String, override val isRetryable: Boolean = false) : VoiceCallError
}

// ---------------------------------------------------------------------------------------------
// Session and minutes
// ---------------------------------------------------------------------------------------------

/** The plan the voice minutes are counted against. */
enum class VoicePlan(val wireValue: String) {
    Free("free"),
    Premium("premium"),
    ;

    companion object {
        fun ofWire(value: String?): VoicePlan? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Voice minutes as the server counts them (the server clock is the only one that counts).
 *
 * @property resetsAtEpochMs when [secondsLeft] refills, epoch milliseconds; `null` for lifetime free minutes.
 * @property callSecondsLeft what is left of the per-call maximum, when the server sends it; [secondsLeft] stays the
 *   account's minutes (the call cap is not applied to it), so the UI can tell "minutes ran out" from "the call
 *   reached its maximum".
 */
data class VoiceQuota(
    val plan: VoicePlan,
    val secondsLeft: Int,
    val secondsTotal: Int,
    val resetsAtEpochMs: Long?,
    val callSecondsLeft: Int? = null,
)

/** Why the call ended, as sent to the server and analytics. */
enum class CallEndReason(val wireValue: String) {
    HangUp("hangup"),
    Limit("limit"),
    Error("error"),
    Offline("offline"),
    Background("background"),
    SystemInterruption("interrupted_by_system"),
    ;

    companion object {
        fun ofWire(value: String?): CallEndReason? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Opens the server session of a call; [sessionId] is made by the client. */
data class VoiceSessionStartRequest(
    val sessionId: VoiceSessionId,
    val entry: VoiceCallEntry,
    val listenLocale: SpeechLocale,
    val sttOnDevice: Boolean,
)

/** Answer of [VoiceSessionGateway.start]. */
sealed interface VoiceSessionStart {
    /** @property stream the server streams `say` chunks for this session; [heartbeatSec] the beat interval. */
    data class Started(val sessionId: VoiceSessionId, val quota: VoiceQuota, val heartbeatSec: Int, val stream: Boolean) :
        VoiceSessionStart

    /** No minutes left: the call does not start. */
    data class LimitReached(val quota: VoiceQuota) : VoiceSessionStart

    /** Server kill switch: voice calls are off. */
    data object Disabled : VoiceSessionStart
}

/** Closes the server session; counters are for analytics. */
data class VoiceSessionEndRequest(
    val sessionId: VoiceSessionId,
    val reason: CallEndReason,
    val elapsedSec: Int,
    val turns: Int,
    val interruptions: Int,
    val interrupted: InterruptedTurn?,
)

/** @property ended the server closed the session (minutes ran out or the session timed out). */
data class VoiceHeartbeat(val quota: VoiceQuota, val ended: Boolean)

/**
 * Answer of [VoiceSessionGateway.end].
 *
 * @property quota minutes left after the call, `null` when the server did not send them.
 * @property secondsCharged what the server charged for this call.
 */
data class VoiceSessionEnd(val quota: VoiceQuota?, val secondsCharged: Int)
