package com.antonchuraev.homesearchchecklist.navigation

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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRow
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
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
 * The bar's plinth shadow must not be painted ACROSS the quick-capture dock.
 *
 * The shadow is an overlay `Box` anchored to the bar's top edge and drawn AFTER the content column,
 * so it shades the bottom `bottomChromeShadowHeight` (16dp) of whatever the shell is hosting. That is
 * exactly right for a list — the rows scroll under the bar and the gradient is what sells it. It is
 * exactly wrong for the capture dock, which is a RAISED plane covering that whole band: shading its
 * bottom edge says "this dock is sliding under the bar", which is the opposite of what it is.
 *
 * Measured before the fix, 412dp light, one column of the dock's own surface:
 *   32dp above the bar   255
 *   2dp above the bar    237
 * and inside the "Photo" pill the fill ramped 241 → 233 over its last 7 rows — i.e. a smudge across
 * the bottom of the whole AI row.
 *
 * A PIXEL assertion rather than a golden on purpose: the effect is a ~3% tonal ramp, invisible in a
 * PNG at review size. A golden would happily record the smudge and then call it the reference.
 *
 * ## Still needed after the bar started hiding under the dock
 * The shell now takes the whole bottom chrome off screen while capture is up, so the shadow is gated
 * off in that state twice over. This test is what makes the gate falsifiable: without it the overlay
 * would be positioned by `padding(bottom = barHeight)` against a `barHeight` left over from the last
 * frame that HAD a bar — i.e. a 16dp band ~80dp up from the bottom, straight across the middle of the
 * dock, in a state no golden of a bar would ever show.
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*V2PlinthShadowOverDockTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2PlinthShadowOverDockTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Top edge of the dock in px, taken from LAYOUT by the fixture.
     *
     * Replaces `image.height - BottomBarHeight`, which described a bar that is not on screen in this
     * state at all — see [theDockSurfaceIsNotShadedByThePlinth_light].
     */
    private var dockTopPx: Int = 0

    /**
     * No band of any kind may be painted across the dock's own surface.
     *
     * ## Why this is now a run and not two samples
     * It used to take one sample two rows above the bar's top edge and one 32dp higher and require
     * them equal. That ruler was `image.height - BottomBarHeight`, i.e. "80dp up from the bottom is
     * the bar" — and with the dock up there is no bar down there any more (the shell hides the whole
     * bottom chrome in that state). Both samples landed inside the dock, so the test compared the dock
     * with itself: green whatever the shadow did, since the band it is hunting for would sit at a
     * third row neither probe looks at. That is the shape this project files under "unfalsifiable when
     * failure is silent".
     *
     * A run down one column of the dock's own margin needs no ruler at all: the dock is opaque and
     * flat there by construction, so ANY variation in that column is paint that does not belong to
     * it — at whatever height a future regression decides to put it. The one thing to skip is the
     * top corner: at [SampleX] the `SheetTop` arc leaves the first ~20 rows outside the surface (they
     * are the host's scrim, checked by `V2BarShoulderFillTest`), so the run starts below it.
     */
    @Test
    fun theDockSurfaceIsNotShadedByThePlinth_light() {
        val image = render(dark = false, captureOpen = true, name = "dockOverBar_light")

        assertTrue(
            "the fixture did not mount the dock — dockTopPx=$dockTopPx",
            dockTopPx in 1 until image.height - CornerSkipDp,
        )

        val first = dockTopPx + CornerSkipDp
        val reference = luminance(image, SampleX, first)
        for (y in first until image.height) {
            val here = luminance(image, SampleX, y)
            assertEquals(
                "the dock's own margin reads $here at row ${y - dockTopPx} below its top edge but " +
                    "$reference at row ${first - dockTopPx} — something is being painted across the " +
                    "dock (the plinth shadow is drawn after the content column and shades whatever " +
                    "occupies the band it is anchored to)",
                reference,
                here,
            )
        }
    }

    /**
     * The other half of the claim, and the reason this cannot be satisfied by deleting the shadow:
     * with no dock up the band above the bar MUST still be shaded, or the plinth loses the separator
     * it exists for.
     *
     * Light only — the discriminator is "page vs shaded page", and in light the ramp is a 7.5% black
     * gradient over cream, which reads clearly in a luminance sample. The dark ramp is 35% black over
     * a near-black page, i.e. a few points of luminance, so it is judged on the recorded frame
     * (`compactBar_412dp_dark.png`) rather than pinned here at a tolerance nobody could justify.
     */
    @Test
    fun withNoDockUp_thePageIsStillShadedAboveTheBar() {
        val image = render(dark = false, captureOpen = false, name = "noDockOverBar_light")
        val barTop = barTopEdgeInLight(image)

        val insideTheBand = luminance(image, SampleX, barTop - 2)
        val aboveTheBand = luminance(image, SampleX, barTop - 2 - ShadowBandDp)

        assertTrue(
            "the 16dp above the bar must stay darker than the page ($insideTheBand vs " +
                "$aboveTheBand) — without a dock the plinth still needs its separator",
            insideTheBand < aboveTheBand,
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Captured to a FILE and re-read rather than through `captureToImage()`: the dock focuses its
     * input on mount, a focused text field blinks its caret forever, and every `captureToImage()` /
     * `fetchSemanticsNode()` path first waits for an idle clock that therefore never arrives.
     * Roborazzi's capture does not wait, which is why the shell's other screenshot tests work at all.
     *
     * `RoborazziTaskType.Record` is forced and the path is outside the golden directory, so these
     * stay scratch frames: `verifyRoborazziAndroidHostTest` neither compares them nor wants them in
     * git. (Note for editors: a glob with a star-slash inside KDoc closes the comment early — this
     * file lost a compile to exactly that.)
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
     * Top edge of the bar, in pixels, derived from GEOMETRY rather than from colour.
     *
     * It used to walk up from the bottom looking for the first row whose mean luminance said "page,
     * not ink" (threshold 150). That worked only while the bar was the ink plinth: page ≈ 251 and bar
     * ≈ 50 sit either side of 150, so the crossing was the bar's edge. The bar is now the shared
     * bottom-chrome grey at ≈ 219 — also above 150 — so the search matched the very first row it
     * looked at and reported the bar's top edge at the BOTTOM of the image. Both probes then sampled
     * inside the bar and compared it with itself: `219 vs 219`, a red test with a green product (the
     * band is intact — measured on the recorded frame at x=170, the page ramps 98.3 → 92.0 L\* over
     * exactly 16px before the chrome starts).
     *
     * The deeper problem is that a probe asserting something ABOUT a colour must not first locate its
     * subject BY that colour — re-tuning the chrome then silently moves the ruler. Worse now that the
     * capture dock shares the bar's tone: with the dock up there is no colour boundary at the bar's
     * top edge at all, by design, so no colour-based detector can exist.
     *
     * Geometry has neither problem. Robolectric reports zero window insets, so the bar is exactly
     * [AppDimens.BottomBarHeight]; [Qualifiers] pins 1dp == 1px; and the labels are single-line at
     * fontScale 1.0, so the bar does not grow.
     */
    private fun barTopEdgeInLight(image: BufferedImage): Int =
        image.height - AppDimens.BottomBarHeight.value.toInt()

    private fun luminance(image: BufferedImage, x: Int, y: Int): Int {
        val rgb = image.getRGB(x, y)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 0.299f + g * 0.587f + b * 0.114f).toInt()
    }

    /**
     * `Column { Box(weight(1f)) { page }; dock }` — the shape `AppScaffold` gives the two slots. The
     * dock therefore ends where the shell's content slot ends, which is where the production dock
     * lands too, and [dockTopPx] is taken from its layout rather than guessed from a constant.
     */
    @Composable
    private fun PageUnderTest(captureOpen: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            Box(modifier = Modifier.weight(1f))
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
                    belowInput = { SourceRow(onSelect = {}) },
                )
            }
        }
    }

    private companion object {
        /** 1dp == 1px at this density, which is what lets the offsets below be written in dp. */
        const val Qualifiers = "w412dp-h891dp"

        /** Scratch frames, deliberately outside the checked-in golden directory. */
        const val ProbeDir = "build/plinth-probe"

        /** The dock's left margin — its own surface, clear of the pills. */
        const val SampleX = 6

        /**
         * Rows to skip below the dock's top edge before the run starts.
         *
         * At [SampleX] the 28dp `SheetTop` arc closes ~17px down, so anything above that is the
         * host's scrim showing through the clipped shoulder — a different surface with its own test
         * (`V2BarShoulderFillTest.theCaptureDockShoulderIsTheDimmedPage_*`). 24 clears the arc and the
         * 1dp top hairline together.
         */
        const val CornerSkipDp = 24

        /** `AppSurface.bottomChromeShadowHeight()`. */
        const val ShadowBandDp = 16

        /**
         * Was a luminance threshold used to find the bar by colour. Deleted rather than re-tuned —
         * see [barTopEdgeInLight] for why a colour-based detector cannot work now that the dock and
         * the bar share one tone, and why the edge is taken from geometry instead.
         */
    }
}
