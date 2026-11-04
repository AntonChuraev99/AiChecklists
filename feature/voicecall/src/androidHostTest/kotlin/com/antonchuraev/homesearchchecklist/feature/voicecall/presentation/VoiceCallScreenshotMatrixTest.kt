package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * DESIGN_SPEC §14 screenshot matrix of the voice call overlay: one PNG per (state × window × theme ×
 * locale × fontScale), all from [VoiceCallPreviewFixtures].
 *
 * Reduced motion is ON so frames are static (no infinite transition keeps Compose from idling):
 * the orb draws its reduced-motion poses (DESIGN_SPEC §4, column «Reduced motion»).
 *
 *   ./gradlew :feature:voicecall:recordRoborazziAndroidHostTest
 *   ./gradlew :feature:voicecall:verifyRoborazziAndroidHostTest
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceCallScreenshotMatrixTest(private val frame: Frame) {

    data class Frame(
        val no: Int,
        val state: String,
        val w: Int,
        val h: Int,
        val dark: Boolean,
        val lang: String,
        val fontScale: Float,
    ) {
        val fileName: String
            get() {
                val theme = if (dark) "dark" else "light"
                val fs = "fs" + (fontScale * 10).toInt()
                return "voiceCall_%02d_%s_%dx%d_%s_%s_%s".format(no, state, w, h, theme, lang, fs)
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
                                VoiceCallScreen(
                                    uiState = state,
                                    onAction = {},
                                    micLevel = { LEVEL },
                                    voiceLevel = { LEVEL },
                                )
                            }
                        }
                    }
                }
                composeRule.waitForIdle()
                composeRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/${frame.fileName}.png")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    companion object {
        private const val LEVEL = 0.6f
        private val fixtures = VoiceCallPreviewFixtures.all.toMap()

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun frames(): List<Frame> {
            var n = 0
            fun f(state: String, w: Int, h: Int, dark: Boolean, lang: String, fs: Float) =
                Frame(++n, state, w, h, dark, lang, fs)
            return listOf(
                f("listening_silence", 320, 640, false, "en", 1.0f),
                f("listening_hearing", 320, 640, false, "ru", 1.5f),
                f("listening_hearing", 412, 915, true, "ru", 1.0f),
                f("connecting", 360, 640, true, "en", 1.0f),
                f("thinking_tool", 320, 640, false, "ru", 1.3f),
                f("thinking_very_slow", 320, 640, false, "ru", 2.0f),
                f("speaking_captions", 412, 915, false, "ru", 1.0f),
                f("speaking_card_captions", 320, 640, true, "ru", 1.5f),
                f("speaking_card_captions", 320, 640, false, "ru", 2.0f),
                f("speaking_captions_off", 360, 640, false, "en", 1.0f),
                f("muted", 360, 640, true, "ru", 1.0f),
                f("reconnecting", 360, 640, false, "ru", 1.5f),
                f("paused_background", 320, 640, false, "ru", 1.5f),
                f("paused_interruption", 412, 915, true, "en", 1.0f),
                f("limit_warning_free", 320, 640, false, "ru", 1.5f),
                f("perm_request", 320, 640, false, "ru", 2.0f),
                f("perm_blocked", 320, 640, false, "ru", 2.0f),
                f("err_no_recognizer", 360, 640, true, "ru", 1.5f),
                f("err_no_voice", 360, 640, true, "ru", 1.5f),
                f("err_offline", 360, 640, true, "ru", 1.5f),
                f("err_server", 360, 640, true, "ru", 1.5f),
                f("err_flag_off", 360, 640, true, "ru", 1.5f),
                f("summary", 320, 640, false, "ru", 1.5f),
                f("summary_many", 360, 640, true, "ru", 1.0f),
                f("summary_nothing", 360, 640, false, "en", 1.0f),
                f("summary_dropped", 360, 640, false, "en", 1.0f),
                f("limit_free", 412, 915, true, "ru", 1.0f),
                f("limit_daily", 412, 915, true, "ru", 1.0f),
                f("speaking_card_captions", 800, 360, false, "ru", 1.0f),
                f("listening_silence", 840, 1200, false, "en", 1.0f),
                f("speaking_card_captions", 320, 640, false, "hi", 1.5f),
                // Edge data (DESIGN_SPEC §14 «Граничные данные»).
                f("speaking_card_long_title", 320, 640, false, "ru", 2.0f),
                f("timer_max", 320, 640, false, "ru", 1.5f),
                f("limit_warning_premium", 360, 640, true, "ru", 1.0f),
            )
        }
    }
}
