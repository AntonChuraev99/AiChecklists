package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxPage
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxScreenState
import com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox.InboxTask
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayScreenState
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * While the quick-capture dock is up, the two corners its `SheetTop` shape clips away must show the
 * DIMMED page — never the raw one.
 *
 * ## The defect, from a device
 * The dock lives in `AppScaffold`'s `bottomBar` slot and the capture scrim lives inside the CONTENT
 * slot. That boundary is deliberate — it is what keeps the dock, the snackbar and the system-nav
 * strip out of the dim at any keyboard height, with nothing to measure — but it also means the strip
 * BEHIND the dock is outside the scrim: the scaffold's own container, i.e. the page at full
 * brightness. The dock's clipped shoulders showed it. Measured on the shell's recorded frame before
 * the fix, 412dp light, at x = 2: `#FBFAF8` in the shoulder against `#8A8988` in the page beside it —
 * ΔL\* +41, the brightest thing in the bottom half of a screen whose entire subject at that moment is
 * the dock. Reported from a Pixel 9 as two light corners next to the dock.
 *
 * ## Why this test is here and not beside the shell's
 * `composeApp`'s `V2BarShoulderFillTest` covers the same claim, but it must build its own stand-in for
 * this screen: `feature:home` is not on that module's test classpath. A stand-in can only ever prove
 * that the stand-in is right. THIS one renders the real [InboxScreen] — the real scaffold, the real
 * slot boundary, the real two-tile scrim — so it is the one that fails if the fix is removed from the
 * host rather than from a fixture.
 *
 * ## Why the probe is a pixel and not a golden
 * The defect is a ~28x28px wedge in a corner. It survived a device build and several golden reviews
 * precisely because it is small; a golden would record it and then be the reference for it.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxCaptureDockShoulderTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h891dp-normal-long-notround-any-160dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureDockShoulderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `AppSurface.ground()` for the theme of the current frame, lifted OUT of composition.
     *
     * This is the value the DEFECT produced, so the assertion can name it instead of comparing two
     * pixels of one frame with each other — which would pass just as happily on a frame with no scrim
     * at all, i.e. on a fixture that had quietly stopped reproducing the case.
     */
    private var groundUnderTest: Int = 0

    /**
     * `gistiDockColor()` for the theme of the current frame, lifted OUT of composition.
     *
     * The value the dock's surface MUST read once nothing is painted over it — named, rather than
     * derived from another pixel of the same frame, so the assertion cannot be satisfied by a frame
     * where the dock is uniformly wrong.
     */
    private var dockColourUnderTest: Int = 0

    /** [InboxScreen] `koinInject()`s both of these directly; without a root the composition dies. */
    @Before
    fun startKoin() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AnalyticsTracker> { NoopAnalyticsTracker }
                    single<AppLogger> { NoopAppLogger }
                }
            )
        }
    }

    @After
    fun stopKoinAfterTest() = stopKoin()

    @Test
    fun inboxCaptureDockShoulder_light_isTheDimmedPage_notTheRawPage() =
        assertShoulderIsDimmed(Host.Inbox, dark = false)

    @Test
    fun inboxCaptureDockShoulder_dark_isTheDimmedPage_notTheRawPage() =
        assertShoulderIsDimmed(Host.Inbox, dark = true)

    /**
     * The Calendar tab mounts the SAME dock through a different screen, and the two paint their
     * content scrims by different mechanisms (an overlay `Box` here, a `drawWithContent` layer
     * there). Two hosts, one contract — and a contract kept by hand in two places is one that gets
     * kept in one of them.
     */
    @Test
    fun calendarCaptureDockShoulder_light_isTheDimmedPage_notTheRawPage() =
        assertShoulderIsDimmed(Host.Calendar, dark = false)

    @Test
    fun calendarCaptureDockShoulder_dark_isTheDimmedPage_notTheRawPage() =
        assertShoulderIsDimmed(Host.Calendar, dark = true)

    // ── The same contract on a window too short to hold both bars ────────────────────────────────
    // See [ShortWindow] for why this is the keyboard case, expressed without a keyboard.

    @Test
    @Config(qualifiers = ShortWindow)
    fun inboxCaptureDock_shortWindow_light_dockIsBrightAndShoulderIsTheDimmedPage() =
        assertDimStopsAtTheDock(Host.Inbox, dark = false)

    @Test
    @Config(qualifiers = ShortWindow)
    fun inboxCaptureDock_shortWindow_dark_dockIsBrightAndShoulderIsTheDimmedPage() =
        assertDimStopsAtTheDock(Host.Inbox, dark = true)

    @Test
    @Config(qualifiers = ShortWindow)
    fun calendarCaptureDock_shortWindow_light_dockIsBrightAndShoulderIsTheDimmedPage() =
        assertDimStopsAtTheDock(Host.Calendar, dark = false)

    @Test
    @Config(qualifiers = ShortWindow)
    fun calendarCaptureDock_shortWindow_dark_dockIsBrightAndShoulderIsTheDimmedPage() =
        assertDimStopsAtTheDock(Host.Calendar, dark = true)

    /**
     * On an over-constrained window, NOTHING but the behind-the-dock tile may paint at or below the
     * dock's top edge — so the dock's own surface is the bottom chrome, and its shoulder is the page
     * dimmed exactly once.
     *
     * Both halves are asserted together because the defect produced both at once and from one cause:
     * a tile sized from the content slot's OFFSET ran 65px past the dock's top edge, laying a second
     * scrim over the shoulder (the page dimmed twice) and a first one over the dock (the bottom
     * chrome dimmed once). Asserting only the shoulder would let a future fix that merely stops
     * painting the shoulder pass while the dock stayed dim, and vice versa.
     */
    private fun assertDimStopsAtTheDock(host: Host, dark: Boolean) {
        val image = render(host, dark, case = "shortwindow")
        val dockTop = dockTopEdge(image)

        assertTrue(
            "this frame no longer over-constrains the scaffold: the dock's top edge is at y=$dockTop, " +
                "which is at or below the app bar's minimum height (${AppBarMinHeight}px), so the " +
                "content slot still had room and the case is not being exercised. Shorten [ShortWindow] " +
                "(the dock grew: it is ${image.height - dockTop}px tall here).",
            dockTop in 1 until AppBarMinHeight,
        )

        val dockSurface = rgb(image, image.width / QuarterWidth, dockTop + IntoTheDock)
        assertEquals(
            "the dock's own surface ${IntoTheDock}px below its top edge reads ${hex(dockSurface)} " +
                "instead of the bottom chrome ${hex(dockColourUnderTest)} — a scrim tile is painting " +
                "OVER the dock. The dim's bottom edge must be the dock's top edge, and no proxy for it " +
                "(host: $host, theme: ${themeName(dark)})",
            hex(dockColourUnderTest),
            hex(dockSurface),
        )

        assertShoulderIsDimmed(host, dark, image)
    }

    private fun assertShoulderIsDimmed(host: Host, dark: Boolean) =
        assertShoulderIsDimmed(host, dark, render(host, dark))

    private fun assertShoulderIsDimmed(host: Host, dark: Boolean, image: BufferedImage) {
        val wedgeBottom = shoulderWedgeBottom(image)

        val shoulder = rgb(image, SampleX, wedgeBottom - InsideWedge)
        val dimmedPageAbove = rgb(image, SampleX, wedgeBottom - AboveTheDock)

        assertNotEquals(
            "the shoulder reads ${hex(shoulder)} — the RAW page. The host's scrim is not being " +
                "painted behind the dock, so its two clipped corners are the brightest thing beside " +
                "a 45%-dimmed page (host: $host, theme: ${themeName(dark)})",
            hex(groundUnderTest),
            hex(shoulder),
        )
        assertEquals(
            "the shoulder reads ${hex(shoulder)} while the page ${AboveTheDock - InsideWedge}px " +
                "above it reads ${hex(dimmedPageAbove)} — the corner must continue the dimmed page " +
                "rather than step off it (host: $host, theme: ${themeName(dark)})",
            hex(dimmedPageAbove),
            hex(shoulder),
        )
    }

    /**
     * The first row, walking UP the left margin from the bottom of the window, that is no longer the
     * dock's own surface — i.e. the bottom of the clipped corner wedge.
     *
     * Walked from the bottom rather than measured from a semantics node on purpose. The dock focuses
     * its input on mount and a focused field blinks its caret forever, so every `fetchSemanticsNode` /
     * `captureToImage` path first waits for an idle clock that never arrives; Roborazzi's file capture
     * does not wait, which is why the whole probe is done on the PNG.
     *
     * [SampleX] is inside the 28dp corner sector, where the arc closes ~17px below the dock's top
     * edge, and it is clear of the dock's 16dp horizontal padding — so every row below the wedge in
     * this column is flat dock surface and the walk cannot stop early on a pill or a glyph.
     */
    private fun shoulderWedgeBottom(image: BufferedImage): Int {
        val dockSurface = rgb(image, SampleX, image.height - 2)
        var y = image.height - 2
        while (y > 0 && rgb(image, SampleX, y) == dockSurface) y--
        assertTrue(
            "walked the whole window without leaving the dock's surface (${hex(dockSurface)}) — the " +
                "dock did not render, or it is not the bottom chrome",
            y in AboveTheDock until image.height - 2,
        )
        return y
    }

    /**
     * The dock's top edge, found by scanning DOWN the quarter-width column until the frame stops
     * being the app bar behind its dim.
     *
     * That column is clear of every glyph either host puts in the bar — the Inbox's title is
     * start-aligned and short, the Calendar's is centred, and neither test passes a `creditsSource`
     * or a navigation icon — so the first change of colour going down is the dock's own top edge and
     * cannot be a letter. Scanned downwards rather than walked up from the bottom like
     * [shoulderWedgeBottom]: this probe needs the EDGE, and the bottom-up walk stops at the wedge,
     * ~17px below it.
     */
    private fun dockTopEdge(image: BufferedImage): Int {
        val x = image.width / QuarterWidth
        val barUnderDim = rgb(image, x, 0)
        var y = 0
        while (y < image.height && rgb(image, x, y) == barUnderDim) y++
        return y
    }

    private fun render(host: Host, dark: Boolean, case: String = "tall"): BufferedImage {
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                // Recorded inside the theme that is actually rendering, so the expected value and the
                // drawn one cannot come apart across a palette change.
                groundUnderTest = AppSurface.ground().toArgb() and 0xFFFFFF
                dockColourUnderTest = gistiDockColor().toArgb() and 0xFFFFFF
                when (host) {
                    Host.Inbox -> InboxUnderTest()
                    // Empty on both pages: this probe is about the dock's corners, and an agenda long
                    // enough to scroll would only add noise. The dock is mounted regardless.
                    Host.Calendar -> CalendarScreen(
                        todayState = TodayScreenState.Empty,
                        calendarState = CalendarState.Empty,
                        drawerState = null,
                        onTodayReminderClick = { _, _ -> },
                        onTodayRetry = {},
                        onCalendarIntent = {},
                        captureDockOpen = true,
                        captureEnabled = true,
                        onAddTaskRowClick = {},
                    )
                }
            }
        }
        val file = File("$ProbeDir/captureShoulder_${case}_${host.name.lowercase()}_${themeName(dark)}.png")
        file.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = file.path,
            // Forced Record on a path outside the golden directory: these are scratch frames, so
            // `verifyRoborazzi*` neither compares them nor wants them in git.
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        return ImageIO.read(file)
    }

    @Composable
    private fun InboxUnderTest() {
        InboxScreen(
            state = InboxScreenState.Content(
                pages = listOf(
                    InboxPage(
                        checklistId = 1L,
                        title = "Inbox",
                        isInbox = true,
                        // Two rows only: the list must not reach the dock, so the plane that ends at
                        // its top edge is the PAGE and the equality above is exact. Cards are inset
                        // by ScreenPaddingHorizontal anyway, so the sampled column is page at every
                        // list length — this just keeps the frame readable if it is ever opened by
                        // eye.
                        tasks = listOf(task("First task"), task("Second task")),
                    ),
                ),
            ),
            contentBottomPadding = 0.dp,
            onIntent = {},
            snackbarHostState = SnackbarHostState(),
            swallowRootBack = false,
            createDockOpen = true,
            onCreateDockDismiss = {},
        )
    }

    /** Which of the two screens that mount [QuickCaptureDock] the frame is rendered from. */
    private enum class Host { Inbox, Calendar }

    private fun task(text: String) = InboxTask(
        item = ChecklistFillItem(
            text = text,
            checked = false,
            priority = 0,
            templateItemId = "template-$text",
        )
    )

    private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

    private fun hex(rgb: Int): String = "#%06X".format(rgb)

    private fun themeName(dark: Boolean) = if (dark) "dark" else "light"

    private object NoopAppLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    private object NoopAnalyticsTracker : AnalyticsTracker {
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) {}
    }

    private companion object {
        /** Scratch frames, deliberately outside any checked-in golden directory. */
        const val ProbeDir = "build/capture-shoulder-probe"

        /** 1dp == 1px at 160dpi, which is what lets the offsets below be written as plain numbers. */
        const val SampleX = 2

        /** Rows above the wedge's bottom edge to sample: clear of the arc's anti-aliasing. */
        const val InsideWedge = 3

        /**
         * Rows above the wedge's bottom edge that are guaranteed to be OUTSIDE the dock.
         *
         * The wedge's bottom sits ~17px below the dock's top edge at [SampleX], so 30 clears the top
         * edge and its 1dp hairline with room to spare and lands on the dimmed page.
         */
        const val AboveTheDock = 30

        /**
         * A window too short to hold the app bar AND the dock at once — the keyboard case, expressed
         * without a keyboard.
         *
         * Robolectric never raises an IME: `WindowInsets.ime` is 0 in every frame this suite records,
         * so no golden and no pixel probe taken at [the class-level size][CaptureDockShoulderTest] can
         * reach the reported defect. It does not need to. The keyboard is not the cause — it is one
         * way of reaching the cause, which is `topBar + bottomBar > windowHeight`. Material3's
         * `Scaffold` then places the two bars OVERLAPPING (top bar at 0, bottom bar at
         * `height - bottomBarHeight`) and collapses the content slot to zero height at the top bar's
         * offset, i.e. BELOW the dock's top edge. A short window produces exactly that arithmetic,
         * deterministically, on the JVM.
         *
         * 260dp holds the dock (213dp at this width) with ~47dp of app bar showing above it: enough
         * for the shoulder probe to sample the page above the dock, and short enough that the app bar
         * (88dp with a subtitle) runs well past the dock's top edge. The guard in
         * [assertDimStopsAtTheDock] fails loudly if the dock ever grows or shrinks out of that band.
         */
        const val ShortWindow =
            "w412dp-h260dp-normal-notlong-notround-any-160dpi-keyshidden-nonav"

        /**
         * `TopAppBarDefaults.TopAppBarExpandedHeight`. The bar is TALLER than this on both hosts (a
         * subtitle on the Inbox, a status-bar inset wherever there is one), so a dock whose top edge
         * is above this line is proof the two bars overlap — and a dock below it is proof they do not.
         */
        const val AppBarMinHeight = 64

        /** Divides the frame width to reach a column clear of the app bar's text on both hosts. */
        const val QuarterWidth = 4

        /**
         * Rows below the dock's top edge to sample its surface: past the 1dp hairline, inside the 8dp
         * gap the dock keeps between that hairline and its first chip.
         */
        const val IntoTheDock = 4
    }
}
