package com.antonchuraev.homesearchchecklist.feature.home.presentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppDueChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppItemMetaChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppItemMetaChipDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSourceIcon
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSourceKind
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.DueLabelSpec
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.dueLabelSpec
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.format.label

/**
 * One task in a list, with the project-wide 30/70 hit-zone split: the left 30% toggles the checkbox,
 * the right 70% opens the details sheet (`.claude/rules/ui-card-patterns.md`).
 *
 * ## Why it is `TaskRow` and not `InboxTaskRow`
 * The same object appears on the Inbox, on Today, and in the review flow. Three private copies would
 * be three hit-zone splits drifting apart, and the split is the one part of this row that is
 * genuinely load-bearing — a 5% slip makes taps land on the wrong action without anything looking
 * broken. So it lives in the shared components package and takes the domain item directly.
 *
 * ## Why a card
 * This used to be a bare `Row` on the page background: no container, no rule, nothing but 4dp of air
 * between neighbours, so the tasks read as floating text rather than as objects. The very same object
 * on the checklist detail screen is a card, so the fix is not a new style but the existing one —
 * `AppCardDefaults` (filled `surfaceContainerLowest` + 1dp `outlineVariant` hairline + zero
 * elevation). On the app's warm off-white page a pure-white card reads clearly; the hairline is what
 * carries the separation, which is why elevation stays flat (stacked shadows in a dense list produce
 * grey "ears" around every card).
 *
 * Uses Material3 [Card] directly rather than `AppCard`: `AppCard` exposes a single `onClick` for the
 * whole surface, which cannot express the 30/70 split. The click handling stays on an INNER overlay
 * Box — moving it onto the Card's own modifier lets the ripple escape the rounded corners (precedent
 * `appcard-onlongclick-ripple-clip`).
 *
 * ## What the row now says that it did not
 * A task with a reminder used to look exactly like a task without one. The meta row under the title
 * carries the due chip, the source glyph and the attachment count, so the date is visible where the
 * task is rather than two taps away in a sheet. Priority moved from a 16dp trailing star to a 3dp
 * bar at the leading edge: it frees ~20dp of width for the title, lines the marker up down the list,
 * and leaves the trailing edge free for the swipe affordances that arrive next.
 *
 * ⛔ **Nothing in the meta row is clickable.** The chip, the glyph and the bar sit UNDER the invisible
 * 30/70 overlay; making any of them tappable would carve a hole in the 70% zone, so a tap next to the
 * chip would stop opening the sheet. Scheduling from a row is a swipe, not a button.
 *
 * ## Swiping
 * Wrap this composable from the OUTSIDE (`SwipeToDismissBox { TaskRow(...) }`). The 30/70 overlay
 * uses `combinedClickable`, which claims the initial press, so a swipe container nested *inside* the
 * row would never see the horizontal drag.
 *
 * @param item the task. The whole domain item, not a projection: the meta row reads five of its
 *   fields and a projection would be a second, drifting copy of the model.
 * @param compact renders the same row WITHOUT the card: a shorter, flat line whose only separator is
 *   the divider the list draws between neighbours. The chrome is what costs the vertical space, so
 *   dropping it — rather than shrinking the type — is what fits more tasks on screen while the text
 *   stays exactly as readable. The due chip survives into this layout (in the trailing edge, where
 *   there is no second line to put it on); losing it here would re-create the very defect this row
 *   exists to fix. ⚠️ Above the default `fontScale` the single line itself is given up while the
 *   card still is not — see `singleLine` in the body.
 * @param onCheckedChange the left 30% and the checkbox itself.
 * @param onDetailsClick the right 70%, plus a long press anywhere.
 * @param modifier applied to the row's container.
 * @param source where the task came from. Always [GistiSourceKind.Manual] today — the field that
 *   feeds it arrives with the connected-sources model — and `Manual` draws nothing, so the slot
 *   costs no pixels until then.
 * @param nowMillis the clock the due chip compares against. A parameter rather than an ambient read
 *   so that a screenshot test can pin it, and so the ViewModel can later drive the "a due date just
 *   passed" transition from a single ticking source instead of every row reading its own clock.
 */
@Composable
fun TaskRow(
    item: ChecklistFillItem,
    compact: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
    source: GistiSourceKind = GistiSourceKind.Manual,
    nowMillis: Long = currentTimeMillis(),
) {
    val due = item.dueLabelSpec(nowMillis = nowMillis)
    // priorityIndicator, not star: the bar is the ONLY channel carrying priority on this row (the
    // 16dp trailing star is gone), so it has to clear the 3:1 non-text contrast bar by itself.
    // `star` is a glyph colour and gives ~2:1 against the white card — readable as an icon with a
    // silhouette, not as a 3dp stripe.
    val priorityColor = GistiColors.priorityIndicator

    // 🔴 Compact is one line only at the DEFAULT text size.
    //
    // On one line the title and the date chip share ~300dp, and when they stop fitting the loser is
    // always the date: the chip clips its middle, so "Tomorrow 09:00" rendered as "Tom…9:00" and
    // "Mon,Wed 18:30" as "Mon,…8:30". That is not a shortened label, it is a DIFFERENT hour that
    // looks entirely plausible — the one failure a date chip must never have. Widening the chip
    // instead just moves the damage onto the title, and at fontScale 1.5 in Russian no split of
    // 300dp holds both.
    //
    // So above the default scale the row uses the comfortable two-line anatomy: the title gets the
    // full width and the chip gets a line of its own, where it never truncates. Nothing is lost —
    // at those scales the titles were already wrapping, so the "single line" the compact setting
    // promises had stopped being true anyway.
    //
    // The threshold sits above 1.0 rather than at it because the OS offers 0.85 / 1.0 / 1.15 / 1.3
    // / 1.5 / 1.8 / 2.0 — 1.05 separates "the user did not ask for bigger text" from "they did"
    // without depending on an exact float compare.
    val singleLine = compact && LocalDensity.current.fontScale <= CompactSingleLineFontScaleLimit

    // One content block, two containers. Extracting the content into a local lambda keeps the 30/70
    // hit-zone split and the strike-through logic single-sourced — two copies would drift, and this
    // row's hit zones are load-bearing enough that a drift here is a silent UX regression.
    val rowContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = if (compact) {
                        AppDensity.RowMinHeightCompact
                    } else {
                        AppDensity.RowMinHeightComfortable
                    },
                )
                // Drawn rather than laid out: a real 3dp child would need `fillMaxHeight` inside a
                // Box whose own height comes from its siblings, which resolves to nothing under the
                // unbounded height constraint a lazy item is measured with. `drawBehind` paints the
                // final measured size and costs no layout pass. The card clips it to the corner
                // radius; in the compact layout there is no card and it is a plain edge.
                .then(
                    if (item.priority > 0) {
                        Modifier.priorityBar(priorityColor)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(
                        horizontal = AppDensity.RowPaddingHorizontal,
                        vertical = AppDensity.RowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDensity.RowGap),
            ) {
                Checkbox(
                    checked = item.checked,
                    // null → the tap overlay below owns the gesture, so the checkbox never competes
                    // with the 30/70 split for the same pointer.
                    onCheckedChange = null,
                )

                if (singleLine) {
                    TaskTitle(item = item, modifier = Modifier.weight(1f))
                    // No second line to hang a meta row from, so the date moves to the trailing
                    // edge. The attachment chip does NOT come with it: of the three indicators it
                    // is the least load-bearing, and the width it would take is the width the date
                    // needs.
                    AppSourceIcon(kind = source)
                    if (due != null) {
                        AppDueChip(
                            state = due.state,
                            label = due.form.label(),
                            maxWidth = CompactDueChipMaxWidth,
                            isRepeating = item.repeatRule != null,
                            hasAlarm = item.alarmEnabled,
                        )
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        TaskTitle(item = item)
                        TaskMetaRow(
                            item = item,
                            due = due,
                            source = source,
                        )
                    }
                }
            }

            // Invisible tap overlay — no ripple, matching ChecklistItemCard: the feedback is the
            // state change (checkbox flip / sheet appearing), not an indication.
            val checkInteraction = remember { MutableInteractionSource() }
            val detailsInteraction = remember { MutableInteractionSource() }
            Row(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .weight(CheckZoneWeight)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = checkInteraction,
                            indication = null,
                            onClick = { onCheckedChange(!item.checked) },
                            onLongClick = onDetailsClick,
                        ),
                )
                Box(
                    modifier = Modifier
                        .weight(DetailsZoneWeight)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = detailsInteraction,
                            indication = null,
                            onClick = onDetailsClick,
                            onLongClick = onDetailsClick,
                        ),
                )
            }
        }
    }

    if (compact) {
        // No Card, no border, no shape: on the page background this is a plain line, and the list's
        // own divider is the separator. Wrapping it in a zero-elevation borderless Card instead would
        // still paint the card's container colour and leave the row looking like a card that lost its
        // outline.
        Box(modifier = modifier) { rowContent() }
    } else {
        Card(
            modifier = modifier,
            colors = AppCardDefaults.colors(),
            border = AppCardDefaults.border(),
            elevation = AppCardDefaults.flatElevation(),
            shape = MaterialTheme.shapes.medium,
        ) {
            rowContent()
        }
    }
}

/**
 * The task text.
 *
 * `maxLines` is **2**, down from 3. The meta row underneath adds a line, and three lines of title on
 * top of it takes a 60dp row past 96dp and breaks the list's rhythm. The full text is always one tap
 * away in the details sheet, which is where a task long enough to need three lines is being read
 * anyway.
 */
@Composable
private fun TaskTitle(item: ChecklistFillItem, modifier: Modifier = Modifier) {
    Text(
        text = item.text,
        style = AppTextStyles.taskTitle,
        color = if (item.checked) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        // Done state is carried by the strike-through and the dimmer text, never by a tinted
        // container: a list of alternately tinted cards is harder to scan than a uniform one, and
        // the strike-through is the colour-independent (WCAG) signal.
        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
        maxLines = TaskTitleMaxLines,
        overflow = TextOverflow.Ellipsis,
        // fillMaxWidth is mandatory for any Text inside a HorizontalPager — without it the text
        // overflows the page instead of wrapping (rule ui-card-patterns).
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The second line: everything about the task that is not its text.
 *
 * Emits nothing at all when there is nothing to say, so an ordinary undated task keeps exactly the
 * height it had. That is also why "no due date" is the absence of a chip rather than a chip reading
 * "No date": the neutral state should cost zero pixels.
 */
@Composable
private fun TaskMetaRow(
    item: ChecklistFillItem,
    due: DueLabelSpec?,
    source: GistiSourceKind,
) {
    val attachments = item.attachments.size
    if (due == null && source == GistiSourceKind.Manual && attachments == 0) return

    Row(
        modifier = Modifier.padding(top = AppDensity.MetaRowTopGap),
        horizontalArrangement = Arrangement.spacedBy(AppDensity.MetaChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (due != null) {
            AppDueChip(
                state = due.state,
                label = due.form.label(),
                // The chip has this line almost to itself, so the ROW is its bound, not a
                // constant. `weight(1f, fill = false)` means the glyph and the attachment count
                // are measured FIRST and the chip takes what is left — the same "the flexible
                // child is the one that shrinks" rule the chip uses internally for its own label.
                //
                // The 140dp default is withheld because it is wrong here in both directions: too
                // tight for "Tomorrow 09:00" plus an alarm glyph even at fontScale 1.0 (it shipped
                // as "Tomorr… 09:00"), and far short of the ~300dp this line actually has.
                modifier = Modifier.weight(1f, fill = false),
                maxWidth = Dp.Unspecified,
                isRepeating = item.repeatRule != null,
                // A date alone does not arm anything — that is the whole reason the model carries
                // the two separately — so the bell glyph appears only when an alarm really is set.
                hasAlarm = item.alarmEnabled,
            )
        }
        AppSourceIcon(kind = source)
        if (attachments > 0) {
            AppItemMetaChip(
                icon = Icons.Filled.AttachFile,
                label = attachments.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Left share of the row: the checkbox zone. */
private const val CheckZoneWeight = 0.30f

/** Right share of the row: the details zone. */
private const val DetailsZoneWeight = 0.70f

/** Title lines before the text is clipped. See [TaskTitle]. */
private const val TaskTitleMaxLines = 2

/**
 * Width ceiling for the due chip on the single-line compact row.
 *
 * Sized to the LONGEST label this chip can produce at `fontScale` 1.0 — "Tomorrow 09:00" with an
 * alarm glyph, "Mon,Wed 18:30" with a repeat glyph — because anything less does not shorten those,
 * it corrupts them ("Tom…9:00", "Mon,…8:30"). It was 112dp, which shipped exactly that.
 *
 * It costs the title ~36dp, so a task text past roughly 22 characters now wraps to a second line
 * where it used to be clipped. That is the right way round: a clipped title is visibly clipped,
 * while a clipped time reads as a correct time.
 *
 * Not font-scale-scaled, unlike [AppItemMetaChipDefaults.maxWidth] — above the default text size
 * this row is not single-line at all (see `singleLine` in [TaskRow]) and this value goes unused.
 */
private val CompactDueChipMaxWidth: Dp = 148.dp

/**
 * Highest `fontScale` at which the compact row still puts the title and the date on ONE line.
 *
 * See `singleLine` in [TaskRow] for why there is a limit at all.
 */
private const val CompactSingleLineFontScaleLimit = 1.05f

/**
 * Paints the priority bar along the row's leading edge.
 *
 * Leading, not left: the offset is computed from the layout direction, so on an RTL locale the bar
 * follows the text to the other side instead of sitting under the trailing chips.
 */
private fun Modifier.priorityBar(color: Color): Modifier = drawBehind {
    val width = AppDensity.PriorityBarWidth.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
    drawRect(color = color, topLeft = Offset(x, 0f), size = Size(width, size.height))
}
