package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * Result of dispatching a [ToolCall] to the underlying repository layer.
 *
 * [Success.messageKey] refers to a `chat_dispatch_*` string resource; [Success.args]
 * populate `%1$s` / `%2$s` positional placeholders in that resource.
 * [AmbiguousMatch.candidates] lists checklist names when the hint matched >1 list.
 * [NotFound.messageKey] refers to a `chat_dispatch_*` string resource explaining what
 * was not found; [NotFound.args] populate positional placeholders.
 * [RequiresPremium] gates write operations for free-tier users (future enforcement).
 */
sealed interface DispatchOutcome {
    data class Success(
        val messageKey: String,
        val args: List<String> = emptyList(),
    ) : DispatchOutcome

    data class AmbiguousMatch(val candidates: List<String>) : DispatchOutcome

    data class NotFound(
        val messageKey: String,
        val args: List<String> = emptyList(),
    ) : DispatchOutcome

    data object RequiresPremium : DispatchOutcome
}
