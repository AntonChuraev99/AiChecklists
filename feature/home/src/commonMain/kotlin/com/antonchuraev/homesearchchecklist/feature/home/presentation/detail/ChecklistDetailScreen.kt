package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
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
        val result = snackbarHostState.showSnackbar(
            message = "\"${undo.fillItem.text}\" deleted",
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
                                onNoteClick = { onIntent(ChecklistDetailIntent.OnAddNoteClick(item.id)) },
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
                                    onNoteClick = { onIntent(ChecklistDetailIntent.OnAddNoteClick(item.id)) },
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
        }
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
            activeTab = state.activeReminderTab,
            currentReminder = state.checklist.reminderAt,
            currentRepeatRule = state.checklist.repeatRule,
            repeatRuleSummary = state.repeatRuleSummary,
            pendingRepeatConfig = state.pendingRepeatConfig,
            showEndConditionPicker = state.showEndConditionPicker,
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
private fun ChecklistItemCard(
    item: ChecklistFillItem,
    isDragging: Boolean,
    isEditMode: Boolean,
    wiggleAngle: Float,
    onCheckedChange: (Boolean) -> Unit,
    onNoteClick: () -> Unit,
    onLongClick: () -> Unit,
    cardDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        if (isDragging) 8.dp else AppDimens.CardElevation
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (isEditMode && !isDragging) {
                    rotationZ = wiggleAngle
                }
            }
            .then(
                if (isEditMode) {
                    cardDragModifier
                } else {
                    Modifier.combinedClickable(
                        onClick = { onCheckedChange(!item.checked) },
                        onLongClick = onLongClick
                    )
                }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppDimens.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isEditMode) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = onCheckedChange
                    )
                }

                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f).padding(
                        start = if (isEditMode) AppDimens.SpacingSm else 0.dp
                    )
                )

                if (!isEditMode) {
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
            }

            if (!isEditMode) {
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
private fun ReminderSheet(
    activeTab: ReminderTab,
    currentReminder: Long?,
    currentRepeatRule: ReminderRepeatRule?,
    repeatRuleSummary: String?,
    pendingRepeatConfig: PendingRepeatConfig?,
    showEndConditionPicker: Boolean,
    onTabSelected: (ReminderTab) -> Unit,
    onPresetSelected: (Long) -> Unit,
    onCustomDateRequested: () -> Unit,
    onRemoveReminder: () -> Unit,
    onRepeatTypeSelected: (RepeatType) -> Unit,
    onSmartPresetSelected: (PendingRepeatConfig) -> Unit,
    onRepeatIntervalChanged: (Int) -> Unit,
    onWeekDayToggled: (Int) -> Unit,
    onResetChecksToggled: (Boolean) -> Unit,
    onRepeatTimeChanged: (Int, Int) -> Unit,
    onEndConditionClick: () -> Unit,
    onEndConditionSelected: (RepeatEndCondition) -> Unit,
    onDismissEndCondition: () -> Unit,
    onSaveRepeat: () -> Unit,
    onRemoveRepeat: () -> Unit,
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
        ) {
            // Tab row
            PrimaryTabRow(
                selectedTabIndex = if (activeTab == ReminderTab.ONCE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = activeTab == ReminderTab.ONCE,
                    onClick = { onTabSelected(ReminderTab.ONCE) },
                    text = { Text(stringResource(Res.string.reminder_tab_once)) }
                )
                Tab(
                    selected = activeTab == ReminderTab.REPEAT,
                    onClick = { onTabSelected(ReminderTab.REPEAT) },
                    text = { Text(stringResource(Res.string.reminder_tab_repeat)) }
                )
            }

            // Tab content
            when (activeTab) {
                ReminderTab.ONCE -> OnceTabContent(
                    currentReminder = currentReminder,
                    onPresetSelected = onPresetSelected,
                    onCustomDateRequested = onCustomDateRequested,
                    onRemoveReminder = onRemoveReminder
                )
                ReminderTab.REPEAT -> RepeatTabContent(
                    config = pendingRepeatConfig ?: PendingRepeatConfig(),
                    currentRepeatRule = currentRepeatRule,
                    repeatRuleSummary = repeatRuleSummary,
                    showEndConditionPicker = showEndConditionPicker,
                    onTypeSelected = onRepeatTypeSelected,
                    onSmartPresetSelected = onSmartPresetSelected,
                    onIntervalChanged = onRepeatIntervalChanged,
                    onWeekDayToggled = onWeekDayToggled,
                    onResetChecksToggled = onResetChecksToggled,
                    onTimeChanged = onRepeatTimeChanged,
                    onEndConditionClick = onEndConditionClick,
                    onEndConditionSelected = onEndConditionSelected,
                    onDismissEndCondition = onDismissEndCondition,
                    onSave = onSaveRepeat,
                    onRemove = onRemoveRepeat
                )
            }
        }
    }
}

@Composable
private fun OnceTabContent(
    currentReminder: Long?,
    onPresetSelected: (Long) -> Unit,
    onCustomDateRequested: () -> Unit,
    onRemoveReminder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXxl),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
    ) {
        if (currentReminder != null) {
            CurrentReminderCard(reminderAtMillis = currentReminder)
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        }

        ReminderPresetRow(
            icon = Icons.Outlined.Schedule,
            text = stringResource(Res.string.reminder_in_one_hour),
            onClick = {
                onPresetSelected(Clock.System.now().plus(1, DateTimeUnit.HOUR).toEpochMilliseconds())
            }
        )
        ReminderPresetRow(
            icon = Icons.Outlined.WbSunny,
            text = stringResource(Res.string.reminder_tomorrow_morning),
            onClick = { onPresetSelected(tomorrowAt(hour = 9, minute = 0)) }
        )
        ReminderPresetRow(
            icon = Icons.Outlined.WbTwilight,
            text = stringResource(Res.string.reminder_tomorrow_evening),
            onClick = { onPresetSelected(tomorrowAt(hour = 18, minute = 0)) }
        )
        ReminderPresetRow(
            icon = Icons.Outlined.CalendarMonth,
            text = stringResource(Res.string.reminder_pick_date_time),
            onClick = onCustomDateRequested
        )

        if (currentReminder != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))
            AppButtonText(
                text = stringResource(Res.string.reminder_remove),
                onClick = onRemoveReminder
            )
        }
    }
}

@Composable
private fun RepeatTabContent(
    config: PendingRepeatConfig,
    currentRepeatRule: ReminderRepeatRule?,
    repeatRuleSummary: String?,
    showEndConditionPicker: Boolean,
    onTypeSelected: (RepeatType) -> Unit,
    onSmartPresetSelected: (PendingRepeatConfig) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onWeekDayToggled: (Int) -> Unit,
    onResetChecksToggled: (Boolean) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    onEndConditionClick: () -> Unit,
    onEndConditionSelected: (RepeatEndCondition) -> Unit,
    onDismissEndCondition: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXxl),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        // Current repeat schedule card
        if (currentRepeatRule != null && repeatRuleSummary != null) {
            CurrentRepeatCard(
                summary = repeatRuleSummary,
                timeHour = config.timeHour,
                timeMinute = config.timeMinute
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        }

        // Custom state for toggling custom repeat section
        var customExpanded by rememberSaveable { mutableStateOf(config.isCustom) }
        val isCustomActive = config.isCustom || customExpanded

        // Preset type options
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_daily),
            selected = !isCustomActive && config.type == RepeatType.DAILY && config.interval == 1,
            onClick = { customExpanded = false; onTypeSelected(RepeatType.DAILY) }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_weekdays),
            selected = !isCustomActive && config.type == RepeatType.WEEKLY && config.interval == 1
                    && config.weekDays == setOf(1, 2, 3, 4, 5),
            onClick = {
                customExpanded = false
                onSmartPresetSelected(PendingRepeatConfig(
                    type = RepeatType.WEEKLY, interval = 1,
                    weekDays = setOf(1, 2, 3, 4, 5),
                    timeHour = config.timeHour, timeMinute = config.timeMinute
                ))
            }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_weekly),
            selected = !isCustomActive && config.type == RepeatType.WEEKLY && config.interval == 1 && config.weekDays.isEmpty(),
            onClick = { customExpanded = false; onTypeSelected(RepeatType.WEEKLY) }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_biweekly),
            selected = !isCustomActive && config.type == RepeatType.WEEKLY && config.interval == 2 && config.weekDays.isEmpty(),
            onClick = {
                customExpanded = false
                onSmartPresetSelected(PendingRepeatConfig(
                    type = RepeatType.WEEKLY, interval = 2,
                    timeHour = config.timeHour, timeMinute = config.timeMinute
                ))
            }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_monthly),
            selected = !isCustomActive && config.type == RepeatType.MONTHLY && config.interval == 1,
            onClick = { customExpanded = false; onTypeSelected(RepeatType.MONTHLY) }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_quarterly),
            selected = !isCustomActive && config.type == RepeatType.MONTHLY && config.interval == 3,
            onClick = {
                customExpanded = false
                onSmartPresetSelected(PendingRepeatConfig(
                    type = RepeatType.MONTHLY, interval = 3,
                    timeHour = config.timeHour, timeMinute = config.timeMinute
                ))
            }
        )
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_yearly),
            selected = !isCustomActive && config.type == RepeatType.YEARLY && config.interval == 1,
            onClick = { customExpanded = false; onTypeSelected(RepeatType.YEARLY) }
        )

        // Custom option
        RepeatTypeOption(
            text = stringResource(Res.string.reminder_repeat_custom),
            selected = isCustomActive,
            onClick = { customExpanded = !customExpanded }
        )

        // Custom interval section
        AnimatedVisibility(visible = isCustomActive) {
            CustomRepeatSection(
                config = config,
                onTypeChanged = onTypeSelected,
                onIntervalChanged = onIntervalChanged,
                onWeekDayToggled = onWeekDayToggled
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))

        // Time of day picker
        RepeatTimePicker(
            hour = config.timeHour,
            minute = config.timeMinute,
            onTimeChanged = onTimeChanged
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))

        // Reset checkboxes toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onResetChecksToggled(!config.resetChecks) }
                .padding(vertical = AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.reminder_reset_checks),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(Res.string.reminder_reset_checks_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppSwitch(
                checked = config.resetChecks,
                onCheckedChange = null
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpacingSm))

        // End condition row
        val endConditionText = when (config.endCondition) {
            is RepeatEndCondition.Never -> stringResource(Res.string.reminder_ends_never)
            is RepeatEndCondition.UntilDate -> {
                val dt = Instant.fromEpochMilliseconds(config.endCondition.dateMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                stringResource(Res.string.reminder_ends_on_date) + " ${formatReminderDateTime(dt)}"
            }
            is RepeatEndCondition.AfterCount -> "${config.endCondition.maxCount} ${stringResource(Res.string.reminder_ends_occurrences)}"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onEndConditionClick)
                .padding(vertical = AppDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.reminder_ends),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = endConditionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        // Save button
        AppButton(
            text = stringResource(Res.string.reminder_repeat_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        )

        // Remove repeat button (only if an active repeat schedule exists)
        if (currentRepeatRule != null) {
            AppButtonText(
                text = stringResource(Res.string.reminder_remove_repeat),
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // End condition picker dialog
    if (showEndConditionPicker) {
        EndConditionDialog(
            currentCondition = config.endCondition,
            onConditionSelected = onEndConditionSelected,
            onDismiss = onDismissEndCondition
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatTimePicker(
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showTimePicker = true }
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(Res.string.reminder_repeat_time_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(Res.string.reminder_select_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                AppButtonText(
                    text = stringResource(Res.string.ok),
                    onClick = {
                        onTimeChanged(timeState.hour, timeState.minute)
                        showTimePicker = false
                    }
                )
            },
            dismissButton = {
                AppButtonText(
                    text = stringResource(Res.string.cancel),
                    onClick = { showTimePicker = false }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
private fun ReminderPresetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CurrentReminderCard(reminderAtMillis: Long) {
    val tz = TimeZone.currentSystemDefault()
    val reminderDateTime = Instant.fromEpochMilliseconds(reminderAtMillis)
        .toLocalDateTime(tz)
    val formattedDate = formatReminderDateTime(reminderDateTime)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CurrentRepeatCard(summary: String, timeHour: Int, timeMinute: Int) {
    val timeFormatted = "${timeHour.toString().padStart(2, '0')}:${timeMinute.toString().padStart(2, '0')}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            Icon(
                imageVector = Icons.Outlined.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(Res.string.reminder_repeat_active, summary, timeFormatted),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RepeatTypeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = AppDimens.SpacingSm, horizontal = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomRepeatSection(
    config: PendingRepeatConfig,
    onTypeChanged: (RepeatType) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onWeekDayToggled: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            // "Every [N]" row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                Text(
                    text = stringResource(Res.string.reminder_repeat_every),
                    style = MaterialTheme.typography.bodyLarge
                )

                // Interval input
                var intervalText by rememberSaveable(config.interval) {
                    mutableStateOf(config.interval.toString())
                }
                AppTextField(
                    value = intervalText,
                    onValueChange = { text ->
                        intervalText = text.filter { it.isDigit() }.take(2)
                        intervalText.toIntOrNull()?.let { onIntervalChanged(it) }
                    },
                    modifier = Modifier.width(56.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    )
                )
            }

            // Type selector chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
            ) {
                val types = listOf(
                    RepeatType.DAILY to stringResource(Res.string.reminder_repeat_days),
                    RepeatType.WEEKLY to stringResource(Res.string.reminder_repeat_weeks),
                    RepeatType.MONTHLY to stringResource(Res.string.reminder_repeat_months),
                    RepeatType.YEARLY to stringResource(Res.string.reminder_repeat_years)
                )
                types.forEach { (type, label) ->
                    val selected = config.type == type
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTypeChanged(type) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = AppDimens.SpacingMd,
                                vertical = AppDimens.SpacingXs
                            )
                        )
                    }
                }
            }

            // Weekday chips (only for WEEKLY type)
            if (config.type == RepeatType.WEEKLY) {
                Text(
                    text = stringResource(Res.string.reminder_repeat_on),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val dayLabels = listOf(
                    1 to stringResource(Res.string.reminder_weekday_mon),
                    2 to stringResource(Res.string.reminder_weekday_tue),
                    3 to stringResource(Res.string.reminder_weekday_wed),
                    4 to stringResource(Res.string.reminder_weekday_thu),
                    5 to stringResource(Res.string.reminder_weekday_fri),
                    6 to stringResource(Res.string.reminder_weekday_sat),
                    7 to stringResource(Res.string.reminder_weekday_sun)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dayLabels.forEach { (dayNumber, label) ->
                        val isSelected = dayNumber in config.weekDays
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onWeekDayToggled(dayNumber) },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndConditionDialog(
    currentCondition: RepeatEndCondition,
    onConditionSelected: (RepeatEndCondition) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by rememberSaveable {
        mutableStateOf(
            when (currentCondition) {
                is RepeatEndCondition.Never -> "never"
                is RepeatEndCondition.UntilDate -> "date"
                is RepeatEndCondition.AfterCount -> "count"
            }
        )
    }
    var countText by rememberSaveable {
        mutableStateOf(
            (currentCondition as? RepeatEndCondition.AfterCount)?.maxCount?.toString() ?: "10"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reminder_ends)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                // Never
                RepeatTypeOption(
                    text = stringResource(Res.string.reminder_ends_never),
                    selected = selectedType == "never",
                    onClick = { selectedType = "never" }
                )
                // After N occurrences
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RepeatTypeOption(
                        text = stringResource(Res.string.reminder_ends_after_count),
                        selected = selectedType == "count",
                        onClick = { selectedType = "count" }
                    )
                }
                if (selectedType == "count") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        modifier = Modifier.padding(start = AppDimens.SpacingXxl)
                    ) {
                        AppTextField(
                            value = countText,
                            onValueChange = { text ->
                                countText = text.filter { it.isDigit() }.take(3)
                            },
                            modifier = Modifier.width(64.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Text(
                            text = stringResource(Res.string.reminder_ends_occurrences),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButtonText(
                text = stringResource(Res.string.reminder_ends_done),
                onClick = {
                    val condition = when (selectedType) {
                        "count" -> RepeatEndCondition.AfterCount(countText.toIntOrNull()?.coerceIn(1, 999) ?: 10)
                        else -> RepeatEndCondition.Never
                    }
                    onConditionSelected(condition)
                }
            )
        },
        dismissButton = {
            AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
        }
    )
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

private fun formatReminderDateTime(dateTime: LocalDateTime): String {
    val month = dateTime.month.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    val day = dateTime.dayOfMonth
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$month $day, $hour:$minute"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDateTimePicker(
    selectedDateMillis: Long?,
    minDateMillis: Long,
    initialHour: Int,
    isTimeInPast: Boolean,
    onDateSelected: (Long) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (selectedDateMillis == null) {
        val currentYear = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).year
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = minDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= minDateMillis
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year >= currentYear
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                AppButtonText(
                    text = stringResource(Res.string.reminder_next),
                    onClick = { dateState.selectedDateMillis?.let(onDateSelected) }
                )
            },
            dismissButton = {
                AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
            }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = 0)

        LaunchedEffect(timeState.hour, timeState.minute) {
            onTimeChanged(timeState.hour, timeState.minute)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.reminder_select_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                AppButtonText(
                    text = stringResource(Res.string.ok),
                    onClick = { onTimeSelected(timeState.hour, timeState.minute) },
                    enabled = !isTimeInPast
                )
            },
            dismissButton = {
                AppButtonText(text = stringResource(Res.string.cancel), onClick = onDismiss)
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

private fun tomorrowAt(hour: Int, minute: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val tomorrowDate = now.plus(1, DateTimeUnit.DAY, tz).toLocalDateTime(tz).date
    val targetDateTime = LocalDateTime(tomorrowDate, LocalTime(hour, minute))
    return targetDateTime.toInstant(tz).toEpochMilliseconds()
}

fun combinePickerResults(datePickerMillis: Long, hour: Int, minute: Int): Long {
    val utcMidnight = Instant.fromEpochMilliseconds(datePickerMillis)
    val localDate = utcMidnight.toLocalDateTime(TimeZone.UTC).date
    val localDateTime = LocalDateTime(localDate, LocalTime(hour, minute))
    return localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(Res.string.add_item_placeholder),
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
