package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden for [RecurringNudgeCard]. Guards the `InlineNudgeCard` extraction: the recurring nudge
 * must stay pixel-identical after it became a thin wrapper.
 *
 *   ./gradlew :feature:home:recordRoborazziAndroidHostTest --tests "*InlineNudgeScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-normal-long-notround-any-420dpi-keyshidden-nonav")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineNudgeScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun recurringNudge_light() = capture(darkTheme = false)

    @Test
    fun recurringNudge_dark() = capture(darkTheme = true)

    private fun capture(darkTheme: Boolean) {
        composeTestRule.setContent {
            AppTheme(darkTheme = darkTheme) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(AppDimens.SpacingLg),
                ) {
                    RecurringNudgeCard(onAccept = {}, onDismiss = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
