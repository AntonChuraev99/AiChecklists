package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_change
import aichecklists.core.designsystem.generated.resources.analyze_source_link_short
import aichecklists.core.designsystem.generated.resources.analyze_source_pdf
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import aichecklists.core.designsystem.generated.resources.analyze_source_text_file_short
import aichecklists.core.designsystem.generated.resources.analyze_source_text_short
import aichecklists.core.designsystem.generated.resources.analyze_source_voice
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The measured claims of the compact source picker, pinned as numbers.
 *
 * The design brief promises two heights — **104dp** expanded on an ordinary phone and **48dp**
 * collapsed — against the ~332dp the six full-width cards used to take. Those numbers are the whole
 * reason for the change (the editor has to land in the first screen), so they are asserted rather
 * than eyeballed on a frame: a screenshot shows that a block looks short, it does not show that it
 * stayed short after the next spacing tweak.
 *
 * The second half of the file guards the rule that makes the measured ladder safe at all: selecting
 * a material may change FILL and BORDER, never geometry. The columns are measured from an idle probe
 * pill, so a selected pill that grew — a check glyph, a bolder label, more padding — would stop
 * fitting the column measured for it and would drop its own label onto a second line.
 *
 * Run:
 *   ./gradlew :feature:analyze:testAndroidHostTest --tests "*AnalyzeSourcePickerFitTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyzeSourcePickerFitTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task, so an unrestored RU
     * default would silently re-render every LATER test class in Russian.
     */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val defaultLocale: Locale = Locale.getDefault()

    // ── The heights the redesign is measured against ─────────────────────────

    @Test
    fun expandedOnAPhone_isTwoRowsOfThree_and104dpTall() {
        val laid = layout("w412dp-h891dp")

        assertEquals("six pills over two rows; got ${laid.pills}", 2, laid.rowCount)
        assertEquals("…three per row; got ${laid.pills}", 3, laid.columnCount)
        assertEquals(
            "the expanded block must measure 104dp (two 48dp rows + one 8dp gap); got ${laid.blockHeightDp}",
            EXPANDED_DP,
            laid.blockHeightDp,
            TOLERANCE_DP,
        )
    }

    /** The narrowest supported phone still fits three abreast in English. */
    @Test
    fun expandedAt320dp_isStillTwoRowsOfThree_and104dpTall() {
        val laid = layout("w320dp-h568dp")

        assertEquals("three per row at 320dp; got ${laid.pills}", 3, laid.columnCount)
        assertEquals(EXPANDED_DP, laid.blockHeightDp, TOLERANCE_DP)
    }

    /**
     * Collapsed: ONE pill at the minimum touch target, and content-width rather than full-width.
     *
     * The width assertion is not cosmetic — a full-width capsule reads as the screen's primary
     * button and competes with the real Analyze CTA below it.
     */
    @Test
    fun collapsed_isASinglePillOf48dp_andNarrowerThanTheWindow() {
        val laid = layout("w412dp-h891dp", selected = InputDataType.PHOTO, expanded = false)

        assertEquals("collapsed shows exactly one pill; got ${laid.pills}", 1, laid.pills.size)
        assertEquals(
            "the collapsed control must measure 48dp; got ${laid.blockHeightDp}",
            COLLAPSED_DP,
            laid.blockHeightDp,
            TOLERANCE_DP,
        )
        assertTrue(
            "the collapsed pill must hug its content, not fill the window; got ${laid.pills}",
            laid.pills.single().width < laid.blockWidth / 2f,
        )
    }

    /** A tablet takes the top rung: all six abreast, one 48dp row. */
    @Test
    fun expandedOnATablet_isOneRowOfSix_and48dpTall() {
        val laid = layout("w600dp-h800dp")

        assertEquals("six abreast is ONE row; got ${laid.pills}", 1, laid.rowCount)
        assertEquals("…of six columns; got ${laid.pills}", 6, laid.columnCount)
        assertEquals(COLLAPSED_DP, laid.blockHeightDp, TOLERANCE_DP)
    }

    /**
     * The worst combination the brief names: narrowest width × the first accessibility step × the
     * longest locale.
     *
     * No column count is pinned here on purpose — which rung wins depends on translation length and
     * would turn a copy edit into a red test for no reason. What must hold is the property the
     * ladder exists for: whatever rung is chosen, row-mates are the same height (nothing wrapped)
     * and the block is still a fraction of the ~332dp it replaced.
     */
    @Test
    fun worstCase_320dpRussianAtFontScale15_staysCompactWithoutWrapping() {
        val laid = layout("w320dp-h568dp", fontScale = 1.5f, locale = Locale("ru"))

        laid.assertRowMatesShareHeight()
        assertTrue(
            "the worst case must still be well under the ~332dp it replaced; got ${laid.blockHeightDp}",
            laid.blockHeightDp < WORST_CASE_CEILING_DP,
        )
    }

    /**
     * The top of Android's accessibility font range on the narrowest phone, in the longest locale.
     *
     * Measured: two abreast still fits (156dp columns), so the assertion is that the ladder does NOT
     * over-collapse — dropping to one column here would double the block's height for nothing. Every
     * pill must still be exactly one line tall.
     */
    @Test
    fun atFontScale20_theGridStepsDownWithoutWrappingAndWithoutOverCollapsing() {
        val laid = layout("w320dp-h568dp", fontScale = 2f, locale = Locale("ru"))

        assertTrue(
            "two abreast fits at 320dp × 2.0 in RU — collapsing to one column here would double " +
                "the block for nothing; got ${laid.pills}",
            laid.columnCount >= 2,
        )
        laid.assertRowMatesShareHeight()
        assertTrue(
            "every pill must stay one line tall — a wrapped label is the defect the measured " +
                "ladder exists to prevent; got ${laid.pills}",
            laid.pills.all { it.height == laid.pills.first().height },
        )
    }

    /**
     * The bottom rung, reached honestly: a ~200dp content width (freeform / split-screen window, or
     * a foldable cover display) at fontScale 2.0, where not even two pills fit.
     *
     * Without this control a 2-abreast result at 320dp could not be told apart from a ladder that is
     * simply incapable of going lower.
     */
    @Test
    fun at200dpAndFontScale20_theGridFallsBackToOneColumn() {
        val laid = layout("w200dp-h568dp", fontScale = 2f, locale = Locale("ru"))

        assertEquals("one column means six rows; got ${laid.pills}", 6, laid.rowCount)
        assertEquals("…and one left edge; got ${laid.pills}", 1, laid.columnCount)
        laid.assertRowMatesShareHeight()
    }

    /** Devanagari matras sit above AND below the baseline — `heightIn`, never `height`. */
    @Test
    fun inHindiAtFontScale15_rowMatesStillShareOneHeight() =
        layout("w412dp-h891dp", fontScale = 1.5f, locale = Locale("hi")).assertRowMatesShareHeight()

    // ── Selection may not move a single pixel ────────────────────────────────

    /**
     * The rule that keeps the measured ladder honest, checked for ALL SIX materials at once.
     *
     * Seven grids in ONE composition — an idle baseline plus one per selection — because
     * `setContent` may be called only once per test rule, and splitting this into six near-identical
     * methods is how two of them end up asserting different things a year from now. Each label
     * therefore appears seven times; the copies are told apart by their vertical order, which is the
     * order the grids are stacked in.
     */
    @Test
    fun selectingAMaterial_neverMovesTheGrid() {
        RuntimeEnvironment.setQualifiers("w412dp-h891dp")
        val selections = InputDataType.entries.toList()
        var labels = emptyList<String>()
        composeTestRule.setContent {
            labels = allLabels()
            AppTheme(darkTheme = false) {
                Column(verticalArrangement = Arrangement.spacedBy(GridSeparation)) {
                    (listOf(null) + selections).forEach { selection ->
                        AnalyzeSourcePicker(
                            selectedType = selection,
                            expanded = true,
                            onExpandedChange = {},
                            onTypeSelected = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        labels.forEach { label ->
            val copies = composeTestRule.onAllNodesWithText(label)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .sortedBy { it.top }
            assertEquals(
                "\"$label\" must appear once per grid — a probe copy leaking into semantics doubles it",
                selections.size + 1,
                copies.size,
            )
            val idle = copies.first()
            copies.drop(1).forEachIndexed { index, selectedCopy ->
                val selection = selections[index]
                assertEquals(
                    "selecting $selection moved \"$label\" horizontally — selection must change fill " +
                        "and border only, because the columns were measured from an IDLE probe pill",
                    idle.left,
                    selectedCopy.left,
                    0f,
                )
                assertEquals(
                    "selecting $selection resized \"$label\" — a pill that grows stops fitting the " +
                        "column measured for it and drops its label onto a second line",
                    idle.width,
                    selectedCopy.width,
                    0f,
                )
                assertEquals(
                    "selecting $selection changed the height of \"$label\"",
                    idle.height,
                    selectedCopy.height,
                    0f,
                )
            }
        }
    }

    // ── What a screen reader gets ────────────────────────────────────────────

    /**
     * The chosen material must be announced as SELECTED, not as one of six identical buttons.
     *
     * The blue fill is a sighted-only cue: without `semantics { selected }` a TalkBack user hears
     * "Photo, button. PDF, button. File, button…" and has no way to learn which one is active. This
     * is the same hole that was closed on the chat's preset chips.
     */
    @Test
    fun theChosenMaterialIsAnnouncedAsSelected_notJustPaintedBlue() {
        RuntimeEnvironment.setQualifiers("w412dp-h891dp")
        var photo = ""
        var pdf = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            pdf = stringResource(Res.string.analyze_source_pdf)
            AppTheme(darkTheme = false) {
                AnalyzeSourcePicker(
                    selectedType = InputDataType.PHOTO,
                    expanded = true,
                    onExpandedChange = {},
                    onTypeSelected = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(photo).assert(isSelected())
        composeTestRule.onNodeWithText(pdf).assert(isNotSelected())
    }

    /**
     * The collapsed control needs an accessible name that says what tapping DOES.
     *
     * Its visible word is just the material — "Photo" — which reads as a label, not as a control
     * that changes the source. The chevron carries that meaning for sighted users only.
     */
    @Test
    fun theCollapsedControlAnnouncesThatItChangesTheSource() {
        RuntimeEnvironment.setQualifiers("w412dp-h891dp")
        var expected = ""
        composeTestRule.setContent {
            expected = stringResource(
                Res.string.analyze_source_change,
                stringResource(Res.string.analyze_source_photo),
            )
            AppTheme(darkTheme = false) {
                AnalyzeSourcePicker(
                    selectedType = InputDataType.PHOTO,
                    expanded = false,
                    onExpandedChange = {},
                    onTypeSelected = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(expected).assertExists()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private data class Pill(val label: String, val left: Float, val top: Float, val width: Float, val height: Float) {
        override fun toString() = "$label(l=$left, t=$top, w=$width, h=$height)"
    }

    private class Laid(val pills: List<Pill>, val blockHeightDp: Float, val blockWidth: Float) {
        val rowCount get() = pills.map { it.top }.distinct().size
        val columnCount get() = pills.map { it.left }.distinct().size

        fun assertRowMatesShareHeight() = pills.groupBy { it.top }.forEach { (top, mates) ->
            assertTrue(
                "pills on row top=$top have different heights (a wrapped label beside an " +
                    "unwrapped one) — $mates",
                mates.map { it.height }.distinct().size == 1,
            )
        }
    }

    private fun layout(
        qualifiers: String,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        selected: InputDataType? = null,
        expanded: Boolean = true,
    ): Laid {
        // BOTH: `setQualifiers` moves the Android resource configuration Robolectric measures
        // against, while Compose Resources resolves values-ru / values-hi off the JVM default
        // locale. A qualifier-only test renders English while claiming to be the RU case.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        var labels = emptyList<String>()
        var density = 1f
        composeTestRule.setContent {
            labels = allLabels()
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = false) {
                    AnalyzeSourcePicker(
                        selectedType = selected,
                        expanded = expanded,
                        onExpandedChange = {},
                        onTypeSelected = {},
                        modifier = Modifier.fillMaxWidth().testTag(PickerTag),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val block: Rect = composeTestRule.onNodeWithTag(PickerTag).fetchSemanticsNode().boundsInRoot
        // `onAllNodesWithText` returns the CAPSULE, not the label: `Modifier.clickable` merges the
        // pill's descendants, so the label's text lives on the capsule's own node. That is what makes
        // these bounds the pill's bounds — verified against a dumped semantics tree on 2026-08-17,
        // one 74x48px node per pill with `MergeDescendants = 'true'` — and it holds for the collapsed
        // control too, whose contentDescription overrides the NAME without removing the text.
        val pills = labels.mapNotNull { label ->
            composeTestRule.onAllNodesWithText(label)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.boundsInRoot
                ?.let { Pill(label, it.left, it.top, it.width, it.height) }
        }
        return Laid(pills, block.height / density, block.width)
    }

    private companion object {
        const val PickerTag = "analyze-source-picker"

        /** Two 48dp rows plus the 8dp gap between them. */
        const val EXPANDED_DP = 104f

        /** One pill at [com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens.MinTouchTarget]. */
        const val COLLAPSED_DP = 48f

        /** Half of the ~332dp the six full-width cards took — the change has to earn its keep. */
        const val WORST_CASE_CEILING_DP = 200f

        const val TOLERANCE_DP = 0.5f

        /**
         * Wide enough that two stacked grids can never share a `top`, so the copies of one label
         * can be told apart by vertical order alone.
         */
        val GridSeparation = 24.dp
    }
}

/** The six visible labels, in the order [analyzeSourceEntries] offers them. */
@androidx.compose.runtime.Composable
private fun allLabels(): List<String> = listOf(
    stringResource(Res.string.analyze_source_photo),
    stringResource(Res.string.analyze_source_pdf),
    stringResource(Res.string.analyze_source_text_file_short),
    stringResource(Res.string.analyze_source_link_short),
    stringResource(Res.string.analyze_source_text_short),
    stringResource(Res.string.analyze_source_voice),
)
