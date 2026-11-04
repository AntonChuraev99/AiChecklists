package com.antonchuraev.homesearchchecklist.core.voice.impl.fakes

import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSegment
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSource
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSummary
import com.antonchuraev.homesearchchecklist.core.voice.api.TurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceHeartbeat
import com.antonchuraev.homesearchchecklist.core.voice.api.VoicePlan
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceQuota
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceResult
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEnd
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionEndRequest
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStart
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceSessionStartRequest
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Fakes of the voice transport (port of AiTeacher voice/fakes/FakeVoiceBackend.kt) plus fixtures, for the
// DefaultVoiceCallEngine tests of this module.

object VoiceFixtures {
    val freeQuota = VoiceQuota(plan = VoicePlan.Free, secondsLeft = 300, secondsTotal = 300, resetsAtEpochMs = null)
    val emptyFreeQuota = freeQuota.copy(secondsLeft = 0)
    val premiumQuota = VoiceQuota(plan = VoicePlan.Premium, secondsLeft = 1_800, secondsTotal = 1_800, resetsAtEpochMs = null)

    /** A streamed reply: one `say` per sentence, then `done` with the matching `spoken` and [actions]. */
    fun streamedReply(
        turnId: TurnId,
        vararg sentences: String,
        lang: String? = null,
        actions: List<VoiceAgentAction> = emptyList(),
    ): List<VoiceTurnEvent> =
        sentences.mapIndexed { seq, text -> VoiceTurnEvent.Say(turnId, seq, listOf(SpokenSegment(text, lang))) } +
            VoiceTurnEvent.Done(
                turnId = turnId,
                replayed = false,
                spoken = SpokenSummary(
                    sentences = sentences.size,
                    truncated = false,
                    source = SpokenSource.Model,
                    text = sentences.joinToString(" "),
                ),
                actions = actions,
            )
}

/** Records every turn; [reply] decides the events (default: one sentence `"OK."`). */
class FakeVoiceTurnGateway(
    var reply: (VoiceTurnRequest) -> Flow<VoiceTurnEvent> = { request ->
        flowOf(*VoiceFixtures.streamedReply(TurnId("t-${request.clientTurnId.value}"), "OK.").toTypedArray())
    },
) : VoiceTurnGateway {
    val requests = mutableListOf<VoiceTurnRequest>()

    override fun turn(request: VoiceTurnRequest): Flow<VoiceTurnEvent> {
        requests += request
        return reply(request)
    }
}

/** The voice session with scripted answers; every call is recorded in order. */
class FakeVoiceSessionGateway(
    var startResult: (VoiceSessionStartRequest) -> VoiceResult<VoiceSessionStart> = { request ->
        VoiceResult.Success(
            VoiceSessionStart.Started(request.sessionId, VoiceFixtures.freeQuota, heartbeatSec = 15, stream = true),
        )
    },
    var heartbeatResult: (VoiceSessionId, Int) -> VoiceResult<VoiceHeartbeat> = { _, elapsed ->
        val quota = VoiceFixtures.freeQuota
        VoiceResult.Success(VoiceHeartbeat(quota.copy(secondsLeft = (quota.secondsLeft - elapsed).coerceAtLeast(0)), ended = false))
    },
    var endResult: (VoiceSessionEndRequest) -> VoiceResult<VoiceSessionEnd> = { request ->
        VoiceResult.Success(VoiceSessionEnd(quota = VoiceFixtures.freeQuota, secondsCharged = request.elapsedSec))
    },
    var quotaResult: () -> VoiceResult<VoiceQuota> = { VoiceResult.Success(VoiceFixtures.freeQuota) },
) : VoiceSessionGateway {
    val starts = mutableListOf<VoiceSessionStartRequest>()
    val heartbeats = mutableListOf<Pair<VoiceSessionId, Int>>()
    val ends = mutableListOf<VoiceSessionEndRequest>()
    var quotaCalls = 0
        private set

    override suspend fun start(r: VoiceSessionStartRequest): VoiceResult<VoiceSessionStart> {
        starts += r
        return startResult(r)
    }

    override suspend fun heartbeat(id: VoiceSessionId, elapsedSec: Int): VoiceResult<VoiceHeartbeat> {
        heartbeats += id to elapsedSec
        return heartbeatResult(id, elapsedSec)
    }

    override suspend fun end(r: VoiceSessionEndRequest): VoiceResult<VoiceSessionEnd> {
        ends += r
        return endResult(r)
    }

    override suspend fun quota(): VoiceResult<VoiceQuota> {
        quotaCalls++
        return quotaResult()
    }
}
