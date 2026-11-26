package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** The nudge shake path (DESIGN_SPEC §9): 0 → −4 → 4 → −2 → 0 dp, resting at 0 on both ends. */
class OutOfCreditsBannerShakeTest {

    @Test
    fun pathHitsEveryKeyframe() {
        assertEquals(0f, shakeOffsetDp(0f))
        assertEquals(-4f, shakeOffsetDp(0.25f))
        assertEquals(4f, shakeOffsetDp(0.5f))
        assertEquals(-2f, shakeOffsetDp(0.75f))
        assertEquals(0f, shakeOffsetDp(1f))
    }

    @Test
    fun interpolatesBetweenKeyframes() {
        assertEquals(0f, shakeOffsetDp(0.375f), 1e-4f) // halfway −4 → 4
        assertEquals(-2f, shakeOffsetDp(0.125f), 1e-4f) // halfway 0 → −4
    }

    @Test
    fun outOfRangeProgressRestsAtZero() {
        assertEquals(0f, shakeOffsetDp(-1f))
        assertEquals(0f, shakeOffsetDp(2f))
    }
}
