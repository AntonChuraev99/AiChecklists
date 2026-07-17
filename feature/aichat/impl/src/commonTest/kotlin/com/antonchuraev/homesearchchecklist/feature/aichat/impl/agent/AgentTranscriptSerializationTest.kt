package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Persistence contract for the transcript entries stored in `ai_agent_transcript` (Stage 3).
 *
 * These tests pin two things a package rename or an accidental annotation drop would silently
 * break on already-stored rows:
 *   1. the polymorphic discriminator is the STABLE @SerialName, never the class's FQN;
 *   2. `thoughtSignature` survives the round-trip — a dropped one degrades Gemini 3.x answers
 *      exactly like the wire-loss incident this field exists to prevent.
 */
class AgentTranscriptSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun roundTrip(entry: AgentTranscriptEntry): AgentTranscriptEntry {
        val encoded = json.encodeToString(AgentTranscriptEntry.serializer(), entry)
        return json.decodeFromString(AgentTranscriptEntry.serializer(), encoded)
    }

    @Test
    fun modelToolCalls_roundTrips_withStableDiscriminatorAndSignature() {
        val entry = AgentTranscriptEntry.ModelToolCalls(
            listOf(
                AgentToolCall(
                    id = "call-1",
                    name = "add_item",
                    args = buildJsonObject {
                        put("checklist", "Groceries")
                        put("item", "milk")
                    },
                    thoughtSignature = "BASE64-SIGNATURE",
                ),
            ),
        )

        val encoded = json.encodeToString(AgentTranscriptEntry.serializer(), entry)
        // The on-disk discriminator is the SerialName, not the Kotlin FQN — the whole point of
        // annotating the sealed hierarchy. A rename must not orphan stored rows.
        assertTrue(encoded.contains("\"model_tool_calls\""), "expected stable discriminator, got: $encoded")
        assertTrue(encoded.contains("\"thought_signature\""), "signature key must be persisted")

        val decoded = roundTrip(entry) as AgentTranscriptEntry.ModelToolCalls
        assertEquals(entry, decoded)
        assertEquals("BASE64-SIGNATURE", decoded.calls.single().thoughtSignature)
    }

    @Test
    fun modelToolCalls_nullSignature_roundTrips() {
        val entry = AgentTranscriptEntry.ModelToolCalls(
            listOf(AgentToolCall(id = "c", name = "read_checklist", args = buildJsonObject { })),
        )
        val decoded = roundTrip(entry) as AgentTranscriptEntry.ModelToolCalls
        assertEquals(null, decoded.calls.single().thoughtSignature)
    }

    @Test
    fun toolResults_roundTrips_preservingJsonObject() {
        val entry = AgentTranscriptEntry.ToolResults(
            listOf(
                AgentToolResult(
                    id = "call-1",
                    name = "add_item",
                    result = buildJsonObject {
                        put("status", "success")
                        put("checklist_id", 42)
                    },
                ),
            ),
        )
        val encoded = json.encodeToString(AgentTranscriptEntry.serializer(), entry)
        assertTrue(encoded.contains("\"tool_results\""))
        assertEquals(entry, roundTrip(entry))
    }

    @Test
    fun userAndModelText_roundTrip_withDiscriminators() {
        val u = AgentTranscriptEntry.UserText("add milk")
        val m = AgentTranscriptEntry.ModelText("Added milk to Groceries.")

        assertTrue(json.encodeToString(AgentTranscriptEntry.serializer(), u).contains("\"user_text\""))
        assertTrue(json.encodeToString(AgentTranscriptEntry.serializer(), m).contains("\"model_text\""))
        assertEquals(u, roundTrip(u))
        assertEquals(m, roundTrip(m))
    }
}
