package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.AudioRecorderErrorCodes
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.rememberAudioRecorderLauncher
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.rememberAudioPlayerLauncher
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.core.common.api.AiEntrySource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeEntryArgs
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun AnalyzeScreen(
    checklistId: Long? = null,
    fillDefault: Boolean = false,
    initialText: String? = null,
    autoAnalyze: Boolean = false,
    /** Material chosen at the door, so this screen opens ON it instead of on its source picker. */
    initialInputKind: AnalyzeInputKind? = null,
    /** Which affordance opened this screen — stamped onto the `ai_analyze_*` events. */
    entrySource: AiEntrySource? = null,
    viewModel: AnalyzeViewModel = koinViewModel(
        // Both new arguments are part of the KEY. Without them, opening Photo and then Voice from
        // the dock would resolve the SAME cached ViewModel and the second tap would land on the
        // photo picker — the very "tapped Voice, got the wrong screen" dead end this work removes.
        key = "analyze_${checklistId}_${fillDefault}_${initialText?.hashCode()}_${autoAnalyze}_${initialInputKind}_$entrySource"
    ) {
        parametersOf(
            checklistId,
            fillDefault,
            initialText,
            autoAnalyze,
            AnalyzeEntryArgs(inputKind = initialInputKind, source = entrySource),
        )
    }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.ANALYZE) }

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    // Visit lifecycle for the background analysis: the token survives rotation (same visit) and is
    // new after this entry was popped and pushed again (new visit → fresh form, see AnalyzeViewModel).
    // Leaving composition detaches the screen, so a finished job is delivered by the app snackbar
    // instead of yanking the user back here.
    val visitToken = rememberSaveable { Uuid.random().toString() }
    DisposableEffect(viewModel, visitToken) {
        viewModel.sendIntent(AnalyzeJobIntent.OnScreenEntered(visitToken))
        onDispose { viewModel.sendIntent(AnalyzeJobIntent.OnScreenLeft) }
    }

    // Opened ON a material → open that material's picker, don't ask for the material a second time.
    // The capture dock's "Photo" pill already said what the source is; the "Choose Photo" button
    // underneath is then a tap for a decision the user has already made.
    //
    // Resolved from THIS SCREEN'S ARGUMENTS by [resolveEntryMaterial] — the same function that seeds
    // the ViewModel — so the prefill precedence still rules (shared text opens as RAW_TEXT even when
    // the door said PHOTO) without the screen having to read the live selection.
    //
    // ⚠️ No `remember` and no state read on purpose. This used to be
    // `remember(viewModel) { screenState.selectedInputType }`, and a plain `remember` dies where the
    // ViewModel survives — a back-stack return or a configuration change. Entering by the Link door
    // (nothing auto-opens, so no saveable latch is ever registered), switching to Photo on screen and
    // coming back re-evaluated it as PHOTO and opened the system file picker with no tap. Arguments
    // are constant for the screen's lifetime, so there is nothing to freeze and nothing to go stale.
    AnalyzeEntryMaterialPicker(
        initialText = initialText,
        initialInputKind = initialInputKind,
        onFileSelected = { path, name ->
            viewModel.sendIntent(AnalyzeScreenIntent.OnFileSelected(path, name))
        },
    )

    // Owner decision 3: ask for notifications once, when the first analysis starts. The system dialog
    // goes up after the first Waiting frame (the screen is drawn under it, no artificial delay); the
    // dock shows the rationale until the answer arrives. The analysis runs whatever the answer.
    val requestNotificationPermission = rememberAnalyzeNotificationPermissionRequest { granted ->
        viewModel.sendIntent(AnalyzeJobIntent.OnNotificationPermissionResult(granted))
    }
    val currentRequest by rememberUpdatedState(requestNotificationPermission)
    LaunchedEffect(screenState.askNotificationPermission) {
        if (screenState.askNotificationPermission) {
            withFrameNanos { }
            currentRequest()
        }
    }

    AnalyzeScreenContent(
        screenState = screenState,
        onIntent = viewModel::sendIntent,
    )
}

/** On-screen time after which the Waiting subtitle switches once to "Large files can take up to a minute". */
internal const val SLOW_WAITING_HINT_AFTER_MS = 15_000L

/**
 * Stateless Analyze screen: top bar, bottom CTA, body and error dialog, rendered from [screenState]
 * with every action reported through [onIntent].
 *
 * [AnalyzeScreen] is the production caller and passes its ViewModel's `sendIntent`; this entry exists
 * so the screen can be drawn from fixed state (store-listing screenshots) without Koin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalTime::class)
@Composable
fun AnalyzeScreenContent(
    screenState: AnalyzeScreenState,
    onIntent: (AnalyzeScreenIntent) -> Unit,
    /** Previews pass 0 to draw the "slow" Waiting subtitle without waiting 15 s. */
    slowWaitingHintAfterMs: Long = SLOW_WAITING_HINT_AFTER_MS,
) {
    val title = if (screenState.isFillMode) {
        stringResource(Res.string.analyze_fill_title)
    } else {
        stringResource(Res.string.analyze_title)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val phase = screenState.jobPhase

    // The Waiting subtitle switches ONCE to "Large files can take up to a minute" after 15 s on screen.
    // The phase-entry time is saveable, so a rotation does not restart the count.
    val jobRunning = screenState.isAnalyzing
    val jobEnteredAtMs = rememberSaveable(jobRunning) { Clock.System.now().toEpochMilliseconds() }
    var slowHint by remember(jobRunning) {
        mutableStateOf(Clock.System.now().toEpochMilliseconds() - jobEnteredAtMs >= slowWaitingHintAfterMs)
    }
    LaunchedEffect(jobRunning, slowHint) {
        if (jobRunning && !slowHint) {
            val elapsed = Clock.System.now().toEpochMilliseconds() - jobEnteredAtMs
            delay((slowWaitingHintAfterMs - elapsed).coerceAtLeast(0L))
            slowHint = true
        }
    }

    AppScaffold(
        title = title,
        onBackButtonClick = { onIntent(AnalyzeScreenIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            AnimatedContent(
                targetState = phase,
                contentKey = { it.dockKey(screenState.selectedInputType != null) },
                transitionSpec = {
                    fadeIn(AppMotion.effectsDefault) togetherWith fadeOut(AppMotion.effectsDefault)
                },
                label = "analyze-dock",
            ) { dockPhase ->
                when (dockPhase) {
                    AnalyzeJobPhase.Editing -> if (screenState.selectedInputType != null) {
                        AnalyzeDock { EditingDockContent(screenState, onIntent) }
                    }
                    is AnalyzeJobPhase.Waiting -> AnalyzeDock {
                        WaitingDockContent(
                            canCancel = dockPhase.canCancel,
                            showNotificationRationale = screenState.askNotificationPermission,
                            onContinueInBackground = { onIntent(AnalyzeJobIntent.OnContinueInBackgroundClick) },
                            onCancel = { onIntent(AnalyzeJobIntent.OnCancelWaitingClick) },
                        )
                    }
                    // Less than a second until the navigation lands: nothing to press.
                    AnalyzeJobPhase.Delivering -> Unit
                    is AnalyzeJobPhase.Failed -> AnalyzeDock {
                        FailedDockContent(
                            cta = dockPhase.cta,
                            onRetry = { onIntent(AnalyzeJobIntent.OnRetryClick) },
                            onBackToEditing = { onIntent(AnalyzeJobIntent.OnBackToEditingClick) },
                            onUpgrade = { onIntent(AnalyzeJobIntent.OnUpgradeClick) },
                        )
                    }
                }
            }
        }
    ) {
        updateTransition(phase, label = "analyze-body").Crossfade(
            animationSpec = AppMotion.effectsDefault,
            contentKey = { it.bodyKey() },
        ) { bodyPhase ->
            if (bodyPhase == AnalyzeJobPhase.Editing) {
                AnalyzeContent(
                    screenState = screenState,
                    onIntent = onIntent
                )
            } else {
                JobStatusBody(phase = bodyPhase, slowHint = slowHint)
            }
        }

        // Error dialog. A Failed phase is drawn by the body + dock above; its `error` mirror is for the
        // dialog-only callers and must not stack a second rendering of the same failure.
        screenState.error?.takeIf { phase !is AnalyzeJobPhase.Failed }?.let { error ->
            AnalyzeMessageDialog(
                message = error,
                onDismiss = { onIntent(AnalyzeScreenIntent.OnDismissError) },
                // A hint (4th parallel start) is not a failure: no "Error" title.
                title = if (screenState.errorIsHint) null else stringResource(Res.string.error),
            )
        }
    }
}

/** Body crossfades only between these three; Waiting ⇄ network ⇄ Delivering swap in place. */
private fun AnalyzeJobPhase.bodyKey(): Int = when (this) {
    AnalyzeJobPhase.Editing -> 0
    is AnalyzeJobPhase.Waiting, AnalyzeJobPhase.Delivering -> 1
    is AnalyzeJobPhase.Failed -> 2
}

/** Dock fades between these; a Waiting(network → online) change keeps the same dock. */
private fun AnalyzeJobPhase.dockKey(hasSource: Boolean): Int = when (this) {
    AnalyzeJobPhase.Editing -> if (hasSource) 0 else -1
    is AnalyzeJobPhase.Waiting -> 1
    AnalyzeJobPhase.Delivering -> -1
    is AnalyzeJobPhase.Failed -> 2
}

/** Editing dock: cost row, "Analyze", and the "Not enough credits" barrier. */
@Composable
private fun ColumnScope.EditingDockContent(
    screenState: AnalyzeScreenState,
    onIntent: (AnalyzeScreenIntent) -> Unit,
) {
    // All users (including premium) need enough credits; unknown cost is not gated.
    val hasEnoughCredits = screenState.hasEnoughCredits

    CostInfoRow(
        balance = (screenState.aiCredits - screenState.creditsReservedByRunningJobs).coerceAtLeast(0),
        aiActionCost = screenState.aiActionCost,
        balanceIsEnough = hasEnoughCredits,
    )

    AppButton(
        text = stringResource(Res.string.analyze_button),
        onClick = { onIntent(AnalyzeScreenIntent.OnAnalyzeClick) },
        modifier = Modifier.fillMaxWidth().testTag("analyze_analyze_button"),
        enabled = hasEnoughCredits
    )

    if (!hasEnoughCredits) {
        Text(
            text = stringResource(Res.string.analyze_not_enough_credits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun AnalyzeContent(
    screenState: AnalyzeScreenState,
    onIntent: (AnalyzeScreenIntent) -> Unit
) {
    // UI-only, and deliberately NOT in the ViewModel: "is the picker open" survives nothing and
    // means nothing to the domain. rememberSaveable rather than remember so a rotation (or a process
    // death behind the system file dialog) does not silently re-open a grid the user had closed —
    // the initialiser runs once, and after a restore the saved value wins.
    var pickerExpanded by rememberSaveable { mutableStateOf(screenState.selectedInputType == null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .adaptiveContentWidth()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        // The "N analyses in progress" strip lives in the same item as the top spacer: as an item of
        // its own it would add a spacedBy gap even while hidden.
        item(key = "top-spacer") {
            Column {
                Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
                JobsInProgressRow(count = screenState.otherJobsInProgress)
            }
        }

        // Only while nothing is chosen. Once a material is picked, the pill showing it says what it
        // is and the editor below says what to do — a heading there labels nothing. The long
        // "What would you like to analyze?" is gone in both states: it repeated the top bar's own
        // title and ran to two lines on a 320dp screen.
        if (screenState.selectedInputType == null) {
            item(key = "source-heading") {
                Text(
                    text = stringResource(Res.string.analyze_select_source_short),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "source-picker") {
            AnalyzeSourcePicker(
                selectedType = screenState.selectedInputType,
                expanded = pickerExpanded,
                onExpandedChange = { pickerExpanded = it },
                onTypeSelected = { onIntent(AnalyzeScreenIntent.OnInputTypeSelected(it)) }
            )
        }

        // Input section based on selected type. No "Add your content" heading above it any more:
        // every one of the six sections names itself ("Choose Photo", the URL field's own label,
        // "Tap to record"), so the heading labelled nothing and cost a whole row of the first screen.
        screenState.selectedInputType?.let { type ->
            item(key = "input-section") {
                InputSection(
                    type = type,
                    inputText = screenState.inputText,
                    inputUrl = screenState.inputUrl,
                    inputError = screenState.inputError,
                    selectedFilePath = screenState.selectedFilePath,
                    selectedFileName = screenState.selectedFileName,
                    isRecording = screenState.isRecording,
                    recordedAudioPath = screenState.recordedAudioPath,
                    recordedAudioDuration = screenState.recordedAudioDuration,
                    onTextChanged = { onIntent(AnalyzeScreenIntent.OnTextInputChanged(it)) },
                    onUrlChanged = { onIntent(AnalyzeScreenIntent.OnUrlInputChanged(it)) },
                    onFileSelected = { path, name -> onIntent(AnalyzeScreenIntent.OnFileSelected(path, name)) },
                    onStartRecording = { onIntent(AnalyzeScreenIntent.OnStartRecording) },
                    onStopRecording = { onIntent(AnalyzeScreenIntent.OnStopRecording) },
                    onRecordingComplete = { path, duration -> onIntent(AnalyzeScreenIntent.OnRecordingComplete(path, duration)) },
                    onRecordingError = { error -> onIntent(AnalyzeScreenIntent.OnRecordingError(error)) },
                    onDeleteRecording = { onIntent(AnalyzeScreenIntent.OnDeleteRecording) },
                    onSubmit = { onIntent(AnalyzeScreenIntent.OnAnalyzeClick) },
                )
            }
        }

        item(key = "bottom-spacer") {
            Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
        }
    }
}

@Composable
private fun InputSection(
    type: InputDataType,
    inputText: String,
    inputUrl: String,
    inputError: String?,
    selectedFilePath: String?,
    selectedFileName: String?,
    isRecording: Boolean,
    recordedAudioPath: String?,
    recordedAudioDuration: Long,
    onTextChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onFileSelected: (filePath: String, fileName: String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRecordingComplete: (filePath: String, durationMs: Long) -> Unit,
    onRecordingError: (String) -> Unit,
    onDeleteRecording: () -> Unit,
    onSubmit: () -> Unit,
) {
    when (type) {
        InputDataType.PHOTO, InputDataType.PDF, InputDataType.TEXT_FILE -> {
            // `toFilePickerType()` answers for exactly these three materials, so this branch's set and
            // the mapping's non-null set are the same set. `?.let` rather than `!!` or an elvis
            // fallback: the null case is unreachable, and the two ways of "handling" it anyway are
            // both worse — a crash on the screen the user is standing on, or the plausible-wrong
            // picker this nullability was introduced to delete.
            type.toFilePickerType()?.let { pickerType ->
                FileInputSection(
                    type = type,
                    pickerType = pickerType,
                    selectedFilePath = selectedFilePath,
                    selectedFileName = selectedFileName,
                    onFileSelected = onFileSelected
                )
            }
        }

        InputDataType.WEB_LINK -> {
            AppTextField(
                value = inputUrl,
                onValueChange = onUrlChanged,
                placeholder = stringResource(Res.string.analyze_url_placeholder),
                label = stringResource(Res.string.analyze_url_label),
                showClearButton = true,
                // The ViewModel clears the error on the next edit; the Analyze button stays enabled.
                isError = inputError != null,
                errorMessage = inputError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.testTag("analyze_url_field"),
            )
        }

        InputDataType.RAW_TEXT -> {
            AppTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = stringResource(Res.string.analyze_text_placeholder),
                label = stringResource(Res.string.analyze_text_label),
                singleLine = false,
                maxLines = 10,
                showClearButton = true
            )
        }

        InputDataType.VOICE -> {
            VoiceInputSection(
                isRecording = isRecording,
                recordedAudioPath = recordedAudioPath,
                recordedAudioDuration = recordedAudioDuration,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onRecordingComplete = onRecordingComplete,
                onRecordingError = onRecordingError,
                onDeleteRecording = onDeleteRecording
            )
        }
    }
}

@Composable
private fun FileInputSection(
    type: InputDataType,
    /**
     * Resolved by the caller through `toFilePickerType()` — the same mapping the on-entry auto-open
     * uses, so the button and the auto-open can never open two different dialogs for one material.
     */
    pickerType: FilePickerType,
    selectedFilePath: String?,
    selectedFileName: String?,
    onFileSelected: (filePath: String, fileName: String) -> Unit
) {
    val selectPhotoText = stringResource(Res.string.analyze_select_photo)
    val selectPdfText = stringResource(Res.string.analyze_select_pdf)
    val selectFileText = stringResource(Res.string.analyze_select_file)
    val selectText = stringResource(Res.string.select)
    val selectedText = stringResource(Res.string.selected)

    val filePickerLauncher = rememberFilePickerLauncher(
        type = pickerType,
        onResult = { result ->
            if (result != null) {
                onFileSelected(result.filePath, result.fileName)
            }
        }
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        val buttonText = when (type) {
            InputDataType.PHOTO -> selectPhotoText
            InputDataType.PDF -> selectPdfText
            InputDataType.TEXT_FILE -> selectFileText
            else -> selectText
        }

        AppButtonSecondary(
            text = buttonText,
            onClick = { filePickerLauncher.launch() },
            modifier = Modifier.fillMaxWidth()
        )

        selectedFilePath?.let {
            AppCard {
                Text(
                    text = "$selectedText ${selectedFileName ?: it.substringAfterLast("/")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun VoiceInputSection(
    isRecording: Boolean,
    recordedAudioPath: String?,
    recordedAudioDuration: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRecordingComplete: (filePath: String, durationMs: Long) -> Unit,
    onRecordingError: (String) -> Unit,
    onDeleteRecording: () -> Unit
) {
    var permissionDeniedPermanently by remember { mutableStateOf(false) }
    var grantedHint by remember { mutableStateOf<String?>(null) }
    grantedHint?.let { hint ->
        AnalyzeMessageDialog(message = hint, onDismiss = { grantedHint = null })
    }

    // Pre-fetch the permission denied string for use in callback
    val permissionDeniedMessage = stringResource(Res.string.analyze_voice_permission_denied)
    val permissionGrantedMessage = stringResource(Res.string.analyze_voice_permission_granted)
    val tooShortMessage = stringResource(Res.string.analyze_voice_too_short)
    val recordingFailedMessage = stringResource(Res.string.analyze_voice_recording_failed)
    val playbackFailedMessage = stringResource(Res.string.analyze_voice_playback_failed)
    val logger: AppLogger = koinInject()

    val audioRecorderLauncher = rememberAudioRecorderLauncher(
        onResult = { result ->
            if (result != null) {
                onRecordingComplete(result.filePath, result.durationMs)
            }
        },
        onError = { error ->
            when (error) {
                AudioRecorderErrorCodes.PERMISSION_DENIED -> {
                    // User denied but can try again - don't show permanent error
                    onRecordingError(permissionDeniedMessage)
                }
                AudioRecorderErrorCodes.PERMISSION_DENIED_PERMANENTLY -> {
                    // The tap set isRecording = true; nothing will record, so reset it.
                    onStopRecording()
                    permissionDeniedPermanently = true
                }
                // Tap-to-record normally auto-starts once the permission is granted; this code
                // only arrives when the request state was lost under the dialog (activity recreated).
                // It is guidance, not a failure: no "Error" dialog. Reset the recording flag via the
                // ordinary stop intent and show the neutral hint dialog below.
                AudioRecorderErrorCodes.PERMISSION_GRANTED_PRESS_AGAIN -> {
                    onStopRecording()
                    grantedHint = permissionGrantedMessage
                }
                AudioRecorderErrorCodes.RECORDING_TOO_SHORT -> onRecordingError(tooShortMessage)
                else -> {
                    // Any other string is a raw recorder failure message — log it, show a localized text.
                    logger.error("Analyze", "Voice recorder failed: $error", IllegalStateException(error))
                    onRecordingError(recordingFailedMessage)
                }
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        if (permissionDeniedPermanently) {
            VoicePermissionBlockedCard(
                canOpenAppSettings = audioRecorderLauncher.canOpenAppSettings,
                onOpenAppSettings = { audioRecorderLauncher.openAppSettings() },
                // Back to the record button: the next tap asks / re-checks the permission.
                onTryAgain = { permissionDeniedPermanently = false },
            )
        } else if (recordedAudioPath != null) {
            // Show recorded audio info with playback controls
            var isPlayingAudio by remember { mutableStateOf(false) }

            val audioPlayerLauncher = rememberAudioPlayerLauncher(
                onPlaybackComplete = {
                    isPlayingAudio = false
                },
                onError = { error ->
                    isPlayingAudio = false
                    // The player reports a raw platform message (MediaPlayer exception text, the
                    // web "not supported" stub). Raw goes to the log only; the user sees localized text.
                    logger.error("Analyze", "Voice playback failed: $error", IllegalStateException(error))
                    onRecordingError(playbackFailedMessage)
                }
            )

            AppCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Play/Pause button
                        androidx.compose.material3.IconButton(
                            onClick = {
                                if (isPlayingAudio) {
                                    audioPlayerLauncher.stop()
                                    isPlayingAudio = false
                                } else {
                                    audioPlayerLauncher.play(recordedAudioPath)
                                    isPlayingAudio = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = if (isPlayingAudio) {
                                    stringResource(Res.string.analyze_voice_stop)
                                } else {
                                    stringResource(Res.string.analyze_voice_play)
                                },
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(Res.string.analyze_voice_recorded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatDuration(recordedAudioDuration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Delete button
                    androidx.compose.material3.IconButton(
                        onClick = {
                            audioPlayerLauncher.stop()
                            onDeleteRecording()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(Res.string.analyze_voice_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            // Recording button
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        }
                    )
                    .border(
                        width = 3.dp,
                        color = if (isRecording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        if (isRecording) {
                            audioRecorderLauncher.stop()
                            onStopRecording()
                        } else {
                            onStartRecording()
                            audioRecorderLauncher.start()
                        }
                    },
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = if (isRecording) {
                            stringResource(Res.string.analyze_voice_stop)
                        } else {
                            stringResource(Res.string.analyze_voice_tap_to_record)
                        },
                        modifier = Modifier.size(48.dp),
                        tint = if (isRecording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Text(
                text = if (isRecording) {
                    stringResource(Res.string.analyze_voice_recording)
                } else {
                    stringResource(Res.string.analyze_voice_tap_to_record)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRecording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * The screen's one message dialog. [title] null = a hint (e.g. "Microphone is on. Tap record to
 * start."), not a failure; the error dialog passes the "Error" title.
 */
@Composable
internal fun AnalyzeMessageDialog(message: String, onDismiss: () -> Unit, title: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.ok),
                onClick = onDismiss,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

/**
 * Microphone permission refused for good (Android "Don't ask again", browser "Block").
 *
 * "Open Settings" only where the recorder can open an app-settings screen (Android, iOS); the web
 * has none — site permissions live behind the lock icon in the address bar, which the text names
 * instead. "Try again" on every platform: the user may have re-allowed the microphone meanwhile,
 * and without it the card was a dead end until the screen was left.
 */
@Composable
internal fun VoicePermissionBlockedCard(
    canOpenAppSettings: Boolean,
    onOpenAppSettings: () -> Unit,
    onTryAgain: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(
                    if (canOpenAppSettings) {
                        Res.string.analyze_voice_permission_denied_settings
                    } else {
                        Res.string.analyze_voice_permission_denied_site_settings
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (canOpenAppSettings) {
                AppButton(
                    text = stringResource(Res.string.analyze_voice_open_settings),
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppButtonSecondary(
                text = stringResource(Res.string.analyze_voice_try_again),
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * "This action costs N credits • M credits". [balance] is already net of the credits running jobs
 * hold. Unknown cost ([aiActionCost] null): only "M credits" — never a made-up number. The balance
 * colour follows the same rule as the button barrier ([balanceIsEnough]).
 */
@Composable
private fun CostInfoRow(
    balance: Int,
    aiActionCost: Int?,
    balanceIsEnough: Boolean,
    modifier: Modifier = Modifier
) {
    // All users (including premium) now use credits
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.IconSizeSm),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(AppDimens.SpacingXs))
        // ONE text, so a narrow screen at a large font wraps it as a sentence. Three Texts in a Row
        // squeezed the balance into a one-letter-wide column at 320dp × 1.5 (RU).
        val costPart = aiActionCost?.let { stringResource(Res.string.analyze_cost_info, it) }
        val balancePart = stringResource(Res.string.credits_display, balance)
        val balanceColor = if (balanceIsEnough) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
        Text(
            text = buildAnnotatedString {
                if (costPart != null) append("$costPart • ")
                withStyle(SpanStyle(color = balanceColor)) { append(balancePart) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
