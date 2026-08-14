package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
     * The dock's own surface two rows above the bar must be the same tone as the dock 32dp higher.
     * Any difference there is paint that does not belong to the dock.
     */
    @Test
    fun theDockSurfaceIsNotShadedByThePlinth_light() {
        val image = render(dark = false, captureOpen = true, name = "dockOverBar_light")
        val barTop = barTopEdgeInLight(image)

        val atTheEdge = luminance(image, SampleX, barTop - 2)
        val wellAbove = luminance(image, SampleX, barTop - 2 - ShadowBandDp * 2)

        assertEquals(
            "the dock's surface reads $atTheEdge two rows above the bar but $wellAbove further up " +
                "— the plinth shadow is being painted over the dock",
            wellAbove,
            atTheEdge,
        )
    }

    /**
     * The other half of the claim, and the reason this cannot be satisfied by deleting the shadow:
     * with no dock up the band above the bar MUST still be shaded, or the plinth loses the separator
     * it exists for.
     *
     * Light only — the discriminator is "page vs ink", a ~200-point step in light; in dark the token
     * is an opaque 1dp hairline rather than a gradient, and that frame is checked by eye
     * (`compactBar_412dp_dark_captureOpen_realDock.png`).
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

    @Composable
    private fun PageUnderTest(captureOpen: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            if (captureOpen) {
                // Bottom-aligned inside the content box, which is where the production dock lands:
                // it sits in `AppScaffold`'s bottomBar slot and that scaffold fills this same box, so
                // the dock's bottom edge IS the plinth's top edge either way.
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
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
    }

    private companion object {
        /** 1dp == 1px at this density, which is what lets the offsets below be written in dp. */
        const val Qualifiers = "w412dp-h891dp"

        /** Scratch frames, deliberately outside the checked-in golden directory. */
        const val ProbeDir = "build/plinth-probe"

        /** The dock's left margin — its own surface, clear of the pills. */
        const val SampleX = 6

        /** `AppSurface.bottomChromeShadowHeight()`. */
        const val ShadowBandDp = 16

        /**
         * Was a luminance threshold used to find the bar by colour. Deleted rather than re-tuned —
         * see [barTopEdgeInLight] for why a colour-based detector cannot work now that the dock and
         * the bar share one tone, and why the edge is taken from geometry instead.
         */
    }
}
