package com.antonchuraev.homesearchchecklist.desingsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure decisions behind [SourcePillGrid]: how many columns it is allowed to try, and how
 * wide each of them is.
 *
 * Both were extracted when the four-door row was generalised to serve the Analyze screen's six
 * materials, and both are the kind of arithmetic that a screenshot cannot defend: a grid whose
 * ladder skipped a rung, or whose columns were each a pixel narrow, would look entirely plausible in
 * every frame and would only show up as a wrapped label in one locale at one text scale.
 *
 * Run:
 *   ./gradlew :core:designsystem:testAndroidHostTest --tests "*SourcePillLadderTest*"
 */
class SourcePillLadderTest {

    // ── The ladder ───────────────────────────────────────────────────────────

    /**
     * The four-door ladder must be exactly what [SourceRow] shipped with.
     *
     * This is the regression guard for the ten recorded `SourceRowScreenshotTest` goldens: they
     * cover the 4-abreast, 2×2 and single-column arrangements, so a ladder that grew or lost a rung
     * would move at least one of them.
     */
    @Test
    fun fourDoors_keepTheLadderTheRowShippedWith() {
        assertEquals(listOf(4, 2, 1), sourcePillRungs(4))
    }

    /** Six materials: 6 abreast on a tablet, 3×2 on a phone, 2×3 at large text, then one column. */
    @Test
    fun sixMaterials_stepThroughSixThreeTwoOne() {
        assertEquals(listOf(6, 3, 2, 1), sourcePillRungs(6))
    }

    /**
     * A prime count must not fall off a cliff.
     *
     * Divisors alone would give five materials only `5` and `1` — so dropping one material (say
     * VOICE on a platform without a recorder) would send the grid from one row straight to a
     * six-times-taller single column with nothing in between. The ladder is derived from row counts
     * instead, which fills the gap.
     */
    @Test
    fun aPrimeCount_stillHasRungsBetweenAllAbreastAndOneColumn() {
        assertEquals(listOf(5, 3, 2, 1), sourcePillRungs(5))
        assertEquals(listOf(7, 4, 3, 2, 1), sourcePillRungs(7))
    }

    @Test
    fun everyLadder_startsAtTheCountEndsAtOneAndOnlyEverNarrows() {
        (1..12).forEach { count ->
            val rungs = sourcePillRungs(count)
            assertEquals("$count must be able to try all-abreast; got $rungs", count, rungs.first())
            assertEquals("$count must be able to fall back to one column; got $rungs", 1, rungs.last())
            assertEquals(
                "a ladder must strictly narrow, or the fit loop could pick a WIDER rung after " +
                    "rejecting a narrower one; got $rungs",
                rungs.sortedDescending(),
                rungs,
            )
            assertEquals("a rung must not repeat; got $rungs", rungs.distinct(), rungs)
        }
    }

    // ── The columns ──────────────────────────────────────────────────────────

    /**
     * Columns must tile the available width EXACTLY — that is the whole reason explicit widths can
     * replace `Modifier.weight(1f)` without moving a golden by a pixel.
     */
    @Test
    fun columnsTileTheAvailableWidthExactly_atEveryWidthAndRung() {
        (1..8).forEach { perRow ->
            (0..400).forEach { available ->
                val widths = sourcePillColumnWidths(available, perRow)
                assertEquals("perRow=$perRow available=$available produced ${widths.size} columns", perRow, widths.size)
                assertEquals(
                    "perRow=$perRow available=$available: columns must sum to the available width, " +
                        "got ${widths.toList()}",
                    available,
                    widths.sum(),
                )
            }
        }
    }

    /**
     * The columns are peers: no pill may be more than one pixel wider than another, ever. A larger
     * spread is the "two peers at two different sizes" defect this component's equal-width rule
     * exists to prevent.
     */
    @Test
    fun columnsNeverDifferByMoreThanOnePixel() {
        (2..8).forEach { perRow ->
            (0..400).forEach { available ->
                val widths = sourcePillColumnWidths(available, perRow).toList()
                assertTrue(
                    "perRow=$perRow available=$available: $widths",
                    widths.max() - widths.min() <= 1,
                )
            }
        }
    }

    /**
     * The leftover pixels go to the LEADING columns, which is what `Row` does with weights — spelled
     * out with a worked example so the parity is readable rather than merely asserted.
     */
    @Test
    fun leftoverPixelsGoToTheLeadingColumns_asWeightsWouldHaveDoneIt() {
        // 100 / 3 = 33.33 → base 33, one pixel left over, and it lands on the first column.
        assertEquals(listOf(34, 33, 33), sourcePillColumnWidths(100, 3).toList())
        // 101 / 3 = 33.67 → base 34, one pixel over-allocated, taken back off the first column.
        assertEquals(listOf(33, 34, 34), sourcePillColumnWidths(101, 3).toList())
        // An exact division leaves nothing to distribute.
        assertEquals(listOf(33, 33, 33), sourcePillColumnWidths(99, 3).toList())
    }

    /** A single column is the whole width — the fallback rung must not shave a pixel off it. */
    @Test
    fun oneColumnTakesTheWholeWidth() {
        assertEquals(listOf(360), sourcePillColumnWidths(360, 1).toList())
    }
}
