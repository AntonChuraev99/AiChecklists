package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.outlined.AttachFile
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TimePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppLinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonDestructive
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.FillOptionsSheet
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiChecklistAction
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.rememberDockFullExpandState
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiDockColor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiChecklistPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.ChatDockItemCreateOverride
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiItemCreatePromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppItemMetaChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.util.asWholeUrl
import com.antonchuraev.homesearchchecklist.desingsystem.util.displayDomain
import com.antonchuraev.homesearchchecklist.desingsystem.util.extractUrls
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.util.rememberLinkifiedText
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.DueLabelStyle
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.dueLabelSpec
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.label
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.containsRepeat
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.resolveChipLabel
import com.antonchuraev.homesearchchecklist.desingsystem.components.TokenChipPreview
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly.MoveToDayBottomSheet
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly.WeeklyChecklistDetailContent
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.ItemCreateReminderPreset
import com.antonchuraev.homesearchchecklist.feature.home.presentation.create.PendingItemAttachment
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.formatReminderDateTime
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PlatformCapabilities
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.FilePickerType
import com.antonchuraev.homesearchchecklist.core.filepicker.api.picker.rememberFilePickerLauncher
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Material 3 `FabPrimaryContainerHeight`. Not a design token in [AppDimens] because it is the
 * component's own size, not spacing — but it has to be named here because the list's bottom padding
 * and the snackbar host both have to reserve exactly the band the FAB floats in.
 */
private val DetailFabSize = 56.dp

/**
 * Vertical space the FAB occupies over the list: its own height plus the gap it keeps from the
 * bottom edge. Anything that must not end up underneath the FAB reserves this.
 */
private val DetailFabBand = DetailFabSize + AppDimens.SpacingLg

@Composable
fun ChecklistDetailScreen(
    checklistId: Long,
    focusItemId: String? = null,
    /**
     * Folder drill-down level: id of the FOLDER node whose children are shown. null = checklist root.
     * Forwarded from the [com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute.ChecklistDetail]
     * route by App.kt. Part of the ViewModel key so each level gets its own ViewModel instance.
     */
    currentFolderId: String? = null,
    /**
     * Opens the AI-chat sheet pre-anchored to this checklist.
     * Called with (checklistId, checklistName) so App.kt can display the context label.
     * Wired by App.kt.
     */
    onOpenChatSheet: ((Long, String) -> Unit)? = null,
    /** Mirror-collapse the chat context when this screen's dock closes. Wired by App.kt. */
    onChatCollapse: () -> Unit = {},
    /** True when the chat input is blank — drives BACK (collapse only when blank; else text holds open). */
    chatInputBlank: Boolean = true,
    /** Bumped by App.kt on every route change → animate the dock back to its Peek (auto-collapse). */
    routeCollapseSignal: Int = 0,
    /**
     * App-level continuous-drag chat dock content rendered INSIDE this screen's [GistiGlassChatDock]
     * (so the Haze backdrop blur is preserved). Invoked as `chatDockContent(dockState, placeholder,
     * dockAvailableDp, chips, itemCreateOverride)` — the screen owns its own [AnchoredDraggableState].
     * The last arg is non-null only while the dock is in item-create mode (the "+" flow); null = the
     * default AI-chat dock, byte-for-byte unchanged. When [chatDockContent] is null the dock is hidden.
     */
    chatDockContent: (@Composable (AnchoredDraggableState<DockAnchor>, String, Dp, @Composable () -> Unit, ChatDockItemCreateOverride?, () -> Unit) -> Unit)? = null,
    /** App-level FULL chat overlay content (the dock's third "floor"). Per-screen; see [ChecklistDetailContent]. */
    chatFullContent: (@Composable (DockFullExpandState, Int) -> Unit)? = null,
    /**
     * Fires a contextual prompt-chip [GistiChecklistAction] for THIS checklist (chips above the
     * chat input). Called with (checklistId, checklistName, action) so App.kt can set the chat
     * context and dispatch the right prefill / send. Wired by App.kt.
     */
    onChecklistQuickAction: ((Long, String, GistiChecklistAction) -> Unit)? = null,
    /**
     * v2 nav arm: render the inline "+ Add task" row instead of the dock's item-create mode, and
     * zero the item-create scrim.
     *
     * The dock itself is removed by App.kt passing `chatDockContent = null`; this flag exists because
     * that also removes the dock's [ChatDockItemCreateOverride] fast-add path, and shipping the arm
     * without a replacement input would be a REGRESSION (losing 4 reminder presets, Important,
     * Repeat, the live Smart-Add chip, attachment staging and multi-add) even though items still
     * get created. Default false → the control arm renders no extra row.
     */
    useInlineAddRow: Boolean = false,
    /**
     * v2 nav arm: non-null adds ONE AI-chat action to the top bar.
     *
     * The v2 chat FAB is hidden on non-tab routes (this screen is one), so without this the detail
     * screen would have no chat entry point at all once the dock is gone. Null in the control arm →
     * zero extra actions, so the toolbar is byte-identical.
     */
    onOpenChat: (() -> Unit)? = null,
    viewModel: ChecklistDetailViewModel = koinViewModel(
        key = "checklist_detail_${checklistId}_${currentFolderId ?: "root"}"
    ) { parametersOf(checklistId, currentFolderId) }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.CHECKLIST_DETAIL) }

    // Detect return from exact alarm settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.handleReturnedFromSettings()
    }

    val state by viewModel.screenState.collectAsStateWithLifecycle()

    when (val currentState = state) {
        ChecklistDetailState.Loading -> LoadingContent()
        ChecklistDetailState.NotFound -> NotFoundContent(
            onBack = { viewModel.sendIntent(ChecklistDetailIntent.OnBackClick) },
            onDelete = { viewModel.sendIntent(ChecklistDetailIntent.OnDeleteCorruptedChecklist) },
        )
        is ChecklistDetailState.Content -> ChecklistDetailContent(
            state = currentState,
            onIntent = viewModel::sendIntent,
            focusItemId = focusItemId,
            onOpenChatSheet = onOpenChatSheet?.let { cb ->
                { cb(currentState.checklist.id, currentState.checklist.name) }
            },
            onChatCollapse = onChatCollapse,
            chatInputBlank = chatInputBlank,
            routeCollapseSignal = routeCollapseSignal,
            chatDockContent = chatDockContent,
            chatFullContent = chatFullContent,
            onChecklistQuickAction = onChecklistQuickAction?.let { cb ->
                { action -> cb(currentState.checklist.id, currentState.checklist.name, action) }
            },
            useInlineAddRow = useInlineAddRow,
            onOpenChat = onOpenChat,
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
private fun NotFoundContent(
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    // Local UI flag — the NotFound state carries no fields, and a one-shot confirm here
    // needs no ViewModel round-trip (the delete itself goes through an intent).
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AppScaffold(
        title = stringResource(Res.string.error),
        onBackButtonClick = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.checklist_not_found),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            Text(
                text = stringResource(Res.string.checklist_not_found_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
            AppButtonDestructive(
                text = stringResource(Res.string.delete_checklist),
                onClick = { showDeleteConfirm = true },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.checklist_delete_title)) },
            text = { Text(stringResource(Res.string.checklist_not_found_delete_message)) },
            confirmButton = {
                AppButton(
                    text = stringResource(Res.string.delete),
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                )
            },
            dismissButton = {
                AppButtonText(
                    text = stringResource(Res.string.cancel),
                    onClick = { showDeleteConfirm = false }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecklistDetailContent(
    state: ChecklistDetailState.Content,
    onIntent: (ChecklistDetailIntent) -> Unit,
    focusItemId: String? = null,
    onOpenChatSheet: (() -> Unit)? = null,
    onChatCollapse: () -> Unit = {},
    chatInputBlank: Boolean = true,
    routeCollapseSignal: Int = 0,
    chatDockContent: (@Composable (AnchoredDraggableState<DockAnchor>, String, Dp, @Composable () -> Unit, ChatDockItemCreateOverride?, () -> Unit) -> Unit)? = null,
    chatFullContent: (@Composable (DockFullExpandState, Int) -> Unit)? = null,
    onChecklistQuickAction: ((GistiChecklistAction) -> Unit)? = null,
    /** v2: see [ChecklistDetailScreen]'s parameter of the same name. */
    useInlineAddRow: Boolean = false,
    /** v2: see [ChecklistDetailScreen]'s parameter of the same name. */
    onOpenChat: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Focus plumbing for the v2 inline add row, so the toolbar "+" still has somewhere to send the
    // caret now that there is no dock to expand. A signal counter rather than a direct
    // requestFocus() call: the row lives INSIDE the LazyColumn, so its FocusRequester is only
    // attached while the row is composed — calling requestFocus from the toolbar would throw
    // whenever the row is scrolled off screen. The toolbar bumps the signal and scrolls; the row
    // focuses itself when it composes. `handled` stops a later re-composition (scrolling the row
    // back into view) from popping the keyboard again on a stale signal.
    val inlineAddFocusRequester = remember { FocusRequester() }
    var inlineAddFocusSignal by remember { mutableStateOf(0) }
    var inlineAddFocusHandled by remember { mutableStateOf(0) }

    // Measured chat-dock height (already includes the dock's own ime + navbar insets). Hoisted ABOVE
    // AppScaffold so the snackbarHost slot can lift snackbars/toasts to sit just above the dock
    // instead of under it. Written by the dock's onSizeChanged; read by the list's bottom
    // contentPadding (dockHeight) AND the snackbar host.
    var dockHeightPx by remember { mutableStateOf(0) }

    // ── Continuous-drag chat dock state (per-screen; never shared across two-pane panes) ──
    val dockState = remember { AnchoredDraggableState(initialValue = DockAnchor.Peek) }
    // Per-screen FULL overlay state (the dock's third "floor") + the dock's live Expanded height (the
    // overlay's collapsed start height). Full is a SEPARATE state — never a third dock anchor.
    val fullState = rememberDockFullExpandState()
    var dockExpandedHeightPx by remember { mutableStateOf(0) }
    val dockScope = rememberCoroutineScope()
    val dockExpanded by remember { derivedStateOf { dockState.targetValue == DockAnchor.Expanded } }
    val chatFocusManager = LocalFocusManager.current
    // Tell App when THIS checklist's dock opens (seed context + name + analytics) / closes.
    // Collapsing the dock also exits item-create mode (chips + placeholder revert to the AI-chat dock).
    // In item-create mode we do NOT seed the chat context / fire the "chat opened" event — the user
    // opened the dock to add an item, not to chat.
    LaunchedEffect(dockExpanded) {
        if (dockExpanded) {
            if (!state.itemCreateMode) onOpenChatSheet?.invoke()
        } else {
            onChatCollapse()
        }
    }
    // Exit item-create only once the dock has FULLY settled at Peek — NOT the instant targetValue flips
    // to Peek (which is immediate on animateTo). settledValue updates only when the dock physically stops
    // at an anchor, so item-create (and its chips) stay on screen through the WHOLE collapse animation.
    // Otherwise itemCreateMode flips false mid-collapse → hasLastAnswer flips true → the chat-answer frame
    // swaps into the still-open-but-closing dock and flashes for the animation's duration (the "second
    // Back briefly opens the chat" artefact). Deferring the exit to the settle makes every collapse path
    // (Back AND grabber-drag) seamless: the chat frame only appears once the dock is already hidden at Peek.
    LaunchedEffect(dockState.settledValue) {
        if (dockState.settledValue == DockAnchor.Peek && state.itemCreateMode) {
            onIntent(ChecklistDetailIntent.OnDockItemCreateClosed)
        }
    }
    // Auto-collapse on any route change (App bumps the signal). animateTo needs anchors → NaN-guard.
    LaunchedEffect(routeCollapseSignal) {
        if (routeCollapseSignal > 0 && !dockState.offset.isNaN()) dockState.animateTo(DockAnchor.Peek)
    }
    // BACK while expanded: hide the keyboard, then collapse to peek ONLY if the input is blank (a
    // non-blank draft holds the dock open). Once collapsed the handler disables → next BACK exits.
    // PlatformBackHandler is the project's KMP-safe wrapper (no-op shape on wasmJs).
    PlatformBackHandler(enabled = dockExpanded || state.itemCreateMode) {
        chatFocusManager.clearFocus()
        when {
            // v2 (inline add row): there is NO dock, so dockState.offset is permanently NaN and the
            // branch below would do nothing at all — BACK swallowed with no visible effect, which is
            // a bug, not a no-op. Exit item-create directly instead; once it is off this handler
            // disables and the next BACK reaches NavDisplay.
            useInlineAddRow && state.itemCreateMode ->
                onIntent(ChecklistDetailIntent.OnDockItemCreateClosed)
            // Item-create: Back closes the create dock and returns the screen to its OPENED (peek)
            // state. ALWAYS collapse to Peek (regardless of the unrelated chat-input draft) — settling
            // to Peek flips dockExpanded→false, which fires OnDockItemCreateClosed and exits the mode.
            // (The dock keeps this Peek target stable while the re-appearing chat-answer frame grows —
            // see updateAnchors(newTarget) in GistiExpandableDockContent — so it no longer springs back
            // open as a chat. That was the "second Back opens the chat" bug.)
            state.itemCreateMode ->
                if (!dockState.offset.isNaN()) dockScope.launch {
                    dockState.animateTo(DockAnchor.Peek)
                    // Exit item-create AFTER the collapse settles. The settledValue effect below also does
                    // this, but only fires on a settledValue CHANGE — a rapid "+"→Back before the open
                    // animation reached Expanded leaves settledValue stale at Peek (never changed), so its
                    // exit wouldn't fire and item-create would stick. Awaiting the collapse here closes that
                    // gap. Idempotent with the settledValue exit (OnDockItemCreateClosed just clears state).
                    onIntent(ChecklistDetailIntent.OnDockItemCreateClosed)
                }
            // Chat: BACK always collapses to Peek. A draft is preserved — the text lives in the chat
            // input, which stays visible at Peek.
            !dockState.offset.isNaN() ->
                dockScope.launch { dockState.animateTo(DockAnchor.Peek) }
        }
    }
    // FULL overlay open → BACK collapses it back onto the expanded dock. Registered AFTER the dock
    // handler above so it wins while both are enabled (full-open implies dock-expanded).
    PlatformBackHandler(enabled = fullState.isOpen) {
        dockScope.launch { fullState.close() }
    }

    // Diagnostic logger for the attachment add path (picker callbacks below). On web these go to
    // the browser console as [D]/[W] Attachments: ...
    val logger: AppLogger = koinInject()
    // Controls the FillOptionsSheet opened from the "Fill checklist" row inside the settings sheet
    // (OverflowMenuSheet). The two Fill buttons used to live in the bottom bar, then the toolbar;
    // they now live in this sheet.
    var showFillSheet by remember { mutableStateOf(false) }
    val smartAddHintActive = remember { mutableStateOf(false) }
    // Normalized snapshot of the input at the moment the hint snackbar was shown.
    // We dismiss the snackbar only when the user makes a meaningful change
    // (whitespace-only edits don't dismiss — they haven't changed the parser-visible content).
    val smartAddHintTriggerInput = remember { mutableStateOf<String?>(null) }

    // Snackbar message from ViewModel (exact alarm, Smart Add hints, and attachment feedback)
    val exactGrantedMessage = stringResource(Res.string.reminder_exact_alarm_granted)
    val exactDeniedMessage = stringResource(Res.string.reminder_exact_alarm_denied)
    val smartAddHintAddText = stringResource(Res.string.smart_add_hint_add_item_text)
    val smartAddHintAddTime = stringResource(Res.string.smart_add_hint_add_time)
    val attachmentPremiumLimitMsg = stringResource(Res.string.attachment_premium_limit_reached_snackbar)
    val attachmentLoadErrorMsg = stringResource(Res.string.attachment_load_error)
    val attachmentTooLargeMsg = stringResource(Res.string.attachment_size_too_large_snackbar)
    val attachmentDeletedMsg = stringResource(Res.string.attachment_deleted_snackbar)
    val folderReminderUnavailableMsg = stringResource(Res.string.folder_reminder_unavailable)
    val calendarAppNotFoundMsg = stringResource(Res.string.calendar_app_not_found)
    val recurringNudgeSetMsg = stringResource(Res.string.recurring_nudge_set_confirmation)
    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        val isSmartAddHint = message == ChecklistDetailViewModel.SNACKBAR_SMART_ADD_HINT_ADD_TEXT ||
            message == ChecklistDetailViewModel.SNACKBAR_SMART_ADD_HINT_ADD_TIME
        val text = when (message) {
            ChecklistDetailViewModel.SNACKBAR_EXACT_GRANTED -> exactGrantedMessage
            ChecklistDetailViewModel.SNACKBAR_EXACT_DENIED -> exactDeniedMessage
            ChecklistDetailViewModel.SNACKBAR_SMART_ADD_HINT_ADD_TEXT -> smartAddHintAddText
            ChecklistDetailViewModel.SNACKBAR_SMART_ADD_HINT_ADD_TIME -> smartAddHintAddTime
            ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_PREMIUM_LIMIT -> attachmentPremiumLimitMsg
            ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_LOAD_ERROR -> attachmentLoadErrorMsg
            ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_TOO_LARGE -> attachmentTooLargeMsg
            ChecklistDetailViewModel.SNACKBAR_ATTACHMENT_DELETED -> attachmentDeletedMsg
            ChecklistDetailViewModel.SNACKBAR_FOLDER_REMINDER_UNAVAILABLE -> folderReminderUnavailableMsg
            ChecklistDetailViewModel.SNACKBAR_CALENDAR_APP_NOT_FOUND -> calendarAppNotFoundMsg
            ChecklistDetailViewModel.SNACKBAR_RECURRING_NUDGE_SET -> recurringNudgeSetMsg
            else -> message
        }
        smartAddHintActive.value = isSmartAddHint
        if (isSmartAddHint) {
            val currentInput = (state as? ChecklistDetailState.Content)?.pendingItemInput.orEmpty()
            smartAddHintTriggerInput.value = currentInput.normalizeForHintComparison()
        }
        snackbarHostState.showSnackbar(text, withDismissAction = true)
        smartAddHintActive.value = false
        smartAddHintTriggerInput.value = null
        onIntent(ChecklistDetailIntent.OnSnackbarDismissed)
    }

    // Auto-dismiss smart-add hint snackbar as soon as user makes a meaningful edit.
    // Whitespace-only changes (e.g. trailing space) preserve the snackbar — they don't
    // actually change what the parser would see on the next tap.
    val contentState = state as? ChecklistDetailState.Content
    LaunchedEffect(contentState?.pendingItemInput) {
        if (!smartAddHintActive.value) return@LaunchedEffect
        val currentNormalized = contentState?.pendingItemInput.orEmpty().normalizeForHintComparison()
        if (currentNormalized != smartAddHintTriggerInput.value) {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
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

    // ── Attachment: FilePicker launchers ────────────────────────────────────────
    // Two static pickers — avoids re-keying a single picker on type change. IMAGE is the photo
    // gallery; ANY is the "any file or photo" document picker (header row + "+File" tile both use it).
    val imagePicker = rememberFilePickerLauncher(type = FilePickerType.IMAGE) { result ->
        logger.debug(
            "Attachments",
            "picker callback: result=${result != null}, pendingItemId=${state.pendingAttachmentItemId}",
        )
        val itemId = state.pendingAttachmentItemId ?: run {
            logger.warning("Attachments", "picker callback: pendingAttachmentItemId is null — drop result")
            return@rememberFilePickerLauncher
        }
        onIntent(ChecklistDetailIntent.OnImagePickerLaunched)
        if (result == null) {
            logger.debug("Attachments", "picker callback: result null (cancelled)")
            return@rememberFilePickerLauncher
        }
        logger.debug("Attachments", "picker callback: dispatching OnAttachmentPicked path=${result.filePath}")
        onIntent(
            ChecklistDetailIntent.OnAttachmentPicked(
                itemId = itemId,
                sourcePath = result.filePath,
                fileName = result.fileName,
                mimeType = result.mimeType,
            )
        )
    }
    val filePicker = rememberFilePickerLauncher(type = FilePickerType.ANY) { result ->
        logger.debug(
            "Attachments",
            "picker callback: result=${result != null}, pendingItemId=${state.pendingAttachmentItemId}",
        )
        val itemId = state.pendingAttachmentItemId ?: run {
            logger.warning("Attachments", "picker callback: pendingAttachmentItemId is null — drop result")
            return@rememberFilePickerLauncher
        }
        onIntent(ChecklistDetailIntent.OnFilePickerLaunched)
        if (result == null) {
            logger.debug("Attachments", "picker callback: result null (cancelled)")
            return@rememberFilePickerLauncher
        }
        logger.debug("Attachments", "picker callback: dispatching OnAttachmentPicked path=${result.filePath}")
        onIntent(
            ChecklistDetailIntent.OnAttachmentPicked(
                itemId = itemId,
                sourcePath = result.filePath,
                fileName = result.fileName,
                mimeType = result.mimeType,
            )
        )
    }

    // Launch picker as a side effect of state flags (cleared immediately after launch
    // via OnImagePickerLaunched / OnFilePickerLaunched intent to prevent re-launch on recompose).
    LaunchedEffect(state.triggerImagePicker) {
        if (state.triggerImagePicker) imagePicker.launch()
    }
    LaunchedEffect(state.triggerFilePicker) {
        if (state.triggerFilePicker) filePicker.launch()
    }

    // Item-create picker (ANY type). The callback dispatches only intents built from `result`, but on
    // wasmJs a picker callback captures Compose state at composition time — wrap `onIntent` in
    // rememberUpdatedState so a re-composed lambda is always seen (filepicker stale-closure trap).
    val latestOnIntent by rememberUpdatedState(onIntent)
    val itemCreatePicker = rememberFilePickerLauncher(type = FilePickerType.ANY) { result ->
        latestOnIntent(ChecklistDetailIntent.OnItemCreatePickerLaunched)
        if (result == null) return@rememberFilePickerLauncher
        latestOnIntent(
            ChecklistDetailIntent.OnItemCreateAttachmentPicked(
                sourcePath = result.filePath,
                fileName = result.fileName,
                mimeType = result.mimeType,
            )
        )
    }
    LaunchedEffect(state.triggerItemCreatePicker) {
        if (state.triggerItemCreatePicker) itemCreatePicker.launch()
    }

    // ── Attachment: open-externally via AttachmentOpener ────────────────────────
    val attachmentOpener: com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener = org.koin.compose.koinInject()
    val coroutineScope = rememberCoroutineScope()    // shared with reorder below
    LaunchedEffect(state.pendingOpenExternallyPath) {
        val path = state.pendingOpenExternallyPath ?: return@LaunchedEffect
        onIntent(ChecklistDetailIntent.OnOpenExternallyDispatched)
        coroutineScope.launch {
            attachmentOpener.openExternally(path, state.pendingOpenExternallyMimeType)
        }
    }

    var isEditMode by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    /**
     * LazyColumn index of the v2 inline add row, which sits directly AFTER the active rows:
     * `[header(s)][active rows][inline_add_item][completed_header][completed rows]`.
     *
     * Exists because the row must be scrolled into view before it can take focus — it lives inside the
     * LazyColumn, so its FocusRequester only attaches while it is composed. Same arithmetic the
     * added-item scroll below uses; kept as one lambda so the two cannot drift apart.
     */
    val inlineAddRowIndex: () -> Int = {
        val checkedIds = if (state.separateCompleted) {
            state.defaultFill?.items?.filter { it.checked }?.map { it.id }?.toSet().orEmpty()
        } else {
            emptySet()
        }
        val activeNodeCount = if (state.foldersEnabled) {
            state.levelNodes.count { node ->
                node !is LevelNode.Leaf || node.fillItemId !in checkedIds
            }
        } else {
            val visibleIds = state.visibleFillItemIds
            val visible = state.defaultFill?.items?.let { items ->
                if (visibleIds == null) items else items.filter { it.id in visibleIds }
            }.orEmpty()
            visible.count { it.id !in checkedIds }
        }
        val headerCount = 1 + (if (state.additionalFillsCount > 0) 1 else 0)
        headerCount + activeNodeCount
    }
    // Pinned (not exitUntilCollapsed): the toolbar action icons (share / add / reminder /
    // overflow-settings) must stay reachable while the list scrolls. exitUntilCollapsed on the
    // single-row CenterAlignedTopAppBar (Compact) treats the whole bar height as collapsible and
    // scrolls it fully off-screen ("collapses"). Pinned keeps it fixed while still swapping in the
    // scrolledContainerColor as content passes under it.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hapticFeedback = LocalHapticFeedback.current
    // coroutineScope declared above (shared with attachment open-externally handler)

    // ── Focus-item scroll + highlight (from Calendar deeplink) ─────────────────
    // highlightedItemId drives animateColorAsState in ChecklistItemCard for a
    // ~1s primaryContainer flash — same timing as CalendarScreen DateHeader highlight.
    var highlightedItemId by remember { mutableStateOf<String?>(null) }
    // One-shot guard: scroll happens only once per screen entry, not on every
    // recomposition or back-stack restore. rememberSaveable survives configuration change.
    var didFocusScroll by rememberSaveable { mutableStateOf(false) }

    // Fade out the highlight after 1 s
    LaunchedEffect(highlightedItemId) {
        if (highlightedItemId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1000L)
        highlightedItemId = null
    }

    // Scroll to focusItemId once, as soon as items are available in state.
    // Key includes defaultFill?.items so we retry if state was Loading when first composed.
    LaunchedEffect(focusItemId, state.defaultFill?.items) {
        if (didFocusScroll || focusItemId == null) return@LaunchedEffect
        // Folder mode inserts FolderCards into the list, so the flat index math below no longer
        // matches the rendered LazyColumn (and a deeplinked item may live in a nested folder not
        // shown at this level). Skip the auto-scroll for folder checklists (Phase 4 will resolve
        // deeplink-to-nested-item). Non-folder checklists are unaffected.
        if (state.foldersEnabled) {
            didFocusScroll = true
            return@LaunchedEffect
        }
        val fill = state.defaultFill ?: return@LaunchedEffect

        // Derive unchecked / completed split exactly as the LazyColumn does.
        val sourceUnchecked = if (state.separateCompleted) {
            fill.items.filter { !it.checked }
        } else {
            fill.items
        }
        val completedInState = if (state.separateCompleted) {
            fill.items.filter { it.checked }
        } else {
            emptyList()
        }

        // Compute target index in the LazyColumn:
        //   slot 0 : ProgressHeader  (always)
        //   slot 1 : ViewAllFillsCard (conditional)
        //   slots … : unchecked items
        //   slot   : inline_add_item (conditional — not active on fresh open)
        //   slot   : completed_header (conditional)
        //   slots … : completed items (conditional)
        var index = 1 // ProgressHeader
        if (state.additionalFillsCount > 0) index += 1 // ViewAllFillsCard

        val uncheckedIdx = sourceUnchecked.indexOfFirst { it.id == focusItemId }
        if (uncheckedIdx >= 0) {
            index += uncheckedIdx
        } else {
            // Not in unchecked — look in completed section
            index += sourceUnchecked.size
            if (state.separateCompleted && completedInState.isNotEmpty()) {
                val completedIdx = completedInState.indexOfFirst { it.id == focusItemId }
                if (completedIdx < 0) return@LaunchedEffect // item not found at all
                index += 1 // completed_header
                index += completedIdx
            } else {
                return@LaunchedEffect // item not found
            }
        }

        listState.animateScrollToItem(index)
        highlightedItemId = focusItemId
        didFocusScroll = true
    }

    // Scroll to the freshly-added item after a dock item-create add (parity with the removed inline
    // path, which scrolled the list to the new row). Standard view only — the Weekly pane owns its
    // own list and scrolls itself (see WeeklyChecklistDetailContent). Keyed on the one-shot signal.
    LaunchedEffect(state.addedItemSignal) {
        if (state.addedItemSignal > 0 && state.checklist.viewMode == ChecklistViewMode.Standard) {
            // The new item is appended as the last ACTIVE (unchecked) row. With separateCompleted on,
            // the LazyColumn renders a "Completed" section (header + checked rows) BELOW the active
            // list, so totalItemsCount-1 is a completed row — scrolling there overshoots PAST the new
            // item and looks like it "landed in the middle". Target the last ACTIVE row instead
            // (robust whether or not the completed section is expanded).
            val checkedIds = if (state.separateCompleted) {
                state.defaultFill?.items?.filter { it.checked }?.map { it.id }?.toSet().orEmpty()
            } else {
                emptySet()
            }
            val activeNodeCount = if (state.foldersEnabled) {
                state.levelNodes.count { node ->
                    node !is LevelNode.Leaf || node.fillItemId !in checkedIds
                }
            } else {
                val visibleIds = state.visibleFillItemIds
                val visible = state.defaultFill?.items?.let { items ->
                    if (visibleIds == null) items else items.filter { it.id in visibleIds }
                }.orEmpty()
                visible.count { it.id !in checkedIds }
            }
            val headerCount = 1 + (if (state.additionalFillsCount > 0) 1 else 0)
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                val target = (headerCount + activeNodeCount - 1).coerceIn(0, total - 1)
                listState.animateScrollToItem(target)
            }
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

    // ── Item-create scrim state (shared by BOTH scrims) ──────────────────────────────────────────
    // A single alpha drives two tiled scrims so the WHOLE screen dims in lockstep while item-create
    // is active — the content scrim (inside the AppScaffold content, below the bright dock) and the
    // top-bar scrim (a root sibling ABOVE the AppScaffold, so it can dim the app bar the scaffold owns).
    // [contentTopPx] is the measured height of the top-bar zone (status bar + pinned app bar), captured
    // from the content anchor's position in the root; the top scrim uses exactly it, so the two scrims
    // tile edge-to-edge with no double-dark seam and no bright gap. Pinned app bar (pinnedScrollBehavior)
    // → the height is stable, and onGloballyPositioned re-measures on any layout change regardless.
    //
    // v2 (useInlineAddRow): forced to 0f. Both scrims are driven by state.itemCreateMode ALONE, not
    // by showDock — so with the dock removed they would still dim the entire screen with nothing
    // bright left to justify the dimming. There is no scrim in the inline-row design.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (state.itemCreateMode && !useInlineAddRow) 0.5f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "item_create_scrim",
    )
    var contentTopPx by remember { mutableStateOf(0f) }

    // ── Level counters — ONE source for the bar subtitle, the progress line, the empty state and the
    // list itself ──────────────────────────────────────────────────────────────────────────────────
    // Hoisted above AppScaffold because the top bar (a scaffold slot) and the list content live in
    // two different lambdas that cannot see each other's locals. A second, mirrored count in the bar
    // would drift from the list the moment either side changed.
    //
    // visibleFillItemIds is null when folders are off (flat list) → no filtering, so this is exactly
    // the set of leaves the Standard list renders at the current drill-down level.
    val visibleFillItems = remember(state.defaultFill?.items, state.visibleFillItemIds) {
        val items = state.defaultFill?.items.orEmpty()
        val ids = state.visibleFillItemIds
        if (ids == null) items else items.filter { it.id in ids }
    }

    // A level is leaves PLUS folders, and the counters have to agree with what the list draws.
    //
    // visibleFillItemIds carries LEAVES ONLY — the ViewModel routes a folder child into levelNodes as
    // LevelNode.Folder and never into visibleIds — while the list renders from levelNodes, folders
    // included. Counting leaves alone therefore reported "0 tasks" and an empty level on the root of
    // any checklist whose items live in folders, which is the normal shape of the folders feature:
    // "No tasks yet" printed directly above the folder cards, with the progress line hidden next to
    // folders showing "3 / 7" of their own.
    //
    // FolderUiModel.checked/total are already the aggregate over the folder's whole subtree
    // (ChecklistTree.folderProgress), so folding them in counts each descendant exactly once — the
    // leaves at this level and the subtrees hanging off it are disjoint sets.
    //
    // levelNodes is only populated in folder mode, so with folders off this filter is empty and the
    // numbers are the plain leaf counts they always were.
    val folderNodes = remember(state.levelNodes) {
        state.levelNodes.filterIsInstance<LevelNode.Folder>()
    }
    val checkedCount = visibleFillItems.count { it.checked } + folderNodes.sumOf { it.model.checked }
    val totalCount = visibleFillItems.size + folderNodes.sumOf { it.model.total }
    val progressFraction = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f
    val isComplete = totalCount > 0 && checkedCount == totalCount
    // Empty means "nothing to show", not "no leaves": a level holding only folders is not empty.
    val isLevelEmpty = visibleFillItems.isEmpty() && folderNodes.isEmpty()
    // Weekly has no folder levels and shows the whole week in one pane, so its subtitle counts the
    // whole fill; Standard counts the level the user is actually looking at, folder subtrees included.
    val openTaskCount = if (state.checklist.viewMode == ChecklistViewMode.Weekly) {
        state.defaultFill?.items.orEmpty().count { !it.checked }
    } else {
        totalCount - checkedCount
    }

    // ── v2 FAB visibility — a LAMBDA, not a value ─────────────────────────────────────────────────
    // THREE consumers need the same answer: the FAB itself, the list's bottom contentPadding (so the
    // last card — and its right-70% "open details" zone — is not trapped under the FAB) and the
    // snackbar host, which is a scaffold slot and cannot see the content scope at all.
    //
    // Hoisting the ANSWER would have meant reading WindowInsets.ime in this function's body, and the
    // IME inset animates frame by frame: the whole screen — top bar, actions, snackbar host and
    // content — would become one recomposition scope invalidated on every frame the keyboard moves.
    // Worse, it would do so in BOTH arms, and this change must leave the classic view untouched.
    // A lambda moves each read into its CALLER's scope, so only that slot re-runs.
    //
    // The arm/mode/edit tests come FIRST so v1 short-circuits and never reads an inset at all.
    //
    // The IME check is a plain comparison rather than the derivedStateOf used further down for the
    // dock: derivedStateOf over two already-read Dp values never invalidates (it reads no state
    // inside), which would freeze the FAB visible over the keyboard.
    val fabShown: @Composable () -> Boolean = {
        if (!useInlineAddRow ||
            state.checklist.viewMode != ChecklistViewMode.Standard ||
            isEditMode
        ) {
            false
        } else {
            val fabImeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val fabNavBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            fabImeBottom <= fabNavBottom + AppDimens.SpacingSm
        }
    }

    // Root Box wraps the AppScaffold so the top-bar scrim can be a sibling ABOVE the scaffold chrome.
    // Bottom sheets / dialogs stay OUTSIDE this Box (below) so they are never dimmed and float on top.
    Box(modifier = Modifier.fillMaxSize()) {
    AppScaffold(
        // v2: the name (of the checklist, or of the FOLDER while drilled in) moves out of the in-list
        // ProgressHeader and into the bar, with the open-task count as its subtitle — the same
        // anatomy the other v2 screens use. v1 keeps title = null, so its bar is untouched.
        title = if (useInlineAddRow) {
            state.currentFolderTitle ?: state.checklist.name
        } else {
            null
        },
        subtitle = if (useInlineAddRow) {
            pluralStringResource(Res.plurals.inbox_task_count, openTaskCount, openTaskCount)
        } else {
            null
        },
        startAlignedTitle = useInlineAddRow,
        onBackButtonClick = {
            if (isEditMode) {
                isEditMode = false
            } else {
                onIntent(ChecklistDetailIntent.OnBackClick)
            }
        },
        scrollBehavior = scrollBehavior,
        // Content extends behind the navbar so the glass dock's blurred backdrop covers the navbar
        // zone (matches MainScreen); the dock carries its own navigationBarsPadding.
        contentExtendsBehindNavBar = true,
        snackbarHost = {
            // Lift the snackbar to sit ABOVE the floating chat dock (the measured dockHeightPx already
            // includes the dock's ime + navbar insets). When the dock is hidden (edit mode / no chat
            // content) fall back to a plain imePadding so the snackbar still clears the keyboard.
            val dockShown = chatDockContent != null && !isEditMode
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = when {
                    dockShown -> Modifier.padding(
                        bottom = with(LocalDensity.current) { dockHeightPx.toDp() } + AppDimens.SpacingSm
                    )
                    // v2 has no dock, so it would fall into the plain-imePadding branch below — but
                    // contentExtendsBehindNavBar means nothing insets this host from the navigation
                    // bar, and the FAB now occupies the bottom-end corner. The undo-delete snackbar
                    // is this screen's most frequent message; it has to clear both.
                    useInlineAddRow -> Modifier
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(bottom = if (fabShown()) DetailFabBand else 0.dp)
                    else -> Modifier.imePadding()
                },
            )
        },
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
                // Normal mode: regular toolbar actions.
                // Visual order right→left (as the user reads the bar): settings · notifications ·
                // add item · share. Compose lays the actions Row out left→right, so the source order
                // below is the reverse: share, add, notifications, overflow(settings).
                // Fill + Edit no longer live here — they are rows inside the settings sheet
                // (OverflowMenuSheet) opened by the overflow icon.
                //
                // v2 trims the bar to three: chat · reminder · overflow. Share moves INTO the
                // overflow sheet (see its onShareClick) and "+" becomes the FAB — neither action is
                // dropped, they change carrier.
                if (!useInlineAddRow) {
                    IconButton(onClick = { onIntent(ChecklistDetailIntent.OnShareClick) }) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(Res.string.share))
                    }
                }
                // v2 only: the chat FAB is hidden on non-tab routes and the dock is gone, so this is
                // the detail screen's chat entry point. Null in the control arm → nothing rendered,
                // toolbar unchanged.
                if (onOpenChat != null) {
                    IconButton(
                        onClick = {
                            // Seed the chat with THIS checklist BEFORE opening it. In the control arm
                            // the dock's own expand callback does this; in v2 the dock lives in the
                            // shell and has no idea which screen raised it, so a chat opened here
                            // would answer "what am I missing?" against no checklist at all.
                            onOpenChatSheet?.invoke()
                            onOpenChat()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(Res.string.detail_open_chat_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Toolbar "+" only in Standard view. In Weekly view items are added via the
                // per-day section "+" (inline WeeklyAddItemRow), which already targets the correct
                // weekday — a toolbar "+" there would need a day to land in and is redundant.
                //
                // v2 moves this action to the FAB below (search "v2 FAB"); its onClick is this
                // button's former inline-row branch, moved verbatim, so fast add is not lost.
                if (state.checklist.viewMode != ChecklistViewMode.Weekly && !useInlineAddRow) {
                    IconButton(
                        onClick = {
                            // Enter item-create mode in the shared chat dock. FIFO ordering: the mode flag
                            // is set synchronously here (sendIntent → MutableStateFlow update) BEFORE the
                            // dock animation coroutine launches, so the dock already binds to item-create
                            // by the time it expands.
                            // Re-tap guard: if already in item-create mode, just re-expand (don't re-open —
                            // re-opening would wipe in-progress text).
                            if (!state.itemCreateMode) {
                                onIntent(ChecklistDetailIntent.OnDockItemCreateOpened(null))
                            }
                            dockScope.launch {
                                if (!dockState.offset.isNaN()) dockState.animateTo(DockAnchor.Expanded)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(Res.string.add_item)
                        )
                    }
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
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnOverflowMenuClick) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.more_options)
                    )
                }
            }
        },
        // No bottomBar: the chat dock is now a floating glassmorphism overlay rendered inside the
        // content area (see the anchor Box below) so its backdrop can blur the scrolling content.
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
            // ── Glassmorphism chat-dock overlay scaffolding ──────────────────────────
            // The dock is a floating overlay (NOT a Scaffold bottomBar) so its backdrop can be a
            // live blur of the scrolling content. The scroll content is marked hazeSource; the dock
            // (sibling, BottomCenter) samples it via hazeEffect. dockHeight is measured so the list
            // gets exactly enough bottom contentPadding to scroll clear of the dock (single owner).
            val hazeState = rememberHazeState()
            val density = LocalDensity.current
            val dockHeight = with(density) { dockHeightPx.toDp() }
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // IME is "open" when its inset exceeds the nav-bar inset by a margin (closed ime == navbar).
            val imeVisible by remember { derivedStateOf { imeBottom > navBottom + 8.dp } }
            // Show the dock unless editing. The `!imeVisible` gate only suppresses it for the LIST's
            // inline add-item field (dockExpanded==false): focusing the chat input expands the dock
            // (dockExpanded==true), so the chat keeps the dock visible and lifts it above the keyboard
            // (GistiGlassChatDock owns ime ∪ navigationBars). The add-item field never expands chat,
            // so the dock still hides for it.
            val showDock = !isEditMode && (dockExpanded || !imeVisible) && chatDockContent != null
            // Single owner of the bottom inset. Content now runs edge-to-edge behind the navbar, so
            // the no-dock branch (edit mode) adds the navbar inset itself; when the dock is shown its
            // measured dockHeight already includes its own navigationBarsPadding.
            val listBottomPadding = when {
                // Keyboard up AND the dock is expanded above it (item-create chips OR the AI chat
                // panel — both float above the keyboard via their own imePadding). The list's
                // Modifier.imePadding() reserves the keyboard, so here we ADDITIONALLY reserve the
                // dock body height ABOVE the keyboard (measured dock height minus the ime inset the
                // list already handled) — otherwise the last items stay hidden under the expanded dock.
                imeVisible && (state.itemCreateMode || dockExpanded) ->
                    (dockHeight - imeBottom).coerceAtLeast(0.dp) + AppDimens.SpacingXxl
                // IME open (plain chat over the list): the list's Modifier.imePadding() already shrinks
                // the viewport above the keyboard (restoring the pre-dock adjustResize "content lifts"
                // behaviour), so the contentPadding only needs breathing room here — adding imeBottom
                // too would double-count and push the last item too far up.
                imeVisible -> AppDimens.SpacingSm
                showDock -> dockHeight + AppDimens.SpacingXxl
                // v2 always lands here (no dock). The FAB floats OVER the list, so without its band
                // the last card sits under it — and with it the right-70% zone that opens the item
                // details sheet, which would read as a dead card rather than a covered one.
                else -> navBottom + AppDimens.SpacingLg + (if (fabShown()) DetailFabBand else 0.dp)
            }
            // FIX B: answer cap (status bar → keyboard top), computed HERE at the host where the ime
            // inset is reliable. Unspecified when the keyboard is down (use the design cap).
            val statusTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val containerHDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
            val dockAvailableDp = if (imeVisible) {
                (containerHDp - imeBottom - statusTopDp).coerceAtLeast(0.dp)
            } else {
                Dp.Unspecified
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Report where the scaffold content starts in the root (= status bar + top app bar
                    // height). The top-bar scrim in the root Box uses this as its exact height so the two
                    // scrims tile without a seam. Recomputes on any layout change (size-class flip etc.).
                    .onGloballyPositioned { contentTopPx = it.positionInRoot().y },
            ) {
              Box(
                  modifier = Modifier
                      .fillMaxSize()
                      .background(MaterialTheme.colorScheme.background)
                      .hazeSource(hazeState)
              ) {
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
                        // imePadding() shrinks the week list above the keyboard when adding a per-day
                        // item (edge-to-edge: the window no longer resizes itself, so the list must).
                        modifier = Modifier.fillMaxSize().imePadding(),
                        // Clear the floating chat-dock overlay at the bottom of the week list.
                        contentBottomPadding = listBottomPadding,
                        // One-shot signal → scroll to the day section of the dock-added item (today).
                        addedItemSignal = state.addedItemSignal,
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
            // Folder mode: show only the leaf items of the current folder level. [visibleFillItems]
            // is hoisted to the top of this composable (the bar subtitle and the progress line read
            // the same list) — visibleFillItemIds is null when folders are off, so with folders off
            // it is the unfiltered fill, identical to before.
            val completedItems by remember(visibleFillItems, state.separateCompleted) {
                derivedStateOf {
                    if (state.separateCompleted) visibleFillItems.filter { it.checked }
                    else emptyList()
                }
            }
            var completedExpanded by remember { mutableStateOf(true) }

            // SINGLE mixed reorderable list of nodes at this level (folders + leaves intermixed in
            // template order), so a folder can be dragged to any slot between items. In folder mode
            // the order comes from the ViewModel (state.levelNodes); with folders OFF there are no
            // folders and the list is just the (filtered) fill items — identical to before.
            val checkedFillIds = remember(visibleFillItems, state.separateCompleted) {
                if (state.separateCompleted) {
                    visibleFillItems.filter { it.checked }.map { it.id }.toSet()
                } else {
                    emptySet()
                }
            }
            val sourceNodes = remember(state.levelNodes, state.foldersEnabled, visibleFillItems, checkedFillIds) {
                if (state.foldersEnabled) {
                    // Drop checked leaves into the completed section; keep folders + unchecked leaves.
                    state.levelNodes.filter { node ->
                        node !is LevelNode.Leaf || node.fillItemId !in checkedFillIds
                    }
                } else {
                    // Flat list (no folders): every visible fill item is a leaf node, minus the
                    // completed ones when separateCompleted is on.
                    visibleFillItems
                        .filter { it.id !in checkedFillIds }
                        .map { LevelNode.Leaf(it.id) }
                }
            }
            val fillItemById = remember(defaultFill.items) { defaultFill.items.associateBy { it.id } }

            // Local mutable list for optimistic reorder (no DB writes during drag)
            var localNodes by remember(sourceNodes) {
                mutableStateOf(sourceNodes)
            }

            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                // Offset = number of LazyColumn items BEFORE the reorderable list: ProgressHeader (1)
                // + optional ViewAllFillsCard. Folders now live IN the reorderable list, so they no
                // longer add to the offset (matches the proven MainScreenContent reorder offset =
                // header-item count). NB: the previous value (2 + …) was off by one, which made the
                // first row undraggable to the very top — corrected here so a top folder can move up.
                val headerCount = 1 + (if (state.additionalFillsCount > 0) 1 else 0)
                val fromIndex = from.index - headerCount
                val toIndex = to.index - headerCount
                if (fromIndex >= 0 && toIndex >= 0 && fromIndex < localNodes.size && toIndex < localNodes.size) {
                    localNodes = localNodes.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
            // v2 progress line — chrome, not a card: full-bleed under the app bar, Material's own
            // 4dp height, no clip. The name and the "3 / 7" counter it used to sit under now live in
            // the bar, so the bare line is all that is left of ProgressHeader here.
            //
            // Hidden on an empty level (a 0% line under an empty screen is noise) and, by living in
            // the Standard branch, never rendered in Weekly at all.
            if (useInlineAddRow) {
                val progressDescription = stringResource(
                    Res.string.detail_progress_content_description,
                    checkedCount,
                    totalCount,
                )
                AnimatedVisibility(
                    visible = !isLevelEmpty,
                    enter = expandVertically(animationSpec = tween(durationMillis = 200)) +
                        fadeIn(animationSpec = tween(durationMillis = 200)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 200)) +
                        fadeOut(animationSpec = tween(durationMillis = 200)),
                ) {
                    AppLinearProgressIndicator(
                        progress = { progressFraction },
                        // The visible ratio is gone from the screen, so without this a screen reader
                        // would announce a bare percentage with nothing to anchor it.
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = progressDescription },
                        color = if (isComplete) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Shrink the list above the keyboard. Under enableEdgeToEdge the window no longer
                    // resizes on IME, so without this the keyboard overlaps the last item and the
                    // inline add-input (the regression after the chat-dock floating-overlay rework).
                    .imePadding()
                    .adaptiveContentWidth()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                // Bottom contentPadding clears the floating dock (IME lift is handled by imePadding
                // above) — this is the "empty space" between the last item and the floating dock.
                contentPadding = PaddingValues(bottom = listBottomPadding),
            ) {
                // Progress header. At a folder level the header shows the folder name (the
                // checklist name still lives in the top app bar); the progress bar reflects only
                // the items visible at this level.
                item {
                    // ProgressHeader + the retention recurring nudge share ONE LazyColumn slot so the
                    // deeplink auto-scroll index math (slot 0 = ProgressHeader) stays valid whether or
                    // not the nudge is showing.
                    //
                    // The slot itself is UNCONDITIONAL in both arms even when v2 leaves it empty:
                    // inlineAddRowIndex(), the focus-item auto-scroll and the reorder offset all
                    // count it as header slot 0. Dropping it would send the FAB scroll and every
                    // drag-and-drop write one row off.
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                        if (useInlineAddRow) {
                            // v2: the name and the "3 / 7" counter moved to the bar and the progress
                            // bar to the chrome above the list, so what is left of ProgressHeader
                            // here is the celebration banner — plus an empty state, which the old
                            // header never had (an empty level used to render as a bare 0% bar).
                            AnimatedVisibility(
                                visible = isComplete,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                CompletionBanner()
                            }
                            if (isLevelEmpty) {
                                EmptyState(
                                    icon = Icons.Outlined.ChecklistRtl,
                                    title = stringResource(Res.string.detail_empty_title),
                                    description = stringResource(Res.string.detail_empty_description),
                                    modifier = Modifier.padding(top = AppDimens.SpacingXxl),
                                )
                            }
                        } else {
                            ProgressHeader(
                                items = visibleFillItems,
                                name = state.currentFolderTitle ?: state.checklist.name,
                            )
                        }
                        if (state.showRecurringNudge) {
                            RecurringNudgeCard(
                                onAccept = { onIntent(ChecklistDetailIntent.OnRecurringNudgeAccepted) },
                                onDismiss = { onIntent(ChecklistDetailIntent.OnRecurringNudgeDismissed) },
                            )
                        }
                    }
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

                // Mixed reorderable list of folders + unchecked leaves at this level, in template
                // order. A folder can be dragged to any slot between items (folders + items mixed).
                // With folders OFF, localNodes contains only leaves → identical to the previous
                // flat-list behaviour.
                items(
                    count = localNodes.size,
                    key = { localNodes[it].reorderId },
                ) { index ->
                    val node = localNodes[index]
                    ReorderableItem(
                        state = reorderableState,
                        key = node.reorderId,
                        enabled = isEditMode,
                    ) { isDragging ->
                        when (node) {
                            is LevelNode.Folder -> {
                                val folder = node.model
                                FolderCard(
                                    name = folder.name,
                                    total = folder.total,
                                    progressLabel = stringResource(
                                        Res.string.folder_progress,
                                        folder.checked,
                                        folder.total,
                                    ),
                                    hasReminder = folder.hasReminder,
                                    isEditMode = isEditMode,
                                    isDragging = isDragging,
                                    wiggleAngle = wiggleAngle,
                                    onOpen = { onIntent(ChecklistDetailIntent.OnOpenFolder(folder.id)) },
                                    onLongPress = { onIntent(ChecklistDetailIntent.OnFolderLongPress(folder.id)) },
                                    cardDragModifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onIntent(ChecklistDetailIntent.OnFinalizeReorder(localNodes.map { it.reorderId }))
                                        },
                                    ),
                                )
                            }
                            is LevelNode.Leaf -> {
                                val item = fillItemById[node.fillItemId]
                                if (item != null) {
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
                                            isHighlighted = item.id == highlightedItemId,
                                            cardDragModifier = Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onIntent(ChecklistDetailIntent.OnFinalizeReorder(localNodes.map { it.reorderId }))
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add-item input moved to the shared chat dock (item-create mode, the "+" toolbar
                // button). The former inline LazyColumn field has been removed — adding items now
                // reuses the dock's bottom input + selectable reminder/property chips.
                //
                // …EXCEPT in the v2 nav arm, where the dock is gone. The row below is its
                // replacement. It is emitted only when [useInlineAddRow] is true — not an empty
                // `item {}` — so the control arm's LazyColumn gets no extra slot and no extra
                // `spacedBy` gap. Everything binds to the EXISTING item-create intents:
                // OnAddItemWithParse is the single source of truth for the template+fill dual write,
                // Smart-Add parsing and reminder scheduling (rule `checklist-domain`), so no item is
                // ever written from here.
                if (useInlineAddRow) {
                    item(key = "inline_add_item") {
                        // Focus the row when the toolbar "+" asked for it AND the row is actually
                        // composed. A failure is logged, never swallowed: a "+" that silently does
                        // nothing reads as a frozen screen.
                        LaunchedEffect(inlineAddFocusSignal) {
                            if (inlineAddFocusSignal > inlineAddFocusHandled) {
                                inlineAddFocusHandled = inlineAddFocusSignal
                                runCatching { inlineAddFocusRequester.requestFocus() }
                                    .onFailure { e ->
                                        logger.warning(
                                            "ChecklistDetail",
                                            "inline add-row focus request failed: ${e.message}",
                                        )
                                    }
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                        ) {
                            // Chips + staged attachments appear once the row is active, exactly as
                            // they did inside the expanded dock — an input that only takes text
                            // would be a regression even though items still get created.
                            if (state.itemCreateMode) {
                                ItemCreateChipsRow(
                                    state = state,
                                    onIntent = onIntent,
                                    horizontalPadding = 0.dp,
                                )
                                ItemCreateAttachmentStrip(
                                    pending = state.itemCreatePendingAttachments,
                                    onRemove = { sourcePath ->
                                        onIntent(
                                            ChecklistDetailIntent
                                                .OnRemoveItemCreatePendingAttachment(sourcePath)
                                        )
                                    },
                                    horizontalPadding = 0.dp,
                                )
                            }
                            InlineAddItemInput(
                                text = state.pendingItemInput,
                                canSend = state.pendingItemInput.isNotBlank(),
                                focusRequester = inlineAddFocusRequester,
                                onTextChange = {
                                    onIntent(ChecklistDetailIntent.OnItemInputChanged(it))
                                },
                                onSubmit = { onIntent(ChecklistDetailIntent.OnAddItemWithParse) },
                                onAttachClick = {
                                    onIntent(ChecklistDetailIntent.OnItemCreateAttachClick)
                                },
                                onFocusChanged = { focused ->
                                    when {
                                        // Focus IS the "open item-create" gesture here: it reveals
                                        // the chips and initialises their state, mirroring what the
                                        // dock's "+" used to do.
                                        focused && !state.itemCreateMode ->
                                            onIntent(
                                                ChecklistDetailIntent.OnDockItemCreateOpened(null)
                                            )
                                        // Blur with a draft KEEPS the mode (and the chip selections)
                                        // alive — the dock's own "a draft holds it open" rule.
                                        !focused && state.itemCreateMode &&
                                            state.pendingItemInput.isBlank() ->
                                            onIntent(ChecklistDetailIntent.OnDockItemCreateClosed)
                                    }
                                },
                            )
                        }
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
                                    isHighlighted = item.id == highlightedItemId,
                                )
                            }
                        }
                    }
                }

            }
            } // end Column (v2 progress line + list)
                } // end Standard ->
                } // end when (state.checklist.viewMode)
              } // end hazeSource Box (live backdrop captured by the dock)

              // v2 FAB — the toolbar "+" in a new carrier, bottom-end where Todoist puts it. A
              // sibling of the haze source rather than an AppScaffold slot: AppScaffold has no FAB
              // slot and core/designsystem is out of scope here. Hidden with the keyboard up (the
              // inline add row already owns that moment) and in edit mode (drag/delete, not create).
              AnimatedVisibility(
                  visible = fabShown(),
                  enter = scaleIn(tween(durationMillis = 200, easing = FastOutSlowInEasing)) + fadeIn(),
                  exit = scaleOut(tween(durationMillis = 150)) + fadeOut(),
                  modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .navigationBarsPadding()
                      .padding(
                          end = AppDimens.ScreenPaddingHorizontal,
                          bottom = AppDimens.SpacingLg,
                      ),
              ) {
                  FloatingActionButton(
                      onClick = {
                          // Verbatim the toolbar "+"'s v2 branch — same intent, same focus signal,
                          // same scroll target. Re-tap guard: if item-create is already open, do NOT
                          // re-open it (that would wipe in-progress text), just re-focus and scroll.
                          if (!state.itemCreateMode) {
                              onIntent(ChecklistDetailIntent.OnDockItemCreateOpened(null))
                          }
                          // Bump BEFORE the scroll: the row focuses itself when it composes, and the
                          // scroll is what composes it.
                          //
                          // Targets the inline row's OWN index, not totalItemsCount - 1. The list is
                          // [header(s)][active rows][inline_add_item][completed_header][completed
                          // rows], so with separateCompleted on and a screenful of completed items
                          // the last index scrolls PAST the inline row — it leaves the composition,
                          // its LaunchedEffect never runs, and the tap becomes a silent no-op.
                          inlineAddFocusSignal++
                          dockScope.launch {
                              val total = listState.layoutInfo.totalItemsCount
                              if (total > 0) {
                                  val target = inlineAddRowIndex().coerceIn(0, total - 1)
                                  listState.animateScrollToItem(target)
                              }
                          }
                      },
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                  ) {
                      Icon(
                          imageVector = Icons.Filled.Add,
                          // NOT add_item: the inline add row's send button already uses it, and in v2
                          // both are on screen at once — two nodes with the same description are
                          // indistinguishable to a screen reader (and ambiguous to UI tests).
                          contentDescription = stringResource(Res.string.detail_add_task_fab),
                      )
                  }
              }

              // Item-create CONTENT scrim — dims the list while item-create mode is active. A sibling
              // drawn AFTER the list content but BEFORE the navbar strip + dock below: it covers the
              // scrolling content, then the navbar strip and the dock draw ON TOP and stay bright (the
              // nav strip is visually part of the dock — same gistiDockColor() — so it must NOT be dimmed;
              // see the bottom-nav-must-match-dock fix). Being later in the Box, the dock also keeps its
              // touch priority so the scrim never steals its taps. The TOP-BAR scrim (root Box, below)
              // dims the app bar in lockstep via the shared [scrimAlpha]. Tapping the scrim collapses the
              // dock to Peek → exits item-create via the settledValue==Peek gate above.
              if (scrimAlpha > 0.001f) {
                  Box(
                      modifier = Modifier
                          .matchParentSize()
                          .background(Color.Black.copy(alpha = scrimAlpha))
                          .then(
                              if (state.itemCreateMode) {
                                  Modifier.clickable(
                                      interactionSource = remember { MutableInteractionSource() },
                                      indication = null,
                                  ) {
                                      if (!dockState.offset.isNaN()) {
                                          dockScope.launch { dockState.animateTo(DockAnchor.Peek) }
                                      }
                                  }
                              } else {
                                  // Fading out (mode already exited): non-interactive so it can't block taps.
                                  Modifier
                              },
                          ),
                  )
              }

              // Nav-bar strip — same as MainScreen: paint the system navigation-bar zone with the dock's
              // own gistiDockColor() so the gesture/nav strip matches the dock instead of letting the
              // white page show through beneath it (the dock sits navbar-padded ABOVE this strip, owning
              // ime ∪ navigationBars, so the strip can't live inside the dock — it is a sibling filling
              // exactly the navbar inset at the screen bottom). Drawn AFTER the content scrim so it stays
              // dock-white during item-create (the system-nav zone belongs to the dock, not the dimmed
              // content). Only while the dock is shown.
              if (showDock) {
                  Box(
                      modifier = Modifier
                          .align(Alignment.BottomCenter)
                          .fillMaxWidth()
                          .windowInsetsBottomHeight(WindowInsets.navigationBars)
                          .background(gistiDockColor()),
                  )
              }

              // Floating dock — a DIRECT child of the anchor Box. FIX B: `.imePadding()` lifts the WHOLE
              // dock above the keyboard at the HOST (the deep windowInsetsPadding(ime) inside read ≈0
              // on the phone). Host is the single bottom-inset owner: imePadding + navigationBarsPadding
              // (collapsed peek still clears the navbar). FIX D: flat grey dock. Hidden in edit mode and
              // while the LIST's add-item IME is up (but not while the chat itself is expanded).
              if (showDock && chatDockContent != null) {
                  GistiGlassChatDock(
                      hazeState = hazeState,
                      bottomPadding = AppDimens.SpacingSm,
                      modifier = Modifier
                          .align(Alignment.BottomCenter)
                          .fillMaxWidth()
                          .imePadding()
                          .navigationBarsPadding()
                          // Track the measured dock height in EVERY anchor (peek AND expanded). The
                          // list's bottom contentPadding is derived from it, so when the chat panel
                          // expands to half-screen the list gains enough bottom room to scroll its
                          // last items clear of the dock. (Previously the height was frozen at Peek to
                          // avoid a list reflow on open — but that trapped the last items under the
                          // open chat, which is the bug this fixes.)
                          .onSizeChanged {
                              dockHeightPx = it.height
                              // Live dock height → the full overlay's start height (grows out of the dock).
                              dockExpandedHeightPx = it.height
                          },
                      // The morphing chat content. Peek placeholder = contextual "Ask about <name>…";
                      // chips hosted INSIDE the morph (it fades + collapses them as the dock expands).
                      pillContent = {
                          val itemCreate = state.itemCreateMode
                          chatDockContent(
                              dockState,
                              // Item-create mode shows the "I want to…" placeholder; AI-chat shows the
                              // contextual "Ask Gisti…" peek placeholder.
                              if (itemCreate) {
                                  stringResource(Res.string.item_create_input_placeholder)
                              } else {
                                  stringResource(Res.string.main_ask_gisti_placeholder)
                              },
                              dockAvailableDp,
                              {
                                  // Peek chips = the AI-chat contextual chips. Hidden in item-create mode
                                  // (the item-create chips render in the expanded answer frame instead).
                                  if (!itemCreate && onChecklistQuickAction != null) {
                                      GistiPromptChips(
                                          chips = gistiChecklistPromptChips(
                                              whatsMissingLabel = stringResource(Res.string.checklist_prompt_whats_missing),
                                              generateIdeasLabel = stringResource(Res.string.checklist_prompt_generate_ideas),
                                              addItemsLabel = stringResource(Res.string.checklist_prompt_add_items),
                                              summaryLabel = stringResource(Res.string.checklist_prompt_summary),
                                              remindLabel = stringResource(Res.string.checklist_prompt_remind),
                                          ),
                                          // Tapping a chip animates the dock open AND fires its chat flow.
                                          onChipClick = { action ->
                                              dockScope.launch {
                                                  if (!dockState.offset.isNaN()) dockState.animateTo(DockAnchor.Expanded)
                                              }
                                              onChecklistQuickAction(action)
                                          },
                                          onNewListClick = null,
                                          modifier = Modifier.fillMaxWidth(),
                                      )
                                  }
                              },
                              // Item-create override: when non-null the dock binds its input to the
                              // checklist VM's create path (Send adds an item; the AI chat is never
                              // called) and shows the selectable item-create chips.
                              if (itemCreate) {
                                  ChatDockItemCreateOverride(
                                      text = state.pendingItemInput,
                                      onTextChange = { onIntent(ChecklistDetailIntent.OnItemInputChanged(it)) },
                                      onSend = { onIntent(ChecklistDetailIntent.OnAddItemWithParse) },
                                      canSend = state.pendingItemInput.isNotBlank(),
                                      chips = { ItemCreateChipsRow(state = state, onIntent = onIntent) },
                                      onAttachClick = { onIntent(ChecklistDetailIntent.OnItemCreateAttachClick) },
                                      attachmentStrip = {
                                          ItemCreateAttachmentStrip(
                                              pending = state.itemCreatePendingAttachments,
                                              onRemove = { sp ->
                                                  onIntent(ChecklistDetailIntent.OnRemoveItemCreatePendingAttachment(sp))
                                              },
                                          )
                                      },
                                      hasAttachments = state.itemCreatePendingAttachments.isNotEmpty(),
                                  )
                              } else {
                                  null
                              },
                              // ↗ / drag-up over Expanded → open the FULL overlay.
                              { dockScope.launch { fullState.open() } },
                          )
                      },
                  )
              }
            } // end anchor Box
        }
    }

        // Top-bar scrim — the second tile, dimming the app-bar zone (status bar + pinned app bar) that
        // the AppScaffold owns, in lockstep with the content scrim via the shared [scrimAlpha]. Its
        // height is the measured [contentTopPx], so its bottom edge meets the content scrim's top edge
        // exactly — no double-dark seam, no bright gap. Interactive only while item-create is active;
        // a tap collapses the dock to Peek → exits item-create (same as tapping the content scrim).
        if (scrimAlpha > 0.001f && contentTopPx > 0.5f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { contentTopPx.toDp() })
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .then(
                        if (state.itemCreateMode) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!dockState.offset.isNaN()) {
                                    dockScope.launch { dockState.animateTo(DockAnchor.Peek) }
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        // FULL chat overlay — sibling of AppScaffold (covers the top bar; statusBarsPadding inside).
        // Opaque, above the dock AND the top-bar scrim. Renders nothing until opened (its own reveal gate).
        chatFullContent?.invoke(fullState, dockExpandedHeightPx)
    } // end root Box (top-bar scrim + full chat overlay — siblings above the AppScaffold chrome)

    // Item details sheet — opens when user taps the right 70% of a ChecklistItemCard
    val detailsItem = state.itemDetailsSheetFor?.let { id ->
        state.defaultFill?.items?.firstOrNull { it.id == id }
    }
    if (detailsItem != null) {
        val isEditingThisItem = state.editingItemTextFor == detailsItem.id
        val isPremium = state.userLimits?.isPremium == true
        ItemDetailsSheet(
            item = detailsItem,
            isEditingText = isEditingThisItem,
            editingTextDraft = state.editingItemTextDraft,
            onStartTextEdit = { onIntent(ChecklistDetailIntent.OnStartItemTextEdit(detailsItem.id)) },
            onTextDraftChange = { onIntent(ChecklistDetailIntent.OnItemTextDraftChange(it)) },
            onConfirmTextEdit = { onIntent(ChecklistDetailIntent.OnConfirmItemTextEdit) },
            onCancelTextEdit = { onIntent(ChecklistDetailIntent.OnCancelItemTextEdit) },
            onReminderClick = { onIntent(ChecklistDetailIntent.OnItemReminderClick(detailsItem.id)) },
            onNoteClick = { onIntent(ChecklistDetailIntent.OnAddNoteClick(detailsItem.id)) },
            onTogglePriority = { onIntent(ChecklistDetailIntent.OnToggleItemPriority(detailsItem.id)) },
            onDelete = { onIntent(ChecklistDetailIntent.OnDeleteItemFromSheet(detailsItem.id)) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissItemDetailsSheet) },
            onAttachmentClick = { id -> onIntent(ChecklistDetailIntent.OnAttachmentClick(id)) },
            onAddImageClick = { onIntent(ChecklistDetailIntent.OnAddImageAttachment(detailsItem.id)) },
            onAddFileClick = { onIntent(ChecklistDetailIntent.OnAddFileAttachment(detailsItem.id)) },
            canAddAttachment = isPremium || detailsItem.attachments.size < ChecklistDetailViewModel.FREE_ATTACHMENT_LIMIT_PER_ITEM,
            // Move to… targets the leaf's TEMPLATE node (folders live on the template). Hidden for
            // legacy fill rows without a template link (nothing to re-parent).
            showMoveAction = state.foldersEnabled && detailsItem.templateItemId != null,
            onMoveClick = {
                detailsItem.templateItemId?.let { onIntent(ChecklistDetailIntent.OnMoveNodeRequested(it)) }
            },
        )
    }

    // ── AttachmentFullscreenViewer ───────────────────────────────────────────────
    val viewerState = state.attachmentViewerState
    if (viewerState != null) {
        val viewerItem = state.defaultFill?.items?.firstOrNull { it.id == viewerState.itemId }
        if (viewerItem != null && viewerItem.attachments.isNotEmpty()) {
            AttachmentFullscreenViewer(
                attachments = viewerItem.attachments,
                initialAttachmentId = viewerState.initialAttachmentId,
                onClose = { onIntent(ChecklistDetailIntent.OnCloseAttachmentViewer) },
                onDelete = { id -> onIntent(ChecklistDetailIntent.OnDeleteAttachment(viewerState.itemId, id)) },
                onOpenExternally = { id -> onIntent(ChecklistDetailIntent.OnOpenAttachmentExternally(id)) },
            )
        } else {
            // Last attachment was deleted — auto-close the viewer.
            LaunchedEffect(Unit) { onIntent(ChecklistDetailIntent.OnCloseAttachmentViewer) }
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

    // ── Folder node actions (Phase 4) ──

    // Folder actions sheet (long-press a FolderCard): Reminder / Rename / Move to… / Delete.
    val folderForActions = state.folderActionsSheetFor?.let { id ->
        state.checklist.items.firstOrNull { it.id == id && it.isFolder }
    }
    if (folderForActions != null) {
        // The folder is always at the current drill-down level when its actions sheet is open, so
        // its UI model (carrying the reminder flag) is present in state.folders.
        val folderHasReminder = state.folders
            .firstOrNull { it.id == folderForActions.id }
            ?.hasReminder == true
        FolderActionsSheet(
            folderName = folderForActions.text,
            hasReminder = folderHasReminder,
            onReminder = { onIntent(ChecklistDetailIntent.OnFolderReminderClick(folderForActions.id)) },
            onMove = { onIntent(ChecklistDetailIntent.OnMoveNodeRequested(folderForActions.id)) },
            onDelete = { onIntent(ChecklistDetailIntent.OnDeleteFolder(folderForActions.id)) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissFolderActions) },
            // Inline rename of the folder name (no separate Rename row / dialog) — mirrors the leaf
            // ItemDetailsSheet text edit. Edit mode is active when the rename draft targets this folder.
            isEditingName = state.folderRenameForId == folderForActions.id,
            editingNameDraft = state.folderRenameDraft,
            onStartNameEdit = { onIntent(ChecklistDetailIntent.OnRenameFolder(folderForActions.id)) },
            onNameDraftChange = { onIntent(ChecklistDetailIntent.OnFolderRenameDraftChange(it)) },
            onConfirmNameEdit = { onIntent(ChecklistDetailIntent.OnConfirmRenameFolder) },
        )
    }

    // Move to… target sheet (folder OR leaf — identified by template node id)
    val moveNodeId = state.moveSheetForNodeId
    if (moveNodeId != null) {
        MoveToFolderSheet(
            targets = state.moveTargets,
            onTargetSelected = { targetFolderId ->
                onIntent(ChecklistDetailIntent.OnMoveNodeToFolder(moveNodeId, targetFolderId))
            },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissMoveSheet) },
        )
    }

    // Delete folder confirmation (cascade warning)
    if (state.pendingFolderDeleteId != null) {
        DeleteFolderConfirmationDialog(
            descendantCount = state.pendingFolderDeleteCount,
            onConfirm = { onIntent(ChecklistDetailIntent.OnConfirmDeleteFolder) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissDeleteFolder) },
        )
    }

    // Disable-folders (flatten) confirmation: shown only when folders exist and the user turns
    // the feature off. Items are kept and lifted to the top level; folder containers are removed.
    if (state.showFlattenFoldersConfirm) {
        DisableFoldersConfirmationDialog(
            onConfirm = { onIntent(ChecklistDetailIntent.OnConfirmDisableFolders) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissDisableFolders) },
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
                showEndConditionPicker = state.showEndConditionPicker,
                isLocked = state.reminderSheetLocked,
                showFullScreenOption = true,
                fullScreenEnabled = state.checklist.reminderFullScreen,
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
                onAddToCalendar = { onIntent(ChecklistDetailIntent.OnAddToCalendar) },
                onDismiss = { onIntent(ChecklistDetailIntent.OnDismissReminderUI) },
                onUpgradeClick = { onIntent(ChecklistDetailIntent.OnReminderUpgradeClick) },
                onFullScreenToggled = { onIntent(ChecklistDetailIntent.OnChecklistReminderFullScreenToggled(it)) },
            )
        )
    }

    // Per-item reminder sheet (reuses ReminderSheet with item-scoped callbacks)
    val itemReminderItem = state.itemReminderSheetFor?.let { id ->
        state.defaultFill?.items?.firstOrNull { it.id == id }
    }
    if (state.itemReminderSheetFor != null) {
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = state.activeItemReminderTab,
                currentReminder = itemReminderItem?.reminderAt,
                currentRepeatRule = itemReminderItem?.repeatRule,
                // Intentionally no fallback to raw enum name (e.g. "DAILY"):
                // prefer hiding CurrentRepeatCard entirely over showing meaningless
                // text. The card has a `summary != null` guard.
                repeatRuleSummary = state.repeatRuleSummary,
                pendingRepeatConfig = state.pendingRepeatConfig,
                showEndConditionPicker = state.showEndConditionPicker,
                isLocked = state.itemReminderSheetLocked,
                showFullScreenOption = true,
                fullScreenEnabled = state.itemReminderFullScreen,
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(ChecklistDetailIntent.OnItemReminderTabSelected(it)) },
                onPresetSelected = { triggerAt ->
                    if (itemReminderItem != null) {
                        onIntent(ChecklistDetailIntent.OnSaveItemReminder(
                            itemReminderItem.id, triggerAt, null, null, state.itemReminderFullScreen
                        ))
                    }
                },
                onCustomDateRequested = { onIntent(ChecklistDetailIntent.OnCustomDateRequested) },
                onRemoveReminder = {
                    if (itemReminderItem != null) {
                        onIntent(ChecklistDetailIntent.OnRemoveItemReminder(itemReminderItem.id))
                    }
                },
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
                    if (config != null && itemReminderItem != null) {
                        val rule = config.toRule()
                        val timeMinutes = config.timeHour * 60 + config.timeMinute
                        onIntent(ChecklistDetailIntent.OnSaveItemReminder(
                            itemReminderItem.id, null, rule, timeMinutes, state.itemReminderFullScreen
                        ))
                    }
                },
                onRemoveRepeat = {
                    if (itemReminderItem != null) {
                        onIntent(ChecklistDetailIntent.OnRemoveItemReminder(itemReminderItem.id))
                    }
                },
                onAddToCalendar = {
                    if (itemReminderItem != null) {
                        onIntent(ChecklistDetailIntent.OnAddItemToCalendar(itemReminderItem.id))
                    }
                },
                onFullScreenToggled = { onIntent(ChecklistDetailIntent.OnItemReminderFullScreenToggled(it)) },
                onDismiss = { onIntent(ChecklistDetailIntent.OnDismissItemReminderSheet) },
                onUpgradeClick = { onIntent(ChecklistDetailIntent.OnItemReminderUpgradeClick) },
            )
        )
    }

    // Item-create repeat / reminder sheet (opened by the "🔁 Repeat" chip in item-create mode).
    // Reuses the shared ReminderSheet; Save stages the repeat onto the item-create chip state instead
    // of persisting to an existing item. The ONCE tab stays functional (one-shot reminder for the new
    // item) so neither tab is dead.
    if (state.itemCreateRepeatSheetOpen) {
        ReminderSheet(
            state = ReminderSheetState(
                activeTab = state.activeReminderTab,
                currentReminder = state.itemCreateReminderAt,
                currentRepeatRule = state.itemCreateRepeat?.toRule(),
                repeatRuleSummary = state.repeatRuleSummary,
                pendingRepeatConfig = state.pendingRepeatConfig,
                showEndConditionPicker = state.showEndConditionPicker,
                isLocked = state.itemCreateRepeatSheetLocked,
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(ChecklistDetailIntent.OnItemCreateRepeatTabSelected(it)) },
                onPresetSelected = { triggerAt -> onIntent(ChecklistDetailIntent.OnItemCreateReminderSet(triggerAt)) },
                onCustomDateRequested = { onIntent(ChecklistDetailIntent.OnItemCreateReminderPickRequested) },
                onRemoveReminder = { onIntent(ChecklistDetailIntent.OnItemCreateReminderSet(null)) },
                onRepeatTypeSelected = { onIntent(ChecklistDetailIntent.OnRepeatTypeSelected(it)) },
                onSmartPresetSelected = { onIntent(ChecklistDetailIntent.OnSmartPresetSelected(it)) },
                onRepeatIntervalChanged = { onIntent(ChecklistDetailIntent.OnRepeatIntervalChanged(it)) },
                onWeekDayToggled = { onIntent(ChecklistDetailIntent.OnWeekDayToggled(it)) },
                onResetChecksToggled = { onIntent(ChecklistDetailIntent.OnResetChecksToggled(it)) },
                onRepeatTimeChanged = { h, m -> onIntent(ChecklistDetailIntent.OnRepeatTimeChanged(h, m)) },
                onEndConditionClick = { onIntent(ChecklistDetailIntent.OnEndConditionClick) },
                onEndConditionSelected = { onIntent(ChecklistDetailIntent.OnEndConditionSelected(it)) },
                onDismissEndCondition = { onIntent(ChecklistDetailIntent.OnDismissEndConditionPicker) },
                onSaveRepeat = { onIntent(ChecklistDetailIntent.OnItemCreateRepeatSaved) },
                onRemoveRepeat = { onIntent(ChecklistDetailIntent.OnItemCreateRepeatRemoved) },
                onDismiss = { onIntent(ChecklistDetailIntent.OnDismissItemCreateRepeatSheet) },
                onUpgradeClick = { onIntent(ChecklistDetailIntent.OnReminderUpgradeClick) },
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

    // Full-screen-intent (alarm-style) permission instruction sheet
    if (state.showFullScreenIntentSheet) {
        FullScreenIntentInstructionSheet(
            dontShowAgain = state.fsiDontShowAgain,
            onDontShowAgainChanged = { onIntent(ChecklistDetailIntent.OnFsiDontShowChanged(it)) },
            onOpenSettings = { onIntent(ChecklistDetailIntent.OnFsiOpenSettings) },
            onSkip = { onIntent(ChecklistDetailIntent.OnFsiSkip) },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissFsiSheet) }
        )
    }

    // Overflow menu bottom sheet
    if (state.showOverflowSheet) {
        OverflowMenuSheet(
            separateCompleted = state.separateCompleted,
            autoDeleteCompleted = state.autoDeleteCompleted,
            hasCompletedItems = state.defaultFill?.items?.any { it.checked } == true,
            foldersEnabled = state.foldersEnabled,
            insideFolder = state.currentFolderId != null,
            isWeeklyMode = state.checklist.viewMode == ChecklistViewMode.Weekly,
            onEditClick = {
                // Edit moved here from the toolbar — close this sheet, then navigate to edit.
                onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
                onIntent(ChecklistDetailIntent.OnEditChecklistClick)
            },
            onFillClick = {
                // Fill moved here from the toolbar — close this sheet, then open the Fill options sheet.
                onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
                showFillSheet = true
            },
            // v2 only: Share left the toolbar to keep the bar at three icons. Null in control, where
            // the toolbar button is still there.
            onShareClick = if (useInlineAddRow) {
                {
                    // Dismiss first, like Edit and Fill: OnShareClick navigates away, and a sheet
                    // left open behind the navigation is still there on the way back.
                    onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
                    onIntent(ChecklistDetailIntent.OnShareClick)
                }
            } else {
                null
            },
            onCreateFolder = {
                onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
                onIntent(ChecklistDetailIntent.OnCreateFolder)
            },
            onToggleFoldersEnabled = { onIntent(ChecklistDetailIntent.OnToggleFoldersEnabled) },
            onDeleteCompletedItems = { onIntent(ChecklistDetailIntent.OnDeleteCompletedItems) },
            onToggleAutoDeleteCompleted = { onIntent(ChecklistDetailIntent.OnToggleAutoDeleteCompleted) },
            onToggleSeparateCompleted = { onIntent(ChecklistDetailIntent.OnToggleSeparateCompleted) },
            onDeleteClick = {
                onIntent(ChecklistDetailIntent.OnDismissOverflowSheet)
                // Inside a folder the destructive row targets that folder (cascade-confirm dialog
                // + auto-pop of the deleted level are already handled by OnDeleteFolder).
                val folderId = state.currentFolderId
                if (folderId != null) {
                    onIntent(ChecklistDetailIntent.OnDeleteFolder(folderId))
                } else {
                    onIntent(ChecklistDetailIntent.OnDeleteChecklistClick)
                }
            },
            onDismiss = { onIntent(ChecklistDetailIntent.OnDismissOverflowSheet) }
        )
    }

    // Fill options sheet — opened from the "Fill checklist" row in the settings sheet.
    // Hosts the two Fill actions (manual / via AI) that previously lived in the bottom bar.
    if (showFillSheet) {
        FillOptionsSheet(
            onFillManually = {
                showFillSheet = false
                onIntent(ChecklistDetailIntent.OnAddFillClick)
            },
            onFillViaAi = {
                showFillSheet = false
                onIntent(ChecklistDetailIntent.OnAddFillViaAiClick)
            },
            onDismiss = { showFillSheet = false },
            title = stringResource(Res.string.fill_options_title),
            fillManuallyLabel = stringResource(Res.string.fill_manually_title),
            fillManuallyDescription = stringResource(Res.string.fill_manually_desc),
            fillViaAiLabel = stringResource(Res.string.fill_via_ai_title),
            fillViaAiDescription = stringResource(Res.string.fill_via_ai_desc),
        )
    }
}

/**
 * Retention: quiet, one-time inline suggestion offering to make this checklist repeat weekly. Kept
 * deliberately low-key (a suggestion, not an alarm): a hairline [AppCard] with a small repeat tile,
 * a title/subtitle, a dismiss "x", and an outlined (secondary) accept button. Visibility + show-once
 * persistence are owned by [ChecklistDetailViewModel]; this is a pure renderer.
 */
@Composable
private fun RecurringNudgeCard(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier,
        contentPadding = PaddingValues(AppDimens.SpacingMd),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.recurring_nudge_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.recurring_nudge_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.recurring_nudge_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppButtonSecondary(
                text = stringResource(Res.string.recurring_nudge_accept),
                onClick = onAccept,
                icon = Icons.Outlined.Repeat,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProgressHeader(items: List<ChecklistFillItem>, name: String) {
    val checkedCount = items.count { it.checked }
    val totalCount = items.size
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
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = AppDimens.SpacingMd),
            )
            Text(
                text = "$checkedCount / $totalCount",
                style = MaterialTheme.typography.titleMedium,
                color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        AppLinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
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

    // SwipeToDismissBox stacks backgroundContent behind content unconditionally, so the slab is
    // hidden only while the card fully covers it — the completion scale-pop undershoots below 1f
    // on its bouncy settle and flashes the red slab on a plain check. dismissDirection reads the
    // drag offset (not targetValue), so the slab still appears from the first pixel of a swipe.
    val isSwiping by remember {
        derivedStateOf { dismissState.dismissDirection != SwipeToDismissBoxValue.Settled }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { if (isSwiping) SwipeDeleteBackground() },
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
    isHighlighted: Boolean = false,
    cardDragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    // Highlight animation: flash primaryContainer for ~1 s when navigated from Calendar.
    // animateColorAsState provides a smooth fade-in/out over 280 ms on both edges.
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
        animationSpec = tween(durationMillis = 280),
        label = "item_highlight",
    )

    // Light completion flourish: a subtle one-shot scale "pop" when the item transitions
    // unchecked -> checked. Guarded by prevChecked so it fires ONLY on a real check (not on
    // first composition, and not when an already-checked card is re-bound while scrolling —
    // LaunchedEffect(item.checked) alone would re-pop on rebind). graphicsLayer scale is
    // draw-only, so it never reflows the list (no placement jump).
    var prevChecked by remember(item.id) { mutableStateOf(item.checked) }
    val completionScale = remember(item.id) { Animatable(1f) }
    LaunchedEffect(item.id, item.checked) {
        if (item.checked && !prevChecked) {
            completionScale.snapTo(1f)
            completionScale.animateTo(1.06f, tween(durationMillis = 110))
            completionScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        prevChecked = item.checked
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            if (isEditMode && !isDragging) {
                rotationZ = wiggleAngle
            }
            scaleX = completionScale.value
            scaleY = completionScale.value
        }
        .then(if (isEditMode) cardDragModifier else Modifier)

    val cardContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            // ── Highlight overlay: drawn first (behind content), fades in/out ──
            if (highlightColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(highlightColor)
                )
            }
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
                    // Item text. If the whole line is a single URL, show a compact read-only
                    // "🔗 domain" tag instead of the long raw URL; if a URL sits among words,
                    // render it inline as a colored domain token (read-only — opening is offered
                    // in ItemDetailsSheet, never on the card per the 30/70 hit-zone rule).
                    val textWholeUrl = item.text.asWholeUrl()
                    val textStartPadding =
                        Modifier.padding(start = if (isEditMode) AppDimens.SpacingSm else 0.dp)
                    if (textWholeUrl != null) {
                        AppItemMetaChip(
                            icon = Icons.Filled.Link,
                            label = displayDomain(textWholeUrl),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = textStartPadding,
                        )
                    } else {
                        Text(
                            text = rememberLinkifiedText(
                                raw = item.text,
                                linkColor = MaterialTheme.colorScheme.primary,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.checked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                            modifier = Modifier.fillMaxWidth().then(textStartPadding)
                        )
                    }

                    if (!isEditMode) {
                        item.note?.let { note ->
                            val noteWholeUrl = note.asWholeUrl()
                            if (noteWholeUrl != null) {
                                AppItemMetaChip(
                                    icon = Icons.Filled.Link,
                                    label = displayDomain(noteWholeUrl),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            } else {
                                Text(
                                    text = rememberLinkifiedText(
                                        raw = note,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (!isEditMode) {
                        ItemMetaRow(item = item)
                    }
                }
                // Priority is now shown as a chip inside ItemMetaRow (below item text).
                // The star Icon here is intentionally removed to avoid duplication.
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
                            .combinedClickable(
                                interactionSource = tapInteractionSource,
                                indication = null,
                                onClick = onItemTap,
                                onLongClick = onLongClick
                            )
                    )
                }
            }
        }
    }

    // Shared "filled + hairline" card in both themes (see AppCardDefaults). "Lifted while dragging"
    // is shown by an accent ring — the border animates to primary at 2dp — NOT by a shadow, so there
    // is no side-ear artifact and the visual is identical on Android and Web.
    val borderColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "border_color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isDragging) 2.dp else 1.dp,
        label = "border_width"
    )
    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = AppCardDefaults.colors(),
        border = BorderStroke(borderWidth, borderColor),
        elevation = AppCardDefaults.flatElevation()
    ) { cardContent() }
}

/**
 * Things-style detail sheet for a checklist item: editable name plus one row per per-item action —
 * Reminder, Note, Open link (one per URL in the text/note), Attachments, Priority, Move, Delete.
 *
 * Hosted by TWO screens. The checklist detail screen opens it from a `ChecklistItemCard`'s right 70%;
 * the v2 Inbox tab opens it from a task row and adds the [showMoveToProjectAction] row. It stays one
 * composable on purpose — the Inbox previously carried a four-row copy of it, and the copy read to
 * the user as the Inbox having lost reminders, notes and attachments.
 *
 * Every host-specific surface (the reminder sheet, the note dialog, the file pickers, the fullscreen
 * attachment viewer) is raised through a callback and rendered by the HOST, so wiring a row here
 * without wiring its surface there produces a dead row. Both hosts wire all of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemDetailsSheet(
    item: ChecklistFillItem,
    isEditingText: Boolean = false,
    editingTextDraft: String = "",
    onStartTextEdit: () -> Unit = {},
    onTextDraftChange: (String) -> Unit = {},
    onConfirmTextEdit: () -> Unit = {},
    onCancelTextEdit: () -> Unit = {},
    onReminderClick: () -> Unit,
    onNoteClick: () -> Unit,
    onTogglePriority: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    // Attachment callbacks — defaulted so existing call sites compile unchanged
    onAttachmentClick: (attachmentId: String) -> Unit = {},
    onAddImageClick: () -> Unit = {},
    onAddFileClick: () -> Unit = {},
    canAddAttachment: Boolean = true,
    // Move to… — only shown when the checklist has folders enabled (Phase 4). Defaulted off so
    // existing call sites / tests compile unchanged.
    showMoveAction: Boolean = false,
    onMoveClick: () -> Unit = {},
    /**
     * Extra action rows contributed by the HOST, rendered between Move and Delete.
     *
     * A slot rather than one more `showX`/`onX` pair per host row: the Inbox adds two ("Move to
     * project", "Open project"), a third host would add its own, and each pair widens this signature
     * for every call site that will never use it. Build the rows with [ItemDetailsSheetRow] — it is
     * `internal` for exactly this — so a host row is visually identical to a built-in one instead of
     * being a look-alike copy.
     */
    hostActions: (@Composable () -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val logger = koinInject<AppLogger>()
    // URLs found in the item text + note (deduped). Each gets its own "Open link" action row.
    val itemUrls = remember(item.text, item.note) {
        (extractUrls(item.text) + extractUrls(item.note)).distinct()
    }

    // Compact → ModalBottomSheet (phone / narrow web); wider → AlertDialog (desktop web / tablet).
    val isDialog = rememberAppWindowSizeClass() != AppWindowSizeClass.Compact

    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        // No small title: the editable headline below is the only item name. On Expanded/web the
        // AlertDialog would otherwise also render this title, duplicating the name (user-reported;
        // same fix already applied to FolderActionsSheet).
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrollable because the row set is not fixed: URLs add one row each, and RU/HI
                // titles are 30-50% longer than the English they were laid out against, so the tall
                // case (reminder + note + two links + attachments + priority + both moves + delete)
                // overflows a short phone. Without this the Delete row simply cannot be reached.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                // The bottom sheet sits flush to the screen edge → keep full breathing room.
                // The AlertDialog already adds Material's own bottom padding; stacking ours on top
                // is what produced the oversized gap on web, so drop it in the dialog branch.
                .padding(bottom = if (isDialog) 0.dp else AppDimens.SpacingXxl)
        ) {
            // Item name as sheet title — tap to enter inline edit mode
            if (isEditingText) {
                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                var hasGainedFocus by remember { mutableStateOf(false) }
                val canSave = remember(editingTextDraft, item.text) {
                    val trimmed = editingTextDraft.trim()
                    trimmed.isNotEmpty() && trimmed != item.text.trim()
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }

                // Header: Save action top-right, independent of title width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppButtonText(
                        text = stringResource(Res.string.save),
                        onClick = onConfirmTextEdit,
                        enabled = canSave,
                    )
                }

                BasicTextField(
                    value = editingTextDraft,
                    onValueChange = onTextDraftChange,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.SpacingMd)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus) {
                                onConfirmTextEdit()
                            }
                        }
                )
            } else {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDimens.MinTouchTarget)
                        .clickable(onClick = onStartTextEdit)
                        .padding(bottom = AppDimens.SpacingMd)
                )
            }

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
            // If the note is just a pasted URL, show its domain instead of the long raw link.
            val noteSubtitle = item.note?.let { note -> note.asWholeUrl()?.let(::displayDomain) ?: note }
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

            // ── Open link row(s) — one per URL detected in the text/note ──
            // openUri is called synchronously from onClick (NOT a coroutine) so the web build
            // doesn't get the window.open blocked as a popup. runCatching guards a malformed URL.
            val openLinkTitle = stringResource(Res.string.detail_item_sheet_action_open_link)
            itemUrls.forEach { url ->
                ItemDetailsSheetRow(
                    icon = Icons.Filled.Link,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = openLinkTitle,
                    subtitle = displayDomain(url),
                    showChevron = true,
                    onClick = {
                        runCatching { uriHandler.openUri(url) }
                            .onFailure { logger.warning("OpenLink", "openUri failed for $url: ${it.message}") }
                    },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Attachments section (Android only — gated via PlatformCapabilities) ──
            if (PlatformCapabilities.attachmentsSupported) {
                val attachmentCount = item.attachments.size
                val attachmentsSubtitle = when (attachmentCount) {
                    0 -> stringResource(Res.string.detail_item_sheet_subtitle_no_attachments)
                    1 -> stringResource(Res.string.detail_item_sheet_subtitle_attachment_count_one, 1)
                    else -> stringResource(Res.string.detail_item_sheet_subtitle_attachment_count_other, attachmentCount)
                }
                val attachmentsIconTint = if (attachmentCount > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant

                // Row header — static, no chevron; thumbnails expand inline below
                ItemDetailsSheetRow(
                    icon = if (attachmentCount > 0)
                        Icons.Filled.AttachFile
                    else
                        Icons.Outlined.AttachFile,
                    iconTint = attachmentsIconTint,
                    title = stringResource(Res.string.detail_item_sheet_action_attachments),
                    subtitle = attachmentsSubtitle,
                    showChevron = false,
                    // Tapping the row header opens the any-file/photo picker (same as the "+File" tile),
                    // so the whole "Attachments — tap to add" row is an add affordance, not just the tiles.
                    onClick = onAddFileClick,
                )

                // Inline thumbnails below — not wrapped in ItemDetailsSheetRow.
                // The parent Column has horizontal padding = ScreenPaddingHorizontal (16dp).
                // AttachmentsThumbnailRow uses zero horizontal contentPadding so tiles align
                // with the left edge of the other rows inside the same Column.
                AttachmentsThumbnailRow(
                    attachments = item.attachments,
                    onAttachmentClick = onAttachmentClick,
                    onAddImageClick = onAddImageClick,
                    onAddFileClick = onAddFileClick,
                    canAddMore = canAddAttachment,
                    modifier = Modifier.padding(top = AppDimens.SpacingSm, bottom = AppDimens.SpacingMd),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Priority row ──
            val isImportant = item.priority > 0
            val priorityTitle = stringResource(
                if (isImportant) Res.string.item_priority_unmark
                else Res.string.item_priority_mark
            )
            val priorityIconTint = if (isImportant)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant

            ItemDetailsSheetRow(
                icon = if (isImportant) Icons.Filled.Star else Icons.Outlined.Star,
                iconTint = priorityIconTint,
                title = priorityTitle,
                subtitle = null,
                showChevron = false,
                onClick = onTogglePriority,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Move to… row (folder mode only) ──
            if (showMoveAction) {
                ItemDetailsSheetRow(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(Res.string.folder_move),
                    subtitle = null,
                    showChevron = true,
                    onClick = onMoveClick,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Host-contributed rows (Inbox: "Move to project", "Open project") ──
            hostActions?.invoke()

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

/**
 * One action row of [ItemDetailsSheet]. `internal` so a host filling the `hostActions` slot renders
 * rows identical to the built-in ones — the v2 Inbox previously kept a private look-alike copy of
 * this that had neither [subtitle] nor [showChevron], which is why its sheet could not even show
 * "Reminder — tomorrow 09:00".
 */
@Composable
internal fun ItemDetailsSheetRow(
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
 * Renders a horizontal row of 0–3 read-only meta chips below the item text:
 * priority (⭐), reminder (🔔), and attachments (📎), in that fixed order.
 *
 * Design decisions:
 * - Uses [AppItemMetaChip] (core:designsystem, 6th App* component).
 * - Row, NOT FlowRow — prevents wrapping to 2–3 lines on compact screens.
 *   Reminder label is width-capped at 140dp with ellipsis to protect layout on
 *   very narrow devices (320dp - 30% checkbox ≈ 224dp usable).
 * - Only rendered when at least one chip is active; zero chips = invisible (no spacer).
 * - Reminder uses [formatItemReminderLabel] (already Composable, handles missed/repeat).
 *   Missed reminder uses errorContainer/onErrorContainer for visual urgency.
 * - No clickable / indication on any chip — toggling is handled by ItemDetailsSheet.
 * - Priority is shown here as a chip; the old right-aligned Star Icon is removed.
 */
@Composable
private fun ItemMetaRow(
    item: ChecklistFillItem,
    modifier: Modifier = Modifier,
) {
    val hasPriority = item.priority > 0
    val hasReminder = item.hasActiveReminder
    val hasAttachments = item.attachments.isNotEmpty()

    if (!hasPriority && !hasReminder && !hasAttachments) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasPriority) {
            AppItemMetaChip(
                icon = Icons.Filled.Star,
                label = stringResource(Res.string.item_chip_priority),
                // GistiColors.star, not tertiaryContainer. Priority was drawn in three different
                // colours across the product — teal here, blue on the Inbox row, and the gold token
                // literally named for it used nowhere — so the same meaning read as three things.
                // One meaning, one colour, and it is the one the token exists for.
                //
                // The gold arrives as a WASH rather than as the label colour: #F4A923 is a graphic
                // accent (it is drawn solid on the row's 3dp priority bar and on a filled star
                // glyph), and as text on a light surface it sits near 2:1 — unreadable. The label
                // therefore keeps onSurface, and the container carries the identity.
                containerColor = GistiColors.star.copy(alpha = PriorityChipContainerAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (hasReminder) {
            val reminderMissed = isReminderMissed(item)
            val reminderLabel = formatItemReminderLabel(item)
            AppItemMetaChip(
                icon = Icons.Filled.Notifications,
                label = reminderLabel,
                containerColor = if (reminderMissed)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (reminderMissed)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }

        if (hasAttachments) {
            AppItemMetaChip(
                icon = Icons.Filled.AttachFile,
                label = item.attachments.size.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Opacity of the gold wash behind the priority chip's label.
 *
 * Low enough that the label keeps its normal `onSurface` contrast on the card, high enough that the
 * chip reads as gold rather than as one more neutral tag next to the reminder and attachment chips.
 */
private const val PriorityChipContainerAlpha = 0.18f

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
 * - No reminder      → "Remind"
 * - One-shot, future → "May 5, 18:00"
 * - One-shot, past   → "Missed (May 5)"
 * - Recurring        → compact rule text, e.g. "Daily 09:00" / "Weekly Mon 18:00"
 *
 * ## Why this delegates
 * This function used to write those labels as Kotlin literals — "Daily", "Weekly", "Mon",
 * "Missed (May 5)" — which is one language shipped to every locale. The words now come from
 * [DueLabelStyle.Absolute] in the shared [dueLabelSpec] formatter, so this screen and the task
 * row's due chip resolve the same fields through the same clock and the same string table.
 *
 * The rendered result is unchanged in English, deliberately: this screen is not being redesigned
 * here, it is only being made translatable. The chip's shorter, relative vocabulary
 * ("Today 18:00") lives behind [DueLabelStyle.Relative].
 */
@Composable
internal fun formatItemReminderLabel(item: ChecklistFillItem): String {
    val spec = item.dueLabelSpec(
        nowMillis = currentTimeMillis(),
        style = DueLabelStyle.Absolute,
    ) ?: return stringResource(Res.string.detail_item_action_remind)

    return spec.label()
}

@Composable
// internal, not private: the v2 Inbox hosts the same ItemDetailsSheet and its Note row has to open
// the same dialog. A second copy would drift (one already exists at FillDetailScreen.kt).
internal fun NoteDialog(
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

@Composable
private fun FillTargetBottomSheet(
    onFillMainChecklist: () -> Unit,
    onCreateNewFill: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.fill_target_title)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {

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

/**
 * Actions sheet for a FOLDER node (opened by long-pressing a [FolderCard]): Reminder, Rename,
 * Move to…, and Delete. Mirrors the leaf [ItemDetailsSheet] layout (same [ItemDetailsSheetRow] rows
 * and [AdaptiveSheetOrDialog] container) so folders feel consistent with items.
 *
 * The Reminder row reuses the leaf per-item reminder flow end-to-end: tapping it opens the shared
 * [ReminderSheet] scoped to the folder's fill row (resolved in the ViewModel), so there is no
 * duplicate reminder UI.
 *
 * The folder name doubles as an inline-editable title — tapping it swaps the headline for a
 * [BasicTextField] with autofocus + a Save action, mirroring the leaf [ItemDetailsSheet] text edit
 * (so there is no separate "Rename" row / dialog). Confirm-on-blur keeps the gesture forgiving.
 *
 * @param folderName  Current folder name (sheet title + inline-edit headline).
 * @param isEditingName Whether the headline is currently in inline-edit mode.
 * @param editingNameDraft Draft text shown in the inline editor (owned by the ViewModel).
 * @param onStartNameEdit Tap the headline → enter inline-edit mode (keeps the sheet open).
 * @param onNameDraftChange Inline editor keystroke.
 * @param onConfirmNameEdit Commit the rename (Save / blur).
 * @param hasReminder Whether the folder currently has an active reminder (drives the row's icon +
 *                    subtitle, exactly like the leaf reminder row).
 * @param onReminder  Open the reminder sheet for this folder.
 * @param onMove      Open the "Move to…" target sheet for this folder.
 * @param onDelete    Request a (cascading) folder delete → confirm dialog.
 */
// internal (not private) so the Roborazzi screenshot test in androidHostTest can render it in
// isolation — see feature/home/src/androidHostTest/.../FolderComponentsScreenshotTest.kt.
@Composable
internal fun FolderActionsSheet(
    folderName: String,
    hasReminder: Boolean,
    onReminder: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    isEditingName: Boolean = false,
    editingNameDraft: String = "",
    onStartNameEdit: () -> Unit = {},
    onNameDraftChange: (String) -> Unit = {},
    onConfirmNameEdit: () -> Unit = {},
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        // No small title: the editable headline below is the only folder name. On Expanded/web the
        // AlertDialog would otherwise also render this title, duplicating the name (user-reported).
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl)
        ) {
            // Folder name as the sheet title — tap to rename inline (same pattern as the leaf
            // ItemDetailsSheet), so there is no separate "Rename" row.
            if (isEditingName) {
                val focusRequester = remember { FocusRequester() }
                val keyboardController = LocalSoftwareKeyboardController.current
                var hasGainedFocus by remember { mutableStateOf(false) }
                val canSave = remember(editingNameDraft, folderName) {
                    val trimmed = editingNameDraft.trim()
                    trimmed.isNotEmpty() && trimmed != folderName.trim()
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }

                // Header: Save action top-right, independent of title width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppButtonText(
                        text = stringResource(Res.string.save),
                        onClick = onConfirmNameEdit,
                        enabled = canSave,
                    )
                }

                BasicTextField(
                    value = editingNameDraft,
                    onValueChange = onNameDraftChange,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.SpacingMd)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus) {
                                onConfirmNameEdit()
                            }
                        }
                )
            } else {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppDimens.MinTouchTarget)
                        .clickable(onClick = onStartNameEdit)
                        .padding(bottom = AppDimens.SpacingMd)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Reminder row — reuses the leaf reminder presentation (Notifications icon + status
            // subtitle) and the leaf reminder flow underneath.
            ItemDetailsSheetRow(
                icon = if (hasReminder) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                iconTint = if (hasReminder) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                title = stringResource(Res.string.folder_reminder),
                subtitle = stringResource(
                    if (hasReminder) Res.string.folder_reminder_active
                    else Res.string.detail_item_sheet_subtitle_no_reminder
                ),
                showChevron = true,
                onClick = onReminder,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ItemDetailsSheetRow(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = stringResource(Res.string.folder_move),
                subtitle = null,
                showChevron = true,
                onClick = onMove,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            ItemDetailsSheetRow(
                icon = Icons.Outlined.Delete,
                iconTint = MaterialTheme.colorScheme.error,
                title = stringResource(Res.string.folder_delete),
                subtitle = null,
                showChevron = false,
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

/**
 * Confirm dialog for deleting a folder. The message scales with the cascade size
 * ([descendantCount]): an empty folder gets a plain prompt; a non-empty one warns how many
 * nested items will go with it.
 */
// internal (not private) so the Roborazzi screenshot test in androidHostTest can render it.
@Composable
internal fun DeleteFolderConfirmationDialog(
    descendantCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when (descendantCount) {
        0 -> stringResource(Res.string.folder_delete_message_empty)
        1 -> stringResource(Res.string.folder_delete_message_one)
        else -> stringResource(Res.string.folder_delete_message_other, descendantCount)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.folder_delete_title)) },
        text = { Text(message) },
        confirmButton = {
            AppButtonDestructive(
                text = stringResource(Res.string.delete),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            AppButtonText(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

/**
 * Confirmation shown before disabling folders on a checklist that still has folder nodes.
 * Flatten is non-destructive to items (they move to the top level) but removes the folders, so we
 * warn first. The confirm button is a normal (not destructive) action since the items are kept.
 */
// internal (not private) so the Roborazzi screenshot test in androidHostTest can render it.
@Composable
internal fun DisableFoldersConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.folders_disable_title)) },
        text = { Text(stringResource(Res.string.folders_disable_message)) },
        confirmButton = {
            AppButton(
                text = stringResource(Res.string.folders_disable_confirm),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            AppButtonText(
                text = stringResource(Res.string.folders_disable_cancel),
                onClick = onDismiss,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
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

@Composable
// internal: the Inbox item sheet runs the same pre-flight permission check before it schedules an
// alarm, and skipping it there would arm reminders the system silently drops.
internal fun NotificationPermissionSheet(
    onEnableClick: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.reminder_notification_permission_title)) },
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

@Composable
private fun FullScreenIntentInstructionSheet(
    dontShowAgain: Boolean,
    onDontShowAgainChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.reminder_fsi_title)) },
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

            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

            // Steps
            StepRow(number = 1, text = stringResource(Res.string.reminder_fsi_step1))
            StepRow(number = 2, text = stringResource(Res.string.reminder_fsi_step2))
            StepRow(number = 3, text = stringResource(Res.string.reminder_fsi_step3))

            // Description
            Text(
                text = stringResource(Res.string.reminder_fsi_description),
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
                    text = stringResource(Res.string.reminder_fsi_dont_show),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Buttons
            AppButton(
                text = stringResource(Res.string.reminder_fsi_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
            AppButtonText(
                text = stringResource(Res.string.reminder_fsi_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExactAlarmInstructionSheet(
    dontShowAgain: Boolean,
    onDontShowAgainChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.reminder_exact_alarm_title)) },
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

@Composable
private fun OverflowMenuSheet(
    separateCompleted: Boolean,
    autoDeleteCompleted: Boolean,
    hasCompletedItems: Boolean,
    foldersEnabled: Boolean,
    // True while drilled into a folder (state.currentFolderId != null). Retargets the destructive
    // row from the whole checklist to THIS folder — deleting the checklist from inside one of its
    // folders reads as a mis-tap, and the folder is what the user is looking at.
    insideFolder: Boolean,
    isWeeklyMode: Boolean,
    onEditClick: () -> Unit,
    onFillClick: () -> Unit,
    /**
     * Share the checklist. Non-null only in the v2 arm, where the toolbar was trimmed to three icons
     * and Share moved in here; null in control leaves the sheet exactly as it was (Share still has
     * its own toolbar button there, so a row would be a duplicate).
     */
    onShareClick: (() -> Unit)? = null,
    onCreateFolder: () -> Unit,
    onToggleFoldersEnabled: () -> Unit,
    onDeleteCompletedItems: () -> Unit,
    onToggleAutoDeleteCompleted: () -> Unit,
    onToggleSeparateCompleted: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(Res.string.more_options)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl)
        ) {
            // Edit checklist — navigates to the edit screen. Moved here from the toolbar so the bar
            // stays at three primary actions (add / reminder / share + overflow).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditClick() }
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.checklist_edit_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider()

            // Fill checklist — opens the Fill options sheet (manual / via AI). Moved here from the
            // toolbar; toggles the local showFillSheet flag at the call site.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFillClick() }
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.NoteAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.fill_options_open),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider()

            // Share — v2 only (see [onShareClick]). Third, right after the two other "do something
            // with this whole checklist" rows and above the structural toggles.
            if (onShareClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShareClick() }
                        .padding(vertical = AppDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                    Text(
                        text = stringResource(Res.string.share),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                HorizontalDivider()
            }

            // Folders master toggle. Mutually exclusive with Weekly view: disabled (greyed, with a
            // hint) while the checklist is in Weekly mode, since both are alternative groupings of
            // the same flat list.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isWeeklyMode) Modifier else Modifier.clickable { onToggleFoldersEnabled() }
                    )
                    .padding(vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (isWeeklyMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.folders_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isWeeklyMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isWeeklyMode) {
                        Text(
                            text = stringResource(Res.string.folders_toggle_weekly_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AppSwitch(
                    checked = foldersEnabled,
                    onCheckedChange = null,
                    enabled = !isWeeklyMode,
                )
            }

            HorizontalDivider()

            // New Folder (folder mode only) — creates a folder at the current drill-down level.
            if (foldersEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateFolder() }
                        .padding(vertical = AppDimens.SpacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                    Text(
                        text = stringResource(Res.string.folder_create),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                HorizontalDivider()
            }

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

            // Delete checklist — or, while drilled into a folder, delete that folder instead.
            // The call site branches on the same insideFolder flag, so label and action can't
            // drift apart.
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
                    text = stringResource(
                        if (insideFolder) Res.string.folder_delete else Res.string.delete_checklist
                    ),
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

/**
 * The selectable chip set shown in the shared chat dock while it is in item-create mode (the
 * checklist-detail "+"). Two groups in one wrapping [GistiSelectableChipRow]: reminder presets
 * (🔔 single-select) and independent property toggles (⭐ Important / 🔁 Repeat). The active
 * reminder/repeat/important come from [ChecklistDetailState.Content].
 *
 * A live Smart-Add token preview is shown above the chips while the typed text contains a date/time
 * AND no reminder chip overrides it (the chip wins over the parsed reminder).
 */
@Composable
private fun ItemCreateChipsRow(
    state: ChecklistDetailState.Content,
    onIntent: (ChecklistDetailIntent) -> Unit,
    /**
     * Edge inset for the token chip and the chip row. Defaults to the screen padding because the
     * dock hosts this with no outer padding of its own; the v2 inline row passes 0.dp since its
     * LazyColumn already applies the screen padding (double padding — rule `ui-card-patterns`).
     */
    horizontalPadding: Dp = AppDimens.ScreenPaddingHorizontal,
) {
    val tz = TimeZone.currentSystemDefault()
    val pickTimeLabel = state.itemCreateReminderAt
        ?.takeIf { state.itemCreateReminderPreset == ItemCreateReminderPreset.CUSTOM }
        ?.let { formatReminderDateTime(Instant.fromEpochMilliseconds(it).toLocalDateTime(tz)) }
        ?: stringResource(Res.string.item_create_chip_pick_time)
    val repeatLabel = state.itemCreateRepeat
        ?.let { buildRepeatSummary(it) }
        ?: stringResource(Res.string.item_create_chip_repeat)
    val selectedReminder = when (state.itemCreateReminderPreset) {
        ItemCreateReminderPreset.ONE_HOUR -> GistiItemCreateAction.REMIND_1H
        ItemCreateReminderPreset.TOMORROW_MORNING -> GistiItemCreateAction.REMIND_TOMORROW_MORNING
        ItemCreateReminderPreset.TONIGHT -> GistiItemCreateAction.REMIND_TONIGHT
        ItemCreateReminderPreset.CUSTOM -> GistiItemCreateAction.REMIND_PICK
        null -> null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val token = state.parsedToken
        if (token != null && state.itemCreateReminderAt == null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(bottom = AppDimens.SpacingSm)
            ) {
                TokenChipPreview(
                    label = resolveChipLabel(token.display),
                    isRepeat = token.display.containsRepeat(),
                )
            }
        }
        GistiSelectableChipRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            chips = gistiItemCreatePromptChips(
                in1HourLabel = stringResource(Res.string.item_create_chip_in_1_hour),
                tomorrowMorningLabel = stringResource(Res.string.item_create_chip_tomorrow_morning),
                tonightLabel = stringResource(Res.string.item_create_chip_tonight),
                pickTimeLabel = pickTimeLabel,
                importantLabel = stringResource(Res.string.item_create_chip_important),
                repeatLabel = repeatLabel,
                selectedReminder = selectedReminder,
                importantSelected = state.itemCreateImportant,
                repeatSelected = state.itemCreateRepeat != null,
            ),
            onChipClick = { action ->
                when (action) {
                    GistiItemCreateAction.REMIND_1H ->
                        onIntent(ChecklistDetailIntent.OnItemCreatePresetSelected(ItemCreateReminderPreset.ONE_HOUR))
                    GistiItemCreateAction.REMIND_TOMORROW_MORNING ->
                        onIntent(ChecklistDetailIntent.OnItemCreatePresetSelected(ItemCreateReminderPreset.TOMORROW_MORNING))
                    GistiItemCreateAction.REMIND_TONIGHT ->
                        onIntent(ChecklistDetailIntent.OnItemCreatePresetSelected(ItemCreateReminderPreset.TONIGHT))
                    GistiItemCreateAction.REMIND_PICK ->
                        onIntent(ChecklistDetailIntent.OnItemCreateReminderPickRequested)
                    GistiItemCreateAction.IMPORTANT ->
                        onIntent(ChecklistDetailIntent.OnItemCreateImportantToggled)
                    GistiItemCreateAction.REPEAT ->
                        onIntent(ChecklistDetailIntent.OnItemCreateRepeatRequested)
                }
            },
        )
    }
}

/**
 * Horizontal preview strip of files staged in item-create mode (BEFORE the item exists), rendered
 * above the create input. Each 64dp tile shows the picked source directly (Coil loads raw picker
 * URIs and opfs:// alike — the proven chat pattern) with a × button to unstage it. Images crop-fill;
 * other types show a file icon. Renders nothing when [pending] is empty.
 */
@Composable
private fun ItemCreateAttachmentStrip(
    pending: List<PendingItemAttachment>,
    onRemove: (sourcePath: String) -> Unit,
    /** See [ItemCreateChipsRow]'s parameter of the same name — 0.dp from the v2 inline row. */
    horizontalPadding: Dp = AppDimens.ScreenPaddingHorizontal,
) {
    if (pending.isEmpty()) return
    val tileShape = RoundedCornerShape(8.dp)
    val removeLabel = stringResource(Res.string.item_create_remove_attachment)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        contentPadding = PaddingValues(vertical = AppDimens.SpacingXs),
    ) {
        items(pending, key = { it.sourcePath }) { att ->
            Box(modifier = Modifier.size(64.dp)) {
                Surface(
                    shape = tileShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (att.mimeType?.startsWith("image/") == true) {
                        AsyncImage(
                            model = att.sourcePath,
                            contentDescription = att.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(tileShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = att.fileName,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(AppDimens.IconSizeMd),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onRemove(att.sourcePath) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = removeLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Todoist-style "+ Add task" row — the v2 replacement for the dock's item-create input.
 *
 * Deliberately dumb: it owns NO create logic. [onSubmit] fires
 * [ChecklistDetailIntent.OnAddItemWithParse], which is the single source of truth for the
 * template+fill dual write, Smart-Add parsing and reminder scheduling. Writing the item here would
 * re-open the template/fill desync bug and skip the Smart-Add parser entirely.
 *
 * Unlike the original pre-dock inline input (removed in 2ac512ed) this row does NOT self-dismiss
 * when the keyboard hides: in v2 it is a permanent affordance at the end of the list, so a closing
 * keyboard must leave it in place. It also never clears its own focus after a send — the ViewModel
 * keeps item-create mode on precisely so the user can add several tasks in a row
 * (asserted by `ChecklistDetailSmartAddTest`).
 *
 * @param canSend false while the input is blank → both submit paths are inert, so a blank item can
 *   never be created.
 * @param focusRequester lets the toolbar "+" put the caret here (there is no dock left to expand).
 * @param onFocusChanged drives item-create mode: focus opens it (revealing the chips), blur closes
 *   it only when the draft is empty.
 */
@Composable
private fun InlineAddItemInput(
    text: String,
    canSend: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAttachClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        AppTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = stringResource(Res.string.detail_inline_add_placeholder),
            singleLine = false,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSend) onSubmit() }),
        )
        IconButton(onClick = onAttachClick) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                // Reuses the existing attachment label rather than minting a v2-only key: the
                // contract's string list has none for this button, and inventing one would drift
                // in translation against the identical action elsewhere in this screen.
                contentDescription = stringResource(Res.string.attachment_add_file_button),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSubmit, enabled = canSend) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(Res.string.add_item),
                tint = if (canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private val WHITESPACE_RUN_REGEX = Regex("""\s+""")

/**
 * Normalizes input for smart-add hint dismissal comparison.
 *
 * Collapses consecutive whitespace to a single space and trims, so that
 * whitespace-only edits (e.g. trailing space, internal double space) do
 * not look like meaningful content changes from the parser's perspective.
 */
private fun String.normalizeForHintComparison(): String =
    trim().replace(WHITESPACE_RUN_REGEX, " ")
