package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChecklistDetailScreen(
    checklistId: Long,
    viewModel: ChecklistDetailViewModel = koinViewModel { parametersOf(checklistId) }
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    when (val currentState = state) {
        ChecklistDetailState.Loading -> LoadingContent()
        ChecklistDetailState.NotFound -> NotFoundContent(
            onBack = { viewModel.sendIntent(ChecklistDetailIntent.OnBackClick) }
        )
        is ChecklistDetailState.Content -> ChecklistDetailContent(
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
                text = stringResource(Res.string.checklist_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChecklistDetailContent(
    state: ChecklistDetailState.Content,
    onIntent: (ChecklistDetailIntent) -> Unit
) {
    var showAddItemDialog by remember { mutableStateOf(false) }

    AppScaffold(
        title = if (state.isEditing) stringResource(Res.string.checklist_edit_title) else state.checklist.name,
        onBackButtonClick = { onIntent(ChecklistDetailIntent.OnBackClick) },
        actions = {
            if (state.isEditing) {
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnCancelEditClick) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.cancel))
                }
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnSaveClick) }) {
                    Icon(Icons.Outlined.Check, contentDescription = stringResource(Res.string.save))
                }
            } else {
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnEditClick) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.checklist_edit))
                }
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnDeleteChecklistClick) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        AppTextField(
                            value = state.editingName,
                            onValueChange = { onIntent(ChecklistDetailIntent.OnNameChanged(it)) },
                            label = stringResource(Res.string.create_name_section),
                            placeholder = stringResource(Res.string.create_name_placeholder)
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                    }
                }

                item {
                    ProgressHeader(checklist = state.checklist)
                }

                if (state.checklist.items.isEmpty()) {
                    item {
                        EmptyItemsMessage()
                    }
                } else {
                    itemsIndexed(state.checklist.items) { index, item ->
                        ChecklistItemCard(
                            item = item,
                            isEditing = state.isEditing,
                            onCheckedChange = { checked ->
                                onIntent(ChecklistDetailIntent.OnItemCheckedChange(index, checked))
                            },
                            onDeleteClick = { onIntent(ChecklistDetailIntent.OnDeleteItemClick(index)) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            FloatingActionButton(
                onClick = { showAddItemDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppDimens.SpacingLg),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(Res.string.create_add_item),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showAddItemDialog) {
        AddItemDialog(
            onDismiss = { showAddItemDialog = false },
            onConfirm = { text ->
                onIntent(ChecklistDetailIntent.OnAddItem(text))
                showAddItemDialog = false
            }
        )
    }

    if (state.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            checklistName = state.checklist.name,
            onConfirm = { onIntent(ChecklistDetailIntent.OnConfirmDelete) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissDeleteConfirmation) }
        )
    }
}

@Composable
private fun ProgressHeader(checklist: Checklist) {
    val checkedCount = checklist.items.count { it.checked }
    val totalCount = checklist.items.size
    val progress = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
    }
}

@Composable
private fun EmptyItemsMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingXxl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.checklist_no_items),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChecklistItemCard(
    item: ChecklistItem,
    isEditing: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    AppCard(
        onClick = { if (!isEditing) onCheckedChange(!item.checked) }
    ) {
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
            if (isEditing) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_add_item_dialog_title)) },
        text = {
            AppTextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(Res.string.create_item_name_label),
                placeholder = ""
            )
        },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.save),
                onClick = { onConfirm(text) }
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
    checklistName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.checklist_delete_title)) },
        text = {
            Text(stringResource(Res.string.checklist_delete_message, checklistName))
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
