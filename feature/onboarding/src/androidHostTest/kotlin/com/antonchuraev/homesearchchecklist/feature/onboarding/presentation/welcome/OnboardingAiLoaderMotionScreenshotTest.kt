package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
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
 * The AI loader with motion ON (DESIGN_SPEC 2026-11-17 §L2): the active step is the real
 * indeterminate `CircularProgressIndicator`, which never lets Compose idle — so these frames run on
 * the compose test clock with `autoAdvance = false` and advance it by hand. The advance drives the
 * loader's own frame clock, i.e. it also proves the steps tick on that clock.
 * [OnboardingScreenshotMatrixTest] covers the loader with reduced motion (static ring).
 *
 *   ./gradlew :feature:onboarding:recordRoborazziAndroidHostTest
 *   ./gradlew :feature:onboarding:verifyRoborazziAndroidHostTest
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingAiLoaderMotionScreenshotTest(private val frame: Frame) {

    data class Frame(val name: String, val w: Int, val h: Int, val dark: Boolean, val lang: String, val advanceMs: Long) {
        override fun toString(): String = name
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capture() {
        RuntimeEnvironment.setQualifiers("w${frame.w}dp-h${frame.h}dp-port-xhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(frame.lang))
        try {
            composeRule.mainClock.autoAdvance = false
            composeRule.setContent {
                AppTheme(darkTheme = frame.dark) {
                    CompositionLocalProvider(LocalReducedMotion provides false) {
                        OnboardingContent(
                            state = OnboardingPreviewFixtures.aiGenerating,
                            onIntent = {},
                            snackbarHostState = SnackbarHostState(),
                        )
                    }
                }
            }
            composeRule.mainClock.advanceTimeBy(frame.advanceMs)
            composeRule.onRoot().captureRoboImage(
                filePath = "src/androidHostTest/roborazzi/onboarding_ailoader_${frame.name}.png",
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun frames(): List<Frame> = listOf(
            Frame("step1_360x800_light_en", 360, 800, dark = false, lang = "en", advanceMs = 600),
            Frame("step2_360x800_light_en", 360, 800, dark = false, lang = "en", advanceMs = 1_800),
            Frame("step4_360x800_dark_ru", 360, 800, dark = true, lang = "ru", advanceMs = 4_800),
            Frame("step2_360x800_light_hi", 360, 800, dark = false, lang = "hi", advanceMs = 1_800),
        )
    }
}
