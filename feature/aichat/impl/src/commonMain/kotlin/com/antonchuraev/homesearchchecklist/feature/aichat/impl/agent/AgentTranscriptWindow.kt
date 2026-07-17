package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentTranscriptEntry

/**
 * Chooses how much of a persisted conversation to send to `chat_agent`, and guarantees the
 * slice is a shape Gemini will accept.
 *
 * ## Why a window is mandatory, not an optimisation
 *
 * The server does not trim: `CHAT_AGENT_MAX_TRANSCRIPT_ENTRIES = 60` answers **HTTP 400**, it
 * does not drop the oldest entries. A transcript grows by 2 entries per tool round plus 2 per
 * turn, so an un-windowed persisted conversation reaches 60 in roughly 15-20 turns and then
 * every further message fails. The window is what keeps a long-lived chat alive.
 *
 * The other server cap, `CHAT_AGENT_MAX_TOTAL_CHARS = 12000`, counts **only `role="user"`
 * entries** — assistant prose and tool results are counted nowhere. So it cannot be relied on
 * to bound anything: the client has to measure its own payload. [MAX_CHARS] is deliberately
 * stricter than 12000 and counts EVERY entry, which makes it a true upper bound on the user
 * dimension too.
 *
 * ## Why the cut is by TURN and never by entry
 *
 * Cutting a fixed number of entries off the front can slice between a `model_tool_calls` and
 * its `tool_results`. Gemini rejects a `function_response` with no matching `function_call`
 * (and vice versa), so that slice is a 400 — a failure mode that appears only after a user has
 * chatted enough to trigger truncation, i.e. never in a quick manual test.
 *
 * A TURN (one user message plus everything answering it, up to the next user message) contains
 * its rounds by construction, so cutting at turn boundaries makes the pairing invariant true by
 * shape rather than by vigilance. [sanitize] then covers the one case turn-cutting cannot: rows
 * that arrive already broken (a payload that failed to decode, an interrupted write from an
 * older build).
 *
 * `thoughtSignature` needs no special handling here for the same structural reason: it rides
 * inside the `model_tool_calls` entry, which is only ever kept or dropped whole.
 */
internal object AgentTranscriptWindow {

    /**
     * Turns of context to send, newest-first. Six covers the referential back-and-forth the
     * agent actually needs ("да, добавь все", "а во второй список?") without paying tokens for
     * a conversation the user has moved on from.
     */
    const val MAX_TURNS = 6

    /**
     * Entry ceiling for the SEED (the past turns plus the new user message), chosen against the
     * server's 60-entry 400 with the live turn's growth left in: the loop can still append
     * `AGENT_MAX_ROUNDS * 2 = 10` entries after this window is computed, so the worst case on
     * the wire is 40 — a real margin, not a fit.
     */
    const val MAX_ENTRIES = 30

    /**
     * Character ceiling across ALL entries of the seed. Bounds token cost (tool results are
     * machine data and can be large) and, because it counts user text too, keeps the payload
     * comfortably inside the server's user-only 12000 cap.
     */
    const val MAX_CHARS = 8000

    /**
     * Returns the newest turns of [entries] that fit the caps, oldest-first.
     *
     * The most recent turn is always kept even if it alone exceeds a cap: dropping the message
     * the user just sent would be a worse answer than a big one, and a single turn cannot
     * exceed the entry ceiling anyway.
     */
    fun select(
        entries: List<AgentTranscriptEntry>,
        maxTurns: Int = MAX_TURNS,
        maxEntries: Int = MAX_ENTRIES,
        maxChars: Int = MAX_CHARS,
    ): List<AgentTranscriptEntry> {
        val clean = sanitize(entries)
        if (clean.isEmpty()) return emptyList()

        var window = groupIntoTurns(clean).takeLast(maxTurns)
        while (
            window.size > 1 &&
            (window.sumOf { it.size } > maxEntries || window.sumOf { turn -> turn.sumOf { charsOf(it) } } > maxChars)
        ) {
            window = window.drop(1)
        }
        return window.flatten()
    }

    /**
     * Drops tool entries that are not part of an adjacent call→results pair.
     *
     * Defence in depth for rows that are already broken on disk. The realistic source is a
     * decode failure: the reader skips one unreadable payload, and its sibling — perfectly
     * readable — becomes an orphan that would 400 the whole turn. Better to lose one round of
     * memory than the send.
     */
    fun sanitize(entries: List<AgentTranscriptEntry>): List<AgentTranscriptEntry> {
        val out = mutableListOf<AgentTranscriptEntry>()
        var i = 0
        while (i < entries.size) {
            when (val entry = entries[i]) {
                is AgentTranscriptEntry.ModelToolCalls -> {
                    val next = entries.getOrNull(i + 1)
                    if (next is AgentTranscriptEntry.ToolResults) {
                        out += entry
                        out += next
                        i += 2
                    } else {
                        // Calls with no results: a `function_call` Gemini would demand an answer for.
                        i += 1
                    }
                }
                // Results whose calls are gone (or were never adjacent) — an unanswerable
                // `function_response`. Reached only via a broken row; a healthy pair is
                // consumed by the branch above.
                is AgentTranscriptEntry.ToolResults -> i += 1
                else -> {
                    out += entry
                    i += 1
                }
            }
        }
        return out
    }

    /** Splits at every [AgentTranscriptEntry.UserText]; anything before the first is its own group. */
    private fun groupIntoTurns(entries: List<AgentTranscriptEntry>): List<List<AgentTranscriptEntry>> {
        val turns = mutableListOf<MutableList<AgentTranscriptEntry>>()
        for (entry in entries) {
            if (entry is AgentTranscriptEntry.UserText || turns.isEmpty()) {
                turns.add(mutableListOf())
            }
            turns.last().add(entry)
        }
        return turns
    }

    /** Approximate wire size of one entry. JsonObject.toString() is the JSON it serialises to. */
    private fun charsOf(entry: AgentTranscriptEntry): Int = when (entry) {
        is AgentTranscriptEntry.UserText -> entry.text.length
        is AgentTranscriptEntry.ModelText -> entry.text.length
        is AgentTranscriptEntry.ModelToolCalls -> entry.calls.sumOf {
            it.name.length + it.args.toString().length + (it.thoughtSignature?.length ?: 0)
        }
        is AgentTranscriptEntry.ToolResults -> entry.results.sumOf {
            it.name.length + it.result.toString().length
        }
    }
}
