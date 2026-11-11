package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceCallLayoutMathTest {

    // ── transcriptWindow ──

    @Test
    fun shortText_showsAllLines() {
        assertEquals(0 until 2, transcriptWindow(lineCount = 2, anchorLine = 0, maxLines = 4))
    }

    @Test
    fun tail_ofLongPartial_showsLastLines() {
        assertEquals(3 until 6, transcriptWindow(lineCount = 6, anchorLine = 5, maxLines = 3))
    }

    @Test
    fun spokenLine_staysVisible_withTheNextOne() {
        // Speaking line 6 of 10, window of 4: lines 4..7 (the spoken one + one ahead).
        assertEquals(4 until 8, transcriptWindow(lineCount = 10, anchorLine = 6, maxLines = 4))
    }

    @Test
    fun earlyInReply_windowIsFullFromTheTop() {
        assertEquals(0 until 4, transcriptWindow(lineCount = 10, anchorLine = 0, maxLines = 4))
    }

    @Test
    fun anchorPastTheEnd_isClamped() {
        assertEquals(6 until 10, transcriptWindow(lineCount = 10, anchorLine = 42, maxLines = 4))
    }

    @Test
    fun noLines_emptyWindow() {
        assertTrue(transcriptWindow(lineCount = 0, anchorLine = 0, maxLines = 4).isEmpty())
    }

    // ── orbDiameter / planStage ──

    @Test
    fun orb_isCappedByWidth_thenByMax() {
        // 288 wide stage: 288 / 1.3 = 221 (DESIGN_SPEC §1.1 replica of the 320×640 prototype).
        assertEquals(221, orbDiameter(stageHeightPx = 1000, widthPx = 288, gapPx = 8, haloExtent = 1.3f, maxPx = 240))
        assertEquals(240, orbDiameter(stageHeightPx = 1000, widthPx = 1000, gapPx = 8, haloExtent = 1.3f, maxPx = 240))
    }

    @Test
    fun negativeStage_givesZeroOrb() {
        assertEquals(0, orbDiameter(stageHeightPx = -50, widthPx = 288, gapPx = 8, haloExtent = 1.3f, maxPx = 240))
    }

    @Test
    fun roomy_keepsHint() {
        val plan = plan(height = 500, transcript = 0, withHint = 80, noHint = 40)
        assertEquals(StagePlan.Orb(221, showHint = true), plan)
    }

    @Test
    fun hintGoesFirst_whenOrbWouldDropBelowThreshold() {
        // With hint: (300 − 100 − 120 − 8) / 1.3 = 55 < 140; without: (300 − 100 − 40 − 8) / 1.3 = 116 ≥ 96.
        val plan = plan(height = 300, transcript = 100, withHint = 120, noHint = 40)
        assertEquals(StagePlan.Orb(116, showHint = false), plan)
    }

    @Test
    fun stageCollapsesToDot_belowOrbMin() {
        assertEquals(StagePlan.Dot, plan(height = 250, transcript = 120, withHint = null, noHint = 60))
    }

    @Test
    fun noHint_isNotMeasuredWhenTheHintFits() {
        var measured = false
        planStage(
            heightPx = 500, widthPx = 288, transcriptPx = 0, statusWithHintPx = 80,
            statusNoHintPx = { measured = true; 40 },
            gapPx = 8, haloExtent = 1.3f, maxPx = 240, minPx = 96, hintThresholdPx = 140,
        )
        assertEquals(false, measured)
    }

    private fun plan(height: Int, transcript: Int, withHint: Int?, noHint: Int) = planStage(
        heightPx = height,
        widthPx = 288,
        transcriptPx = transcript,
        statusWithHintPx = withHint,
        statusNoHintPx = { noHint },
        gapPx = 8,
        haloExtent = 1.3f,
        maxPx = 240,
        minPx = 96,
        hintThresholdPx = 140,
    )
}
