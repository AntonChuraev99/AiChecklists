package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.OutOfCreditsBannerState
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
 * DESIGN_SPEC 2026-11-26 matrix of the out-of-credits banner on its own: every variant of
 * [OutOfCreditsBannerState] × width 320/360 × light/dark × EN/RU/HI × fontScale 1.0/1.3/1.5, plus the
 * nudge peak. The banner sits on the bottom-chrome plane it lives on in the chat input slot.
 * Placement in the three chat surfaces is shot separately (phase B).
 *
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest --tests "*OutOfCreditsBannerScreenshotTest*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutOfCreditsBannerScreenshotTest(private val frame: Frame) {

    data class Frame(
        val no: Int,
        val state: String,
        val w: Int,
        val dark: Boolean,
        val lang: String,
        val fontScale: Float,
    ) {
        val fileName: String
            get() {
                val theme = if (dark) "dark" else "light"
                val fs = "fs" + (fontScale * 10).toInt()
                return "outOfCredits_%02d_%s_%d_%s_%s_%s".format(no, state, w, theme, lang, fs)
            }

        override fun toString(): String = fileName
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun capture() {
        RuntimeEnvironment.setQualifiers("w${frame.w}dp-h640dp-port-xhdpi")
        RuntimeEnvironment.setFontScale(frame.fontScale)
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(frame.lang))
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        AppTheme(darkTheme = frame.dark) {
                            CompositionLocalProvider(
                                LocalReducedMotion provides true,
                                LocalChatSurfaceTone provides ChatSurfaceTone.BottomChrome,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(AppSurface.bottomChrome())
                                        .padding(bottom = AppDimens.SpacingSm)
                                        .testTag(FRAME_TAG),
                                ) {
                                    if (frame.state == NUDGE_PEAK) {
                                        OutOfCreditsBannerCard(
                                            state = OutOfCreditsBannerState.Free(premiumDailyCredits = 300),
                                            flash = { 1f },
                                            notSent = true,
                                            onCtaClick = {},
                                            modifier = Modifier.padding(OutOfCreditsBannerDefaults.ContentPadding),
                                        )
                                    } else {
                                        OutOfCreditsBanner(
                                            state = states.getValue(frame.state),
                                            nudgeKey = 0,
                                            onCtaClick = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(FRAME_TAG)
                    .captureRoboImage("src/androidHostTest/roborazzi/${frame.fileName}.png")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    companion object {
        private const val FRAME_TAG = "frame"
        private const val NUDGE_PEAK = "nudge_peak"

        private val states = mapOf(
            "free" to OutOfCreditsBannerState.Free(premiumDailyCredits = 300),
            "free_1000" to OutOfCreditsBannerState.Free(premiumDailyCredits = 1000),
            "free_no_amount" to OutOfCreditsBannerState.Free(premiumDailyCredits = null),
            "free_low" to OutOfCreditsBannerState.Free(premiumDailyCredits = 300, lowBalance = true),
            "premium" to OutOfCreditsBannerState.Premium(),
            "premium_low" to OutOfCreditsBannerState.Premium(lowBalance = true),
            "guest" to OutOfCreditsBannerState.Guest,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun frames(): List<Frame> {
            var n = 0
            fun f(state: String, w: Int, dark: Boolean, lang: String, fs: Float) =
                Frame(++n, state, w, dark, lang, fs)
            return listOf(
                f("free", 360, false, "en", 1.0f),
                f("free", 360, true, "en", 1.0f),
                f("free", 360, false, "ru", 1.0f),
                f("free", 360, false, "ru", 1.3f),
                f("free", 360, false, "hi", 1.0f),
                f("free_1000", 320, false, "ru", 1.5f),
                f("free", 320, true, "hi", 1.5f),
                f("free_no_amount", 360, false, "en", 1.0f),
                f("free_no_amount", 320, false, "ru", 1.3f),
                f("free_low", 360, false, "en", 1.0f),
                f("free_low", 320, true, "ru", 1.3f),
                f("premium", 360, false, "en", 1.0f),
                f("premium", 360, true, "hi", 1.0f),
                f("premium", 360, false, "ru", 1.3f),
                f("premium", 320, false, "ru", 1.5f),
                f("premium_low", 360, false, "en", 1.0f),
                f("guest", 360, false, "en", 1.0f),
                f("guest", 360, true, "hi", 1.0f),
                f("guest", 360, false, "ru", 1.3f),
                f("guest", 320, false, "ru", 1.5f),
                f(NUDGE_PEAK, 360, false, "en", 1.0f),
                f(NUDGE_PEAK, 360, true, "en", 1.0f),
            )
        }
    }
}
