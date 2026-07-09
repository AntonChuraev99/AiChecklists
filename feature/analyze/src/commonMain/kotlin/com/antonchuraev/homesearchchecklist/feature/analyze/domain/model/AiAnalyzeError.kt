package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

/**
 * Typed failure taxonomy for the AI-analyze path.
 *
 * Why typed (not a raw [Throwable] or a stringly message): the ~40% `ai_analyze_failed` rate
 * decomposed into several distinct causes (transient 5xx, client network/DNS/timeout, empty
 * user_id, App Check 403, credit gate). Presentation needs to react DIFFERENTLY per cause (a
 * distinct "signing you in…" prompt for [UserNotReady], a generic retry for transport errors),
 * and analytics needs a coarse machine `reason` instead of reverse-engineering free text. Keeping
 * the type all the way to the ViewModel is the KMP boundary pattern — the data layer (which owns
 * Ktor) maps transport exceptions into these variants so presentation never imports Ktor.
 *
 * Extends [Exception] so it flows unchanged through `Result.failure(it)` and the existing
 * `.fold { onFailure }` chains, while [AiFailureReason.classify] pattern-matches the variant.
 */
sealed class AiAnalyzeError(message: String) : Exception(message) {

    /**
     * The caller had a BLANK `userId` at call time (new-user / web-before-sign-in timing). The
     * request is not sent — this is terminal, never retried. [MARKER] is a stable, non-user-facing
     * data-layer string; presentation swaps it for a localized "signing you in…" message.
     */
    data object UserNotReady : AiAnalyzeError(MARKER)

    /**
     * A non-2xx HTTP response from the AI Cloud Function. [statusCode] is what lets the classifier
     * split `server_5xx` (retryable, transient) from `auth_403` (App Check / review-bot) — a
     * distinction that is impossible from a Ktor deserialization exception alone. [message] carries
     * the server's own error string when the body was JSON, so it stays user-presentable.
     */
    class Http(val statusCode: Int, serverMessage: String?) :
        AiAnalyzeError(serverMessage ?: "http_$statusCode")

    /** Connect / socket / overall request timeout surfaced after retries were exhausted. */
    data object Timeout : AiAnalyzeError("timeout")

    /** DNS / connection / IO transport failure (e.g. "Unable to resolve host") after retries. */
    data object Network : AiAnalyzeError("network")

    companion object {
        /** Stable marker used both as [UserNotReady]'s message and for message-fallback classification. */
        const val MARKER = "user_not_ready"
    }
}
