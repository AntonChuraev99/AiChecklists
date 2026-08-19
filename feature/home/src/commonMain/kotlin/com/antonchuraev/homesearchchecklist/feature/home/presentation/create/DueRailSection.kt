package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.create_setting_reminder_none
import aichecklists.core.designsystem.generated.resources.due_planner_repeat_off
import aichecklists.core.designsystem.generated.resources.due_preset_in_1_hour
import aichecklists.core.designsystem.generated.resources.due_preset_next_week
import aichecklists.core.designsystem.generated.resources.due_preset_tomorrow
import aichecklists.core.designsystem.generated.resources.due_preset_tonight
import aichecklists.core.designsystem.generated.resources.due_preset_weekend
import aichecklists.core.designsystem.generated.resources.due_rail_no_date
import aichecklists.core.designsystem.generated.resources.item_create_chip_important
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePlannerPanel
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetCell
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetId
import com.antonchuraev.homesearchchecklist.desingsystem.components.DueRailRow
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiSelectableChipItem
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.DueLabelForm
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.label
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.resolveDueLabel
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.resolveRepeatSummaryLabel
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.smartadd.resolveChipLabel
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * The capture dock's `aboveInput` slot: the due rail, the planner it expands into, and the Important
 * toggle.
 *
 * ONE composable for both capture hosts. It replaces `TaskCreateChipsRow` in the dock (that row stays
 * on the checklist detail screen, which is a different surface with a different width budget), and
 * with it the separate Smart-Add token preview: the token is now the SECOND thing the leading chip
 * can be showing, so drawing it again above the rail would be the same answer twice — and it was
 * literally the second line this rail was designed to reclaim.
 *
 * ## Where "Important" went, and why it is here at all
 * The approved rail mock dropped it. It is not droppable: the chip is shipped behaviour of both docks
 * (`GistiItemCreateAction.IMPORTANT` → `TaskDraft.important` → `priority = 1` on both halves of the
 * created pair), and silently deleting a user-facing control is the most expensive class of defect in
 * this project.
 *
 * It sits INSIDE the rail's own `FlowRow`, via [DueRailRow]'s `trailing` slot, and that placement was
 * paid for: hosted as a sibling row underneath — the first shape this took — it cost a permanent
 * extra line of dock height on EVERY capture, including the 96.6% that never set a date. On the
 * over-constrained window that made the dock fill the frame edge to edge and left the page scrim
 * nothing to dim; `CaptureDockShoulderTest` failed on exactly that. Inside the flow the toggle wraps
 * only when the line is genuinely full, which is what a wrapping rail is for.
 *
 * @param nowMillis the clock the whole section is rendered against, so a screenshot can pin it. The
 *   Inbox passes its ticking value; the Calendar tab has none and takes the default.
 */
@Composable
fun DueRailSection(
    draft: TaskDraft,
    due: DraftDueUiState,
    onIntent: (DraftDueIntent) -> Unit,
    onImportantToggle: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AppDimens.ScreenPaddingHorizontal,
    nowMillis: Long = currentTimeMillis(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val now = Instant.fromEpochMilliseconds(nowMillis)

    // What SEND would write, not what the chip tap recorded. The two differ once the dock has been
    // open across the preset's own moment ("Tonight" tapped at 17:50, still open at 18:10), and the
    // rail has to show the answer the user is about to get — otherwise it announces a time that is
    // already in the past and then silently files the task for tomorrow.
    val resolvedDueAt = draft.resolveReminderAtNow(now, timeZone)
    val leadAnswer = dueLeadLabel(draft, resolvedDueAt, nowMillis, timeZone)

    val labels = duePresetLabels()
    val available = availablePresets(now, timeZone)
    val appliedId = draft.reminderPreset?.toDuePresetId()

    // The rail carries the first few offers; the grid carries all of them. Not "as many as fit" —
    // fitting is decided at measure time and cannot be known here, and a rail whose CONTENTS changed
    // with the window would reshuffle under the user between two phones.
    //
    // ⚠️ The applied preset is dropped BEFORE the take, never after. Taken first, the rail showed
    // "[RAIL_PRESET_COUNT] minus the applied one" — and on the Calendar tab, whose draft arrives with
    // TONIGHT already applied, that left exactly ONE offer in the row. The applied answer is already
    // stated by the leading chip; removing it is supposed to free its width for the next offer, not
    // shrink the set. Caught by the Spec review, 2026-08-18.
    val railPresets = available
        .mapNotNull { preset ->
            preset.toDuePresetId()
                ?.takeIf { it != appliedId }
                ?.let { DuePresetChip(id = it, label = labels.getValue(it)) }
        }
        .take(RAIL_PRESET_COUNT)

    val cells = available.mapNotNull { preset ->
        val id = preset.toDuePresetId() ?: return@mapNotNull null
        DuePresetCell(
            id = id,
            label = labels.getValue(id),
            timeLabel = dueTimeLabel(computePresetReminderAt(preset, now, timeZone), nowMillis, timeZone),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        DueRailRow(
            leadLabel = leadAnswer ?: stringResource(Res.string.due_rail_no_date),
            hasDate = leadAnswer != null,
            expanded = due.plannerExpanded,
            presets = railPresets,
            onLeadClick = { onIntent(DraftDueIntent.OnLeadClick) },
            onClearDate = { onIntent(DraftDueIntent.OnClearDate) },
            onPresetClick = { onIntent(DraftDueIntent.OnPresetClick(it)) },
            horizontalPadding = horizontalPadding,
            // The v1 chip component, not a local look-alike: it already carries the `selected`
            // semantics and the 48dp target under a 38dp pill. Inside the rail's own FlowRow, so it
            // wraps only when the line is genuinely full — as a sibling row it cost a line of dock
            // height on every capture, date or no date.
            trailing = {
                GistiSelectableChipItem(
                    icon = Icons.Outlined.StarBorder,
                    label = stringResource(Res.string.item_create_chip_important),
                    selected = draft.important,
                    onClick = onImportantToggle,
                )
            },
        )

        DuePlannerPanel(
            expanded = due.plannerExpanded,
            cells = cells,
            // The GRID is the editor, so the current answer is visible in it — the "exactly one
            // visual answer" rule applies to the RAIL, where the lead chip already states it.
            selectedPreset = appliedId,
            hasDate = leadAnswer != null,
            timeValueLabel = resolvedDueAt
                ?.let { dueTimeLabel(it, nowMillis, timeZone) }
                ?: stringResource(Res.string.create_setting_reminder_none),
            repeatValueLabel = draft.repeat
                ?.let { resolveRepeatSummaryLabel(it) }
                ?: stringResource(Res.string.due_planner_repeat_off),
            onPresetClick = { onIntent(DraftDueIntent.OnPresetClick(it)) },
            onPickDateClick = { onIntent(DraftDueIntent.OnPickDateClick) },
            onTimeClick = { onIntent(DraftDueIntent.OnTimeClick) },
            onRepeatClick = { onIntent(DraftDueIntent.OnRepeatClick) },
            onDoneClick = { onIntent(DraftDueIntent.OnPlannerCollapse) },
            horizontalPadding = horizontalPadding,
        )

    }
}

/**
 * The rail's single answer to "when", already localised — or `null` when there is none.
 *
 * Priority, and every step of it is load-bearing:
 *  1. a staged REPEAT. It is reachable from the planner now, and a saved rule that showed nowhere
 *     would be a control that swallows its tap. It cannot coexist with the others — `withRepeat` and
 *     `withCustomReminder` each clear the other — so this branch never hides a competing answer.
 *  2. the resolved one-shot date.
 *  3. the Smart-Add token: the parser recognised a date in the typed text and nothing has overridden
 *     it, which is exactly the condition under which the send path applies it.
 */
@Composable
private fun dueLeadLabel(
    draft: TaskDraft,
    resolvedDueAt: Long?,
    nowMillis: Long,
    timeZone: TimeZone,
): String? {
    draft.repeat?.let { return resolveRepeatSummaryLabel(it) }
    if (resolvedDueAt != null) {
        return resolveDueLabel(
            reminderAt = resolvedDueAt,
            repeatRule = null,
            nowMillis = nowMillis,
            timeZone = timeZone,
        )?.label()
    }
    return draft.parsedToken?.let { resolveChipLabel(it.display) }
}

/**
 * The time under a planner cell: bare "19:00" while the day is obvious from the label above it,
 * the full relative form ("Sat 10:00", "Sep 14") once it is not.
 *
 * Built from [resolveDueLabel] rather than from a local formatter so the vocabulary and the
 * day-before-month order stay the translation team's decision, in one place, for the whole app.
 */
@Composable
private fun dueTimeLabel(atMillis: Long, nowMillis: Long, timeZone: TimeZone): String {
    val form = resolveDueLabel(
        reminderAt = atMillis,
        repeatRule = null,
        nowMillis = nowMillis,
        timeZone = timeZone,
    )?.form
    return when (form) {
        // "Tonight"/"Tomorrow" already name the day; repeating it under them wastes the cell's
        // second line on a word the user just read.
        is DueLabelForm.Today -> form.time
        is DueLabelForm.Tomorrow -> form.time
        null -> ""
        else -> form.label()
    }
}

/**
 * The capture dock's `belowInput` slot, folded away while the due planner is open.
 *
 * ## Why it has to fold at all
 * Measured on a 320dp x 568dp window at fontScale 1.3 in RU: the expanded planner plus this row
 * overruns the window, and the last two source pills are cut off by its bottom edge. That is with
 * `WindowInsets.ime = 0` — Robolectric raises no keyboard — so on a device, where the keyboard takes
 * roughly 250dp more, it is worse. The dock is already 201-213dp with both rows before the planner
 * adds its grid.
 *
 * ## Why it ANIMATES rather than disappearing
 * A row that vanishes on an unrelated tap reads as a feature that was removed, and this project has
 * shipped exactly that report before. Fading and shrinking says "put away", and the same animation
 * run backwards is what brings it back — which is the half that must not be forgotten: the row is the
 * only route into Analyze from this surface, and content → checklist is half of all checklist
 * creation here.
 *
 * The spec is [AppMotion]'s tween rather than its spring, on that object's own instruction: an
 * unsettled spring driving `shrinkVertically` re-measures the subtree every frame, and on a loaded
 * Skiko canvas that is judder. [LocalReducedMotion] collapses the size change to a snap and shortens
 * the fade, matching every other collapsing surface in this design system.
 */
@Composable
fun CollapsibleSourceRow(
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val sizeSpec: FiniteAnimationSpec<IntSize> =
        if (reduced) snap() else AppMotion.spatialDefaultTween()
    val alphaSpec: FiniteAnimationSpec<Float> =
        if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()

    AnimatedVisibility(
        visible = !collapsed,
        modifier = modifier,
        enter = expandVertically(animationSpec = sizeSpec) + fadeIn(animationSpec = alphaSpec),
        exit = shrinkVertically(animationSpec = sizeSpec) + fadeOut(animationSpec = alphaSpec),
    ) {
        content()
    }
}

/** The SHORT preset copy — see the `due_preset_*` note in `strings.xml` for why it is its own set. */
@Composable
private fun duePresetLabels(): Map<DuePresetId, String> = mapOf(
    DuePresetId.TONIGHT to stringResource(Res.string.due_preset_tonight),
    DuePresetId.TOMORROW to stringResource(Res.string.due_preset_tomorrow),
    DuePresetId.WEEKEND to stringResource(Res.string.due_preset_weekend),
    DuePresetId.IN_1_HOUR to stringResource(Res.string.due_preset_in_1_hour),
    DuePresetId.NEXT_WEEK to stringResource(Res.string.due_preset_next_week),
)

/**
 * How many offers the RAIL shows, out of the five the grid holds.
 *
 * TWO, and the binding constraint is Important rather than the presets themselves.
 *
 * At three, the row measured `No date ⌄` + Tonight + Tomorrow + Weekend + Important ≈ 421dp against
 * the 380dp available on a 412dp window — so `FlowRow` wrapped Important onto a second line and the
 * dock grew by a row it pays for on EVERY capture, including the vast majority that never set a
 * date. `CaptureDockShoulderTest` caught it: on the over-constrained window the dock then filled the
 * frame edge to edge and the scrim had no page left to dim.
 *
 * Owner's call, 2026-08-18, over the two alternatives: Important stays a visible one-tap control in
 * the row, and the third offer moves into the grid — which is one tap away and holds all five. The
 * offer that loses its place is the LAST of [presetDisplayOrder], never a reshuffle: the row's
 * contents must not change between two phones.
 *
 * ⚠️ What two buys is width on a WIDE window, not a single line everywhere. Recorded frames:
 * at 412dp the row fits on one line, at 360dp Important still wraps — the English labels plus the
 * lead chip already spend the budget. So the saving is real where it was measured and absent on the
 * commonest phone width; if the count is ever revisited, revisit it against a 360dp frame, because
 * there the third offer costs nothing that the second has not already cost.
 */
private const val RAIL_PRESET_COUNT = 2
