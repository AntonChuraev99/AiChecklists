package com.antonchuraev.homesearchchecklist.mcp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guard frame for [McpContent]: its value-prop and note rows live in the shared
 * `integrations/IntegrationRows.kt` (also used by the Telegram screen), and MCP must keep rendering
 * exactly as before the lift.
 *
 *   ./gradlew :composeApp:recordRoborazziAndroidHostTest --tests "*McpContentScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class McpContentScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mcpContent_411_light() {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    McpContent(onCopyEndpoint = {}, onOpenGuide = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/androidHostTest/roborazzi/mcp/mcp_content_411_light.png")
    }
}
