package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_empty_subtitle
import aichecklists.core.designsystem.generated.resources.chat_empty_title
import aichecklists.core.designsystem.generated.resources.chat_features_case_ask
import aichecklists.core.designsystem.generated.resources.chat_features_case_create
import aichecklists.core.designsystem.generated.resources.chat_features_case_from_attachment
import aichecklists.core.designsystem.generated.resources.chat_features_case_reminder
import aichecklists.core.designsystem.generated.resources.chat_features_example_ask
import aichecklists.core.designsystem.generated.resources.chat_features_example_create
import aichecklists.core.designsystem.generated.resources.chat_features_example_from_attachment
import aichecklists.core.designsystem.generated.resources.chat_features_example_reminder
import aichecklists.core.designsystem.generated.resources.chat_panel_greeting
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------
// ChatBody — switches the empty-state greeting/suggestions against the message list.
// Hosted by BOTH the full-screen ChatScreen (ChatContent) and the App-level FULL chat overlay so
// the two surfaces stay identical. The visibility key is messages.isEmpty() (NOT inputText): a
// suggestion tap only PREFILLS the composer, so the empty state must stay put until the first send.
// ---------------------------------------------------------------------------

/**
 * Chat body: [ChatEmptyState] while the conversation is empty, [ChatMessageList] once it has
 * content. Cross-faded so the first send transitions smoothly instead of hard-cutting.
 *
 * "Empty" = no messages AND no pending choice AND not processing — the same predicate the dock uses
 * for its own empty/answer switch. A prefilled composer does NOT count as content, so tapping a
 * suggestion keeps the empty state visible (no flicker) until an actual message is sent.
 */
@Composable
fun ChatBody(
    state: ChatScreenState,
    onIntent: (ChatScreenIntent) -> Unit,
    listState: LazyListState,
    showTodayDivider: Boolean,
    totalItemCount: Int,
    modifier: Modifier = Modifier,
) {
    val showEmpty = state.messages.isEmpty() && state.pendingChoice == null && !state.isProcessing
    Crossfade(
        targetState = showEmpty,
        animationSpec = tween(durationMillis = 300),
        modifier = modifier,
        label = "chat_body_empty_crossfade",
    ) { empty ->
        if (empty) {
            ChatEmptyState(
                compact = false,
                onPrefill = { onIntent(ChatScreenIntent.OnPrefillInput(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ChatMessageList(
                state = state,
                onIntent = onIntent,
                listState = listState,
                showTodayDivider = showTodayDivider,
                totalItemCount = totalItemCount,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ChatEmptyState — localized greeting + suggestion starters.
//
// The localized copy IS the "we speak your language" signal (all strings from Compose Resources) —
// there is deliberately NO in-chat language badge. Two densities:
//  - compact=false (FULL): centered hero + 4 full-width suggestion cards (ChatScreen + dock full).
//  - compact=true  (dock expanded body): left-aligned greeting + a scrollable prompt-chip row.
// Every suggestion PREFILLS the composer (onPrefill) — it never sends.
// ---------------------------------------------------------------------------

/**
 * Suggestion-driven empty state for the chat.
 *
 * @param compact   false = full-screen hero layout; true = the dock's expanded greeting body.
 * @param onPrefill Called with the example/prompt text when a suggestion is tapped. Seeds the
 *                  composer only — the send is a separate deliberate action.
 */
@Composable
fun ChatEmptyState(
    compact: Boolean,
    onPrefill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        ChatEmptyStateCompact(onPrefill = onPrefill, modifier = modifier)
    } else {
        ChatEmptyStateFull(onPrefill = onPrefill, modifier = modifier)
    }
}

@Composable
private fun ChatEmptyStateFull(
    onPrefill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .adaptiveContentWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            ) {
                Text(
                    text = stringResource(Res.string.chat_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.chat_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                suggestionCards().forEach { card ->
                    val text = stringResource(card.exampleRes)
                    SuggestionCard(
                        text = text,
                        leadingIcon = card.icon,
                        onClick = { onPrefill(text) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyStateCompact(
    onPrefill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Text(
            text = stringResource(Res.string.chat_panel_greeting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        )
        // Prefill map: chip action -> the example sentence seeded into the composer. Short verb
        // labels (case_*), longer prefills (example_*). Label != prefill is intentional.
        val prefillByAction: Map<ChatSuggestionAction, String> = mapOf(
            ChatSuggestionAction.CREATE to stringResource(Res.string.chat_features_example_create),
            ChatSuggestionAction.ATTACHMENT to stringResource(Res.string.chat_features_example_from_attachment),
            ChatSuggestionAction.REMINDER to stringResource(Res.string.chat_features_example_reminder),
            ChatSuggestionAction.ASK to stringResource(Res.string.chat_features_example_ask),
        )
        val chips = listOf(
            GistiPromptChip("✨", stringResource(Res.string.chat_features_case_create), ChatSuggestionAction.CREATE),
            GistiPromptChip("📎", stringResource(Res.string.chat_features_case_from_attachment), ChatSuggestionAction.ATTACHMENT),
            GistiPromptChip("🔔", stringResource(Res.string.chat_features_case_reminder), ChatSuggestionAction.REMINDER),
            GistiPromptChip("💬", stringResource(Res.string.chat_features_case_ask), ChatSuggestionAction.ASK),
        )
        // Edge-to-edge row: no outer horizontal padding here (GistiPromptChips owns its content
        // padding), so the first/last chip can scroll out from under the screen edge.
        GistiPromptChips(
            chips = chips,
            onChipClick = { action -> prefillByAction[action]?.let(onPrefill) },
        )
    }
}

@Composable
private fun SuggestionCard(
    text: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        // Plane-relative + firm outline: the empty state renders on the page in the full chat and
        // inside the chrome in the compact dock, and these cards are tappable suggestions rather
        // than content. See AppChatColors.
        color = AppChatColors.raised(),
        border = BorderStroke(1.dp, AppChatColors.controlOutline()),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.NorthEast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The four suggestion actions offered in the compact prompt-chip row. */
private enum class ChatSuggestionAction { CREATE, ATTACHMENT, REMINDER, ASK }

/** A full-width suggestion card definition: leading vector icon + the example string to prefill. */
private data class SuggestionCardSpec(
    val icon: ImageVector,
    val exampleRes: org.jetbrains.compose.resources.StringResource,
)

/**
 * The four FULL-layout suggestion cards, in scan order. Icons are Material vectors (NOT emoji):
 * Skiko/wasm renders emoji as tofu without LocalEmojiFont, and these cards use plain [Icon]s.
 */
private fun suggestionCards(): List<SuggestionCardSpec> = listOf(
    SuggestionCardSpec(Icons.Outlined.AutoAwesome, Res.string.chat_features_example_create),
    SuggestionCardSpec(Icons.Outlined.AttachFile, Res.string.chat_features_example_from_attachment),
    SuggestionCardSpec(Icons.Outlined.Notifications, Res.string.chat_features_example_reminder),
    SuggestionCardSpec(Icons.Outlined.ChatBubbleOutline, Res.string.chat_features_example_ask),
)
