package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TimePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly.MoveToDayBottomSheet
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly.WeeklyChecklistDetailContent
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.formatReminderDateTime
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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

    // Detect return from exact alarm settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.handleReturnedFromSettings()
    }

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
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar message from ViewModel (exact alarm permission result)
    val exactGrantedMessage = stringResource(Res.string.reminder_exact_alarm_granted)
    val exactDeniedMessage = stringResource(Res.string.reminder_exact_alarm_denied)
    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        val text = when (message) {
            ChecklistDetailViewModel.SNACKBAR_EXACT_GRANTED -> exactGrantedMessage
            ChecklistDetailViewModel.SNACKBAR_EXACT_DENIED -> exactDeniedMessage
            else -> message
        }
        snackbarHostState.showSnackbar(text)
        onIntent(ChecklistDetailIntent.OnSnackbarDismissed)
    }

    // Undo snackbar for swipe-to-delete
    val undoLabel = stringResource(Res.string.undo)
    LaunchedEffect(state.pendingUndoItem) {
        val undo = state.pendingUndoItem ?: return@LaunchedEffect
        val deletedMessage = getString(Res.string.checklist_item_deleted, undo.fillItem.text)
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onIntent(ChecklistDetailIntent.OnUndoDeleteItem)
        }
    }

    var addItemActive by remember { mutableStateOf(false) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

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

    AppScaffold(
        onBackButtonClick = {
            if (isEditMode) {
                isEditMode = false
            } else {
                onIntent(ChecklistDetailIntent.OnBackClick)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        actions = {
            if (isEditMode) {
                // Edit mode: only "Done" button
                TextButton(onClick = { isEditMode = false }) {
                    Text(
                        text = stringResource(Res.string.done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Normal mode: regular toolbar actions
                IconButton(
                    onClick = {
                        if (addItemActive) {
                            onIntent(ChecklistDetailIntent.OnQuickAddCancelled(hadText = false))
                            addItemActive = false
                        } else {
                            addItemActive = true
                            onIntent(ChecklistDetailIntent.OnQuickAddOpened)
                            coroutineScope.launch {
                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(Res.string.add_item)
                    )
                }
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnReminderClick) }) {
                    val hasActiveSchedule = state.checklist.reminderAt != null
                            || state.checklist.repeatRule != null
                    Icon(
                        imageVector = if (hasActiveSchedule)
                            Icons.Filled.Notifications
                        else
                            Icons.Outlined.Notifications,
                        contentDescription = stringResource(Res.string.reminder_set_reminder),
                        tint = if (hasActiveSchedule)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnShareClick) }) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(Res.string.share))
                }
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnEditChecklistClick) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.checklist_edit))
                }
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnOverflowMenuClick) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.more_options)
                    )
                }
            }
        },
        bottomBar = {
            // Hide bottom bar in edit mode
            if (!isEditMode) {
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
            when (state.checklist.viewMode) {
                ChecklistViewMode.Weekly -> {
                    val todayWeekday = remember {
                        Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber
                    }
                    WeeklyChecklistDetailContent(
                        state = state,
                        todayWeekday = todayWeekday,
                        onIntent = onIntent,
                        onAddItemToDay = { weekday, text -> onIntent(ChecklistDetailIntent.OnAddItemToDay(weekday, text)) },
                        onItemCheckedChange = { itemId, checked -> onIntent(ChecklistDetailIntent.OnItemCheckedChange(itemId, checked)) },
                        onItemLongPress = { itemId -> onIntent(ChecklistDetailIntent.OnItemLongPressForMove(itemId)) },
                        onItemTap = { itemId -> onIntent(ChecklistDetailIntent.OnItemTapForDetails(itemId)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // MoveToDayBottomSheet — sibling to WeeklyChecklistDetailContent, not inside it
                    state.moveToDayItemId?.let { itemId ->
                        val fillItem = state.defaultFill?.items?.firstOrNull { it.id == itemId }
                        MoveToDayBottomSheet(
                            currentWeekday = fillItem?.weekday,
                            todayWeekday = todayWeekday,
                            onDaySelected = { weekday -> onIntent(ChecklistDetailIntent.OnMoveItemToDay(itemId, weekday)) },
                            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissMoveToDaySheet) },
                        )
                    }
                }
                ChecklistViewMode.Standard -> {
            val sourceUnchecked = remember(defaultFill.items, state.separateCompleted) {
                if (state.separateCompleted) defaultFill.items.filter { !it.checked }
                else defaultFill.items
            }
            val completedItems by remember(defaultFill.items, state.separateCompleted) {
                derivedStateOf {
                    if (state.separateCompleted) defaultFill.items.filter { it.checked }
                    else emptyList()
                }
            }
            var completedExpanded by remember { mutableStateOf(true) }

            // Local mutable list for optimistic reorder (no DB writes during drag)
            var localUnchecked by remember(sourceUnchecked) {
                mutableStateOf(sourceUnchecked)
            }

            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                // Offset: title(0) + progress(1) + optional fills card(2) = 2 or 3 header items
                val headerCount = 2 + (if (state.additionalFillsCount > 0) 1 else 0)
                val fromIndex = from.index - headerCount
                val toIndex = to.index - headerCount
                if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localUnchecked.size && toIndex < localUnchecked.size) {
                    localUnchecked = localUnchecked.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                item {
                    Text(
                        text = state.checklist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = AppDimens.SpacingMd)
                    )
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

                // Unchecked items (or all items when separateCompleted is off)
                items(
                    count = localUnchecked.size,
                    key = { localUnchecked[it].id }
                ) { index ->
                    val item = localUnchecked[index]
                    ReorderableItem(
                        state = reorderableState,
                        key = item.id,
                        enabled = isEditMode
                    ) { isDragging ->
                        SwipeableChecklistItemCard(
                            isEditMode = isEditMode,
                            onSwipeDelete = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onIntent(ChecklistDetailIntent.OnSwipeDeleteItem(item.id))
                            },
                        ) {
                            ChecklistItemCard(
                                item = item,
                                isDragging = isDragging,
                                isEditMode = isEditMode,
                                wiggleAngle = wiggleAngle,
                                onCheckedChange = { checked ->
                                    onIntent(ChecklistDetailIntent.OnItemCheckedChange(item.id, checked))
                                },
                                onItemTap = { onIntent(ChecklistDetailIntent.OnItemTapForDetails(item.id)) },
                                onLongClick = {
                                    if (!isEditMode) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isEditMode = true
                                    }
                                },
                                cardDragModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onIntent(ChecklistDetailIntent.OnFinalizeReorder(localUnchecked.map { it.id }))
                                    }
                                )
                            )
                        }
                    }
                }

                // Inline add item input (shown when toolbar "+" is tapped)
                if (addItemActive && !isEditMode) {
                    item(key = "inline_add_item") {
                        InlineAddItemInput(
                            onAddItem = { onIntent(ChecklistDetailIntent.OnAddItem(it)) },
                            onClose = { hadText ->
                                onIntent(ChecklistDetailIntent.OnQuickAddCancelled(hadText = hadText))
                                addItemActive = false
                            }
                        )
                    }
                }

                // Completed section (only when separateCompleted is on and there are completed items)
                if (state.separateCompleted && completedItems.isNotEmpty()) {
                    item(key = "completed_header") {
                        CompletedSectionHeader(
                            completedCount = completedItems.size,
                            expanded = completedExpanded,
                            onToggle = {
                                val newExpanded = !completedExpanded
                                completedExpanded = newExpanded
                                onIntent(ChecklistDetailIntent.OnCompletedSectionToggle(
                                    expanded = newExpanded,
                                    completedCount = completedItems.size
                                ))
                            }
                        )
                    }

                    if (completedExpanded) {
                        items(
                            count = completedItems.size,
                            key = { completedItems[it].id }
                        ) { index ->
                            val item = completedItems[index]
                            SwipeableChecklistItemCard(
                                isEditMode = isEditMode,
                                onSwipeDelete = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onIntent(ChecklistDetailIntent.OnSwipeDeleteItem(item.id))
                                },
                            ) {
                                ChecklistItemCard(
                                    item = item,
                                    isDragging = false,
                                    isEditMode = isEditMode,
                                    wiggleAngle = wiggleAngle,
                                    onCheckedChange = { checked ->
                                        onIntent(ChecklistDetailIntent.OnItemCheckedChange(item.id, checked))
                                    },
                                    onItemTap = { onIntent(ChecklistDetailIntent.OnItemTapForDetails(item.id)) },
                                    onLongClick = {
                                        if (!isEditMode) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isEditMode = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Bottom spacing for the fixed buttons
                item {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                }
            }
                } // end Standard ->
            } // end when (state.checklist.viewMode)
        }
    }

    // Item details sheet — opens when user taps the right 70% of a ChecklistItemCard
    val detailsItem = state.itemDetailsSheetFor?.let { id ->
        state.defaultFill?.items?.firstOrNull { it.id == id }
    }
    if (detailsItem != null) {
        ItemDetailsSheet(
            item = detailsItem,
            onReminderClick = { onIntent(ChecklistDetailIntent.OnItemReminderClick(detailsItem.id)) },
            onNoteClick = { onIntent(ChecklistDetailIntent.OnAddNoteClick(detailsItem.id)) },
            onDelete = { onIntent(ChecklistDetailIntent.OnDeleteItemFromSheet(detailsItem.id)) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissItemDetailsSheet) },
        )
    }

    // Note dialog
    if (state.noteDialogItemId != null) {
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

    // Fill target bottom sheet
    if (state.showFillTargetSheet) {
        FillTargetBottomSheet(
            onFillMainChecklist = { onIntent(ChecklistDetailIntent.OnFillMainChecklistSelected) },
            onCreateNewFill = { onIntent(ChecklistDetailIntent.OnCreateNewFillSelected) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnFillTargetSheetDismiss) }
        )
    }

    // Notification permission bottom sheet
    if (state.showNotificationPermissionSheet) {
        val requestPermission = rememberNotificationPermissionRequester { granted ->
            onIntent(ChecklistDetailIntent.OnNotificationPermissionResult(granted))
        }
        NotificationPermissionSheet(
            onEnableClick = requestPermission,
            onSkip = { onIntent(ChecklistDetailIntent.OnNotificationPermissionSkip) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissNotificationPermissionSheet) }
        )
    }

    // Unified reminder / repeat bottom sheet with tabs
    if (state.showReminderSheet) {
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = state.activeReminderTab,
                currentReminder = state.checklist.reminderAt,
                currentRepeatRule = state.checklist.repeatRule,
                repeatRuleSummary = state.repeatRuleSummary,
                pendingRepeatConfig = state.pendingRepeatConfig,
                showEndConditionPicker = state.showEndConditionPicker
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(ChecklistDetailIntent.OnReminderTabSelected(it)) },
                onPresetSelected = { onIntent(ChecklistDetailIntent.OnReminderPresetSelected(it)) },
                onCustomDateRequested = { onIntent(ChecklistDetailIntent.OnCustomDateRequested) },
                onRemoveReminder = { onIntent(ChecklistDetailIntent.OnRemoveReminder) },
                onRepeatTypeSelected = { onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(ChecklistDetailIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(ChecklistDetailIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(ChecklistDetailIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(ChecklistDetailIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(ChecklistDetailIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(ChecklistDetailIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(ChecklistDetailIntent.OnDismissEndConditionPicker) },
                onSaveRepeat = { onIntent(ChecklistDetailIntent.OnSaveRepeatSchedule) },
                onRemoveRepeat = { onIntent(ChecklistDetailIntent.OnRemoveRepeatSchedule) },
                onDismiss = { onIntent(ChecklistDetailIntent.OnDismissReminderUI) }
            )
        )
    }

    // Per-item reminder sheet (reuses ReminderSheet with item-scoped callbacks)
    val itemReminderItem = state.itemReminderSheetFor?.let { id ->
        state.defaultFill?.items?.firstOrNull { it.id == id }
    }
    if (itemReminderItem != null) {
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = state.activeItemReminderTab,
                currentReminder = itemReminderItem.reminderAt,
                currentRepeatRule = itemReminderItem.repeatRule,
                // Intentionally no fallback to raw enum name (e.g. "DAILY"):
                // prefer hiding CurrentRepeatCard entirely over showing meaningless
                // text. The card has a `summary != null` guard.
                repeatRuleSummary = state.repeatRuleSummary,
                pendingRepeatConfig = state.pendingRepeatConfig,
                showEndConditionPicker = state.showEndConditionPicker
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(ChecklistDetailIntent.OnItemReminderTabSelected(it)) },
                onPresetSelected = { triggerAt ->
                    onIntent(ChecklistDetailIntent.OnSaveItemReminder(
                        itemReminderItem.id, triggerAt, null, null
                    ))
                },
                onCustomDateRequested = { onIntent(ChecklistDetailIntent.OnCustomDateRequested) },
                onRemoveReminder = { onIntent(ChecklistDetailIntent.OnRemoveItemReminder(itemReminderItem.id)) },
                onRepeatTypeSelected = { onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(ChecklistDetailIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(ChecklistDetailIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(ChecklistDetailIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(ChecklistDetailIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(ChecklistDetailIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(ChecklistDetailIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(ChecklistDetailIntent.OnDismissEndConditionPicker) },
                onSaveRepeat = {
                    val config = state.pendingRepeatConfig
                    if (config != null) {
                        val rule = config.toRule()
                        val timeMinutes = config.timeHour * 60 + config.timeMinute
                        onIntent(ChecklistDetailIntent.OnSaveItemReminder(
                            itemReminderItem.id, null, rule, timeMinutes
                        ))
                    }
                },
                onRemoveRepeat = { onIntent(ChecklistDetailIntent.OnRemoveItemReminder(itemReminderItem.id)) },
                onDismiss = { onIntent(ChecklistDetailIntent.OnDismissItemReminderSheet) }
            )
        )
    }

    // Custom date/time picker
    if (state.showCustomPicker) {
        ReminderDateTimePicker(
            selectedDateMillis = state.customPickerDateMillis,
            minDateMillis = state.customPickerMinDateMillis,
            initialHour = state.customPickerInitialHour,
            isTimeInPast = state.isCustomTimeInPast,
            onDateSelected = { onIntent(ChecklistDetailIntent.OnDateSelected(it)) },
            onTimeChanged = { hour, minute -> onIntent(ChecklistDetailIntent.OnCustomTimeChanged(hour, minute)) },
            onTimeSelected = { hour, minute -> onIntent(ChecklistDetailIntent.OnTimeSelected(hour, minute)) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissReminderUI) }
        )
    }

    // Exact alarm permission instruction sheet
    if (state.showExactAlarmSheet) {
        ExactAlarmInstructionSheet(
            dontShowAgain = state.exactAlarmDontShowAgain,
            onDontShowAgainChanged = { onIntent(ChecklistDetailIntent.OnExactAlarmDontShowChanged(it)) },
            onOpenSettings = { onIntent(ChecklistDetailIntent.OnExactAlarmOpenSettings) },
            onSkip = { onIntent(ChecklistDetailIntent.OnExactAlarmSkip) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissExactAlarmSheet) }
        )
    }

    // Overflow menu bottom sheet
    if (state.showOverflowSheet) {
        OverflowMenuSheet(
            separateCompleted = state.separateCompleted,
            autoDeleteCompleted = state.autoDeleteCompleted,
            hasCompletedItems = state.defaultFill?.items?.any { it.checked } == true,
            onDeleteCompletedItems = { onIntent(ChecklistDetailIntent.OnDeleteCompletedItems) },
            onToggleAutoDeleteCompleted = { onIntent(ChecklistDetailIntent.OnToggleAutoDeleteCompleted) },
            onToggleSeparateCompleted = { onIntent(ChecklistDetailIntent.OnToggleSeparateCompleted) },
            onDeleteClick = {
                onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
                onIntent(ChecklistDetailIntent.OnDeleteChecklistClick)
            },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissOverflowSheet) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableChecklistItemCard(
    isEditMode: Boolean,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (isEditMode) {
        Box(modifier = modifier) { content() }
        return
    }

    val dismissState = rememberSwipeToDismissBoxState()

    // Observe dismiss: delete item and immediately reset state so re-swipe works after undo
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onSwipeDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeDeleteBackground() },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
    ) {
        content()
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = AppDimens.SpacingXl),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(Res.string.delete_item),
            tint = MaterialTheme.colorScheme.onError,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistItemCard(
    item: ChecklistFillItem,
    isDragging: Boolean,
    isEditMode: Boolean,
    wiggleAngle: Float,
    onCheckedChange: (Boolean) -> Unit,
    onItemTap: () -> Unit,
    onLongClick: () -> Unit,
    cardDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current

    val cardModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            if (isEditMode && !isDragging) {
                rotationZ = wiggleAngle
            }
        }
        .then(if (isEditMode) cardDragModifier else Modifier)

    val cardContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            // ── Visual layer: Checkbox + Text at original positions ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(
                        horizontal = AppDimens.SpacingMd,
                        vertical = AppDimens.SpacingSm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isEditMode) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = null, // tap overlay handles it
                        modifier = Modifier.padding(end = AppDimens.SpacingSm)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
                ) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.checked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.fillMaxWidth().padding(
                            start = if (isEditMode) AppDimens.SpacingSm else 0.dp
                        )
                    )

                    if (!isEditMode) {
                        item.note?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (!isEditMode && item.hasActiveReminder) {
                        val reminderMissed = isReminderMissed(item)
                        val reminderLabel = formatItemReminderLabel(item)
                        val chipColor = if (reminderMissed)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = chipColor
                            )
                            Text(
                                text = reminderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor
                            )
                        }
                    }
                }
            }

            // ── Tap overlay: invisible 30/70 split (above visual layer) ──
            // No ripple — clicks are silent; user feedback comes from state change
            // (checkbox flip / sheet appearing).
            if (!isEditMode) {
                val checkInteractionSource = remember { MutableInteractionSource() }
                val tapInteractionSource = remember { MutableInteractionSource() }
                Row(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.30f)
                            .fillMaxHeight()
                            .combinedClickable(
                                interactionSource = checkInteractionSource,
                                indication = null,
                                onClick = { onCheckedChange(!item.checked) },
                                onLongClick = onLongClick
                            )
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.70f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = tapInteractionSource,
                                indication = null,
                                onClick = onItemTap
                            )
                    )
                }
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
            modifier = cardModifier,
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
            modifier = cardModifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) { cardContent() }
    }
}

/**
 * Things-style detail sheet for a checklist item.
 *
 * Shows item name + three action rows: Reminder, Note, Delete.
 * Wiring (close + open target) is handled in Phase B (android-expert).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemDetailsSheet(
    item: ChecklistFillItem,
    onReminderClick: () -> Unit,
    onNoteClick: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl)
        ) {
            // Item name as sheet title
            Text(
                text = item.text,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppDimens.SpacingMd)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Reminder row ──
            val hasReminder = item.hasActiveReminder
            val reminderMissed = hasReminder && isReminderMissed(item)
            val reminderTitle = stringResource(Res.string.detail_item_sheet_action_reminder)
            val reminderSubtitle = when {
                hasReminder -> formatItemReminderLabel(item)
                else -> stringResource(Res.string.detail_item_sheet_subtitle_no_reminder)
            }
            val reminderIconTint = when {
                reminderMissed -> MaterialTheme.colorScheme.error
                hasReminder -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            ItemDetailsSheetRow(
                icon = if (hasReminder) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                iconTint = reminderIconTint,
                title = reminderTitle,
                subtitle = reminderSubtitle,
                showChevron = true,
                onClick = onReminderClick,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Note row ──
            val hasNote = item.note != null
            val noteTitle = stringResource(
                if (hasNote) Res.string.detail_item_sheet_action_edit_note
                else Res.string.detail_item_sheet_action_note
            )
            val noteSubtitle = item.note
                ?: stringResource(Res.string.detail_item_sheet_subtitle_no_note)
            val noteIconTint = if (hasNote)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant

            ItemDetailsSheetRow(
                icon = if (hasNote) Icons.Filled.Note else Icons.Outlined.NoteAdd,
                iconTint = noteIconTint,
                title = noteTitle,
                subtitle = noteSubtitle,
                showChevron = true,
                onClick = onNoteClick,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // ── Delete row ──
            ItemDetailsSheetRow(
                icon = Icons.Outlined.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(Res.string.detail_item_sheet_action_delete),
                subtitle = null,
                showChevron = false,
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ItemDetailsSheetRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    showChevron: Boolean,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Returns true if the item has a one-shot reminder that has already passed (missed).
 * Recurring reminders never count as missed — they always show the next future trigger.
 */
private fun isReminderMissed(item: ChecklistFillItem): Boolean {
    if (item.repeatRule != null) return false
    val at = item.reminderAt ?: return false
    return at < Clock.System.now().toEpochMilliseconds()
}

/**
 * Formats the label shown on the Remind TextButton inside ChecklistItemCard.
 *
 * States:
 * - No reminder   → "Remind"
 * - One-shot, future → "May 5, 18:00"  (formatted via formatReminderDateTime)
 * - One-shot, past   → "Missed (May 5)"
 * - Recurring        → compact rule text, e.g. "Daily 09:00" / "Weekly Mon 18:00"
 */
@Composable
internal fun formatItemReminderLabel(item: ChecklistFillItem): String {
    if (!item.hasActiveReminder) return stringResource(Res.string.detail_item_action_remind)

    val tz = TimeZone.currentSystemDefault()

    // Recurring reminder — show rule + time from repeatTimeOfDayMinutes
    val rule = item.repeatRule
    if (rule != null) {
        val minutes = item.repeatTimeOfDayMinutes ?: 0
        val h = (minutes / 60).toString().padStart(2, '0')
        val m = (minutes % 60).toString().padStart(2, '0')
        val ruleText = when (rule.type) {
            RepeatType.DAILY -> "Daily $h:$m"
            RepeatType.WEEKLY -> {
                val days = rule.weekDays
                if (days.isNullOrEmpty()) "Weekly $h:$m"
                else {
                    val dayNames = days.sorted().joinToString(",") { iso ->
                        when (iso) {
                            1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"
                            5 -> "Fri"; 6 -> "Sat"; else -> "Sun"
                        }
                    }
                    "Weekly $dayNames $h:$m"
                }
            }
            RepeatType.MONTHLY -> "Monthly $h:$m"
            RepeatType.YEARLY -> "Yearly $h:$m"
        }
        return ruleText
    }

    // One-shot reminder
    val at = item.reminderAt ?: return stringResource(Res.string.detail_item_action_remind)
    val localDt = Instant.fromEpochMilliseconds(at).toLocalDateTime(tz)
    val formatted = formatReminderDateTime(localDt)

    return if (isReminderMissed(item)) {
        // Show date only for missed (no time clutter)
        val month = localDt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "Missed ($month ${localDt.dayOfMonth})"
    } else {
        formatted
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
                placeholder = stringResource(Res.string.fill_note_placeholder),
                singleLine = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FillTargetBottomSheet(
    onFillMainChecklist: () -> Unit,
    onCreateNewFill: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Text(
                text = stringResource(Res.string.fill_target_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
            )

            AppCard(onClick = onFillMainChecklist) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.fill_target_main),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(Res.string.fill_target_main_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AppCard(onClick = onCreateNewFill) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.fill_target_new),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(Res.string.fill_target_new_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPermissionSheet(
    onEnableClick: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bell icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Title
            Text(
                text = stringResource(Res.string.reminder_notification_permission_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Description
            Text(
                text = stringResource(Res.string.reminder_notification_permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Feature list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
            ) {
                NotificationFeatureRow(
                    icon = Icons.Outlined.Schedule,
                    text = stringResource(Res.string.reminder_notification_permission_feature1)
                )
                NotificationFeatureRow(
                    icon = Icons.Outlined.Notifications,
                    text = stringResource(Res.string.reminder_notification_permission_feature2)
                )
                NotificationFeatureRow(
                    icon = Icons.Outlined.AutoAwesome,
                    text = stringResource(Res.string.reminder_notification_permission_feature3)
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Buttons
            AppButton(
                text = stringResource(Res.string.reminder_notification_permission_enable),
                onClick = onEnableClick,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            AppButtonText(
                text = stringResource(Res.string.reminder_notification_permission_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NotificationFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactAlarmInstructionSheet(
    dontShowAgain: Boolean,
    onDontShowAgainChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(Res.string.reminder_exact_alarm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

            // Steps
            StepRow(number = 1, text = stringResource(Res.string.reminder_exact_alarm_step1))
            StepRow(number = 2, text = stringResource(Res.string.reminder_exact_alarm_step2))
            StepRow(number = 3, text = stringResource(Res.string.reminder_exact_alarm_step3))

            // Description
            Text(
                text = stringResource(Res.string.reminder_exact_alarm_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.SpacingXs)
            )

            // Don't show again checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = dontShowAgain,
                    onCheckedChange = onDontShowAgainChanged
                )
                Text(
                    text = stringResource(Res.string.reminder_exact_alarm_dont_show),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Buttons
            AppButton(
                text = stringResource(Res.string.reminder_exact_alarm_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
            AppButtonText(
                text = stringResource(Res.string.reminder_exact_alarm_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenuSheet(
    separateCompleted: Boolean,
    autoDeleteCompleted: Boolean,
    hasCompletedItems: Boolean,
    onDeleteCompletedItems: () -> Unit,
    onToggleAutoDeleteCompleted: () -> Unit,
    onToggleSeparateCompleted: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl)
        ) {
            // Delete completed items button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasCompletedItems) Modifier.clickable { onDeleteCompletedItems() }
                        else Modifier
                    )
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistRemove,
                    contentDescription = null,
                    tint = if (hasCompletedItems) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.delete_completed_items),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasCompletedItems) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            HorizontalDivider()

            // Auto-delete completed toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleAutoDeleteCompleted() }
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.RemoveDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.auto_delete_completed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                AppSwitch(
                    checked = autoDeleteCompleted,
                    onCheckedChange = null
                )
            }

            HorizontalDivider()

            // Separate completed toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSeparateCompleted() }
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.separate_completed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                AppSwitch(
                    checked = separateCompleted,
                    onCheckedChange = null
                )
            }

            HorizontalDivider()

            // Delete checklist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteClick() }
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.delete_checklist),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CompletedSectionHeader(
    completedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.completed_count, completedCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) {
                stringResource(Res.string.collapse)
            } else {
                stringResource(Res.string.expand)
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InlineAddItemInput(
    onAddItem: (String) -> Unit,
    onClose: (hadText: Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var wasKeyboardVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Track keyboard visibility: when keyboard hides via system back, close input
    LaunchedEffect(imeBottom) {
        val isKeyboardVisible = imeBottom > 0
        if (wasKeyboardVisible && !isKeyboardVisible) {
            focusManager.clearFocus()
            onClose(text.isNotBlank())
        }
        wasKeyboardVisible = isKeyboardVisible
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(Res.string.add_item_placeholder),
            singleLine = false,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) {
                        onAddItem(text)
                        text = ""
                    }
                }
            )
        )
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAddItem(text)
                    text = ""
                } else {
                    onClose(false)
                }
            }
        ) {
            Icon(
                imageVector = if (text.isNotBlank()) Icons.Outlined.Check else Icons.Outlined.Add,
                contentDescription = stringResource(Res.string.add_item)
            )
        }
    }
}
