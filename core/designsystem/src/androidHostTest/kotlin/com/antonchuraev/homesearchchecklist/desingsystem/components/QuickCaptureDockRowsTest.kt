package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The dock keeps BOTH of its rows — the date presets and the AI source row — at every supported
 * size and text scale.
 *
 * It briefly did not. A "short viewport" rule silently dropped the preset row (Today / Tomorrow /
 * Important) whenever the window was under 590dp tall OR `fontScale >= 1.3`, and its own recorded
 * goldens showed the presets missing on a 320×568 phone at the DEFAULT text scale — an ordinary
 * small phone, not an edge case. Two things make that unacceptable rather than a trade-off:
 *
 *  - `fontScale >= 1.3` is a stock accessibility setting, so the rule took a one-tap way to set a
 *    due date away from exactly the users least able to go find it in a sheet instead;
 *  - the row it dropped is a `LazyRow` that already scrolls sideways, so the space it "needed" was
 *    never width — it was ~48dp of height, and height can be bought back from padding.
 *
 * Silent removal of a working affordance is the failure mode `~/.claude/rules/user-feedback.md`
 * names outright: when a variant exists that keeps the function, it is the one to take.
 *
 * Run:
 *   ./gradlew :core:designsystem:testAndroidHostTest --tests "*QuickCaptureDockRowsTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickCaptureDockRowsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val defaultLocale: Locale = Locale.getDefault()

    /** The narrowest supported phone at the DEFAULT text scale — the shot that shipped without presets. */
    @Test
    fun bothRows_at320dp_defaultTextScale() = assertBothRowsPresent("w320dp-h568dp", fontScale = 1f)

    /** 320dp + the first accessibility step, in RU where the chip labels are longest. */
    @Test
    fun bothRows_at320dp_fontScale13_russian() =
        assertBothRowsPresent("w320dp-h568dp", fontScale = 1.3f, locale = Locale("ru"))

    /** The exact case the old rule was written for: a 640dp window at fontScale 1.3. */
    @Test
    fun bothRows_at360x640_fontScale13() = assertBothRowsPresent("w360dp-h640dp", fontScale = 1.3f)

    /** A normal modern phone at a large accessibility scale. */
    @Test
    fun bothRows_at412dp_fontScale15() = assertBothRowsPresent("w412dp-h891dp", fontScale = 1.5f)

    /**
     * A host that passes only the preset row must be unaffected — the collapse rule used to be
     * gated on "there is a second row", and removing it must not start dropping the only row a
     * one-row host has.
     */
    @Test
    fun aHostWithNoSourceRow_stillShowsItsPresets() {
        RuntimeEnvironment.setQualifiers("w320dp-h568dp")
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    DockUnderTest(withSourceRow = false)
                }
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "the preset row must survive when it is the dock's only extra row",
            1,
            composeTestRule.onAllNodesWithText(PresetsMarker).fetchSemanticsNodes().size,
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun assertBothRowsPresent(
        qualifiers: String,
        fontScale: Float,
        locale: Locale = Locale.ENGLISH,
    ) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        var photo = ""
        composeTestRule.setContent {
            photo = stringResource(Res.string.analyze_source_photo)
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        DockUnderTest(withSourceRow = true)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "$qualifiers fs=$fontScale locale=$locale: the date presets must stay on screen — " +
                "they are the one-tap way to set a due date",
            1,
            composeTestRule.onAllNodesWithText(PresetsMarker).fetchSemanticsNodes().size,
        )
        assertEquals(
            "$qualifiers fs=$fontScale locale=$locale: the AI source row must stay on screen too — " +
                "neither row may be traded for the other",
            1,
            composeTestRule.onAllNodesWithText(photo).fetchSemanticsNodes().size,
        )
        // The row's HEADING is part of the row, not decoration on top of it. Four unlabelled pills
        // under a task field read as "attach one of these to this task" — an offer the app already
        // serves — instead of "or build me a checklist out of this"; the dock shipped that way and the
        // owner reported it (2026-08-17). It is asserted on the same matrix as the pills because a
        // heading is the FIRST thing a future "make the dock shorter" rule would drop, and it is the
        // one part of the row that carries no icon to hint at its absence.
        assertEquals(
            "$qualifiers fs=$fontScale locale=$locale: the source row's heading must stay on screen — " +
                "without it the four pills are an attachment picker, not a route into Analyze",
            1,
            composeTestRule.onAllNodesWithText(SectionHeadingMarker).fetchSemanticsNodes().size,
        )
    }

    /**
     * The presets are a plain [Text] rather than the real `TaskCreateChipsRow`: that component lives
     * in `feature:home` and this module cannot depend on it. What matters here is only that the
     * `aboveInput` slot is occupied and reports whether the dock rendered it.
     *
     * `belowInput` carries [SourceRowSection], which is what BOTH hosts pass — the fixture tracks the
     * production call sites so a size/scale rule cannot start dropping something the app draws.
     */
    @Composable
    private fun DockUnderTest(withSourceRow: Boolean) {
        QuickCaptureDock(
            text = "",
            onTextChange = {},
            onAdd = {},
            placeholder = "Add a task…",
            aboveInput = {
                Text(
                    text = PresetsMarker,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                )
            },
            belowInput = if (withSourceRow) {
                { SourceRowSection(title = SectionHeadingMarker, onSelect = {}) }
            } else {
                null
            },
        )
    }

    private companion object {
        /** Stands in for the reminder/priority chips; matched by text, so it must be unique. */
        const val PresetsMarker = "Today   Tomorrow   Important"

        /**
         * Stands in for `capture_dock_ai_entry_title`. A literal rather than the resource so this stays
         * an assertion about the SECTION rather than about one locale's copy — the RU run below would
         * otherwise need a second expected string, and matching the localized value is what the host's
         * own test (`InboxAiSourceRowTest`) does.
         */
        const val SectionHeadingMarker = "Or create a checklist from:"
    }
}
