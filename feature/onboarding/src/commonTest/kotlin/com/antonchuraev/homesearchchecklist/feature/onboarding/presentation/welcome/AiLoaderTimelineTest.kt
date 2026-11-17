package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import kotlin.test.Test
import kotlin.test.assertEquals

/** The AI loader's UI timeline (DESIGN_SPEC 2026-11-17 §L2.2, §L2.5). */
class AiLoaderTimelineTest {

    @Test
    fun activeStep_ticksAt1200_2600_4200() {
        assertEquals(0, AiLoaderTimeline.activeStep(0))
        assertEquals(0, AiLoaderTimeline.activeStep(1_199))
        assertEquals(1, AiLoaderTimeline.activeStep(1_200))
        assertEquals(1, AiLoaderTimeline.activeStep(2_599))
        assertEquals(2, AiLoaderTimeline.activeStep(2_600))
        assertEquals(3, AiLoaderTimeline.activeStep(4_200))
    }

    @Test
    fun activeStep_lastStepNeverCompletesOnTheClock() {
        assertEquals(AiLoaderTimeline.STEP_COUNT - 1, AiLoaderTimeline.activeStep(10 * 60_000))
    }

    @Test
    fun caption_needsTheSlowFlag_thenFollowsAt16And26s_withoutLooping() {
        assertEquals(0, AiLoaderTimeline.caption(30_000, slow = false))
        assertEquals(1, AiLoaderTimeline.caption(8_000, slow = true))
        assertEquals(1, AiLoaderTimeline.caption(15_999, slow = true))
        assertEquals(2, AiLoaderTimeline.caption(16_000, slow = true))
        assertEquals(3, AiLoaderTimeline.caption(26_000, slow = true))
        assertEquals(3, AiLoaderTimeline.caption(10 * 60_000, slow = true))
    }

    @Test
    fun milestones_areAscending_andCoverEveryVisibleChange() {
        assertEquals(listOf(1_200L, 2_600L, 4_200L, 16_000L, 26_000L), AiLoaderTimeline.milestones)
    }
}
