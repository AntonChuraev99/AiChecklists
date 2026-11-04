package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.util.Locale

/**
 * DESIGN_SPEC §13 screenshot matrix of the refreshed onboarding: one PNG per (state × window ×
 * theme × locale × fontScale), every state from [OnboardingPreviewFixtures] plus the edge data of §13.
 *
 * Reduced motion is ON, so the demo stages show their poster (final) frame and no demo clock or orb
 * transition keeps Compose from idling. Frames with [Frame.advanceMs] > 0 idle the Robolectric main
 * looper that long — into the 1.2 s mode highlight (ring on the picture), past the 300 ms spinner
 * threshold, through the overlay's timeline.
 *
 *   ./gradlew :feature:onboarding:recordRoborazziAndroidHostTest
 *   ./gradlew :feature:onboarding:verifyRoborazziAndroidHostTest
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenshotMatrixTest(private val frame: Frame) {

    data class Frame(
        val no: Int,
        val state: String,
        val w: Int,
        val h: Int,
        val dark: Boolean,
        val lang: String,
        val fontScale: Float,
        val advanceMs: Long = 0L,
    ) {
        val fileName: String
            get() {
                val theme = if (dark) "dark" else "light"
                val fs = "fs" + (fontScale * 10).toInt()
                return "onboarding_%02d_%s_%dx%d_%s_%s_%s".format(no, state, w, h, theme, lang, fs)
            }

        override fun toString(): String = fileName
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun capture() {
        val state = fixtures.getValue(frame.state)
        val land = if (frame.w > frame.h) "-land" else "-port"
        RuntimeEnvironment.setQualifiers("w${frame.w}dp-h${frame.h}dp$land-xhdpi")
        RuntimeEnvironment.setFontScale(frame.fontScale)
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(frame.lang))
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        AppTheme(darkTheme = frame.dark) {
                            CompositionLocalProvider(LocalReducedMotion provides true) {
                                OnboardingContent(
                                    state = state,
                                    onIntent = {},
                                    snackbarHostState = SnackbarHostState(),
                                )
                            }
                        }
                    }
                }
                composeRule.waitForIdle()
                if (frame.advanceMs > 0) {
                    // A composition set through ActivityScenario is driven by the Choreographer on the
                    // paused Robolectric main looper, not by the compose rule's clock: idling the
                    // looper for N ms runs N ms of frames. The UI's holds are frame-clock waits
                    // (awaitFrameMillis), so this lands exactly inside them.
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(frame.advanceMs))
                }
                composeRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/${frame.fileName}.png")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    companion object {
        /** Inside the 1.2 s mode highlight (reduced motion: the ring snaps on, holds, snaps off). */
        private const val RING_MS = 600L

        /** Past the 300 ms threshold of the chip-creating spinner. */
        private const val SPINNER_MS = 500L

        /** Past the reduced-motion overlay timeline (note 300 ms → crossfade → list). */
        private const val OVERLAY_MS = 1500L

        private val fixtures: Map<String, OnboardingUiState> = OnboardingPreviewFixtures.all.toMap() + mapOf(
            // Edge data (DESIGN_SPEC §13): the field at 4 lines AND the error box under it.
            "tryItTypedLongAiError" to OnboardingPreviewFixtures.tryItTypedLong.copy(
                aiError = OnboardingAiError.Unavailable,
            ),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun frames(): List<Frame> {
            var n = 0
            fun f(state: String, w: Int, h: Int, dark: Boolean, lang: String, fs: Float, advance: Long = 0L) =
                Frame(++n, state, w, h, dark, lang, fs, advance)
            return listOf(
                // Step 1 — Showcase
                f("showcaseDefault", 360, 800, false, "en", 1.0f),
                f("showcaseDefault", 360, 800, true, "en", 1.0f),
                f("showcaseDefault", 412, 915, false, "ru", 1.0f),
                f("showcaseDefault", 412, 915, true, "ru", 1.0f),
                f("showcaseDefault", 360, 800, false, "en", 1.5f),
                f("showcaseDefault", 360, 800, false, "ru", 2.0f),
                f("showcaseDefault", 360, 800, false, "hi", 1.0f),
                f("showcaseCallOff", 360, 800, false, "en", 1.0f),
                f("showcaseDefault", 320, 640, false, "ru", 2.0f),
                f("showcaseDefault", 1280, 800, false, "en", 1.0f),
                f("showcaseDefault", 600, 960, true, "en", 1.3f),
                f("showcaseDefault", 800, 360, false, "en", 1.0f),
                // Step 2 — Try it
                f("tryItDefault", 360, 800, false, "en", 1.0f),
                f("tryItDefault", 412, 915, true, "ru", 1.0f),
                f("tryItModeWrite", 360, 800, false, "en", 1.0f, RING_MS),
                f("tryItModeAttach", 360, 800, false, "en", 1.0f, RING_MS),
                f("tryItModeTalk", 360, 800, false, "en", 1.0f, RING_MS),
                f("tryItCallHidden", 360, 800, false, "en", 1.0f),
                f("tryItTyped", 360, 800, true, "en", 1.0f),
                f("tryItTypedLong", 360, 800, false, "en", 1.0f),
                f("tryItEmptyHint", 360, 800, false, "en", 1.0f),
                f("tryItAiError", 360, 800, false, "en", 1.0f),
                f("tryItSignInError", 360, 800, true, "en", 1.0f),
                f("tryItChipCreating", 360, 800, false, "en", 1.0f, SPINNER_MS),
                f("tryItDefault", 360, 800, false, "ru", 1.5f),
                f("tryItDefault", 360, 800, false, "ru", 2.0f),
                f("tryItDefault", 360, 800, false, "hi", 1.0f),
                f("tryItDefault", 1280, 800, true, "en", 1.0f),
                f("tryItDefault", 600, 960, false, "en", 1.0f),
                f("tryItDefault", 320, 640, false, "ru", 2.0f),
                f("tryItTypedLongAiError", 360, 800, false, "ru", 1.3f),
                f("tryItCallHidden", 360, 800, false, "hi", 1.5f),
                // Overlays
                f("sampleReading", 360, 800, false, "en", 1.0f, OVERLAY_MS),
                f("sampleReady", 360, 800, false, "en", 1.0f, OVERLAY_MS),
                f("sampleReady", 412, 915, true, "ru", 1.0f, OVERLAY_MS),
                f("sampleReady", 320, 640, false, "ru", 1.0f, OVERLAY_MS),
                f("sampleReady", 1280, 800, false, "en", 1.0f, OVERLAY_MS),
                f("aiGenerating", 360, 800, false, "en", 1.0f),
                f("aiGeneratingSlowLongPrompt", 360, 800, true, "ru", 1.5f),
                // Modes where the page is taller than the window: the scroll target is visible.
                f("tryItModeWrite", 360, 800, false, "ru", 1.5f, RING_MS),
                f("tryItModeTalk", 360, 800, false, "ru", 1.5f, RING_MS),
            )
        }
    }
}
