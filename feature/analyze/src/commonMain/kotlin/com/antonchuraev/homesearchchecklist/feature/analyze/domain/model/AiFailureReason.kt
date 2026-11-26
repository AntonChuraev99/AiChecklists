package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Coarse machine reason attached to the `ai_analyze_failed` analytics event so failures are
 * groupable in Amplitude WITHOUT regex over free text. [wireValue] is the string sent on the wire;
 * change the enum identifier freely, change the string only with intent (it is historical data).
 *
 * [classify] is a PURE function (no coroutines, no platform APIs → unit-testable on any target).
 * It prefers the typed [AiAnalyzeError] variant (reliable) and falls back to message matching for
 * plain exceptions the repository builds from a 200-with-`success:false` server body (e.g.
 * "Not enough credits").
 *
 * [retryableWithSameInput]: whether sending the SAME input again can succeed. False for failures
 * about the input itself (a retry would fail again, or charge-and-fail) and for the credit gate.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class AiFailureReason(val wireValue: String, val retryableWithSameInput: Boolean) {
    CREDIT_GATE("credit_gate", retryableWithSameInput = false),
    INPUT_TOO_LONG("input_too_long", retryableWithSameInput = false),
    NETWORK("network", retryableWithSameInput = true),
    TIMEOUT("timeout", retryableWithSameInput = true),
    SERVER_5XX("server_5xx", retryableWithSameInput = true),
    AUTH_403("auth_403", retryableWithSameInput = true),
    USER_NOT_READY("user_not_ready", retryableWithSameInput = true),

    /** `error_code = input_too_large` (server, 413) or a bare HTTP 413 from a platform body limit. */
    INPUT_TOO_LARGE("input_too_large", retryableWithSameInput = false),

    /** `error_code = nothing_found` (422, refunded) or an empty success from an older server. */
    NOTHING_FOUND("nothing_found", retryableWithSameInput = false),

    /** The file could not be read on the device; nothing was sent. */
    INPUT_UNREADABLE("input_unreadable", retryableWithSameInput = false),

    /** A web file job lost its bytes to a page reload. */
    INTERRUPTED("interrupted", retryableWithSameInput = false),

    /** The user cancelled a job that was waiting for a connection. */
    CANCELLED("cancelled", retryableWithSameInput = false),

    /** The checklist a fill job was for was deleted while the job waited. */
    TARGET_MISSING("target_missing", retryableWithSameInput = false),

    /** Server refused the input itself (`error_code = invalid_input` / `unsupported_input_type`). */
    INVALID_INPUT("invalid_input", retryableWithSameInput = false),

    /**
     * Anything unclassified. Also the decode target of the removed `DAILY_LIMIT` (2026-11-26: there
     * is no daily request limit any more, credits are the only gate): a job persisted in
     * `analysis_jobs_v1` by an older build must still decode, or the whole queue — active jobs
     * included — would be dropped on the first read.
     */
    @JsonNames("DAILY_LIMIT")
    UNKNOWN("unknown", retryableWithSameInput = true);

    /**
     * Failures a platform scheduler retries with the SAME job id before giving up: transport, and
     * USER_NOT_READY — a job enqueued before registration finished simply waits for the user id.
     */
    val isTransient: Boolean
        get() = this == NETWORK || this == TIMEOUT || this == SERVER_5XX || this == USER_NOT_READY

    companion object {

        fun classify(error: Throwable?): AiFailureReason = when (error) {
            is AiAnalyzeError.UserNotReady -> USER_NOT_READY
            is AiAnalyzeError.Timeout -> TIMEOUT
            is AiAnalyzeError.Network -> NETWORK
            is AiAnalyzeError.InputUnreadable -> INPUT_UNREADABLE
            is AiAnalyzeError.NothingFound -> NOTHING_FOUND
            is AiAnalyzeError.Interrupted -> INTERRUPTED
            is AiAnalyzeError.Cancelled -> CANCELLED
            is AiAnalyzeError.ServerRefusal -> classifyByMessage(error.message)
            is AiAnalyzeError.Http -> classifyHttp(error)
            else -> classifyByMessage(error?.message)
        }

        private fun classifyHttp(error: AiAnalyzeError.Http): AiFailureReason {
            // The server's machine code wins over the status: it is the reason the server stated.
            when (error.errorCode) {
                "input_too_large" -> return INPUT_TOO_LARGE
                "nothing_found" -> return NOTHING_FOUND
                "invalid_input", "unsupported_input_type" -> return INVALID_INPUT
            }
            return when {
                // 413 without a code = a platform body limit (Cloud Run / proxy) hit before our code ran.
                error.statusCode == 413 -> INPUT_TOO_LARGE
                error.statusCode == 403 -> AUTH_403
                error.statusCode in 500..599 -> SERVER_5XX
                error.statusCode == 402 -> CREDIT_GATE
                // Other 4xx (e.g. 400 validation): fall back to the server message for the specific cause.
                else -> classifyByMessage(error.message).takeIf { it != UNKNOWN } ?: UNKNOWN
            }
        }

        private fun classifyByMessage(message: String?): AiFailureReason {
            val msg = message?.lowercase() ?: return UNKNOWN
            return when {
                AiAnalyzeError.MARKER in msg || "user_id is required" in msg || "user id is required" in msg -> USER_NOT_READY
                "not enough credit" in msg || "insufficient credit" in msg -> CREDIT_GATE
                "maximum length" in msg || "too long" in msg || "exceeds maximum" in msg -> INPUT_TOO_LONG
                "unable to resolve host" in msg || "failed to connect" in msg ||
                    "connection" in msg || "unexpected end of stream" in msg ||
                    "network is unreachable" in msg -> NETWORK
                "timeout" in msg || "timed out" in msg -> TIMEOUT
                "app check" in msg || "unauthorized" in msg || "forbidden" in msg ||
                    "notransformation" in msg -> AUTH_403
                else -> UNKNOWN
            }
        }
    }
}
