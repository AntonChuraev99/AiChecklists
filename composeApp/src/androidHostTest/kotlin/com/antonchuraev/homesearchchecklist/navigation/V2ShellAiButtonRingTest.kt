package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.nav_chat_fab_content_description
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
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
 * The raised centre AI button is separated from the bar by a RING of the page colour — and by nothing
 * else.
 *
 * ## What this replaces
 * M3 gives a `FloatingActionButton` a 6dp shadow by default, and that is what the button shipped
 * with. The owner removed it from the device along with the rest of the bottom chrome's shadows
 * ("потом нужно убрать тени в нижней навигации под кнопкой по центру", 2026-09-03), which left the
 * circle painted flat onto the bar with no separation at all where it crosses the bar's top edge.
 * The replacement is a filled circle 3dp larger drawn under it in [AppSurface.bottomChromeShoulder]
 * — the page colour. Above the bar it is invisible (it is the colour of what is behind it); across
 * the bar's edge it cuts a crisp notch.
 *
 * ## One probe, two failures
 * The probe reads the pixels of that annulus at the button's vertical middle, where it sits INSIDE
 * the bar, and requires the page colour exactly. It fails if
 *  - the ring is removed or shrinks — those pixels become the bar's chrome, and
 *  - a shadow comes back in any of the FOUR elevation slots — the ring is what a shadow would be
 *    drawn onto, so any darkening of it is a value other than the page's.
 *
 * The second is the reason all four slots are zeroed rather than just `defaultElevation`: leaving
 * `pressedElevation` at 6dp brings the shadow back under the finger, i.e. in the exact frame the
 * owner would be looking at while judging whether it is gone.
 *
 * Run:
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*V2ShellAiButtonRingTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2ShellAiButtonRingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** `AppSurface.bottomChromeShoulder()` for the frame, lifted out of composition so a probe can NAME it. */
    private var shoulderUnderTest: Int = 0

    /** `AppSurface.bottomChrome()` for the frame — the value the annulus reads WITHOUT the ring. */
    private var chromeUnderTest: Int = 0

    /** `AppSurface.card()` for the frame — what a list row behind the overhang is painted with. */
    private var cardUnderTest: Int = 0

    @Test
    fun theRingAroundTheAiButtonIsThePage_light() = assertRingIsThePage(dark = false)

    @Test
    fun theRingAroundTheAiButtonIsThePage_dark() = assertRingIsThePage(dark = true)

    /**
     * With a CARD scrolled under the raised half, the ring must stop at the bar's top edge.
     *
     * ## The defect this is written against
     * The ring is filled with [AppSurface.bottomChromeShoulder] — the PAGE colour — on the reasoning
     * that above the bar it is "the colour of what is behind it" and therefore invisible. That holds
     * only over bare page. The button overhangs the hosted SCREEN, and a screen scrolls cards under
     * it: `AppSurface.card()` is `#1A1C20` in dark against the page's `#121317`, so an unclipped ring
     * painted a 3dp page-coloured crescent over the card — a cut-out punched through the list, which
     * is the opposite of invisible.
     *
     * Dark only, deliberately: this is the theme where card and page differ enough to be judged on a
     * named colour rather than on a threshold (light's `#FFFFFF` card against a `#FBFAF8` page is
     * ΔL\* +1.7, which no probe should be asked to arbitrate).
     *
     * Two probes, and the second is what stops the fix from being "delete the ring": above the edge
     * the pixel must be the CARD, below it the PAGE.
     */
    @Test
    fun theRingStopsAtTheBarsTopEdge_whenACardIsUnderTheOverhang_dark() {
        var chatLabel = ""
        RuntimeEnvironment.setQualifiers(Qualifiers)
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            AppTheme(darkTheme = true) {
                shoulderUnderTest = AppSurface.bottomChromeShoulder().toArgb() and 0xFFFFFF
                chromeUnderTest = AppSurface.bottomChrome().toArgb() and 0xFFFFFF
                cardUnderTest = AppSurface.card().toArgb() and 0xFFFFFF
                V2NavigationShell(
                    selectedTab = V2Destination.Inbox,
                    onNavigate = {},
                    onOpenChat = {},
                    onOpenSettings = {},
                    onOpenUpdates = {},
                    showCreateFab = false,
                    onOpenCreate = {},
                    barVisible = true,
                    captureOpen = false,
                    overlayContent = null,
                    // A card filling the page, i.e. the state a scrolled list is in behind the
                    // button's raised half.
                    content = { CardPageUnderTest() },
                )
            }
        }
        composeTestRule.waitForIdle()

        val gap = composeTestRule.onNodeWithContentDescription(chatLabel)
            .fetchSemanticsNode().boundsInRoot
        val image = render(name = "aiButtonRing_cardUnderOverhang_dark")

        val centreX = gap.center.x.toInt()
        val barTop = gap.top.toInt()
        val radiusPx = (V2ShellMetrics.AiButtonSize.value / 2f).toInt()

        // ABOVE the bar's edge, in the ring's top crescent: the circle's half-width at
        // ShadowProbeRisePx up is ~23px, the ring's ~26.6px, so the button's radius (28) is clear of
        // both — this is the pixel the unclipped ring bled the page colour onto.
        val aboveY = barTop - AboveEdgeProbePx
        listOf(centreX - radiusPx, centreX + radiusPx).forEach { x ->
            val here = rgb(image, x, aboveY)
            assertEquals(
                "($x, $aboveY) is ABOVE the bar's top edge with a card behind it, and reads " +
                    "${hex(here)}" +
                    (if (here == shoulderUnderTest) " — that is the PAGE: the ring is painting " +
                        "over the list and reads as a cut-out punched through it." else "") +
                    ". The ring must be clipped to below the bar's edge",
                hex(cardUnderTest),
                hex(here),
            )
        }

        // BELOW it the ring is still the page — otherwise "clip it" would be satisfied by deleting it.
        val belowY = barTop +
            (V2ShellMetrics.AiButtonSize.value / 2f - V2ShellMetrics.AiButtonOverhang.value).toInt()
        val ringOffset = radiusPx + 1
        listOf(centreX - ringOffset, centreX + ringOffset).forEach { x ->
            assertEquals(
                "($x, $belowY) is inside the bar, where the ring's whole job is to notch it",
                hex(shoulderUnderTest),
                hex(rgb(image, x, belowY)),
            )
        }
    }

    private fun assertRingIsThePage(dark: Boolean) {
        var chatLabel = ""
        RuntimeEnvironment.setQualifiers(Qualifiers)
        composeTestRule.setContent {
            chatLabel = stringResource(Res.string.nav_chat_fab_content_description)
            AppTheme(darkTheme = dark) {
                shoulderUnderTest = AppSurface.bottomChromeShoulder().toArgb() and 0xFFFFFF
                chromeUnderTest = AppSurface.bottomChrome().toArgb() and 0xFFFFFF
                V2NavigationShell(
                    selectedTab = V2Destination.Inbox,
                    onNavigate = {},
                    onOpenChat = {},
                    onOpenSettings = {},
                    onOpenUpdates = {},
                    showCreateFab = false,
                    onOpenCreate = {},
                    barVisible = true,
                    captureOpen = false,
                    overlayContent = null,
                    content = { PageUnderTest() },
                )
            }
        }
        composeTestRule.waitForIdle()

        // The AI hit area spans the bar's 76dp gap and is bottom-aligned to the bar, so its TOP edge
        // is the bar's top edge and its centre x is the button's. Read from layout rather than from
        // `image.height - BottomBarHeight`: `NavigationBar` sizes itself from its content, so the
        // constant slips the moment a label wraps.
        val gap = composeTestRule.onNodeWithContentDescription(chatLabel)
            .fetchSemanticsNode().boundsInRoot
        val image = render(name = "aiButtonRing_${if (dark) "dark" else "light"}")

        assertTrue(
            "the fixture did not render the bar — gap bounds $gap",
            gap.top > 0f && gap.center.x > 0f,
        )

        // 1dp == 1px at this qualifier's density. The button overhangs the bar by AiButtonOverhang,
        // so its vertical middle sits (radius − overhang) BELOW the bar's top edge — inside the bar,
        // which is the only place the ring is meant to be visible at all.
        val centreX = gap.center.x.toInt()
        val centreY = gap.top.toInt() +
            (V2ShellMetrics.AiButtonSize.value / 2f - V2ShellMetrics.AiButtonOverhang.value).toInt()
        val radiusPx = (V2ShellMetrics.AiButtonSize.value / 2f).toInt()

        // ONE pixel outside the circle, on both sides — inside the 3px annulus and clear of the
        // antialiasing on both of its rims (measured: the circle's edge lands on radius−1, the
        // ring's on radius+2).
        val ringOffset = radiusPx + 1
        listOf(centreX - ringOffset, centreX + ringOffset).forEach { x ->
            val here = rgb(image, x, centreY)
            assertEquals(
                "the ring at ($x, $centreY) reads ${hex(here)} rather than the page's " +
                    "${hex(shoulderUnderTest)}" +
                    (if (here == chromeUnderTest) " — that is the BAR: the ring is gone." else "") +
                    ". The raised button's only separator is a ${V2ShellMetrics.AiButtonRing} ring " +
                    "of the page colour",
                hex(shoulderUnderTest),
                hex(here),
            )
        }

        // Counterexample: past the ring, the bar must still be the bar. Without it the assertion
        // above would pass just as happily on a frame where the whole bar had gone page-coloured.
        val outside = rgb(image, centreX + ringOffset + BeyondRingPx, centreY)
        assertEquals(
            "beyond the ring the bar must still be the bar — read ${hex(outside)}",
            hex(chromeUnderTest),
            hex(outside),
        )

        // ── The shadow half ──────────────────────────────────────────────────────────────────────
        // ABOVE the bar, beside the circle: nothing but the page is behind these pixels and the ring
        // does not reach them, so any value other than the page is something being drawn AROUND the
        // button — which, on a FloatingActionButton, is its elevation shadow.
        val shadowY = centreY - ShadowProbeRisePx
        listOf(centreX - radiusPx, centreX + radiusPx).forEach { x ->
            val here = rgb(image, x, shadowY)
            assertEquals(
                "($x, $shadowY) — above the bar, ${ShadowProbeRisePx}px up from the button's middle " +
                    "and just outside its ring — reads ${hex(here)} rather than the bare page's " +
                    "${hex(shoulderUnderTest)}. The centre button carries NO shadow: all four " +
                    "FloatingActionButtonDefaults.elevation slots must be 0, or the one the owner " +
                    "sees comes back the instant a finger lands on it",
                hex(shoulderUnderTest),
                hex(here),
            )
        }
    }

    /**
     * Captured to a FILE and re-read rather than through `captureToImage()` — Roborazzi's capture
     * does not first wait for an idle clock. `Record` is forced and the path is outside the golden
     * directory, so these stay scratch frames.
     */
    private fun render(name: String): BufferedImage {
        val file = File("$ProbeDir/$name.png")
        file.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(
            filePath = file.path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        return ImageIO.read(file)
    }

    @Composable
    private fun PageUnderTest() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        )
    }

    /** The page with a CARD filling it — a list row scrolled under the button's raised half. */
    @Composable
    private fun CardPageUnderTest() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.card()),
        )
    }

    private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

    private fun hex(rgb: Int): String = "#%06X".format(rgb)

    private companion object {
        /** 1dp == 1px at this density, which is what lets the offsets below be written in dp. */
        const val Qualifiers = "w412dp-h891dp"

        /** Scratch frames, deliberately outside the checked-in golden directory. */
        const val ProbeDir = "build/ai-button-ring-probe"

        /** How far past the ring's outer edge the counterexample probe lands. */
        const val BeyondRingPx = 4

        /**
         * How far ABOVE the button's vertical middle the shadow probe sits.
         *
         * 22px: high enough to be above the bar's top edge (the middle sits 6px below it) and to put
         * the ring's own half-width at ~21.6px, so a probe at the button's RADIUS (28px) is ~6px
         * clear of the ring and lands on bare page.
         */
        const val ShadowProbeRisePx = 22

        /**
         * How far ABOVE the bar's top edge the clip probe sits.
         *
         * 8px: inside the 22dp overhang (so it is over the hosted screen, not the bar) and high
         * enough that the ring's half-width there (~26.6px at 16px up from the button's middle) is
         * still under the button's own radius, which is where the probe lands.
         */
        const val AboveEdgeProbePx = 8
    }
}
