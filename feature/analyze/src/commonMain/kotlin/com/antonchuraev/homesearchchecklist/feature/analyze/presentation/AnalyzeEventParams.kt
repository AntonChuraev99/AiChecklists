package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult

/**
 * Param maps of `ai_analyze_started` / `ai_analyze_completed` / `ai_analyze_failed`.
 *
 * One builder for every emitter. The Analyze screen is the main one; the onboarding's typed goal
 * turns text into a list in place (no Analyze screen) and emits the same three events. Two hand-built
 * maps of one event drift, and a dimension missing on one emitter cannot segment the funnel at all.
 *
 * [inputType] is `AnalyzeInputKind.wire` / `InputDataType.name.lowercase()`; [source] is
 * `AiEntrySource.wire` or `"none"`. [surface] is set only by an emitter that is NOT the Analyze
 * screen (`AnalyticsEvents.Analyze.SURFACE_ONBOARDING`); null omits the key.
 */
object AnalyzeEventParams {

    /**
     * Additive dimension of a background job's completed / failed event: `foreground` or
     * `background` at the moment it finished. NOT `surface` — CSAT reads `surface == onboarding`.
     */
    const val APP_STATE = "app_state"
    const val APP_STATE_FOREGROUND = "foreground"
    const val APP_STATE_BACKGROUND = "background"

    /** `ai_analyze_result_opened`: the user opened a finished job's outcome ([VIA] / [OUTCOME]). */
    const val EVENT_RESULT_OPENED = "ai_analyze_result_opened"
    const val VIA = "via"
    const val OUTCOME = "outcome"

    fun resultOpened(via: String, outcome: String): Map<String, Any> = mapOf(VIA to via, OUTCOME to outcome)

    fun started(inputType: String, source: String, surface: String? = null): Map<String, Any> = buildMap {
        put(AnalyticsParams.INPUT_TYPE, inputType)
        put(AnalyticsParams.SOURCE, source)
        surface?.let { put(AnalyticsParams.SURFACE, it) }
    }

    fun completed(
        inputType: String,
        source: String,
        result: AnalyzeResult,
        surface: String? = null,
        appState: String? = null,
    ): Map<String, Any> = buildMap {
        put(AnalyticsParams.INPUT_TYPE, inputType)
        put(AnalyticsParams.SOURCE, source)
        surface?.let { put(AnalyticsParams.SURFACE, it) }
        appState?.let { put(APP_STATE, it) }
        put(AnalyticsParams.ITEM_COUNT, result.suggestedItems.size)
        // Guardrail dimensions — present only when the server sent an arm; omitted otherwise so the
        // event schema stays clean (matches ai_chat_response_received).
        result.modelVariant?.let { put(AnalyticsParams.AI_MODEL_VARIANT, it) }
        result.modelId?.let { put(AnalyticsParams.AI_MODEL_ID, it) }
        result.aiFlow?.let { put(AnalyticsParams.AI_FLOW, it) }
    }

    fun failed(
        inputType: String,
        source: String,
        reason: AiFailureReason,
        error: Throwable,
        surface: String? = null,
        appState: String? = null,
    ): Map<String, Any> = buildMap {
        put(AnalyticsParams.INPUT_TYPE, inputType)
        put(AnalyticsParams.SOURCE, source)
        surface?.let { put(AnalyticsParams.SURFACE, it) }
        appState?.let { put(APP_STATE, it) }
        put(AnalyticsParams.FAILURE_REASON, reason.wireValue)
        put(AnalyticsParams.ERROR, error.message ?: "unknown")
    }
}
