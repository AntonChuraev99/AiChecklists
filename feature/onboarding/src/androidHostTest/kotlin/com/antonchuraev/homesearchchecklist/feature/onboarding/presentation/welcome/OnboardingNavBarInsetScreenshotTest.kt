package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.LocalSampleNoteImage
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Onboarding with a REAL system navigation bar (and, in one frame, the keyboard) in the window.
 * Plain Robolectric reports zero insets, so [OnboardingScreenshotMatrixTest] cannot see the nav-bar
 * zone at all — the Talk composer plate once stopped at the nav-bar top and the strip under it showed
 * the page colour instead of the plate's bottom-chrome sand (Pixel 9, 2026-11-17).
 *
 * Insets are dispatched to the decor view after `setDecorFitsSystemWindows(false)`, which is what an
 * edge-to-edge activity receives on a device.
 *
 *   ./gradlew :feature:onboarding:recordRoborazziAndroidHostTest
 *   ./gradlew :feature:onboarding:verifyRoborazziAndroidHostTest
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingNavBarInsetScreenshotTest(private val frame: Frame) {

    data class Frame(
        val name: String,
        val state: String,
        val w: Int,
        val h: Int,
        val dark: Boolean,
        /** Bottom nav bar (portrait) in dp: 48 = three-button, 24 = gesture. */
        val navBottomDp: Int = 0,
        /** Side nav bar (landscape three-button) in dp, on the end edge. */
        val navEndDp: Int = 0,
        /** Keyboard height in dp (0 = closed). */
        val imeDp: Int = 0,
    ) {
        override fun toString(): String = name
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun capture() {
        val state = OnboardingPreviewFixtures.all.toMap().getValue(frame.state)
        val land = if (frame.w > frame.h) "-land" else "-port"
        RuntimeEnvironment.setQualifiers("w${frame.w}dp-h${frame.h}dp$land-xhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    activity.setContent {
                        AppTheme(darkTheme = frame.dark) {
                            CompositionLocalProvider(
                                LocalReducedMotion provides true,
                                LocalSampleNoteImage provides SampleNotePlaceholder.create(),
                            ) {
                                OnboardingContent(state = state, onIntent = {}, snackbarHostState = SnackbarHostState())
                            }
                        }
                    }
                }
                composeRule.waitForIdle()
                scenario.onActivity { activity ->
                    val d = activity.resources.displayMetrics.density
                    fun px(dp: Int) = (dp * d).toInt()
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(24), 0, 0))
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, px(frame.navEndDp), px(frame.navBottomDp)),
                        )
                        .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px(frame.imeDp)))
                        .setVisible(WindowInsetsCompat.Type.ime(), frame.imeDp > 0)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
                }
                composeRule.waitForIdle()
                composeRule.onRoot().captureRoboImage(
                    filePath = "src/androidHostTest/roborazzi/onboarding_navbar_${frame.name}.png",
                )
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun frames(): List<Frame> = listOf(
            // The reported defect: the plate's sand must run under the nav bar, light and dark.
            Frame("talk_3btn_360x800_light", "talkDefault", 360, 800, dark = false, navBottomDp = 48),
            Frame("talk_3btn_360x800_dark", "talkDefault", 360, 800, dark = true, navBottomDp = 48),
            Frame("talk_gesture_360x800_light", "talkDefault", 360, 800, dark = false, navBottomDp = 24),
            // Keyboard up: the composer sits right on the keyboard, no nav-bar-height gap.
            Frame("talk_ime_360x800_light", "talkTyped", 360, 800, dark = false, navBottomDp = 48, imeDp = 280),
            // Landscape three-button nav on the side: content still clears it.
            Frame("talk_side_800x360_light", "talkDefault", 800, 360, dark = false, navEndDp = 48),
            // The other steps keep clearing the nav bar on the page colour.
            Frame("write_3btn_360x800_light", "writeDefault", 360, 800, dark = false, navBottomDp = 48),
        )
    }
}
