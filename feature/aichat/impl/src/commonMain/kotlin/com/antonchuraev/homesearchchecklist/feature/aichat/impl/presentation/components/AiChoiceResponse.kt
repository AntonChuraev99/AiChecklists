package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_choice_edit_label
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_default
import aichecklists.core.designsystem.generated.resources.chat_choice_save
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.AgentPlanItem
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.PendingChoice
import org.jetbrains.compose.resources.stringResource

/**
 * Claude-style assistant choice block: a prompt bubble followed by tappable choice chips.
 *
 * Replaces [ChatPreviewCard] (Apply/Cancel/Reject) and [AgentPlanCard] (Apply all/Cancel) with
 * one component. A tap on a chip executes immediately — the ViewModel resolves the chip's
 * [com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction] and runs it.
 *
 * Structure (NOT an AppCard — this is a styled dialogue turn, not a form):
 * ```
 * AiSenderLabel          (reused from ChatMessageBubble)
 * ┌──────────────────┐   prompt bubble: surfaceContainerLowest + 1dp outlineVariant
 * │  prompt / batch  │   shape 20-20-20-4 (tail bottom-left), ChatMarkdownText
 * └──────────────────┘
 * [chip] [chip] [chip]   FlowRow (short ≤2) or Column-fillMaxWidth (≥3 / long / narrow)
 * [escape chip]          separate row
 * ```
 * When [PendingChoice.editText] is non-null, the chip row is replaced by an inline
 * [OutlinedTextField] (auto-focus + IME) and the primary chip relabels to "Save".
 *
 * @param onSelect    Called with a chip's option id when tapped.
 * @param onEditChange Called as the user types in the inline edit field.
 * @param onEditConfirm Called when the user confirms the inline edit (primary chip).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiChoiceResponse(
    pending: PendingChoice,
    onSelect: (optionId: String) -> Unit,
    onEditChange: (String) -> Unit,
    onEditConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val choice = pending.choice
    val isExecuting = pending.executingId != null
    val isEditing = pending.editText != null

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(400)) +
            slideInVertically(animationSpec = tween(400)) { it / 12 },
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            // Blank prompt → AI-options case: the question is already its own assistant bubble
            // above (persisted as a message), so render ONLY the chips — no label, no bubble.
            // Non-blank prompt → Phase-1 write-intent / batch: label + prompt bubble + chips.
            if (choice.prompt.isNotBlank()) {
                AiSenderLabel()

                // Prompt bubble — assistant-style tonal bubble (mirrors ChatMessageBubble received side).
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 20.dp,
                        bottomStart = 4.dp,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        ChatMarkdownText(
                            markdown = choice.prompt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Agent-batch: numbered list of actions inside the bubble.
                        pending.batchItems?.let { items ->
                            BatchActionList(
                                items = items,
                                modifier = Modifier.padding(top = AppDimens.SpacingSm),
                            )
                        }
                    }
                }
            }

            if (isEditing) {
                EditField(
                    text = pending.editText.orEmpty(),
                    onTextChange = onEditChange,
                )
                // While editing, the primary option relabels to "Save" and confirms the edit.
                val primary = choice.options.firstOrNull { it.role == ChoiceRole.Primary }
                    ?: choice.options.firstOrNull()
                if (primary != null) {
                    AiChoiceChip(
                        label = stringResource(Res.string.chat_choice_save),
                        role = ChoiceRole.Primary,
                        onClick = onEditConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                }
            } else {
                ChoiceChips(
                    options = choice.options,
                    executingId = pending.executingId,
                    executingLabel = pending.executingLabel,
                    blockInteractive = !isExecuting,
                    onSelect = onSelect,
                )

                choice.escape?.let { escape ->
                    Row(modifier = Modifier.padding(top = AppDimens.SpacingXs)) {
                        AiChoiceChip(
                            label = escape.label,
                            role = escape.role,
                            onClick = { onSelect(escape.id) },
                            enabled = !isExecuting,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Layout-switching chip container. Short labels (≤ [SHORT_LABEL_MAX] chars) AND ≤ 2 options →
 * [FlowRow]; otherwise a vertical [Column] with full-width chips so long labels wrap cleanly.
 * Width is dictated by the parent (one composable, no separate dock variant).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    options: List<ChoiceOption>,
    executingId: String?,
    executingLabel: String?,
    blockInteractive: Boolean,
    onSelect: (String) -> Unit,
) {
    val allShort = options.all { it.label.length <= SHORT_LABEL_MAX }
    val useFlow = allShort && options.size <= 2
    val fallbackLoading = stringResource(Res.string.chat_choice_executing_default)

    if (useFlow) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            options.forEach { option ->
                ChoiceChipFor(option, executingId, executingLabel, fallbackLoading, blockInteractive, onSelect, fillWidth = false)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            options.forEach { option ->
                ChoiceChipFor(option, executingId, executingLabel, fallbackLoading, blockInteractive, onSelect, fillWidth = true)
            }
        }
    }
}

@Composable
private fun ChoiceChipFor(
    option: ChoiceOption,
    executingId: String?,
    executingLabel: String?,
    fallbackLoading: String,
    blockInteractive: Boolean,
    onSelect: (String) -> Unit,
    fillWidth: Boolean,
) {
    val isThisLoading = executingId == option.id
    AiChoiceChip(
        label = option.label,
        role = option.role,
        onClick = { onSelect(option.id) },
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        enabled = blockInteractive,
        isLoading = isThisLoading,
        loadingLabel = if (isThisLoading) (executingLabel ?: fallbackLoading) else null,
        leadingIcon = option.role.leadingIcon(),
    )
}

/** Numbered list of proposed agent actions inside the prompt bubble (destructive lines tinted). */
@Composable
private fun BatchActionList(
    items: List<AgentPlanItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.isDestructive) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.IconSizeSm),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/**
 * Inline edit field shown when [PendingChoice.editText] is non-null. Raw M3 [OutlinedTextField]
 * (no project wrapper supports multiline auto-focus) with auto-focus + IME raise, mirroring the
 * old ChatPreviewCard edit field.
 */
@Composable
private fun EditField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(Res.string.chat_choice_edit_label)) },
        minLines = 1,
        maxLines = 6,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

/** Leading icon for a chip role: trash for Destructive, "+" for Add, none otherwise. */
private fun ChoiceRole.leadingIcon(): ImageVector? = when (this) {
    ChoiceRole.Destructive -> Icons.Outlined.Delete
    ChoiceRole.Add -> Icons.Outlined.Add
    ChoiceRole.Primary, ChoiceRole.Default, ChoiceRole.Escape -> null
}

/** Labels at or below this length are considered "short" for the FlowRow layout decision. */
private const val SHORT_LABEL_MAX = 18
