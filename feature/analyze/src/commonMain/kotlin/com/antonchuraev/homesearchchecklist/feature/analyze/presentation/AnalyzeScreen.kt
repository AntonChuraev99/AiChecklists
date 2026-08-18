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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val title = if (screenState.isFillMode) {
        stringResource(Res.string.analyze_fill_title)
    } else {
        stringResource(Res.string.analyze_title)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        title = title,
        onBackButtonClick = { viewModel.sendIntent(AnalyzeScreenIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (screenState.selectedInputType != null && !screenState.isAnalyzing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPaddingHorizontal)
                        .padding(bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
                ) {
                    // Credits info
                    CostInfoRow(
                        aiCredits = screenState.aiCredits,
                        aiActionCost = screenState.aiActionCost,
                        isPremium = screenState.isPremium
                    )

                    // All users (including premium) need enough credits
                    val hasEnoughCredits = screenState.aiCredits >= screenState.aiActionCost

                    AppButton(
                        text = stringResource(Res.string.analyze_button),
                        onClick = { viewModel.sendIntent(AnalyzeScreenIntent.OnAnalyzeClick) },
                        modifier = Modifier.fillMaxWidth(),
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
            }
        }
    ) {
        if (screenState.isAnalyzing) {
            LoadingContent()
        } else {
            AnalyzeContent(
                screenState = screenState,
                onIntent = viewModel::sendIntent
            )
        }

        // Error dialog
        screenState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.sendIntent(AnalyzeScreenIntent.OnDismissError) },
                title = { Text(stringResource(Res.string.error)) },
                text = { Text(error) },
                confirmButton = {
                    AppButtonText(
                        text = stringResource(Res.string.ok),
                        onClick = { viewModel.sendIntent(AnalyzeScreenIntent.OnDismissError) }
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg)
        ) {
            // Circular background with icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                Text(
                    text = stringResource(Res.string.analyze_loading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(Res.string.analyze_loading_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
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
        item(key = "top-spacer") {
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
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
                    onDeleteRecording = { onIntent(AnalyzeScreenIntent.OnDeleteRecording) }
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
    onDeleteRecording: () -> Unit
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
                showClearButton = true
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

    // Pre-fetch the permission denied string for use in callback
    val permissionDeniedMessage = stringResource(Res.string.analyze_voice_permission_denied)

    val audioRecorderLauncher = rememberAudioRecorderLauncher(
        onResult = { result ->
            if (result != null) {
                onRecordingComplete(result.filePath, result.durationMs)
            }
        },
        onError = { error ->
            when (error) {
                "PERMISSION_DENIED" -> {
                    // User denied but can try again - don't show permanent error
                    onRecordingError(permissionDeniedMessage)
                }
                "PERMISSION_DENIED_PERMANENTLY" -> {
                    permissionDeniedPermanently = true
                }
                else -> onRecordingError(error)
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        if (permissionDeniedPermanently) {
            // Permission permanently denied - show message and settings button
            AppCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.SpacingLg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(Res.string.analyze_voice_permission_denied_settings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    AppButton(
                        text = stringResource(Res.string.analyze_voice_open_settings),
                        onClick = { audioRecorderLauncher.openAppSettings() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else if (recordedAudioPath != null) {
            // Show recorded audio info with playback controls
            var isPlayingAudio by remember { mutableStateOf(false) }

            val audioPlayerLauncher = rememberAudioPlayerLauncher(
                onPlaybackComplete = {
                    isPlayingAudio = false
                },
                onError = { error ->
                    isPlayingAudio = false
                    onRecordingError(error)
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun CostInfoRow(
    aiCredits: Int,
    aiActionCost: Int,
    isPremium: Boolean,
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
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(Res.string.analyze_cost_info, aiActionCost),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = " • ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(Res.string.credits_display, aiCredits),
            style = MaterialTheme.typography.bodySmall,
            color = if (aiCredits >= aiActionCost) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}
