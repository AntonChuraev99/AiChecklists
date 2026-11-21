package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageDownscaleMathTest {

    @Test
    fun withinLimit_isLeftAlone() {
        val plan = ImageDownscaleMath.plan(width = 2048, height = 1536, maxLongSidePx = 2048)!!
        assertFalse(plan.needsResize)
        assertEquals(1, plan.inSampleSize)
        assertEquals(2048 to 1536, plan.targetWidth to plan.targetHeight)
    }

    @Test
    fun twelveMegapixelPortrait_scalesLongSideToTheLimit_keepingAspect() {
        val plan = ImageDownscaleMath.plan(width = 3024, height = 4032, maxLongSidePx = 2048)!!
        assertTrue(plan.needsResize)
        // 4032 / 2 = 2016 < 2048 → subsampling by 2 would undershoot, so decode at full size.
        assertEquals(1, plan.inSampleSize)
        assertEquals(1536 to 2048, plan.targetWidth to plan.targetHeight)
    }

    @Test
    fun hugeImage_subsamplesByThePowerOfTwoThatStaysAboveTheLimit() {
        val plan = ImageDownscaleMath.plan(width = 9000, height = 6000, maxLongSidePx = 2048)!!
        // 9000/2 = 4500, /4 = 2250 ≥ 2048, /8 = 1125 < 2048 → 4.
        assertEquals(4, plan.inSampleSize)
        assertEquals(2048 to 1365, plan.targetWidth to plan.targetHeight)
    }

    @Test
    fun limitIsAParameter_notTheConstant() {
        val small = ImageDownscaleMath.plan(width = 4000, height = 1000, maxLongSidePx = 1000)!!
        val large = ImageDownscaleMath.plan(width = 4000, height = 1000, maxLongSidePx = 3000)!!
        assertEquals(1000 to 250, small.targetWidth to small.targetHeight)
        assertEquals(4, small.inSampleSize)
        assertEquals(3000 to 750, large.targetWidth to large.targetHeight)
        assertEquals(1, large.inSampleSize)
    }

    @Test
    fun extremePanorama_neverProducesAZeroSide() {
        val plan = ImageDownscaleMath.plan(width = 100_000, height = 10, maxLongSidePx = 2048)!!
        assertEquals(2048, plan.targetWidth)
        assertEquals(1, plan.targetHeight)
    }

    @Test
    fun undecodableHeader_returnsNull() {
        assertNull(ImageDownscaleMath.plan(width = -1, height = -1, maxLongSidePx = 2048))
        assertNull(ImageDownscaleMath.plan(width = 100, height = 100, maxLongSidePx = 0))
    }
}
