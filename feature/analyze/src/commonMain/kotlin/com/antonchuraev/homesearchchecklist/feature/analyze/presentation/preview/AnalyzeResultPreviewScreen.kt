package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AddItemInputField
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree.ChecklistTree
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeResultPreviewScreen(
    viewModel: AnalyzeResultPreviewViewModel = koinViewModel()
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.ANALYZE_RESULT) }

    val state by viewModel.screenState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        title = if (state.isFillMode)
            stringResource(Res.string.analyze_preview_fill_title)
        else
            stringResource(Res.string.analyze_preview_title),
        onBackButtonClick = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (!state.isLoading && state.editableItems.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.ScreenPaddingHorizontal)
                            .padding(vertical = AppDimens.SpacingLg)
                            .navigationBarsPadding()
                    ) {
                        // In folder mode the count reflects checkable leaves (folders aren't items),
                        // matching the count shown above the list.
                        val createCount = if (state.hasFolders) {
                            state.structuredItems.count { it.type == ChecklistNodeType.ITEM }
                        } else {
                            state.editableItems.size
                        }
                        AppButton(
                            text = if (state.isCreating)
                                stringResource(Res.string.analyze_preview_creating)
                            else if (state.fillDefault)
                                stringResource(Res.string.fill_apply)
                            else if (state.isFillMode)
                                stringResource(Res.string.analyze_preview_create_fill_button, createCount)
                            else
                                stringResource(Res.string.analyze_preview_create_button, createCount),
                            onClick = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnCreateChecklist) },
                            icon = Icons.Filled.Add,
                            enabled = !state.isCreating && state.editableItems.isNotEmpty() && (state.fillDefault || state.checklistName.isNotBlank()),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    AnalyzeResultPreviewContent(
                        state = state,
                        onChecklistNameChanged = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnChecklistNameChanged(it)) },
                        onRemoveItem = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnRemoveItem(it)) },
                        onNewItemTextChange = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnNewItemTextChange(it)) },
                        onAddItem = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnAddItem) }
                    )
                }
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(AppDimens.SpacingLg),
                    action = {
                        TextButton(onClick = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnDismissError) }) {
                            Text(stringResource(Res.string.ok))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun AnalyzeResultPreviewContent(
    state: AnalyzeResultPreviewScreenState,
    onChecklistNameChanged: (String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onNewItemTextChange: (String) -> Unit,
    onAddItem: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.ScreenPaddingHorizontal,
            vertical = AppDimens.SpacingLg
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        // Fill mode header
        if (state.isFillMode && state.targetChecklistName != null) {
            item {
                Text(
                    text = stringResource(Res.string.analyze_preview_for_checklist, state.targetChecklistName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
                )
            }
        }

        // Summary
        state.summary?.let { summary ->
            item {
                val summaryShape = RoundedCornerShape(12.dp)
                val summaryContent: @Composable () -> Unit = {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(AppDimens.SpacingMd)
                    )
                }
                // Summary card intentionally tints its surface with a faint primary wash to set it
                // apart from the item list; the rest of the style (hairline border, no shadow) follows
                // the shared flat card defaults.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = summaryShape,
                    colors = AppCardDefaults.colors(
                        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    border = AppCardDefaults.border(),
                    elevation = AppCardDefaults.flatElevation()
                ) { summaryContent() }
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
            }
        }

        // Checklist name input (hidden in fill-default mode)
        if (!state.fillDefault) {
            item {
                AppTextField(
                    value = state.checklistName,
                    onValueChange = onChecklistNameChanged,
                    label = if (state.isFillMode)
                        stringResource(Res.string.analyze_preview_fill_name_label)
                    else
                        stringResource(Res.string.analyze_preview_name_label),
                    modifier = Modifier.fillMaxWidth(),
                    showClearButton = true
                )
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
            }
        }

        // Items count. In folder mode count only checkable leaves (folders aren't items).
        val displayCount = if (state.hasFolders) {
            state.structuredItems.count { it.type == ChecklistNodeType.ITEM }
        } else {
            state.editableItems.size
        }
        item {
            Text(
                text = pluralStringResource(Res.plurals.items_count, displayCount, displayCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
            )
        }

        if (state.hasFolders) {
            // Folder mode: read-only hierarchical preview. Editing a flat row would desync the
            // tree, so add/remove are hidden; the user restructures after creation.
            item {
                Text(
                    text = stringResource(Res.string.analyze_preview_folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
                )
            }
            itemsIndexed(
                items = state.structuredItems,
                key = { _, item -> item.id }
            ) { _, item ->
                val depth = (ChecklistTree.ancestorPath(state.structuredItems, item.id).size - 1)
                    .coerceAtLeast(0)
                StructuredItemCard(item = item, depth = depth)
            }
        } else {
            // Add new item field (at top, new items appear below)
            item {
                AddItemInputField(
                    text = state.newItemText,
                    onTextChange = onNewItemTextChange,
                    onAdd = onAddItem,
                    placeholder = stringResource(Res.string.analyze_preview_add_item_hint)
                )
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
            }

            // Editable items list (new items appear at top)
            itemsIndexed(
                items = state.editableItems,
                key = { index, item -> "$index-${item.hashCode()}" }
            ) { index, item ->
                ChecklistItemCard(
                    text = item,
                    onRemove = { onRemoveItem(index) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Bottom padding for button
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ChecklistItemCard(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox placeholder
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))

            // Item text
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.analyze_preview_remove_item),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = AppCardDefaults.colors(),
        border = AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation()
    ) { cardContent() }
}

/**
 * Read-only card for the folder-mode preview. Indents by tree [depth] and shows a folder icon
 * for FOLDER nodes (bold) or a checkbox placeholder for leaves. No remove/edit affordance —
 * the structure is finalized at creation; the user restructures on the detail screen.
 */
@Composable
private fun StructuredItemCard(
    item: ChecklistItem,
    depth: Int,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val isFolder = item.type == ChecklistNodeType.FOLDER
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.SpacingMd + (AppDimens.SpacingLg * depth),
                    end = AppDimens.SpacingMd,
                    top = AppDimens.SpacingSm,
                    bottom = AppDimens.SpacingSm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFolder) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                // Checkbox placeholder (matches the flat preview card)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))

            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFolder) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = AppCardDefaults.colors(),
        border = AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation()
    ) { cardContent() }
}

