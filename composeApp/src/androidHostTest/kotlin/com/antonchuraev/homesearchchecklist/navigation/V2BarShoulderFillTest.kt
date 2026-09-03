package com.antonchuraev.homesearchchecklist.navigation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.capture_dock_ai_entry_title
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRowSection
import com.antonchuraev.homesearchchecklist.desingsystem.components.captureDockScrimColor
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
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
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
 * A filled shoulder could still be wrong in either direction, and the frames recorded while this
 * change was made show both. Too BRIGHT reproduces the reported symptom with a predictable colour
 * (`#FAFAFA` with no fill at all). Too DARK is the same hole in the other direction: with the chrome's
 * painted 16dp shadow band removed by the owner ("убери тени от нижней навигации") the shoulder's old
 * value — that gradient's terminal black-over-page — was measured as `#0C0C0F` against a `#121317`
 * page, i.e. a black notch darker than the page AND the chrome beside it.
 *
 * So the assertion is an IDENTITY, not a run with a tolerance: the shoulder is `AppSurface.ground()`,
 * the same value as the plane that ends at the bar's top edge, and the strip above the bar is flat —
 * no ramp of any height. See [theShoulderIsThePage_light] and
 * [theShoulderIsThePageEvenUnderACard_light]. This is what the monotonic page → ramp → shoulder →
 * chrome run used to check, minus the ramp and minus the ±2 tolerance a gradient's last row needed —
 * and a tolerance is where the dark notch above would have hidden.
 *
 * ## The second subject: the CAPTURE DOCK's shoulders
 * The same shape of defect, one surface up, and it reached a device for the same reason — a bright
 * wedge that only shows up against the right background. While the quick-capture dock is open the bar
 * is not on screen at all (see [V2ShellCompactBar]) and the dock is the bottom chrome; its own
 * `SheetTop` corners then showed the UNDIMMED page against the 45%-dimmed page beside them. That half
 * lives in [assertCaptureDockShoulderIsTheDimmedPage], and unlike the bar's it is not a hole through
 * the composition — it is a hole through the host's scrim, so it is fixed by the host and not by a
 * design-system token.
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

    /**
     * `AppSurface.bottomChrome()` for the theme of the current frame, lifted out of composition for
     * the same reason as [cardUnderTest]: an assertion must be able to NAME the bar's own surface
     * rather than compare two pixels of one frame against each other.
     */
    private var chromeUnderTest: Int = 0

    /** `AppSurface.ground()` for the current frame — the plane the shoulder is composited over. */
    private var groundUnderTest: Int = 0

    /**
     * Height of the shell's CONTENT box in px — which is exactly the bar's top edge, because the
     * shell lays the two out as `Column { Box(weight(1f)) { content() }; bar }` and the content box
     * therefore ends where the bar begins.
     *
     * This is the ruler, and it is measured rather than computed. See [barTopEdge].
     */
    private var barTopPx: Int = 0

    /**
     * Top edge of the quick-capture dock in px, from LAYOUT (`positionInRoot`), for the frames where
     * the dock — not the bar — is the bottom chrome. Zero means the fixture never mounted it, which
     * [assertCaptureDockShoulderIsTheDimmedPage] fails on rather than probing row 6 of the window.
     */
    private var dockTopPx: Int = 0

    /** `Locale.setDefault` is JVM-global and Gradle reuses one JVM for the whole task. */
    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

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

    // ── The SAME question one surface up: the capture dock's own shoulders ───
    //
    // This used to be one cell here — "with the capture dock up the bar is square, so there is no
    // shoulder to fill". The premise is gone: with the dock up there is no BAR (the owner's second
    // device verdict, see V2ShellCompactBar), so that cell rendered a frame with neither a bar nor a
    // dock in it and asserted a scan over a page. Green, and about nothing.
    //
    // What replaces it is the same defect one surface up, and this one is real: the dock is a
    // `SheetTop` Surface, its two top corners are clipped away, and what shows through them is
    // whatever the host painted there. See [assertCaptureDockShoulderIsTheDimmedPage].

    @Test
    fun theCaptureDockShoulderIsTheDimmedPage_light() =
        assertCaptureDockShoulderIsTheDimmedPage(dark = false)

    @Test
    fun theCaptureDockShoulderIsTheDimmedPage_dark() =
        assertCaptureDockShoulderIsTheDimmedPage(dark = true)

    // ── Large text: where the ruler used to lie ──────────────────────────────
    //
    // `NavigationBar` sizes each item from its CONTENT (`placeLabelAndIcon`), so a wrapped label
    // makes the bar TALLER than [AppDimens.BottomBarHeight] — the shell says so itself, and measures
    // the bar rather than assuming it for exactly this reason. The old ruler here did assume it:
    // `image.height - BottomBarHeight` starts the scan INSIDE a grown bar, below both 28dp shoulders,
    // so the scan could not see a hole even with the corners wide open, and passed. That is the
    // "unfalsifiable when failure is silent" shape — a green test proving nothing.
    //
    // The goldens do not cover it either: `V2ShellBottomBarScreenshotTest.compactBar_*_fontScale13/15`
    // are recorded without a sentinel, and Robolectric's `#FAFAFA` backdrop against the cream page is
    // ΔL* 0.3 — invisible in a screenshot review, and still a bright nick on a device.

    @Test
    fun noWindowBackdropShowsThroughTheBar_412dp_light_fontScale13() =
        assertNoSentinelInTheBarFootprint(Phone412, dark = false, cardsUnderTheBar = true, fontScale = 1.3f)

    @Test
    fun noWindowBackdropShowsThroughTheBar_412dp_dark_fontScale15() =
        assertNoSentinelInTheBarFootprint(Phone412, dark = true, cardsUnderTheBar = true, fontScale = 1.5f)

    /**
     * The harshest cell in the matrix: the narrowest phone, the longest labels and the largest text
     * at once. "Календарь" / "Проекты" at fontScale 1.5 on a 320dp window is where the bar is most
     * likely to outgrow the constant the ruler used to be.
     */
    @Test
    fun noWindowBackdropShowsThroughTheBar_320dp_light_ru_fontScale15() =
        assertNoSentinelInTheBarFootprint(
            Phone320Ru,
            dark = false,
            cardsUnderTheBar = true,
            fontScale = 1.5f,
            locale = Locale("ru", "RU"),
        )

    // ── The shoulder is the right colour, not merely some colour ─────────────

    /**
     * The shoulder is `AppSurface.ground()` — the page continuing behind the bar's clipped corner —
     * and the strip above the bar carries no band of any kind.
     *
     * Two claims in one frame because they are one decision. The bar's separator is now tone plus the
     * 28dp corners and nothing else (owner: "убери тени от нижней навигации"), so the page must run
     * flat into the bar's top edge AND continue at the same value inside the corner sector. Assert only
     * the first and the shoulder is free to be the black notch the old terminal-shadow value left
     * behind (measured `#0C0C0F` on a `#121317` page); assert only the second and a reintroduced ramp
     * above the bar goes unnoticed.
     *
     * Both themes, because they sit on OPPOSITE sides of the chrome — light's page is lighter than the
     * bottom chrome, dark's is darker — so a regression can be right in one and inverted in the other.
     */
    @Test
    fun theShoulderIsThePage_light() = assertShoulderIsThePage(dark = false, cardsUnderTheBar = false)

    @Test
    fun theShoulderIsThePage_dark() = assertShoulderIsThePage(dark = true, cardsUnderTheBar = false)

    /**
     * The same identity with a solid column of `AppSurface.card()` rows clipped at the bar's edge, so a
     * CARD — not the page — is the last thing painted above the bar.
     *
     * This is the hypothesis "the corners show the white list card, because the content scrolls under
     * the bar", and it closes it by naming the expected value rather than by ruling one out: the
     * shoulder must be `ground()` even here. A shoulder whose colour depends on what the user scrolled
     * to is the original defect wearing a different number, and `shoulder != card` — what this used to
     * assert, light only — passes on every wrong value except one.
     *
     * The strip above the bar is checked for FLATNESS rather than for a value: it is card-coloured in
     * this fixture, and what must not be there is a ramp.
     */
    @Test
    fun theShoulderIsThePageEvenUnderACard_light() =
        assertShoulderIsThePage(dark = false, cardsUnderTheBar = true)

    @Test
    fun theShoulderIsThePageEvenUnderACard_dark() =
        assertShoulderIsThePage(dark = true, cardsUnderTheBar = true)

    // ── shared assertions ────────────────────────────────────────────────────

    /**
     * The quick-capture dock's clipped shoulders must read as the DIMMED page — the same value as the
     * page one row above them — and never as the raw page.
     *
     * ## The defect, measured
     * The host dims the page while the dock is up, and that scrim lives inside `AppScaffold`'s CONTENT
     * slot, which ends exactly at the dock's top edge (deliberately: it is what keeps the dock and the
     * system-nav strip out of the dim at any keyboard height). Behind the dock is therefore the
     * scaffold's own container — the page, undimmed. The two 28dp corners `SheetTop` clips away showed
     * it: measured on the 412dp light frame recorded before the fix, `#FBFAF8` in the shoulder against
     * `#8A8988` in the page beside it, ΔL\* +41 — the brightest thing in the bottom half of a screen
     * whose whole point at that moment is the dock. Reported from a Pixel 9 as two light corners next
     * to the dock.
     *
     * ## Why the expected colour is named twice, once positively and once negatively
     * "The shoulder equals the page above it" alone would pass on a frame with no scrim at all, where
     * both are the raw page — i.e. on a fixture that stopped reproducing the case. So the raw
     * `ground()` is lifted out of composition and asserted AGAINST: it is the exact value the defect
     * produced, so a regression cannot be green, and a fixture that forgot the scrim cannot be green
     * either.
     *
     * ## Why `cardsUnderTheBar = false`
     * So that the plane ending at the dock's top edge is the PAGE, which is what makes the equality
     * exact. With a card there instead the two probes differ by the card↔page step seen through one
     * alpha (measured: light `#8A8988` shoulder against `#8C8C8C`, dark `#0A0B0D` against `#0F1012`)
     * — arithmetic, not a defect. Bounding that arithmetic here would trade an exact assertion for a
     * tolerance, and the tolerance is where a real regression hides;
     * [theShoulderIsThePageEvenUnderACard_light] covers the same "a card is what ends at the edge" case
     * for the BAR, where the shoulder is opaque and the answer can stay an identity.
     */
    private fun assertCaptureDockShoulderIsTheDimmedPage(dark: Boolean) {
        val name = "captureShoulder_${if (dark) "dark" else "light"}"
        val image = render(
            Phone412,
            dark = dark,
            cardsUnderTheBar = false,
            captureOpen = true,
            name = name,
        )

        assertTrue(
            "the fixture did not mount the capture dock — dockTopPx=$dockTopPx (frame: $name)",
            dockTopPx in 1 until image.height - ShoulderProbeDp,
        )

        // Both inside the 28dp corner sector at x = 2: at that inset the arc closes ~17px below the
        // dock's top edge, so +6 is comfortably in the wedge and not on the dock's own surface.
        val shoulder = rgb(image, SampleInset, dockTopPx + ShoulderProbeDp)
        val dimmedPageAbove = rgb(image, SampleInset, dockTopPx - ShoulderProbeDp)

        assertTrue(
            "the shoulder reads ${hex(shoulder)}, which is the RAW page — the host's scrim is not " +
                "being painted behind the dock, so its clipped corners are the brightest thing " +
                "beside a dimmed page (frame: $name)",
            shoulder != groundUnderTest,
        )
        assertEquals(
            "the shoulder reads ${hex(shoulder)} against ${hex(dimmedPageAbove)} one probe above it " +
                "— the corner must continue the dimmed page, not step off it (frame: $name)",
            hex(dimmedPageAbove),
            hex(shoulder),
        )
    }

    /**
     * The two halves of "the chrome's only separators are tone and shape".
     *
     *  1. **The strip above the bar is FLAT** — every one of the [FlatStripDp] rows leading down to the
     *     bar's top edge reads the same value. A row-by-row run rather than "the last row equals a row
     *     higher up": a gradient's two ends can be sampled at any two heights and a two-probe check has
     *     to guess which two, while a run needs no guess and catches a band of any height or alpha. The
     *     expected value is taken from the frame's own top row rather than named, because this fixture
     *     deliberately puts a CARD there in one of its two configurations — what is under test here is
     *     the absence of a ramp, not which plane it would have ramped.
     *  2. **The shoulder is [groundUnderTest]** — `AppSurface.ground()` lifted out of composition, so
     *     the assertion NAMES the colour it expects instead of comparing two pixels of one frame with
     *     each other. That is the guard this branch has now tripped over twice; a shoulder compared
     *     against a neighbour is green on any value both share.
     */
    private fun assertShoulderIsThePage(dark: Boolean, cardsUnderTheBar: Boolean) {
        val name = buildString {
            append("shoulderIsPage_")
            append(if (dark) "dark" else "light")
            if (cardsUnderTheBar) append("_underCard")
        }
        val image = render(Phone412, dark = dark, cardsUnderTheBar = cardsUnderTheBar, name = name)
        val barTop = barTopEdge(image)
        assertRulerIsOnTheBarsOwnSurface(image, barTop, name)

        val topOfStrip = rgb(image, SampleInset, barTop - FlatStripDp)
        for (y in (barTop - FlatStripDp) until barTop) {
            val here = rgb(image, SampleInset, y)
            assertEquals(
                "row ${barTop - y} above the bar reads ${hex(here)} where the row " +
                    "${FlatStripDp}px up reads ${hex(topOfStrip)} — something is being ramped into " +
                    "the bar's top edge, and tone plus the 28dp corners are the whole separator " +
                    "(frame: $name)",
                hex(topOfStrip),
                hex(here),
            )
        }

        // The premise: with cards under the bar the plane ending at its edge must really be a card,
        // otherwise the "even under a card" cell proves nothing. Checked against the KNOWN card colour
        // rather than "brighter than the chrome", which the bare page clears just as easily.
        assertEquals(
            "the plane ending at the bar reads ${hex(topOfStrip)} — the fixture no longer " +
                "reproduces the case this cell exists for (frame: $name)",
            hex(if (cardsUnderTheBar) cardUnderTest else groundUnderTest),
            hex(topOfStrip),
        )

        val shoulder = rgb(image, SampleInset, barTop + 2)
        assertEquals(
            "the clipped corner reads ${hex(shoulder)} instead of the page's " +
                "${hex(groundUnderTest)} — a shoulder brighter than that is the window backdrop " +
                "showing through, darker is a black notch under the corner, and equal to " +
                "${hex(cardUnderTest)} is the corner taking on whatever the list scrolled to " +
                "(frame: $name)",
            hex(groundUnderTest),
            hex(shoulder),
        )
    }

    /**
     * Scans the WHOLE FRAME — every pixel — rather than probing the two corners by their computed
     * arc geometry, and rather than scanning "the bar's footprint" from a ruler.
     *
     * A corner probe has to know the radius, so it silently stops testing anything the day the radius
     * changes. But scanning from a ruler has the same disease one level up, and it is worse because
     * the ruler moves with the CONTENT: this scan used to start at
     * `image.height - AppDimens.BottomBarHeight`, and a `NavigationBar` sizes each item from its
     * content, so at `fontScale >= 1.3` — or in a locale with longer labels — a wrapped label makes
     * the bar taller and that start row lands BELOW both 28dp shoulders. The scan would then find no
     * sentinel because it never looked at the corners, and report success over a hole that is fully
     * open. Green, and proving nothing — the shape this project files under "unfalsifiable when
     * failure is silent".
     *
     * The fix is not a better ruler, it is no ruler: [Sentinel] is a colour the app never paints, the
     * shell covers the whole window, so a correct frame contains ZERO sentinel pixels ANYWHERE. That
     * question needs no geometry at all and cannot be narrowed by a measurement drifting. The measured
     * [barTopEdge] is still used, but only to say WHERE a hit was found, and
     * [assertRulerIsOnTheBarsOwnSurface] keeps the two probes that genuinely sample relative to the
     * edge honest.
     */
    private fun assertNoSentinelInTheBarFootprint(
        qualifiers: String,
        dark: Boolean,
        cardsUnderTheBar: Boolean,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ) {
        val name = buildString {
            append(qualifiers.substringAfter('w').substringBefore("dp"))
            append(if (dark) "_dark" else "_light")
            if (!cardsUnderTheBar) append("_shortList")
            if (locale != Locale.ENGLISH) append("_${locale.language}")
            if (fontScale != 1f) append("_fs${(fontScale * 10).toInt()}")
        }
        val image = render(qualifiers, dark, cardsUnderTheBar, false, "hole_$name", fontScale, locale)
        val barTop = barTopEdge(image)
        assertRulerIsOnTheBarsOwnSurface(image, barTop, name)
        val sentinel = Sentinel.toArgb() and 0xFFFFFF

        var firstHit: String? = null
        var hits = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (rgb(image, x, y) == sentinel) {
                    hits++
                    if (firstHit == null) firstHit = "($x, ${y - barTop} relative to the bar's top edge)"
                }
            }
        }

        assertEquals(
            "$hits pixels of the window backdrop show through, first at $firstHit — something over " +
                "that backdrop is a hole, not a surface (frame: $name)",
            0,
            hits,
        )
    }

    /**
     * The ruler points at the bar's top edge — used by the two probes that SAMPLE relative to it
     * ([assertShoulderIsThePage]), where a drifted
     * ruler silently moves what "the shoulder" and "the page" mean.
     *
     * Two checks, and both are about a ruler that is too LOW, because that is the direction which
     * fails SILENTLY: a scan or a sample starting inside the bar still finds bar-coloured pixels and
     * reports success over an open hole.
     *
     *  1. The pixel under the ruler is the bar's own surface — so the ruler is on the bar at all.
     *  2. The strip below the ruler is at least [AppDimens.BottomBarHeight] tall. The bar can never
     *     be SHORTER than its `defaultMinSize`, so anything less means the ruler is somewhere inside
     *     it. This is what catches the constant this ruler replaced: `image.height - BottomBarHeight`
     *     leaves exactly 80dp below it by construction and passes at fontScale 1.0, but the moment a
     *     wrapped label makes the real bar taller the same arithmetic points 80dp above the BOTTOM
     *     rather than at the top edge, and the strip it claims is short by the growth.
     *
     * ⚠️ Deliberately NOT "the pixel above the band is not the chrome". It was written when
     * `AppSurface.card()` and `AppSurface.bottomChrome()` were the SAME `surfaceContainerLow` in
     * dark: a card row above the bar read `#1A1C20` exactly like the bar did, so the check failed on
     * a correct frame, and a guard that cannot tell two planes apart in one theme is not a guard.
     *
     * That collision is GONE since 2026-09-03 — the chrome is its own literal (`GistiColors.chrome`,
     * `#191D25` dark / `#DCE2EC` light) while `card()` stays on the palette's neutrals, so the two
     * differ in both themes again. The check is still not reinstated here, deliberately: this method
     * validates a RULER, and "what is above it" is the subject of [assertShoulderIsThePage], which
     * already names both colours and scans the whole strip rather than sampling one pixel. Adding a
     * second, weaker copy of that claim here would only give the next reader two places to edit.
     *
     * Sampled at a quarter of the width: clear of the 28dp corner arcs, clear of the raised centre AI
     * button that overhangs the bar's top edge, and two rows down so the sample is the surface rather
     * than the top row's anti-aliasing.
     */
    private fun assertRulerIsOnTheBarsOwnSurface(image: BufferedImage, barTop: Int, name: String) {
        assertTrue(
            "the measured bar top ($barTop) is outside the frame (height ${image.height}) — the " +
                "fixture did not render a bar at all (frame: $name)",
            barTop in 1 until image.height,
        )
        val onTheBar = rgb(image, image.width / 4, barTop + 2)
        assertEquals(
            "the ruler points at $barTop, where the frame reads ${hex(onTheBar)} instead of the " +
                "bar's own ${hex(chromeUnderTest)} — every sample below is taken relative to a row " +
                "that is not the bar (frame: $name)",
            hex(chromeUnderTest),
            hex(onTheBar),
        )
        val stripBelow = image.height - barTop
        assertTrue(
            "the ruler points at $barTop, leaving ${stripBelow}px below it — less than the bar's " +
                "own minimum of ${MinBarHeightPx}px, so the ruler is INSIDE the bar and everything " +
                "above it went unexamined (frame: $name)",
            stripBelow >= MinBarHeightPx,
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
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ): BufferedImage {
        // BOTH: `setQualifiers` moves the Android resource configuration Robolectric measures
        // against, while Compose Resources resolves the destination labels off the JVM default
        // locale — and it is the LABEL that decides whether the bar outgrows 80dp.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                val base = LocalDensity.current
                // `fontScale` only, `density` untouched: dp→px stays 1:1 (so every offset below can
                // still be written in dp) while the text grows, which is the one axis that makes a
                // navigation-bar item taller than its 80dp minimum.
                CompositionLocalProvider(
                    LocalDensity provides Density(density = base.density, fontScale = fontScale),
                ) {
                    // The window, painted a colour the design system never uses. Anything that
                    // reaches this Box is a hole in everything drawn over it.
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
                            content = { PageUnderTest(cardsUnderTheBar, captureOpen) },
                        )
                    }
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
     *
     * ## The capture branch mirrors `AppScaffold`, and the structure is the whole test
     * `Column { Box(weight(1f)) { list + scrim }; dock }` is exactly how the two real hosts mount it:
     * the dock in the `bottomBar` slot, the scrim INSIDE the content slot. Flatten that — paint the
     * scrim over the whole box and drop the dock on top — and the strip behind the dock gets dimmed
     * too, the shoulders come out matching by accident, and the fixture reports a clean frame over the
     * defect it exists for. That is not hypothetical: it is what this fixture's first draft did.
     */
    @Composable
    private fun PageUnderTest(cardsUnderTheBar: Boolean, captureOpen: Boolean = false) {
        val rows = if (cardsUnderTheBar) 40 else 2
        // Recorded here, where the rows are actually painted, so the value an assertion compares
        // against and the value the fixture draws with cannot come apart.
        cardUnderTest = AppSurface.card().toArgb() and 0xFFFFFF
        chromeUnderTest = AppSurface.bottomChrome().toArgb() and 0xFFFFFF
        groundUnderTest = AppSurface.ground().toArgb() and 0xFFFFFF
        // = the scaffold's container: what both slots are drawn on, and therefore what a hole in
        // either of them reveals.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    // THE RULER. This box fills the shell's content slot, and the shell lays content
                    // and bar out as `Column { Box(weight(1f)) { content() }; bar }` — so this box's
                    // bottom edge IS the bar's top edge, by construction, at any font scale or
                    // locale. See [barTopEdge] for what the constant it replaces got wrong.
                    .onSizeChanged { barTopPx = it.height },
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items((0 until rows).toList()) {
                        // Flush, unspaced card rows: whatever row lands against the bar, the plane
                        // ending at the bar's top edge is card-coloured. No gap can put the page
                        // there by luck.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RowHeight)
                                .background(AppSurface.card()),
                        )
                    }
                }
                if (captureOpen) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(captureDockScrimColor()),
                    )
                }
            }
            if (captureOpen) {
                QuickCaptureDock(
                    text = "",
                    onTextChange = {},
                    onAdd = {},
                    placeholder = "Add a task…",
                    // Both of the modifier's jobs matter. The `background` is the fix under test —
                    // the scrim continuing behind the dock. The `onGloballyPositioned` is the ruler:
                    // the dock's own top edge, taken from layout rather than from a colour, because a
                    // probe asserting something ABOUT the shoulder's colour must not locate it BY a
                    // colour.
                    modifier = Modifier
                        .background(captureDockScrimColor())
                        .onGloballyPositioned { dockTopPx = it.positionInRoot().y.toInt() },
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

    /**
     * Top edge of the bar, from the shell's own LAYOUT rather than from colour or from a constant.
     *
     * Not from colour, for the reason every probe in this package had to adopt (see
     * [V2BottomChromeNoBandTest], which took the same rule over): a probe asserting something ABOUT a
     * colour must not locate its subject BY that colour, or re-tuning the chrome silently moves the
     * ruler.
     *
     * And no longer from `image.height - AppDimens.BottomBarHeight`, which was the same mistake one
     * level up. 80dp is the bar's `defaultMinSize`, not its height: `NavigationBar` sizes each item
     * from its CONTENT (`placeLabelAndIcon`), so a wrapped label makes the bar taller — which the
     * shell knows, and is why it measures the bar for the raised button's offset instead of assuming
     * it ([V2NavigationShell]'s "Why the offset is measured" note). A constant ruler therefore starts
     * the scan INSIDE a grown bar, below both 28dp shoulders, and reports a clean run over a hole
     * that is fully open. It only happened to be right because every case in this file ran at
     * fontScale 1.0.
     *
     * [barTopPx] is written by the fixture from `onSizeChanged` on the content box, whose bottom edge
     * is the bar's top edge by construction. The `image.height` fallback is not a default value: it
     * is an out-of-range sentinel that makes [assertRulerIsOnTheBarsOwnSurface] fail loudly if the
     * shell ever stops calling the content slot, instead of silently scanning row 0 onward.
     */
    private fun barTopEdge(image: BufferedImage): Int =
        if (barTopPx > 0) barTopPx else image.height

    private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

    // No luminance helper any more, and that is the point: every probe in this file now compares an
    // exact RGB against a colour lifted out of composition. Luminance existed to give a gradient's ends
    // a single number to be ordered by, and ordering with a tolerance is what let a wrong shoulder pass.
    private fun hex(rgb: Int): String = "#%06X".format(rgb)

    private companion object {
        /** 1dp == 1px at these densities, which is what lets every offset below be written in dp. */
        const val Phone412 = "w412dp-h891dp"
        const val Phone360 = "w360dp-h640dp"

        /** The narrow phone in the locale whose destination labels run longest. */
        const val Phone320Ru = "ru-rRU-w320dp-h568dp"

        /** Scratch frames, deliberately outside the checked-in golden directory. */
        const val ProbeDir = "build/shoulder-probe"

        /**
         * The window colour. Must be one the app never paints, in either theme — a hole filled with a
         * plausible near-white is precisely how this defect reached a device.
         */
        val Sentinel = Color(0xFFFF00FF)

        /** Clear of the 28dp corner arc at every row this test samples, and clear of the edge itself. */
        const val SampleInset = 2

        /**
         * How far above the bar's top edge the strip that must stay FLAT reaches.
         *
         * 24 rather than the 16 the deleted `bottomChromeShadowHeight` was: the number no longer
         * describes a band that exists, it bounds the region a reintroduced one could occupy, and a
         * scan taller than the band still catches it (a 16dp ramp lives entirely inside the last 16
         * of these 24 rows). Sized DOWN from the fixture instead: the short-list configuration puts 2
         * rows of 64dp at the top of the window, so 24 rows above the bar are bare page in it, and 40
         * flush 64dp rows make the same strip solid card in the other.
         */
        const val FlatStripDp = 24

        /**
         * How far above / below the dock's top edge the shoulder probe samples.
         *
         * At [SampleInset] (x = 2) the 28dp corner arc closes ~17px below the top edge, so 6px down
         * is inside the clipped wedge with room to spare — and 6px up is clear of the dock's own 1dp
         * top hairline and its anti-aliasing.
         */
        const val ShoulderProbeDp = 6

        /**
         * The bar's `defaultMinSize` — `NavigationBarHeight` in material3, mirrored as
         * [AppDimens.BottomBarHeight]. A FLOOR, never the height: an item is sized from its content,
         * so the real bar is this or taller. Used only to catch a ruler that has slipped inside it.
         */
        val MinBarHeightPx = AppDimens.BottomBarHeight.value.toInt()

        val RowHeight = 64.dp
    }
}
