package com.antonchuraev.homesearchchecklist.feature.home.presentation.fills

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FillsListScreen(
    checklistId: Long,
    viewModel: FillsListViewModel = koinViewModel { parametersOf(checklistId) }
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    when (val currentState = state) {
        FillsListState.Loading -> LoadingContent()
        FillsListState.NotFound -> NotFoundContent(
            onBack = { viewModel.sendIntent(FillsListIntent.OnBackClick) }
        )
        is FillsListState.Content -> FillsListContent(
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
private fun FillsListContent(
    state: FillsListState.Content,
    onIntent: (FillsListIntent) -> Unit
) {
    AppScaffold(
        title = stringResource(Res.string.fills_list_title),
        onBackButtonClick = { onIntent(FillsListIntent.OnBackClick) }
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

            item {
                Text(
                    text = state.checklist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            }

            items(state.fills, key = { it.id }) { fill ->
                FillCard(
                    fill = fill,
                    onClick = { onIntent(FillsListIntent.OnFillClick(fill)) },
                    onDeleteClick = { onIntent(FillsListIntent.OnDeleteFillClick(fill)) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
            }
        }
    }

    // Delete confirmation dialog
    state.fillToDelete?.let { fill ->
        DeleteFillDialog(
            fillName = fill.name,
            onConfirm = { onIntent(FillsListIntent.OnConfirmDeleteFill) },
            onDismiss = { onIntent(FillsListIntent.OnDismissDeleteDialog) }
        )
    }
}

@Composable
private fun FillCard(
    fill: ChecklistFill,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val checkedCount = fill.items.count { it.checked }
    val totalCount = fill.items.size
    val progress = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f

    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fill.isDefault) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(AppDimens.SpacingXs))
                    }
                    Text(
                        text = if (fill.isDefault && fill.name.isBlank()) {
                            stringResource(Res.string.fills_default_name)
                        } else {
                            fill.name
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
                    Text(
                        text = "$checkedCount / $totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Only show delete button for non-default fills
            if (!fill.isDefault) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Outlined.Delete,
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
private fun DeleteFillDialog(
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
