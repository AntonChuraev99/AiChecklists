package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
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
 * The input row while a voice recording is being transcribed, on the PAGE plane (full ChatScreen).
 *
 * `transcribe_audio` can take tens of seconds on a cold instance. Before this state had its own hint
 * the row showed a lone 20dp spinner in the trailing slot next to the ordinary "Message Gisti"
 * placeholder — the field looked empty and idle, and the wait read as a freeze. Each frame stacks
 * the idle row above the transcribing one so the difference is visible in one image.
 *
 * The chat-dock (bottom-chrome plane) half of the same state is framed by
 * `composeApp/.../ChatDockVoiceHoldScreenshotTest` (`*_transcribing*`).
 *
 * The spinner never goes idle, so the clock is pinned by hand.
 *
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest --tests "*ChatInputTranscribingScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatInputTranscribingScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    /** `Locale.setDefault` is JVM-global and Gradle reuses one JVM for the whole task. */
    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test fun transcribing_360_light() = shoot("w360dp-h400dp")

    @Test fun transcribing_320_dark_fontScale15() = shoot("w320dp-h400dp", dark = true, fontScale = 1.5f)

    @Test fun transcribing_320_light_ru_fontScale15() =
        shoot("ru-rRU-w320dp-h400dp", fontScale = 1.5f, locale = Locale("ru", "RU"))

    @Test fun transcribing_320_light_hi_fontScale15() =
        shoot("hi-rIN-w320dp-h400dp", fontScale = 1.5f, locale = Locale("hi", "IN"))

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ) {
        // Compose Resources reads the JVM default locale, not the Robolectric qualifier.
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = AppDimens.SpacingLg),
                    ) {
                        Label("idle")
                        Row(isTranscribing = false)
                        Label("transcribing")
                        Row(isTranscribing = true)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(PIN_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    @androidx.compose.runtime.Composable
    private fun Label(text: String) = Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
    )

    @androidx.compose.runtime.Composable
    private fun Row(isTranscribing: Boolean) = ChatInputRow(
        text = "",
        onTextChange = {},
        onSend = {},
        onAttachFileClick = {},
        onVoiceRecordingStarted = {},
        onVoiceRecordingStopped = {},
        onVoiceRecordingCancelled = {},
        canSend = false,
        isTranscribing = isTranscribing,
    )

    private companion object {
        const val PIN_MS = 600L
    }
}
