package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.push.PushActivityPolicy.FOREGROUND_MARK_INTERVAL_MS
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `lastActiveAt` must mean "the user opened the app". Before this policy every process wake —
 * including the FCM delivery of the promo itself — refreshed it, so the server's
 * "pushes without a reaction" stop and dormancy window never fired.
 */
class PushActivityPolicyTest {

    @Test
    fun firstForegroundInProcess_marks() {
        assertTrue(PushActivityPolicy.shouldMarkForeground(lastMarkedAtMs = null, nowMs = NOW))
    }

    @Test
    fun resumeInsideInterval_doesNotMark() {
        assertFalse(
            PushActivityPolicy.shouldMarkForeground(lastMarkedAtMs = NOW, nowMs = NOW + FOREGROUND_MARK_INTERVAL_MS - 1),
        )
    }

    @Test
    fun resumeAtInterval_marks() {
        assertTrue(
            PushActivityPolicy.shouldMarkForeground(lastMarkedAtMs = NOW, nowMs = NOW + FOREGROUND_MARK_INTERVAL_MS),
        )
    }

    @Test
    fun clockMovedBackwards_marks() {
        assertTrue(PushActivityPolicy.shouldMarkForeground(lastMarkedAtMs = NOW, nowMs = NOW - 1))
    }

    @Test
    fun backgroundStart_withConfirmedPair_sendsNothing() {
        val marker = PushActivityPolicy.tokenSyncMarker(USER, TOKEN)
        assertFalse(PushActivityPolicy.shouldSyncTokenInBackground(marker, USER, TOKEN))
    }

    @Test
    fun backgroundStart_withoutMarker_registers() {
        assertTrue(PushActivityPolicy.shouldSyncTokenInBackground(storedMarker = "", userId = USER, token = TOKEN))
    }

    @Test
    fun backgroundStart_afterTokenRotation_registers() {
        val marker = PushActivityPolicy.tokenSyncMarker(USER, TOKEN)
        assertTrue(PushActivityPolicy.shouldSyncTokenInBackground(marker, USER, "rotated-token"))
    }

    @Test
    fun backgroundStart_afterUserIdChange_registers() {
        val marker = PushActivityPolicy.tokenSyncMarker(USER, TOKEN)
        assertTrue(PushActivityPolicy.shouldSyncTokenInBackground(marker, "converged-user", TOKEN))
    }

    @Test
    fun marker_doesNotContainTheToken() {
        val marker = PushActivityPolicy.tokenSyncMarker(USER, TOKEN)
        assertFalse(marker.contains(TOKEN))
        assertNotEquals(PushActivityPolicy.tokenSyncMarker(USER, TOKEN), PushActivityPolicy.tokenSyncMarker(USER, "other"))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val USER = "user-0123456789"
        const val TOKEN = "fcm-token-abcdef"
    }
}
