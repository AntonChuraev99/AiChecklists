package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.voice

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSource
import com.antonchuraev.homesearchchecklist.core.voice.api.SpokenSummary
import com.antonchuraev.homesearchchecklist.core.voice.api.TurnId
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceCallError
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnEvent
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnGateway
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnProgress
import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceTurnRequest
import com.antonchuraev.homesearchchecklist.feature.aichat.api.dispatcher.ToolCallDispatcher
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentStepResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentVoiceContext
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChatAgentApiService
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.ChecklistContext
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolCallMapper
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentToolResultSerializer
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.AgentTranscriptWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One voice turn = one `chat_agent` turn in voice mode (`voice.session_id`): the stateless round
 * loop of the text chat (`ChatViewModel.runAgentTurn`) without its plan card — the user cannot see a
 * screen to confirm on, and the server's voice directive has the agent confirm by voice instead.
 *
 * **One instance per call** (Koin `factory`): it keeps that call's conversation ([history], text and
 * tool rounds of finished turns) and nothing outlives the call. Nothing is written to the text chat's
 * history (out of scope for v1).
 *
 * **Idempotent by [VoiceTurnRequest.clientTurnId]** (the engine retries a failed turn with the same
 * id): the turn's progress is kept per id, so
 *  - a finished turn is answered from that cache, `replayed = true`, the tools do not run again;
 *  - a turn that failed after some tool rounds resumes from the last completed round — its tools are
 *    never dispatched twice, and the server sees a mid-turn transcript (no second session claim);
 *  - the id is also the `request_id` of every round, the server's own dedup key.
 *
 * The reply is not streamed by `chat_agent`: [VoiceTurnEvent.Done] carries the whole checked text in
 * `spoken`, and the engine splits it into sentences itself (`SpokenText.sentences`), so no
 * [VoiceTurnEvent.Say] is emitted — the contract allows that for a turn without stream.
 */
internal class ChatAgentVoiceTurnGateway(
    private val agentApi: ChatAgentApiService,
    private val dispatcher: ToolCallDispatcher,
    private val userId: suspend () -> String,
    private val checklistsSummary: suspend () -> List<ChecklistContext>,
    private val logger: AppLogger,
) : VoiceTurnGateway {

    /** Finished turns of this call, oldest first (what the next turn's seed is windowed from). */
    private val history = mutableListOf<AgentTranscriptEntry>()

    /** Turns by client turn id: in flight, failed part-way, or finished (the replay cache). */
    private val turns = HashMap<String, TurnProgress>()

    /** One turn at a time: the engine never overlaps two, a stray retry must not race the first. */
    private val mutex = Mutex()

    override fun turn(request: VoiceTurnRequest): Flow<VoiceTurnEvent> = flow {
        mutex.withLock { emit(runTurn(request, this)) }
    }.catch { e ->
        // Upstream only (`catch` is exception-transparent): a dispatcher or mapper crash becomes the
        // contract's final Failed instead of an exception the engine does not expect.
        if (e is CancellationException) throw e
        logger.error(TAG, "voice turn crashed — ${e.message}", e)
        emit(VoiceTurnEvent.Failed(VoiceCallError.Unavailable))
    }

    private suspend fun runTurn(request: VoiceTurnRequest, out: FlowCollector<VoiceTurnEvent>): VoiceTurnEvent {
        val id = request.clientTurnId.value
        turns[id]?.done?.let { done ->
            logger.debug(TAG, "voice turn ${id.take(8)} replayed from cache")
            return done.copy(replayed = true)
        }
        val userId = userId()
        if (userId.isBlank()) {
            logger.warning(TAG, "voice turn skipped: userId blank (user not registered yet)")
            return VoiceTurnEvent.Failed(VoiceCallError.NotEntitled("user_not_registered"))
        }
        val progress = turns.getOrPut(id) { startTurn(request) }
        val locale = ChatLocale.fromLanguageTag(request.listenLocale.language)
        val voice = AgentVoiceContext(sessionId = request.sessionId.value)

        out.emit(VoiceTurnEvent.Status(VoiceTurnProgress.Thinking))
        // Tool rounds are capped at MAX_TOOL_ROUNDS; the step after the last one is the server's
        // own round-cap answer (`CHAT_AGENT_MAX_ROUNDS`), so the user always hears a reply.
        while (progress.rounds <= MAX_TOOL_ROUNDS) {
            val step = agentApi.step(
                userId = userId,
                transcript = progress.seed + progress.live,
                locale = locale,
                checklistsSummary = progress.summary,
                requestId = id,
                responseLanguage = request.listenLocale.language,
                voice = voice,
            )
            when (step) {
                is AgentStepResult.ToolCalls -> {
                    if (progress.rounds == MAX_TOOL_ROUNDS) break
                    runRound(step, progress, out)
                    out.emit(VoiceTurnEvent.Status(VoiceTurnProgress.Thinking))
                }
                is AgentStepResult.Final -> return finish(id, progress, step.content)
                // Never in voice mode (no `supports_options`); spoken as a question if it ever comes.
                is AgentStepResult.Options -> return finish(id, progress, step.prompt)
                else -> return VoiceTurnEvent.Failed(step.toVoiceError())
            }
        }
        logger.warning(TAG, "voice turn ${id.take(8)} hit the round cap ($MAX_TOOL_ROUNDS) without a final")
        return VoiceTurnEvent.Failed(VoiceCallError.Other(code = "round_cap"))
    }

    /** A new turn: the cut previous reply is settled into [history], then the seed is windowed once. */
    private suspend fun startTurn(request: VoiceTurnRequest): TurnProgress {
        request.interrupted?.let { cut -> applyInterruption(cut.clientTurnId.value, cut.spokenChars) }
        val seed = AgentTranscriptWindow.select(history + AgentTranscriptEntry.UserText(request.text))
        return TurnProgress(userText = request.text, seed = seed, summary = checklistsSummary())
    }

    /** Dispatches every call of one round (in order, one result per call) and records its actions. */
    private suspend fun runRound(
        step: AgentStepResult.ToolCalls,
        progress: TurnProgress,
        out: FlowCollector<VoiceTurnEvent>,
    ) {
        val results = step.calls.map { call ->
            out.emit(VoiceTurnEvent.Status(VoiceTurnProgress.Acting, toolName = call.name))
            val toolCall = AgentToolCallMapper.map(call)
            val json = if (toolCall == null) {
                logger.warning(TAG, "voice turn: unmappable call '${call.name}' — sending error result")
                buildJsonObject {
                    put("status", "unknown_tool")
                    put("detail", call.name)
                }
            } else {
                val outcome = dispatcher.dispatch(toolCall)
                VoiceActionMapper.toAction(outcome)?.let { progress.actions += it }
                AgentToolResultSerializer.serialize(outcome)
            }
            AgentToolResult(call.id, call.name, json)
        }
        // Recorded only once the whole round ran: a retry resumes after it, never inside it.
        progress.live += AgentTranscriptEntry.ModelToolCalls(step.calls)
        progress.live += AgentTranscriptEntry.ToolResults(results)
        progress.rounds++
    }

    private fun finish(id: String, progress: TurnProgress, content: String): VoiceTurnEvent.Done {
        history += AgentTranscriptEntry.UserText(progress.userText)
        history += progress.live
        history += AgentTranscriptEntry.ModelText(content)
        val done = VoiceTurnEvent.Done(
            turnId = TurnId(id),
            replayed = false,
            spoken = SpokenSummary(sentences = 0, truncated = false, source = SpokenSource.Model, text = content),
            actions = progress.actions.toList(),
        )
        progress.done = done
        progress.replyIndex = history.lastIndex
        return done
    }

    /**
     * The reply of [clientTurnId] was cut after [spokenChars]: the agent's memory keeps only what the
     * user heard, so a follow-up ("what was the last one?") is answered from the heard part.
     */
    private fun applyInterruption(clientTurnId: String, spokenChars: Int) {
        val index = turns[clientTurnId]?.replyIndex ?: return
        val reply = history.getOrNull(index) as? AgentTranscriptEntry.ModelText ?: return
        val heard = reply.text.take(spokenChars.coerceIn(0, reply.text.length)).trimEnd()
        history[index] = AgentTranscriptEntry.ModelText(if (heard.isEmpty()) CUT_MARK else "$heard $CUT_MARK")
    }

    private fun AgentStepResult.toVoiceError(): VoiceCallError = when (this) {
        AgentStepResult.NetworkError -> VoiceCallError.Unreachable
        AgentStepResult.ServiceError -> VoiceCallError.Unavailable
        // Never in voice mode (no credit is reserved); kept honest if the server ever charges.
        AgentStepResult.InsufficientCredits -> VoiceCallError.OutOfCredits
        is AgentStepResult.InvalidRequest -> VoiceCallError.ClientBug(reason ?: "invalid_request")
        is AgentStepResult.VoiceRefused -> when {
            error == VOICE_RATE_LIMITED -> VoiceCallError.RateLimited
            // Minutes ran out between two heartbeats: the next heartbeat ends the call with the
            // limit screen, so this turn only fails (not the whole call as "not entitled").
            reason == REASON_EXHAUSTED -> VoiceCallError.Other(code = "voice_session_exhausted")
            reason == REASON_MALFORMED -> VoiceCallError.ClientBug("voice_session_malformed")
            // not_found / closed / expired / disabled: the session is dead, every next turn would be
            // refused the same way — end the call now instead of after three failed turns.
            else -> VoiceCallError.NotEntitled(reason ?: "voice_session_invalid")
        }
        is AgentStepResult.ToolCalls, is AgentStepResult.Final, is AgentStepResult.Options ->
            VoiceCallError.Other(code = "unexpected_result")
    }

    private class TurnProgress(
        val userText: String,
        val seed: List<AgentTranscriptEntry>,
        val summary: List<ChecklistContext>,
    ) {
        val live = mutableListOf<AgentTranscriptEntry>()
        val actions = mutableListOf<VoiceAgentAction>()
        var rounds = 0
        var done: VoiceTurnEvent.Done? = null

        /** Index of this turn's reply in [history], for a later interruption. */
        var replyIndex: Int? = null
    }

    companion object {
        private const val TAG = "VoiceTurnGateway"

        /** Same ceiling as the text chat (`ChatViewModel.AGENT_MAX_ROUNDS`) and the server. */
        const val MAX_TOOL_ROUNDS = 5

        private const val VOICE_RATE_LIMITED = "voice_rate_limited"
        private const val REASON_EXHAUSTED = "exhausted"
        private const val REASON_MALFORMED = "malformed"

        /** Marks a reply the user cut off, in the agent's memory only (never spoken or shown). */
        private const val CUT_MARK = "…"
    }
}
