package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_voice_permission_granted
import aichecklists.core.designsystem.generated.resources.analyze_voice_playback_failed
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.jetbrains.compose.resources.stringResource
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
 * The Analyze voice editor's two dialogs.
 *
 * `playbackError_*` — the recorded clip failed to PLAY. The player reports a raw platform message
 * (the `MediaPlayer` exception text on Android, a hard-coded English "not supported" stub on web).
 * It used to go straight into the error dialog, so a Russian or Hindi user read an English
 * stack-trace fragment. Now every player error maps to `analyze_voice_playback_failed` and the raw
 * text goes to the log. The frame renders the state after that mapping.
 *
 * `permissionGrantedHint_*` — the "Microphone is on. Tap record to start." guidance. It used to be
 * routed through the screen's error state and appeared under an "Error" title. It is a hint: the
 * same dialog, no title.
 *
 * Dialogs are their own window, so frames are taken with `captureScreenRoboImage`.
 *
 *   ./gradlew :feature:analyze:recordRoborazziAndroidHostTest --tests "*AnalyzePlaybackErrorScreenshotTest*"
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyzePlaybackErrorScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** The voice section resolves its logger through `koinInject()`. */
    @get:Rule
    val koinRule = NoOpLoggerKoinRule()

    private val defaultLocale: Locale = Locale.getDefault()

    /** `Locale.setDefault` is JVM-global and Gradle reuses one JVM per test task. */
    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun playbackError_360_light() = shoot("w360dp-h640dp") { PlaybackFailed() }

    @Test
    fun playbackError_320_dark_ru_fontScale15() =
        shoot("w320dp-h568dp", dark = true, fontScale = 1.5f, locale = Locale("ru")) { PlaybackFailed() }

    @Test
    fun permissionGrantedHint_360_light() = shoot("w360dp-h640dp") { GrantedHint() }

    @Test
    fun permissionGrantedHint_320_dark_ru_fontScale15() =
        shoot("w320dp-h568dp", dark = true, fontScale = 1.5f, locale = Locale("ru")) { GrantedHint() }

    @Composable
    private fun PlaybackFailed() {
        val message = stringResource(Res.string.analyze_voice_playback_failed)
        AnalyzeScreenContent(
            screenState = AnalyzeScreenState(
                selectedInputType = InputDataType.VOICE,
                recordedAudioPath = "/tmp/voice-2026-10-14.m4a",
                recordedAudioDuration = 4_000L,
                error = message,
            ),
            onIntent = {},
        )
    }

    /** The screen in the state the hint appears over (voice source, nothing recorded) + the hint. */
    @Composable
    private fun GrantedHint() {
        val message = stringResource(Res.string.analyze_voice_permission_granted)
        AnalyzeScreenContent(
            screenState = AnalyzeScreenState(selectedInputType = InputDataType.VOICE),
            onIntent = {},
        )
        AnalyzeMessageDialog(message = message, onDismiss = {})
    }

    // ── Microphone blocked for good ─────────────────────────────────────────
    // `app` = a platform that can open app settings (Android); `web` = cannot (browser "Block"):
    // the dead "Open Settings" button is gone and the text points at the address-bar lock instead.

    @Test
    fun permissionBlocked_app_360_light() = shoot("w360dp-h640dp") { Blocked(canOpenAppSettings = true) }

    @Test
    fun permissionBlocked_app_320_dark_ru_fontScale15() =
        shoot("w320dp-h568dp", dark = true, fontScale = 1.5f, locale = Locale("ru")) {
            Blocked(canOpenAppSettings = true)
        }

    @Test
    fun permissionBlocked_web_360_light_ru_fontScale15() =
        shoot("w360dp-h640dp", fontScale = 1.5f, locale = Locale("ru")) { Blocked(canOpenAppSettings = false) }

    @Test
    fun permissionBlocked_web_320_dark_ru_fontScale15() =
        shoot("w320dp-h568dp", dark = true, fontScale = 1.5f, locale = Locale("ru")) {
            Blocked(canOpenAppSettings = false)
        }

    @Composable
    private fun Blocked(canOpenAppSettings: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(AppDimens.ScreenPaddingHorizontal),
        ) {
            VoicePermissionBlockedCard(
                canOpenAppSettings = canOpenAppSettings,
                onOpenAppSettings = {},
                onTryAgain = {},
            )
        }
    }

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) { content() }
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage()
    }
}
