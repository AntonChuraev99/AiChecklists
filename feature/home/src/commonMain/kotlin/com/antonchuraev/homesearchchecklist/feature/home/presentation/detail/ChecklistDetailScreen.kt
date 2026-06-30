package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonDestructive
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.FillOptionsSheet
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiChecklistAction
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockAnchor
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiGlassChatDock
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
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.util.asWholeUrl
import com.antonchuraev.homesearchchecklist.desingsystem.util.displayDomain
import com.antonchuraev.homesearchchecklist.desingsystem.util.extractUrls
import com.antonchuraev.homesearchchecklist.desingsystem.util.rememberLinkifiedText
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.containsRepeat
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.resolveChipLabel
import com.antonchuraev.homesearchchecklist.desingsystem.components.TokenChipPreview
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly.MoveToDayBottomSheet
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly.WeeklyChecklistDetailContent
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheet
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetCallbacks
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderSheetState
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderTab
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.ReminderDateTimePicker
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.formatReminderDateTime
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.getString
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
    chatDockContent: (@Composable (AnchoredDraggableState<DockAnchor>, String, Dp, @Composable () -> Unit, ChatDockItemCreateOverride?) -> Unit)? = null,
    /**
     * Fires a contextual prompt-chip [GistiChecklistAction] for THIS checklist (chips above the
     * chat input). Called with (checklistId, checklistName, action) so App.kt can set the chat
     * context and dispatch the right prefill / send. Wired by App.kt.
     */
    onChecklistQuickAction: ((Long, String, GistiChecklistAction) -> Unit)? = null,
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
            onChecklistQuickAction = onChecklistQuickAction?.let { cb ->
                { action -> cb(currentState.checklist.id, currentState.checklist.name, action) }
            },
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
    chatDockContent: (@Composable (AnchoredDraggableState<DockAnchor>, String, Dp, @Composable () -> Unit, ChatDockItemCreateOverride?) -> Unit)? = null,
    onChecklistQuickAction: ((GistiChecklistAction) -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Continuous-drag chat dock state (per-screen; never shared across two-pane panes) ──
    val dockState = remember { AnchoredDraggableState(initialValue = DockAnchor.Peek) }
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
            // Chat: collapse only if the input is blank (a non-blank draft holds the dock open).
            chatInputBlank && !dockState.offset.isNaN() ->
                dockScope.launch { dockState.animateTo(DockAnchor.Peek) }
        }
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
    // Two static pickers (IMAGE + PDF) — avoids re-keying a single picker on type change.
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
    val filePicker = rememberFilePickerLauncher(type = FilePickerType.PDF) { result ->
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
            val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(target)
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

    AppScaffold(
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.imePadding(),
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
                IconButton(onClick = { onIntent(ChecklistDetailIntent.OnShareClick) }) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(Res.string.share))
                }
                // Toolbar "+" only in Standard view. In Weekly view items are added via the
                // per-day section "+" (inline WeeklyAddItemRow), which already targets the correct
                // weekday — a toolbar "+" there would need a day to land in and is redundant.
                if (state.checklist.viewMode != ChecklistViewMode.Weekly) {
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
            var dockHeightPx by remember { mutableStateOf(0) }
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
                // Item-create mode: the keyboard is up AND the expanded dock floats above it (its own
                // imePadding lifts it over the keyboard). The list's Modifier.imePadding() already
                // reserves the keyboard, so here we must ADDITIONALLY reserve the dock body height
                // ABOVE the keyboard — the measured dock height minus the ime inset the list already
                // handled — otherwise freshly added items render hidden under the dock.
                imeVisible && state.itemCreateMode ->
                    (dockHeight - imeBottom).coerceAtLeast(0.dp) + AppDimens.SpacingXxl
                // IME open (plain chat over the list): the list's Modifier.imePadding() already shrinks
                // the viewport above the keyboard (restoring the pre-dock adjustResize "content lifts"
                // behaviour), so the contentPadding only needs breathing room here — adding imeBottom
                // too would double-count and push the last item too far up.
                imeVisible -> AppDimens.SpacingSm
                showDock -> dockHeight + AppDimens.SpacingXxl
                else -> navBottom + AppDimens.SpacingLg
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

            Box(modifier = Modifier.fillMaxSize()) {
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
            // Folder mode: show only the leaf items of the current folder level. visibleFillItemIds
            // is null when folders are off (flat list) → no filtering, identical to before.
            val visibleFillItems = remember(defaultFill.items, state.visibleFillItemIds) {
                val ids = state.visibleFillItemIds
                if (ids == null) defaultFill.items else defaultFill.items.filter { it.id in ids }
            }
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

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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
                    ProgressHeader(
                        items = visibleFillItems,
                        name = state.currentFolderTitle ?: state.checklist.name,
                    )
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
                } // end Standard ->
                } // end when (state.checklist.viewMode)
              } // end hazeSource Box (live backdrop captured by the dock)

              // Nav-bar grey — same as MainScreen: paint the system navigation-bar zone with the dock's
              // own flat-grey token so the gesture/nav strip matches the dock instead of letting the
              // white page show through beneath it (the dock sits navbar-padded ABOVE this strip, owning
              // ime ∪ navigationBars, so the strip can't live inside the dock — it is a sibling filling
              // exactly the navbar inset at the screen bottom). Only while the dock is shown.
              if (showDock) {
                  Box(
                      modifier = Modifier
                          .align(Alignment.BottomCenter)
                          .fillMaxWidth()
                          .windowInsetsBottomHeight(WindowInsets.navigationBars)
                          .background(MaterialTheme.colorScheme.surfaceContainerLow),
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
                          // Freeze the measured height at PEEK so the list's bottom padding stays put
                          // while the CHAT panel expands upward over the list (no list repad/jump).
                          // EXCEPTION — item-create mode: there the user adds items that must stay
                          // visible, so we DO track the expanded dock height (the listBottomPadding
                          // item-create branch subtracts the ime inset back out).
                          .onSizeChanged {
                              if (dockState.targetValue == DockAnchor.Peek || state.itemCreateMode) {
                                  dockHeightPx = it.height
                              }
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
                                  )
                              } else {
                                  null
                              },
                          )
                      },
                  )
              }
            } // end anchor Box
        }
    }

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
            ),
            callbacks = ReminderSheetCallbacks(
                onTabSelected = { onIntent(ChecklistDetailIntent.OnItemReminderTabSelected(it)) },
                onPresetSelected = { triggerAt ->
                    if (itemReminderItem != null) {
                        onIntent(ChecklistDetailIntent.OnSaveItemReminder(
                            itemReminderItem.id, triggerAt, null, null
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
                            itemReminderItem.id, null, rule, timeMinutes
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

    // Overflow menu bottom sheet
    if (state.showOverflowSheet) {
        OverflowMenuSheet(
            separateCompleted = state.separateCompleted,
            autoDeleteCompleted = state.autoDeleteCompleted,
            hasCompletedItems = state.defaultFill?.items?.any { it.checked } == true,
            foldersEnabled = state.foldersEnabled,
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
                onIntent(ChecklistDetailIntent.OnDeleteChecklistClick)
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
 * Things-style detail sheet for a checklist item.
 *
 * Shows item name + three action rows: Reminder, Note, Delete.
 * Wiring (close + open target) is handled in Phase B (android-expert).
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
                    onClick = {},
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
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
        "Missed ($month ${localDt.day})"
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
private fun NotificationPermissionSheet(
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
    isWeeklyMode: Boolean,
    onEditClick: () -> Unit,
    onFillClick: () -> Unit,
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
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                    .padding(bottom = AppDimens.SpacingSm)
            ) {
                TokenChipPreview(
                    label = resolveChipLabel(token.display),
                    isRepeat = token.display.containsRepeat(),
                )
            }
        }
        GistiSelectableChipRow(
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
