package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

/**
 * Coarse machine reason attached to the `ai_analyze_failed` analytics event so failures are
 * groupable in Amplitude WITHOUT regex over free text. [wireValue] is the string sent on the wire;
 * change the enum identifier freely, change the string only with intent (it is historical data).
 *
 * [classify] is a PURE function (no coroutines, no platform APIs → unit-testable on any target).
 * It prefers the typed [AiAnalyzeError] variant (reliable) and falls back to message matching for
 * plain exceptions the repository builds from a 200-with-`success:false` server body (e.g.
 * "Not enough credits").
 */
enum class AiFailureReason(val wireValue: String) {
    CREDIT_GATE("credit_gate"),
    DAILY_LIMIT("daily_limit"),
    INPUT_TOO_LONG("input_too_long"),
    NETWORK("network"),
    TIMEOUT("timeout"),
    SERVER_5XX("server_5xx"),
    AUTH_403("auth_403"),
    USER_NOT_READY("user_not_ready"),
    UNKNOWN("unknown");

    companion object {

        fun classify(error: Throwable?): AiFailureReason = when (error) {
            is AiAnalyzeError.UserNotReady -> USER_NOT_READY
            is AiAnalyzeError.Timeout -> TIMEOUT
            is AiAnalyzeError.Network -> NETWORK
            is AiAnalyzeError.Http -> classifyHttp(error)
            else -> classifyByMessage(error?.message)
        }

        private fun classifyHttp(error: AiAnalyzeError.Http): AiFailureReason = when {
            error.statusCode == 403 -> AUTH_403
            error.statusCode in 500..599 -> SERVER_5XX
            error.statusCode == 402 -> CREDIT_GATE
            error.statusCode == 429 -> DAILY_LIMIT
            // Other 4xx (e.g. 400 validation): fall back to the server message for the specific cause.
            else -> classifyByMessage(error.message).takeIf { it != UNKNOWN } ?: UNKNOWN
        }

        private fun classifyByMessage(message: String?): AiFailureReason {
            val msg = message?.lowercase() ?: return UNKNOWN
            return when {
                AiAnalyzeError.MARKER in msg || "user_id is required" in msg || "user id is required" in msg -> USER_NOT_READY
                "not enough credit" in msg || "insufficient credit" in msg -> CREDIT_GATE
                "daily limit" in msg || "limit reached" in msg || "rate limit" in msg -> DAILY_LIMIT
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
