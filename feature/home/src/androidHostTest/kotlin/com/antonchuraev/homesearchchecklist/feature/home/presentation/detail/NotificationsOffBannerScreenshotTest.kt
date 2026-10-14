package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Matrix for [NotificationsOffBanner]: every frame stacks the three copy states (generic / reminder /
 * settings-required) so one PNG shows the whole state matrix at one width × font scale × theme.
 * The window is tall on purpose — the banner is a list item, height is not the axis under test.
 *
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*NotificationsOffBannerScreenshotTest*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationsOffBannerScreenshotTest(
    private val name: String,
    private val qualifiers: String,
    private val fontScale: Float,
    private val dark: Boolean,
) {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun banner() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        // Compose Resources resolve the language from the JVM default locale, not the qualifier.
        val lang = qualifiers.substringBefore('-').takeIf { it.length == 2 }
        val previous = java.util.Locale.getDefault()
        if (lang != null) java.util.Locale.setDefault(java.util.Locale(lang))
        try {
            render()
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    private fun render() {
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(AppDimens.SpacingLg),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
                ) {
                    BANNERS.forEach { banner ->
                        NotificationsOffBanner(
                            banner = banner,
                            onCta = {},
                            onDismiss = {},
                            modifier = Modifier.adaptiveContentWidth(),
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/notif_banner/$name.png")
    }

    companion object {
        private val BANNERS = listOf(
            NotificationsBannerUi(reminderVariant = false, settingsRequired = false),
            NotificationsBannerUi(reminderVariant = true, settingsRequired = false),
            NotificationsBannerUi(reminderVariant = true, settingsRequired = true),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = buildList {
            for (w in listOf(320, 360)) {
                for (fs in listOf(1.0f, 1.3f, 2.0f)) {
                    for (dark in listOf(false, true)) {
                        val theme = if (dark) "dark" else "light"
                        add(arrayOf("w${w}_fs${fs}_$theme", "w${w}dp-h1400dp-xxhdpi", fs, dark))
                    }
                }
            }
            add(arrayOf("w840_fs1.0_light", "w840dp-h1400dp-xhdpi", 1.0f, false))
            add(arrayOf("ru_w320_fs2.0_light", "ru-w320dp-h1400dp-xxhdpi", 2.0f, false))
            add(arrayOf("hi_w320_fs1.3_dark", "hi-w320dp-h1400dp-xxhdpi", 1.3f, true))
        }
    }
}
