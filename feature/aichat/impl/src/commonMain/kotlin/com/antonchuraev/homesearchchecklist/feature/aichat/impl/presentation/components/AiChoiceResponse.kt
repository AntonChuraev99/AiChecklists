package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_choice_edit_label
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_default
import aichecklists.core.designsystem.generated.resources.chat_choice_remember
import aichecklists.core.designsystem.generated.resources.chat_choice_save
import aichecklists.core.designsystem.generated.resources.chat_object_more
import aichecklists.core.designsystem.generated.resources.chat_preview_new_list_label
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
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
 * │  prompt          │   shape 20-20-20-4 (tail bottom-left), ChatMarkdownText
 * │  object rows     │   D2: the typed object of the action (item / list / time / …)
 * └──────────────────┘
 * [x] Remember choice    only on the which-list picker for an add
 * [chip] [chip] [chip]   wrapping FlowRow (all-short) or Column-fillMaxWidth (any long label)
 * [escape chip]          separate row
 * ```
 * The object rows live INSIDE the bubble on purpose. The bubble is already a container and
 * already means "the AI said this" — a second frame around the rows would turn a dialogue turn
 * into a form, which is what the chips redesign set out to kill. The root stays a [Column].
 *
 * The memory checkbox, by contrast, sits OUTSIDE the bubble: the bubble is what the AI said, the
 * checkbox is what the user does, and the ordering ("check, then pick") reads top-down.
 *
 * When [PendingChoice.editText] is non-null, the chip row is replaced by an inline
 * [OutlinedTextField] (auto-focus + IME) and the primary chip relabels to "Save".
 *
 * @param compact  True for the inline dock (App.kt), false for the full-height chat screen.
 *                 An explicit parameter, NOT a BoxWithConstraints measurement: both surfaces can
 *                 be the same width on the same phone, so width cannot tell them apart. Caps how
 *                 many preview rows render before the overflow line.
 * @param onSelect    Called with a chip's option id when tapped.
 * @param onEditChange Called as the user types in the inline edit field.
 * @param onEditConfirm Called when the user confirms the inline edit (primary chip).
 * @param onMemoryToggle Called when the user flips "Remember my choice". Only ever reachable when
 *                 [PendingChoice.showMemoryToggle] is true.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiChoiceResponse(
    pending: PendingChoice,
    onSelect: (optionId: String) -> Unit,
    onEditChange: (String) -> Unit,
    onEditConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onMemoryToggle: (Boolean) -> Unit = {},
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
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .animateContentSize()
                            // The bubble speaks as one unit; each object row then overrides with
                            // its own role-carrying description (see ObjectRow).
                            .semantics(mergeDescendants = true) {},
                    ) {
                        ChatMarkdownText(
                            markdown = choice.prompt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // D2: the typed object of the action.
                        if (choice.objectRows.isNotEmpty()) {
                            ObjectRows(
                                rows = choice.objectRows,
                                compact = compact,
                                modifier = Modifier.padding(top = AppDimens.SpacingSm),
                            )
                        }
                        // Agent-batch: numbered list of actions inside the bubble. Untouched by D2 —
                        // object rows assume ONE typed object, a batch is N heterogeneous ones.
                        pending.batchItems?.let { items ->
                            BatchActionList(
                                items = items,
                                modifier = Modifier.padding(top = AppDimens.SpacingSm),
                            )
                        }
                    }
                }
            }

            // Memory of choice — between the bubble and the chips, never inside either.
            if (pending.showMemoryToggle && !isEditing) {
                MemoryRow(
                    checked = pending.rememberChoice,
                    enabled = !isExecuting,
                    onCheckedChange = onMemoryToggle,
                )
            }

            if (isEditing) {
                EditField(
                    text = pending.editText.orEmpty(),
                    onTextChange = onEditChange,
                    isChecklistName = pending.isCreatingChecklist,
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
                    // No extra top padding: the parent column's SpacingSm already separates the
                    // escape chip from the options above. The old +SpacingXs pushed the gap to 12dp,
                    // which read as a loose break between two button rows (audit fix — tighten).
                    Row {
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
 * The D2 object block: the typed rows describing WHAT the pending action will act on.
 *
 * Repeatable rows — the proposed items of a list about to be created ([RowKind.Preview]) and a
 * multi-item add ([RowKind.Item]) — are capped at [PREVIEW_CAP_COMPACT] in the dock and
 * [PREVIEW_CAP_FULL] on the full screen, with an "…and N more" tail. The cap lives HERE, not in
 * the ViewModel, because only the renderer knows which surface it is on; the ViewModel emits the
 * full truth and this decides how much of it fits.
 *
 * Singular rows (destination, time, file, name, count, date range) are never capped: dropping the
 * time or the list would hide the very thing the question is asking about.
 */
@Composable
private fun ObjectRows(
    rows: List<ChoiceObjectRow>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val cap = if (compact) PREVIEW_CAP_COMPACT else PREVIEW_CAP_FULL
    val cappableCount = rows.count { it.kind.isCappable() }
    val hiddenCount = (cappableCount - cap).coerceAtLeast(0)
    var shown = 0
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        rows.forEach { row ->
            if (row.kind.isCappable()) {
                if (shown >= cap) return@forEach
                shown++
            }
            ObjectRow(row)
        }
        if (hiddenCount > 0) {
            val moreText = stringResource(Res.string.chat_object_more, hiddenCount.toString())
            ObjectRow(
                ChoiceObjectRow(
                    value = moreText,
                    kind = RowKind.Overflow,
                    emphasis = RowEmphasis.Detail,
                    contentDescription = moreText,
                ),
            )
        }
    }
}

/** Rows that repeat and may therefore be truncated with an "…and N more" tail. */
private fun RowKind.isCappable(): Boolean = this == RowKind.Preview || this == RowKind.Item

/**
 * One object row: a 16dp leading icon + the value.
 *
 * The icon carries a 2dp top offset so its optical centre lines up with the first text line
 * (a 16dp glyph against a 20dp line box sits high otherwise) — same treatment as ChatPricingRow.
 * The icon is decorative: the row's meaning is spoken by [ChoiceObjectRow.contentDescription],
 * so the icon takes a null description rather than an empty one.
 */
@Composable
private fun ObjectRow(row: ChoiceObjectRow) {
    val cs = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    // Emphasis drives BOTH weight and color — never colour alone (a state told only by hue is
    // invisible to a colour-blind or greyscale reader).
    val style = when (row.emphasis) {
        RowEmphasis.Primary, RowEmphasis.Danger -> typography.titleSmall
        RowEmphasis.Detail, RowEmphasis.Accent -> typography.bodyMedium
    }
    val color = when (row.emphasis) {
        RowEmphasis.Primary -> cs.onSurface
        RowEmphasis.Detail -> cs.onSurfaceVariant
        RowEmphasis.Accent -> cs.onSurface
        RowEmphasis.Danger -> cs.error
    }
    Row(
        modifier = Modifier.semantics { contentDescription = row.contentDescription },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        val icon = row.kind.icon()
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimens.IconSizeSm)
                    .padding(top = AppDimens.SpacingXxs),
                tint = color,
            )
        } else {
            // Preview / overflow rows: a literal bullet (or blank) holds the same 16dp gutter so
            // every row's text starts on one vertical line.
            Text(
                text = if (row.kind == RowKind.Preview) PREVIEW_BULLET else "",
                style = typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(AppDimens.IconSizeSm),
            )
        }
        Text(text = row.value, style = style, color = color)
    }
}

/** Leading icon per row kind; null → the row draws a literal bullet / blank gutter instead. */
private fun RowKind.icon(): ImageVector? = when (this) {
    RowKind.Item -> Icons.Outlined.Checklist
    RowKind.Destination, RowKind.Name -> Icons.Outlined.FormatListBulleted
    RowKind.Time -> Icons.Outlined.Schedule
    RowKind.File -> Icons.Outlined.AttachFile
    RowKind.Count -> Icons.Outlined.NotificationsNone
    RowKind.DateRange -> Icons.Outlined.Event
    RowKind.Preview, RowKind.Overflow -> null
}

/**
 * "Remember my choice" — a checkbox, deliberately NOT a chip.
 *
 * In this block a chip tap EXECUTES. A control that only toggles state must not wear the chip
 * shape, or the contract users learned in D1 ("tap = it happens") stops holding.
 *
 * The whole row is the target ([toggleable] with [Role.Checkbox]); the Checkbox itself takes a
 * null callback so the tap is not counted twice. Height comes from the Checkbox's own 48dp
 * minimum touch target.
 */
@Composable
private fun MemoryRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        // No AppCheckbox in the design system yet — raw M3 on theme tokens (the only checkbox in
        // the chat surface). Worth promoting to core/designsystem if a second one appears.
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = stringResource(Res.string.chat_choice_remember),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chip container. Every chat chip hugs its content width — never stretched, never full-width (the
 * global rule from the audit). The chips flow in a wrapping [FlowRow] with NO fixed row count, so
 * the layout self-adapts to N options (2, 3, … 6) by spilling onto as many rows as it needs. A long
 * label simply takes more of its row (and, at the extreme, wraps to two lines inside its own pill)
 * rather than forcing the whole block to a column of stretched buttons.
 *
 * Meta ("Shopping • 12 • 3 Jul") rides INSIDE the pill after the label (see [AiChoiceChip], called
 * with `fillWidth = false`) — a single hugging pill, NOT two `weight(1f)` children that would split
 * the space 50/50 and clip (the project's known "two weights split space" bug).
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
    val fallbackLoading = stringResource(Res.string.chat_choice_executing_default)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        // Tighter vertical rhythm between wrapped chip rows (4dp) than the horizontal gap (8dp).
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        options.forEach { option ->
            ChoiceChipFor(option, executingId, executingLabel, fallbackLoading, blockInteractive, onSelect, fillWidth = false)
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
        meta = option.meta,
        fillWidth = fillWidth,
    )
}

/**
 * List of proposed / applied actions inside the prompt bubble (destructive lines tinted).
 *
 * Numbering appears only from TWO items up: since D1 a single-action choice also renders through
 * here (it is how the question gets its object), and "1. • Milk" reads like a broken list.
 *
 * NEVER truncated — takes no `compact` flag on purpose. Unlike the create-checklist preview rows
 * ([ObjectRows], capped at [PREVIEW_CAP_COMPACT] in the dock), the agent batch is the full plan the
 * user is being asked to approve: hiding step 3 of 4 in the dock would have them approve actions
 * they cannot see. Every step renders on every surface.
 */
@Composable
private fun BatchActionList(
    items: List<AgentPlanItem>,
    modifier: Modifier = Modifier,
) {
    val numbered = items.size > 1
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            ) {
                if (numbered) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
 *
 * @param isChecklistName When true the field is naming a checklist about to be created, so it
 *  takes the specific "List name" label instead of the generic edit one.
 */
@Composable
private fun EditField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isChecklistName: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val labelRes = if (isChecklistName) Res.string.chat_preview_new_list_label else Res.string.chat_choice_edit_label
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(labelRes)) },
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

/** Bullet for a preview row (a literal, like the renderer's — punctuation, not language). */
private const val PREVIEW_BULLET = "•"

/**
 * How many proposed items a create-checklist preview shows before the "…and N more" row.
 * The dock has ~440dp to spend and must keep the question, the chips and the escape on screen;
 * the full screen can afford the whole shape of the list.
 */
private const val PREVIEW_CAP_COMPACT = 2
private const val PREVIEW_CAP_FULL = 6
