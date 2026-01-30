package com.antonchuraev.homesearchchecklist.feature.home.presentation.fill

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FillDetailScreen(
    fillId: Long,
    viewModel: FillDetailViewModel = koinViewModel { parametersOf(fillId) }
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    when (val currentState = state) {
        FillDetailState.Loading -> LoadingContent()
        FillDetailState.NotFound -> NotFoundContent(
            onBack = { viewModel.sendIntent(FillDetailIntent.OnBackClick) }
        )
        is FillDetailState.Content -> FillDetailContent(
            state = currentState,
            onIntent = viewModel::sendIntent
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotFoundContent(onBack: () -> Unit) {
    AppScaffold(
        title = stringResource(Res.string.error),
        onBackButtonClick = onBack
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.fill_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FillDetailContent(
    state: FillDetailState.Content,
    onIntent: (FillDetailIntent) -> Unit
) {
    AppScaffold(
        title = if (state.isEditing) stringResource(Res.string.fill_edit_title) else state.fill.name,
        onBackButtonClick = { onIntent(FillDetailIntent.OnBackClick) },
        actions = {
            if (state.isEditing) {
                IconButton(onClick = { onIntent(FillDetailIntent.OnCancelEditClick) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.cancel))
                }
                IconButton(onClick = { onIntent(FillDetailIntent.OnSaveClick) }) {
                    Icon(Icons.Outlined.Check, contentDescription = stringResource(Res.string.save))
                }
            } else {
                IconButton(onClick = { onIntent(FillDetailIntent.OnEditClick) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.checklist_edit))
                }
                IconButton(onClick = { onIntent(FillDetailIntent.OnDeleteClick) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
            }

            if (state.isEditing) {
                item {
                    Column {
                        AppTextField(
                            value = state.editingName,
                            onValueChange = { onIntent(FillDetailIntent.OnNameChanged(it)) },
                            label = stringResource(Res.string.checklist_fill_name_label),
                            placeholder = stringResource(Res.string.checklist_fill_name_placeholder),
                            isError = state.editingNameError != null
                        )
                        state.editingNameError?.let { error ->
                            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                }
            }

            item {
                ProgressHeader(fill = state.fill)
            }

            itemsIndexed(
                items = state.fill.items,
                key = { _, item -> item.id }
            ) { index, item ->
                FillItemCard(
                    item = item,
                    onCheckedChange = { checked ->
                        onIntent(FillDetailIntent.OnItemCheckedChange(index, checked))
                    },
                    onNoteClick = { onIntent(FillDetailIntent.OnAddNoteClick(index)) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
            }
        }
    }

    if (state.noteDialogItemIndex != null) {
        NoteDialog(
            note = state.editingNote,
            onNoteChanged = { onIntent(FillDetailIntent.OnNoteChanged(it)) },
            onDismiss = { onIntent(FillDetailIntent.OnDismissNoteDialog) },
            onConfirm = { onIntent(FillDetailIntent.OnSaveNote) }
        )
    }

    if (state.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            fillName = state.fill.name,
            onConfirm = { onIntent(FillDetailIntent.OnConfirmDelete) },
            onDismiss = { onIntent(FillDetailIntent.OnDismissDeleteConfirmation) }
        )
    }
}

@Composable
private fun ProgressHeader(fill: ChecklistFill) {
    val checkedCount = fill.items.count { it.checked }
    val totalCount = fill.items.size
    val progress = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f
    val isComplete = totalCount > 0 && checkedCount == totalCount

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Completion celebration banner
        AnimatedVisibility(
            visible = isComplete,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CompletionBanner()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.checklist_progress),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$checkedCount / $totalCount",
                style = MaterialTheme.typography.titleMedium,
                color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
    }
}

@Composable
private fun CompletionBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.fill_complete_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.fill_complete_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FillItemCard(
    item: ChecklistFillItem,
    onCheckedChange: (Boolean) -> Unit,
    onNoteClick: () -> Unit
) {
    AppCard(
        onClick = { onCheckedChange(!item.checked) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = onCheckedChange
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNoteClick) {
                    Icon(
                        Icons.Outlined.NoteAdd,
                        contentDescription = stringResource(Res.string.fill_add_note),
                        tint = if (item.note != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = AppDimens.SpacingSm)
                )
            }
        }
    }
}

@Composable
private fun NoteDialog(
    note: String,
    onNoteChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.fill_note_dialog_title)) },
        text = {
            AppTextField(
                value = note,
                onValueChange = onNoteChanged,
                label = "",
                placeholder = stringResource(Res.string.fill_note_placeholder)
            )
        },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.save),
                onClick = onConfirm
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

@Composable
private fun DeleteConfirmationDialog(
    fillName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.checklist_fill_delete_title)) },
        text = {
            Text(stringResource(Res.string.checklist_fill_delete_message, fillName))
        },
        confirmButton = {
            AppButton(
                text = stringResource(Res.string.delete),
                onClick = onConfirm
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
