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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.rememberFilePickerLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder.rememberAudioRecorderLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.recorder.rememberAudioPlayerLauncher
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AnalyzeScreen(
    checklistId: Long? = null,
    viewModel: AnalyzeViewModel = koinViewModel { parametersOf(checklistId) }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("analyze") }

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    val title = if (screenState.isFillMode) {
        stringResource(Res.string.analyze_fill_title)
    } else {
        stringResource(Res.string.analyze_title)
    }

    AppScaffold(
        title = title,
        onBackButtonClick = { viewModel.sendIntent(AnalyzeScreenIntent.OnBackClick) },
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
private fun AnalyzeContent(
    screenState: AnalyzeScreenState,
    onIntent: (AnalyzeScreenIntent) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        item {
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        }

        item {
            Text(
                text = stringResource(Res.string.analyze_select_source),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            InputTypeSelector(
                selectedType = screenState.selectedInputType,
                onTypeSelected = { onIntent(AnalyzeScreenIntent.OnInputTypeSelected(it)) }
            )
        }

        // Input section based on selected type
        screenState.selectedInputType?.let { type ->
            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.analyze_enter_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
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

        item {
            Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
        }
    }
}

@Composable
private fun InputTypeSelector(
    selectedType: InputDataType?,
    onTypeSelected: (InputDataType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            InputTypeCard(
                icon = Icons.Outlined.Image,
                title = stringResource(Res.string.analyze_source_photo),
                isSelected = selectedType == InputDataType.PHOTO,
                onClick = { onTypeSelected(InputDataType.PHOTO) },
                modifier = Modifier.weight(1f)
            )
            InputTypeCard(
                icon = Icons.Outlined.PictureAsPdf,
                title = stringResource(Res.string.analyze_source_pdf),
                isSelected = selectedType == InputDataType.PDF,
                onClick = { onTypeSelected(InputDataType.PDF) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            InputTypeCard(
                icon = Icons.Outlined.Description,
                title = stringResource(Res.string.analyze_source_text_file),
                isSelected = selectedType == InputDataType.TEXT_FILE,
                onClick = { onTypeSelected(InputDataType.TEXT_FILE) },
                modifier = Modifier.weight(1f)
            )
            InputTypeCard(
                icon = Icons.Outlined.Link,
                title = stringResource(Res.string.analyze_source_link),
                isSelected = selectedType == InputDataType.WEB_LINK,
                onClick = { onTypeSelected(InputDataType.WEB_LINK) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            InputTypeCard(
                icon = Icons.Outlined.TextFields,
                title = stringResource(Res.string.analyze_source_text),
                isSelected = selectedType == InputDataType.RAW_TEXT,
                onClick = { onTypeSelected(InputDataType.RAW_TEXT) },
                modifier = Modifier.weight(1f)
            )
            InputTypeCard(
                icon = Icons.Outlined.Mic,
                title = stringResource(Res.string.analyze_source_voice),
                isSelected = selectedType == InputDataType.VOICE,
                onClick = { onTypeSelected(InputDataType.VOICE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InputTypeCard(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium
        )
    } else {
        Modifier
    }

    AppCard(
        onClick = onClick,
        modifier = modifier.then(borderModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.SpacingSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
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
            FileInputSection(
                type = type,
                selectedFilePath = selectedFilePath,
                selectedFileName = selectedFileName,
                onFileSelected = onFileSelected
            )
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
    selectedFilePath: String?,
    selectedFileName: String?,
    onFileSelected: (filePath: String, fileName: String) -> Unit
) {
    val selectPhotoText = stringResource(Res.string.analyze_select_photo)
    val selectPdfText = stringResource(Res.string.analyze_select_pdf)
    val selectFileText = stringResource(Res.string.analyze_select_file)
    val selectText = stringResource(Res.string.select)
    val selectedText = stringResource(Res.string.selected)

    val filePickerType = when (type) {
        InputDataType.PHOTO -> FilePickerType.IMAGE
        InputDataType.PDF -> FilePickerType.PDF
        InputDataType.TEXT_FILE -> FilePickerType.TEXT
        else -> FilePickerType.TEXT
    }

    val filePickerLauncher = rememberFilePickerLauncher(
        type = filePickerType,
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
