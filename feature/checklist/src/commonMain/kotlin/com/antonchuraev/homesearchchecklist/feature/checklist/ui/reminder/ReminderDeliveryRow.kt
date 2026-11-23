package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.reminder_channel_caption
import aichecklists.core.designsystem.generated.resources.reminder_channel_collapsed_sd
import aichecklists.core.designsystem.generated.resources.reminder_channel_connect_body
import aichecklists.core.designsystem.generated.resources.reminder_channel_connect_body_web
import aichecklists.core.designsystem.generated.resources.reminder_channel_connect_title
import aichecklists.core.designsystem.generated.resources.reminder_channel_expanded_sd
import aichecklists.core.designsystem.generated.resources.reminder_channel_hide_options
import aichecklists.core.designsystem.generated.resources.reminder_channel_save_failed_short
import aichecklists.core.designsystem.generated.resources.reminder_channel_show_options
import aichecklists.core.designsystem.generated.resources.reminder_channel_title
import aichecklists.core.designsystem.generated.resources.reminder_channel_value_web_off
import aichecklists.core.designsystem.generated.resources.reminder_channel_value_web_on
import aichecklists.core.designsystem.generated.resources.telegram_reminders_option_both
import aichecklists.core.designsystem.generated.resources.telegram_reminders_option_push
import aichecklists.core.designsystem.generated.resources.telegram_reminders_option_telegram
import aichecklists.core.designsystem.generated.resources.telegram_reminders_option_telegram_body
import aichecklists.core.designsystem.generated.resources.telegram_reminders_save_failed
import aichecklists.core.designsystem.generated.resources.telegram_reminders_saving_cd
import aichecklists.core.designsystem.generated.resources.telegram_reminders_web_off
import aichecklists.core.designsystem.generated.resources.telegram_reminders_web_on
import aichecklists.core.designsystem.generated.resources.telegram_reminders_web_on_body
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.isWebOn
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.TelegramLogo
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * From this font scale on, the trailing chevron / arrow is not composed (spec §7): at 320dp × 2.0 RU
 * «Напоминания» overflowed under it. Expanded / collapsed stays conveyed by the content below the
 * header and by `stateDescription`.
 */
private const val TRAILING_HIDE_FONT_SCALE = 1.5f

/** The leading icon column and the radio control slot share this width, so they line up (frame C1). */
private val LeadingSlot = 24.dp
private val SpinnerSize = 20.dp
private val SpinnerStroke = 2.dp

/** Android / iOS option order — the same as the Telegram screen (addendum §2.1). */
private val ChannelOptions = listOf(ReminderChannel.Push, ReminderChannel.Telegram, ReminderChannel.Both)

/**
 * "Where all reminders go" row of the reminder sheet (spec
 * `docs/designs/telegram-channel-banner-2026-11-21/spec.md` §3): [ReminderDeliveryRowUi.Connect]
 * leads to the Telegram screen, [ReminderDeliveryRowUi.Choice] is a card that expands in place into
 * the channel radio group. [expanded] is the sheet's UI-local state.
 */
@Composable
internal fun ReminderDeliveryRow(
    row: ReminderDeliveryRowUi,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    callbacks: ReminderDeliveryCallbacks,
) {
    when (row) {
        is ReminderDeliveryRowUi.Connect -> DeliveryConnectRow(web = row.web, onClick = callbacks.onConnectClick)
        is ReminderDeliveryRowUi.Choice -> DeliveryChoiceCard(
            row = row,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            callbacks = callbacks,
        )
    }
}

/** Not linked: the anatomy of `CalendarActionRow`, top-aligned for a wrapped two-line text column. */
@Composable
private fun DeliveryConnectRow(web: Boolean, onClick: () -> Unit) {
    val titleStyle = MaterialTheme.typography.bodyLarge
    val iconPad = firstLinePad(LeadingSlot, titleStyle)
    // AppCard(onClick) carries no role of its own (see AppCard) — TalkBack must hear "button".
    AppCard(
        onClick = onClick,
        modifier = Modifier
            .semantics { role = Role.Button }
            .testTag("reminder_sheet_delivery_connect"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            TelegramLogo(modifier = Modifier.padding(top = iconPad), size = LeadingSlot)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.reminder_channel_connect_title),
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (web) Res.string.reminder_channel_connect_body_web else Res.string.reminder_channel_connect_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showTrailing()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = iconPad).size(LeadingSlot),
                )
            }
        }
    }
}

@Composable
private fun DeliveryChoiceCard(
    row: ReminderDeliveryRowUi.Choice,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    callbacks: ReminderDeliveryCallbacks,
) {
    val reducedMotion = LocalReducedMotion.current
    AppCard(
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.testTag("reminder_sheet_delivery_card"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ChoiceHeader(
                row = row,
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) },
                reducedMotion = reducedMotion,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    expandVertically(AppMotion.spatialDefaultTween()) + fadeIn(AppMotion.effectsDefaultTween())
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    shrinkVertically(AppMotion.spatialDefaultTween()) + fadeOut(AppMotion.effectsDefaultTween())
                },
            ) {
                ChoiceOptions(row = row, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun ChoiceHeader(
    row: ReminderDeliveryRowUi.Choice,
    expanded: Boolean,
    onToggle: () -> Unit,
    reducedMotion: Boolean,
) {
    val titleStyle = MaterialTheme.typography.bodyLarge
    val iconPad = firstLinePad(LeadingSlot, titleStyle)
    val clickLabel = stringResource(
        if (expanded) Res.string.reminder_channel_hide_options else Res.string.reminder_channel_show_options,
    )
    val stateText = stringResource(
        if (expanded) Res.string.reminder_channel_expanded_sd else Res.string.reminder_channel_collapsed_sd,
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else AppMotion.effectsDefaultTween(),
        label = "deliveryArrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onToggle)
            .semantics { stateDescription = stateText }
            .testTag("reminder_sheet_delivery_header")
            .padding(AppDimens.CardPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        // One spinner on screen, never two: collapsed → here; expanded → in the tapped radio slot.
        Box(
            modifier = Modifier.padding(top = iconPad).size(LeadingSlot),
            contentAlignment = Alignment.Center,
        ) {
            if (row.saving != null && !expanded) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SpinnerSize).clearAndSetSemantics { },
                    strokeWidth = SpinnerStroke,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TelegramLogo(size = LeadingSlot)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.reminder_channel_title),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Collapsed after a failed save, the value line reports it (until the next tap); the
            // expanded card reports it under the radio group instead, so the header stays the value.
            val failedHere = row.failed && !expanded
            Text(
                text = if (failedHere) {
                    stringResource(Res.string.reminder_channel_save_failed_short)
                } else {
                    stringResource(row.valueRes())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (failedHere) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showTrailing()) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = iconPad)
                    .size(LeadingSlot)
                    .rotate(arrowRotation),
            )
        }
    }
}

@Composable
private fun ChoiceOptions(row: ReminderDeliveryRowUi.Choice, callbacks: ReminderDeliveryCallbacks) {
    // Where the header's text column starts: the radio titles and the caption align to it.
    val textStart = AppDimens.CardPadding + LeadingSlot + AppDimens.SpacingMd
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(start = textStart),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = stringResource(Res.string.reminder_channel_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = textStart, end = AppDimens.CardPadding, top = AppDimens.SpacingSm),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = AppDimens.CardPadding),
        ) {
            val saving = row.saving
            if (row.web) {
                listOf(true, false).forEach { on ->
                    DeliveryOptionRow(
                        title = stringResource(
                            if (on) Res.string.telegram_reminders_web_on else Res.string.telegram_reminders_web_off,
                        ),
                        supporting = if (on) stringResource(Res.string.telegram_reminders_web_on_body) else null,
                        selected = saving == null && row.confirmed.isWebOn() == on,
                        saving = saving != null && saving.isWebOn() == on,
                        onClick = { callbacks.onWebClick(on) },
                        modifier = Modifier.testTag(
                            if (on) "reminder_sheet_delivery_web_on" else "reminder_sheet_delivery_web_off",
                        ),
                    )
                }
            } else {
                ChannelOptions.forEach { option ->
                    DeliveryOptionRow(
                        title = stringResource(option.titleRes()),
                        supporting = if (option == ReminderChannel.Telegram) {
                            stringResource(Res.string.telegram_reminders_option_telegram_body)
                        } else {
                            null
                        },
                        selected = saving == null && row.confirmed == option,
                        saving = saving == option,
                        onClick = { callbacks.onChannelClick(option) },
                        modifier = Modifier.testTag("reminder_sheet_delivery_${option.name.lowercase()}"),
                    )
                }
            }
        }
        if (row.failed) {
            Text(
                text = stringResource(Res.string.telegram_reminders_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(start = textStart, end = AppDimens.CardPadding, bottom = AppDimens.SpacingMd)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * One radio row (the addendum's option row, without the group skin — the rows already sit inside a
 * card). The whole row is the click target (≥ 48dp); the RadioButton itself is not clickable.
 * [saving] swaps the radio for a spinner in the same 24dp slot and announces "Saving".
 */
@Composable
private fun DeliveryOptionRow(
    title: String,
    supporting: String?,
    selected: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleStyle = MaterialTheme.typography.bodyLarge
    val controlPad = firstLinePad(LeadingSlot, titleStyle)
    val savingCd = stringResource(Res.string.telegram_reminders_saving_cd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (saving) Modifier.semantics { stateDescription = savingCd } else Modifier)
            .padding(vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Box(
            modifier = Modifier.padding(top = controlPad).size(LeadingSlot),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SpinnerSize).clearAndSetSemantics { },
                    strokeWidth = SpinnerStroke,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                RadioButton(selected = selected, onClick = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun showTrailing(): Boolean = LocalDensity.current.fontScale < TRAILING_HIDE_FONT_SCALE

/**
 * Top padding that puts the centre of a [leading]-tall element on the centre of the first line of
 * [style] text (the text keeps its own top). At fontScale 1.0 bodyLarge's line is 24dp → 0; at 2.0
 * it is 48dp → 12dp. Never negative: a line shorter than the element leaves the element on top.
 */
@Composable
private fun firstLinePad(leading: Dp, style: TextStyle): Dp {
    val lineHeight = style.lineHeight
    if (lineHeight.type != TextUnitType.Sp) return 0.dp
    val line = with(LocalDensity.current) { lineHeight.toDp() }
    return ((line - leading) / 2).coerceAtLeast(0.dp)
}

/** The collapsed value line: the confirmed channel (web describes Telegram only, never "phone"). */
private fun ReminderDeliveryRowUi.Choice.valueRes(): StringResource = when {
    web && confirmed.isWebOn() -> Res.string.reminder_channel_value_web_on
    web -> Res.string.reminder_channel_value_web_off
    else -> confirmed.titleRes()
}

private fun ReminderChannel.titleRes(): StringResource = when (this) {
    ReminderChannel.Push -> Res.string.telegram_reminders_option_push
    ReminderChannel.Telegram -> Res.string.telegram_reminders_option_telegram
    ReminderChannel.Both -> Res.string.telegram_reminders_option_both
}
