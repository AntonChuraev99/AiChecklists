package com.antonchuraev.homesearchchecklist.core.voice.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The little logic of the voice contract: wire codes, the voice of a segment, error codes, ids. */
class VoiceContractLogicTest {

    @Test
    fun wireCodesRoundTripAndAnUnknownOneIsNull() {
        assertEquals(CallEndReason.SystemInterruption, CallEndReason.ofWire("interrupted_by_system"))
        assertEquals(VoicePlan.Premium, VoicePlan.ofWire("premium"))
        assertEquals(SpokenSource.Fixed, SpokenSource.ofWire("fixed"))
        assertEquals(BargeInMethod.Voice, BargeInMethod.ofWire("voice"))
        assertNull(SpokenSource.ofWire("fallback"))
        assertNull(CallEndReason.ofWire(null))
    }

    @Test
    fun aSegmentIsSpokenInItsOwnLanguageBlankFallsBackToTheCallLocale() {
        val call = SpeechLocale("en-GB")
        assertEquals(call, SpeechLocale.forLanguage("en", call), "same language keeps the call's region")
        assertEquals(call, SpeechLocale.forLanguage("en-US", call), "same language with region keeps the call's")
        assertEquals(call, SpeechLocale.forLanguage(null, call))
        assertEquals(call, SpeechLocale.forLanguage("  ", call))
        assertEquals(SpeechLocale("ru-RU"), SpeechLocale.forLanguage("ru", call))
        assertEquals(SpeechLocale("de-DE"), SpeechLocale.forLanguage("de_AT", call))
        assertEquals(SpeechLocale("ja"), SpeechLocale.forLanguage("JA", call), "unknown region -> bare code")
    }

    @Test
    fun languageOfALocaleTag() {
        assertEquals("en", SpeechLocale("en-US").language)
        assertEquals("pt", SpeechLocale("PT_br").language)
        assertEquals("hi", SpeechLocale("hi").language)
    }

    @Test
    fun onlyTransportFailuresAreRetried() {
        assertTrue(VoiceCallError.Unreachable.isRetryable)
        assertTrue(VoiceCallError.Unavailable.isRetryable)
        assertFalse(VoiceCallError.TurnInProgress.isRetryable)
        assertFalse(VoiceCallError.NotEntitled().isRetryable)
        assertFalse(VoiceCallError.OutOfCredits.isRetryable)
        assertFalse(VoiceCallError.RateLimited.isRetryable)
        assertEquals("bad_text", VoiceCallError.ClientBug("bad_text").code)
        assertEquals("quota_store", VoiceCallError.Other("quota_store").code)
    }

    @Test
    fun randomIdsAreUuidsAndDiffer() {
        val a = ClientTurnId.random()
        val b = ClientTurnId.random()
        assertNotEquals(a, b)
        assertEquals(36, a.value.length)
        assertEquals(36, VoiceSessionId.random().value.length)
    }
}
