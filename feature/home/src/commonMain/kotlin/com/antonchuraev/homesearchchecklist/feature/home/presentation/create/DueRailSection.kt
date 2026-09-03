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
import aichecklists.core.designsystem.generated.resources.due_rail_clear_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_clear_repeat_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_no_date
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePlannerPanel
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetCell
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.DuePresetId
import com.antonchuraev.homesearchchecklist.desingsystem.components.DueRailRow
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
 * The capture dock's `aboveInput` slot: the due rail and the planner it expands into.
 *
 * ONE composable for both capture hosts. It replaces `TaskCreateChipsRow` in the dock (that row stays
 * on the checklist detail screen, which is a different surface with a different width budget), and
 * with it the separate Smart-Add token preview: the token is now the SECOND thing the leading chip
 * can be showing, so drawing it again above the rail would be the same answer twice — and it was
 * literally the second line this rail was designed to reclaim.
 *
 * ## Where "Important" went — it is NOT here any more
 * It used to ride in [DueRailRow]'s `trailing` slot, pinned right of the scrolling offers, glyph-only
 * and filled `primary` when on. The owner reopened that on 2026-09-03 ("кнопка добавить в избранное…
 * очень плохо выглядит и находится в плохом месте") and it moved into the INPUT ROW, beside the
 * submit "+", as
 * [ImportantStarToggle][com.antonchuraev.homesearchchecklist.desingsystem.components.ImportantStarToggle]
 * — passed by each host to `QuickCaptureDock(trailingToggle = …)`.
 *
 * Moved, not dropped: the flag is shipped behaviour of both docks
 * (`GistiItemCreateAction.IMPORTANT` → `TaskDraft.important` → `priority = 1` on both halves of the
 * created pair), and silently deleting a user-facing control is the most expensive class of defect in
 * this project. Two things improved with the move: it no longer folds away when the planner opens (it
 * was unreachable in that state), and the ~56dp it held outside the rail's scroll goes back to the
 * offers, which were clipping mid-word at 360dp.
 *
 * @param nowMillis the clock the whole section is rendered against, so a screenshot can pin it. The
 *   Inbox passes its ticking value; the Calendar tab has none and takes the default.
 */
@Composable
fun DueRailSection(
    draft: TaskDraft,
    due: DraftDueUiState,
    onIntent: (DraftDueIntent) -> Unit,
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
            timeLabel = dueTimeLabel(id, computePresetReminderAt(preset, now, timeZone), nowMillis, timeZone),
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
            // The `×` clears whatever the lead chip is showing, and with a repeat staged that is the
            // RULE, not a date — `hasDate` is true for a repeat too. One label announced "Clear
            // date" over a chip reading "Every day 09:00", which is a screen reader stating the
            // wrong consequence for a destructive action.
            clearDateLabel = if (draft.repeat != null) {
                stringResource(Res.string.due_rail_clear_repeat_a11y)
            } else {
                stringResource(Res.string.due_rail_clear_a11y)
            },
            // No trailing slot any more. Important moved into the INPUT ROW on 2026-09-03 — see this
            // file's KDoc and `ImportantStarToggle`. The parameter stays on [DueRailRow] as design
            // system API; this dock passes nothing.
            trailing = null,
        )

        DuePlannerPanel(
            expanded = due.plannerExpanded,
            cells = cells,
            // The GRID is the editor, so the current answer is visible in it — the "exactly one
            // visual answer" rule applies to the RAIL, where the lead chip already states it.
            selectedPreset = appliedId,
            timeValueLabel = resolvedDueAt
                ?.let { dueTimeLabel(appliedId, it, nowMillis, timeZone) }
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
 *  2. the resolved one-shot date — which, since Smart-Add was wired to the dock on 2026-08-19, is
 *     ALSO where a recognised phrase arrives: `resolveReminderAtNow` falls back to the token's own
 *     instant, so a typed "tomorrow at 7" reaches this branch and is formatted by the same
 *     formatter as a tapped preset. That is the point — the chip and the send path read one
 *     function, so what is shown and what is written cannot disagree.
 *  3. the Smart-Add token's own display string. A fallback for a token that carries no resolvable
 *     instant, which today's parser barely produces; branch 2 catches the rest.
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
 * the full relative form ("Tomorrow 19:00", "Sat 10:00", "Sep 14") once it is not.
 *
 * Built from [resolveDueLabel] rather than from a local formatter so the vocabulary and the
 * day-before-month order stay the translation team's decision, in one place, for the whole app.
 *
 * ## The day word is dropped only by the cell that already said it
 * [id] is what makes that decidable, and it is not decoration. "Tonight" rolls over: past
 * [EVENING_HOUR] the preset resolves to TOMORROW 19:00 (`TaskDraft.computePresetReminderAt`). With
 * the day word stripped from every Tomorrow form alike, the 20:30 cell read
 *
 * ```
 * Tonight
 * 19:00
 * ```
 *
 * for a moment 22.5 hours away — and one tap later the lead chip corrected itself to "Tomorrow
 * 19:00", so the panel contradicted its own result (UI audit, 2026-08-19). Only the cell LABELLED
 * tomorrow may drop the word tomorrow.
 */
@Composable
private fun dueTimeLabel(
    id: DuePresetId?,
    atMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone,
): String {
    val form = resolveDueLabel(
        reminderAt = atMillis,
        repeatRule = null,
        nowMillis = nowMillis,
        timeZone = timeZone,
    )?.form
    return when (form) {
        // Nothing in this set resolves to a same-day moment other than the one whose label says so.
        is DueLabelForm.Today -> form.time
        is DueLabelForm.Tomorrow ->
            if (id == DuePresetId.TOMORROW) form.time else form.label()
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
 * THREE since 2026-08-19, and the change is a consequence of the row, not a new opinion about
 * offers. While the rail wrapped, this constant was pinned at TWO by Important rather than by the
 * presets: at three the row measured `No date ⌄` + Tonight + Tomorrow + Weekend + Important ≈ 421dp
 * against the 380dp available on a 412dp window, `FlowRow` wrapped the toggle onto a second line,
 * and the dock grew by a row it paid for on EVERY capture (`CaptureDockShoulderTest`, 2026-08-18).
 * That was already only half a fix — the recorded frames showed Important still wrapping at 360dp,
 * the commonest phone width there is, where the two-offer row spends the budget just as fully.
 *
 * [DueRailRow] scrolls now, so overflow costs a swipe instead of a line of dock height, and the
 * constant is free to answer the question it is actually about: how many offers are worth showing
 * before the grid. Three covers the shape of a captured day — tonight, tomorrow, the weekend —
 * without the row needing to scroll at all on a 412dp window in English.
 *
 * The offers that lose their place are the LAST of [presetDisplayOrder], never a reshuffle: the
 * row's contents must not change between two phones. They are one tap away in the grid, which holds
 * all five with the time each resolves to.
 */
private const val RAIL_PRESET_COUNT = 3
