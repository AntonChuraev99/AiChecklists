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

    private fun assertShoulderIsDimmed(host: Host, dark: Boolean) {
        val image = render(host, dark)
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

    private fun render(host: Host, dark: Boolean): BufferedImage {
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                // Recorded inside the theme that is actually rendering, so the expected value and the
                // drawn one cannot come apart across a palette change.
                groundUnderTest = AppSurface.ground().toArgb() and 0xFFFFFF
                when (host) {
                    Host.Inbox -> InboxUnderTest()
                    // Empty on both pages: this probe is about the dock's corners, and an agenda long
                    // enough to scroll would only add noise. The dock is mounted regardless.
                    Host.Calendar -> CalendarScreen(
                        todayState = TodayScreenState.Empty,
                        calendarState = CalendarState.Empty,
                        drawerState = null,
                        onTodayReminderClick = { _, _ -> },
                        onTodayCreateChecklistClick = {},
                        onTodayRetry = {},
                        onCalendarIntent = {},
                        captureDockOpen = true,
                        captureEnabled = true,
                        onAddTaskRowClick = {},
                    )
                }
            }
        }
        val file = File("$ProbeDir/captureShoulder_${host.name.lowercase()}_${themeName(dark)}.png")
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
    }
}
