package com.antonchuraev.homesearchchecklist.notification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Off-device unit test for [shouldUseFullScreenIntent] — the pure gate that decides whether a
 * per-item reminder escalates to a full-screen (alarm-style) notification. It must be true only
 * when the item opted in AND the OS permits full-screen intents.
 */
class FullScreenIntentGateTest {

    @Test
    fun shouldUseFullScreenIntent_flagAndPermitted_returnsTrue() {
        assertTrue(shouldUseFullScreenIntent(fullScreenFlag = true, permitted = true))
    }

    @Test
    fun shouldUseFullScreenIntent_flagOptedInButNotPermitted_returnsFalse() {
        assertFalse(shouldUseFullScreenIntent(fullScreenFlag = true, permitted = false))
    }

    @Test
    fun shouldUseFullScreenIntent_permittedButNotOptedIn_returnsFalse() {
        assertFalse(shouldUseFullScreenIntent(fullScreenFlag = false, permitted = true))
    }

    @Test
    fun shouldUseFullScreenIntent_neitherOptedInNorPermitted_returnsFalse() {
        assertFalse(shouldUseFullScreenIntent(fullScreenFlag = false, permitted = false))
    }
}
