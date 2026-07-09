package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure classifier coverage for the `ai_analyze_failed` reason taxonomy. No coroutines / no mocks —
 * [AiFailureReason.classify] maps a [Throwable] to a coarse reason, preferring the typed
 * [AiAnalyzeError] variant and falling back to server-message matching for plain exceptions the
 * repository builds from a 200-with-`success:false` body.
 */
class AiFailureReasonTest {

    @Test
    fun classify_userNotReadyError_isUserNotReady() {
        assertEquals(AiFailureReason.USER_NOT_READY, AiFailureReason.classify(AiAnalyzeError.UserNotReady))
    }

    @Test
    fun classify_timeoutError_isTimeout() {
        assertEquals(AiFailureReason.TIMEOUT, AiFailureReason.classify(AiAnalyzeError.Timeout))
    }

    @Test
    fun classify_networkError_isNetwork() {
        assertEquals(AiFailureReason.NETWORK, AiFailureReason.classify(AiAnalyzeError.Network))
    }

    @Test
    fun classify_http403_isAuth403() {
        assertEquals(AiFailureReason.AUTH_403, AiFailureReason.classify(AiAnalyzeError.Http(403, "Missing App Check token")))
    }

    @Test
    fun classify_http503_isServer5xx() {
        assertEquals(AiFailureReason.SERVER_5XX, AiFailureReason.classify(AiAnalyzeError.Http(503, null)))
    }

    @Test
    fun classify_http402_isCreditGate() {
        assertEquals(AiFailureReason.CREDIT_GATE, AiFailureReason.classify(AiAnalyzeError.Http(402, "Not enough credits")))
    }

    @Test
    fun classify_http429_isDailyLimit() {
        assertEquals(AiFailureReason.DAILY_LIMIT, AiFailureReason.classify(AiAnalyzeError.Http(429, "Daily limit reached")))
    }

    @Test
    fun classify_http400InputTooLong_isInputTooLong() {
        assertEquals(
            AiFailureReason.INPUT_TOO_LONG,
            AiFailureReason.classify(AiAnalyzeError.Http(400, "Input exceeds maximum length of 10000")),
        )
    }

    @Test
    fun classify_serverSuccessFalseCreditMessage_isCreditGate() {
        // 200-with-success:false path: repository wraps the server error in a plain Exception.
        assertEquals(AiFailureReason.CREDIT_GATE, AiFailureReason.classify(Exception("Not enough credits remaining")))
    }

    @Test
    fun classify_dnsFailureMessage_isNetwork() {
        assertEquals(AiFailureReason.NETWORK, AiFailureReason.classify(Exception("Unable to resolve host \"...\"")))
    }

    @Test
    fun classify_serverUserIdRequired_isUserNotReady() {
        assertEquals(AiFailureReason.USER_NOT_READY, AiFailureReason.classify(Exception("user_id is required")))
    }

    @Test
    fun classify_unknownMessage_isUnknown() {
        assertEquals(AiFailureReason.UNKNOWN, AiFailureReason.classify(Exception("something totally unexpected")))
    }

    @Test
    fun classify_null_isUnknown() {
        assertEquals(AiFailureReason.UNKNOWN, AiFailureReason.classify(null))
    }
}
