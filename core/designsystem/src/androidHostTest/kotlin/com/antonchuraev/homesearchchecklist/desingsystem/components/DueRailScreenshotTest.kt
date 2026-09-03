package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.inbox_quick_add_placeholder
import aichecklists.core.designsystem.generated.resources.capture_dock_ai_entry_title
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
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
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
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import aichecklists.core.designsystem.generated.resources.due_planner_done
import aichecklists.core.designsystem.generated.resources.item_create_chip_important

/**
 * Visual proof for the due rail — the affordance R1 exists to add, and the one the 3.4% date-set
 * rate is measured against.
 *
 * These are REPORT shots, recorded rather than verified: they are what the rail is BUILT against and
 * what travels with the change as evidence. What each frame has to show:
 *  - the rail is ONE line at every width. It scrolls sideways when the chips outgrow it (RU at
 *    fontScale 1.3 on a 320dp dock is where that starts) and fades the edge it can scroll towards,
 *    but it never takes a second line of dock height;
 *  - with the planner open the rail holds the lead chip ALONE — the offers and the Important toggle
 *    fold away, because the grid below already carries the offers and Important is not an answer;
 *  - exactly ONE visual answer to "when": the lead chip. A preset is never drawn selected, and the
 *    applied one is gone from the row entirely;
 *  - every target clears 48dp, including the `×` inside the lead chip, and every chip GROWS with the
 *    text instead of clipping it;
 *  - the planner grid stays a 2x3 of equal cells, and BOTH controls under it are live from an
 *    empty draft — a repeat replaces the date rather than needing one.
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
     * Important ON, with a date applied — the state whose ON-ness used to be unobservable.
     *
     * Pinned outside the scroll, glyph filled and container blue, beside the widest lead chip the
     * rail can carry. Both right-hand things at once, which no earlier frame showed.
     */
    @Test
    fun dueRail_360dp_light_importantOn() =
        shoot("w360dp-h640dp") { DockStub(state = DueRailFixture.DateApplied, important = true) }

    /**
     * State 2: one tap in. The lead chip carries the answer in the active tone with its own `×`, and
     * "Tomorrow" is GONE from the presets — the mock's first defect was that same answer showing
     * twice and costing the width that pushed the rail onto a second line.
     */
    @Test
    fun dueRail_360dp_light_dateApplied() =
        shoot("w360dp-h640dp") { DockStub(state = DueRailFixture.DateApplied) }

    /** State 3: the planner open. Six offers visible at once, no scroll, both controls live. */
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

    /**
     * The rail SCROLLED TO ITS END — the frame every other golden here is blind to.
     *
     * All nine frames above are recorded at scroll 0, so the row's trailing edge is off-screen in
     * every one of them and the inset regression below shipped invisible. 320dp / fontScale 1.3 / RU
     * is the narrowest configuration with the longest copy, i.e. the one that overflows hardest.
     */
    @Test
    fun dueRail_320dp_light_scrolledToEnd() {
        var weekend = ""
        composeTestRule.mount("w320dp-h568dp", 1.3f, dark = false, locale = Locale("ru")) {
            weekend = stringResource(Res.string.due_preset_weekend)
            DockStub()
        }
        composeTestRule.waitForIdle()
        composeTestRule.scrollRailToEnd(weekend)
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── Semantics: the half of the contract a PNG cannot show ────────────────

    /**
     * Scrolled to its end, the last offer keeps the dock's own inset — it does not run into the edge.
     *
     * ## The regression this was written against
     * The rail's END inset used to live INSIDE `if (trailing != null)`, on the pinned chip, and the
     * scrolling half carried `padding(start = horizontalPadding)` alone with a comment saying so
     * ("this half runs to the pinned chip"). When Important left that slot on 2026-09-03 the row lost
     * its only right-hand inset: scrolled to the end the fade switches off (`state.value ==
     * state.maxValue`), and the last pill sat flush against the window at 0dp while the lead chip
     * kept 16dp on the left.
     *
     * ## Why no golden could have caught it
     * Roborazzi records at scroll 0, where the trailing edge is off-screen by construction — which is
     * exactly why this is an assertion on BOUNDS at `maxValue` and not one more frame. The frame
     * beside it ([dueRail_320dp_light_scrolledToEnd]) exists to be looked at, not to prove this.
     */
    @Test
    fun rail_scrolledToEnd_keepsTheDocksEndInset() {
        var weekend = ""
        composeTestRule.mount("w320dp-h568dp", 1.3f, dark = false, locale = Locale("ru")) {
            weekend = stringResource(Res.string.due_preset_weekend)
            DockStub()
        }
        composeTestRule.waitForIdle()

        val range = composeTestRule.scrollRailToEnd(weekend)

        // Precondition: the row really did overflow and really is at its end. Without it the
        // assertion below passes trivially on a rail that never needed to scroll.
        assertTrue(
            "the fixture must overflow at 320dp/1.3x — maxValue=${range.maxValue()}",
            range.maxValue() > 0f,
        )
        assertEquals(
            "…and be scrolled all the way to it",
            range.maxValue(),
            range.value(),
        )

        val last = composeTestRule.onNodeWithText(weekend).getUnclippedBoundsInRoot()
        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the last offer ends at ${last.right} in a ${root.right} window — the rail must keep the " +
                "dock's ${AppDimens.ScreenPaddingHorizontal} inset on the right, the way the lead " +
                "chip keeps it on the left. Scrolled to the end there is no fade left to mark that " +
                "edge, so a chip flush against the window is all the user sees",
            last.right <= root.right - AppDimens.ScreenPaddingHorizontal,
        )
    }

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
     * The Important toggle is ON SCREEN in the state that used to swallow it — now from the INPUT
     * ROW, which is where it moved on 2026-09-03.
     *
     * ## What this test used to assert, and why the contract changed
     * It read `important_isPinnedOnScreenWithADateApplied` and pinned the toggle to the rail's
     * `trailing` slot, outside the scroll: with the rail scrolling since 2026-08-19 the chip had been
     * disappearing off the right edge, and pinning it was the fix. The owner reopened the whole
     * control on 2026-09-03 ("кнопка добавить в избранное… очень плохо выглядит и находится в плохом
     * месте"), so it is an [ImportantStarToggle] in the field's trailing slot now, beside the "+".
     * That is a SPEC change by owner request, not a test yielding to the code — and it is a strictly
     * stronger position: the input row cannot scroll and does not fold with the planner.
     *
     * ⚠️ Asserted on BOUNDS, not with `assertIsDisplayed`, and that is the difference between a test
     * and a decoration: `isDisplayed` is satisfied by any non-empty clipped rectangle, so a control
     * hanging half-off the viewport passes it. What has to hold is that its own unclipped box lies
     * INSIDE the root, both edges.
     */
    @Test
    fun important_isInTheInputRowWithADateApplied() {
        var important = ""
        var leadLabel = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            important = stringResource(Res.string.item_create_chip_important)
            leadLabel = stringResource(Res.string.due_tomorrow, "09:00")
            // The widest state the lead chip has — "Tomorrow 09:00 ×" — plus three offers. The rail
            // being at its widest must have no effect on the toggle any more.
            DockStub(state = DueRailFixture.DateApplied)
        }
        composeTestRule.waitForIdle()

        // Glyph only, so the accessible NAME is a contentDescription — and that this lookup works at
        // all is the second half of the label-less change.
        val toggle = composeTestRule.onNodeWithContentDescription(important)
        toggle.assertWidthIsAtLeast(AppDimens.MinTouchTarget)
            .assertHeightIsAtLeast(AppDimens.MinTouchTarget)

        val bounds = toggle.getUnclippedBoundsInRoot()
        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the toggle must end inside the window, not past it " +
                "(right=${bounds.right}, window=${root.right})",
            bounds.right <= root.right,
        )
        assertTrue(
            "…and start inside it (left=${bounds.left})",
            bounds.left >= root.left,
        )

        // In the INPUT row, not in the rail: strictly below the lead chip. Read off that chip's own
        // box rather than off a constant, so a font scale cannot move the ruler out from under it.
        val leadChip = composeTestRule.onNodeWithText(leadLabel).getUnclippedBoundsInRoot()
        assertTrue(
            "the toggle must sit BELOW the due rail — it is part of the input row now " +
                "(toggle top=${bounds.top}, rail bottom=${leadChip.bottom})",
            bounds.top >= leadChip.bottom,
        )
    }

    /**
     * Off ↔ on is announced as a STATE on one control, not as two controls.
     *
     * `toggleableState`, not a swapped `contentDescription`: a screen reader that hears a different
     * NAME for each state cannot say the thing was toggled, only that something else is there now.
     * The name stays `item_create_chip_important` in both, which is also why no new copy was needed.
     *
     * The ROLE is asserted with it. A name and a state with no role are announced as a button that
     * happens to mention "checked", so the one thing the user needs — "double-tap to toggle" — never
     * gets said; `Surface(onClick)` alone would announce `Button` and contradict the state.
     */
    @Test
    fun importantToggle_announcesItsStateAndReportsTheTap() {
        var important = ""
        var taps = 0
        val on = mutableStateOf(false)
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            important = stringResource(Res.string.item_create_chip_important)
            DockStub(important = on.value, onImportantToggle = { taps++ })
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "OFF must be announced as an unchecked toggle",
            listOf(ToggleableState.Off),
            composeTestRule.toggleStatesOf(important),
        )
        composeTestRule.onNodeWithContentDescription(important).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        )

        composeTestRule.onNodeWithContentDescription(important).performClick()
        composeTestRule.waitForIdle()
        assertEquals("the tap must reach the host", 1, taps)

        composeTestRule.runOnIdle { on.value = true }
        composeTestRule.waitForIdle()
        assertEquals(
            "ON must be announced as the SAME control in a different state, not a second control",
            listOf(ToggleableState.On),
            composeTestRule.toggleStatesOf(important),
        )
    }

    /**
     * Done dismisses the panel, so it must not be the loudest thing in the dock.
     *
     * It was an `AppButton` — filled `primary`, pill — sitting ~40dp above the filled `primary`
     * submit "+", which gave the dock two primary actions and made the louder one the one that only
     * closes a panel. It is an `AppButtonText` now, and this pins that by COLOUR: no pixel of the
     * Done row may carry the `primary` fill.
     *
     * A pixel probe rather than a type check, because "is it an AppButtonText" is a fact about the
     * source, while "is there a blue slab in the dock" is the thing the owner is looking at.
     *
     * ⚠️ Captured to a FILE through Roborazzi and re-read, never with `captureToImage()`: the dock
     * focuses its input on mount, a focused field blinks its caret forever, and `captureToImage`
     * first waits for an idle clock that therefore never arrives (`ComposeTimeoutException`, seen
     * while writing this test). Roborazzi's capture does not wait. The frame is forced to `Record`
     * outside the golden directory, so `verifyRoborazzi*` neither compares it nor wants it in git.
     */
    @Test
    fun done_isNotAFilledPrimaryButton() {
        var done = ""
        var chromeRgb = 0
        var primaryRgb = 0
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            done = stringResource(Res.string.due_planner_done)
            // NAMED, not derived from another pixel of the same frame: a probe that compares the
            // button with its neighbour passes just as happily on a frame where both are wrong.
            chromeRgb = AppSurface.bottomChrome().toArgb() and 0xFFFFFF
            primaryRgb = MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF
            DockStub(state = DueRailFixture.Expanded)
        }
        composeTestRule.waitForIdle()

        val bounds = composeTestRule.onNodeWithText(done).getUnclippedBoundsInRoot()
        val file = File("$ProbeDir/done_not_filled.png")
        file.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = file.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        val image = ImageIO.read(file)

        // The button's LEADING edge at its vertical middle — 1dp == 1px at this qualifier's density.
        // Chosen because it is the one point a pill-shaped fill is guaranteed to cover (the widest
        // part of the capsule) and the one a text button's LABEL never reaches: `AppButtonText`'s own
        // content colour IS `primary`, so counting blue pixels anywhere in the box would flag the
        // word "Done" itself.
        val x = bounds.left.value.toInt() + EdgeProbeInsetPx
        val y = ((bounds.top.value + bounds.bottom.value) / 2f).toInt()
        val here = image.getRGB(x, y) and 0xFFFFFF

        assertEquals(
            "Done must not be a filled primary button — it dismisses the planner, while the action " +
                "that commits the task is the '+' in the field below it. Its leading edge reads " +
                "#%06X where the dock's own #%06X is expected".format(here, chromeRgb) +
                (if (here == primaryRgb) " — that is the `primary` fill." else ""),
            chromeRgb,
            here,
        )
    }

    /**
     * Both settings rows are live with NO date applied, and Repeat is the interesting one.
     *
     * Repeat shipped greyed until there was a date. `TaskDraft.withRepeat(config)` nulls `reminderAt`
     * AND `reminderPreset`, so a saved rule REPLACES the date rather than decorating it — the gate
     * asked the user to pick a date the very next step would delete, and `Surface(enabled = false)`
     * left them no way to find that out.
     */
    @Test
    fun repeat_isReachableWithoutADate() {
        var repeat = ""
        var time = ""
        var repeatTaps = 0
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            repeat = stringResource(Res.string.due_planner_repeat)
            time = stringResource(Res.string.due_planner_time)
            // Expanded with NO date — the exact state the chip used to be dead in.
            DockStub(state = DueRailFixture.Expanded, onRepeatClick = { repeatTaps++ })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(repeat).assertIsEnabled()
        // The control beside it, from the same fixture, is live too — otherwise the assertion above
        // would pass just as well on a panel that had gone live as a whole for an unrelated reason.
        composeTestRule.onNodeWithText(time).assertIsEnabled()
        // And neither declares an unavailability: there is none left to declare.
        assertEquals(listOf<String?>(null), composeTestRule.onAllNodesWithTextStateDescriptions(repeat))

        // The tap ARRIVES. This is what `Surface(enabled = false)` used to eat.
        composeTestRule.onNodeWithText(repeat).performClick()
        composeTestRule.waitForIdle()
        assertEquals("the tap must reach the host", 1, repeatTaps)
    }

    /**
     * The rail's presets never claim a selection, and the planner's cells always do.
     *
     * Two halves of the same rule, and since the rail folds its offers away while the planner is
     * open they are now observed in the two states that actually show them: the rail COLLAPSED with
     * a date applied, the grid EXPANDED. In the rail the answer belongs to the lead chip alone, so
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
            DockStub(state = DueRailFixture.DateApplied)
        }
        composeTestRule.waitForIdle()

        // Collapsed with a date applied: "Tonight" is a rail offer and nothing else, and it declares
        // no selection at all — `null`, which is a third state and not a synonym for false.
        assertEquals(
            "a rail preset must carry no selection",
            listOf(null),
            composeTestRule.selectedFlagsOf(tonight),
        )
        // The APPLIED preset is dropped from the rail entirely; the lead chip is already stating it.
        assertEquals(
            "the applied preset must be gone from the rail",
            emptyList<Boolean?>(),
            composeTestRule.selectedFlagsOf(tomorrow),
        )
    }

    /** The grid half of the same rule: every cell reports a selection, the applied one reports true. */
    @Test
    fun selection_isDeclaredByEveryGridCell() {
        var tonight = ""
        var tomorrow = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            tonight = stringResource(Res.string.due_preset_tonight)
            tomorrow = stringResource(Res.string.due_preset_tomorrow)
            DockStub(state = DueRailFixture.ExpandedWithDate)
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "an unapplied grid cell must declare a selection and report false",
            listOf(false),
            composeTestRule.selectedFlagsOf(tonight),
        )
        assertEquals(
            "the applied preset must be selected in the grid",
            listOf(true),
            composeTestRule.selectedFlagsOf(tomorrow),
        )
    }

    /**
     * Opening the planner leaves the ANSWER in the rail and folds the offers away.
     *
     * Because the grid two rows below holds all five of them WITH the time each resolves to —
     * keeping the pills up here as well puts two controls carrying the same accessible name on one
     * screen.
     *
     * ## The Important half of this test is gone, deliberately
     * It used to assert that the toggle folded with the offers ("скрывать при выборе даты",
     * 2026-08-19). It does not fold any more because it is not in the rail any more: on 2026-09-03 it
     * moved into the input row (see [important_isInTheInputRowWithADateApplied]), where it is
     * reachable in EVERY state — including this one, which is the state it used to be missing from.
     * The claim was dropped rather than inverted here so that this test keeps one subject.
     *
     * Asserted in BOTH directions on purpose: a fold that never comes back is the defect this
     * project has actually shipped, and an assertion that only checks the disappearance passes just
     * as happily on it.
     */
    @Test
    fun rail_foldsTheOffersWhileThePlannerIsOpen() {
        var tonight = ""
        // ONE mount, toggled — `mount` wraps `setContent`, which a test may call exactly once, and
        // the round trip is the point anyway: two separate mounts could not tell a fold that comes
        // back from a rail that was built expanded.
        val fixture = mutableStateOf(DueRailFixture.Idle)
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            tonight = stringResource(Res.string.due_preset_tonight)
            DockStub(state = fixture.value)
        }
        composeTestRule.waitForIdle()

        // Collapsed, "Tonight" is in the rail only — the grid is not composed.
        assertEquals(1, composeTestRule.onAllNodesWithText(tonight).fetchSemanticsNodes().size)

        composeTestRule.runOnIdle { fixture.value = DueRailFixture.Expanded }
        composeTestRule.waitForIdle()

        // Expanded, "Tonight" is in the GRID only — one node, not two. This is the assertion that
        // catches a rail which kept its pills: it would read 2.
        assertEquals(1, composeTestRule.onAllNodesWithText(tonight).fetchSemanticsNodes().size)

        // …and back. The fold is a fold, not a deletion.
        composeTestRule.runOnIdle { fixture.value = DueRailFixture.Idle }
        composeTestRule.waitForIdle()

        assertEquals(1, composeTestRule.onAllNodesWithText(tonight).fetchSemanticsNodes().size)
    }

    /**
     * …and the toggle that no longer folds is still there when the planner is open.
     *
     * The other half of the change above, kept as its own cell so the fold test keeps one subject.
     * This is the state the control was unreachable in before 2026-09-03.
     */
    @Test
    fun important_survivesThePlannerBeingOpen() {
        var important = ""
        composeTestRule.mount("w360dp-h640dp", 1f, dark = false, locale = Locale.ENGLISH) {
            important = stringResource(Res.string.item_create_chip_important)
            DockStub(state = DueRailFixture.Expanded)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(important).assertExists()
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    /** The four states the rail can be in, as far as layout is concerned. */
    private enum class DueRailFixture { Idle, DateApplied, Expanded, ExpandedWithDate }

    private companion object {
        /** Scratch frames for the pixel probes, deliberately outside the golden directory. */
        const val ProbeDir = "build/due-rail-probe"

        /** How far inside a control's leading edge a probe lands — clear of its antialiased rim. */
        const val EdgeProbeInsetPx = 2
    }

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
    private fun DockStub(
        state: DueRailFixture = DueRailFixture.Idle,
        onRepeatClick: () -> Unit = {},
        important: Boolean = false,
        onImportantToggle: () -> Unit = {},
    ) {
        val hasDate = state == DueRailFixture.DateApplied || state == DueRailFixture.ExpandedWithDate
        val expanded = state == DueRailFixture.Expanded || state == DueRailFixture.ExpandedWithDate
        val applied = if (hasDate) DuePresetId.TOMORROW else null

        val tonight = stringResource(Res.string.due_preset_tonight)
        val tomorrow = stringResource(Res.string.due_preset_tomorrow)
        val weekend = stringResource(Res.string.due_preset_weekend)
        val inOneHour = stringResource(Res.string.due_preset_in_1_hour)
        val nextWeek = stringResource(Res.string.due_preset_next_week)

        // Three offers plus the Important toggle — the shape both capture hosts mount since
        // 2026-08-19, when the rail started scrolling instead of wrapping and `RAIL_PRESET_COUNT`
        // went from two to three.
        //
        // The rail renders whatever list it is handed and does not decide the count; that decision
        // lives in `feature:home` (`DueRailSection`), which cannot be imported from here. So this is
        // not a mirrored constant pretending to stay in sync — it is a fixture that draws the shipped
        // arrangement, and the live host frames in `CaptureDockDueRailReportTest` are what prove the
        // real numbers. What these goldens must never be is a picture of a configuration production
        // does not show, and a golden nobody can tell is lying is worse than no golden.
        val railPresets = listOf(
            DuePresetChip(DuePresetId.TONIGHT, tonight),
            DuePresetChip(DuePresetId.TOMORROW, tomorrow),
            DuePresetChip(DuePresetId.WEEKEND, weekend),
        ).filter { it.id != applied }

        val cells = listOf(
            DuePresetCell(DuePresetId.TONIGHT, tonight, "19:00"),
            DuePresetCell(DuePresetId.TOMORROW, tomorrow, "09:00"),
            DuePresetCell(DuePresetId.WEEKEND, weekend, "Sat 10:00"),
            DuePresetCell(DuePresetId.IN_1_HOUR, inOneHour, "14:20"),
            DuePresetCell(DuePresetId.NEXT_WEEK, nextWeek, "Mon 09:00"),
        )

        QuickCaptureDock(
            text = "",
            onTextChange = {},
            onAdd = {},
            // The real resource, not an English literal: a frame named _ru_ that renders two
            // English strings cannot be used to check a locale, which is what it exists for.
            placeholder = stringResource(Res.string.inbox_quick_add_placeholder),
            aboveInput = {
                Column {
                    DueRailRow(
                        // The formatted answer the host produces. `due_tomorrow` is the real
                        // formatter string, so the RU frames get the RU shape rather than an English
                        // literal wearing a Russian frame's name.
                        leadLabel = if (hasDate) {
                            stringResource(Res.string.due_tomorrow, "09:00")
                        } else {
                            stringResource(Res.string.due_rail_no_date)
                        },
                        hasDate = hasDate,
                        expanded = expanded,
                        presets = railPresets,
                        onLeadClick = {},
                        onClearDate = {},
                        onPresetClick = {},
                        // No trailing slot: Important lives in the INPUT row now (2026-09-03), which
                        // is what `trailingToggle` below mounts. The rail keeps the parameter as
                        // design-system API and both capture hosts pass nothing.
                        trailing = null,
                    )
                    DuePlannerPanel(
                        expanded = expanded,
                        cells = cells,
                        selectedPreset = applied,
                        timeValueLabel = "19:00",
                        repeatValueLabel = stringResource(Res.string.due_planner_repeat_off),
                        onPresetClick = {},
                        onPickDateClick = {},
                        onTimeClick = {},
                        onRepeatClick = onRepeatClick,
                        onDoneClick = {},
                    )
                }
            },
            belowInput = {
                SourceRowSection(
                    title = stringResource(Res.string.capture_dock_ai_entry_title),
                    onSelect = {},
                )
            },
            // The seat Important took on 2026-09-03: the field's trailing slot, immediately before
            // the "+". Both capture hosts mount exactly this, so a frame without it would flatter the
            // input row's width budget.
            trailingToggle = {
                ImportantStarToggle(selected = important, onClick = onImportantToggle)
            },
        )
    }
}

/**
 * Scrolls the due rail as far right as it goes and reports where it landed.
 *
 * Driven through the node's own `ScrollBy` semantics action with a deliberate overshoot, which the
 * scroll state clamps to `maxValue` — NOT through `performScrollTo()` on the last chip, which stops
 * the moment that chip is fully visible and so would land at "chip flush against the viewport", i.e.
 * exactly the state the inset assertion has to be able to fail on.
 *
 * The rail is addressed as "the scrollable that contains [anchorText]" rather than by a test tag:
 * the dock has other scrollables in other states, and a tag would be production code added for a
 * test.
 */
private fun ComposeContentTestRule.scrollRailToEnd(anchorText: String): ScrollAxisRange {
    val rail = onNode(hasScrollAction() and hasAnyDescendant(hasText(anchorText)))
    rail.performSemanticsAction(SemanticsActions.ScrollBy) { it(RailScrollOvershootPx, 0f) }
    waitForIdle()
    return rail.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
}

/** Far wider than any rail this test mounts; the scroll state clamps it to `maxValue`. */
private const val RailScrollOvershootPx = 10_000f

/** Every `SemanticsProperties.ToggleableState` carried by a node named [description], in tree order. */
private fun ComposeContentTestRule.toggleStatesOf(description: String): List<ToggleableState?> =
    onAllNodesWithContentDescription(description).fetchSemanticsNodes().map {
        it.config.getOrElseNullable(SemanticsProperties.ToggleableState) { null }
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
