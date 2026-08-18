package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.capture_dock_ai_entry_title
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRowSection
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * **Nothing** is painted as a band at the bottom of the v2 shell — not over the page above the bar, and
 * not across the quick-capture dock.
 *
 * ## What this file used to claim, and why it is the opposite now
 * It was `V2PlinthShadowOverDockTest`, and it pinned a shadow that had to exist in one state and be
 * gated off in the other: a 16dp `Transparent → black` overlay anchored to the bar's top edge (7.5%
 * light / 35% dark), drawn after the content column so a scrolling list was shaded by it. Its
 * counterexample cell asserted that WITHOUT the dock up the page above the bar was still shaded, so the
 * gate could not be satisfied by deleting the band.
 *
 * The owner deleted the band: "потом на главном экране убери тени от нижней навигации". The bottom
 * chrome is now separated from the page by tone plus the 28dp `SheetTop` corners and by nothing else
 * (see `AppSurface.bottomChrome`). So the counterexample is inverted here — the strip above the bar must
 * be FLAT — and the dock half survives unchanged in intent: it never wanted a band across the dock, and
 * it still does not.
 *
 * ## Why a run of rows and not two probes
 * A gradient's ends can be sampled at any two heights, so a two-probe check has to guess which two, and
 * this file has already been burned by exactly that: both of its probes once landed inside the same
 * surface and compared it with itself, green whatever the shadow did. A row-by-row run needs no guess
 * and catches a band of ANY height or alpha, at whatever offset a future regression puts it.
 *
 * ## Overlap with `V2BarShoulderFillTest`, deliberately kept
 * [noBandIsPaintedAboveTheBar_light] and `V2BarShoulderFillTest.theShoulderIsThePage_light` both scan
 * the strip above the bar. Different fixtures on purpose: that one renders the shell over a MAGENTA
 * sentinel window with a `LazyColumn` of cards, this one over the real `AppSurface.ground()` with an
 * empty page. A band introduced by the shell would show in both; one introduced by the theme or by a
 * card's own draw would show in only one.
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*V2BottomChromeNoBandTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2BottomChromeNoBandTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Top edge of the dock in px, taken from LAYOUT by the fixture.
     *
     * Replaces `image.height - BottomBarHeight`, which described a bar that is not on screen in this
     * state at all — see [theDockSurfaceCarriesNoBand_light].
     */
    private var dockTopPx: Int = 0

    /**
     * Top edge of the BAR in px — the height of the shell's content box, whose bottom edge is the bar's
     * top edge by construction (`Column { Box(weight(1f)) { content() }; bar }`).
     *
     * From layout rather than from `image.height - AppDimens.BottomBarHeight`, which this file used to
     * compute. 80dp is the bar's `defaultMinSize`, not its height: `NavigationBar` sizes each item from
     * its content, so a wrapped label makes the real bar taller and the constant then points somewhere
     * inside it — a ruler that slips silently in the one direction that still finds bar-coloured pixels.
     */
    private var barTopPx: Int = 0

    /** `AppSurface.ground()` for the current frame, lifted out of composition so a probe can NAME it. */
    private var groundUnderTest: Int = 0

    /**
     * With no dock up, the strip leading down to the bar's top edge must be the page at ONE value.
     *
     * This is the cell that used to assert the opposite ("the 16dp above the bar must stay darker than
     * the page — without a dock the plinth still needs its separator"). Inverting it rather than
     * deleting it is what keeps the removal falsifiable: restoring the band, at any height or alpha,
     * fails here.
     *
     * Light only, and now for a stronger reason than the old one. The old cell needed light because its
     * discriminator was "page vs shaded page" and the dark ramp moved luminance by a few points, which
     * is not a threshold anybody could justify. This one asserts EQUALITY of exact RGB, so it works in
     * either theme — dark is covered by `V2BarShoulderFillTest.theShoulderIsThePage_dark`, which runs
     * the same scan, and a second copy here would only mean two places to edit.
     */
    @Test
    fun noBandIsPaintedAboveTheBar_light() {
        val image = render(dark = false, captureOpen = false, name = "noBandAboveBar_light")

        assertTrue(
            "the fixture did not render a bar — barTopPx=$barTopPx",
            barTopPx in FlatStripDp until image.height,
        )
        val stripBelow = image.height - barTopPx
        assertTrue(
            "the measured bar top ($barTopPx) leaves ${stripBelow}px below it, less than the bar's " +
                "own ${MinBarHeightPx}px minimum — the ruler is inside the bar",
            stripBelow >= MinBarHeightPx,
        )

        val page = rgb(image, SampleX, barTopPx - FlatStripDp)
        assertEquals(
            "the strip above the bar starts at ${hex(page)} rather than the page's " +
                "${hex(groundUnderTest)} — the fixture is not rendering the page it claims to",
            hex(groundUnderTest),
            hex(page),
        )
        for (y in (barTopPx - FlatStripDp) until barTopPx) {
            val here = rgb(image, SampleX, y)
            assertEquals(
                "row ${barTopPx - y} above the bar reads ${hex(here)} against the page's " +
                    "${hex(page)} — a band is being painted into the bar's top edge, and the bottom " +
                    "chrome's separator is tone plus the 28dp corners only",
                hex(page),
                hex(here),
            )
        }
    }

    /**
     * No band of any kind may be painted across the dock's own surface.
     *
     * ## Why this is a run and not two samples
     * It used to take one sample two rows above the bar's top edge and one 32dp higher and require them
     * equal. That ruler was `image.height - BottomBarHeight`, i.e. "80dp up from the bottom is the
     * bar" — and with the dock up there is no bar down there any more (the shell hides the whole bottom
     * chrome in that state). Both samples landed inside the dock, so the test compared the dock with
     * itself: green whatever was painted, since the band it hunts would sit at a third row neither probe
     * looks at. That is the shape this project files under "unfalsifiable when failure is silent".
     *
     * A run down one column of the dock's own margin needs no ruler at all: the dock is opaque and flat
     * there by construction, so ANY variation in that column is paint that does not belong to it — at
     * whatever height a future regression decides to put it. The one thing to skip is the top corner: at
     * [SampleX] the `SheetTop` arc leaves the first ~20 rows outside the surface (they are the host's
     * scrim, checked by `V2BarShoulderFillTest`), so the run starts below it.
     *
     * ## Still worth keeping with the shadow gone
     * The band this was written against no longer exists, so today the cell passes on the absence of its
     * subject. It stays because the QUESTION is not about that band: the shell draws two overlays after
     * the content column (the raised AI button's positioning box, and whatever `overlayContent` the host
     * passes), and this is the only assertion that any of them stops short of the dock's surface. The
     * measured 7% ramp it originally caught — 241 → 233 across the bottom of the "Photo" pill row, the
     * dock reading as if it slid UNDER the bar — is what that class of mistake looks like.
     */
    @Test
    fun theDockSurfaceCarriesNoBand_light() {
        val image = render(dark = false, captureOpen = true, name = "noBandOverDock_light")

        assertTrue(
            "the fixture did not mount the dock — dockTopPx=$dockTopPx",
            dockTopPx in 1 until image.height - CornerSkipDp,
        )

        val first = dockTopPx + CornerSkipDp
        val reference = rgb(image, SampleX, first)
        for (y in first until image.height) {
            val here = rgb(image, SampleX, y)
            assertEquals(
                "the dock's own margin reads ${hex(here)} at row ${y - dockTopPx} below its top edge " +
                    "but ${hex(reference)} at row ${first - dockTopPx} — something is being painted " +
                    "across the dock by an overlay drawn after the content column",
                hex(reference),
                hex(here),
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Captured to a FILE and re-read rather than through `captureToImage()`: the dock focuses its input
     * on mount, a focused text field blinks its caret forever, and every `captureToImage()` /
     * `fetchSemanticsNode()` path first waits for an idle clock that therefore never arrives.
     * Roborazzi's capture does not wait, which is why the shell's other screenshot tests work at all.
     *
     * `RoborazziTaskType.Record` is forced and the path is outside the golden directory, so these stay
     * scratch frames: `verifyRoborazziAndroidHostTest` neither compares them nor wants them in git.
     * (Note for editors: a glob with a star-slash inside KDoc closes the comment early — the file this
     * one replaces lost a compile to exactly that.)
     */
    private fun render(dark: Boolean, captureOpen: Boolean, name: String): BufferedImage {
        RuntimeEnvironment.setQualifiers(Qualifiers)
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                V2NavigationShell(
                    selectedTab = V2Destination.Inbox,
                    onNavigate = {},
                    onOpenChat = {},
                    onOpenSettings = {},
                    onOpenUpdates = {},
                    showCreateFab = true,
                    onOpenCreate = {},
                    barVisible = true,
                    captureOpen = captureOpen,
                    overlayContent = null,
                    content = { PageUnderTest(captureOpen = captureOpen) },
                )
            }
        }
        val file = File("$ProbeDir/$name.png")
        file.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = file.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        return ImageIO.read(file)
    }

    /**
     * `Column { Box(weight(1f)) { page }; dock }` — the shape `AppScaffold` gives the two slots. The
     * dock therefore ends where the shell's content slot ends, which is where the production dock lands
     * too, and both rulers are taken from layout rather than guessed from a constant.
     */
    @Composable
    private fun PageUnderTest(captureOpen: Boolean) {
        // Recorded where the page is actually painted, so the value an assertion compares against and
        // the value the fixture draws with cannot come apart.
        groundUnderTest = AppSurface.ground().toArgb() and 0xFFFFFF
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    // THE RULER for the bar: this box fills the shell's content slot, so its bottom
                    // edge IS the bar's top edge, at any font scale or locale.
                    .onSizeChanged { barTopPx = it.height },
            )
            if (captureOpen) {
                QuickCaptureDock(
                    text = "",
                    onTextChange = {},
                    onAdd = {},
                    placeholder = "Add a task…",
                    modifier = Modifier.onGloballyPositioned {
                        dockTopPx = it.positionInRoot().y.toInt()
                    },
                    aboveInput = {
                        Text(
                            text = "Today   Tomorrow   Important",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = AppDimens.ScreenPaddingHorizontal,
                            ),
                        )
                    },
                    belowInput = {
                        // `SourceRowSection`, exactly as both hosts mount it — NOT the bare
                        // `SourceRow`. A fixture that keeps passing the bare row records a dock one
                        // text line shorter than the one the app draws, which is the very defect
                        // `SourceRowScreenshotTest`'s KDoc warns about. The heading comes from
                        // `strings.xml` for the same reason the hosts read it there: a literal here
                        // would measure the English line in every locale.
                        SourceRowSection(
                            title = stringResource(Res.string.capture_dock_ai_entry_title),
                            onSelect = {},
                        )
                    },
                )
            }
        }
    }

    private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

    private fun hex(rgb: Int): String = "#%06X".format(rgb)

    private companion object {
        /** 1dp == 1px at this density, which is what lets the offsets below be written in dp. */
        const val Qualifiers = "w412dp-h891dp"

        /** Scratch frames, deliberately outside the checked-in golden directory. */
        const val ProbeDir = "build/no-band-probe"

        /** The dock's left margin — its own surface, clear of the pills. */
        const val SampleX = 6

        /**
         * Rows to skip below the dock's top edge before the run starts.
         *
         * At [SampleX] the 28dp `SheetTop` arc closes ~17px down, so anything above that is the host's
         * scrim showing through the clipped shoulder — a different surface with its own test
         * (`V2BarShoulderFillTest.theCaptureDockShoulderIsTheDimmedPage_*`). 24 clears the arc and the
         * 1dp top hairline together.
         */
        const val CornerSkipDp = 24

        /**
         * How far above the bar's top edge the strip that must stay flat reaches. Was
         * `AppSurface.bottomChromeShadowHeight()` (16dp) back when it described a band that existed;
         * now it bounds the region a reintroduced one could occupy, and a scan taller than the band
         * still catches it.
         */
        const val FlatStripDp = 24

        /**
         * The bar's `defaultMinSize` — `NavigationBarHeight` in material3, mirrored as
         * [AppDimens.BottomBarHeight]. A FLOOR, never the height. Used only to catch a ruler that has
         * slipped inside the bar.
         */
        val MinBarHeightPx = AppDimens.BottomBarHeight.value.toInt()
    }
}
