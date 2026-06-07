package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.SyncAccountBanner
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.desingsystem.components.PremiumBanner
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChecklistListCard
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
    onReorderChecklists: (List<Long>) -> Unit = {},
    onSignInClick: () -> Unit = {},
    // Bottom contentPadding for the list/grid so the last item scrolls clear of the floating
    // chat-dock overlay (the host measures the dock height and passes it here). Defaults to the
    // plain bottom spacing when no dock is shown.
    contentBottomPadding: Dp = AppDimens.SpacingXxl,
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val isCompact = windowSizeClass == AppWindowSizeClass.Compact

    if (isCompact) {
        MainScreenContentLazyColumn(
            screenState = screenState,
            isEditMode = isEditMode,
            onChecklistClick = onChecklistClick,
            onAddChecklistClick = onAddChecklistClick,
            onPremiumBannerClick = onPremiumBannerClick,
            onEnterEditMode = onEnterEditMode,
            onReorderChecklists = onReorderChecklists,
            onSignInClick = onSignInClick,
            contentBottomPadding = contentBottomPadding,
        )
    } else {
        MainScreenContentLazyGrid(
            screenState = screenState,
            onChecklistClick = onChecklistClick,
            onAddChecklistClick = onAddChecklistClick,
            onPremiumBannerClick = onPremiumBannerClick,
            contentBottomPadding = contentBottomPadding,
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
    onAddChecklistClick: () -> Unit,
    onPremiumBannerClick: () -> Unit,
    onEnterEditMode: () -> Unit,
    onReorderChecklists: (List<Long>) -> Unit,
    onSignInClick: () -> Unit = {},
    contentBottomPadding: Dp = AppDimens.SpacingXxl,
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Local mutable list for optimistic reorder
    var localList by remember(screenState.checklists) {
        mutableStateOf(screenState.checklists)
    }

    val lazyListState = rememberLazyListState()
    // Header items before the checklist rows. Their count drives the reorder index offset.
    // Gisti redesign: the loud premium banner is shown only to active subscribers (status);
    // free users get a calm upgrade hint below the list instead. A "My lists" section header
    // always precedes the cards when the list is non-empty.
    // The loud "Premium Member" status banner was removed — premium is now shown as a
    // "PRO" prefix on the credits chip (top bar). Free users still get the upgrade banner below.
    val headerItemCount = (if (screenState.isGoogleLinked) 0 else 1) +
        1 // "My lists" section header

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Offset by headerItemCount because banner items occupy the first indices
        val fromIndex = from.index - headerItemCount
        val toIndex = to.index - headerItemCount
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
            bottom = contentBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        if (!screenState.isGoogleLinked) {
            item(key = "sync_banner") {
                SyncAccountBanner(onSignInClick = onSignInClick)
            }
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
                        description = stringResource(Res.string.main_empty_description),
                        action = {
                            AppButton(
                                text = stringResource(Res.string.main_empty_cta),
                                onClick = onAddChecklistClick,
                            )
                        }
                    )
                }
            }
        } else {
            item(key = "my_lists_header") {
                MyListsHeader(count = localList.size)
            }
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

                    // Drag/wiggle stay on the OUTER modifier (not clipped — shadow + drag
                    // transform survive). Tap/long-press-to-edit move INSIDE the card via
                    // onClick/onLongClick so the ripple is clipped to the rounded corners.
                    // In edit mode the card is not tappable (the drag handle owns the gesture).
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
                                Modifier
                            }
                        )

                    ChecklistCard(
                        checklistWithProgress = checklistWithProgress,
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        modifier = cardModifier,
                        onClick = if (isEditMode) {
                            null
                        } else {
                            { onChecklistClick(checklistWithProgress) }
                        },
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnterEditMode()
                        },
                    )
                }
            }

            // Free users get the bright gradient upsell banner below the list (replaces
            // the earlier quiet CalmUpgradeHint string per the 2026-06-01 redesign).
            // Hidden for premium users. verticalArrangement spacedBy already separates it
            // from the last card, so no extra top padding is needed.
            val limits = screenState.userLimits
            if (limits != null && !limits.isPremium) {
                item(key = "upgrade_banner") {
                    PremiumBanner(
                        isActive = false,
                        formattedExpirationDate = null,
                        onUpgradeClick = onPremiumBannerClick,
                        onSubscriptionClick = onPremiumBannerClick,
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
    onAddChecklistClick: () -> Unit,
    onPremiumBannerClick: () -> Unit,
    contentBottomPadding: Dp = AppDimens.SpacingXxl,
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
                action = {
                    AppButton(
                        text = stringResource(Res.string.main_empty_cta),
                        onClick = onAddChecklistClick,
                    )
                }
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
            bottom = contentBottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        item(
            key = "my_lists_header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            MyListsHeader(count = screenState.checklists.size)
        }

        items(
            items = screenState.checklists,
            key = { it.checklist.id },
        ) { checklistWithProgress ->
            ChecklistCard(
                checklistWithProgress = checklistWithProgress,
                isEditMode = false,
                isDragging = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChecklistClick(checklistWithProgress) },
            )
        }

        val limits = screenState.userLimits
        if (limits != null && !limits.isPremium) {
            item(
                key = "upgrade_banner",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                PremiumBanner(
                    isActive = false,
                    formattedExpirationDate = null,
                    onUpgradeClick = onPremiumBannerClick,
                    onSubscriptionClick = onPremiumBannerClick,
                )
            }
        }
    }
}

/**
 * Section header for the "My lists" group — uppercase caps label + count, per the
 * Gisti variant-D design (gisti-screens.jsx RxHome "My lists" header).
 */
@Composable
private fun MyListsHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.main_my_lists).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * Home list card — delegates to the design-system [ChecklistListCard], mapping the domain
 * [ChecklistWithProgress] to primitives (the design-system module must not depend on the
 * feature/checklist domain model). The avatar tint + monogram are derived from the
 * checklist id (the model has no emoji/color field), so this is a pure UI concern.
 *
 * Tap / long-press are passed via [onClick]/[onLongClick] and handled INSIDE the card (ripple
 * clipped to the rounded corners). Drag/wiggle modifiers (graphicsLayer, longPressDraggableHandle)
 * stay on [modifier] so the elevation shadow + drag transform are not clipped. In edit mode the
 * caller passes onClick = null (the drag handle owns the gesture). [isEditMode] / [isDragging]
 * remain in the signature for call-site compatibility; they are no longer read here.
 */
@Composable
private fun ChecklistCard(
    checklistWithProgress: ChecklistWithProgress,
    @Suppress("UNUSED_PARAMETER") isEditMode: Boolean,
    @Suppress("UNUSED_PARAMETER") isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    ChecklistListCard(
        name = checklistWithProgress.checklist.name,
        checkedItems = checklistWithProgress.checkedItems,
        totalItems = checklistWithProgress.totalItems,
        seed = checklistWithProgress.checklist.id,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}
