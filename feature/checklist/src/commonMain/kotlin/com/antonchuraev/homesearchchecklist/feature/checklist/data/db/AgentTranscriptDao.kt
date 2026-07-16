package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface AgentTranscriptDao {
    /**
     * Appends a whole round at once.
     *
     * Room wraps a multi-row `@Insert` in a transaction, and that is the point: a
     * `model_tool_calls` row committed WITHOUT its `tool_results` sibling would be replayed to
     * Gemini as a `function_call` with no `function_response` and rejected. Inserting the pair
     * in one call makes "killed between the two writes" unrepresentable.
     */
    @Insert
    suspend fun insertAll(entries: List<AgentTranscriptEntity>)

    /** Rounds belonging to [turnMessageIds], oldest first. Order within a turn is [seq]. */
    @Query(
        """
        SELECT * FROM ai_agent_transcript
        WHERE turnMessageId IN (:turnMessageIds)
        ORDER BY seq ASC
        """
    )
    suspend fun findByTurnIds(turnMessageIds: List<String>): List<AgentTranscriptEntity>

    /**
     * Drops any round already recorded for [turnMessageId].
     *
     * Makes re-running the agent loop for the same user message idempotent: a turn owns exactly
     * one set of rounds. Without it a re-run (e.g. an "Ask AI" escalation on a message that
     * already had one) would leave both attempts on disk and replay a doubled ping-pong later.
     */
    @Query("DELETE FROM ai_agent_transcript WHERE turnMessageId = :turnMessageId")
    suspend fun deleteByTurnId(turnMessageId: String)

    /**
     * Keeps the [keepTurns] most recent turns and deletes the rest.
     *
     * Prunes by TURN, never by row: cutting the table at an arbitrary row could delete a
     * `model_tool_calls` and strand its `tool_results`. Recency is `MAX(seq)` per turn, so a
     * turn is only ever dropped whole.
     */
    @Query(
        """
        DELETE FROM ai_agent_transcript
        WHERE turnMessageId NOT IN (
            SELECT turnMessageId FROM ai_agent_transcript
            GROUP BY turnMessageId
            ORDER BY MAX(seq) DESC
            LIMIT :keepTurns
        )
        """
    )
    suspend fun pruneToRecentTurns(keepTurns: Int)

    @Query("DELETE FROM ai_agent_transcript")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ai_agent_transcript")
    suspend fun count(): Int
}
