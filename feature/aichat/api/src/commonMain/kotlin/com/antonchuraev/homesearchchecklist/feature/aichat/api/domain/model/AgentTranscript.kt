package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

import kotlinx.serialization.json.JsonObject

/**
 * One entry in the structured agent transcript the client maintains and resends
 * each round (the CF is stateless). The client appends [ModelToolCalls] + [ToolResults]
 * after each round of execution.
 *
 * [UserText] / [ModelText] also carry recent CONVERSATION HISTORY: before the current
 * turn's tool rounds, the client seeds the transcript with prior chat messages
 * (user → [UserText], assistant → [ModelText]) so the agent has the context needed to
 * act on referential confirmations (e.g. "да, добавь все" referring to a proposal the
 * assistant just made — plan finding #2).
 */
sealed interface AgentTranscriptEntry {
    data class UserText(val text: String) : AgentTranscriptEntry
    /** Assistant prose (a prior conversational turn, or a final agent answer). */
    data class ModelText(val text: String) : AgentTranscriptEntry
    data class ModelToolCalls(val calls: List<AgentToolCall>) : AgentTranscriptEntry
    data class ToolResults(val results: List<AgentToolResult>) : AgentTranscriptEntry
}

/**
 * A tool call the model requested. [args] is preserved verbatim (echoed back next round).
 *
 * [thoughtSignature] is an opaque base64 blob the server round-trips for Gemini 3.x models,
 * which reject a replayed tool call that arrives without it. The client never reads it —
 * it only has to hand back exactly what it was given. Null on models that emit none
 * (the 2.5 control arm) and on entries restored from history saved before this field existed;
 * the server substitutes a documented placeholder in that case, at some cost to answer quality.
 */
data class AgentToolCall(
    val id: String,
    val name: String,
    val args: JsonObject,
    val thoughtSignature: String? = null,
)

/** The result of executing a tool call. [result] is the dispatcher's serialized outcome. */
data class AgentToolResult(val id: String, val name: String, val result: JsonObject)
