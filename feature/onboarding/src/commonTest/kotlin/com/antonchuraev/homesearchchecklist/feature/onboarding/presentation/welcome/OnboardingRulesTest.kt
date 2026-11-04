package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure rules of the onboarding (FEATURE_SPEC §11.1, first paragraph). */
class OnboardingRulesTest {

    // ── plainListName ──────────────────────────────────────────────────────────

    @Test
    fun plainListName_shortText_untouchedButTrimmed() {
        assertEquals("Pack for a beach trip", plainListName("  Pack for a beach trip \n"))
    }

    @Test
    fun plainListName_multiLineText_collapsesWhitespace() {
        assertEquals("Pack bags and book taxi", plainListName("Pack bags\nand   book\ttaxi"))
    }

    @Test
    fun plainListName_exactlyAtLimit_untouched() {
        val sixty = "a".repeat(PLAIN_LIST_NAME_MAX)
        assertEquals(sixty, plainListName(sixty))
    }

    @Test
    fun plainListName_longText_cutOnWordBoundaryWithEllipsis_withinLimit() {
        val text = "Plan the move to the new flat: book the van for Saturday morning and pack the kitchen"

        val name = plainListName(text)

        assertTrue(name.length <= PLAIN_LIST_NAME_MAX, "got ${name.length}: $name")
        assertTrue(name.endsWith("…"))
        val body = name.removeSuffix("…")
        assertFalse(body.endsWith(" "), "no trailing space before the ellipsis: '$name'")
        assertTrue(text.startsWith(body), "the kept part is a prefix of the text")
        // The cut lands on a boundary: the next character of the original text is a space.
        assertEquals(' ', text[body.length])
    }

    @Test
    fun plainListName_cutExactlyBeforeASpace_keepsTheFullWord() {
        // 59 chars of words followed by a space: the word at the cut is whole, keep it.
        val head = "word ".repeat(11) + "abcd" // 59 chars
        val name = plainListName("$head tail tail")

        assertEquals("$head…", name)
    }

    @Test
    fun plainListName_singleHugeWord_cutMidWord() {
        val name = plainListName("x".repeat(100))

        assertEquals(PLAIN_LIST_NAME_MAX, name.length)
        assertTrue(name.endsWith("…"))
    }

    // ── resolveAiError ─────────────────────────────────────────────────────────

    @Test
    fun resolveAiError_webSignedOutCreditGate_isSignInRequired() {
        assertEquals(OnboardingAiError.SignInRequired, resolveAiError(AiFailureReason.CREDIT_GATE, isWeb = true, isGoogleLinked = false))
        assertEquals(OnboardingAiError.SignInRequired, resolveAiError(AiFailureReason.USER_NOT_READY, isWeb = true, isGoogleLinked = false))
    }

    @Test
    fun resolveAiError_androidCreditGate_isUnavailable() {
        assertEquals(OnboardingAiError.Unavailable, resolveAiError(AiFailureReason.CREDIT_GATE, isWeb = false, isGoogleLinked = false))
    }

    @Test
    fun resolveAiError_webSignedInCreditGate_isUnavailable() {
        assertEquals(OnboardingAiError.Unavailable, resolveAiError(AiFailureReason.CREDIT_GATE, isWeb = true, isGoogleLinked = true))
    }

    @Test
    fun resolveAiError_webSignedOutNetwork_isUnavailable() {
        assertEquals(OnboardingAiError.Unavailable, resolveAiError(AiFailureReason.NETWORK, isWeb = true, isGoogleLinked = false))
    }

    // ── OnboardingSeed.OwnFile.wire ────────────────────────────────────────────

    @Test
    fun ownFileSeed_wire_forEveryMaterialKind() {
        assertEquals("own_file_photo", OnboardingSeed.OwnFile(AnalyzeInputKind.PHOTO).wire)
        assertEquals("own_file_pdf", OnboardingSeed.OwnFile(AnalyzeInputKind.PDF).wire)
        assertEquals("own_file_web_link", OnboardingSeed.OwnFile(AnalyzeInputKind.WEB_LINK).wire)
        assertEquals("own_file_voice", OnboardingSeed.OwnFile(AnalyzeInputKind.VOICE).wire)
    }
}
