package com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
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
                        // When folders are used the count reflects checkable leaves (folders aren't
                        // items), matching the count shown above the list. On the flat path it's the
                        // included (soft-capped) editable list.
                        val createCount = if (state.useFolders) {
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
                        onAddItem = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnAddItem) },
                        onUseFoldersChanged = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnUseFoldersChanged(it)) },
                        onToggleOverflowExpanded = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnToggleOverflowExpanded) },
                        onAddOverflowItem = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnAddOverflowItem(it)) },
                        onAddAllOverflowItems = { viewModel.sendIntent(AnalyzeResultPreviewScreenIntent.OnAddAllOverflowItems) }
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
    onAddItem: () -> Unit,
    onUseFoldersChanged: (Boolean) -> Unit,
    onToggleOverflowExpanded: () -> Unit,
    onAddOverflowItem: (Int) -> Unit,
    onAddAllOverflowItems: () -> Unit
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

        // "Use folders" opt-in. Only offered when the AI actually returned a structure (nothing to
        // fold otherwise). Default OFF — a flat checklist unless the user opts in.
        if (state.aiReturnedFolders) {
            item {
                UseFoldersToggle(
                    checked = state.useFolders,
                    onCheckedChange = onUseFoldersChanged
                )
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
            }
        }

        // Items count. When folders are used count only checkable leaves (folders aren't items);
        // on the flat path count the included (soft-capped) items.
        val displayCount = if (state.useFolders) {
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

        if (state.useFolders) {
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
            // Soft 10-item recommendation. Shown only when the cap actually held items back; the
            // held-back items live in the "Show N more" section below (nothing is lost).
            if (state.hasOverflow) {
                item {
                    Text(
                        text = stringResource(Res.string.analyze_preview_soft_cap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
                    )
                }
            }

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

            // Included (editable) items list
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

            // Soft-cap overflow: a collapsed expander revealing the held-back items, each with an
            // "add" affordance, plus an "Add all". Fully recoverable — nothing was truncated.
            if (state.hasOverflow) {
                item {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                    OverflowHeader(
                        count = state.overflowItems.size,
                        expanded = state.isOverflowExpanded,
                        onToggle = onToggleOverflowExpanded,
                        onAddAll = onAddAllOverflowItems
                    )
                }
                if (state.isOverflowExpanded) {
                    itemsIndexed(
                        items = state.overflowItems,
                        key = { index, item -> "overflow-$index-${item.hashCode()}" }
                    ) { index, item ->
                        OverflowItemCard(
                            text = item,
                            onAdd = { onAddOverflowItem(index) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Bottom padding for button
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * "Use folders" opt-in row. Per the design-system rule the [AppSwitch] takes a null
 * `onCheckedChange` and the surrounding clickable Row owns the toggle (avoids the double-toggle
 * bug). Label + helper on the left, switch on the right.
 */
@Composable
private fun UseFoldersToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.analyze_preview_use_folders_label),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.analyze_preview_use_folders_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
        AppSwitch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

/**
 * Header for the soft-cap overflow section: a "Show N more" / "Show less" expander on the left and
 * an "Add all" shortcut on the right.
 */
@Composable
private fun OverflowHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onToggle) {
            Text(
                text = if (expanded)
                    stringResource(Res.string.analyze_preview_show_less)
                else
                    stringResource(Res.string.analyze_preview_show_more, count)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onAddAll) {
            Text(text = stringResource(Res.string.analyze_preview_add_all))
        }
    }
}

/**
 * A held-back overflow item (folders-off soft cap). Read-only text with an "add" button that moves
 * it into the included list. Distinguished from [ChecklistItemCard] by the trailing Add icon.
 */
@Composable
private fun OverflowItemCard(
    text: String,
    onAdd: () -> Unit,
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
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.analyze_preview_add_overflow_item),
                    tint = MaterialTheme.colorScheme.primary,
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

