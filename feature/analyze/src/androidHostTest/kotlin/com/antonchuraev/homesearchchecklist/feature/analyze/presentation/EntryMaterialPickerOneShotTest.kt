package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerLauncher
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerResult
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The on-entry picker's one-shot contract.
 *
 * Owner report 2026-08-17: opening Analyze from the capture dock's Photo pill must open the system
 * picker itself, so the user comes back to a screen that already holds the photo. The thing that can
 * actually be wrong is not "does it open" but "does it open exactly once" — and every way of getting
 * that wrong is invisible to a compile and to a happy-path run:
 *  - clearing the latch on a successful pick re-opens the dialog forever after a cancel;
 *  - a plain `remember` re-opens it on the way back from a rotation;
 *  - launching from composition instead of an effect re-opens it on every recomposition.
 *
 * Robolectric + a real composition rather than `commonTest`, because two of those three only exist
 * as behaviour of a composition that gets saved and restored. [StateRestorationTester] is what makes
 * the rotation case reachable on the JVM at all.
 *
 * The launcher is faked through the `rememberLauncher` seam, so these assertions cover the gate, the
 * latch and the dialog type without a real system file dialog. What they deliberately do NOT cover
 * is the platform `actual` behind the default argument — that needs a device or a browser.
 *
 * Run: `./gradlew :feature:analyze:testAndroidHostTest --tests "*EntryMaterialPickerOneShotTest*"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryMaterialPickerOneShotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun enteringOnPhoto_opensTheImagePickerExactlyOnce() {
        val picker = RecordingPicker()

        composeTestRule.setContent {
            EntryMaterialPickerOneShot(
                material = InputDataType.PHOTO,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(FilePickerType.IMAGE),
            picker.opened,
            "Entering on Photo must open the image picker once, with no tap on \"Choose Photo\"",
        )
    }

    @Test
    fun enteringOnPdf_opensThePdfPickerExactlyOnce() {
        val picker = RecordingPicker()

        composeTestRule.setContent {
            EntryMaterialPickerOneShot(
                material = InputDataType.PDF,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(FilePickerType.PDF), picker.opened)
    }

    /**
     * No material (the screen was opened without a named source) and every non-file material.
     *
     * All five in one composition on purpose: none of them reaches a launcher at all, so there is no
     * per-material state to keep apart.
     */
    @Test
    fun enteringWithoutAFileMaterial_neverOpensAPicker() {
        val picker = RecordingPicker()
        val quiet = listOf(
            null,
            InputDataType.TEXT_FILE,
            InputDataType.WEB_LINK,
            InputDataType.RAW_TEXT,
            InputDataType.VOICE,
        )

        composeTestRule.setContent {
            quiet.forEachIndexed { index, material ->
                key(index) {
                    EntryMaterialPickerOneShot(
                        material = material,
                        onFileSelected = picker::recordSelection,
                        rememberLauncher = picker.factory,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(
            emptyList(),
            picker.opened,
            "Opening the screen with no material, or on a text/voice material, must leave the user " +
                "in their editor — a file dialog over a text field is a hijacked screen",
        )
    }

    @Test
    fun recomposing_doesNotOpenTheDialogASecondTime() {
        val picker = RecordingPicker()
        // Hoisted OUTSIDE setContent: a mutableStateOf created inside setContent without remember is
        // rewritten on every recomposition and the test can never observe a change.
        val tick = mutableIntStateOf(0)

        composeTestRule.setContent {
            // The counter has to reach the composable through a PARAMETER, not merely be read in the
            // enclosing scope: with equal parameters Compose skips the call entirely, and the test
            // would then pass because nothing recomposed rather than because the latch held.
            // Capturing `nonce` gives the callback a new identity per tick, which defeats skipping.
            val nonce = tick.value
            EntryMaterialPickerOneShot(
                material = InputDataType.PHOTO,
                onFileSelected = { path, name ->
                    if (nonce >= 0) picker.recordSelection(path, name)
                },
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { tick.value = 1 }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(FilePickerType.IMAGE),
            picker.opened,
            "The launch must live in an effect, not in the composition itself",
        )
    }

    @Test
    fun rotating_doesNotOpenTheDialogASecondTime() {
        val picker = RecordingPicker()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            EntryMaterialPickerOneShot(
                material = InputDataType.PHOTO,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf(FilePickerType.IMAGE), picker.opened, "opens once on entry")

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(FilePickerType.IMAGE),
            picker.opened,
            "The latch must be saveable: a rotation while the system dialog is in front recreates " +
                "the Activity, and a plain remember would stack a second dialog on the first",
        )
    }

    /**
     * The cancel path, which is the one that decides WHERE the latch is consumed.
     *
     * Consuming it on a successful pick — or anywhere inside the result callback — leaves it armed
     * after a dismissal, and the next composition (here: a rotation) re-opens the dialog the user
     * just closed. Consuming it at launch is what makes a cancel final.
     */
    @Test
    fun cancellingTheDialogThenRotating_doesNotReopenIt() {
        val picker = RecordingPicker()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            EntryMaterialPickerOneShot(
                material = InputDataType.PHOTO,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()

        // The user dismissed the system dialog without picking anything.
        composeTestRule.runOnIdle { picker.deliver(null) }
        composeTestRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(FilePickerType.IMAGE),
            picker.opened,
            "A cancel must be final — the latch is consumed at launch, never on a result",
        )
        assertEquals(
            emptyList(),
            picker.selections,
            "A cancel must not report a file",
        )
    }

    @Test
    fun pickingAFile_reportsItOnceSoTheScreenComesBackHoldingIt() {
        val picker = RecordingPicker()

        composeTestRule.setContent {
            EntryMaterialPickerOneShot(
                material = InputDataType.PHOTO,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            picker.deliver(
                FilePickerResult(filePath = "/cache/analyze_1.jpg", fileName = "beach.jpg", mimeType = "image/jpeg")
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf("/cache/analyze_1.jpg" to "beach.jpg"),
            picker.selections,
            "Path and name must reach the screen in that order — swapping them shows the user a " +
                "file card naming a cache path",
        )
    }

    // ─── The screen-level wiring: what the visit OPENED on ────────────────────
    //
    // These mount [AnalyzeEntryMaterialPicker] — the production two lines of AnalyzeScreen — rather
    // than the one-shot directly, because the reported defect was in the DERIVATION, not in the latch:
    // the material was read from the live screen state and cached in a plain `remember`, which dies on
    // a back-stack return and on a configuration change while the ViewModel does not.

    /**
     * Entering by a NON-file door must never open a picker, and must still never open one after the
     * composition has been saved and restored.
     *
     * This is the reported scenario, minus the parts a host test cannot stage: enter on Link (nothing
     * auto-opens, so no saveable latch is ever registered for this visit), leave and come back. With
     * the material cached from the live selection this came back as PHOTO and the system file picker
     * opened with no tap at all.
     */
    @Test
    fun enteringOnALinkDoor_opensNoPickerBeforeOrAfterRestore() {
        val picker = RecordingPicker()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            AnalyzeEntryMaterialPicker(
                initialText = null,
                initialInputKind = AnalyzeInputKind.WEB_LINK,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()
        assertEquals(emptyList(), picker.opened, "a link door has no picker to open")

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(
            emptyList(),
            picker.opened,
            "Coming back must re-derive the SAME material from the route's own arguments. A door that " +
                "opened no dialog on the way in must not open one on the way back — there is no latch " +
                "for it to be stopped by.",
        )
    }

    /**
     * A prefilled text extra outranks a PHOTO door, and keeps outranking it across a restore.
     *
     * This is the case the deleted `remember(viewModel)` existed to protect and the one a naive "just
     * read the route's inputKind" fix would break: `ACTION_PROCESS_TEXT` arrives as PHOTO-plus-text on
     * some entry points, the text is already in the field, and a photo dialog over it hides text the
     * user can no longer see.
     */
    @Test
    fun prefilledTextBeatsAPhotoDoor_soNoDialogOpensOverIt() {
        val picker = RecordingPicker()
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            AnalyzeEntryMaterialPicker(
                initialText = "buy milk",
                initialInputKind = AnalyzeInputKind.PHOTO,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(emptyList(), picker.opened)
    }

    /** The positive half: a PHOTO door with nothing prefilled still auto-opens, exactly once. */
    @Test
    fun enteringOnAPhotoDoor_stillOpensTheImagePickerOnce() {
        val picker = RecordingPicker()

        composeTestRule.setContent {
            AnalyzeEntryMaterialPicker(
                initialText = null,
                initialInputKind = AnalyzeInputKind.PHOTO,
                onFileSelected = picker::recordSelection,
                rememberLauncher = picker.factory,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(FilePickerType.IMAGE), picker.opened)
    }

    // ─── Fake ─────────────────────────────────────────────────────────────────

    /**
     * Stands in for [com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher].
     *
     * Records which dialog was opened and how many times, and keeps the callback so a test can play
     * the user's answer — a pick or a dismissal — back into the composition.
     */
    private class RecordingPicker {
        val opened = mutableListOf<FilePickerType>()
        val selections = mutableListOf<Pair<String, String>>()
        private var onResult: ((FilePickerResult?) -> Unit)? = null

        val factory: @Composable (FilePickerType, (FilePickerResult?) -> Unit) -> FilePickerLauncher =
            { type, callback ->
                onResult = callback
                remember(type) { FilePickerLauncher { opened += type } }
            }

        fun recordSelection(filePath: String, fileName: String) {
            selections += filePath to fileName
        }

        fun deliver(result: FilePickerResult?) {
            onResult?.invoke(result)
        }
    }
}
