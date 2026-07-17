package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 *
 * ## Persistence (Stage 3)
 *
 * The type is [Serializable] so the tool rounds of PAST turns survive an app restart
 * (`ai_agent_transcript` table, added in database version 18). Only [ModelToolCalls] /
 * [ToolResults] are actually stored — the text entries are rebuilt from `ai_chat_history`,
 * which stays the single source of truth for anything the user can see.
 *
 * **The [SerialName] on each variant is a storage contract, not decoration.** Without it
 * kotlinx writes the fully-qualified class name as the polymorphic discriminator, and every
 * package rename would silently orphan rows already on disk.
 */
@Serializable
sealed interface AgentTranscriptEntry {
    @Serializable
    @SerialName("user_text")
    data class UserText(val text: String) : AgentTranscriptEntry

    /** Assistant prose (a prior conversational turn, or a final agent answer). */
    @Serializable
    @SerialName("model_text")
    data class ModelText(val text: String) : AgentTranscriptEntry

    @Serializable
    @SerialName("model_tool_calls")
    data class ModelToolCalls(val calls: List<AgentToolCall>) : AgentTranscriptEntry

    @Serializable
    @SerialName("tool_results")
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
 *
 * Since Stage 3 the signature is also written to disk as part of this entry's JSON payload:
 * a persisted tool call that came back from Room without its signature would degrade exactly
 * like a legacy one, so it must round-trip through storage as faithfully as through the wire.
 */
@Serializable
data class AgentToolCall(
    val id: String,
    val name: String,
    val args: JsonObject,
    @SerialName("thought_signature") val thoughtSignature: String? = null,
)

/** The result of executing a tool call. [result] is the dispatcher's serialized outcome. */
@Serializable
data class AgentToolResult(val id: String, val name: String, val result: JsonObject)
