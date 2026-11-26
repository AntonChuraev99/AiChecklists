@file:OptIn(ExperimentalFoundationApi::class)

package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatInputRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiExpandableDockContent
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiFullChatOverlay
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.rememberDockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatEmptyState
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.OutOfCreditsBannerState
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.OutOfCreditsBanner
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
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
 * The out-of-credits banner in the two App-level chat hosts (DESIGN_SPEC credits banner §6, §11):
 * the v2 dock (Expanded, and the control-arm Peek strip) and the full-screen chat overlay.
 *
 * Real: [V2ChatDockOverlay], [GistiExpandableDockContent], [GistiFullChatOverlay], [ChatInputRow],
 * [OutOfCreditsBanner]. Fake: the `inputContent` slot wiring — a copy of the two `inputContent`
 * lambdas in `App.kt` reduced to banner + input (no ViewModel). A change to App.kt's slot that drops
 * the banner is NOT caught here; ChatScreen's own slot is covered by ChatScreenRedesignScreenshotTest.
 *
 *   ./gradlew :composeApp:recordRoborazziAndroidHostTest --tests "*ChatDockOutOfCreditsScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDockOutOfCreditsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restore() {
        Locale.setDefault(defaultLocale)
        RuntimeEnvironment.setQualifiers(Phone360)
    }

    // ── v2 dock, Expanded ───────────────────────────────────────────────────

    @Test fun dockExpanded_360_light_free() = shoot(Phone360) { V2Dock(Free) }

    @Test fun dockExpanded_360_dark_free() = shoot(Phone360, dark = true) { V2Dock(Free) }

    @Test fun dockExpanded_360_light_ru_fontScale13_free() =
        shoot(Phone360, fontScale = 1.3f, locale = Ru) { V2Dock(Free) }

    @Test fun dockExpanded_360_light_premium() = shoot(Phone360) { V2Dock(OutOfCreditsBannerState.Premium()) }

    @Test fun dockExpanded_360_light_guest() = shoot(Phone360) { V2Dock(OutOfCreditsBannerState.Guest) }

    @Test fun dockExpanded_840_light_guest() = shoot(Tablet840) { V2Dock(OutOfCreditsBannerState.Guest) }

    // ── control-arm dock resting at Peek ────────────────────────────────────

    @Test fun dockPeek_360_light_free() = shoot(Phone360) { PeekDock(Free) }

    @Test fun dockPeek_320_light_ru_fontScale15_guest() =
        shoot(Phone320, fontScale = 1.5f, locale = Ru) { PeekDock(OutOfCreditsBannerState.Guest) }

    @Test fun dockPeek_360_dark_hi_fontScale13_premium() =
        shoot(Phone360, dark = true, fontScale = 1.3f, locale = Hi) { PeekDock(OutOfCreditsBannerState.Premium()) }

    // ── full-screen chat overlay ────────────────────────────────────────────

    @Test fun overlay_360_light_free() = shoot(Phone360) { FullOverlay(Free) }

    @Test fun overlay_360_dark_low() =
        shoot(Phone360, dark = true) { FullOverlay(Free.copy(lowBalance = true)) }

    // ── Harness ─────────────────────────────────────────────────────────────

    private fun shoot(
        qualifiers: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        locale: Locale = Locale.ENGLISH,
        content: @Composable () -> Unit,
    ) {
        Locale.setDefault(locale)
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                AppTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        HomeStub()
                        content()
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(SettleMs)
        composeTestRule.onRoot().captureRoboImage()
    }

    private companion object {
        const val Phone360 = "w360dp-h640dp"
        const val Phone320 = "w320dp-h640dp"
        const val Tablet840 = "w840dp-h900dp"
        const val SettleMs = 3_000L
        val Ru = Locale("ru", "RU")
        val Hi = Locale("hi", "IN")
        val Free = OutOfCreditsBannerState.Free(premiumDailyCredits = 300)
    }
}

private const val Placeholder = "Ask Gisti…"

@Composable
private fun V2Dock(banner: OutOfCreditsBannerState) {
    V2ChatDockOverlay(
        visible = true,
        onDismissRequest = {},
        chatDockContent = { dockState, placeholder, dockAvailableDp, _, _, onOpenFull ->
            // Chips are null while the banner is up (App.kt chipsContent).
            DockContent(banner, dockState, placeholder, dockAvailableDp, onOpenFull)
        },
        chatFullContent = { _, _ -> },
        peekPlaceholder = Placeholder,
        chips = {},
    )
}

@Composable
private fun BoxScopeAlignedPeek(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

@Composable
private fun PeekDock(banner: OutOfCreditsBannerState) {
    val dockState = remember { AnchoredDraggableState(initialValue = DockAnchor.Peek) }
    BoxScopeAlignedPeek { DockContent(banner, dockState, Placeholder, Dp.Unspecified, {}) }
}

/** The dock half of App.kt's `chatDockContent` in chat mode, reduced to banner + input. */
@Composable
private fun DockContent(
    banner: OutOfCreditsBannerState,
    dockState: AnchoredDraggableState<DockAnchor>,
    placeholder: String,
    dockAvailableDp: Dp,
    onOpenFull: () -> Unit,
) {
    GistiExpandableDockContent(
        state = dockState,
        hasLastAnswer = false,
        dockAvailableDp = dockAvailableDp,
        onExpandFull = onOpenFull,
        onHelpClick = {},
        chipsContent = null,
        inputBlank = true,
        keepExpanded = false,
        lastAnswerContent = {},
        emptyStateContent = {
            ChatEmptyState(compact = true, onPrefill = {}, modifier = Modifier.padding(vertical = AppDimens.SpacingMd))
        },
        inputContent = { onInputFocusChanged ->
            Column {
                OutOfCreditsBanner(state = banner, nudgeKey = 0, onCtaClick = {})
                ChatInputRow(
                    text = "",
                    onTextChange = {},
                    onSend = {},
                    onAttachFileClick = {},
                    onVoiceRecordingStarted = {},
                    onVoiceRecordingStopped = {},
                    onVoiceRecordingCancelled = {},
                    onFocusChanged = onInputFocusChanged,
                    placeholderOverride = placeholder,
                )
            }
        },
    )
}

/** App.kt's `chatFullContent`, reduced: an empty history and the banner + input slot. */
@Composable
private fun FullOverlay(banner: OutOfCreditsBannerState) {
    val state = rememberDockFullExpandState()
    LaunchedEffect(state) {
        snapshotFlow { state.anchored.offset }.first { !it.isNaN() }
        state.open()
    }
    GistiFullChatOverlay(
        state = state,
        dockStartHeightPx = 0,
        onCollapse = {},
        containerColor = AppSurface.chatSheet(),
        historyContent = { Box(Modifier.fillMaxSize()) },
        inputContent = {
            Column {
                OutOfCreditsBanner(state = banner, nudgeKey = 0, onCtaClick = {})
                ChatInputRow(
                    text = "Add milk",
                    onTextChange = {},
                    onSend = {},
                    onAttachFileClick = {},
                    onVoiceRecordingStarted = {},
                    onVoiceRecordingStopped = {},
                    onVoiceRecordingCancelled = {},
                    canSend = true,
                )
            }
        },
    )
}

@Composable
private fun HomeStub() {
    Column(Modifier.fillMaxSize().padding(horizontal = AppDimens.ScreenPaddingHorizontal)) {
        Text(
            text = "Inbox",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
        )
        listOf("Buy milk", "Book the dentist", "Send the invoice", "Water the plants").forEach { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = AppDimens.SpacingMd),
            )
            HorizontalDivider()
        }
    }
}
