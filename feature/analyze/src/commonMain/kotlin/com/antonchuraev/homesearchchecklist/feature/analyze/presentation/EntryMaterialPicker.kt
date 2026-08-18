package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerLauncher
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerResult
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.toInputDataType

/**
 * Whether entering the screen already standing ON this material should open its system picker
 * straight away, instead of leaving the user in front of a "Choose …" button.
 *
 * Only the two file materials qualify. PHOTO and PDF land on a screen whose entire editor is one
 * button that opens the system picker, so requiring that tap asks the user to name the same thing
 * twice — the friction the owner reported on 2026-08-17 ("нажал photo → сразу открывать выбор
 * вложения, чтобы когда выбрал то уже увидел экран создания с этой фоткой").
 *
 * The other four say no for reasons that are NOT the same reason, which is why they are listed
 * rather than folded into an `else`:
 *  - WEB_LINK and RAW_TEXT land on a text field. There is no picker to open.
 *  - VOICE would start RECORDING the microphone on screen entry. Nobody asked for that, and unlike
 *    a dismissed file dialog it is not a no-op the user can walk away from.
 *  - TEXT_FILE is not a door: no `AnalyzeInputKind` maps to it, so it is only ever reached by
 *    tapping the on-screen source picker — where the button IS the tap the user just chose to make.
 *
 * Exhaustive on purpose, no `else`, matching `AnalyzeInputKind.toInputDataType()`. A new material
 * must break this file at compile time and force the auto-open question to be answered out loud,
 * rather than inherit a silent default.
 */
internal fun InputDataType.opensPickerOnEntry(): Boolean = when (this) {
    InputDataType.PHOTO,
    InputDataType.PDF -> true

    InputDataType.TEXT_FILE,
    InputDataType.WEB_LINK,
    InputDataType.RAW_TEXT,
    InputDataType.VOICE -> false
}

/**
 * Maps a material onto the system picker that can supply it, or `null` when it has none.
 *
 * One function rather than a `when` at each call site: the on-screen "Choose …" button and the
 * on-entry auto-open both need this mapping, and a gate copy-pasted into two places is exactly how
 * the two drift apart while each site's own test stays green.
 *
 * ⚠️ Nullable, not "TEXT for everything else". WEB_LINK, RAW_TEXT and VOICE are typed IN, not picked
 * from the filesystem, so there is no true answer for them — and `FilePickerType.TEXT` is a
 * *plausible* wrong one, which is worse than none: it compiles, it reads as deliberate, and the day
 * some new call site forgets the `opensPickerOnEntry()` gate it silently opens a text-file chooser
 * for a voice recording instead of failing where the mistake is. `null` makes every caller say out
 * loud what it does with a material that has no picker.
 */
internal fun InputDataType.toFilePickerType(): FilePickerType? = when (this) {
    InputDataType.PHOTO -> FilePickerType.IMAGE
    InputDataType.PDF -> FilePickerType.PDF
    InputDataType.TEXT_FILE -> FilePickerType.TEXT

    InputDataType.WEB_LINK,
    InputDataType.RAW_TEXT,
    InputDataType.VOICE -> null
}

/**
 * The material the Analyze screen OPENS on, decided from the navigation arguments and nothing else.
 *
 * ## One rule, two readers
 * [AnalyzeViewModel] seeds `selectedInputType` with it, and the screen's on-entry picker
 * ([AnalyzeEntryMaterialPicker]) decides whether to auto-open a dialog with it. Those two must agree
 * — the screen must not open a photo dialog over a text field it is showing — and the repo's own rule
 * for a gate with two readers applies: collapse it into one function, or watch the copies drift while
 * each side's test stays green.
 *
 * ## Prefill wins over the door
 * A non-blank [initialText] IS raw text: it arrives from `ACTION_PROCESS_TEXT` ("Checklist from
 * text") and from the activation hero, and it is already in the input field. Honouring a PHOTO door
 * on top of it would throw a file dialog over text the user can no longer see. So the prefill is
 * checked first and the door only decides when there is nothing prefilled.
 *
 * ## Arguments only, deliberately
 * Nothing here reads the screen's CURRENT material, and that is the whole point rather than an
 * omission. The screen used to freeze the live value in a `remember(viewModel)`, and a plain
 * `remember` dies on a back-stack return and on a configuration change while the ViewModel does not:
 * entering by the Link door, switching to Photo on screen and then coming back re-evaluated the
 * "material the screen opened on" as PHOTO and opened the system file picker with no tap at all.
 * Arguments cannot do that — they are constant for the screen's whole lifetime, so the value needs no
 * cache to be stable and has no cache to go stale.
 */
internal fun resolveEntryMaterial(
    initialText: String?,
    initialInputKind: AnalyzeInputKind?,
): InputDataType? = when {
    !initialText.isNullOrBlank() -> InputDataType.RAW_TEXT
    else -> initialInputKind?.toInputDataType()
}

/**
 * The screen-level wiring of [EntryMaterialPickerOneShot]: resolve what the screen opened on, then
 * let the one-shot decide whether that material has a dialog to open.
 *
 * A named composable rather than two lines inside [AnalyzeScreen], because inside the screen this
 * decision could only be observed by mounting the whole screen — Koin-resolved ViewModel, twelve
 * collaborators and a real file picker included — which is to say it could not be observed at all.
 * Here the same production code runs under a state-restoration tester in a host test.
 *
 * @param rememberLauncher the same test seam [EntryMaterialPickerOneShot] documents; production always
 *   takes the default.
 */
@Composable
internal fun AnalyzeEntryMaterialPicker(
    initialText: String?,
    initialInputKind: AnalyzeInputKind?,
    onFileSelected: (filePath: String, fileName: String) -> Unit,
    rememberLauncher: @Composable (
        type: FilePickerType,
        onResult: (FilePickerResult?) -> Unit,
    ) -> FilePickerLauncher = { type, onResult -> rememberFilePickerLauncher(type, onResult) },
) {
    EntryMaterialPickerOneShot(
        material = resolveEntryMaterial(initialText, initialInputKind),
        onFileSelected = onFileSelected,
        rememberLauncher = rememberLauncher,
    )
}

/**
 * Opens the system file picker ONCE when the screen was entered on a material that has one.
 *
 * [material] is the material the screen OPENED on, and the caller is responsible for freezing it:
 * this composable deliberately re-arms if [material] changes, because the only legitimate way for
 * that to happen is a fresh entry. Following the live selection instead would mean that tapping
 * "Photo" in the on-screen source picker also auto-opens the dialog — a different feature, and one
 * that would take away the very button that lets a user who cancelled try again.
 *
 * `null`, or a material that is not a file, means "nothing to open": no launcher is created at all.
 *
 * "Once" has to survive three things, and each one is a way this pattern has broken before:
 *  - **Recomposition** — the launch lives in a [LaunchedEffect], not in the composition itself.
 *  - **State restoration** (Android rotation or process death while the system dialog is in front).
 *    Hence [rememberSaveable]: a plain `remember` dies with the Activity and the picker would open a
 *    second time on top of the first.
 *  - **A cancelled dialog** — the latch is consumed AT LAUNCH, before the picker is even shown, and
 *    never on a result. Clearing it on success only would re-open the dialog forever after every
 *    cancel; clearing it inside the result callback would leave it armed when the user dismissed
 *    without picking. Cancelling therefore leaves the user on the ordinary screen with the material
 *    preselected and the "Choose …" button still there — the recovery path stays intact.
 *
 * [rememberLauncher] exists so all of the above is assertable on the JVM without a real system file
 * dialog; production always uses the default, and that default — the platform `actual` behind
 * [rememberFilePickerLauncher] — is the one part only a real run on device or in a browser can
 * verify.
 */
@Composable
internal fun EntryMaterialPickerOneShot(
    material: InputDataType?,
    onFileSelected: (filePath: String, fileName: String) -> Unit,
    rememberLauncher: @Composable (
        type: FilePickerType,
        onResult: (FilePickerResult?) -> Unit,
    ) -> FilePickerLauncher = { type, onResult -> rememberFilePickerLauncher(type, onResult) },
) {
    val pickerType = remember(material) {
        material?.takeIf { it.opensPickerOnEntry() }?.toFilePickerType()
    }

    if (pickerType != null) {
        // The launcher on every platform remembers the FIRST onResult it was handed, and on iOS it
        // does so without a rememberUpdatedState of its own. Route the callback through one here so
        // no platform can strand a picked file in a stale closure — the defect that silently dropped
        // web attachments.
        val currentOnFileSelected by rememberUpdatedState(onFileSelected)
        val launcher = rememberLauncher(pickerType) { result ->
            // A cancel (null) is deliberately a no-op beyond leaving the screen usable: nothing
            // failed, the user simply changed their mind.
            if (result != null) {
                currentOnFileSelected(result.filePath, result.fileName)
            }
        }

        var pending by rememberSaveable(pickerType) { mutableStateOf(true) }
        LaunchedEffect(pickerType) {
            if (pending) {
                pending = false
                launcher.launch()
            }
        }
    }
}
