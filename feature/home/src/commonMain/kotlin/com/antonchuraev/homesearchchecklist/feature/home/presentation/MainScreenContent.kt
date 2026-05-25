package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.desingsystem.components.PremiumBanner
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Minimum column width for checklist cards in grid mode (Medium/Expanded). */
private val ChecklistGridMinColumnSize = 320.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreenContent(
    screenState: MainScreenState.Success,
    isEditMode: Boolean,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onAddChecklistClick: () -> Unit,
    onAiAnalyzeClick: () -> Unit,
    onPremiumBannerClick: () -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onReorderChecklists: (List<Long>) -> Unit = {}
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    if (isCompact) {
        MainScreenContentLazyColumn(
            screenState = screenState,
            isEditMode = isEditMode,
            onChecklistClick = onChecklistClick,
            onPremiumBannerClick = onPremiumBannerClick,
            onEnterEditMode = onEnterEditMode,
            onReorderChecklists = onReorderChecklists,
        )
    } else {
        MainScreenContentLazyGrid(
            screenState = screenState,
            onChecklistClick = onChecklistClick,
            onPremiumBannerClick = onPremiumBannerClick,
        )
    }
}

/**
 * Compact (phone) path — reorderable LazyColumn with drag-drop.
 * sh.calvin.reorderable only supports LazyColumn/LazyRow, so this path
 * is kept as-is for Compact screens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreenContentLazyColumn(
    screenState: MainScreenState.Success,
    isEditMode: Boolean,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onPremiumBannerClick: () -> Unit,
    onEnterEditMode: () -> Unit,
    onReorderChecklists: (List<Long>) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Local mutable list for optimistic reorder
    var localList by remember(screenState.checklists) {
        mutableStateOf(screenState.checklists)
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Offset by 1 because premium_banner item occupies index 0
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localList.size && toIndex < localList.size) {
            localList = localList.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Wiggle animation for edit mode
    val wiggleTransition = rememberInfiniteTransition(label = "wiggle")
    val wiggleAngle by wiggleTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleAngle"
    )

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingLg,
            bottom = AppDimens.SpacingXxl
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        item(key = "premium_banner") {
            PremiumBanner(
                isActive = screenState.subscriptionStatus.isActive,
                formattedExpirationDate = screenState.formattedExpirationDate,
                onUpgradeClick = onPremiumBannerClick,
                onSubscriptionClick = onPremiumBannerClick
            )
        }

        if (localList.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Checklist,
                        title = stringResource(Res.string.main_empty_title),
                        description = stringResource(Res.string.main_empty_description)
                    )
                }
            }
        } else {
            items(
                items = localList,
                key = { it.checklist.id }
            ) { checklistWithProgress ->
                ReorderableItem(
                    state = reorderableState,
                    key = checklistWithProgress.checklist.id,
                    enabled = isEditMode
                ) { isDragging ->
                    val isDark = LocalIsDarkTheme.current

                    val cardModifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (isEditMode && !isDragging) {
                                rotationZ = wiggleAngle
                            }
                        }
                        .then(
                            if (isEditMode) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onReorderChecklists(localList.map { it.checklist.id })
                                    }
                                )
                            } else {
                                Modifier.combinedClickable(
                                    onClick = { onChecklistClick(checklistWithProgress) },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onEnterEditMode()
                                    }
                                )
                            }
                        )

                    ChecklistCard(
                        checklistWithProgress = checklistWithProgress,
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        modifier = cardModifier,
                    )
                }
            }
        }
    }
}

/**
 * Medium/Expanded (tablet, desktop) path — non-reorderable LazyVerticalGrid.
 *
 * GridCells.Adaptive(minSize = 320.dp) distributes columns automatically:
 * - Medium (~600dp content area after rail): 1–2 columns
 * - Expanded (~840dp+ content area after permanent drawer): 2–3 columns
 *
 * Edit Mode ("Done" button / long-press drag) is hidden on this path because
 * sh.calvin.reorderable does not support LazyVerticalGrid.
 *
 * PremiumBanner uses span = maxLineSpan to occupy the full row width.
 * EmptyState uses span = maxLineSpan to stay centered across all columns.
 */
@Composable
private fun MainScreenContentLazyGrid(
    screenState: MainScreenState.Success,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onPremiumBannerClick: () -> Unit,
) {
    if (screenState.checklists.isEmpty()) {
        // Empty state — center it, no grid needed
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Outlined.Checklist,
                title = stringResource(Res.string.main_empty_title),
                description = stringResource(Res.string.main_empty_description),
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = ChecklistGridMinColumnSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingLg,
            bottom = AppDimens.SpacingXxl,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        // Full-width premium banner across all columns
        item(
            key = "premium_banner",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            PremiumBanner(
                isActive = screenState.subscriptionStatus.isActive,
                formattedExpirationDate = screenState.formattedExpirationDate,
                onUpgradeClick = onPremiumBannerClick,
                onSubscriptionClick = onPremiumBannerClick,
            )
        }

        items(
            items = screenState.checklists,
            key = { it.checklist.id },
        ) { checklistWithProgress ->
            ChecklistCard(
                checklistWithProgress = checklistWithProgress,
                isEditMode = false,
                isDragging = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onChecklistClick(checklistWithProgress) },
                    ),
            )
        }
    }
}

/**
 * Shared card composable used by both LazyColumn (Compact) and LazyVerticalGrid
 * (Medium/Expanded) paths.
 *
 * The [modifier] is supplied by the caller and carries the clickable/draggable
 * gesture — this keeps the card itself free of gesture coupling.
 */
@Composable
private fun ChecklistCard(
    checklistWithProgress: ChecklistWithProgress,
    isEditMode: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
            ) {
                Text(
                    text = checklistWithProgress.checklist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (checklistWithProgress.totalItems > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
                    ) {
                        AppLinearProgressIndicator(
                            progress = { checklistWithProgress.progress },
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "${checklistWithProgress.checkedItems}/${checklistWithProgress.totalItems}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.main_no_items),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isEditMode) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (isDark) {
        val borderColor by animateColorAsState(
            targetValue = if (isDragging) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            label = "border_color"
        )
        val borderWidth by animateDpAsState(
            targetValue = if (isDragging) 2.dp else 1.dp,
            label = "border_width"
        )
        OutlinedCard(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(borderWidth, borderColor)
        ) { cardContent() }
    } else {
        val elevation by animateDpAsState(
            targetValue = if (isDragging) 8.dp else AppDimens.CardElevation,
            label = "card_elevation"
        )
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) { cardContent() }
    }
}
