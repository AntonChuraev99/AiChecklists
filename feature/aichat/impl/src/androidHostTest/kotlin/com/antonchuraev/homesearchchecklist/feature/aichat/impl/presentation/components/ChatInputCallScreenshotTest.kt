package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatInputRow
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
 * The chat composer's voice-call button (owner, layout-debug 2026-11-16: «звонок можно начать с
 * главного экрана или в чате»), on the chat-dock plane ([AppSurface.chatSheet]) with the dock's "?"
 * leading slot.
 *
 * Each frame stacks: the row WITHOUT a call entry (before / web / flag off) · idle with the call ·
 * recording (call greyed) · reply processing (call greyed) · text typed (call hidden, the mic is Send).
 *
 *   ./gradlew :feature:aichat:impl:recordRoborazziAndroidHostTest --tests "*ChatInputCallScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatInputCallScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test fun call_360_light() = shoot("w360dp-h700dp")

    @Test fun call_360_dark() = shoot("w360dp-h700dp", dark = true)

    @Test fun call_320_light() = shoot("w320dp-h700dp")

    @Test fun call_320_dark() = shoot("w320dp-h700dp", dark = true)

    @Test fun call_320_light_ru_fontScale13() =
        shoot("ru-rRU-w320dp-h700dp", fontScale = 1.3f, locale = Locale("ru", "RU"))

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
    ) {
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
                            .background(AppSurface.chatSheet())
                            .padding(vertical = AppDimens.SpacingLg),
                    ) {
                        Label("before: no call entry")
                        Row(hasCall = false)
                        Label("idle")
                        Row()
                        Label("recording")
                        Row(isRecording = true)
                        Label("processing reply")
                        Row(isEnabled = false)
                        Label("text typed")
                        Row(text = "Buy milk", canSend = true)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(PIN_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Composable
    private fun Label(text: String) = Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
    )

    @Composable
    private fun Row(
        hasCall: Boolean = true,
        text: String = "",
        canSend: Boolean = false,
        isRecording: Boolean = false,
        isEnabled: Boolean = true,
    ) = ChatInputRow(
        text = text,
        onTextChange = {},
        onSend = {},
        onAttachFileClick = {},
        onVoiceRecordingStarted = {},
        onVoiceRecordingStopped = {},
        onVoiceRecordingCancelled = {},
        canSend = canSend,
        isRecording = isRecording,
        isEnabled = isEnabled,
        placeholderOverride = "Ask Gisti…",
        onCallClick = if (hasCall) ({}) else null,
    )

    private companion object {
        const val PIN_MS = 600L
    }
}
