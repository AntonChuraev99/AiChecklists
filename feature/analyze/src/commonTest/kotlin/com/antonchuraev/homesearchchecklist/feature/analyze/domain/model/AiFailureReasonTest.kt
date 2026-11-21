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

    // ── Server error_code (FEATURE_SPEC analyze-reliability-background-2026-11-20 §6.8) ─────────

    @Test
    fun classify_errorCodeInputTooLarge_isInputTooLarge() {
        assertEquals(
            AiFailureReason.INPUT_TOO_LARGE,
            AiFailureReason.classify(AiAnalyzeError.Http(413, "File is too large. The limit is 20 MB.", "input_too_large")),
        )
    }

    @Test
    fun classify_bareHttp413_isInputTooLarge() {
        // A platform body limit answers 413 before our code runs: no JSON, no error_code.
        assertEquals(AiFailureReason.INPUT_TOO_LARGE, AiFailureReason.classify(AiAnalyzeError.Http(413, null)))
    }

    @Test
    fun classify_errorCodeNothingFound_isNothingFound() {
        assertEquals(
            AiFailureReason.NOTHING_FOUND,
            AiFailureReason.classify(AiAnalyzeError.Http(422, "Nothing to turn into a checklist", "nothing_found")),
        )
    }

    @Test
    fun classify_localTypedErrors() {
        assertEquals(AiFailureReason.NOTHING_FOUND, AiFailureReason.classify(AiAnalyzeError.NothingFound))
        assertEquals(AiFailureReason.INPUT_UNREADABLE, AiFailureReason.classify(AiAnalyzeError.InputUnreadable))
        assertEquals(AiFailureReason.INTERRUPTED, AiFailureReason.classify(AiAnalyzeError.Interrupted))
        assertEquals(AiFailureReason.CANCELLED, AiFailureReason.classify(AiAnalyzeError.Cancelled))
    }

    @Test
    fun inputFailures_areNotRetryableWithTheSameInput_transportOnesAre() {
        listOf(
            AiFailureReason.INPUT_TOO_LARGE, AiFailureReason.NOTHING_FOUND, AiFailureReason.INPUT_UNREADABLE,
            AiFailureReason.INTERRUPTED, AiFailureReason.CREDIT_GATE, AiFailureReason.DAILY_LIMIT,
        ).forEach { assertEquals(false, it.retryableWithSameInput, "$it") }
        listOf(AiFailureReason.NETWORK, AiFailureReason.TIMEOUT, AiFailureReason.SERVER_5XX, AiFailureReason.AUTH_403)
            .forEach { assertEquals(true, it.retryableWithSameInput, "$it") }
    }

    /** F6b: a refused input used to be UNKNOWN (retryable) → "Try again" re-sent what the server refused. */
    @Test
    fun classify_invalidInputCodes_areANonRetryableInputReason() {
        listOf("invalid_input", "unsupported_input_type").forEach { code ->
            val reason = AiFailureReason.classify(AiAnalyzeError.Http(400, "Bad input", code))
            assertEquals(AiFailureReason.INVALID_INPUT, reason, code)
            assertEquals(false, reason.retryableWithSameInput, code)
        }
    }

    @Test
    fun newWireValues_areTheSpecifiedStrings() {
        assertEquals("input_too_large", AiFailureReason.INPUT_TOO_LARGE.wireValue)
        assertEquals("nothing_found", AiFailureReason.NOTHING_FOUND.wireValue)
        assertEquals("input_unreadable", AiFailureReason.INPUT_UNREADABLE.wireValue)
        assertEquals("interrupted", AiFailureReason.INTERRUPTED.wireValue)
        assertEquals("cancelled", AiFailureReason.CANCELLED.wireValue)
    }
}
