package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Room entity for the AI-chat agent transcript (database version 18).
 *
 * Stores ONLY the agent's tool rounds — `model_tool_calls` and `tool_results`. The user /
 * assistant prose of a conversation is NOT duplicated here: it is rebuilt from
 * [ChatHistoryEntry], which remains the single source of truth for everything the user can
 * see. Splitting it the other way (a second full copy of the conversation) would let the two
 * stores drift, and the drift would be invisible — the model would silently remember a
 * different conversation from the one on screen.
 *
 * ## Fields
 *
 * [seq] is the insertion order and the only ordering key. It is `INTEGER PRIMARY KEY
 * AUTOINCREMENT`, i.e. an alias of SQLite's rowid, so it is already indexed — a separate
 * index on it would be dead weight.
 *
 * [turnMessageId] is the id of the [ChatHistoryEntry] **user** message whose turn produced
 * this round. It is what lets the rebuild put the rounds back between the user message and
 * the assistant answer of the same turn. Indexed: every read filters on it.
 *
 * [kind] mirrors the payload's polymorphic discriminator ("model_tool_calls" / "tool_results")
 * as a real column, so the pairing invariant can be checked and the table can be inspected
 * without parsing JSON.
 *
 * [payloadJson] is the serialised [AgentTranscriptEntry]. Tool args and results are already
 * `JsonObject` on the domain model, so this is a faithful round-trip — including the
 * `thought_signature` Gemini 3.x requires back on a replayed call.
 */
@Entity(
    tableName = "ai_agent_transcript",
    indices = [Index("turnMessageId")],
)
data class AgentTranscriptEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0L,
    val turnMessageId: String,
    val kind: String,
    val payloadJson: String,
    val timestamp: Long,
)
