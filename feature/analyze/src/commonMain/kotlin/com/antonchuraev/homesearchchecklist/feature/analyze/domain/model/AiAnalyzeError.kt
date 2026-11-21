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
    class Http(
        val statusCode: Int,
        serverMessage: String?,
        /**
         * Machine-readable `error_code` of the server envelope (`input_too_large`, `nothing_found`,
         * `invalid_input`, `unsupported_input_type`). Null on servers that predate it — the
         * classifier then falls back to the status code and the message.
         */
        val errorCode: String? = null,
    ) : AiAnalyzeError(serverMessage ?: "http_$statusCode")

    /**
     * A 2xx `{"success": false, "error": "..."}` body: the SERVER refused and said why (credits, limit,
     * too long). Typed so the job keeps [message] as user-presentable text — unlike a local exception,
     * whose text must never reach the user.
     */
    class ServerRefusal(serverMessage: String) : AiAnalyzeError(serverMessage)

    /** Connect / socket / overall request timeout surfaced after retries were exhausted. */
    data object Timeout : AiAnalyzeError("timeout")

    /** DNS / connection / IO transport failure (e.g. "Unable to resolve host") after retries. */
    data object Network : AiAnalyzeError("network")

    /**
     * The file material could not be read on the device (cache cleared, permission revoked). Raised
     * BEFORE any request: nothing is sent and nothing is charged. Replaces the old behaviour of
     * sending the text "Failed to read image: <path>" to the model and charging for it.
     */
    data object InputUnreadable : AiAnalyzeError("input_unreadable")

    /**
     * The server answered successfully but there is nothing to put in a checklist (zero items).
     * The new server says so itself (`error_code = nothing_found`, credits refunded); an old server
     * returns an empty success, which the repository maps here so an empty checklist is never created.
     */
    data object NothingFound : AiAnalyzeError("nothing_found")

    /** A web file job lost its bytes to a page reload before it was sent. */
    data object Interrupted : AiAnalyzeError("interrupted")

    /** The user cancelled a job that was still waiting for a connection. */
    data object Cancelled : AiAnalyzeError("cancelled")

    companion object {
        /** Stable marker used both as [UserNotReady]'s message and for message-fallback classification. */
        const val MARKER = "user_not_ready"
    }
}
