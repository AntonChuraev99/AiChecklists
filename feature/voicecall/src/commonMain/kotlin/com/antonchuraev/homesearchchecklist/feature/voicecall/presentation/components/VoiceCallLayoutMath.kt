package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components

import kotlin.math.max
import kotlin.math.min

/**
 * Which lines of a bottom-anchored transcript text are visible (DESIGN_SPEC §1.1 step 4).
 *
 * The window ends one line past [anchorLine] (the line being spoken plus the next, so the eye has
 * somewhere to go), but never shows fewer than [maxLines] lines when the text has them, and never
 * more than [maxLines].
 *
 * @param anchorLine the line that must stay visible; `lineCount - 1` = show the tail.
 * @return `start until end` line indices; empty when [lineCount] is 0.
 */
fun transcriptWindow(lineCount: Int, anchorLine: Int, maxLines: Int): IntRange {
    if (lineCount <= 0 || maxLines <= 0) return IntRange.EMPTY
    val anchor = anchorLine.coerceIn(0, lineCount - 1)
    val end = min(lineCount, max(anchor + 2, min(lineCount, maxLines)))
    val start = max(0, end - maxLines)
    return start until end
}

/** How the call stage spends its height (DESIGN_SPEC §1.1 steps 5–6). */
sealed interface StagePlan {
    /** A stage orb of [diameterPx]; [showHint] = the status hint keeps its place. */
    data class Orb(val diameterPx: Int, val showHint: Boolean) : StagePlan

    /** Not enough height for an orb ≥ OrbMin: the stage collapses, a dot sits by the title. */
    data object Dot : StagePlan
}

/**
 * The orb's diameter for a stage of [stageHeightPx] × [widthPx]:
 * `min((stageH − gap) / haloExtent, width / haloExtent, max)`.
 */
fun orbDiameter(stageHeightPx: Int, widthPx: Int, gapPx: Int, haloExtent: Float, maxPx: Int): Int {
    val byHeight = ((stageHeightPx - gapPx) / haloExtent).toInt()
    val byWidth = (widthPx / haloExtent).toInt()
    return min(min(byHeight, byWidth), maxPx).coerceAtLeast(0)
}

/**
 * The ladder: the hint goes first (orb below [hintThresholdPx]), then the stage itself (orb below
 * [minPx]). [statusWithHintPx] is `null` when the phase has no hint.
 */
@Suppress("LongParameterList")
fun planStage(
    heightPx: Int,
    widthPx: Int,
    transcriptPx: Int,
    statusWithHintPx: Int?,
    statusNoHintPx: () -> Int,
    gapPx: Int,
    haloExtent: Float,
    maxPx: Int,
    minPx: Int,
    hintThresholdPx: Int,
): StagePlan {
    if (statusWithHintPx != null) {
        val d = orbDiameter(heightPx - transcriptPx - statusWithHintPx, widthPx, gapPx, haloExtent, maxPx)
        if (d >= hintThresholdPx) return StagePlan.Orb(d, showHint = true)
    }
    val d = orbDiameter(heightPx - transcriptPx - statusNoHintPx(), widthPx, gapPx, haloExtent, maxPx)
    return if (d >= minPx) StagePlan.Orb(d, showHint = false) else StagePlan.Dot
}
