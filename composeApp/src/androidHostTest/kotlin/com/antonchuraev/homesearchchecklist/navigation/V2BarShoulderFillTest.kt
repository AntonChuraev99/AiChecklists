package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
 * The two corners [com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens.SheetTop]
 * clips off the v2 bar must be PAINTED, not left as a hole through the composition.
 *
 * ## The defect
 * The bar is the last child of the shell's `Column`; the content box above it ends exactly at its top
 * edge, so nothing in the app is behind it. `Modifier.clip(SheetTop)` therefore cut two 28dp
 * quarter-discs out of the app's bottom edge and whatever the PLATFORM paints under the Compose
 * surface showed through them. Measured on the goldens recorded before this fix, at `x = 2`, two rows
 * below the bar's top edge:
 *
 * | Frame | Shoulder | Chrome beside it | Page above it |
 * |---|---|---|---|
 * | `compactBar_412dp_light` | `#FAFAFA` | `#DEDCD6` | `#FBFAF8` |
 * | `compactBar_412dp_dark` | `#FAFAFA` | `#1A1C20` | `#121317` |
 *
 * The dark row is the tell: a near-white wedge (L\* 98.0) beside a chrome at L\* 10.2, i.e. ΔL\* +88
 * on a screen whose page sits at L\* 5.9 — the single largest tonal step in the frame. `#FAFAFA` is not a
 * colour this design system owns anywhere — it is Robolectric's window backdrop. On a device the same
 * hole resolves to `android:windowBackground` (cream) and reads as the bright nick the owner reported
 * from a Pixel 9; on wasmJs there is no `windowBackground` behind the canvas at all.
 *
 * ## Why the backdrop here is MAGENTA
 * The bug is "an unowned colour shows through", so the test must not be able to pass by accident when
 * the unowned colour happens to resemble the right one — which is exactly how this survived to a
 * device: `#FAFAFA` against a `#FBFAF8` page is ΔL\* 0.3, invisible in any screenshot review, and the
 * earlier white bar (`#FFFFFF`) hid it completely. Painting the window [Sentinel] makes a hole
 * unmissable and makes the assertion falsifiable: before the fix these tests fail with magenta in the
 * shoulders, after it no sentinel pixel survives anywhere in the bar's footprint.
 *
 * ## What is asserted beyond "not a hole"
 * A filled shoulder could still be wrong — a bright fill would reproduce the reported symptom with a
 * predictable colour. So the run down the screen's edge must also be MONOTONIC: page → shadow ramp →
 * shoulder → chrome, with the shoulder no lighter than the shadow band that ends on it
 * ([shoulderContinuesTheShadowBand]) and never lighter than the unshaded page. Before the fix the
 * shoulder was the brightest pixel in the whole region — brighter even than the page — which is what
 * made it read as backdrop rather than as depth.
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*V2BarShoulderFillTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2BarShoulderFillTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `AppSurface.card()` for the theme the current frame was rendered in, lifted OUT of composition
     * so an assertion can name the colour it expects instead of comparing two pixels of the frame
     * against each other. It has to come from composition rather than from a literal: the accessor
     * branches on `LocalIsDarkTheme`, and a hardcoded `#FFFFFF` would keep passing after a palette
     * change while the fixture silently stopped drawing cards.
     */
    private var cardUnderTest: Int = 0

    // ── The hole itself, across the matrix ───────────────────────────────────

    @Test
    fun noWindowBackdropShowsThroughTheBar_412dp_light() =
        assertNoSentinelInTheBarFootprint(Phone412, dark = false, cardsUnderTheBar = true)

    @Test
    fun noWindowBackdropShowsThroughTheBar_412dp_dark() =
        assertNoSentinelInTheBarFootprint(Phone412, dark = true, cardsUnderTheBar = true)

    @Test
    fun noWindowBackdropShowsThroughTheBar_360dp_light() =
        assertNoSentinelInTheBarFootprint(Phone360, dark = false, cardsUnderTheBar = true)

    @Test
    fun noWindowBackdropShowsThroughTheBar_360dp_dark() =
        assertNoSentinelInTheBarFootprint(Phone360, dark = true, cardsUnderTheBar = true)

    /**
     * The short-list half of the matrix: no card reaches the bar, so the plane that ends at its top
     * edge is the bare page. The shoulder must not change with it — that is the whole point of
     * painting it rather than letting it inherit whatever stopped above.
     */
    @Test
    fun noWindowBackdropShowsThroughTheBar_412dp_light_shortList() =
        assertNoSentinelInTheBarFootprint(Phone412, dark = false, cardsUnderTheBar = false)

    /**
     * With the capture dock up the bar is square ([V2SplitNavigationBar]'s `roundedTop = false`), so
     * there is no shoulder to fill — and no hole either. Pinned so a future change cannot "fix" the
     * corners by rounding them in this state too and re-open the hole under the dock.
     */
    @Test
    fun noWindowBackdropShowsThroughTheBar_412dp_light_captureOpen() =
        assertNoSentinelInTheBarFootprint(Phone412, dark = false, cardsUnderTheBar = true, captureOpen = true)

    // ── The shoulder is the right colour, not merely some colour ─────────────

    /**
     * Hypothesis this closes explicitly: "the corners show the white list CARD, because the content
     * scrolls under the bar". They do not, and they must not — a shoulder whose colour depends on what
     * the user scrolled to is the same defect wearing a different value.
     *
     * The frame is rendered with a solid column of `AppSurface.card()` rows clipped at the bar's edge,
     * so a card really is the last thing painted above the bar. The shoulder must still read as the
     * shadowed page.
     */
    @Test
    fun theShoulderIsNotTheCardScrolledUnderIt_light() {
        val image = render(Phone412, dark = false, cardsUnderTheBar = true, name = "cardUnderBar_light")
        val barTop = barTopEdge(image)

        // Sampled ABOVE the 16dp shadow band, not two rows above the bar. Inside the band the card is
        // progressively darkened, so a pixel at `barTop - 2` is not the card's colour at all — the old
        // probe took that shaded pixel and called it "the card above it" in its own failure message.
        val cardAboveTheBar = rgb(image, SampleInset, barTop - ShadowBandDp - 4)
        val shoulder = rgb(image, SampleInset, barTop + 2)

        // The card really is the plane ending at the bar — otherwise this test proves nothing.
        //
        // Against the KNOWN card colour, not against a pixel taken INSIDE the bar. The old guard read
        // "brighter than the chrome", which the bare page (`#FBFAF8`, lum 250) clears exactly as easily
        // as a card (`#FFFFFF`, 255) — so it passed whether or not any card ever reached the bar, i.e.
        // it never checked the premise this test's name rests on. `cardsUnderTheBar = false` must be
        // able to FAIL this line; with the old one it could not.
        assertEquals(
            "expected a card row above the bar, found ${hex(cardAboveTheBar)} — the fixture no " +
                "longer reproduces the case it exists for",
            hex(cardUnderTest),
            hex(cardAboveTheBar),
        )
        assertTrue(
            "the shoulder reads ${hex(shoulder)}, the card above it ${hex(cardAboveTheBar)} — the " +
                "clipped corner is showing whatever the list scrolled to",
            shoulder != cardAboveTheBar,
        )
    }

    /**
     * The shoulder must meet the shadow band with no step: it takes that gradient's terminal value, so
     * the band's last row and the shoulder are the same tone within a rounding step. A brighter
     * shoulder is the reported defect; a much darker one is a visible arc-shaped stripe.
     *
     * Both themes, because they land on OPPOSITE sides of the chrome — light's page is lighter than
     * the bottom chrome, dark's is darker — and only a run measured down the edge catches a regression
     * that is correct in one and inverted in the other.
     */
    @Test
    fun shoulderContinuesTheShadowBand_light() = assertMonotonicEdgeRun(dark = false)

    @Test
    fun shoulderContinuesTheShadowBand_dark() = assertMonotonicEdgeRun(dark = true)

    // ── shared assertions ────────────────────────────────────────────────────

    private fun assertMonotonicEdgeRun(dark: Boolean) {
        val image = render(
            Phone412,
            dark = dark,
            cardsUnderTheBar = false,
            name = "edgeRun_${if (dark) "dark" else "light"}",
        )
        val barTop = barTopEdge(image)

        val page = luminance(image, SampleInset, barTop - ShadowBandDp - 8)
        val bandEnd = luminance(image, SampleInset, barTop - 1)
        val shoulder = luminance(image, SampleInset, barTop + 2)

        assertTrue(
            "the shadow band must darken the page before the bar ($bandEnd vs $page)",
            bandEnd <= page,
        )
        assertTrue(
            "the shoulder ($shoulder) is brighter than the unshaded page ($page) — a clipped corner " +
                "may never be the brightest thing at the bottom of the screen",
            shoulder <= page,
        )
        assertTrue(
            "the shoulder ($shoulder) does not continue the shadow band it meets ($bandEnd) — the " +
                "band ramps to exactly the shoulder's tone, so any step here is a seam",
            kotlin.math.abs(shoulder - bandEnd) <= BandSeamTolerance,
        )
    }

    /**
     * Scans the bar's whole footprint — every row from its top edge to the bottom of the window,
     * across the full width — rather than probing the two corners by their computed arc geometry.
     *
     * A corner probe has to know the radius, so it silently stops testing anything the day the radius
     * changes. A scan asks the question that actually matters ("is any part of this surface a hole?")
     * and keeps asking it whatever shape the clip takes.
     */
    private fun assertNoSentinelInTheBarFootprint(
        qualifiers: String,
        dark: Boolean,
        cardsUnderTheBar: Boolean,
        captureOpen: Boolean = false,
    ) {
        val name = buildString {
            append(if (qualifiers == Phone412) "412" else "360")
            append(if (dark) "_dark" else "_light")
            if (!cardsUnderTheBar) append("_shortList")
            if (captureOpen) append("_captureOpen")
        }
        val image = render(qualifiers, dark, cardsUnderTheBar, captureOpen, "hole_$name")
        val barTop = barTopEdge(image)
        val sentinel = Sentinel.toArgb() and 0xFFFFFF

        var firstHit: String? = null
        var hits = 0
        for (y in barTop until image.height) {
            for (x in 0 until image.width) {
                if (rgb(image, x, y) == sentinel) {
                    hits++
                    if (firstHit == null) firstHit = "($x, ${y - barTop} below the bar's top edge)"
                }
            }
        }

        assertEquals(
            "$hits pixels of the window backdrop show through the bar, first at $firstHit — the " +
                "clipped corners are a hole, not a surface (frame: $name)",
            0,
            hits,
        )
    }

    // ── harness ──────────────────────────────────────────────────────────────

    /**
     * Captured to a FILE and re-read rather than through `captureToImage()`: with the capture dock up
     * the real dock focuses its input on mount, a focused field blinks its caret forever, and every
     * `captureToImage()` / `fetchSemanticsNode()` path first waits for an idle clock that therefore
     * never arrives. Roborazzi's capture does not wait.
     *
     * `RoborazziTaskType.Record` is forced and the path is outside the golden directory, so these stay
     * scratch frames: `verifyRoborazziAndroidHostTest` neither compares them nor wants them in git.
     */
    private fun render(
        qualifiers: String,
        dark: Boolean,
        cardsUnderTheBar: Boolean,
        captureOpen: Boolean = false,
        name: String,
    ): BufferedImage {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                // The window, painted a colour the design system never uses. Anything that reaches
                // this Box is a hole in everything drawn over it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Sentinel),
                ) {
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
                        content = { PageUnderTest(cardsUnderTheBar) },
                    )
                }
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
     * A `LazyColumn` rather than a plain `Column`: it CLIPS to its bounds, so the last card is cut off
     * exactly at the bar's top edge the way a real scrolled list is. A `Column` would overflow and
     * draw its rows straight over the bar, which would hide the very region under test.
     */
    @Composable
    private fun PageUnderTest(cardsUnderTheBar: Boolean) {
        val rows = if (cardsUnderTheBar) 40 else 2
        // Recorded here, where the rows are actually painted, so the value an assertion compares
        // against and the value the fixture draws with cannot come apart.
        cardUnderTest = AppSurface.card().toArgb() and 0xFFFFFF
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items((0 until rows).toList()) {
                    // Flush, unspaced card rows: whatever row lands against the bar, the plane ending
                    // at the bar's top edge is card-coloured. No gap can put the page there by luck.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .background(AppSurface.card()),
                    )
                }
            }
        }
    }

    /**
     * Top edge of the bar, from GEOMETRY rather than colour — the same rule
     * [V2PlinthShadowOverDockTest] had to adopt: a probe asserting something ABOUT a colour must not
     * locate its subject BY that colour, or re-tuning the chrome silently moves the ruler.
     *
     * Robolectric reports zero window insets, so the bar is exactly [AppDimens.BottomBarHeight]; the
     * qualifiers pin 1dp == 1px; labels are single-line at fontScale 1.0, so the bar does not grow.
     */
    private fun barTopEdge(image: BufferedImage): Int =
        image.height - AppDimens.BottomBarHeight.value.toInt()

    private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

    private fun luminanceOf(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (r * 0.299f + g * 0.587f + b * 0.114f).toInt()
    }

    private fun luminance(image: BufferedImage, x: Int, y: Int): Int = luminanceOf(rgb(image, x, y))

    private fun hex(rgb: Int): String = "#%06X".format(rgb)

    private companion object {
        /** 1dp == 1px at these densities, which is what lets every offset below be written in dp. */
        const val Phone412 = "w412dp-h891dp"
        const val Phone360 = "w360dp-h640dp"

        /** Scratch frames, deliberately outside the checked-in golden directory. */
        const val ProbeDir = "build/shoulder-probe"

        /**
         * The window colour. Must be one the app never paints, in either theme — a hole filled with a
         * plausible near-white is precisely how this defect reached a device.
         */
        val Sentinel = Color(0xFFFF00FF)

        /** Clear of the 28dp corner arc at every row this test samples, and clear of the edge itself. */
        const val SampleInset = 2

        /** `AppSurface.bottomChromeShadowHeight()`. */
        const val ShadowBandDp = 16

        /**
         * The band's last drawn row sits at 15.5/16 of the gradient's alpha while the shoulder takes
         * the full value, so the two differ by a fraction of one 8-bit step even when correct.
         */
        const val BandSeamTolerance = 2

        val RowHeight = 64.dp
    }
}
