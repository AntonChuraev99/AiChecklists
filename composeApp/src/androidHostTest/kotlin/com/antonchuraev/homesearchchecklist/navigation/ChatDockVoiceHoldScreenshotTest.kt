@file:OptIn(ExperimentalFoundationApi::class)

package com.antonchuraev.homesearchchecklist.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiExpandableDockContent
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatEmptyState
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatRecordingOverlay
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chat dock must stay on screen for the whole hold-to-record → transcription → text flow.
 *
 * ## What broke
 * A hold-to-record disables the input, the disabled field drops focus, and the dock read that blur
 * as "keyboard dismissed, input blank → collapse to Peek". In the v2 shell a settle at Peek is a
 * dismissal (`V2ChatDockOverlay` unmounts the dock), so the recording timer, the transcription
 * spinner and finally the recognised text all landed in a dock that was no longer there — the user
 * saw the plain home screen and concluded nothing had happened.
 *
 * ## What is real here and what is not
 * Real: [V2ChatDockOverlay] (the host that dismisses), `GistiExpandableDockContent` (the dock that
 * collapses), [ChatInputRow] (the field that blurs when disabled), [ChatRecordingOverlay], and
 * [chatDockKeepExpanded] — the production predicate `App.kt` feeds the dock.
 * Fake: the three flags a `ChatViewModel` would own, and the slot wiring itself, which is a copy of
 * the `chatDockContent` lambda in `App.kt` reduced to what this flow touches. A change to that
 * lambda that stops calling [chatDockKeepExpanded] is NOT caught here.
 *
 * Every test asserts the input was focused before the recording starts: without focus there is no
 * blur, the collapse never runs, and the frames would be green on the broken code as well.
 *
 * The clock is driven by hand — the recording dots, the spinner and the caret never go idle.
 *
 * Record + inspect:
 *   ./gradlew :composeApp:recordRoborazziAndroidHostTest --tests "*ChatDockVoiceHoldScreenshotTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDockVoiceHoldScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetQualifiers() = RuntimeEnvironment.setQualifiers(Phone360)

    // ── Frames ───────────────────────────────────────────────────────────────

    @Test
    fun voiceDock_360_light_idleOpen() {
        val voice = openDock()
        composeTestRule.onRoot().captureRoboImage()
        assertDockOpen(voice)
    }

    @Test
    fun voiceDock_360_light_recording() = recordingFrame()

    @Test
    fun voiceDock_360_dark_recording() = recordingFrame(dark = true)

    @Test
    fun voiceDock_320_light_recording_fontScale15() = recordingFrame(Phone320, fontScale = 1.5f)

    @Test
    fun voiceDock_360_light_transcribing() = transcribingFrame()

    @Test
    fun voiceDock_320_dark_transcribing_fontScale15() =
        transcribingFrame(Phone320, dark = true, fontScale = 1.5f)

    @Test
    fun voiceDock_360_light_result() {
        val voice = openDock()
        startRecording(voice)
        stopRecording(voice)
        mutate {
            voice.isTranscribing = false
            voice.inputText = RecognisedText
        }
        composeTestRule.onRoot().captureRoboImage()
        assertDockOpen(voice)
    }

    @Test
    fun voiceDock_360_dark_result_fontScale15() {
        val voice = openDock(dark = true, fontScale = 1.5f)
        startRecording(voice)
        stopRecording(voice)
        mutate {
            voice.isTranscribing = false
            voice.inputText = RecognisedText
        }
        composeTestRule.onRoot().captureRoboImage()
        assertDockOpen(voice)
    }

    // ── Behaviour without a frame ────────────────────────────────────────────

    /**
     * The hold is sampled at the blur and is not a latch: a cancelled recording with a blank input
     * leaves the dock where the user had it, and the very next blur dismisses it as it always did.
     */
    @Test
    fun cancelledRecording_blankInput_releasesTheHold() {
        val voice = openDock()
        startRecording(voice)
        mutate { voice.isRecording = false }
        assertDockOpen(voice)

        composeTestRule.runOnUiThread { voice.focusRequester.requestFocus() }
        advance()
        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()

        composeTestRule.runOnUiThread { voice.focusManager?.clearFocus() }
        advance()
        assertFalse(
            "a blur with a blank input and nothing in flight must still collapse the dock",
            voice.dockMounted,
        )
    }

    /**
     * The control-arm hosts (MainScreen, ChecklistDetailScreen) keep the dock mounted at Peek, so
     * there the same blur shrank the panel instead of removing it. Same dock, same predicate.
     */
    @Test
    fun peekHost_recording_keepsTheDockExpanded() {
        val voice = openDock(host = Host.PeekStrip)
        startRecording(voice)
        assertEquals(DockAnchor.Expanded, voice.dockState?.targetValue)
        stopRecording(voice)
        assertEquals(DockAnchor.Expanded, voice.dockState?.targetValue)
    }

    // ── Harness ──────────────────────────────────────────────────────────────

    private fun recordingFrame(
        qualifiers: String = Phone360,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        val voice = openDock(qualifiers, dark, fontScale)
        startRecording(voice)
        composeTestRule.onRoot().captureRoboImage()
        assertDockOpen(voice)
    }

    private fun transcribingFrame(
        qualifiers: String = Phone360,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        val voice = openDock(qualifiers, dark, fontScale)
        startRecording(voice)
        stopRecording(voice)
        composeTestRule.onRoot().captureRoboImage()
        assertDockOpen(voice)
    }

    private fun openDock(
        qualifiers: String = Phone360,
        dark: Boolean = false,
        fontScale: Float = 1f,
        host: Host = Host.V2Overlay,
    ): VoiceFixtureState {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeTestRule.mainClock.autoAdvance = false
        val voice = VoiceFixtureState()
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(darkTheme = dark) { VoiceDockFixture(voice, host) }
            }
        }
        advance()
        // The precondition the whole file rests on: no focus → no blur → nothing to prove.
        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
        return voice
    }

    private fun startRecording(voice: VoiceFixtureState) = mutate { voice.isRecording = true }

    private fun stopRecording(voice: VoiceFixtureState) = mutate {
        voice.isRecording = false
        voice.isTranscribing = true
    }

    private fun mutate(block: () -> Unit) {
        composeTestRule.runOnUiThread(block)
        Snapshot.sendApplyNotifications()
        advance()
    }

    /** Long enough for the bouncy collapse, the overlay's exit and the host's unmount to all finish. */
    private fun advance() = composeTestRule.mainClock.advanceTimeBy(SettleMs)

    private fun assertDockOpen(voice: VoiceFixtureState) {
        assertTrue("the dock was dismissed while the voice flow was on screen", voice.dockMounted)
        assertEquals(DockAnchor.Expanded, voice.dockState?.targetValue)
    }

    private companion object {
        const val Phone360 = "w360dp-h640dp"
        const val Phone320 = "w320dp-h568dp"
        const val SettleMs = 3_000L
        const val RecognisedText = "Remind me to call the dentist tomorrow at nine"
    }
}

private enum class Host { V2Overlay, PeekStrip }

/** The three flags `ChatViewModel` owns in production, plus what the assertions need to read. */
private class VoiceFixtureState {
    var isRecording by mutableStateOf(false)
    var isTranscribing by mutableStateOf(false)
    var inputText by mutableStateOf("")
    var dockMounted by mutableStateOf(true)
    var dockState: AnchoredDraggableState<DockAnchor>? = null
    var focusManager: FocusManager? = null
    val focusRequester = FocusRequester()
}

@Composable
private fun VoiceDockFixture(voice: VoiceFixtureState, host: Host) {
    val focusManager = LocalFocusManager.current
    SideEffect { voice.focusManager = focusManager }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeStub()
        when (host) {
            Host.V2Overlay -> V2ChatDockOverlay(
                visible = voice.dockMounted,
                onDismissRequest = { voice.dockMounted = false },
                chatDockContent = { dockState, placeholder, dockAvailableDp, chips, _, onOpenFull ->
                    VoiceDockContent(voice, dockState, placeholder, dockAvailableDp, chips, onOpenFull)
                },
                chatFullContent = { _, _ -> },
                peekPlaceholder = Placeholder,
                chips = {},
            )

            Host.PeekStrip -> {
                // What MainScreen and ChecklistDetailScreen do: one long-lived state that rests at
                // Peek, opened here the way a tap on the input opens it.
                val dockState = remember { AnchoredDraggableState(initialValue = DockAnchor.Peek) }
                LaunchedEffect(Unit) {
                    snapshotFlow { dockState.offset.isNaN() }.first { !it }
                    dockState.animateTo(DockAnchor.Expanded)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    VoiceDockContent(voice, dockState, Placeholder, Dp.Unspecified, {}, {})
                }
            }
        }
    }
}

/** The `chatDockContent` lambda of `App.kt`, reduced to the chat-mode voice flow. */
@Composable
private fun VoiceDockContent(
    voice: VoiceFixtureState,
    dockState: AnchoredDraggableState<DockAnchor>,
    placeholder: String,
    dockAvailableDp: Dp,
    chips: @Composable () -> Unit,
    onOpenFull: () -> Unit,
) {
    SideEffect { voice.dockState = dockState }
    GistiExpandableDockContent(
        state = dockState,
        hasLastAnswer = false,
        dockAvailableDp = dockAvailableDp,
        onExpandFull = onOpenFull,
        onHelpClick = {},
        chipsContent = chips,
        inputFocusRequester = voice.focusRequester,
        inputBlank = voice.inputText.isBlank(),
        keepExpanded = chatDockKeepExpanded(
            itemCreateActive = false,
            hasLastAnswer = false,
            isRecording = voice.isRecording,
            isTranscribing = voice.isTranscribing,
        ),
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
                text = voice.inputText,
                onTextChange = { voice.inputText = it },
                onSend = {},
                onAttachFileClick = {},
                onVoiceRecordingStarted = {},
                onVoiceRecordingStopped = {},
                onVoiceRecordingCancelled = {},
                canSend = voice.inputText.isNotBlank(),
                isRecording = voice.isRecording,
                isTranscribing = voice.isTranscribing,
                focusRequester = voice.focusRequester,
                onFocusChanged = onInputFocusChanged,
                placeholderOverride = placeholder,
            )
        },
        recordingOverlay = {
            ChatRecordingOverlay(
                isRecording = voice.isRecording,
                durationMs = RecordedMs,
                isDragCancel = false,
            )
        },
    )
}

/** A recognisable home page, so a frame with the dock gone is unmistakably "just the home screen". */
@Composable
private fun HomeStub() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
    ) {
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

private const val Placeholder = "Ask Gisti…"
private const val RecordedMs = 4_000L
