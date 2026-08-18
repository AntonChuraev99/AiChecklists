package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_source_link_short
import aichecklists.core.designsystem.generated.resources.analyze_source_pdf
import aichecklists.core.designsystem.generated.resources.analyze_source_photo
import aichecklists.core.designsystem.generated.resources.analyze_source_voice
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
 * Visual proof for the AI entry row — the affordance that gives the v2 shell a route into Analyze
 * at all.
 *
 * These are REPORT shots, deliberately recorded rather than verified: they exist to be looked at
 * while the row is being built and to travel with the change as evidence. What each one has to
 * show:
 *  - the four labels are all readable and none is truncated (a truncated material name is the
 *    whole defect this row exists to fix — a generic door recorded ZERO photo/pdf/voice analyses
 *    in 30 days);
 *  - the pills clear the 48dp minimum touch target and grow instead of clipping at large text;
 *  - the row steps 4 abreast → 2×2 → one column as space runs out, never by dropping or wrapping a
 *    label;
 *  - the dock keeps BOTH rows — date presets AND sources — at every supported size and text scale.
 *    It briefly did not: an eviction rule dropped the presets under 590dp of window or at
 *    fontScale ≥ 1.3, and its own golden of a 320dp phone at the default scale had none.
 *
 * Record + inspect:
 *   ./gradlew :core:designsystem:recordRoborazziAndroidHostTest --tests "*SourceRowScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourceRowScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task, so an unrestored RU
     * default would silently re-render every LATER test class in Russian.
     */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val defaultLocale: Locale = Locale.getDefault()

    private fun shoot(
        qualifiers: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        // BOTH: `setQualifiers` moves the Android resource configuration Robolectric measures
        // against, while Compose Resources resolves values-ru / values-hi off the JVM default
        // locale. A qualifier-only shot renders English while claiming to be the RU frame.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.BottomCenter,
                    ) { content() }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Baseline: the dock as the Inbox and Calendar tabs mount it — presets above, sources below. */
    @Test
    fun dock_withSources_360dp_light() = shoot("w360dp-h640dp") { DockStub() }

    /** Dark theme — the pills must stay distinguishable from the dock surface behind them. */
    @Test
    fun dock_withSources_360dp_dark() = shoot("w360dp-h640dp", dark = true) { DockStub() }

    // ── Both rows survive every supported size and text scale ────────────────
    //
    // This block is the visual half of `QuickCaptureDockRowsTest`. The dock briefly evicted the date
    // presets below 590dp of window OR at fontScale >= 1.3, and the frame that proved it wrong was
    // `320dp at the default scale` — an ordinary small phone with no presets on it. Each shot below
    // must show BOTH rows: reminder presets above the input, four AI pills below it.

    /** The narrowest supported phone at the DEFAULT text scale — the frame that shipped broken. */
    @Test
    fun dock_bothRows_320dp_defaultScale() = shoot("w320dp-h568dp") { DockStub() }

    /** 320dp plus the first accessibility step, in RU where the chip labels run longest. */
    @Test
    fun dock_bothRows_320dp_fontScale13_ru() =
        shoot("w320dp-h568dp", fontScale = 1.3f, locale = Locale("ru")) { DockStub() }

    /** The exact viewport the old eviction rule was written for. */
    @Test
    fun dock_bothRows_360x640_fontScale13() =
        shoot("w360dp-h640dp", fontScale = 1.3f) { DockStub() }

    /** A current-generation phone at a large accessibility scale. */
    @Test
    fun dock_bothRows_412dp_fontScale15() =
        shoot("w412dp-h891dp", fontScale = 1.5f) { DockStub() }

    /**
     * Positive control for the measured fit rule: given room, the row MUST go four abreast.
     *
     * Without this shot the 2×2 seen on a phone would be indistinguishable from a component that
     * can only ever produce 2×2 — i.e. from a broken measurement that happens to look plausible.
     */
    @Test
    fun dock_withSources_600dp_fourAbreast() = shoot("w600dp-h800dp") { DockStub() }

    /**
     * The bottom rung of the fit ladder: 200dp × fontScale 2.0, where two abreast does NOT fit.
     *
     * Before the second probe existed this frame showed a two-line "Photo" beside a one-line "PDF"
     * — two peers at two different heights. It must now be a single column of four full-width pills,
     * every label on one line. Geometry is asserted by `SourceRowFitTest`; this is what it looks like.
     */
    @Test
    fun sourceRow_200dp_fontScale20_singleColumn() =
        shoot("w200dp-h568dp", fontScale = 2f) { EmptyStateStub() }

    /** The second entry: Inbox empty state, heading + the same shared row. */
    @Test
    fun sourceRow_inboxEmptyState() = shoot("w360dp-h640dp") { EmptyStateStub() }

    /** Empty state at large text — heading and pills both grow, nothing clips. */
    @Test
    fun sourceRow_inboxEmptyState_fontScale13() =
        shoot("w360dp-h640dp", fontScale = 1.3f) { EmptyStateStub() }

    /**
     * Each door must exist exactly ONCE in the semantics tree.
     *
     * The fit rule is decided by subcomposing a probe copy of the pills, and a subcomposed slot
     * still lands in the semantics tree even when it is measured and never placed. Left unguarded,
     * every label existed twice: TalkBack would read out eight doors for four, and any UI test
     * matching on a label would fail on ambiguity rather than on the thing it was testing. This is
     * the regression guard for that, and it fails loudly if the probe ever loses
     * `clearAndSetSemantics`.
     */
    @Test
    fun sourceRow_exposesEachDoorExactlyOnce() {
        RuntimeEnvironment.setQualifiers("w360dp-h640dp")
        var labels = emptyList<String>()
        composeTestRule.setContent {
            labels = listOf(
                stringResource(Res.string.analyze_source_photo),
                stringResource(Res.string.analyze_source_pdf),
                // The SHORT form, matching what SourceRow renders: "Web Link" is what the
                // full-width picker card uses, and asserting on it here would look for a label the
                // row never draws.
                stringResource(Res.string.analyze_source_link_short),
                stringResource(Res.string.analyze_source_voice),
            )
            AppTheme(darkTheme = false) { SourceRow(onSelect = {}) }
        }
        composeTestRule.waitForIdle()

        labels.forEach { label ->
            assertEquals(
                "\"$label\" must appear exactly once — a probe copy leaking into semantics doubles it",
                1,
                composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().size,
            )
        }
    }

    /**
     * The real [QuickCaptureDock] with a stand-in for the host's chip row.
     *
     * The presets are a plain Text rather than the real `TaskCreateChipsRow` because that component
     * lives in `feature:home` and this module cannot depend on it — what matters here is only that
     * SOMETHING occupies the `aboveInput` slot, so the collapse rule has something to collapse.
     *
     * `belowInput` carries the HEADED section, not a bare [SourceRow], because that is what both hosts
     * pass since 2026-08-17 — the dock shipped with four unlabelled pills under the task field, which
     * read as "attach one of these to this task" rather than as "or build me a checklist out of this".
     * A fixture that keeps passing the bare row would go on recording a frame the app no longer draws,
     * and the input-to-heading gap (which comes out of the input's own padding, not the section's) is
     * only judgeable with the heading in the frame.
     */
    @Composable
    private fun DockStub() {
        QuickCaptureDock(
            text = "",
            onTextChange = {},
            onAdd = {},
            placeholder = "Add a task…",
            aboveInput = {
                Text(
                    text = "Today   Tomorrow   Important",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                )
            },
            belowInput = {
                SourceRowSection(
                    // Literal, not `stringResource`: this stands in for the host's copy the way the
                    // preset Text above stands in for the chip row. What the frame is judged on is the
                    // type scale and the two gaps, and a literal keeps the fixture readable.
                    title = "Or create a checklist from:",
                    onSelect = {},
                )
            },
        )
    }

    /** The Inbox's in-list door — the same shared section, on the bare page instead of the chrome. */
    @Composable
    private fun EmptyStateStub() {
        SourceRowSection(
            title = "Turn content into a checklist",
            onSelect = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.ScreenPaddingHorizontal),
        )
    }
}
