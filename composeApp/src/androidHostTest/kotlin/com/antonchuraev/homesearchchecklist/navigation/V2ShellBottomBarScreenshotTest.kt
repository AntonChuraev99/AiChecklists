package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.QuickCaptureDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.captureDockScrimColor
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Visual proof for the v2 Compact bottom chrome — the shared-grey redesign.
 *
 * These are REPORT shots, deliberately recorded rather than verified: the owner's verdicts here have
 * all been visual, and the defects behind them are measurable but invisible in any assertion.
 *
 * ## The three states this file has now recorded, in order
 *  1. **White bar on a cream page.** ΔL\* **+1.7** (1.04:1) — no tonal step at all, the whole
 *     separation riding on a 1dp hairline. Rejected: "отвратительно… это что веб?"
 *  2. **The ink plinth** (`inverseSurface`, ΔL\* −78.4). Fixed light, and left dark on the top of the
 *     container ladder at ΔL\* **+13.5** — the palest slab on a dark screen. Rejected from the
 *     device: "при чёрной теме проглядывается белый цвет под нижней навигацией".
 *  3. **One grey for the whole bottom chrome** (current) — see [AppSurface.bottomChrome] for the full
 *     ΔL\* table and for why dark moves DOWN from +13.5 to +4.3 rather than going darker than the
 *     page (going darker measures −2.0, i.e. less separation, not more).
 *
 * What each shot has to show:
 *  - the bar and the chat dock are ONE surface — same tone, same 28dp top corner — so the bottom of
 *    the screen reads as one object instead of stacked docks;
 *  - **with quick-capture up there is no bar at all.** The dock is the bottom chrome in that state
 *    (owner's verdict from a Pixel 9: "при создании чеклиста в открытом состоянии не должна быть
 *    видна нижняя навигация"), it reaches the window's bottom edge, and its own clipped shoulders
 *    read as the dimmed page rather than as two bright nicks;
 *  - the bar is a different plane from the page it sits under, carried by tone PLUS
 *    [AppSurface.bottomChromeShadow], because neither theme's tone step is large enough alone;
 *  - the ACTIVE tab is obvious at a glance (the old `secondaryContainer` pill on white was 1.31:1);
 *  - every label stays readable on the chrome in both themes;
 *  - the bar still grows instead of truncating at fontScale ≥ 1.3 in RU/HI, and the raised AI button
 *    stays glued to the bar's top edge when it does;
 *  - Medium and Expanded are untouched — the chrome is an edge-of-screen device and a rail has no
 *    edge.
 *
 * NOT covered by these PNGs, and not claimed: the tab-change morph and the AI button's hand-off
 * (motion never appears in a still), touch routing (that is `V2ShellAiButtonTest`), and the system
 * navigation strip — Robolectric reports zero window insets, so the shot cannot show the chrome
 * painting the 3-button / gesture strip. That one follows structurally from `NavigationBar` applying
 * `WindowInsets.navigationBars` to its own container, which this change does not touch.
 *
 * Record + inspect:
 *   ./gradlew :composeApp:recordRoborazziAndroidHostTest --tests "*V2ShellBottomBarScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class V2ShellBottomBarScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `Locale.setDefault` is JVM-global and Gradle reuses one JVM for the whole test task, so an
     * unrestored RU default would silently re-render every LATER test class in Russian — including
     * assertions that match on English strings.
     */
    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private val defaultLocale: Locale = Locale.getDefault()

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        selectedTab: String = V2Destination.Inbox,
        barVisible: Boolean = true,
        captureOpen: Boolean = false,
    ) {
        // BOTH, and neither alone is enough. `setQualifiers` moves the Android resource
        // configuration, which is what Robolectric measures against; Compose Resources
        // (`org.jetbrains.compose.resources`) resolves values-ru / values-hi off the JVM default
        // locale instead, so a qualifier-only test renders English labels while claiming to be the
        // RU shot — verified on the first recording of this file.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    V2NavigationShell(
                        selectedTab = selectedTab,
                        onNavigate = {},
                        onOpenChat = {},
                        onOpenSettings = {},
                        onOpenUpdates = {},
                        showCreateFab = true,
                        onOpenCreate = {},
                        barVisible = barVisible,
                        captureOpen = captureOpen,
                        overlayContent = null,
                        content = { InboxStub(captureOpen = captureOpen) },
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── The main frame: what the owner looks at ──────────────────────────────

    /** Reference frame — the one to hold against `claude_design/…/d_inkPlinth_light.png`. */
    @Test
    fun compactBar_412dp_light() = shoot("w412dp-h891dp")

    /** Dark: the plinth converges to an ordinary raised surface and must NOT use `inverse*`. */
    @Test
    fun compactBar_412dp_dark() = shoot("w412dp-h891dp", dark = true)

    /** The active pill has to be legible on a tab whose label is longer than "Inbox". */
    @Test
    fun compactBar_412dp_light_projectsSelected() =
        shoot("w412dp-h891dp", selectedTab = V2Destination.Projects)

    // ── Size sweep ───────────────────────────────────────────────────────────

    /** Narrowest supported phone: 76dp of gap leaves ~61dp per item. */
    @Test
    fun compactBar_320dp_light() = shoot("w320dp-h568dp")

    @Test
    fun compactBar_360dp_light() = shoot("w360dp-h640dp")

    /** Medium: the rail, deliberately untouched by the plinth. */
    @Test
    fun mediumRail_600dp_light() = shoot("w600dp-h800dp")

    /** Expanded: the permanent drawer, also untouched. */
    @Test
    fun expandedDrawer_840dp_light() = shoot("w840dp-h1000dp")

    // ── Type scale × locale: the case that grows the bar ─────────────────────

    @Test
    fun compactBar_412dp_light_fontScale13() = shoot("w412dp-h891dp", fontScale = 1.3f)

    /** RU at 1.5: labels wrap to two lines, the bar grows, the button must follow it. */
    @Test
    fun compactBar_412dp_light_ru_fontScale15() =
        shoot("ru-rRU-w412dp-h891dp", fontScale = 1.5f, locale = Locale("ru", "RU"))

    @Test
    fun compactBar_412dp_dark_ru_fontScale15() =
        shoot("ru-rRU-w412dp-h891dp", dark = true, fontScale = 1.5f, locale = Locale("ru", "RU"))

    /** HI carries the longest label in the app — कैलेंडर. */
    @Test
    fun compactBar_412dp_light_hi_fontScale13() =
        shoot("hi-rIN-w412dp-h891dp", fontScale = 1.3f, locale = Locale("hi", "IN"))

    /** Narrowest window AND the longest labels at once. */
    @Test
    fun compactBar_320dp_light_ru_fontScale13() =
        shoot("ru-rRU-w320dp-h568dp", fontScale = 1.3f, locale = Locale("ru", "RU"))

    // ── The two states the chrome changes shape in ───────────────────────────

    /**
     * Quick-capture is up — the state the owner reported twice from a Pixel 9.
     *
     * Rendered with the REAL [QuickCaptureDock] and the page dimmed by the host's scrim, because
     * both defects reported against this state are invisible without those two things:
     *  - the bottom navigation showing under the dock is only a defect once you can see that the dock
     *    is the surface the user is working in;
     *  - the dock's `SheetTop` shoulders only read as "two bright corners" against a DIMMED page.
     *    Undimmed, they are the page beside the page — ΔL\* 0.
     *
     * The stub dock these cells used to render could show neither: it had no rounded corners at all,
     * so it had no shoulders, and the fixture painted no scrim.
     */
    @Test
    fun compactBar_412dp_light_captureOpen() =
        shoot("w412dp-h891dp", captureOpen = true)

    @Test
    fun compactBar_412dp_dark_captureOpen() =
        shoot("w412dp-h891dp", dark = true, captureOpen = true)

    /** The narrower phone: the dock's rows tighten, its shoulders do not move. */
    @Test
    fun compactBar_360dp_light_captureOpen() =
        shoot("w360dp-h640dp", captureOpen = true)

    @Test
    fun compactBar_360dp_dark_captureOpen() =
        shoot("w360dp-h640dp", dark = true, captureOpen = true)

    /**
     * RU at fontScale 1.3 with capture up: the dock is at its tallest (wrapped chip labels, a bigger
     * input) on a window that has not grown, which is where a dock that reserves the wrong bottom
     * strip runs out of screen.
     */
    @Test
    fun compactBar_412dp_light_captureOpen_ru_fontScale13() =
        shoot(
            "ru-rRU-w412dp-h891dp",
            captureOpen = true,
            fontScale = 1.3f,
            locale = Locale("ru", "RU"),
        )

    /** A pushed detail route: no plinth, no button, and the content owns the whole window. */
    @Test
    fun compactBar_412dp_light_barHidden() = shoot("w412dp-h891dp", barVisible = false)

    // ── Fake screen under the chrome ─────────────────────────────────────────

    /**
     * Stand-in for the Inbox screen. Rows are the real design-system tones ([AppSurface.card] on
     * [AppSurface.ground]) rather than feature code — the point of the shot is the step between the
     * PAGE and the bar, so the page has to be the real page colour, and `feature:home` is not on
     * this module's test classpath.
     *
     * ## The dock is mounted the way `AppScaffold` mounts it, and that is load-bearing
     * `Column { Box(weight(1f)) { page + scrim }; dock }` is not decoration — it is the ONE
     * structural fact that decides what the dock's clipped shoulders reveal. In production the dock
     * lives in `AppScaffold`'s `bottomBar` slot and the capture scrim lives inside the CONTENT slot,
     * so the scrim stops exactly at the dock's top edge and the strip behind the dock is undimmed
     * page. A fixture that instead paints the scrim across the whole box and drops the dock on top
     * dims that strip too — and then the shoulders come out the same colour as the page beside them,
     * i.e. the fixture reports a clean frame over the exact defect it was built to show. This one
     * was written that way first; the frame it recorded was ΔL\* 0 where the device shows a bright
     * nick.
     */
    @Composable
    private fun InboxStub(captureOpen: Boolean) {
        // = the scaffold's own container: what is behind BOTH slots, and therefore what any hole in
        // either of them shows.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppSurface.ground()),
        ) {
            // = AppScaffold's content slot. The scrim is a child of THIS, never of the parent.
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                ) {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
                    Text(
                        text = "Inbox",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "5 tasks · 2 due today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                    listOf(
                        "Book the venue for Saturday",
                        "Pick up dry cleaning",
                        "Draft Q3 planning notes",
                        "Reply to Marta about the invoice",
                        "Water the plants",
                        "Renew gym membership",
                        "Call the landlord back",
                        "Prepare the sprint demo",
                    ).forEach { TaskRowStub(it) }
                }
                if (captureOpen) {
                    // The CONTENT scrim, exactly as `InboxScreen` and `CalendarScreen` paint it:
                    // scoped to this slot, so it ends where the dock begins.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(captureDockScrimColor()),
                    )
                }
            }
            // = AppScaffold's bottomBar slot.
            if (captureOpen) {
                QuickCaptureDock(
                    text = "",
                    onTextChange = {},
                    onAdd = {},
                    placeholder = "Add a task…",
                    // The scrim's third tile, exactly as both hosts pass it: behind the dock, so its
                    // clipped `SheetTop` shoulders read as the DIMMED page instead of the raw one.
                    modifier = Modifier.background(captureDockScrimColor()),
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

    @Composable
    private fun TaskRowStub(title: String) {
        val shape = MaterialTheme.shapes.medium
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(AppSurface.card())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingMd),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(modifier = Modifier.size(AppDimens.SpacingMd))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

}
