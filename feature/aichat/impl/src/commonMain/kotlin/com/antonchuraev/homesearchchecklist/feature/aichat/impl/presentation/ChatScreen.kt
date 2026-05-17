package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatMessage
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.AiChatPricingHelpSheet
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatHeader
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatInputRow
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatMessageBubble
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components.ChatPreviewCard
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
 * ───────────────────────
 * LazyColumn (messages, weight=1f)
 *   ↳ ChatMessageBubble per message
 *   ↳ ChatPreviewCard  (if state.pendingPreview != null, as last sticky item)
 * ───────────────────────
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
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive or a preview card appears.
    // Total item count = messages.size + (1 if preview is shown).
    val totalItemCount = state.messages.size + if (state.pendingPreview != null) 1 else 0
    LaunchedEffect(totalItemCount) {
        if (totalItemCount > 0) {
            listState.animateScrollToItem(totalItemCount - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatHeader(
                creditBalance = state.creditBalance,
                onHelpClick = { onIntent(ChatScreenIntent.OnHelpClick) },
                onBackClick = { onIntent(ChatScreenIntent.OnBackClick) },
                onMenuClick = { scope.launch { drawerState.open() } },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            // Message list — occupies all remaining vertical space
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingMd,
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            ) {
                items(
                    items = state.messages,
                    key = { it.id },
                ) { message ->
                    ChatMessageBubble(message = message)
                }

                // Preview card appears after the last message as a sticky bottom item.
                // Kept INSIDE LazyColumn so it participates in the same scroll region
                // and scrolls naturally when more messages arrive below it.
                state.pendingPreview?.let { preview ->
                    item(key = "preview_card") {
                        ChatPreviewCard(
                            preview = preview,
                            onApply = { onIntent(ChatScreenIntent.OnPreviewApply) },
                            onCancel = { onIntent(ChatScreenIntent.OnPreviewCancel) },
                            modifier = Modifier.padding(top = AppDimens.SpacingSm),
                        )
                    }
                }
            }

            // Input row is OUTSIDE LazyColumn — always pinned above the keyboard
            ChatInputRow(
                text = state.inputText,
                onTextChange = { onIntent(ChatScreenIntent.OnInputChange(it)) },
                onSend = { onIntent(ChatScreenIntent.OnSendClick) },
                isEnabled = !state.isProcessing,
            )
        }
    }

    // Pricing sheet overlay — conditionally shown above the scaffold
    if (state.showPricingSheet) {
        AiChatPricingHelpSheet(
            onDismiss = { onIntent(ChatScreenIntent.OnHelpDismiss) },
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
