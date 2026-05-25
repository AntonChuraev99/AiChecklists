package com.antonchuraev.homesearchchecklist.feature.home.presentation.fills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
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

@OptIn(ExperimentalMaterial3Api::class)
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

/** Minimum column width for fill cards in grid mode (Medium/Expanded). */
private val FillGridMinColumnSize = 320.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FillsListContent(
    state: FillsListState.Content,
    onIntent: (FillsListIntent) -> Unit
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        title = stringResource(Res.string.fills_list_title),
        onBackButtonClick = { onIntent(FillsListIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
    ) {
        if (isCompact) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .adaptiveContentWidth()
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
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = FillGridMinColumnSize),
                modifier = Modifier.fillMaxSize().adaptiveContentWidth(maxWidthDp = 1200),
                contentPadding = PaddingValues(
                    start = AppDimens.ScreenPaddingHorizontal,
                    end = AppDimens.ScreenPaddingHorizontal,
                    top = AppDimens.SpacingMd,
                    bottom = AppDimens.SpacingXxl,
                ),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                // Checklist name header — full-width across all columns
                item(
                    key = "checklist_name",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        text = state.checklist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = AppDimens.SpacingXs),
                    )
                }

                items(state.fills, key = { it.id }) { fill ->
                    FillCard(
                        fill = fill,
                        onClick = { onIntent(FillsListIntent.OnFillClick(fill)) },
                        onDeleteClick = { onIntent(FillsListIntent.OnDeleteFillClick(fill)) }
                    )
                }
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
                    AppLinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
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
