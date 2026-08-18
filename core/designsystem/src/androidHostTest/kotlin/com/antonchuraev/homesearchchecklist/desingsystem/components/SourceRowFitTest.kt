package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_link_short
import aichecklists.core.designsystem.generated.resources.analyze_source_pdf
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import aichecklists.core.designsystem.generated.resources.analyze_source_voice
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The measured fit ladder of [SourceRow]: four abreast → two abreast → one column.
 *
 * [SourceRow]'s own contract says it "never drops a label and never truncates one", and the way it
 * keeps that promise is by measuring the widest pill and only choosing a layout whose columns are at
 * least that wide. The rung that was missing is the SECOND one: the component probed whether four
 * fit and then fell into a 2×2 with no check of its own, so on a narrow window at a large text scale
 * the 2×2 was chosen while two pills per row did not actually fit. The result is visible and ugly —
 * a label wraps by words and the pill beside it stays one line, i.e. two peers at two different
 * heights, one of them 50% taller.
 *
 * Measured on this file's own probe before the fix, at 200dp × fontScale 2.0:
 *   Photo  left=0   top=0   w=96  h=72   ← wrapped
 *   PDF    left=104 top=0   w=96  h=48   ← not wrapped
 * Identical geometry in RU and HI, because the break is driven by the WIDEST label at that scale,
 * not by the translation.
 *
 * 200dp × fontScale 2.0 is not a stunt: 2.0 is the top of Android's accessibility font range, and a
 * ~200dp content width is what a freeform / split-screen window or a foldable cover display gives.
 *
 * Run:
 *   ./gradlew :core:designsystem:testAndroidHostTest --tests "*SourceRowFitTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourceRowFitTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task, so an unrestored RU
     * default would silently re-render every LATER test class in Russian.
     */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val defaultLocale: Locale = Locale.getDefault()

    // ── The rung that was missing ────────────────────────────────────────────

    @Test
    fun whenTwoAbreastDoesNotFit_theRowFallsBackToOneColumn() {
        val pills = layout("w200dp-h568dp", fontScale = 2f)

        assertEquals(
            "four pills that cannot fit two abreast must stack one per row; got $pills",
            4,
            pills.map { it.top }.distinct().size,
        )
        assertEquals(
            "a single column means one left edge for all four; got $pills",
            1,
            pills.map { it.left }.distinct().size,
        )
    }

    /**
     * The visible symptom, asserted directly: peers must look like peers.
     *
     * Kept separate from the layout-shape assertions above because it is the claim that survives any
     * future change of strategy — however the row decides to arrange itself, two pills side by side
     * may never end up at two different heights.
     *
     * One config per test method on purpose: `setContent` may be called only once per test, so a
     * loop over configs fails on the harness rather than on the assertion.
     */
    @Test
    fun pillsSharingARow_neverDifferInHeight_at200dpFontScale2() =
        assertUniformRows("w200dp-h568dp", fontScale = 2f)

    @Test
    fun pillsSharingARow_neverDifferInHeight_at240dpFontScale15() =
        assertUniformRows("w240dp-h568dp", fontScale = 1.5f)

    @Test
    fun pillsSharingARow_neverDifferInHeight_at320dpFontScale13() =
        assertUniformRows("w320dp-h568dp", fontScale = 1.3f)

    /** RU: the longest of the three shipped label sets after English. */
    @Test
    fun pillsSharingARow_neverDifferInHeight_inRussian() =
        assertUniformRows("w200dp-h568dp", fontScale = 2f, locale = Locale("ru"))

    /** HI: Devanagari matras sit above AND below the baseline, so its line box is the tallest. */
    @Test
    fun pillsSharingARow_neverDifferInHeight_inHindi() =
        assertUniformRows("w200dp-h568dp", fontScale = 2f, locale = Locale("hi"))

    // ── Positive controls: the ladder must not collapse to "always one column" ──

    @Test
    fun givenRoom_theRowStaysFourAbreast() {
        val pills = layout("w360dp-h640dp", fontScale = 1f)

        assertEquals("four abreast is ONE row; got $pills", 1, pills.map { it.top }.distinct().size)
        assertEquals("…with four columns; got $pills", 4, pills.map { it.left }.distinct().size)
    }

    @Test
    fun whenFourDoNotFitButTwoDo_theRowGoesTwoByTwo() {
        val pills = layout("w320dp-h568dp", fontScale = 1.3f)

        assertEquals("2x2 is two rows; got $pills", 2, pills.map { it.top }.distinct().size)
        assertEquals("…of two columns; got $pills", 2, pills.map { it.left }.distinct().size)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private data class Pill(val label: String, val left: Float, val top: Float, val height: Float) {
        override fun toString() = "$label(left=$left, top=$top, h=$height)"
    }

    // ── Semantics: four DOORS are not a selection ────────────────────────────

    /**
     * None of the four doors carries `selected`.
     *
     * [SourceRow] opens a flow; it does not remember what you last opened. Reporting
     * `selected = false` on all four made TalkBack say "not selected" about controls that were never
     * selectable — a statement about state where there is no state — and it did so on the v2 shell's
     * ONLY route into Analyze. `SourcePill`'s `selected` is `Boolean?` for exactly this: `null` writes
     * nothing.
     *
     * Asserted through the node the text query returns, which IS the capsule: `Modifier.clickable`
     * merges the pill's descendants, so the label and the selection state are on one node.
     */
    @Test
    fun theFourDoorsCarryNoSelectedState() {
        RuntimeEnvironment.setQualifiers("w412dp-h891dp")
        var labels = emptyList<String>()
        composeTestRule.setContent {
            labels = doorLabels()
            AppTheme(darkTheme = false) {
                SourceRow(onSelect = {}, modifier = Modifier.fillMaxWidth())
            }
        }
        composeTestRule.waitForIdle()

        labels.forEach { label ->
            val node = composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().single()
            assertEquals(
                "\"$label\" is a door, not an option — it must expose no Selected property at all, " +
                    "got ${node.config.getOrNull(SemanticsProperties.Selected)}",
                null,
                node.config.getOrNull(SemanticsProperties.Selected),
            )
        }
    }

    // ── An UNBOUNDED width must not collapse the row ─────────────────────────

    /**
     * With infinite room the grid takes the TOP rung, not the bottom one.
     *
     * `constraints.maxWidth` is `Constraints.Infinity` (Int.MAX_VALUE) inside a horizontally
     * scrollable parent, and the fit arithmetic — `widestPill * rung` and
     * `maxWidth - gapPx * (perRow - 1)` — overflows to a NEGATIVE Int there. Every rung then fails its
     * check and four doors stack into a single 200dp-tall column. `fillMaxWidth` makes this unreachable
     * from either host today; it is one hoist into a scrollable row away from being reachable, and the
     * failure is silent.
     */
    @Test
    fun givenUnboundedWidth_theRowStaysFourAbreast() {
        RuntimeEnvironment.setQualifiers("w412dp-h891dp")
        var labels = emptyList<String>()
        composeTestRule.setContent {
            labels = doorLabels()
            AppTheme(darkTheme = false) {
                // The unbounded-width parent, which is the whole point of the case.
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    SourceRow(onSelect = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        val tops = labels.map { label ->
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().single().boundsInRoot.top
        }
        assertEquals(
            "unbounded width must resolve to ONE row of four; the pills landed on ${tops.distinct().size} " +
                "rows (tops=$tops), which is the Int overflow collapsing the ladder to a single column",
            1,
            tops.distinct().size,
        )
    }

    private fun assertUniformRows(qualifiers: String, fontScale: Float, locale: Locale = Locale.ENGLISH) {
        val pills = layout(qualifiers, fontScale, locale)
        pills.groupBy { it.top }.forEach { (top, rowMates) ->
            assertTrue(
                "$qualifiers fs=$fontScale locale=$locale: pills on row top=$top have different " +
                    "heights (a wrapped label beside an unwrapped one) — $rowMates",
                rowMates.map { it.height }.distinct().size == 1,
            )
        }
    }

    private fun layout(
        qualifiers: String,
        fontScale: Float,
        locale: Locale = Locale.ENGLISH,
    ): List<Pill> {
        // BOTH: `setQualifiers` moves the Android resource configuration Robolectric measures
        // against, while Compose Resources resolves values-ru / values-hi off the JVM default
        // locale. A qualifier-only test renders English while claiming to be the RU case.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        var labels = emptyList<String>()
        composeTestRule.setContent {
            labels = listOf(
                stringResource(Res.string.analyze_source_photo),
                stringResource(Res.string.analyze_source_pdf),
                stringResource(Res.string.analyze_source_link_short),
                stringResource(Res.string.analyze_source_voice),
            )
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = false) {
                    SourceRow(onSelect = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        composeTestRule.waitForIdle()
        return labels.map { label ->
            val bounds: Rect = composeTestRule.onAllNodesWithText(label)
                .fetchSemanticsNodes()
                .single()
                .boundsInRoot
            Pill(label, bounds.left, bounds.top, bounds.height)
        }
    }

    /** The four doors' labels, in the order [SourceRow] offers them. */
    @Composable
    private fun doorLabels(): List<String> = listOf(
        stringResource(Res.string.analyze_source_photo),
        stringResource(Res.string.analyze_source_pdf),
        stringResource(Res.string.analyze_source_link_short),
        stringResource(Res.string.analyze_source_voice),
    )
}
