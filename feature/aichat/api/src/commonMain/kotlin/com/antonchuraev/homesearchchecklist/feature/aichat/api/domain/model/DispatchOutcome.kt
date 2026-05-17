package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Result of dispatching a [ToolCall] to the underlying repository layer.
 *
 * [Success.humanReadable] is a UI-ready string (e.g. "Added 'Milk' to Shopping").
 * [AmbiguousMatch.candidates] lists checklist names when the hint matched >1 list.
 * [NotFound.reason] explains what was not found (item, checklist, etc.).
 * [RequiresPremium] gates write operations for free-tier users (future enforcement).
 */
sealed interface DispatchOutcome {
    data class Success(val humanReadable: String) : DispatchOutcome
    data class AmbiguousMatch(val candidates: List<String>) : DispatchOutcome
    data class NotFound(val reason: String) : DispatchOutcome
    data object RequiresPremium : DispatchOutcome
}
