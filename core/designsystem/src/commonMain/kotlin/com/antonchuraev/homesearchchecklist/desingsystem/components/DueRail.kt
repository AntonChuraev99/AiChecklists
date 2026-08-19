package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.due_planner_done
import aichecklists.core.designsystem.generated.resources.due_planner_pick_date
import aichecklists.core.designsystem.generated.resources.due_planner_repeat
import aichecklists.core.designsystem.generated.resources.due_planner_time
import aichecklists.core.designsystem.generated.resources.due_rail_clear_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_collapsed_a11y
import aichecklists.core.designsystem.generated.resources.due_rail_expanded_a11y
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSchedule
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import org.jetbrains.compose.resources.stringResource

/**
 * The due-date presets the capture dock offers, as VALUES rather than as UI.
 *
 * Resolving one of these to an instant is the host's job and deliberately not this module's: the
 * arithmetic needs a time zone and the current clock (`Instant.plus(1, DateTimeUnit.DAY, zone)`,
 * never a field-wise add on `LocalDateTime`, which breaks across a DST switch), and it has to be
 * re-run at SEND time so a "Tonight" picked at 17:50 and sent at 18:10 does not land in the past.
 * `core:designsystem` therefore names the offers and renders them; it never computes one.
 *
 * There is no `PICK_DATE` entry. Picking an arbitrary date is not a preset — it opens a picker the
 * design system may not host (see [DuePlannerPanel]) — so it travels as its own callback and cannot
 * be accidentally handled by a `when` branch that thinks it produced a value.
 */
enum class DuePresetId {
    TONIGHT,
    TOMORROW,
    WEEKEND,
    IN_1_HOUR,
    NEXT_WEEK,
}

/**
 * One preset as it appears in [DueRailRow]: an id and the short label the host localised for it.
 *
 * The label is a plain `String` rather than a resource id because the host already resolves its own
 * copy and because "short" is a *product* requirement this type cannot enforce — the rail is a
 * wrapping row on a 320dp dock, so `Сегодня вечером` (the existing `item_create_chip_tonight` in RU)
 * pushes it onto a second line on its own. Rail copy is the SHORT set (`due_preset_*`).
 */
@Immutable
data class DuePresetChip(val id: DuePresetId, val label: String)

/**
 * One preset as it appears in [DuePlannerPanel]: the same id, plus the time it would resolve to
 * ("19:00", "Sat 10:00") shown under the label.
 *
 * The resolved time is what makes the grid a decision rather than a guess — the whole reason the
 * panel exists is that "Tonight" means nothing until the user can see it is 19:00. The host formats
 * it, for the same reason it resolves the instant.
 */
@Immutable
data class DuePresetCell(val id: DuePresetId, val label: String, val timeLabel: String)

/** Visible height floor of the two-line planner cell, before it grows for a wrapped label. */
private val DueCellMinHeight = 62.dp

/** Corner of a planner cell. `AppShapes.medium`'s 16dp on a 62dp box; a pill would read as a chip. */
private val DueCellCorner = 14.dp

/**
 * Inner horizontal padding of a rail chip's label.
 *
 * 12dp rather than the chat dock's 14dp, and the difference is measured rather than taste: at 14dp
 * the four English chips of the approved mock needed 366dp of a 328dp rail and dropped "Weekend"
 * onto a second line at 360dp / fontScale 1.0 — the ONE configuration the mock shows on one line.
 * Wrapping is this row's designed behaviour under pressure, not its resting state.
 */
private val ChipLabelPadding = AppDimens.SpacingMd

/** Icon size inside a rail chip / planner control. Matches [SourceRow]'s pills. */
private val DueIconSize = 16.dp

/**
 * The due-date rail: the current answer on the left, one-tap presets after it, on ONE line always.
 *
 * ## One scrolling line, not a wrapping one
 * Owner's call, 2026-08-19, over the wrapping row this shipped as: «Important при создании пункта в
 * 2 ряду — можно в 1 ряд листающийся». A wrapping row spends a whole line of dock height on the
 * commonest phone width there is: at 360dp the lead chip plus two English offers plus the Important
 * toggle already overrun the 328dp available, so the toggle dropped to a second row on EVERY
 * capture — including the 96.6% that never set a date. Height above the keyboard is the scarcest
 * thing this surface has.
 *
 * The row therefore scrolls, and the two objections the wrapping shape was chosen for are answered
 * rather than accepted:
 *  - **TalkBack.** A plain [Row] under `horizontalScroll`, NOT a `LazyRow`: it composes every chip
 *    whether or not it is in the viewport, so all of them are in the semantics tree and TalkBack
 *    scrolls the container to reach them. A `LazyRow` here would offer a screen-reader user whatever
 *    happened to fit, which is why the rail must never become one.
 *  - **Discoverability.** A chip that has scrolled off is invisible, so the row says that it has
 *    one: the fading edge below is drawn only on a side that can actually scroll, which makes the
 *    last visible pill bleed out rather than end — the standard "there is more this way" cue.
 *
 * ⚠️ **wasmJs.** Horizontal scroll under Skiko has a history with the mouse wheel and the trackpad
 * (JetBrains/compose-multiplatform#1064, #4975 — both closed, neither with a version this project
 * can point at). Touch drag is unaffected, and on a desktop-width window the row does not overflow
 * at all, so the wheel never has to carry it. Verify on `:9090` after any change here anyway; that
 * is the project rule for wasmJs and this is exactly the class of thing it exists for.
 *
 * ## The rail stands down while the planner is open
 * With [expanded] true the offers and [trailing] fold away and the lead chip is left alone. Both
 * halves are load-bearing:
 *  - The grid below holds every offer WITH the time it resolves to. Keeping the pills up here too
 *    puts two controls carrying the same accessible name ("Tonight") on one screen — ambiguous to a
 *    screen reader and to any test matching that label.
 *  - Important is not an answer to "when". While the panel is the whole conversation it is noise,
 *    and it is the one chip the dock cannot afford beside a 62dp grid at fontScale 1.3.
 * It FOLDS rather than vanishing, and comes back the same way — a control that disappears on an
 * unrelated tap reads as a feature that was removed, which this project has shipped and heard about.
 *
 * ## Exactly one visual answer to "when"
 * The lead chip carries the answer and nothing else does. The presets are **never** drawn selected:
 * they are buttons whose label IS their value, not a set with a current member. The host is expected
 * to drop the applied preset from [presets] entirely — showing "Tomorrow" as a checked chip next to
 * a lead chip reading "Tomorrow, 9:00" states the same fact twice AND costs the width that pushed
 * the rail onto a second line in the first place.
 *
 * ## Two targets in one chip
 * With a date set, the lead chip is a label and a `×`, and they do different things. So the CHIP is
 * not clickable — the two halves are, separately, because `Modifier.clickable` merges its
 * descendants: a clickable container with a clickable child inside it collapses into one accessible
 * node and the clear action disappears from the semantics tree. The `×` half is a full
 * [AppDimens.MinTouchTarget] square of its own.
 *
 * @param leadLabel the current answer, already formatted ("No date", "Tomorrow, 9:00"). Formatting a
 *   date is locale-shaped (day-before-month is a translation decision) so it is resolved outside.
 * @param hasDate whether [leadLabel] is an answer or the empty placeholder. Drives the tonal fill and
 *   whether the `×` exists at all — it is not derivable from the label without parsing it.
 * @param expanded whether [DuePlannerPanel] is open. Drives the chevron direction and the chip's
 *   `stateDescription`; without the latter, opening the panel is not an EVENT for a screen reader,
 *   it is a silent layout change.
 * @param presets the offers to show, already filtered by the host (applied preset removed).
 * @param onLeadClick toggle the planner panel.
 * @param onClearDate drop the date. Only reachable while [hasDate].
 * @param onPresetClick a preset was tapped. The host resolves it against the clock and the zone.
 * @param horizontalPadding the rail's own edge inset. A parameter because the dock's `aboveInput`
 *   slot deliberately applies none — see [QuickCaptureDock].
 */
@Composable
fun DueRailRow(
    leadLabel: String,
    hasDate: Boolean,
    expanded: Boolean,
    presets: List<DuePresetChip>,
    onLeadClick: () -> Unit,
    onClearDate: () -> Unit,
    onPresetClick: (DuePresetId) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AppDimens.ScreenPaddingHorizontal,
    clearDateLabel: String = stringResource(Res.string.due_rail_clear_a11y),
    expandedStateLabel: String = stringResource(Res.string.due_rail_expanded_a11y),
    collapsedStateLabel: String = stringResource(Res.string.due_rail_collapsed_a11y),
    /**
     * Chips that belong to the same line but are NOT answers to "when" — in practice the Important
     * toggle the dock has always carried.
     *
     * A slot rather than a typed parameter, for the same reason [QuickCaptureDock] takes slots: the
     * toggle is driven by feature-layer draft state that has no business in the design system.
     *
     * It is rendered INSIDE this row, last, and that placement is the whole point. Hosted as a
     * sibling row underneath instead, it costs a permanent extra line of dock height — on the
     * over-constrained window that filled the frame edge to edge and left the scrim no page to dim
     * (`CaptureDockShoulderTest`, 2026-08-18). Here it costs nothing but the scroll it may push the
     * last offer under, and it folds away entirely while the planner is open.
     */
    trailing: (@Composable () -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val reduced = LocalReducedMotion.current

    // Opening the planner rewrites what this row holds, and whatever the user had scrolled to is not
    // it. Snap back so the ANSWER — the one chip that survives the fold — is what is under the
    // finger when the grid appears, instead of an empty stretch the offers just left.
    LaunchedEffect(expanded) {
        if (expanded) scrollState.scrollTo(0)
    }

    val foldEnter = expandHorizontally(
        animationSpec = if (reduced) snap() else AppMotion.spatialDefaultTween(),
    ) + fadeIn(
        animationSpec = if (reduced) tween(AppMotion.ReducedMotionMillis)
        else AppMotion.effectsDefaultTween(),
    )
    val foldExit = shrinkHorizontally(
        animationSpec = if (reduced) snap() else AppMotion.spatialDefaultTween(),
    ) + fadeOut(
        animationSpec = if (reduced) tween(AppMotion.ReducedMotionMillis)
        else AppMotion.effectsDefaultTween(),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                // weight, so the scrolling half yields the pinned half its width instead of
                // covering it. This is the whole reason the trailing slot is not inside the scroll.
                .weight(1f)
                .scrollableEdgeFade(scrollState)
                .horizontalScroll(scrollState)
                // INSIDE the scroll, not outside it. Applied to the viewport instead, the inset
                // would stay put while the chips slid under it and the row would clip 20dp short of
                // its own edge; as content padding it scrolls away with the first chip, which is
                // what makes the fade land on a pill rather than on a gap. No END inset here: this
                // half runs to the pinned chip, and the fade is what marks that edge.
                .padding(start = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            // The chips differ in height by a hair (the lead chip carries labelLarge, the pills
            // labelMedium) and heightIn lets each keep its own. Without this they would be stretched
            // to the tallest, which is how a 48dp pill becomes a 56dp one at fontScale 1.3.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DueLeadChip(
                label = leadLabel,
                hasDate = hasDate,
                expanded = expanded,
                clearDateLabel = clearDateLabel,
                expandedStateLabel = expandedStateLabel,
                collapsedStateLabel = collapsedStateLabel,
                onClick = onLeadClick,
                onClear = onClearDate,
            )
            // The offers fold while the planner is open — see the KDoc. One AnimatedVisibility
            // around the whole group rather than one per pill: they would otherwise animate their
            // widths independently and the row would ripple instead of retract.
            AnimatedVisibility(visible = !expanded, enter = foldEnter, exit = foldExit) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                    // The gap the parent would have put between the lead chip and this group is
                    // inside the group instead — AnimatedVisibility collapses to zero width, and a
                    // parent gap would survive the fold as a stray 4dp after the lead chip.
                    modifier = Modifier.padding(start = AppDimens.SpacingXs),
                ) {
                    presets.forEach { preset ->
                        DuePresetPill(label = preset.label, onClick = { onPresetClick(preset.id) })
                    }
                }
            }
        }

        // PINNED, outside the scroll — and that placement is the finding rather than the taste.
        // Measured on the recorded frames the day the rail started scrolling: with three offers the
        // Important toggle was visible for 23dp against a 24dp fade, i.e. every visible pixel of it
        // sat inside the gradient; with a date applied the wider lead chip pushed it off the edge
        // entirely. A shipped control that does not exist on screen in the commonest state is the
        // most expensive defect class in this project, and its ON state was unobservable too.
        //
        // Pinning also matches what this row already says about itself: the scrolling half is the
        // list of ANSWERS to "when", and Important is not one of them — it is a modifier on the task
        // being sent. A list scrolls; a modifier does not go looking for the user.
        if (trailing != null) {
            AnimatedVisibility(visible = !expanded, enter = foldEnter, exit = foldExit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = AppDimens.SpacingXs,
                        end = horizontalPadding,
                    ),
                ) {
                    trailing()
                }
            }
        }
    }
}

/**
 * Fades whichever edge of a horizontally scrolling row still has content behind it.
 *
 * The only thing telling a user this row scrolls, so it is drawn from live scroll state rather than
 * from "is the content wider than the box": at rest with the row scrolled to 0 there is no fade on
 * the left and one on the right, which reads as "more that way" — and both disappear once the row
 * fits, so a rail that does not scroll looks exactly as it did before it could.
 *
 * `DstIn` against an offscreen layer rather than a gradient painted in the dock's colour, because
 * this component does not know that colour: [AppChatColors] resolves against `LocalChatSurfaceTone`,
 * which the DOCK provides, and a hardcoded scrim would be visibly wrong on the other tone and in the
 * other theme. Fading the alpha channel is colour-blind by construction.
 *
 * Both reads of [state] happen in the DRAW lambda, which is a deliberate deferral: read in
 * composition they would recompose the whole rail on every scrolled pixel — the recomposition-scope
 * defect this project has already paid for once with window insets.
 */
private fun Modifier.scrollableEdgeFade(state: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = EdgeFadeWidth.toPx()
        if (state.value > 0) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = fade,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.value < state.maxValue) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fade,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * How far the scroll cue bleeds in from an edge.
 *
 * 24dp: wide enough that the pill under it reads as continuing past the edge rather than as a
 * rendering glitch, narrow enough that it never eats a whole preset label — the shortest one in the
 * set ("1 h") is 40dp of text.
 */
private val EdgeFadeWidth = 24.dp

/**
 * The rail's leading chip: the current answer, the expander, and — once there is a date — the clear
 * target.
 *
 * Two visual states, and which one applies depends on [hasDate] ALONE:
 *
 * | `hasDate` | `expanded` | fill | border | trailing |
 * |---|---|---|---|---|
 * | false | false | [AppChatColors.raised] | [AppChatColors.controlOutline] | chevron down |
 * | false | true  | [AppChatColors.raised] | [AppChatColors.controlOutline] | chevron up |
 * | true  | any   | [GistiSchedule.activeContainer] | `primary` | `×` |
 *
 * ⚠️ The accent used to include `expanded`, so an open panel painted the chip blue whether or not
 * anything was set. Blue means "this is the answer" everywhere else in this component — on a chip
 * reading "When?" above an empty grid it announced a value the draft did not have (UI audit,
 * 2026-08-19). Openness is carried by the CHEVRON and by `stateDescription`, which is where a state
 * that is not a value belongs.
 *
 * ⚠️ The idle chip is FILLED, not transparent, and that is not a detail. A transparent control on
 * this dock is a shipped defect of this exact codebase: the fill was the only thing separating the
 * chip from `#DEDCD6` chrome, the remaining hairline measured 1.04 : 1, and the report was "the
 * buttons blend into the background". See [AppChatColors] — a transparent control does not get a
 * firmer line, it gets a fill.
 */
@Composable
private fun DueLeadChip(
    label: String,
    hasDate: Boolean,
    expanded: Boolean,
    clearDateLabel: String,
    expandedStateLabel: String,
    collapsedStateLabel: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    // `hasDate` ALONE — see the table above. An open panel is a state, not an answer.
    val accented = hasDate
    // GistiSchedule's ACTIVE tone specifically, not `colors(state)`. The other tones are wrong here
    // for one reason each: `Later` and `Someday` resolve to `AppSurface.recessed()`, which is a step
    // DOWN from the page and lands within ~2 L* of this dock's own chrome — the applied chip would
    // dissolve into the dock in the one place the answer has to be readable — and `Overdue` cannot
    // occur on a draft, whose date is by construction in the future. Active is the one tone that is
    // a real step off BOTH planes in BOTH themes (#E3F0FC on light, #0F3B66 on dark).
    val container = if (accented) GistiSchedule.activeContainer else AppChatColors.raised()
    val content = if (accented) GistiSchedule.activeContent else MaterialTheme.colorScheme.onSurface
    val border = if (accented) MaterialTheme.colorScheme.primary else AppChatColors.controlOutline()
    val stateLabel = if (expanded) expandedStateLabel else collapsedStateLabel

    Surface(
        shape = AppShapeTokens.Pill,
        color = container,
        border = BorderStroke(AppDimens.DividerThickness, border),
        // heightIn, never height: `Modifier.height` pins min AND max, which clips Devanagari matras
        // and any label past fontScale 1.3, and — the reason it is called out here — silently defeats
        // every 48dp minimum below it. That is the live a11y defect this project already fixed once
        // on the 38dp preset chips.
        modifier = Modifier.heightIn(min = AppDimens.MinTouchTarget),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                modifier = Modifier
                    .heightIn(min = AppDimens.MinTouchTarget)
                    .clickable(role = Role.Button, onClick = onClick)
                    // The expander half, and the only place the open/closed state is announced. A
                    // `Role.Button` with no state description tells a screen-reader user what they
                    // can do and never what happened when they did it.
                    .semantics { stateDescription = stateLabel }
                    .padding(
                        start = ChipLabelPadding,
                        // The `×` half brings its own 48dp of width; a full label padding before it
                        // would push the chip past a third of the rail on a 320dp dock.
                        end = if (hasDate) AppDimens.SpacingSm else ChipLabelPadding,
                    ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (accented) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                )
                if (!hasDate) {
                    Icon(
                        // Decorative: the chevron restates `stateDescription`, which is already on
                        // this node. A description here would have TalkBack say "collapsed" twice.
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(DueIconSize),
                    )
                }
            }
            if (hasDate) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        // A full square target, not the glyph's 16dp. sizeIn rather than size for
                        // the same reason as above — it may grow with the chip, never shrink.
                        .sizeIn(
                            minWidth = AppDimens.MinTouchTarget,
                            minHeight = AppDimens.MinTouchTarget,
                        )
                        .clickable(role = Role.Button, onClick = onClear)
                        // The ONLY label this half has: it holds a glyph, so without a description
                        // it is announced as an unnamed button.
                        .semantics { contentDescription = clearDateLabel },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(DueIconSize),
                    )
                }
            }
        }
    }
}

/**
 * A preset pill in the rail: a value you press, never a state you are in.
 *
 * It writes NO `selected` semantics, and that is deliberate rather than an omission — see
 * [SourcePill]'s `selected` KDoc for the same rule stated from the other side. `selected = false` on
 * something that is never selected makes TalkBack announce "not selected" about a thing that has no
 * selection, and here it would also contradict the lead chip, which is the only answer in the row.
 */
@Composable
private fun DuePresetPill(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = AppShapeTokens.Pill,
        color = AppChatColors.raised(),
        border = BorderStroke(AppDimens.DividerThickness, AppChatColors.controlOutline()),
        modifier = Modifier.heightIn(min = AppDimens.MinTouchTarget),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // SpacingSm, one step tighter than the lead chip's [ChipLabelPadding] — and identical to
            // the source pills below, whose shape and height these already share. Measured: at 12dp
            // the three-preset English rail still needed 355dp of a 344dp row at 360dp / fontScale
            // 1.0 and wrapped; at 8dp it lands on one line with room to spare, which is the state
            // the approved mock shows.
            modifier = Modifier.padding(horizontal = AppDimens.SpacingSm),
        ) {
            Text(
                text = label,
                // labelMedium, one step under the lead chip's labelLarge. Two things fall out of
                // that: the ANSWER stays typographically louder than the offers around it, and
                // these pills match the source pills sitting 60dp below them in the same dock —
                // same height, same corner, same type — instead of reading as a fifth kind of
                // button. It is also what buys the width the row needs to stay on one line.
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                // No maxLines and no ellipsis. A truncated preset is a preset nobody taps, and the
                // row can afford the height because it wraps instead of scrolling.
            )
        }
    }
}

/**
 * The planner: a 2×3 grid of dated offers plus the `[Time] [Repeat] [Done]` control row, expanding
 * inside the dock under [DueRailRow].
 *
 * ## What this component deliberately does NOT do
 * It does not host a date picker, a time picker or the repeat sheet. Those live in
 * `feature:checklist` (`ReminderSheet`), and `core:designsystem` sits UNDER it in the module graph —
 * a dependency the other way round is not expressible, never mind advisable. So all three exits are
 * callbacks ([onPickDateClick], [onTimeClick], [onRepeatClick]) and the host mounts the real v1
 * sheets behind them. That is also the product rule: reuse the v1 surface, do not grow a thinner v2
 * twin of it.
 *
 * It also holds no state. `expanded`, the current preset, the resolved time and the repeat summary
 * are all parameters; the panel renders them and reports taps.
 *
 * ## Repeat is disabled, not hidden, without a date
 * A repeat rule has nothing to repeat FROM until there is an anchor date, so the chip cannot work.
 * It stays on screen and greyed rather than vanishing, because a control that appears and disappears
 * as a side effect of an unrelated tap is a layout that moves under the user's finger — and because
 * a visible-but-off control is what teaches the user that a date unlocks it. `Surface(enabled=false)`
 * carries this to TalkBack as `disabled` for free.
 *
 * ## Expansion is animated as a size, not dragged
 * A vertical `AnchoredDraggableState` here would nest a drag inside the dock's own gesture surface
 * and inside the list behind it. The panel is opened by a tap and closed by a tap, so it animates as
 * a size and owns no gesture at all.
 *
 * The spec is [AppMotion]'s cubic-bezier equivalent rather than its spring, on that object's own
 * instruction: an unsettled spring driving `expandVertically` re-measures the subtree every frame,
 * and on a loaded Skiko canvas that is the judder the tween exists to avoid. Same physics, same
 * duration.
 *
 * @param cells the five dated offers, in display order, each with its resolved time. "Pick date" is
 *   NOT one of them — it is appended by this component as the sixth cell because it produces no
 *   value and therefore has no [DuePresetId].
 * @param selectedPreset the preset the current date came from, or `null`. Selection changes FILL and
 *   BORDER only — never padding, weight or glyphs — because a cell that grew on selection would stop
 *   fitting the equal column measured for it.
 * @param timeValueLabel the resolved time shown on the Time chip ("19:00").
 * @param repeatValueLabel the repeat summary shown on the Repeat chip ("Off", "Daily").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuePlannerPanel(
    expanded: Boolean,
    cells: List<DuePresetCell>,
    selectedPreset: DuePresetId?,
    timeValueLabel: String,
    repeatValueLabel: String,
    onPresetClick: (DuePresetId) -> Unit,
    onPickDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = AppDimens.ScreenPaddingHorizontal,
    pickDateLabel: String = stringResource(Res.string.due_planner_pick_date),
    timeLabel: String = stringResource(Res.string.due_planner_time),
    repeatLabel: String = stringResource(Res.string.due_planner_repeat),
    doneLabel: String = stringResource(Res.string.due_planner_done),
) {
    val reduced = LocalReducedMotion.current
    val sizeSpec: FiniteAnimationSpec<IntSize> =
        if (reduced) snap() else AppMotion.spatialDefaultTween()
    val alphaSpec: FiniteAnimationSpec<Float> =
        if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()

    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(animationSpec = sizeSpec) + fadeIn(animationSpec = alphaSpec),
        exit = shrinkVertically(animationSpec = sizeSpec) + fadeOut(animationSpec = alphaSpec),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = AppDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            // Six slots in rows of three: the five dated offers, then "Pick date". Built as one list
            // and chunked rather than as two hand-written rows, so a set of a different size still
            // produces a grid instead of an off-by-one.
            val slots: List<DuePlannerSlot> =
                cells.map { DuePlannerSlot.Preset(it) } + DuePlannerSlot.PickDate
            slots.chunked(DuePlannerColumns).forEach { group ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    // IntrinsicSize.Min, so every cell in a row is as tall as the tallest one in it.
                    // Without it a wrapped label ("След. неделя" at fontScale 1.3 on a 320dp dock)
                    // leaves one cell taller than its row-mates — peers at two different heights,
                    // the same defect the source-pill grid's equal-column rule exists to prevent.
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    group.forEach { slot ->
                        when (slot) {
                            is DuePlannerSlot.Preset -> DuePlannerCell(
                                label = slot.cell.label,
                                secondaryLabel = slot.cell.timeLabel,
                                icon = null,
                                selected = slot.cell.id == selectedPreset,
                                onClick = { onPresetClick(slot.cell.id) },
                            )

                            DuePlannerSlot.PickDate -> DuePlannerCell(
                                label = pickDateLabel,
                                secondaryLabel = null,
                                icon = Icons.Outlined.CalendarMonth,
                                // Not a member of the preset set: this cell opens a picker rather
                                // than producing a value, so it never carries the current answer.
                                selected = null,
                                onClick = onPickDateClick,
                            )
                        }
                    }
                    // Pad a short final row so its cells keep the width of their peers above instead
                    // of stretching. Unreachable with the five presets shipped today; here because
                    // the grid takes its cells as a parameter.
                    repeat(DuePlannerColumns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Bottom, not CenterVertically: once the two value chips wrap to two lines (fontScale
            // 1.5, or RU at 1.3 on a 320dp dock) a centred Done floats against the gap between them.
            // Aligned to the bottom it sits beside the LAST control, which is also the reading order
            // — set these, then commit.
            Row(verticalAlignment = Alignment.Bottom) {
                // The two value chips wrap between themselves; Done never wraps away from the row.
                // A Row measures its non-weighted child first, so Done keeps its intrinsic width and
                // the FlowRow takes whatever is left — which is exactly the priority order here.
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                ) {
                    DuePlannerControlChip(
                        icon = Icons.Outlined.Schedule,
                        label = timeLabel,
                        value = timeValueLabel,
                        onClick = onTimeClick,
                    )
                    DuePlannerControlChip(
                        icon = Icons.Outlined.Repeat,
                        label = repeatLabel,
                        value = repeatValueLabel,
                        // Live from an empty draft — a repeat replaces the date rather than needing
                        // one. See [DuePlannerControlChip].
                        onClick = onRepeatClick,
                    )
                }
                Spacer(Modifier.width(AppDimens.SpacingSm))
                AppButton(
                    text = doneLabel,
                    onClick = onDoneClick,
                    // Pill, not AppShapeTokens.Button. This button sits INLINE in a row of pill
                    // controls, and a 14dp corner beside 24dp ones is the "several kits in one
                    // frame" reading the bottom-chrome tokens were unified to remove. Everywhere a
                    // button stands alone, the 14dp default still applies.
                    shape = AppShapeTokens.Pill,
                )
            }
        }
    }
}

/** Columns in the planner grid. Three, so six offers fit two rows on a 320dp dock. */
private const val DuePlannerColumns = 3

/** What occupies one cell of the planner grid: a dated offer, or the door to the picker. */
private sealed interface DuePlannerSlot {
    data class Preset(val cell: DuePresetCell) : DuePlannerSlot
    data object PickDate : DuePlannerSlot
}

/**
 * One two-line cell of the planner grid.
 *
 * @param secondaryLabel the resolved time under the label, or `null` when the cell resolves to no
 *   time (the picker door).
 * @param icon shown INSTEAD of [secondaryLabel] and decorative: the visible label right above it is
 *   already this cell's accessible name, and `Surface(onClick)` merges descendants — a description
 *   here would REPLACE that name rather than add to it.
 * @param selected `true`/`false` for a member of the preset set, **`null`** for the picker door,
 *   which is not a member of any set. Only the first two write `semantics { selected }`; a door
 *   announced "not selected" is a claim about state on a thing that has none.
 */
@Composable
private fun RowScope.DuePlannerCell(
    label: String,
    secondaryLabel: String?,
    icon: ImageVector?,
    selected: Boolean?,
    onClick: () -> Unit,
) {
    // Aliased: inside `semantics { }` the name `selected` resolves to the semantics property being
    // assigned, not to this parameter. The compiler stays silent and the state never reaches TalkBack.
    val isSelected = selected
    val isFilled = selected == true
    val container = if (isFilled) GistiSchedule.activeContainer else AppChatColors.raised()
    val labelColor = if (isFilled) GistiSchedule.activeContent else MaterialTheme.colorScheme.onSurface
    val border = if (isFilled) MaterialTheme.colorScheme.primary else AppChatColors.controlOutline()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DueCellCorner),
        color = container,
        border = BorderStroke(AppDimens.DividerThickness, border),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = DueCellMinHeight)
            .semantics { if (isSelected != null) this.selected = isSelected },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = AppDimens.SpacingXs,
                vertical = AppDimens.SpacingSm,
            ),
        ) {
            Text(
                text = label,
                // labelMedium: at labelLarge, "Выходные" broke mid-word inside a 90dp column on a
                // 320dp dock at fontScale 1.3 — a hyphen-less break across "Выходны / е", which is
                // worse than a wrap because it reads as a rendering fault rather than as long copy.
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
                textAlign = TextAlign.Center,
            )
            if (secondaryLabel != null) {
                Spacer(Modifier.height(AppDimens.SpacingXxs))
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (icon != null) {
                Spacer(Modifier.height(AppDimens.SpacingXxs))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(DueIconSize),
                )
            }
        }
    }
}

/**
 * A `[icon] Label value` chip in the planner's control row — Time and Repeat.
 *
 * ## Neither of them is ever disabled, and Repeat is the interesting one
 * Repeat shipped greyed until there was a date, on the model "a repeat is a modifier on a date".
 * The draft says otherwise: `TaskDraft.withRepeat(config)` sets `reminderAt = null` and
 * `reminderPreset = null`, i.e. saving a rule REPLACES the date rather than decorating it, and the
 * read side agrees — `InboxViewModel` opens the REPEAT tab exactly for an item with a rule and no
 * `reminderAt`. So the gate asked the user to pick a date that the very next step would delete, and
 * `Surface(enabled = false)` — not focusable, tap eaten before any handler — left them no way to
 * find that out. A recurring answer is reachable in one tap now, from an empty draft (2026-08-19).
 */
@Composable
private fun DuePlannerControlChip(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = AppShapeTokens.Pill,
        color = AppChatColors.raised(),
        border = BorderStroke(AppDimens.DividerThickness, AppChatColors.controlOutline()),
        modifier = Modifier.heightIn(min = AppDimens.MinTouchTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
            modifier = Modifier.padding(horizontal = AppDimens.SpacingSm),
        ) {
            Icon(
                // Decorative — the label beside it is the accessible name, and `Surface(onClick)`
                // merges descendants, so a description would replace the name instead of adding.
                imageVector = icon,
                contentDescription = null,
                tint = valueColor,
                modifier = Modifier.size(DueIconSize),
            )
            // labelMedium and SpacingSm padding, for the same reason as the rail's pills: at
            // labelLarge / SpacingMd the pair needed 234dp of the 230dp left beside Done and wrapped
            // onto two lines at 360dp / fontScale 1.0, which cost ~50dp of an already 200dp+ dock.
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
            )
        }
    }
}

/** M3's disabled content opacity. Named rather than repeated at the four call sites above. */
private const val DisabledContentAlpha = 0.38f
