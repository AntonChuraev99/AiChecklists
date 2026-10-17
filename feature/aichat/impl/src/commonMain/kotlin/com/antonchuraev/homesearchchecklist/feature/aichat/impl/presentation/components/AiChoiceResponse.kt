package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_choice_edit_label
import aichecklists.core.designsystem.generated.resources.chat_choice_executing_default
import aichecklists.core.designsystem.generated.resources.chat_choice_remember
import aichecklists.core.designsystem.generated.resources.chat_choice_save
import aichecklists.core.designsystem.generated.resources.chat_object_more
import aichecklists.core.designsystem.generated.resources.chat_preview_new_list_label
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceKind
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceObjectRow
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceOption
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChoiceRole
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowEmphasis
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RowKind
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.AgentPlanItem
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.PendingChoice
import org.jetbrains.compose.resources.stringResource

/**
 * Claude-style assistant choice block — redesign B pass 2, variant **"Actions inside the turn"**.
 *
 * ONE assistant bubble holds the whole proposal. A tap on an action executes immediately — the
 * ViewModel resolves the option's [ChoiceAction] and runs it (D1 contract, unchanged).
 *
 * ```
 * (avatar) ┌─────────────────────────────┐  AppChatColors.raised() + 1dp contentOutline, 20/20/20/8,
 *          │ (tile) prompt               │  max 300dp, hugging its content
 *          │  object rows / batch steps   │
 *          │  Remember my choice   [===]  │  which-list for an add only — AppSwitch row
 *          ├─────────────────────────────┤  1dp contentOutline, full bubble width
 *          │ [Primary] [Default] Cancel   │  FlowRow — Escape LAST, as a ghost in the same flow
 *          │   — or —                     │
 *          │ ● Shopping        12 • 3 Jul │  OptionRows when the options are destinations
 *          │ ● Groceries                8 │
 *          │ Cancel                       │
 *          └─────────────────────────────┘
 * ```
 *
 * The header tile is decorative and comes from [com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatChoice.kind];
 * a null kind draws no tile (never guessed from copy).
 *
 * **Blank prompt** (post-action Undo / Move, AI answer options): the outcome or the question is
 * already its own assistant message. The hosts attach these actions INSIDE that message's bubble
 * ([ChatMessageBubble]'s `attachedActions` slot + [AttachedChoiceActions]); this composable only renders
 * them stand-alone, under the avatar slot, when there is no message to attach them to.
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

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(400)) +
            slideInVertically(animationSpec = tween(400)) { it / 12 },
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        if (choice.prompt.isNotBlank()) {
            // B §2.9: no sender label — the avatar sits BESIDE the bubble (this block is always the
            // newest assistant turn, so it is the last of its run and carries it).
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                AssistantAvatarSlot(showAvatar = true)
                Spacer(modifier = Modifier.width(ChatAvatarLayout.Gap))
                Surface(
                    shape = AssistantBubbleShape,
                    // The SAME pair as ChatMessageBubble's received side, and it has to be: this
                    // bubble and an assistant answer are the same thing said two ways, and they are
                    // routinely one above the other in one frame. Plane-relative — see AppChatColors.
                    color = AppChatColors.raised(),
                    border = BorderStroke(1.dp, AppChatColors.contentOutline()),
                    modifier = Modifier.widthIn(max = ChoiceBubbleMaxWidth),
                ) {
                    // Hug the content (B mock): without the intrinsic cap a short question with two
                    // chips would stretch to the 300dp limit.
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .animateContentSize(),
                    ) {
                        Column(
                            // The question speaks as one unit; each object row then overrides with
                            // its own role-carrying description (see ObjectRow). The actions below
                            // stay separate targets.
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                        ) {
                            ChoiceHeader(prompt = choice.prompt, kind = choice.kind)
                            if (choice.objectRows.isNotEmpty()) {
                                ObjectRows(
                                    rows = choice.objectRows,
                                    compact = compact,
                                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 4.dp),
                                )
                            }
                            // Agent batch: numbered steps. Untouched by D2 — object rows assume ONE
                            // typed object, a batch is N heterogeneous ones.
                            pending.batchItems?.let { items ->
                                BatchActionList(
                                    items = items,
                                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 6.dp),
                                )
                            }
                        }
                        ChoiceTurnActions(
                            pending = pending,
                            onSelect = onSelect,
                            onEditChange = onEditChange,
                            onEditConfirm = onEditConfirm,
                            onMemoryToggle = onMemoryToggle,
                        )
                    }
                }
            }
        } else {
            // Stand-alone fallback (no message to attach to): the actions under the avatar slot.
            Column(modifier = modifier.fillMaxWidth().padding(start = ChatAvatarLayout.Slot)) {
                ChoiceActionsArea(
                    pending = pending,
                    onSelect = onSelect,
                    onEditChange = onEditChange,
                    onEditConfirm = onEditConfirm,
                    contentPadding = false,
                )
            }
        }
    }
}

/**
 * The actions of a BLANK-prompt choice, for [ChatMessageBubble]'s `attachedActions` slot: the
 * reply that already said the outcome (or asked the question) carries its Undo / Move / answer
 * options inside its own bubble, under the divider the bubble draws. Same chips, same callbacks,
 * same executing state as the stand-alone block.
 */
@Composable
fun AttachedChoiceActions(
    pending: PendingChoice,
    onSelect: (optionId: String) -> Unit,
    onEditChange: (String) -> Unit = {},
    onEditConfirm: () -> Unit = {},
) {
    ChoiceActionsArea(
        pending = pending,
        onSelect = onSelect,
        onEditChange = onEditChange,
        onEditConfirm = onEditConfirm,
        contentPadding = true,
    )
}

/**
 * The bottom of a proposal turn: the memory switch (which-list only), the inner divider and the
 * actions area. Shared by the [AiChoiceResponse] bubble and by [ChatMessageBubble], which attaches
 * a blank-prompt choice (Undo / Move, AI answer options) inside the message that already said it.
 *
 * Must be called inside a bubble column (it draws a full-width divider).
 */
@Composable
private fun ChoiceTurnActions(
    pending: PendingChoice,
    onSelect: (optionId: String) -> Unit,
    onEditChange: (String) -> Unit,
    onEditConfirm: () -> Unit,
    onMemoryToggle: (Boolean) -> Unit,
) {
    val isExecuting = pending.executingId != null
    val isEditing = pending.editText != null
    val showMemory = pending.showMemoryToggle && !isEditing
    if (showMemory) {
        MemoryRow(
            checked = pending.rememberChoice,
            enabled = !isExecuting,
            onCheckedChange = onMemoryToggle,
        )
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = AppChatColors.contentOutline(),
        // The memory row brings its own 48dp line box; a bare divider after content needs air.
        modifier = Modifier.padding(top = if (showMemory) 0.dp else AppDimens.SpacingSm),
    )
    ChoiceActionsArea(
        pending = pending,
        onSelect = onSelect,
        onEditChange = onEditChange,
        onEditConfirm = onEditConfirm,
        contentPadding = true,
    )
}

/**
 * The actions of a turn: editing (field + Save) · destination rows ([OptionRows]) · a wrapping
 * chip flow with the Escape ghost LAST in the same flow (owner decision O2, 2026-10-17).
 *
 * @param contentPadding True inside a bubble (12dp sides, 10 top, 12 bottom; option rows run
 *  edge-to-edge and own their insets). False for the stand-alone fallback, which sits on the plane.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceActionsArea(
    pending: PendingChoice,
    onSelect: (optionId: String) -> Unit,
    onEditChange: (String) -> Unit,
    onEditConfirm: () -> Unit,
    contentPadding: Boolean,
) {
    val choice = pending.choice
    val isExecuting = pending.executingId != null
    val side = if (contentPadding) AppDimens.SpacingMd else 0.dp
    val chipAreaModifier = Modifier.padding(
        start = side,
        end = side,
        top = if (contentPadding) 10.dp else 0.dp,
        bottom = if (contentPadding) AppDimens.SpacingMd else 0.dp,
    )

    when {
        pending.editText != null -> Column(
            modifier = chipAreaModifier,
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            EditField(
                text = pending.editText,
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
        }

        usesOptionRows(choice.kind, choice.options) -> Column(
            modifier = Modifier.padding(
                top = if (contentPadding) AppDimens.SpacingXs else 0.dp,
                bottom = if (contentPadding) AppDimens.SpacingSm else 0.dp,
            ),
        ) {
            OptionRows(
                options = choice.options,
                executingId = pending.executingId,
                executingLabel = pending.executingLabel,
                blockInteractive = !isExecuting,
                onSelect = onSelect,
                inset = contentPadding,
            )
            choice.escape?.let { escape ->
                Row(modifier = Modifier.padding(start = side, end = side)) {
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

        else -> {
            val fallbackLoading = stringResource(Res.string.chat_choice_executing_default)
            FlowRow(
                modifier = chipAreaModifier,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                // Tighter vertical rhythm between wrapped chip rows (4dp) than the horizontal gap.
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            ) {
                choice.options.forEach { option ->
                    ChoiceChipFor(option, pending.executingId, pending.executingLabel, fallbackLoading, !isExecuting, onSelect)
                }
                choice.escape?.let { escape ->
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

/**
 * Destination options read as rows, not chips: "Which list?" (always destinations) and any option
 * set that carries a meta or a checklist seed (the move-target picker).
 */
internal fun usesOptionRows(kind: ChoiceKind?, options: List<ChoiceOption>): Boolean =
    options.isNotEmpty() &&
        (kind == ChoiceKind.PickList || options.any { it.meta != null || it.seedId != null })

/**
 * Header: a 28dp type tile + the question (titleMedium). The tile aligns to the first line; the
 * question's 24dp line box sits 2dp down so a one-line prompt is centred on the 28dp tile.
 */
@Composable
private fun ChoiceHeader(prompt: String, kind: ChoiceKind?) {
    Row(
        modifier = Modifier.padding(start = 14.dp, top = AppDimens.SpacingMd, end = 14.dp, bottom = AppDimens.SpacingXs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (kind != null) ChoiceKindTile(kind)
        ChatMarkdownText(
            markdown = prompt,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (kind != null) Modifier.padding(top = 2.dp) else Modifier,
        )
    }
}

/** Decorative type tile (spec §2.1): palette tint + 16dp icon; delete = errorContainer / error. */
@Composable
private fun ChoiceKindTile(kind: ChoiceKind) {
    val cs = MaterialTheme.colorScheme
    val (fill, tint, icon) = when (kind) {
        ChoiceKind.CreateChecklist -> Triple(GistiColors.paletteTint(0), GistiColors.paletteContent(0), Icons.Outlined.Checklist)
        ChoiceKind.AddItems -> Triple(GistiColors.paletteTint(1), GistiColors.paletteContent(1), Icons.Outlined.AddTask)
        ChoiceKind.Reminder -> Triple(GistiColors.paletteTint(2), GistiColors.paletteContent(2), Icons.Outlined.Alarm)
        ChoiceKind.Batch -> Triple(
            GistiColors.paletteTint(3),
            GistiColors.paletteContent(3),
            Icons.AutoMirrored.Outlined.PlaylistAddCheck,
        )
        ChoiceKind.PickList, ChoiceKind.Move -> Triple(
            GistiColors.paletteTint(1),
            GistiColors.paletteContent(1),
            Icons.AutoMirrored.Outlined.FormatListBulleted,
        )
        ChoiceKind.Delete -> Triple(cs.errorContainer, cs.error, Icons.Outlined.Delete)
    }
    Box(
        modifier = Modifier
            .size(KindTileSize)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
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
    RowKind.Destination, RowKind.Name -> Icons.AutoMirrored.Outlined.FormatListBulleted
    RowKind.Time -> Icons.Outlined.Schedule
    RowKind.File -> Icons.Outlined.AttachFile
    RowKind.Count -> Icons.Outlined.NotificationsNone
    RowKind.DateRange -> Icons.Outlined.Event
    RowKind.Preview, RowKind.Overflow -> null
}

/**
 * "Remember my choice" — a switch row, deliberately NOT a chip.
 *
 * In this block a chip tap EXECUTES. A control that only toggles state must not wear the chip
 * shape, or the contract users learned in D1 ("tap = it happens") stops holding.
 *
 * The whole row is the target ([toggleable] with [Role.Switch]); the [AppSwitch] itself takes a
 * null callback so the tap is not counted twice. Never pre-checked (state rule, unchanged).
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
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 14.dp, vertical = AppDimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Text(
            text = stringResource(Res.string.chat_choice_remember),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Destination options as rows (spec §2.1 OptionRows): leading project dot (the checklist's avatar
 * colour, [GistiColors.avatarContent] of [ChoiceOption.seedId]; a list icon when there is no seed),
 * label `bodyLarge` (2 lines, then ellipsis) and the meta trailing — never dropped, it is what tells
 * two identically named lists apart. Inset dividers between rows start under the label.
 *
 * Tap = execute, like a chip. The executing row swaps its dot for a spinner and its label for the
 * executing label; every other row dims to 38% and stops taking taps.
 */
@Composable
private fun OptionRows(
    options: List<ChoiceOption>,
    executingId: String?,
    executingLabel: String?,
    blockInteractive: Boolean,
    onSelect: (String) -> Unit,
    inset: Boolean,
) {
    val fallbackLoading = stringResource(Res.string.chat_choice_executing_default)
    val start = if (inset) OptionRowStart else 0.dp
    val end = if (inset) 14.dp else 0.dp
    options.forEachIndexed { index, option ->
        val isThisLoading = executingId == option.id
        val dim = if (blockInteractive || isThisLoading) 1f else DisabledAlpha
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    enabled = blockInteractive,
                    role = Role.Button,
                    onClickLabel = option.label,
                    onClick = { onSelect(option.id) },
                )
                .padding(start = start, end = end, top = AppDimens.SpacingSm, bottom = AppDimens.SpacingSm)
                .alpha(dim),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OptionRowGap),
        ) {
            val seed = option.seedId
            Box(modifier = Modifier.size(OptionLeadSlot), contentAlignment = Alignment.Center) {
                when {
                    isThisLoading -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OptionLeadSlot),
                    )
                    seed != null -> Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(GistiColors.avatarContent(seed)),
                    )
                    else -> Icon(
                        imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(OptionLeadSlot),
                    )
                }
            }
            Text(
                text = if (isThisLoading) (executingLabel ?: fallbackLoading) else option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val meta = option.meta
            if (meta != null && !isThisLoading) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (index < options.lastIndex) {
            HorizontalDivider(
                thickness = 1.dp,
                color = AppChatColors.contentOutline(),
                modifier = Modifier.padding(start = start + OptionLeadSlot + OptionRowGap),
            )
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
) {
    val isThisLoading = executingId == option.id
    AiChoiceChip(
        label = option.label,
        role = option.role,
        onClick = { onSelect(option.id) },
        enabled = blockInteractive,
        isLoading = isThisLoading,
        loadingLabel = if (isThisLoading) (executingLabel ?: fallbackLoading) else null,
        leadingIcon = option.leadingIcon(),
        meta = option.meta,
    )
}

/**
 * List of proposed actions inside the bubble (spec §2.1 BatchActionList): a 20dp numbered circle
 * per step (from TWO steps up — "1." on a single action reads like a broken list), destructive steps
 * marked by a trash circle in `errorContainer` AND `error` text (icon + colour, never colour alone).
 *
 * NEVER truncated — takes no `compact` flag on purpose. The agent batch is the full plan the user
 * is being asked to approve: hiding step 3 of 4 in the dock would have them approve actions they
 * cannot see. Every step renders on every surface.
 */
@Composable
private fun BatchActionList(
    items: List<AgentPlanItem>,
    modifier: Modifier = Modifier,
) {
    val numbered = items.size > 1
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (numbered || item.isDestructive) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            // chipFill, not a bare recessed(): on the dark chrome the bubble IS
                            // recessed() and the circle would vanish (spec risk 9).
                            .background(if (item.isDestructive) cs.errorContainer else AppChatColors.chipFill()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.isDestructive) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isDestructive) cs.error else cs.onSurface,
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

/**
 * Leading icon of an option chip: the post-action pair carries its verb (Undo arrow, move-to-list
 * folder — spec §2.1 post-action turn); otherwise by role — trash for Destructive, "+" for Add.
 */
private fun ChoiceOption.leadingIcon(): ImageVector? = when (action) {
    is ChoiceAction.Undo -> Icons.AutoMirrored.Outlined.Undo
    is ChoiceAction.MoveToList -> Icons.Outlined.DriveFileMove
    else -> when (role) {
        ChoiceRole.Destructive -> Icons.Outlined.Delete
        ChoiceRole.Add -> Icons.Outlined.Add
        ChoiceRole.Primary, ChoiceRole.Default, ChoiceRole.Escape -> null
    }
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

private val ChoiceBubbleMaxWidth = 300.dp
private val KindTileSize = 28.dp
private val OptionRowStart = 12.dp
private val OptionLeadSlot = 18.dp
private val OptionRowGap = 10.dp
private const val DisabledAlpha = 0.38f
