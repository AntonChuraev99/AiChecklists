package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.item_create_chip_important
import aichecklists.core.designsystem.generated.resources.item_create_chip_in_1_hour
import aichecklists.core.designsystem.generated.resources.item_create_chip_pick_time
import aichecklists.core.designsystem.generated.resources.item_create_chip_repeat
import aichecklists.core.designsystem.generated.resources.item_create_chip_tomorrow_morning
import aichecklists.core.designsystem.generated.resources.item_create_chip_tonight
import com.antonchuraev.homesearchchecklist.desingsystem.components.TokenChipPreview
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiItemCreateAction
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.gistiItemCreatePromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.buildRepeatSummary
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.formatReminderDateTime
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.containsRepeat
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.resolveChipLabel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * The chip row of the task-create dock: four single-select reminder presets plus the Important and
 * Repeat toggles, with the Smart-Add token preview above them.
 *
 * Reads a [TaskDraft] and reports a [GistiItemCreateAction]; it owns no state and no rules, so the
 * same row serves the Inbox, the Today tab and the checklist detail screen without any of them
 * knowing about the others. (It began as `ItemCreateChipsRow`, private inside the 4k-line detail
 * screen — which is why the two v2 tabs shipped with no chips at all.)
 *
 * @param horizontalPadding edge inset for the token chip and the chip row. Defaults to the screen
 *   padding because a dock hosts this with no outer padding of its own; an inline host inside an
 *   already-padded list passes `0.dp` (double padding — rule `ui-card-patterns`).
 */
@Composable
fun TaskCreateChipsRow(
    draft: TaskDraft,
    onAction: (GistiItemCreateAction) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AppDimens.ScreenPaddingHorizontal,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /**
     * "Pick time…" and "Repeat" open a date/time picker and the repeat-config sheet (the latter with
     * its own free-tier paywall gate). Only the checklist detail screen hosts those sheets today, so
     * the two v2 tabs switch these chips OFF rather than render buttons that do nothing — a chip that
     * swallows a tap silently is the failure mode `user-feedback.md` exists to prevent.
     */
    showPickTime: Boolean = true,
    showRepeat: Boolean = true,
) {
    // "Pick time…" shows the chosen absolute datetime once a custom time is set, so the chip itself
    // reports the answer instead of re-asking the question.
    val pickTimeLabel = draft.reminderAt
        ?.takeIf { draft.reminderPreset == ItemCreateReminderPreset.CUSTOM }
        ?.let { formatReminderDateTime(Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone)) }
        ?: stringResource(Res.string.item_create_chip_pick_time)
    val repeatLabel = draft.repeat
        ?.let { buildRepeatSummary(it) }
        ?: stringResource(Res.string.item_create_chip_repeat)
    val selectedReminder = when (draft.reminderPreset) {
        ItemCreateReminderPreset.ONE_HOUR -> GistiItemCreateAction.REMIND_1H
        ItemCreateReminderPreset.TOMORROW_MORNING -> GistiItemCreateAction.REMIND_TOMORROW_MORNING
        ItemCreateReminderPreset.TONIGHT -> GistiItemCreateAction.REMIND_TONIGHT
        ItemCreateReminderPreset.CUSTOM -> GistiItemCreateAction.REMIND_PICK
        ItemCreateReminderPreset.WEEKEND, ItemCreateReminderPreset.NEXT_WEEK -> null
        null -> null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Hidden once an explicit reminder is set: the parsed phrase no longer decides the time, and
        // showing both would present two answers to "when" with no clue which one wins.
        val token = draft.parsedToken
        if (token != null && draft.reminderAt == null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(bottom = AppDimens.SpacingSm)
            ) {
                TokenChipPreview(
                    label = resolveChipLabel(token.display),
                    isRepeat = token.display.containsRepeat(),
                )
            }
        }
        GistiSelectableChipRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            chips = gistiItemCreatePromptChips(
                in1HourLabel = stringResource(Res.string.item_create_chip_in_1_hour),
                tomorrowMorningLabel = stringResource(Res.string.item_create_chip_tomorrow_morning),
                tonightLabel = stringResource(Res.string.item_create_chip_tonight),
                pickTimeLabel = pickTimeLabel,
                importantLabel = stringResource(Res.string.item_create_chip_important),
                repeatLabel = repeatLabel,
                selectedReminder = selectedReminder,
                importantSelected = draft.important,
                repeatSelected = draft.repeat != null,
            ).filterNot { chip ->
                (chip.action == GistiItemCreateAction.REMIND_PICK && !showPickTime) ||
                    (chip.action == GistiItemCreateAction.REPEAT && !showRepeat)
            },
            onChipClick = onAction,
        )
    }
}
