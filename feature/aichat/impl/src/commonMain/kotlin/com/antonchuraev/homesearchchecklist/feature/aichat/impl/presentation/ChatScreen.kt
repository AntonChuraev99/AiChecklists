package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
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
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.ChatSurfaceTone
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalChatSurfaceTone
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputLeading
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AttachmentSource
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChatFeaturesHelpSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChatPricingHelpSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatAttachmentChipStrip
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatAttachmentSourceSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatFeedbackSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatRecordingOverlay
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatResponseLanguageSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatSettingsSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatHeader
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatMessageBubble
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChoiceResponse
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AttachedChoiceActions
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
 * ChatHeader (TopAppBar: back · avatar · "Gisti" / subtitle · settings)
 * ───────────────────────────────────────────────────────
 * LazyColumn (messages, weight=1f)
 *   ↳ ChatMessageBubble per message
 *   ↳ AiChoiceResponse (if state.pendingChoice != null, as last sticky item)
 * ───────────────────────────────────────────────────────
 * ChatRecordingOverlay (AnimatedVisibility, above the plate)
 * bottom-chrome plate (SheetTop, owns the nav-bar inset):
 *   ChatAttachmentChipStrip (AnimatedVisibility, above input)
 *   ChatInputRow (leading = credits chip → pricing sheet; pinned above IME)
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
        (if (state.pendingChoice != null) 1 else 0) +
        (if (state.isProcessing && state.pendingChoice == null) 1 else 0)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface,
        // The bottom inset is owned by the input plate (it paints the bottom chrome down through the
        // system-nav zone, B §3.3), so the scaffold only pads top + sides. The snackbar host pads the
        // nav bar itself, since the scaffold no longer reserves it.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding())
        },
        topBar = {
            ChatHeader(
                onSettingsClick = { onIntent(ChatScreenIntent.OnSettingsClick) },
                onBackClick = { onIntent(ChatScreenIntent.OnBackClick) },
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
            // The old header credits chip's paywall route, now one hop further (chip → sheet → Get
            // more). Same host callback ⇒ same paywall source tag, the funnel series continues.
            onGetMoreClick = onNavigateToPaywall?.let { navigate ->
                {
                    onIntent(ChatScreenIntent.OnHelpDismiss)
                    navigate()
                }
            },
        )
    }

    // Features sheet overlay — opens from the Settings sheet's "How to use AI Chat" row
    if (state.showFeaturesSheet) {
        AiChatFeaturesHelpSheet(
            onDismiss = { onIntent(ChatScreenIntent.OnFeaturesHelpDismiss) },
        )
    }

    // Settings sheet overlay — opens from the gear icon in the top bar
    if (state.showSettingsSheet) {
        ChatSettingsSheet(
            creditBalance = state.creditBalance,
            isPremium = state.isPremium,
            deepThinkingEnabled = state.deepThinkingEnabled,
            onDeepThinkingToggle = { onIntent(ChatScreenIntent.OnDeepThinkingToggle(it)) },
            onGetMoreClick = {
                onIntent(ChatScreenIntent.OnSettingsDismiss)
                onNavigateToPaywall?.invoke()
            },
            onClearChat = { onIntent(ChatScreenIntent.OnClearChat) },
            onDismiss = { onIntent(ChatScreenIntent.OnSettingsDismiss) },
            responseLanguageCode = state.responseLanguage,
            onResponseLanguageClick = { onIntent(ChatScreenIntent.OnResponseLanguageClick) },
            defaultChecklistName = state.defaultChecklistName,
            onResetDefaultChecklist = { onIntent(ChatScreenIntent.OnResetDefaultChecklist) },
            onFeaturesHelpClick = {
                onIntent(ChatScreenIntent.OnSettingsDismiss)
                onIntent(ChatScreenIntent.OnFeaturesHelpClick)
            },
        )
    }

    // Response-language picker — a sibling above the settings sheet (drill-in). Selecting a
    // language persists it and closes only this sheet; the settings row updates reactively.
    if (state.showResponseLanguageSheet) {
        ChatResponseLanguageSheet(
            currentCode = state.responseLanguage,
            onSelect = { code -> onIntent(ChatScreenIntent.OnResponseLanguageSelected(code)) },
            onDismiss = { onIntent(ChatScreenIntent.OnResponseLanguagePickerDismiss) },
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
    // The mic press-and-hold gesture measures cancel-vs-commit in WINDOW coordinates (see
    // ChatInputRow), so it stays correct even when this input Column slides down as the keyboard
    // dismisses on record-start — plain `.imePadding()` is enough, no inset freeze required.
    Column(
        modifier = modifier
            .imePadding(),
    ) {
        // (The "≈ 0–3 credits · Why?" pricing band is gone — B §3.3: the credits chip in the input
        // opens the same pricing sheet.)

        // Body — occupies all remaining vertical space. [ChatBody] shows the localized empty-state
        // (greeting + suggestion cards that PREFILL the composer) while the conversation is empty and
        // the [ChatMessageList] once it has content. Shared with the App-level FULL chat overlay so
        // both surfaces behave identically (reverseLayout + IME scroll-anchor fix live in the list).
        ChatBody(
            state = state,
            onIntent = onIntent,
            listState = listState,
            showTodayDivider = showTodayDivider,
            totalItemCount = totalItemCount,
            modifier = Modifier.weight(1f),
        )

        // Recording overlay — slides in from bottom when mic is active
        ChatRecordingOverlay(
            isRecording = state.isRecording,
            durationMs = recordingDurationMs,
            isDragCancel = isDragCancel,
        )

        // Input plate (B §3.3, the mock's bottom chrome): the cool chrome tone with the 28dp
        // SheetTop corners, painted down through the system-nav zone. It owns the nav-bar inset; the
        // keyboard inset is already consumed by the root Column's imePadding, so with the keyboard up
        // navigationBarsPadding adds nothing (no double inset). The plate PROVIDES the chat tone, so
        // the pill / chips inside resolve to their bottom-chrome values (AppChatColors).
        // While a QUESTION is pending the input is hidden (see ChatInputPlateContent); with nothing
        // to hold, an empty plate would read as a stray sliver of chrome — only the nav-bar inset stays.
        val plateHasContent = state.pendingChoice == null || state.pendingChoice.isPostAction ||
            state.pendingAttachments.isNotEmpty()
        if (!plateHasContent) {
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        } else CompositionLocalProvider(LocalChatSurfaceTone provides ChatSurfaceTone.BottomChrome) {
            Surface(
                color = AppSurface.bottomChrome(),
                shape = AppShapeTokens.SheetTop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.navigationBarsPadding().padding(top = AppDimens.SpacingXs)) {
                    ChatInputPlateContent(
                        state = state,
                        onIntent = onIntent,
                        onDragCancelChanged = onDragCancelChanged,
                        onAttachmentSourceSheet = onAttachmentSourceSheet,
                        onVoiceRecordingStarted = onVoiceRecordingStarted,
                        onVoiceRecordingStopped = onVoiceRecordingStopped,
                        onVoiceRecordingCancelled = onVoiceRecordingCancelled,
                    )
                }
            }
        }
    }
}

/** Attachment chips + the input row, inside ChatContent's bottom-chrome plate. */
@Composable
private fun ChatInputPlateContent(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit,
    onDragCancelChanged: (Boolean) -> Unit,
    onAttachmentSourceSheet: () -> Unit,
    onVoiceRecordingStarted: (() -> Unit)?,
    onVoiceRecordingStopped: (() -> Unit)?,
    onVoiceRecordingCancelled: (() -> Unit)?,
) {
        // Pending attachment chips
        ChatAttachmentChipStrip(
            attachments = state.pendingAttachments,
            onRemove = { path -> onIntent(ChatScreenIntent.OnRemoveAttachment(path)) },
        )

        // Input row — pinned above the keyboard. Hidden while a QUESTION is pending: its chips
        // (incl. the "Something else" escape, which dismisses → input returns) are the only
        // interaction, so a parallel text field is noise.
        // A post-action offer (Undo / move) is NOT a question — it has no escape chip, so hiding
        // the input there left no way out but Back (which collapses the dock). Keep typing enabled;
        // sending a new message clears the offer.
        if (state.pendingChoice == null || state.pendingChoice.isPostAction) {
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
                // B §2.10: the credits chip replaces the "?" here (features help moved to Settings).
                // It opens the pricing sheet — the same intent the removed "Why?" band fired.
                leading = ChatInputLeading.Credits(
                    balance = state.creditBalance,
                    onClick = { onIntent(ChatScreenIntent.OnHelpClick) },
                ),
                onHelpClick = { onIntent(ChatScreenIntent.OnFeaturesHelpClick) },
                hasAttachments = state.pendingAttachments.isNotEmpty(),
                isEnabled = !state.isProcessing,
                canSend = state.canSend,
                isRecording = state.isRecording,
                isTranscribing = state.isTranscribing,
                onDragCancelChanged = onDragCancelChanged,
            )
        }
}

// ---------------------------------------------------------------------------
// ChatMessageList — the reverseLayout scrollable message history.
// Extracted from ChatContent so BOTH the full-screen ChatScreen and the App-level
// FULL chat overlay (the expanded dock's third "floor") render the exact same list
// (welcome bubble + typing indicator + pending-choice block + IME scroll-anchor fix).
// The caller applies the fill/weight modifier; this owns the list's own scroll effect.
// ---------------------------------------------------------------------------

/**
 * Reverse-layout scrollable chat history: typing indicator (top), optional pending-choice block,
 * the messages (newest at the bottom) and an optional "Today" divider.
 *
 * The empty-conversation greeting is NOT rendered here — [ChatBody] owns the empty↔list switch and
 * shows [ChatEmptyState] instead, so this list has no welcome bubble to collide with reverseLayout.
 *
 * @param showTodayDivider Whether the most recent message is from today (caller computes it).
 * @param totalItemCount   Message + choice + typing count — the IME scroll-anchor key.
 */
@Composable
fun ChatMessageList(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showTodayDivider: Boolean,
    totalItemCount: Int,
    modifier: Modifier = Modifier,
) {
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

    // B §2.9 — UI-local, derived from the message list (no new state):
    //  - avatar only on the LAST assistant message of a consecutive assistant run; a trailing
    //    assistant block rendered after the messages (typing indicator, or a pending choice WITH a
    //    prompt bubble) carries the run's avatar itself, so the last message then goes without;
    //  - feedback row always on the newest assistant reply, on older ones only after a tap
    //    (`revealedActionsFor`, one reply at a time).
    val trailingAssistantBlock = (state.isProcessing && state.pendingChoice == null) ||
        state.pendingChoice?.choice?.prompt?.isNotBlank() == true
    val avatarMessageIds = remember(state.messages, trailingAssistantBlock) {
        assistantRunTailIds(state.messages, trailingAssistantBlock)
    }
    val latestAssistantId = remember(state.messages) {
        state.messages.lastOrNull { it.role == ChatRole.Assistant }?.id
    }
    var revealedActionsFor by remember { mutableStateOf<String?>(null) }
    // B pass 2 "actions inside the turn": a BLANK-prompt choice (D1 Undo / Move, AI answer options)
    // belongs to the reply that already said the outcome or asked the question, so its actions
    // render inside that reply's bubble instead of as a second, context-free block under it. Only
    // when the newest message IS that assistant reply — otherwise the stand-alone block stays.
    val attachChoiceTo = attachedChoiceMessageId(state.messages, state.pendingChoice)

    LazyColumn(
        modifier = modifier
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
        if (state.isProcessing && state.pendingChoice == null) {
            item(key = "__typing") {
                ChatTypingIndicator()
            }
        }

        state.pendingChoice?.takeIf { attachChoiceTo == null }?.let { pending ->
            item(key = "choice_block") {
                AiChoiceResponse(
                    pending = pending,
                    onSelect = { id -> onIntent(ChatScreenIntent.OnChoiceSelected(id)) },
                    onEditChange = { onIntent(ChatScreenIntent.OnChoiceEditChange(it)) },
                    onEditConfirm = { onIntent(ChatScreenIntent.OnChoiceEditConfirmed) },
                    modifier = Modifier.padding(bottom = AppDimens.SpacingSm),
                    // Full-height screen: the whole preview fits, no dock cap to respect.
                    compact = false,
                    onMemoryToggle = { enabled ->
                        onIntent(ChatScreenIntent.OnChoiceMemoryToggle(enabled))
                    },
                )
            }
        }

        items(
            items = state.messages.asReversed(),
            key = { it.id },
        ) { message ->
            val isAssistant = message.role == ChatRole.Assistant
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
                onPaywallCta = message.paywallCtaCredits?.let {
                    { onIntent(ChatScreenIntent.OnPaywallCtaClick) }
                },
                onRetry = message.retryText?.let { text ->
                    { onIntent(ChatScreenIntent.OnRetryClick(text)) }
                },
                showAvatar = isAssistant && message.id in avatarMessageIds,
                isLatestAssistant = isAssistant && message.id == latestAssistantId,
                actionsRevealed = revealedActionsFor == message.id,
                onToggleActions = {
                    revealedActionsFor = if (revealedActionsFor == message.id) null else message.id
                },
                attachedActions = state.pendingChoice?.takeIf { message.id == attachChoiceTo }?.let { pending ->
                    {
                        AttachedChoiceActions(
                            pending = pending,
                            onSelect = { id -> onIntent(ChatScreenIntent.OnChoiceSelected(id)) },
                            onEditChange = { onIntent(ChatScreenIntent.OnChoiceEditChange(it)) },
                            onEditConfirm = { onIntent(ChatScreenIntent.OnChoiceEditConfirmed) },
                        )
                    }
                },
            )
        }

        if (showTodayDivider) {
            item(key = "__day_divider") {
                com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatDayDivider()
            }
        }
    }
}

/**
 * Id of the reply a BLANK-prompt [pending] choice renders inside (B pass 2): the newest message,
 * when it is an assistant one. Null → no pending choice, a prompt-carrying one (it has its own
 * bubble), or nothing to attach to (the stand-alone block renders instead).
 */
fun attachedChoiceMessageId(messages: List<ChatMessage>, pending: PendingChoice?): String? {
    if (pending == null || pending.choice.prompt.isNotBlank()) return null
    val newest = messages.lastOrNull() ?: return null
    return newest.id.takeIf { newest.role == ChatRole.Assistant }
}

/**
 * Ids of the assistant messages that close a consecutive assistant run — the ones that carry the
 * avatar (B §2.9). A message closes its run when the next-newer message is not an assistant one, or
 * when it is the newest message and no assistant block ([trailingAssistantBlock]: typing indicator /
 * prompt-carrying choice) renders after it.
 */
internal fun assistantRunTailIds(
    messages: List<ChatMessage>,
    trailingAssistantBlock: Boolean,
): Set<String> = buildSet {
    messages.forEachIndexed { index, message ->
        if (message.role != ChatRole.Assistant) return@forEachIndexed
        val next = messages.getOrNull(index + 1)
        val closesRun = if (next == null) !trailingAssistantBlock else next.role != ChatRole.Assistant
        if (closesRun) add(message.id)
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

private val mockChoice = PendingChoice(
    choice = ChatChoice(
        prompt = "Add to Shopping?",
        options = listOf(
            ChoiceOption(
                id = "execute",
                label = "Add",
                role = ChoiceRole.Primary,
                action = ChoiceAction.Execute(ToolCall.AddItem(checklistHint = "Shopping", itemText = "milk")),
            ),
            ChoiceOption(
                id = "edit",
                label = "Edit",
                role = ChoiceRole.Default,
                action = ChoiceAction.Edit,
            ),
        ),
        escape = ChoiceOption(
            id = "escape",
            label = "Something else",
            role = ChoiceRole.Escape,
            action = ChoiceAction.FreeForm("add milk to shopping"),
        ),
        kind = com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceKind.AddItems,
    ),
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
    pendingChoice = mockChoice,
    creditBalance = 3,
)

fun ChatScreenPreviewPricingSheet(): ChatScreenState = ChatScreenState(
    messages = mockMessages,
    showPricingSheet = true,
    creditBalance = 3,
)
