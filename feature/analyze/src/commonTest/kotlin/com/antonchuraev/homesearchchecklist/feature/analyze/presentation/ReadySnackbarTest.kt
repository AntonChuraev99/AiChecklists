package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobFailure
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ReadyAnalysis
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DESIGN_SPEC G1–G3: snackbar folding, name truncation, failure copy and buttons. */
class ReadySnackbarTest {

    private val failed = ReadyAnalysis.Failed("f", AnalysisJobFailure(AiFailureReason.NETWORK, retryableWithSameInput = true))

    @Test
    fun twoOrMoreNewChecklists_foldIntoOneSummary_fillAndFailuresNever() {
        val pending = listOf(
            failed,
            ReadyAnalysis.ChecklistCreated("a", "A"),
            ReadyAnalysis.FillReady("x", "Trip"),
            ReadyAnalysis.ChecklistCreated("b", "B"),
        )

        assertEquals(ReadySnackbar.Summary(listOf("a", "b")), nextReadySnackbar(pending))
        assertEquals(ReadySnackbar.Single(failed), nextReadySnackbar(listOf(failed, ReadyAnalysis.ChecklistCreated("a", "A"))))
        assertNull(nextReadySnackbar(emptyList()))
    }

    @Test
    fun names_areTruncatedTo24Chars() {
        assertEquals("Groceries", truncateForSnackbar("Groceries"))
        assertEquals("A".repeat(24) + "…", truncateForSnackbar("A".repeat(30)))
    }

    @Test
    fun actionGoesToANewLine_forLongCopyOrLargeText() {
        assertFalse(snackbarActionOnNewLine("“Trip” is ready", 1.0f))
        assertTrue(snackbarActionOnNewLine("x".repeat(41), 1.0f))
        assertTrue(snackbarActionOnNewLine("short", 1.3f))
    }

    @Test
    fun failedCta_perReason() {
        fun cta(reason: AiFailureReason) = AnalysisJobFailure(reason, retryableWithSameInput = reason.retryableWithSameInput).failedCta
        assertEquals(FailedCta.Retry, cta(AiFailureReason.NETWORK))
        assertEquals(FailedCta.Retry, cta(AiFailureReason.SERVER_5XX))
        assertEquals(FailedCta.Retry, cta(AiFailureReason.AUTH_403))
        assertEquals(FailedCta.ChangeInput, cta(AiFailureReason.NOTHING_FOUND))
        assertEquals(FailedCta.ChangeInput, cta(AiFailureReason.INPUT_TOO_LARGE))
        assertEquals(FailedCta.ChangeInput, cta(AiFailureReason.INPUT_UNREADABLE))
        assertEquals(FailedCta.ChangeInput, cta(AiFailureReason.INTERRUPTED))
        assertEquals(FailedCta.Dismiss, cta(AiFailureReason.CREDIT_GATE))
        assertEquals(FailedCta.Dismiss, cta(AiFailureReason.DAILY_LIMIT))
    }

    @Test
    fun failureMessage_usesOwnCopy_butKeepsTheServerCreditMessage() = runTest {
        val resolve: suspend (org.jetbrains.compose.resources.StringResource) -> String = { it.key }
        assertEquals(
            "analyze_error_nothing_found",
            AnalysisJobFailure(AiFailureReason.NOTHING_FOUND, "raw", false).userFacingMessage(resolve),
        )
        assertEquals(
            "analyze_error_server",
            AnalysisJobFailure(AiFailureReason.SERVER_5XX, "http_503", true).userFacingMessage(resolve),
        )
        assertEquals(
            "Not enough credits",
            AnalysisJobFailure(AiFailureReason.CREDIT_GATE, "Not enough credits", false).userFacingMessage(resolve),
        )
        assertEquals(
            "analyze_error_analysis_failed",
            AnalysisJobFailure(AiFailureReason.UNKNOWN, "http_418", true).userFacingMessage(resolve),
        )
    }
}
