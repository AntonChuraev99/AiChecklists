package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Converts a [DispatchOutcome] to a compact [JsonObject] suitable for sending back
 * to Gemini as a `function_response` result. The agent loop sends this as
 * [AgentToolResult.result].
 *
 * Format per outcome:
 * - [DispatchOutcome.Success]          → `{ "status":"success", "details":[...args], "checklist_id": N }`
 *                                        (`details` omitted when args empty; `checklist_id` omitted when null)
 * - [DispatchOutcome.ChecklistContent] → `{ "status":"success", "checklist":"name", "items":[{"text":"...","done":bool}...] }`
 * - [DispatchOutcome.NotFound]         → `{ "status":"not_found", "reason":"…", "details":[...args] }`
 *                                        (`reason` only for the keys in [MACHINE_REASONS])
 * - [DispatchOutcome.AmbiguousMatch]   → `{ "status":"ambiguous", "candidates":["a","b",...] }`
 * - [DispatchOutcome.RequiresPremium]  → `{ "status":"requires_premium" }`
 *
 * All serialization is pure — no coroutines, no I/O.
 */
internal object AgentToolResultSerializer {

    /**
     * Outcome keys whose REASON the model must be able to act on, mapped to a stable machine word.
     *
     * `status` + `details` cannot carry this: a move refused because the row owns a reminder and a
     * move of a row that does not exist both serialize to
     * `{"status":"not_found","details":[…]}`, so the model reports "I couldn't find that task"
     * about an item the user can plainly see — and the carefully worded explanation the client
     * translated into three languages never reaches any surface, because [MoveItem] is agent-only
     * and its `messageKey` dies here.
     *
     * Deliberately an ALLOWLIST rather than "always emit the messageKey": every other outcome is
     * already fully described by `status`, and adding a field to results the CONTROL arm of the
     * live nav_v2_arm A/B also receives would change the frozen baseline's model input. Add a key
     * here only when the model must branch on the reason, not merely report it.
     */
    private val MACHINE_REASONS = mapOf(
        // "This row carries a reminder / repeat / attachment, so the client refused to move it."
        // The model is expected to say so and leave the row where it is.
        "chat_dispatch_move_blocked" to "move_blocked",
    )

    /** Serializes a [DispatchOutcome] to a [JsonObject] for the agent loop. */
    fun serialize(outcome: DispatchOutcome): JsonObject = when (outcome) {
        is DispatchOutcome.Success -> buildJsonObject {
            put("status", "success")
            if (outcome.args.isNotEmpty()) {
                putJsonArray("details") {
                    outcome.args.forEach { add(it) }
                }
            }
            outcome.linkedChecklistId?.let { put("checklist_id", it) }
        }

        is DispatchOutcome.ChecklistContent -> buildJsonObject {
            put("status", "success")
            put("checklist", outcome.checklistName)
            putJsonArray("items") {
                outcome.items.forEach { item ->
                    add(buildJsonObject {
                        put("text", item.text)
                        put("done", item.checked)
                    })
                }
            }
        }

        is DispatchOutcome.NotFound -> buildJsonObject {
            put("status", "not_found")
            MACHINE_REASONS[outcome.messageKey]?.let { put("reason", it) }
            if (outcome.args.isNotEmpty()) {
                putJsonArray("details") {
                    outcome.args.forEach { add(it) }
                }
            }
        }

        is DispatchOutcome.AmbiguousMatch -> buildJsonObject {
            put("status", "ambiguous")
            putJsonArray("candidates") {
                outcome.candidates.forEach { add(it) }
            }
        }

        DispatchOutcome.RequiresPremium -> buildJsonObject {
            put("status", "requires_premium")
        }
    }

    /**
     * Returns a `{ "status":"declined" }` result for when the user cancels the plan-card.
     *
     * The agent loop uses this to satisfy the N-calls == N-results invariant required by
     * Gemini's function-calling protocol: every [AgentToolCall] in a turn must have a
     * matching [AgentToolResult], even if the client chose not to execute it.
     */
    fun declinedResult(): JsonObject = buildJsonObject {
        put("status", "declined")
    }
}
