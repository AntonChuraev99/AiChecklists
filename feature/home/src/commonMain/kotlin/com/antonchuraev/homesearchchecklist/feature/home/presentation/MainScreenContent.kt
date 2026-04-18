package com.antonchuraev.homesearchchecklist.feature.home.presentation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.components.PremiumBanner
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
                    val elevation by animateDpAsState(
                        if (isDragging) 8.dp else AppDimens.CardElevation
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isEditMode && !isDragging) {
                                    rotationZ = wiggleAngle
                                }
                            }
                            .then(
                                if (isEditMode) {
                                    Modifier.draggableHandle(
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
                            ),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                    ) {
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
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (checklistWithProgress.totalItems > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { checklistWithProgress.progress },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(MaterialTheme.shapes.small),
                                            color = MaterialTheme.colorScheme.primary,
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
                }
            }
        }
    }
}
