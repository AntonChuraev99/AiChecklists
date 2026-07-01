package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * The server-assigned AI-model A/B arm, deterministic per user across every AI flow
 * (chat / analyze / create). Carried in each AI success response as `model_variant` + `model_id`.
 *
 * @property variant one of "control" | "variant_b" | "override" (never blank — a blank arm is
 *   modelled as `null` [AiModelArm], meaning "experiment off / unknown").
 * @property modelId the concrete model (e.g. "gemini-3.1-flash-lite"); null when the server omitted it.
 */
data class AiModelArm(
    val variant: String,
    val modelId: String? = null,
)

/**
 * App-scoped holder for the AI-model A/B experiment arm. One instance, shared by every AI flow.
 *
 * Why it exists: revenue is the north-star of the model experiment, but a purchase can happen in a
 * session with no AI interaction (or on the paywall right after cold start). So the arm is
 *  1. mirrored into a sticky [AnalyticsParams.AI_MODEL_VARIANT] user-property the first time it is
 *     seen from ANY AI flow — segmenting every downstream event, incl. the paywall funnel; and
 *  2. persisted, so [current] can attach `ai_model_variant`/`ai_model_id` to the purchase event even
 *     across app restarts when no AI call happened that session.
 *
 * All methods are best-effort and never throw: analytics / attribution must never break an AI turn
 * or a purchase.
 */
interface AiModelExperimentTracker {
    /**
     * Records the arm from an AI success response. No-op when [variant] is null/blank (experiment
     * off / older server). Sets the sticky user-property and persists the arm only when the value
     * first appears or actually changes (guarded per process), so it normally runs once per session.
     */
    suspend fun report(variant: String?, modelId: String?, aiFlow: String? = null)

    /** Last persisted arm, or null when unknown (no AI response seen yet on this device). */
    suspend fun current(): AiModelArm?
}
