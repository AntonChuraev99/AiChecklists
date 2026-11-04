package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceCallFormatTest {

    @Test
    fun callClockPadsSecondsAndSwitchesToHoursAtOneHour() {
        assertEquals("0:00", formatCallClock(0))
        assertEquals("0:07", formatCallClock(7))
        assertEquals("2:14", formatCallClock(134))
        assertEquals("59:59", formatCallClock(3599))
        assertEquals("1:00:05", formatCallClock(3605))
        assertEquals("0:00", formatCallClock(-3))
    }

    @Test
    fun minutesLeftRoundsUpAndNeverShowsZero() {
        assertEquals(1, minutesLeftRoundedUp(1))
        assertEquals(1, minutesLeftRoundedUp(59))
        assertEquals(1, minutesLeftRoundedUp(60))
        assertEquals(2, minutesLeftRoundedUp(61))
        assertEquals(1, minutesLeftRoundedUp(0))
    }

    @Test
    fun replySplitsAtSpokenCharsAndClampsOutOfRange() {
        val reply = VoiceReplyUi(text = "Hello there", spokenChars = 5)
        assertEquals("Hello", reply.spokenPart)
        assertEquals(" there", reply.pendingPart)

        assertEquals("Hello there", VoiceReplyUi("Hello there", spokenChars = 99).spokenPart)
        assertEquals("", VoiceReplyUi("Hello there", spokenChars = 99).pendingPart)
        assertEquals("", VoiceReplyUi("Hello there", spokenChars = -1).spokenPart)
    }

    @Test
    fun timerWarnsAtOneMinuteLeftOnly() {
        assertTrue(CallTimerUi(remainingSec = 60).warning)
        assertFalse(CallTimerUi(remainingSec = 61).warning)
        assertFalse(CallTimerUi(remainingSec = null).warning)
    }
}
