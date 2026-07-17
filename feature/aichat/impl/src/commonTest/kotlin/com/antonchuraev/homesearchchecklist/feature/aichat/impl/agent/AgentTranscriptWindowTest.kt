package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolResult
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Green coverage for the send-window policy (Stage 3). The invariant under test is not "how many
 * entries survive" but "the survivors are a shape Gemini accepts": whole turns, every
 * `model_tool_calls` still adjacent to its `tool_results`, and no orphan on either side.
 */
class AgentTranscriptWindowTest {

    private fun user(text: String) = AgentTranscriptEntry.UserText(text)
    private fun model(text: String) = AgentTranscriptEntry.ModelText(text)

    private fun calls(id: String, thoughtSignature: String? = null) =
        AgentTranscriptEntry.ModelToolCalls(
            listOf(
                AgentToolCall(
                    id = id,
                    name = "add_item",
                    args = buildJsonObject { put("item", id) },
                    thoughtSignature = thoughtSignature,
                ),
            ),
        )

    private fun results(id: String) =
        AgentTranscriptEntry.ToolResults(
            listOf(AgentToolResult(id = id, name = "add_item", result = buildJsonObject { put("status", "success") })),
        )

    /** One full agentic turn: user text, a call↔results round, then the final answer. */
    private fun turn(n: Int) = listOf(user("q$n"), calls("t$n"), results("t$n"), model("a$n"))

    private fun List<AgentTranscriptEntry>.toolPairsBalanced(): Boolean {
        var open = 0
        for (e in this) {
            when (e) {
                is AgentTranscriptEntry.ModelToolCalls -> open++
                is AgentTranscriptEntry.ToolResults -> {
                    // Every results entry must be immediately preceded by a calls entry.
                    open--
                    if (open < 0) return false
                }
                else -> Unit
            }
        }
        return open == 0
    }

    // ── Turn windowing ──────────────────────────────────────────────────────────

    @Test
    fun select_keepsOnlyMostRecentTurns_whenOverTurnCap() {
        val entries = (1..10).flatMap { turn(it) }

        val out = AgentTranscriptWindow.select(entries, maxTurns = 3, maxEntries = 1000, maxChars = 100_000)

        // Newest 3 turns → their 3 user texts, oldest-first.
        val userTexts = out.filterIsInstance<AgentTranscriptEntry.UserText>().map { it.text }
        assertEquals(listOf("q8", "q9", "q10"), userTexts)
        assertTrue(out.toolPairsBalanced(), "call↔results pairs must stay balanced after windowing")
    }

    @Test
    fun select_dropsWholeTurns_toFitEntryCap() {
        val entries = (1..6).flatMap { turn(it) } // 24 entries, 6 turns

        // Cap of 10 entries → at most 2 full turns (8 entries); a 3rd would be 12.
        val out = AgentTranscriptWindow.select(entries, maxTurns = 6, maxEntries = 10, maxChars = 100_000)

        assertTrue(out.size <= 10, "must respect the entry cap")
        assertTrue(out.toolPairsBalanced(), "must not slice a turn mid-round")
        // Boundaries are turns: the count is a multiple of 4 (each turn here is 4 entries).
        assertEquals(0, out.size % 4)
    }

    @Test
    fun select_keepsAtLeastTheNewestTurn_evenWhenItAloneExceedsCharCap() {
        val big = "x".repeat(50)
        val entries = listOf(user(big), calls("t1"), results("t1"), model(big))

        // maxChars far below the single turn's size → we still keep it (dropping the just-sent
        // message would be a worse answer than a big payload).
        val out = AgentTranscriptWindow.select(entries, maxTurns = 6, maxEntries = 1000, maxChars = 5)

        assertEquals(entries, out)
    }

    // ── Sanitize (orphan defence) ────────────────────────────────────────────────

    @Test
    fun sanitize_dropsOrphanResults_withNoPrecedingCalls() {
        val entries = listOf(user("q1"), results("orphan"), model("a1"))

        val out = AgentTranscriptWindow.sanitize(entries)

        assertEquals(listOf(user("q1"), model("a1")), out)
        assertTrue(out.toolPairsBalanced())
    }

    @Test
    fun sanitize_dropsCalls_withNoFollowingResults() {
        val entries = listOf(user("q1"), calls("dangling"), model("a1"))

        val out = AgentTranscriptWindow.sanitize(entries)

        assertEquals(listOf(user("q1"), model("a1")), out)
        assertTrue(out.toolPairsBalanced())
    }

    @Test
    fun sanitize_keepsAdjacentPair() {
        val entries = turn(1)
        assertEquals(entries, AgentTranscriptWindow.sanitize(entries))
    }

    @Test
    fun select_emptyInput_returnsEmpty() {
        assertEquals(emptyList(), AgentTranscriptWindow.select(emptyList()))
    }

    @Test
    fun select_preservesThoughtSignature_inKeptRounds() {
        val entries = listOf(user("q1"), calls("t1", thoughtSignature = "SIG-abc"), results("t1"), model("a1"))

        val out = AgentTranscriptWindow.select(entries, maxTurns = 6, maxEntries = 1000, maxChars = 100_000)

        val kept = out.filterIsInstance<AgentTranscriptEntry.ModelToolCalls>().single()
        assertEquals("SIG-abc", kept.calls.single().thoughtSignature)
    }
}
