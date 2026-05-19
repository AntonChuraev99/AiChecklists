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
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
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
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatPreviewCard
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatPricingRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatTypingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    drawerState: DrawerState = DrawerState(DrawerValue.Closed),
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

    // Welcome bubble is a UI affordance, not data — kept in composable scope
    // so the system locale drives its text without needing ViewModel locale state.
    val welcomeText = stringResource(Res.string.chat_assistant_welcome)
    val welcomeBubble = remember(welcomeText) {
        ChatMessage(
            id = "__welcome",
            role = ChatRole.Assistant,
            content = welcomeText,
            timestamp = 0L,
        )
    }

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

    // Auto-scroll to the newest item on changes. With reverseLayout = true the
    // newest item lives at index 0 (visually at the bottom near the input row).
    // Count also includes the typing indicator so the list scrolls when it appears.
    val totalItemCount = state.messages.size +
        (if (state.pendingPreview != null) 1 else 0) +
        (if (state.isProcessing && state.pendingPreview == null) 1 else 0)

    // Auto-scroll triggers:
    //   1. totalItemCount changes — new user/assistant message, preview appears, typing
    //      indicator — keeps the newest item visible.
    //   2. imeBottom changes — when the keyboard closes the viewport grows; the
    //      previously bottom-pinned item drifts up because LazyColumn does not re-anchor
    //      on resize. Re-running scrollToItem(0) snaps it back so a tall preview-card
    //      stays flush above the input row with Apply visible.
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
                onMenuClick = { scope.launch { drawerState.open() } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .imePadding(),
        ) {
            // Pricing info band — sits directly below the TopAppBar,
            // above the message list. Caption + help icon; tapping help
            // opens AiChatPricingHelpSheet via OnHelpClick intent.
            ChatPricingRow(
                onWhyClick = { onIntent(ChatScreenIntent.OnHelpClick) },
            )

            // Message list — occupies all remaining vertical space
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                // reverseLayout = items are laid out bottom-up (newest at the bottom,
                // oldest at the top). This is the standard chat pattern (Telegram /
                // WhatsApp) — items pin to the bottom by default, no gap on
                // near-empty chat, no recomposition lag when keyboard opens.
                reverseLayout = true,
                contentPadding = PaddingValues(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingMd,
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                // With reverseLayout = true, FIRST declared item renders at the BOTTOM
                // of the viewport. So we declare in newest-first order:
                //   typing indicator (shown when processing and no preview card yet)
                //   pendingPreview (most recent action)
                //   user/assistant messages reversed (newest first)
                //   welcome bubble (oldest, rendered at top of viewport)

                // Typing indicator — visible during Layer 1→2→3 round-trip only.
                // Hidden once pendingPreview appears (user already sees the result card).
                if (state.isProcessing && state.pendingPreview == null) {
                    item(key = "__typing") {
                        ChatTypingIndicator()
                    }
                }

                state.pendingPreview?.let { preview ->
                    item(key = "preview_card") {
                        ChatPreviewCard(
                            preview = preview,
                            onApply = { onIntent(ChatScreenIntent.OnPreviewApply) },
                            onCancel = { onIntent(ChatScreenIntent.OnPreviewCancel) },
                            onItemTextChange = { onIntent(ChatScreenIntent.OnPreviewItemTextChange(it)) },
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
                        onOpenChecklist = message.linkedChecklistId?.let { id ->
                            { onIntent(ChatScreenIntent.OnOpenChecklist(id)) }
                        },
                    )
                }

                // Welcome bubble — declared last, so it renders at the TOP of viewport
                // (above all real messages). Follows system locale via stringResource.
                // No feedback callback for the welcome bubble — it's a static affordance,
                // not a real AI response to user input.
                item(key = "__welcome") {
                    ChatMessageBubble(
                        message = welcomeBubble,
                    )
                }
            }

            // Recording overlay — slides in from bottom when mic is active.
            // Sits above the chip strip.
            ChatRecordingOverlay(
                isRecording = state.isRecording,
                durationMs = recordingDurationMs,
                isDragCancel = isDragCancel,
            )

            // Pending attachment chips — AnimatedVisibility driven by list size.
            ChatAttachmentChipStrip(
                attachments = state.pendingAttachments,
                onRemove = { path -> onIntent(ChatScreenIntent.OnRemoveAttachment(path)) },
            )

            // Input row is OUTSIDE LazyColumn — always pinned above the keyboard
            ChatInputRow(
                text = state.inputText,
                onTextChange = { onIntent(ChatScreenIntent.OnInputChange(it)) },
                onSend = { onIntent(ChatScreenIntent.OnSendClick) },
                onAttachFileClick = { showAttachmentSourceSheet = true },
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
                isEnabled = !state.isProcessing,
                canSend = state.canSend,
                isRecording = state.isRecording,
                onDragCancelChanged = { isDragCancel = it },
            )
        }
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
