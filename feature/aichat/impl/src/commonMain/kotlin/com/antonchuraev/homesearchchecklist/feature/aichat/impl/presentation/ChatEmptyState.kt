package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.SparkleTile
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupPositionOf
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
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
//  - compact=false (FULL): bottom-anchored greeting + one 4-row suggestion group (ChatScreen + full overlay).
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

/**
 * Full empty state — redesign B pass 2 (spec §2.1): greeting + ONE grouped container of four
 * suggestions, anchored at the BOTTOM, right above the input it prefills (the eye is already there).
 *
 * The column scrolls: at RU × fontScale 1.5 the headline takes two lines and the group no longer
 * fits a 640dp phone above the input (spec risk 4). At fontScale ≥ 1.3 the 48dp avatar steps aside
 * so the suggestions — the part that does something — keep the room.
 */
@Composable
private fun ChatEmptyStateFull(
    onPrefill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showAvatar = LocalDensity.current.fontScale < AVATAR_HIDE_FONT_SCALE
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Reverse: scroll 0 is the BOTTOM, so when the content outgrows the viewport the
                // suggestions beside the input stay in view and the greeting is what scrolls off —
                // the same anchoring as the reverse-layout message list that replaces this.
                .verticalScroll(rememberScrollState(), reverseScrolling = true),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    // Fill the viewport so the content can sit at its bottom; grow past it to scroll.
                    .heightIn(min = viewportHeight)
                    .widthIn(max = 480.dp)
                    .adaptiveContentWidth()
                    .padding(
                        start = AppDimens.ScreenPaddingHorizontal,
                        end = AppDimens.ScreenPaddingHorizontal,
                        top = AppDimens.SpacingMd,
                        bottom = AppDimens.SpacingMd,
                    ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (showAvatar) {
                    SparkleTile(size = 48.dp, cornerRadius = 24.dp)
                }
                Text(
                    text = stringResource(Res.string.chat_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (showAvatar) AppDimens.SpacingMd else 0.dp)
                        .semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.chat_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppDimens.SpacingXs),
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = AppDimens.SpacingLg)) {
                    val cards = suggestionCards()
                    cards.forEachIndexed { index, card ->
                        val text = stringResource(card.exampleRes)
                        SuggestionRow(
                            text = text,
                            spec = card,
                            position = groupPositionOf(index, cards.size),
                            onClick = { onPrefill(text) },
                        )
                    }
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

/**
 * One suggestion as a segment of the grouped container (the Inbox grammar: `groupSegment`, inset
 * dividers under the text). Tap = prefill the composer, never send.
 *
 * Fill is plane-relative ([AppChatColors.raised]) rather than the group's default `card()`: the same
 * body renders inside the full-screen chat overlay on the bottom chrome.
 */
@Composable
private fun SuggestionRow(
    text: String,
    spec: SuggestionCardSpec,
    position: GroupPosition,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .groupSegment(
                position = position,
                color = AppChatColors.raised(),
                dividerInsetStart = SuggestionDividerInset,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(AppDimens.SpacingMd),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(SuggestionTileSize)
                .clip(CircleShape)
                .background(GistiColors.paletteTint(spec.paletteIndex)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = GistiColors.paletteContent(spec.paletteIndex),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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

private val SuggestionTileSize = 32.dp

/** Divider starts under the text: 12 padding + 32 tile + 12 gap. */
private val SuggestionDividerInset = 56.dp

/** At or above this font scale the empty state's 48dp avatar steps aside (spec risk 4). */
private const val AVATAR_HIDE_FONT_SCALE = 1.3f

/** The four suggestion actions offered in the compact prompt-chip row. */
private enum class ChatSuggestionAction { CREATE, ATTACHMENT, REMINDER, ASK }

/**
 * A suggestion row definition: leading vector icon, its palette position (0 green · 1 blue · 2 peach
 * · 3 lilac — `GistiColors.paletteTint`) and the example string to prefill.
 */
private data class SuggestionCardSpec(
    val icon: ImageVector,
    val paletteIndex: Int,
    val exampleRes: org.jetbrains.compose.resources.StringResource,
)

/**
 * The four FULL-layout suggestions, in scan order. Icons are Material vectors (NOT emoji):
 * Skiko/wasm renders emoji as tofu without LocalEmojiFont, and these rows use plain [Icon]s.
 */
private fun suggestionCards(): List<SuggestionCardSpec> = listOf(
    SuggestionCardSpec(Icons.Outlined.Checklist, 0, Res.string.chat_features_example_create),
    SuggestionCardSpec(Icons.Outlined.AttachFile, 3, Res.string.chat_features_example_from_attachment),
    SuggestionCardSpec(Icons.Outlined.Notifications, 2, Res.string.chat_features_example_reminder),
    SuggestionCardSpec(Icons.Outlined.ChatBubbleOutline, 1, Res.string.chat_features_example_ask),
)
