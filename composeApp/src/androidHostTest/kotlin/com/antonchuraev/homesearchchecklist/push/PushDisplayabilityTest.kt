package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `push_received` fired regardless of whether the user could see the notification. These cases
 * pin what `notifications_enabled` on that event means: app toggle on AND (pre-O, or the target
 * channel exists with importance above NONE).
 */
class PushDisplayabilityTest {

    private val o = 26
    private val preO = 25
    private val importanceNone = 0
    private val importanceDefault = 3

    @Test
    fun appToggleOff_isNotShowable_evenWithLiveChannel() {
        assertFalse(PushDisplayability.isShowable(false, o, importanceDefault))
    }

    @Test
    fun mutedChannel_isNotShowable() {
        assertFalse(PushDisplayability.isShowable(true, o, importanceNone))
    }

    @Test
    fun missingChannelOnO_isNotShowable() {
        assertFalse(PushDisplayability.isShowable(true, o, null))
    }

    @Test
    fun liveChannel_isShowable() {
        assertTrue(PushDisplayability.isShowable(true, o, importanceDefault))
    }

    @Test
    fun preO_hasNoChannels_onlyAppToggleCounts() {
        assertTrue(PushDisplayability.isShowable(true, preO, null))
        assertFalse(PushDisplayability.isShowable(false, preO, null))
    }

    // ── A failed read is "unknown" (property omitted), never "disabled" ──

    private val base: Map<String, Any> = mapOf("push_type" to "comeback")

    @Test
    fun unknownDisplayability_omitsProperty() {
        assertEquals(base, PushDisplayability.withShowable(base, null))
    }

    @Test
    fun knownDisplayability_addsProperty() {
        assertEquals(false, PushDisplayability.withShowable(base, false)[AnalyticsParams.NOTIFICATIONS_ENABLED])
        assertEquals(true, PushDisplayability.withShowable(base, true)[AnalyticsParams.NOTIFICATIONS_ENABLED])
        assertEquals("comeback", PushDisplayability.withShowable(base, true)["push_type"])
    }
}
