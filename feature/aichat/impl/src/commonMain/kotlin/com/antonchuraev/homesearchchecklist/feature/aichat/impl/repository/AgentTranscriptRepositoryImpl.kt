package com.antonchuraev.homesearchchecklist.feature.aichat.impl.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.AgentTranscriptRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.AgentTranscriptDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.AgentTranscriptEntity
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Room-backed [AgentTranscriptRepository].
 *
 * ## Reads retry, writes do not
 *
 * On wasmJs the database is a SQLite Web Worker over OPFS that is **not ready when the app
 * starts** — the first query throws. [ChatViewModel]'s history seed already lives with this
 * (5 × 400 ms, log-only, never a snackbar the user cannot act on), and a read here is reached
 * on the same cold path, so it retries the same way. Android resolves the DB instantly and
 * never spends a retry.
 *
 * Writes deliberately do not retry: by the time a round completes, a network call has been
 * awaited and the worker is long warm. A failed write costs the agent one round of memory,
 * which is logged and survivable — it must never cost the user their answer.
 */
internal class AgentTranscriptRepositoryImpl(
    private val dao: AgentTranscriptDao,
    private val logger: AppLogger,
) : AgentTranscriptRepository {

    companion object {
        private const val TAG = "AgentTranscript"

        /** Mirrors ChatViewModel.HISTORY_LOAD_MAX_RETRIES — same OPFS warm-up, same budget. */
        private const val LOAD_MAX_RETRIES = 5

        /** Mirrors ChatViewModel.HISTORY_LOAD_RETRY_DELAY_MS: ~5 × 400 ms covers the worker start. */
        private const val LOAD_RETRY_DELAY_MS = 400L

        internal const val KIND_MODEL_TOOL_CALLS = "model_tool_calls"
        internal const val KIND_TOOL_RESULTS = "tool_results"

        // ignoreUnknownKeys: forward-compat — a field added to AgentToolCall by a later build
        // must not make an older one choke on its own rows.
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun loadForTurns(
        turnMessageIds: List<String>,
    ): Map<String, List<AgentTranscriptEntry>> {
        if (turnMessageIds.isEmpty()) return emptyMap()

        val rows = readWithRetry(turnMessageIds) ?: return emptyMap()

        return rows
            .groupBy { it.turnMessageId }
            .mapValues { (turnId, turnRows) ->
                turnRows.mapNotNull { row -> row.decodeOrNull(turnId) }
            }
            .filterValues { it.isNotEmpty() }
    }

    override suspend fun appendRound(
        turnMessageId: String,
        calls: AgentTranscriptEntry.ModelToolCalls,
        results: AgentTranscriptEntry.ToolResults,
    ) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        runCatching {
            // One insert = one transaction: the pair is committed together or not at all, so a
            // call can never be replayed to Gemini without the response it demands.
            dao.insertAll(
                listOf(
                    AgentTranscriptEntity(
                        turnMessageId = turnMessageId,
                        kind = KIND_MODEL_TOOL_CALLS,
                        payloadJson = json.encodeToString(AgentTranscriptEntry.serializer(), calls),
                        timestamp = now,
                    ),
                    AgentTranscriptEntity(
                        turnMessageId = turnMessageId,
                        kind = KIND_TOOL_RESULTS,
                        payloadJson = json.encodeToString(AgentTranscriptEntry.serializer(), results),
                        timestamp = now,
                    ),
                )
            )
        }.onFailure { e ->
            // Non-fatal by design: the turn in flight is unaffected, only the memory of this
            // round is lost. Loud in the log because a silent gap here looks like the model
            // "forgetting" for no reason later.
            logger.error(TAG, "appendRound: failed to persist round for turn=$turnMessageId — ${e.message}", e)
        }
    }

    override suspend fun deleteTurn(turnMessageId: String) {
        runCatching { dao.deleteByTurnId(turnMessageId) }
            .onFailure { e ->
                logger.warning(TAG, "deleteTurn: failed for turn=$turnMessageId — ${e.message}")
            }
    }

    override suspend fun pruneToRecentTurns(keepTurns: Int) {
        runCatching { dao.pruneToRecentTurns(keepTurns) }
            .onFailure { e ->
                logger.warning(TAG, "pruneToRecentTurns($keepTurns) failed — ${e.message}")
            }
    }

    override suspend fun clear() {
        logger.info(TAG, "clear: deleting all agent transcript rounds")
        runCatching { dao.deleteAll() }
            .onFailure { e -> logger.error(TAG, "clear: failed — ${e.message}", e) }
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    /** Null means "give up" — the caller degrades to no tool memory rather than failing. */
    private suspend fun readWithRetry(turnMessageIds: List<String>): List<AgentTranscriptEntity>? {
        repeat(LOAD_MAX_RETRIES) { attempt ->
            val result = runCatching { dao.findByTurnIds(turnMessageIds) }
            result.onSuccess { return it }
            result.onFailure { e ->
                logger.warning(
                    TAG,
                    "loadForTurns: attempt ${attempt + 1} failed (DB not ready?) — ${e.message}",
                )
            }
            delay(LOAD_RETRY_DELAY_MS)
        }
        logger.error(
            TAG,
            "loadForTurns: giving up after $LOAD_MAX_RETRIES attempts — this turn runs without tool memory",
            null,
        )
        return null
    }

    /**
     * A row that will not decode is skipped, not fatal. Skipping can orphan its sibling, which
     * is why the window sanitises pairs after this returns.
     */
    private fun AgentTranscriptEntity.decodeOrNull(turnId: String): AgentTranscriptEntry? =
        runCatching { json.decodeFromString(AgentTranscriptEntry.serializer(), payloadJson) }
            .getOrElse { e ->
                logger.warning(
                    TAG,
                    "decode: dropping unreadable row seq=$seq kind=$kind turn=$turnId — ${e.message}",
                )
                null
            }
}
