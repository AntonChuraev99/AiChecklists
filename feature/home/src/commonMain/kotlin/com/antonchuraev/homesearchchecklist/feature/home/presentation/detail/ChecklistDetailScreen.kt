package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
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
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChecklistDetailScreen(
    checklistId: Long,
    viewModel: ChecklistDetailViewModel = koinViewModel { parametersOf(checklistId) }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("checklist_detail") }

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
    AppScaffold(
        title = state.checklist.name,
        onBackButtonClick = { onIntent(ChecklistDetailIntent.OnBackClick) },
        actions = {
            IconButton(onClick = { onIntent(ChecklistDetailIntent.OnShareClick) }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(Res.string.share))
            }
            IconButton(onClick = { onIntent(ChecklistDetailIntent.OnEditChecklistClick) }) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.checklist_edit))
            }
            IconButton(onClick = { onIntent(ChecklistDetailIntent.OnDeleteChecklistClick) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                        .padding(top = AppDimens.SpacingMd, bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
                ) {
                    AppButtonSecondary(
                        text = stringResource(Res.string.checklist_new_fill),
                        onClick = { onIntent(ChecklistDetailIntent.OnAddFillClick) },
                        icon = Icons.Outlined.ContentCopy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppButton(
                        text = stringResource(Res.string.checklist_add_fill_ai),
                        onClick = { onIntent(ChecklistDetailIntent.OnAddFillViaAiClick) },
                        icon = Icons.Outlined.AutoAwesome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        val defaultFill = state.defaultFill

        if (defaultFill == null) {
            // This shouldn't happen normally, but handle gracefully
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                item {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                }

                // Progress header with completion celebration
                item {
                    ProgressHeader(fill = defaultFill)
                }

                // View all fills button (if there are additional fills)
                if (state.additionalFillsCount > 0) {
                    item {
                        ViewAllFillsCard(
                            fillsCount = state.additionalFillsCount,
                            onClick = { onIntent(ChecklistDetailIntent.OnViewAllFillsClick) }
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                    }
                }

                // Checklist items
                itemsIndexed(
                    items = defaultFill.items,
                    key = { _, item -> item.id }
                ) { index, item ->
                    ChecklistItemCard(
                        item = item,
                        onCheckedChange = { checked ->
                            onIntent(ChecklistDetailIntent.OnItemCheckedChange(index, checked))
                        },
                        onNoteClick = { onIntent(ChecklistDetailIntent.OnAddNoteClick(index)) }
                    )
                }

                // Bottom spacing for the fixed buttons
                item {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                }
            }
        }
    }

    // Note dialog
    if (state.noteDialogItemIndex != null) {
        NoteDialog(
            note = state.editingNote,
            onNoteChanged = { onIntent(ChecklistDetailIntent.OnNoteChanged(it)) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissNoteDialog) },
            onConfirm = { onIntent(ChecklistDetailIntent.OnSaveNote) }
        )
    }

    // Add fill dialog
    if (state.showAddFillDialog) {
        AddFillDialog(
            fillName = state.newFillName,
            error = state.fillNameError,
            isLoading = state.isCreatingFill,
            onNameChanged = { onIntent(ChecklistDetailIntent.OnNewFillNameChanged(it)) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissAddFillDialog) },
            onConfirm = { onIntent(ChecklistDetailIntent.OnConfirmAddFill) }
        )
    }

    // Delete confirmation dialog
    if (state.showDeleteConfirmation) {
        DeleteConfirmationDialog(
            checklistName = state.checklist.name,
            onConfirm = { onIntent(ChecklistDetailIntent.OnConfirmDeleteChecklist) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissDeleteConfirmation) }
        )
    }

    // Fill limit dialog
    if (state.showFillLimitDialog && state.userLimits != null) {
        FillLimitDialog(
            maxFills = state.userLimits.maxFillsPerChecklist,
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissFillLimitDialog) },
            onUpgrade = { onIntent(ChecklistDetailIntent.OnUpgradeToPremiumClick) }
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
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
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
private fun ViewAllFillsCard(
    fillsCount: Int,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.checklist_view_all_fills),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.checklist_fills_count, fillsCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChecklistItemCard(
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
private fun AddFillDialog(
    fillName: String,
    error: String?,
    isLoading: Boolean,
    onNameChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(Res.string.checklist_add_fill_dialog_title)) },
        text = {
            Column {
                AppTextField(
                    value = fillName,
                    onValueChange = onNameChanged,
                    label = stringResource(Res.string.checklist_fill_name_label),
                    placeholder = stringResource(Res.string.checklist_fill_name_placeholder),
                    isError = error != null,
                    enabled = !isLoading,
                    showClearButton = true
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                AppButtonText(
                    text = stringResource(Res.string.save),
                    onClick = onConfirm
                )
            }
        },
        dismissButton = {
            if (!isLoading) {
                AppButtonText(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss
                )
            }
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

@Composable
private fun FillLimitDialog(
    maxFills: Int,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.limit_fill_reached_title)) },
        text = {
            Text(stringResource(Res.string.limit_fill_reached_message, maxFills))
        },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.limit_upgrade),
                onClick = onUpgrade
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
