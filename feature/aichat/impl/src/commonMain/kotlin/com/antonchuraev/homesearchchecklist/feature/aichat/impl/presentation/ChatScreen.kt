package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_assistant_welcome
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChatFeaturesHelpSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChatPricingHelpSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatAttachmentChipStrip
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatAttachmentSourceSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatFeedbackSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatRecordingOverlay
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatSettingsSheet
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatHeader
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatMessageBubble
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AgentPlanCard
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatPreviewCard
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatPricingRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatTypingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * AI Chat screen — stateless composable.
 *
 * Receives [state] from the ViewModel (or a mock for previews) and emits
 * user actions via [onIntent]. The ViewModel is wired by android-expert in Phase B.
 *
 * Layout (top → bottom):
 * ```
 * ChatHeader (TopAppBar)
 * ChatPricingRow ("≈ 0–3 credits per query" + help icon)
 * ───────────────────────────────────────────────────────
 * LazyColumn (messages, weight=1f)
 *   ↳ ChatMessageBubble per message
 *   ↳ ChatPreviewCard  (if state.pendingPreview != null, as last sticky item)
 * ───────────────────────────────────────────────────────
 * ChatRecordingOverlay (AnimatedVisibility, above chip strip)
 * ChatAttachmentChipStrip (AnimatedVisibility, above input)
 * ChatInputRow (pinned to bottom, above IME)
 * ```
 *
 * When [state.showPricingSheet] is true, [AiChatPricingHelpSheet] is overlaid.
 *
 * Auto-scroll behaviour: whenever [state.messages] grows, the list scrolls to the
 * last item so the newest message is always visible (standard chat convention).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit,
    drawerState: DrawerState? = null,
    onNavigateToPaywall: (() -> Unit)? = null,
    onAttachmentSourcePicked: ((AttachmentSource) -> Unit)? = null,
    onVoiceRecordingStarted: (() -> Unit)? = null,
    onVoiceRecordingStopped: (() -> Unit)? = null,
    onVoiceRecordingCancelled: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Attachment source sheet — local UI state, not in ViewModel
    var showAttachmentSourceSheet by remember { mutableStateOf(false) }

    // Drag-cancel state for recording overlay label
    var isDragCancel by remember { mutableStateOf(false) }

    // Recording duration timer — ticks every 1s while isRecording is true.
    // Local to Screen because the ViewModel doesn't need this value.
    var recordingDurationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.isRecording) {
        if (state.isRecording) {
            recordingDurationMs = 0L
            while (true) {
                delay(1_000L)
                recordingDurationMs += 1_000L
            }
        } else {
            recordingDurationMs = 0L
        }
    }

    // Day divider visibility: "Today" pill appears only if the most recent message
    // is from today.
    val showTodayDivider = remember(state.messages) {
        val last = state.messages.lastOrNull() ?: return@remember false
        val tz = TimeZone.currentSystemDefault()
        val startOfToday = kotlin.time.Clock.System.now()
            .toLocalDateTime(tz).date
            .atStartOfDayIn(tz)
            .toEpochMilliseconds()
        last.timestamp >= startOfToday
    }

    // Total item count for scroll / IME anchor fix (passed to ChatContent).
    val totalItemCount = state.messages.size +
        (if (state.pendingPreview != null) 1 else 0) +
        (if (state.pendingAgentPlan != null) 1 else 0) +
        (if (state.isProcessing && state.pendingPreview == null && state.pendingAgentPlan == null) 1 else 0)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatHeader(
                creditBalance = state.creditBalance,
                onSettingsClick = { onIntent(ChatScreenIntent.OnSettingsClick) },
                onBackClick = { onIntent(ChatScreenIntent.OnBackClick) },
                onCreditsClick = onNavigateToPaywall,
                onMenuClick = if (drawerState != null) {
                    { scope.launch { drawerState.open() } }
                } else null,
            )
        },
    ) { scaffoldPadding ->
        ChatContent(
            state = state,
            onIntent = onIntent,
            listState = listState,
            snackbarHostState = snackbarHostState,
            showTodayDivider = showTodayDivider,
            totalItemCount = totalItemCount,
            recordingDurationMs = recordingDurationMs,
            isDragCancel = isDragCancel,
            onDragCancelChanged = { isDragCancel = it },
            onAttachmentSourceSheet = { showAttachmentSourceSheet = true },
            onVoiceRecordingStarted = onVoiceRecordingStarted,
            onVoiceRecordingStopped = onVoiceRecordingStopped,
            onVoiceRecordingCancelled = onVoiceRecordingCancelled,
            onNavigateToPaywall = onNavigateToPaywall,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        )
    }

    // Attachment source sheet — presented above scaffold when user taps the clip icon
    if (showAttachmentSourceSheet) {
        ChatAttachmentSourceSheet(
            onSourceSelected = { source ->
                showAttachmentSourceSheet = false
                onIntent(ChatScreenIntent.OnPickAttachment(source))
                onAttachmentSourcePicked?.invoke(source)
            },
            onDismiss = { showAttachmentSourceSheet = false },
        )
    }

    // Pricing sheet overlay — conditionally shown above the scaffold
    if (state.showPricingSheet) {
        AiChatPricingHelpSheet(
            onDismiss = { onIntent(ChatScreenIntent.OnHelpDismiss) },
        )
    }

    // Features sheet overlay — opens from the "?" icon in the input row leading
    if (state.showFeaturesSheet) {
        AiChatFeaturesHelpSheet(
            onDismiss = { onIntent(ChatScreenIntent.OnFeaturesHelpDismiss) },
        )
    }

    // Settings sheet overlay — opens from the gear icon in the top bar
    if (state.showSettingsSheet) {
        ChatSettingsSheet(
            creditBalance = state.creditBalance,
            isPremium = false, // Pending: docs/todos/2026-05-17-chat-settings-premium-flag.md
            deepThinkingEnabled = state.deepThinkingEnabled,
            onDeepThinkingToggle = { onIntent(ChatScreenIntent.OnDeepThinkingToggle(it)) },
            onGetMoreClick = {
                onIntent(ChatScreenIntent.OnSettingsDismiss)
                onNavigateToPaywall?.invoke()
            },
            onClearChat = { onIntent(ChatScreenIntent.OnClearChat) },
            onDismiss = { onIntent(ChatScreenIntent.OnSettingsDismiss) },
        )
    }

    // Feedback sheet overlay — opens when user taps the RateReview icon on an assistant bubble
    state.feedbackTarget?.let { target ->
        val previousUserQuestion = remember(state.messages, target.id) {
            val idx = state.messages.indexOfFirst { it.id == target.id }
            state.messages.take(idx.coerceAtLeast(0)).lastOrNull { it.role == ChatRole.User }?.content
        }
        ChatFeedbackSheet(
            target = target,
            previousUserQuestion = previousUserQuestion,
            feedbackText = state.feedbackText,
            isSubmitting = state.isSubmittingFeedback,
            onTextChange = { onIntent(ChatScreenIntent.OnFeedbackTextChange(it)) },
            onSubmit = { onIntent(ChatScreenIntent.OnFeedbackSubmit) },
            onDismiss = { onIntent(ChatScreenIntent.OnFeedbackDismiss) },
        )
    }
}

// ---------------------------------------------------------------------------
// ChatContent — embeddable chat body (messages + input).
// Extracted so it can be hosted both inside ChatScreen (full-screen) and inside
// GistiChatSheet (expandable bottom-sheet at App.kt level).
//
// Preserves ALL existing LaunchedEffects (reverseLayout IME scroll-anchor fix),
// imePadding() lives on the root Column so the sheet host sets
// contentWindowInsets = WindowInsets(0) to avoid double-inset.
// ---------------------------------------------------------------------------

/**
 * Stateless chat body: messages list + input row + overlays.
 *
 * All scroll / IME / recording local state is hoisted from the caller so that
 * [ChatScreen] (full-screen) and the App-level sheet host can share the same
 * composable without duplicating state setup.
 *
 * The caller is responsible for:
 *  - Creating [listState] via [rememberLazyListState]
 *  - Computing [showTodayDivider] and [totalItemCount]
 *  - Providing [recordingDurationMs] (ticked externally, driven by [state.isRecording])
 *  - Wiring [onAttachmentSourceSheet] to show the source sheet sibling
 */
@Composable
fun ChatContent(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    snackbarHostState: SnackbarHostState,
    showTodayDivider: Boolean,
    totalItemCount: Int,
    recordingDurationMs: Long,
    isDragCancel: Boolean,
    onDragCancelChanged: (Boolean) -> Unit,
    onAttachmentSourceSheet: () -> Unit,
    onVoiceRecordingStarted: (() -> Unit)?,
    onVoiceRecordingStopped: (() -> Unit)?,
    onVoiceRecordingCancelled: (() -> Unit)?,
    onNavigateToPaywall: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Welcome bubble is a UI affordance, not data
    val welcomeText = stringResource(Res.string.chat_assistant_welcome)
    val welcomeBubble = remember(welcomeText) {
        ChatMessage(
            id = "__welcome",
            role = ChatRole.Assistant,
            content = welcomeText,
            timestamp = 0L,
        )
    }

    // IME scroll-anchor fix: re-run scrollToItem(0) when keyboard opens/closes so the
    // reverseLayout list stays pinned to the bottom after viewport resize.
    // MUST use imeBottom as a LaunchedEffect key — totalItemCount alone is insufficient.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val hasInitializedScroll = remember { mutableStateOf(false) }
    LaunchedEffect(totalItemCount, imeBottom) {
        if (totalItemCount > 0) {
            if (!hasInitializedScroll.value) {
                listState.scrollToItem(0)
                hasInitializedScroll.value = true
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(
        modifier = modifier
            .imePadding(),
    ) {
        // Pricing info band — sits directly below the TopAppBar / sheet header,
        // above the message list.
        ChatPricingRow(
            onWhyClick = { onIntent(ChatScreenIntent.OnHelpClick) },
        )

        // Message list — occupies all remaining vertical space
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .adaptiveContentWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = AppDimens.SpacingMd,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            if (state.isProcessing && state.pendingPreview == null && state.pendingAgentPlan == null) {
                item(key = "__typing") {
                    ChatTypingIndicator()
                }
            }

            state.pendingAgentPlan?.let { plan ->
                item(key = "agent_plan_card") {
                    AgentPlanCard(
                        plan = plan,
                        onApply = { onIntent(ChatScreenIntent.OnAgentPlanApply) },
                        onCancel = { onIntent(ChatScreenIntent.OnAgentPlanCancel) },
                        modifier = Modifier.padding(bottom = AppDimens.SpacingSm),
                    )
                }
            }

            state.pendingPreview?.let { preview ->
                item(key = "preview_card") {
                    ChatPreviewCard(
                        preview = preview,
                        onApply = { onIntent(ChatScreenIntent.OnPreviewApply) },
                        onCancel = { onIntent(ChatScreenIntent.OnPreviewCancel) },
                        onReject = { onIntent(ChatScreenIntent.OnPreviewReject) },
                        onItemTextChange = { onIntent(ChatScreenIntent.OnPreviewItemTextChange(it)) },
                        showRejectButton = preview.toolCall !is ToolCall.CreateChecklistFromAttachment,
                        modifier = Modifier.padding(bottom = AppDimens.SpacingSm),
                    )
                }
            }

            items(
                items = state.messages.asReversed(),
                key = { it.id },
            ) { message ->
                ChatMessageBubble(
                    message = message,
                    onFeedbackClick = { onIntent(ChatScreenIntent.OnFeedbackOpen(it)) },
                    onThumbUpClick = { onIntent(ChatScreenIntent.OnThumbUpClick(it)) },
                    onOpenChecklist = message.linkedChecklistId?.let { id ->
                        { onIntent(ChatScreenIntent.OnOpenChecklist(id)) }
                    },
                    onAskAiFallback = message.askAiForText?.let { text ->
                        { onIntent(ChatScreenIntent.OnAskAiFallback(text)) }
                    },
                    showSenderLabel = message.role == ChatRole.Assistant,
                )
            }

            if (showTodayDivider) {
                item(key = "__day_divider") {
                    com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatDayDivider()
                }
            }

            item(key = "__welcome") {
                ChatMessageBubble(
                    message = welcomeBubble,
                    showSenderLabel = false,
                )
            }
        }

        // Recording overlay — slides in from bottom when mic is active
        ChatRecordingOverlay(
            isRecording = state.isRecording,
            durationMs = recordingDurationMs,
            isDragCancel = isDragCancel,
        )

        // Pending attachment chips
        ChatAttachmentChipStrip(
            attachments = state.pendingAttachments,
            onRemove = { path -> onIntent(ChatScreenIntent.OnRemoveAttachment(path)) },
        )

        // Input row — always pinned above the keyboard
        ChatInputRow(
            text = state.inputText,
            onTextChange = { onIntent(ChatScreenIntent.OnInputChange(it)) },
            onSend = { onIntent(ChatScreenIntent.OnSendClick) },
            onAttachFileClick = onAttachmentSourceSheet,
            onVoiceRecordingStarted = {
                onIntent(ChatScreenIntent.OnVoiceRecordingStarted)
                onVoiceRecordingStarted?.invoke()
            },
            onVoiceRecordingStopped = {
                onVoiceRecordingStopped?.invoke()
            },
            onVoiceRecordingCancelled = {
                onVoiceRecordingCancelled?.invoke()
            },
            onHelpClick = { onIntent(ChatScreenIntent.OnFeaturesHelpClick) },
            hasAttachments = state.pendingAttachments.isNotEmpty(),
            isEnabled = !state.isProcessing && state.pendingAgentPlan == null,
            canSend = state.canSend,
            isRecording = state.isRecording,
            isTranscribing = state.isTranscribing,
            onDragCancelChanged = onDragCancelChanged,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews (static mock data — no ViewModel required)
// ---------------------------------------------------------------------------

private val mockMessages = listOf(
    ChatMessage(
        id = "1",
        role = ChatRole.Assistant,
        content = "Hi! I can help you manage your checklists. Try «add milk to shopping» or «remind me on Friday at 6pm».",
        timestamp = 1_000L,
    ),
    ChatMessage(
        id = "2",
        role = ChatRole.User,
        content = "add milk to shopping",
        timestamp = 2_000L,
        costCredits = 0,
        routedLayer = RoutingLayer.Local,
    ),
    ChatMessage(
        id = "3",
        role = ChatRole.Assistant,
        content = "Got it! I'll add «milk» to your Shopping list.",
        timestamp = 3_000L,
    ),
)

private val mockPreview = PendingPreview(
    toolCall = ToolCall.AddItem(
        checklistHint = "Shopping",
        itemText = "milk",
    ),
    humanReadable = "• milk → Shopping",
)

// Pending: docs/todos/2026-05-13-ai-chat-assistant.md — @Preview annotations require
// androidMain; preview composables are intentionally left without @Preview annotation
// so this file compiles in commonMain. Android-expert will wrap them in androidMain previews.

fun ChatScreenPreviewEmpty(): ChatScreenState = ChatScreenState()

fun ChatScreenPreviewMessages(): ChatScreenState = ChatScreenState(
    messages = mockMessages,
    creditBalance = 3,
)

fun ChatScreenPreviewWithPreviewCard(): ChatScreenState = ChatScreenState(
    messages = mockMessages,
    pendingPreview = mockPreview,
    creditBalance = 3,
)

fun ChatScreenPreviewPricingSheet(): ChatScreenState = ChatScreenState(
    messages = mockMessages,
    showPricingSheet = true,
    creditBalance = 3,
)
