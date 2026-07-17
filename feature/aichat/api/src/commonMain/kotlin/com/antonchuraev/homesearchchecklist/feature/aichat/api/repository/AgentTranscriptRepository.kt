package com.antonchuraev.homesearchchecklist.feature.aichat.api.repository

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry

/**
 * Persistence for the AI-chat agent's TOOL ROUNDS (Room, `ai_agent_transcript`, db v18).
 *
 * Complements [ChatHistoryRepository] rather than replacing it: history keeps the prose the
 * user sees, this keeps the `model_tool_calls` / `tool_results` ping-pong that used to live in
 * a local variable of `runAgentTurn` and die with the turn. Restoring both is what lets the
 * assistant remember what it actually DID in an earlier session, not just what it said.
 *
 * Rounds are keyed by [turnMessageId] — the id of the user [ChatMessage] whose turn produced
 * them — so a caller can splice them back between that user message and its answer.
 *
 * Storage is local-only, exactly like chat history: no Firestore mirror, no `cloudId`. The
 * privacy policy's promise that "your full chat history is stored only locally on your device"
 * covers this table verbatim, and a sync would break that promise, not merely add a feature.
 */
interface AgentTranscriptRepository {
    /**
     * Rounds recorded for [turnMessageIds], grouped by turn and ordered oldest-first within it.
     * Turns with nothing recorded are absent from the map (never an empty list).
     *
     * Implementations MUST NOT throw for a transient storage failure: on web the Room/OPFS Web
     * Worker may still be warming up. Returning an empty map degrades the turn to "no tool
     * memory" — worse than perfect, far better than a failed send.
     */
    suspend fun loadForTurns(turnMessageIds: List<String>): Map<String, List<AgentTranscriptEntry>>

    /**
     * Appends one completed round: the calls the model asked for and the results the client
     * produced, written together so the pair can never be half-persisted.
     *
     * @param calls the round's `model_tool_calls` entry.
     * @param results the round's `tool_results` entry — one result per call, same order.
     */
    suspend fun appendRound(
        turnMessageId: String,
        calls: AgentTranscriptEntry.ModelToolCalls,
        results: AgentTranscriptEntry.ToolResults,
    )

    /** Drops every round previously recorded for [turnMessageId] (a turn owns one set). */
    suspend fun deleteTurn(turnMessageId: String)

    /** Keeps the [keepTurns] most recent turns; older turns are dropped whole. */
    suspend fun pruneToRecentTurns(keepTurns: Int)

    /** Deletes everything — the agent-memory half of the user's "Clear chat". */
    suspend fun clear()
}
