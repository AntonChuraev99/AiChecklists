package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RED -> GREEN harness for the glassmorphism chat-dock backdrop blur.
 *
 * Renders [ChecklistDetailChatDock] as a bottom overlay above a deliberately HIGH-CONTRAST
 * hazeSource (red/blue stripes + sharp white text). If Haze's backdrop blur actually renders, the
 * stripes/text directly behind the dock are visibly SMUDGED in the golden PNG. If only the
 * colorEffects tint renders (the reported bug), they stay razor-sharp under a flat tinted strip.
 *
 * This is the deterministic reproduction the device eyeballing could not give us. Record + inspect:
 *   ./gradlew :core:designsystem:recordRoborazziAndroidHostTest --tests "*ChecklistDockGlassScreenshotTest*"
 * Golden PNG lands in:
 *   core/designsystem/src/androidHostTest/roborazzi/...checklistDockGlass_backdropBlur.png
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChecklistDockGlassScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Belt-and-braces. Robolectric rebuilds the configuration per test method, so a qualifier set
     * inside one test is not expected to reach the next — but every frame in this class now states
     * its own size anyway, and this makes the tablet value's blast radius zero either way rather
     * than resting on that expectation.
     */
    @After
    fun resetQualifiers() = RuntimeEnvironment.setQualifiers(Phone320)

    @Test
    fun checklistDockGlass_backdropBlur() {
        RuntimeEnvironment.setQualifiers(Phone320)
        composeTestRule.setContent { ChecklistDockGlassContent() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun mainGlassDock_backdropBlur() {
        RuntimeEnvironment.setQualifiers(Phone320)
        composeTestRule.setContent { MainGlassDockContent() }
        composeTestRule.onRoot().captureRoboImage()
    }

    // ── The dock NARROWER than the window ────────────────────────────────────
    //
    // Every frame above is a phone, where the dock fills the window: its left and right edges ARE
    // the screen's edges. That is the one geometry in which a stroke down the dock's sides is
    // invisible — and it was the only geometry this suite had, which is how a hairline that traced
    // the whole outline instead of just the top edge shipped unseen.
    //
    // `V2ChatDockOverlay` mounts the dock `.adaptiveContentWidth().fillMaxWidth()` — a 720dp cap —
    // so on a tablet-class window the dock is inset and both side edges are interior. Anything the
    // dock draws on them is a border around a floating panel, which is exactly the "web-looking box
    // held together by a frame" the owner rejected. These two frames are where that shows.

    @Test
    fun mainGlassDock_840dp_light() {
        RuntimeEnvironment.setQualifiers(Tablet840)
        composeTestRule.setContent { MainGlassDockContent(dark = false, capWidth = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun mainGlassDock_840dp_dark() {
        RuntimeEnvironment.setQualifiers(Tablet840)
        composeTestRule.setContent { MainGlassDockContent(dark = true, capWidth = true) }
        composeTestRule.onRoot().captureRoboImage()
    }

    private companion object {
        const val Tablet840 = "w840dp-h1200dp"

        /**
         * The phone frames' size, stated rather than inherited. It equals Robolectric's own default
         * — which is what these two goldens were recorded at before any qualifier existed in this
         * file, so naming it keeps them byte-identical — and stating it means the frame's size is
         * readable at the test rather than being a property of the harness that a reader has to go
         * and look up. Both goldens re-verified unchanged after the change.
         */
        const val Phone320 = "w320dp-h470dp"
    }
}

/**
 * High-contrast hazeSource backdrop shared by both dock goldens — red/blue stripes + sharp white
 * text so ANY blur is unmistakable. If Haze's backdrop blur renders, the stripes/text behind the
 * dock's frosted strip are visibly SMUDGED in the golden; if only a flat tint renders (the
 * regression), they stay razor-sharp under a tinted plate.
 */
@Composable
private fun HighContrastBackdrop(hazeState: HazeState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
    ) {
        repeat(14) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(if (i % 2 == 0) Color(0xFFD32F2F) else Color(0xFF1565C0))
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = "Row $i - sharp text to blur", color = Color.White)
            }
        }
    }
}

/**
 * ChecklistDetailScreen dock golden — the context-aware [ChecklistDetailChatDock] over the
 * high-contrast backdrop. The frosted strip above the chips documents the live blur.
 */
@Composable
private fun ChecklistDockGlassContent() {
    AppTheme(darkTheme = false) {
        val hazeState = rememberHazeState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(MaterialTheme.colorScheme.background),
        ) {
            HighContrastBackdrop(hazeState)

            // Component under test — the floating glass dock over the high-contrast backdrop.
            ChecklistDetailChatDock(
                hazeState = hazeState,
                checklistName = "Party Planning",
                onChatClick = {},
                onMicClick = {},
                chatPlaceholder = "Ask Gisti...",
                micContentDescription = "Voice input",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                chipsContent = {
                    GistiPromptChips(
                        chips = gistiChecklistPromptChips(),
                        onChipClick = {},
                    )
                },
            )
        }
    }
}

/**
 * MainScreen dock golden — the generic [GistiGlassChatDock] with the default prompt chips +
 * [AskGistiBar] pill (the exact composition MainScreen mounts) over the same high-contrast backdrop.
 * Documents that the home dock's frosted strip blurs its backdrop too. A regression that breaks Haze
 * sampling makes the strip sharp/opaque here as well.
 */
@Composable
private fun MainGlassDockContent(dark: Boolean = false, capWidth: Boolean = false) {
    AppTheme(darkTheme = dark) {
        val hazeState = rememberHazeState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .background(MaterialTheme.colorScheme.background),
        ) {
            HighContrastBackdrop(hazeState)

            GistiGlassChatDock(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The v2 shell's own chain, in its order: the 720dp cap has to come BEFORE
                    // fillMaxWidth (a max after a fixed width is ignored). Below 720dp it is inert,
                    // which is why the phone frames above can share this composable unchanged.
                    .then(if (capWidth) Modifier.adaptiveContentWidth() else Modifier)
                    .fillMaxWidth(),
                chipsContent = {
                    GistiPromptChips(
                        chips = gistiDefaultPromptChips(),
                        onChipClick = {},
                    )
                },
                pillContent = {
                    AskGistiBar(
                        placeholder = "Ask Gisti to add, remind, or plan…",
                        onClick = {},
                        onMicClick = {},
                    )
                },
            )
        }
    }
}
