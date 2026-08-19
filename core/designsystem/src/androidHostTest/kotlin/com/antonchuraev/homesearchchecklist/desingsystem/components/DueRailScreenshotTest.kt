package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.due_planner_repeat
import aichecklists.core.designsystem.generated.resources.due_planner_repeat_off
import aichecklists.core.designsystem.generated.resources.due_planner_time
import aichecklists.core.designsystem.generated.resources.due_preset_in_1_hour
import aichecklists.core.designsystem.generated.resources.due_preset_next_week
import aichecklists.core.designsystem.generated.resources.due_preset_tomorrow
import aichecklists.core.designsystem.generated.resources.due_preset_tonight
import aichecklists.core.designsystem.generated.resources.due_preset_weekend
import aichecklists.core.designsystem.generated.resources.due_rail_clear_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_collapsed_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_expanded_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_no_date
import aichecklists.core.designsystem.generated.resources.due_tomorrow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipItem
import aichecklists.core.designsystem.generated.resources.item_create_chip_important

/**
 * Visual proof for the due rail — the affordance R1 exists to add, and the one the 3.4% date-set
 * rate is measured against.
 *
 * These are REPORT shots, recorded rather than verified: they are what the rail is BUILT against and
 * what travels with the change as evidence. What each frame has to show:
 *  - the rail never scrolls sideways. Every preset is on screen, wrapping to a second line when it
 *    has to (RU at fontScale 1.3 on a 320dp dock is where that happens);
 *  - exactly ONE visual answer to "when": the lead chip. A preset is never drawn selected, and the
 *    applied one is gone from the row entirely;
 *  - every target clears 48dp, including the `×` inside the lead chip, and every chip GROWS with the
 *    text instead of clipping it;
 *  - the planner grid stays a 2x3 of equal cells, with Repeat greyed while there is no date.
 *
 * Record + inspect:
 *   ./gradlew :core:designsystem:recordRoborazziAndroidHostTest --tests "*DueRailScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DueRailScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task, so an unrestored RU
     * default would silently re-render every LATER test class in Russian.
     */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun ComposeContentTestRule.mount(
        qualifiers: String,
        fontScale: Float,
        dark: Boolean,
        locale: Locale,
        content: @Composable () -> Unit,
    ) {
        // BOTH: `setQualifiers` moves the Android resource configuration Robolectric measures
        // against, while Compose Resources resolves values-ru / values-hi off the JVM default
        // locale. A qualifier-only shot renders English while claiming to be the RU frame.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        setContent {
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
    }

    private fun shoot(
        qualifiers: String,
        fontScale: Float = 1f,
        dark: Boolean = false,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.mount(qualifiers, fontScale, dark, locale, content)
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Frames ───────────────────────────────────────────────────────────────

    /** State 1 of the approved mock: dock open, no date yet, the rail's field declared and empty. */
    @Test
    fun dueRail_360dp_light_noDate() = shoot("w360dp-h640dp") { DockStub() }

    /** The same frame in dark — the chips must stay off the dock's own chrome, not dissolve into it. */
    @Test
    fun dueRail_360dp_dark_noDate() = shoot("w360dp-h640dp", dark = true) { DockStub() }

    /**
     * State 2: one tap in. The lead chip carries the answer in the active tone with its own `×`, and
     * "Tomorrow" is GONE from the presets — the mock's first defect was that same answer showing
     * twice and costing the width that pushed the rail onto a second line.
     */
    @Test
    fun dueRail_360dp_light_dateApplied() =
        shoot("w360dp-h640dp") { DockStub(state = DueRailFixture.DateApplied) }

    /** State 3: the planner open. Six offers visible at once, no scroll, Repeat greyed without a date. */
    @Test
    fun dueRail_360dp_light_expanded() =
        shoot("w360dp-h640dp") { DockStub(state = DueRailFixture.Expanded) }

    /** The planner in dark. Cell fills and the selected cell's tone both have to survive the swap. */
    @Test
    fun dueRail_360dp_dark_expanded() =
        shoot("w360dp-h640dp", dark = true) { DockStub(state = DueRailFixture.ExpandedWithDate) }

    /**
     * State 4 of the mock: the narrowest supported phone, the first accessibility text step, and the
     * locale whose labels run longest. This is the frame that must show the rail WRAPPING rather
     * than scrolling or truncating.
     */
    @Test
    fun dueRail_320dp_fontScale13_ru_noDate() =
        shoot("w320dp-h568dp", fontScale = 1.3f, locale = Locale("ru")) { DockStub() }

    /**
     * The grid under the same pressure. Three columns at 288dp of usable width with 1.3x RU copy is
     * where a label wraps, and the row-mates have to grow WITH it — peers at two different heights
     * is the defect the equal-column rule exists to prevent.
     */
    @Test
    fun dueRail_320dp_fontScale13_ru_expanded() =
        shoot("w320dp-h568dp", fontScale = 1.3f, locale = Locale("ru")) {
            DockStub(state = DueRailFixture.ExpandedWithDate)
        }

    /** A current-generation phone at a large accessibility scale, planner open. */
    @Test
    fun dueRail_412dp_fontScale15_expanded() =
        shoot("w412dp-h891dp", fontScale = 1.5f) { DockStub(state = DueRailFixture.Expanded) }

    // ── Semantics: the half of the contract a PNG cannot show ────────────────

    /**
     * Opening the planner has to be an EVENT for a screen reader, not a silent layout change.
     *
     * `stateDescription` is the only channel that carries it: the chevron is decorative and the
     * panel appearing below is a different node entirely.
     */
    @Test
    fun leadChip_announcesExpandedState() {
        var noDate = ""
        var collapsed = ""
        var expanded = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            noDate = stringResource(Res.string.due_rail_no_date)
            collapsed = stringResource(Res.string.due_rail_collapsed_a11y)
            expanded = stringResource(Res.string.due_rail_expanded_a11y)
            Column {
                DockStub()
                DockStub(state = DueRailFixture.Expanded)
            }
        }
        composeTestRule.waitForIdle()

        val states = composeTestRule.onAllNodesWithTextStateDescriptions(noDate)
        assertEquals(
            "the lead chip must announce collapsed/expanded — without it, opening the planner is " +
                "not an event a screen reader can perceive",
            listOf(collapsed, expanded),
            states,
        )
    }

    /**
     * The `×` is a target of its own, and it is the one element in the rail with no words in it.
     *
     * Both halves of that matter: a full [AppDimens.MinTouchTarget] square (the live a11y defect this
     * project already fixed once came from `Modifier.height` pinning min AND max, which silently
     * defeats every 48dp floor below it), and an accessible name, without which it is announced as
     * an unnamed button.
     */
    @Test
    fun clearTarget_isNamedAndFullSize() {
        var clearLabel = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            clearLabel = stringResource(Res.string.due_rail_clear_a11y)
            DockStub(state = DueRailFixture.DateApplied)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(clearLabel)
            .assertWidthIsAtLeast(AppDimens.MinTouchTarget)
            .assertHeightIsAtLeast(AppDimens.MinTouchTarget)
    }

    /**
     * Repeat without a date is DISABLED, not hidden — and TalkBack has to hear that, otherwise it
     * offers a tap that does nothing.
     */
    @Test
    fun repeat_isDisabledWithoutADate() {
        var repeat = ""
        var time = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            repeat = stringResource(Res.string.due_planner_repeat)
            time = stringResource(Res.string.due_planner_time)
            DockStub(state = DueRailFixture.Expanded)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(repeat).assertIsNotEnabled()
        // The control next to it, from the same fixture, stays live — otherwise the assertion above
        // would also pass on a panel that had gone dead as a whole.
        composeTestRule.onNodeWithText(time).assertIsEnabled()
    }

    /**
     * The rail's presets never claim a selection, and the planner's cells always do.
     *
     * Two halves of the same rule. In the rail the answer belongs to the lead chip alone, so
     * `selected = false` there would announce state about something that has none AND contradict the
     * chip. In the grid the cells ARE a set with a current member, so every one of them must report
     * true or false — four identical unlabelled buttons is what this project's own chip row shipped
     * before the same fix.
     */
    @Test
    fun selection_livesOnTheGridNeverOnTheRail() {
        var tonight = ""
        var tomorrow = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            tonight = stringResource(Res.string.due_preset_tonight)
            tomorrow = stringResource(Res.string.due_preset_tomorrow)
            DockStub(state = DueRailFixture.ExpandedWithDate)
        }
        composeTestRule.waitForIdle()

        // "Tonight" exists twice with a date applied: once as a rail preset, once as a grid cell.
        // Exactly one of them may carry a `selected` property, and it is the grid one.
        assertEquals(
            "the rail preset must carry no selection and the grid cell must carry one",
            listOf(null, false),
            composeTestRule.selectedFlagsOf(tonight),
        )
        // The APPLIED preset is dropped from the rail entirely, so "Tomorrow" exists only in the
        // grid — and there it is the current answer.
        assertEquals(
            "the applied preset must be gone from the rail and selected in the grid",
            listOf(true),
            composeTestRule.selectedFlagsOf(tomorrow),
        )
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    /** The four states the rail can be in, as far as layout is concerned. */
    private enum class DueRailFixture { Idle, DateApplied, Expanded, ExpandedWithDate }

    /**
     * The real [QuickCaptureDock] with the rail in its `aboveInput` slot — the mount the Inbox and
     * Calendar tabs will use.
     *
     * The real dock, not a bare Surface, and that is load-bearing rather than realism for its own
     * sake: [com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors] resolves every
     * fill against `LocalChatSurfaceTone`, which the DOCK provides. On a bare page these chips would
     * be rendered on the wrong plane and the frames would prove nothing about the surface they
     * actually ship on.
     *
     * `belowInput` carries the source section because both hosts pass it today; the R1 host is
     * expected to collapse that row while the planner is open, and these frames are what shows why
     * (the dock is already 201-213dp with both rows).
     */
    @Composable
    private fun DockStub(state: DueRailFixture = DueRailFixture.Idle) {
        val hasDate = state == DueRailFixture.DateApplied || state == DueRailFixture.ExpandedWithDate
        val expanded = state == DueRailFixture.Expanded || state == DueRailFixture.ExpandedWithDate
        val applied = if (hasDate) DuePresetId.TOMORROW else null

        val tonight = stringResource(Res.string.due_preset_tonight)
        val tomorrow = stringResource(Res.string.due_preset_tomorrow)
        val weekend = stringResource(Res.string.due_preset_weekend)
        val inOneHour = stringResource(Res.string.due_preset_in_1_hour)
        val nextWeek = stringResource(Res.string.due_preset_next_week)

        // Two offers plus the Important toggle — the shape both capture hosts actually mount today.
        //
        // The rail renders whatever list it is handed and does not decide the count; that decision
        // lives in `feature:home` (`DueRailSection`), which cannot be imported from here. So this is
        // not a mirrored constant pretending to stay in sync — it is a fixture that draws the shipped
        // arrangement, and the live host frames in `CaptureDockDueRailReportTest` are what prove the
        // real numbers. What these goldens must never be is a picture of a configuration production
        // does not show: at three offers the trailing toggle wrapped onto a second line, and a golden
        // nobody can tell is lying is worse than no golden.
        val railPresets = listOf(
            DuePresetChip(DuePresetId.TONIGHT, tonight),
            DuePresetChip(DuePresetId.TOMORROW, tomorrow),
        ).filter { it.id != applied }

        val cells = listOf(
            DuePresetCell(DuePresetId.TONIGHT, tonight, "19:00"),
            DuePresetCell(DuePresetId.TOMORROW, tomorrow, "9:00"),
            DuePresetCell(DuePresetId.WEEKEND, weekend, "Sat 10:00"),
            DuePresetCell(DuePresetId.IN_1_HOUR, inOneHour, "14:20"),
            DuePresetCell(DuePresetId.NEXT_WEEK, nextWeek, "Mon 9:00"),
        )

        QuickCaptureDock(
            text = "",
            onTextChange = {},
            onAdd = {},
            placeholder = "Add a task…",
            aboveInput = {
                Column {
                    DueRailRow(
                        // The formatted answer the host produces. `due_tomorrow` is the real
                        // formatter string, so the RU frames get the RU shape rather than an English
                        // literal wearing a Russian frame's name.
                        leadLabel = if (hasDate) {
                            stringResource(Res.string.due_tomorrow, "9:00")
                        } else {
                            stringResource(Res.string.due_rail_no_date)
                        },
                        hasDate = hasDate,
                        expanded = expanded,
                        presets = railPresets,
                        onLeadClick = {},
                        onClearDate = {},
                        onPresetClick = {},
                        // The dock has always carried this toggle, and it is what makes the row's
                        // width budget tight — a frame without it would flatter the layout.
                        trailing = {
                            GistiSelectableChipItem(
                                icon = Icons.Outlined.StarBorder,
                                label = stringResource(Res.string.item_create_chip_important),
                                selected = false,
                                onClick = {},
                            )
                        },
                    )
                    DuePlannerPanel(
                        expanded = expanded,
                        cells = cells,
                        selectedPreset = applied,
                        hasDate = hasDate,
                        timeValueLabel = "19:00",
                        repeatValueLabel = stringResource(Res.string.due_planner_repeat_off),
                        onPresetClick = {},
                        onPickDateClick = {},
                        onTimeClick = {},
                        onRepeatClick = {},
                        onDoneClick = {},
                    )
                }
            },
            belowInput = {
                SourceRowSection(title = "Or create a checklist from:", onSelect = {})
            },
        )
    }
}

/**
 * Every `SemanticsProperties.Selected` value carried by a node whose text is [text], in tree order.
 *
 * `null` for a node that declares no selection at all — which is a THIRD state, not a synonym for
 * `false`, and the whole point of the assertion that uses it.
 */
private fun ComposeContentTestRule.selectedFlagsOf(text: String): List<Boolean?> =
    onAllNodesWithText(text).fetchSemanticsNodes().map {
        it.config.getOrElseNullable(SemanticsProperties.Selected) { null }
    }

/** Every `stateDescription` carried by a node whose text is [text], in tree order. */
private fun ComposeContentTestRule.onAllNodesWithTextStateDescriptions(text: String): List<String?> =
    onAllNodesWithText(text).fetchSemanticsNodes().map {
        it.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }
    }
