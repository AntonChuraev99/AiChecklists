package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AudioRecorderErrorCodes
import com.antonchuraev.homesearchchecklist.feature.aichat.api.repository.TranscriptionOutcome
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiFailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure rules of the onboarding (FEATURE_SPEC §11.1, first paragraph). */
class OnboardingRulesTest {

    // ── voice (FEATURE_SPEC 2026-11-17 §4) ──────────────────────────────────────

    @Test
    fun voiceStopReasonFor_mapsEveryRecorderCode() {
        assertEquals(OnboardingVoiceStop.PermissionDenied, voiceStopReasonFor(AudioRecorderErrorCodes.PERMISSION_DENIED))
        assertEquals(
            OnboardingVoiceStop.PermissionDenied,
            voiceStopReasonFor(AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY),
        )
        assertEquals(
            OnboardingVoiceStop.PermissionJustGranted,
            voiceStopReasonFor(AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN),
        )
        assertEquals(OnboardingVoiceStop.TooShort, voiceStopReasonFor(AudioRecorderErrorCodes.RECORDING_TOO_SHORT))
        assertEquals(OnboardingVoiceStop.RecorderError, voiceStopReasonFor("MediaRecorder: start failed -38"))
    }

    @Test
    fun voiceAvailable_onlyWebWithoutGoogleLinkHidesTheMic() {
        assertFalse(voiceAvailable(isWeb = true, isGoogleLinked = false))
        assertTrue(voiceAvailable(isWeb = true, isGoogleLinked = true))
        assertTrue(voiceAvailable(isWeb = false, isGoogleLinked = false))
        assertTrue(voiceAvailable(isWeb = false, isGoogleLinked = true))
    }

    @Test
    fun appendTranscript_emptyField_takesTheTranscriptTrimmed() {
        assertEquals("buy milk", appendTranscript("", "  buy milk \n"))
        assertEquals("buy milk", appendTranscript("   ", "buy milk"))
    }

    @Test
    fun appendTranscript_typedField_appendsWithOneSpace() {
        assertEquals("Groceries for Sunday", appendTranscript("Groceries  ", " for Sunday"))
    }

    @Test
    fun transcriptionFailureSnackbar_successHasNone_everyFailureHasOne() {
        assertEquals(null, transcriptionFailureSnackbar(TranscriptionOutcome.Success("x")))
        assertEquals(OnboardingSnackbar.TRANSCRIBE_EMPTY, transcriptionFailureSnackbar(TranscriptionOutcome.EmptyTranscript))
        assertEquals(OnboardingSnackbar.NO_CREDITS, transcriptionFailureSnackbar(TranscriptionOutcome.InsufficientCredits))
        listOf(
            TranscriptionOutcome.FileMissing,
            TranscriptionOutcome.NetworkError,
            TranscriptionOutcome.NotRegistered,
            TranscriptionOutcome.ServiceError,
        ).forEach { assertEquals(OnboardingSnackbar.TRANSCRIBE_ERROR, transcriptionFailureSnackbar(it), "$it") }
    }

    @Test
    fun steps_branchesShareStepIndex2_countStays2() {
        assertEquals(listOf(1, 2, 2, 2), OnboardingStep.entries.map { it.index })
        assertEquals(2, OnboardingStep.count)
        assertEquals(listOf("showcase", "write", "attach", "talk"), OnboardingStep.entries.map { it.wire })
        assertEquals(listOf(OnboardingStep.Write, OnboardingStep.Talk), OnboardingStep.entries.filter { it.hasComposer })
        assertEquals(OnboardingMode.entries.map { it.step }, OnboardingStep.entries.drop(1), "one tile per branch")
    }

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

    @Test
    fun resolveAiError_nothingFound_isNothingFound_onEveryPlatform() {
        // The AI was reached and found nothing: "can't reach the AI" would send the user away from a
        // working feature. Holds on web signed out too — it is not a credits problem.
        assertEquals(OnboardingAiError.NothingFound, resolveAiError(AiFailureReason.NOTHING_FOUND, isWeb = false, isGoogleLinked = false))
        assertEquals(OnboardingAiError.NothingFound, resolveAiError(AiFailureReason.NOTHING_FOUND, isWeb = true, isGoogleLinked = false))
        assertEquals("nothing_found", OnboardingAiError.NothingFound.wire)
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
