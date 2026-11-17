@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiExpandableDockContent
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatEmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatInputRow
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Report frames for the owner's layout-debug wave A (2026-11-16) on the v2 chat sheet: the REAL
 * [V2NavigationShell] (Compact) hosting the REAL [V2ChatDockOverlay], with a system navigation bar
 * actually present — under plain Robolectric `WindowInsets.navigationBars` is 0 and the strip under
 * the sheet would not exist in the frame at all.
 *
 * Two hosts, because the shell paints two different things under the overlay: a tab (the plate is
 * up) and a pushed route (the plate is gone, the hosted AppScaffold's bottom bar reaches the window
 * bottom).
 *
 * Forced Record into `build/ux-report/layout-debug`, never a golden.
 *   ./gradlew :composeApp:testAndroidHostTest --tests "*ChatDockNavStripReportTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDockNavStripReportTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tab_threeButtonNav_light() = frame("tab_3btn_light", pushedRoute = false, navDp = 48, dark = false)

    @Test
    fun tab_threeButtonNav_dark() = frame("tab_3btn_dark", pushedRoute = false, navDp = 48, dark = true)

    @Test
    fun pushed_threeButtonNav_light() = frame("pushed_3btn_light", pushedRoute = true, navDp = 48, dark = false)

    @Test
    fun pushed_gestureNav_dark() = frame("pushed_gesture_dark", pushedRoute = true, navDp = 24, dark = true)

    /**
     * The frame the owner actually pointed at: a pushed route (checklist detail) whose AppScaffold
     * bottomBar holds the bottom-chrome capture plate, chat CLOSED. The slot is padded by
     * `ime ∪ navigationBars`, so the system-nav zone under the plate is whatever the Scaffold paints.
     */
    @Test
    fun pushed_plate_threeButtonNav_light() =
        frame("pushed_plate_3btn_light", pushedRoute = true, navDp = 48, dark = false, chat = false)

    @Test
    fun pushed_plate_threeButtonNav_dark() =
        frame("pushed_plate_3btn_dark", pushedRoute = true, navDp = 48, dark = true, chat = false)

    private fun frame(name: String, pushedRoute: Boolean, navDp: Int, dark: Boolean, chat: Boolean = true) {
        val activity = composeTestRule.activity
        composeTestRule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(activity.window, false) }
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) { ShellFixture(pushedRoute = pushedRoute, chat = chat) }
        }
        composeTestRule.mainClock.advanceTimeBy(SettleMs)
        val px = (navDp * activity.resources.displayMetrics.density).toInt()
        val statusPx = (24 * activity.resources.displayMetrics.density).toInt()
        composeTestRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(
                activity.window.decorView,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusPx, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px))
                    .build(),
            )
        }
        composeTestRule.mainClock.advanceTimeBy(SettleMs)
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/ux-report/layout-debug/navstrip_$name.png",
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    private companion object {
        const val SettleMs = 3_000L
    }
}

@Composable
private fun ShellFixture(pushedRoute: Boolean, chat: Boolean) {
    V2NavigationShell(
        selectedTab = V2Destination.Inbox,
        onNavigate = {},
        onOpenChat = {},
        onOpenSettings = {},
        onOpenUpdates = {},
        barVisible = !pushedRoute,
        chatOpen = chat,
        overlayContent = {
            V2ChatDockOverlay(
                visible = chat,
                onDismissRequest = {},
                chatDockContent = { dockState, placeholder, dockAvailableDp, chips, _, onOpenFull ->
                    DockContent(dockState, placeholder, dockAvailableDp, chips, onOpenFull)
                },
                chatFullContent = { _, _ -> },
                peekPlaceholder = "Ask Gisti…",
                chips = {},
            )
        },
    ) {
        AppScaffold(
            title = "Groceries",
            // What ChecklistDetailScreen passes for its capture plate (v2 Standard, not editing).
            bottomBarInsetColor = AppSurface.bottomChrome(),
            bottomBar = {
                // Stands in for the detail screen's capture plate (`DetailCapturePlate`, private):
                // the same bottom-chrome Surface with the SheetTop shoulders.
                Surface(
                    color = AppSurface.bottomChrome(),
                    shape = AppShapeTokens.SheetTop,
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                ) {}
            },
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenPaddingHorizontal)) {
                listOf("Buy milk", "Book the dentist", "Send the invoice", "Water the plants").forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = AppDimens.SpacingMd),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The chat-mode slice of the `chatDockContent` lambda in `App.kt`. */
@Composable
private fun DockContent(
    dockState: AnchoredDraggableState<DockAnchor>,
    placeholder: String,
    dockAvailableDp: Dp,
    chips: @Composable () -> Unit,
    onOpenFull: () -> Unit,
) {
    GistiExpandableDockContent(
        state = dockState,
        hasLastAnswer = false,
        dockAvailableDp = dockAvailableDp,
        onExpandFull = onOpenFull,
        onHelpClick = {},
        chipsContent = chips,
        inputFocusRequester = null,
        inputBlank = true,
        keepExpanded = true,
        lastAnswerContent = {},
        emptyStateContent = {
            ChatEmptyState(
                compact = true,
                onPrefill = {},
                modifier = Modifier.padding(vertical = AppDimens.SpacingMd),
            )
        },
        inputContent = { onInputFocusChanged ->
            ChatInputRow(
                text = "",
                onTextChange = {},
                onSend = {},
                onAttachFileClick = {},
                onVoiceRecordingStarted = {},
                onVoiceRecordingStopped = {},
                onVoiceRecordingCancelled = {},
                canSend = false,
                onFocusChanged = onInputFocusChanged,
                placeholderOverride = placeholder,
            )
        },
        recordingOverlay = {},
    )
}
