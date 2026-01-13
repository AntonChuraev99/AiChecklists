package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.InputDataType
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.picker.rememberFilePickerLauncher
import homesearchchecklist.core.designsystem.generated.resources.Res
import homesearchchecklist.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AnalyzeScreen(
    viewModel: AnalyzeViewModel = koinViewModel()
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    AppScaffold(
        title = stringResource(Res.string.analyze_title),
        onBackButtonClick = { viewModel.sendIntent(AnalyzeScreenIntent.OnBackClick) },
        bottomBar = {
            if (screenState.selectedInputType != null && !screenState.isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPaddingHorizontal)
                        .padding(bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding()
                ) {
                    AppButton(
                        text = stringResource(Res.string.analyze_button),
                        onClick = { viewModel.sendIntent(AnalyzeScreenIntent.OnAnalyzeClick) },
                        modifier = Modifier.fillMaxWidth()
                    )
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

        // Result dialog
        if (screenState.showResultDialog && screenState.analyzeResult != null) {
            ResultDialog(
                result = screenState.analyzeResult!!,
                checklistName = screenState.checklistName,
                onChecklistNameChanged = {
                    viewModel.sendIntent(AnalyzeScreenIntent.OnChecklistNameChanged(it))
                },
                onCreateNew = { viewModel.sendIntent(AnalyzeScreenIntent.OnCreateNewChecklistClick) },
                onDismiss = { viewModel.sendIntent(AnalyzeScreenIntent.OnDismissResult) }
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
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.analyze_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onTextChanged = { onIntent(AnalyzeScreenIntent.OnTextInputChanged(it)) },
                    onUrlChanged = { onIntent(AnalyzeScreenIntent.OnUrlInputChanged(it)) },
                    onFileSelected = { path, name -> onIntent(AnalyzeScreenIntent.OnFileSelected(path, name)) }
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
        InputTypeCard(
            icon = Icons.Outlined.TextFields,
            title = stringResource(Res.string.analyze_source_text),
            isSelected = selectedType == InputDataType.RAW_TEXT,
            onClick = { onTypeSelected(InputDataType.RAW_TEXT) },
            modifier = Modifier.fillMaxWidth()
        )
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
    onTextChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onFileSelected: (filePath: String, fileName: String) -> Unit
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
                label = stringResource(Res.string.analyze_url_label)
            )
        }

        InputDataType.RAW_TEXT -> {
            AppTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = stringResource(Res.string.analyze_text_placeholder),
                label = stringResource(Res.string.analyze_text_label),
                singleLine = false,
                maxLines = 10
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
private fun ResultDialog(
    result: AnalyzeResult,
    checklistName: String,
    onChecklistNameChanged: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val itemsFoundText = stringResource(Res.string.analyze_items_found)
    val remainingCount = result.suggestedItems.size - 5
    val andMoreText = stringResource(Res.string.analyze_and_more, remainingCount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.analyze_result_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
            ) {
                result.summary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "$itemsFoundText ${result.suggestedItems.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
                ) {
                    result.suggestedItems.take(5).forEach { item ->
                        Text(
                            text = "• ${item.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (result.suggestedItems.size > 5) {
                        Text(
                            text = andMoreText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

                AppTextField(
                    value = checklistName,
                    onValueChange = onChecklistNameChanged,
                    label = stringResource(Res.string.analyze_checklist_name_label),
                    placeholder = stringResource(Res.string.analyze_checklist_name_placeholder)
                )

                result.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.analyze_create_checklist),
                onClick = onCreateNew
            )
        },
        dismissButton = {
            AppButtonText(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}
