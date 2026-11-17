package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Report frames for #8 of the owner's layout-debug wave A (2026-11-16): the PRESSED area of a source
 * pill. Robolectric does not draw the M3 ripple, so the frames swap [LocalIndication] for a loud
 * magenta probe that fills exactly the region the real ripple would fill — a square block past the
 * capsule's ends is the defect, a magenta capsule is the fix.
 *
 * Forced Record into `build/ux-report/layout-debug`, never a golden.
 *   ./gradlew :core:designsystem:testAndroidHostTest --tests "*PillPressAreaReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h240dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillPressAreaReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sourcePill_page_pressed() = pressed("sourcepill_page_pressed", onChrome = false)

    @Test
    fun sourcePill_dock_pressed() = pressed("sourcepill_dock_pressed", onChrome = true)

    private fun pressed(name: String, onChrome: Boolean) {
        composeTestRule.setContent {
            AppTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalIndication provides ProbeIndication,
                    LocalChatSurfaceTone provides if (onChrome) ChatSurfaceTone.BottomChrome else ChatSurfaceTone.Page,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (onChrome) AppSurface.bottomChrome() else MaterialTheme.colorScheme.background,
                            )
                            .padding(16.dp),
                    ) {
                        SourceRow(onSelect = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Photo")[0].performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/ux-report/layout-debug/$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }
}

/** Fills the node's (clipped) bounds with magenta while pressed — a ripple stand-in Robolectric draws. */
private object ProbeIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = ProbeNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private class ProbeNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(Color.Magenta.copy(alpha = 0.7f))
    }
}
