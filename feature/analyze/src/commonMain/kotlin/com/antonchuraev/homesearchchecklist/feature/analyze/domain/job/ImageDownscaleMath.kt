package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Size decision of an [ImageDownscaler], pure so every platform implementation shares one tested rule.
 *
 * @property needsResize false when the long side is already within the limit (no pixel change needed).
 * @property inSampleSize power-of-two decode subsampling (Android `BitmapFactory.Options.inSampleSize`):
 *   the largest one that still decodes the long side at or above the limit, so the exact scale that
 *   follows only ever shrinks — never upsamples a subsampled bitmap.
 * @property targetWidth / [targetHeight] final pixel size in the source orientation (before any EXIF
 *   rotation; a 90° rotation swaps them, the long side is unchanged).
 */
data class DownscalePlan(
    val needsResize: Boolean,
    val inSampleSize: Int,
    val targetWidth: Int,
    val targetHeight: Int,
)

object ImageDownscaleMath {

    /** Returns null for a non-positive size (undecodable header) or a non-positive limit. */
    fun plan(width: Int, height: Int, maxLongSidePx: Int): DownscalePlan? {
        if (width <= 0 || height <= 0 || maxLongSidePx <= 0) return null
        val longSide = max(width, height)
        if (longSide <= maxLongSidePx) {
            return DownscalePlan(needsResize = false, inSampleSize = 1, targetWidth = width, targetHeight = height)
        }
        var sample = 1
        while (longSide / (sample * 2) >= maxLongSidePx) sample *= 2
        val scale = maxLongSidePx.toDouble() / longSide
        return DownscalePlan(
            needsResize = true,
            inSampleSize = sample,
            targetWidth = max(1, (width * scale).roundToInt()),
            targetHeight = max(1, (height * scale).roundToInt()),
        )
    }
}
