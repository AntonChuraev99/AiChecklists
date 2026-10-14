package com.antonchuraev.homesearchchecklist.activation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Matrix for [ActivationReminderSheet]: three modes × window size × font scale × theme, plus the
 * after-20:00 options, an 80+ char checklist name, the loading CTA, RU / HI and the 840dp dialog.
 * The sheet lives in its own window, so frames are taken with [captureScreenRoboImage].
 *
 *   ./gradlew :composeApp:recordRoborazziAndroidHostTest --tests "*ActivationReminderSheetScreenshotTest*"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivationReminderSheetScreenshotTest(
    private val name: String,
    private val qualifiers: String,
    private val fontScale: Float,
    private val dark: Boolean,
    private val mode: ActivationSheetMode,
    private val evening: Boolean,
    private val longName: Boolean,
    private val requesting: Boolean,
) {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun sheet() {
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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render() {
        val options = if (evening) {
            listOf(
                ActivationReminderOption(ReminderOptionDay.Tomorrow, 9, 0, 0L),
                ActivationReminderOption(ReminderOptionDay.Tomorrow, 20, 0, 0L),
            )
        } else {
            listOf(
                ActivationReminderOption(ReminderOptionDay.Today, 20, 0, 0L),
                ActivationReminderOption(ReminderOptionDay.Tomorrow, 9, 0, 0L),
            )
        }
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                ActivationReminderSheet(
                    checklistName = when {
                        longName -> LONG_NAME
                        name.startsWith("task_") -> "Call the dentist"
                        else -> "Weekend trip packing"
                    },
                    // task_* frames depict an Item target (first Inbox task) → task title (F9).
                    isTask = name.startsWith("task_"),
                    options = options,
                    selectedIndex = 0,
                    mode = mode,
                    isRequesting = requesting,
                    onSelect = {},
                    onConfirm = {},
                    onSkip = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenRoboImage("src/androidHostTest/roborazzi/activation_sheet/$name.png")
    }

    companion object {
        private const val LONG_NAME =
            "Weekend trip packing for the mountains with the kids, the dog and grandma's birthday gifts"

        private fun row(
            name: String,
            q: String,
            fs: Float,
            dark: Boolean = false,
            mode: ActivationSheetMode = ActivationSheetMode.CanAsk,
            evening: Boolean = false,
            longName: Boolean = false,
            requesting: Boolean = false,
        ): Array<Any> = arrayOf(name, q, fs, dark, mode, evening, longName, requesting)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = buildList {
            for ((w, h) in listOf(320 to 640, 360 to 800)) {
                for (fs in listOf(1.0f, 1.3f, 2.0f)) {
                    for (dark in listOf(false, true)) {
                        val theme = if (dark) "dark" else "light"
                        add(row("canask_${w}x${h}_fs${fs}_$theme", "w${w}dp-h${h}dp-xxhdpi", fs, dark))
                    }
                }
            }
            add(row("notifon_360_fs1.0_light", "w360dp-h800dp-xxhdpi", 1.0f, mode = ActivationSheetMode.NotificationsOn))
            add(row("settings_360_fs1.0_dark", "w360dp-h800dp-xxhdpi", 1.0f, dark = true, mode = ActivationSheetMode.SettingsRequired))
            add(row("settings_320_fs2.0_light", "w320dp-h640dp-xxhdpi", 2.0f, mode = ActivationSheetMode.SettingsRequired))
            add(row("evening_longname_360_fs1.0_light", "w360dp-h800dp-xxhdpi", 1.0f, evening = true, longName = true))
            add(row("task_360_fs1.0_light", "w360dp-h800dp-xxhdpi", 1.0f))
            add(row("requesting_360_fs1.0_light", "w360dp-h800dp-xxhdpi", 1.0f, requesting = true))
            add(row("ru_320_fs1.5_light", "ru-w320dp-h640dp-xxhdpi", 1.5f))
            add(row("ru_320_fs2.0_light", "ru-w320dp-h640dp-xxhdpi", 2.0f))
            add(row("hi_360_fs1.3_dark", "hi-w360dp-h800dp-xxhdpi", 1.3f, dark = true))
            add(row("dialog_840_fs1.0_light", "w840dp-h900dp-xhdpi", 1.0f))
        }
    }
}
