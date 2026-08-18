package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSchedule
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiScheduleState

/**
 * The due-date chip: when this task is expected, in the smallest form that still says it.
 *
 * ## Not a component of its own — a preset of [AppItemMetaChip]
 * A second chip implementation living next to the meta chip would be a thin twin: it would re-open
 * the layout defects the meta row already closed (the width cap, the 30/70 hit-zone rule, the
 * measured-first glyphs) and double the cost of every future fix. So this function resolves tokens
 * and hands them to [AppItemMetaChip]; it draws nothing itself except the dashed outline, which the
 * `BorderStroke` API cannot express.
 *
 * ## Three channels, never colour alone
 * Each state carries its own colour ([GistiSchedule]), its own icon, and its own label shape. That
 * is what keeps the meaning alive under colour blindness, in a greyscale screenshot, and behind the
 * iOS 27 glass-transparency slider.
 *
 * | State | Icon | Outline |
 * |---|---|---|
 * | [GistiScheduleState.Later] | `Outlined.Event` | none |
 * | [GistiScheduleState.Someday] | `Outlined.Bedtime` | 1dp dashed — "parked", not scheduled |
 * | [GistiScheduleState.Active] | `Filled.Today` | none |
 * | [GistiScheduleState.Overdue] | `Filled.EventBusy` | 1dp solid — findable in a column |
 *
 * **"No due date" is not a state.** A task without one renders no chip at all: zero pixels, zero
 * weight, zero blame. That is why the parameter is a non-null [GistiScheduleState] — the decision
 * belongs to the call site, which simply does not call this function.
 *
 * ## The label arrives finished
 * [label] is already resolved and localized by the feature layer, exactly like [TokenChipPreview]'s.
 * Formatting a due date needs the domain item, the clock and a locale-aware relative-date vocabulary
 * ("Tomorrow", "Fri 14", weekday names) — pulling any of that in here would make `core:designsystem`
 * depend on `feature:checklist` and would hardcode English into the design system, which is the
 * defect that shipped a Russian error string on the English UI once already.
 *
 * ## Read-only on a card
 * ⛔ There is deliberately no `onClick`. On `ChecklistItemCard` / a task row the chip sits under an
 * invisible 30/70 hit-zone overlay; making it clickable would carve a hole in the 70% zone, so a tap
 * *near* the chip would stop opening the details sheet. Scheduling from a card is a swipe, not a
 * button. The tappable date affordance is [TokenChipPreview] in the capture dock.
 *
 * ## The hour is the part that must survive
 * [TextOverflow.MiddleEllipsis] keeps the head and the tail, so it clips the word and leaves the
 * clock — but only while the chip has room for the clock at all. Squeeze it further and the
 * ellipsis eats into the digits, and the result is not a shortened label: "Today 14:00" becomes
 * "Tod…4:00" and "Mon,Wed 18:30" becomes "Mon…8:30". A wrong time that looks like a right one is
 * the worst thing this chip can do, so the width budget it is given ([maxWidth]) is the load-bearing
 * decision here, not the overflow mode.
 *
 * @param state Which of the four visual states applies. Resolved in the feature layer from
 *   `reminderAt` / `repeatNextAt` against the current clock.
 * @param label Finished, localized label — "Tomorrow 09:00", "Fri 14", "Someday".
 * @param modifier Optional external modifier.
 * @param maxWidth Width ceiling, defaulting to the font-scale-aware
 *   [AppItemMetaChipDefaults.maxWidth]. See that parameter on [AppItemMetaChip] for when to hand it
 *   [Dp.Unspecified] instead.
 * @param isRepeating The task repeats on a schedule.
 * @param hasAlarm An OS alarm is actually armed for this task. A date alone never sets this: the
 *   date is free and unlimited, only the alarm is the metered thing, so the glyph is the one honest
 *   way to tell a user which of the two they have.
 */
@Composable
fun AppDueChip(
    state: GistiScheduleState,
    label: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = AppItemMetaChipDefaults.maxWidth,
    isRepeating: Boolean = false,
    hasAlarm: Boolean = false,
) {
    val colors = GistiSchedule.colors(state)
    val shape = MaterialTheme.shapes.extraSmall

    // A dashed outline is not expressible as a BorderStroke (which is brush + width, no PathEffect),
    // so the dashed case is drawn by the modifier below and the `border` parameter stays null.
    val solidBorder = colors.border
        ?.takeUnless { colors.borderDashed }
        ?.let { BorderStroke(AppDueChipDefaults.BorderWidth, it) }
    val dashedBorderColor = colors.border?.takeIf { colors.borderDashed }

    AppItemMetaChip(
        icon = state.icon(),
        label = label,
        containerColor = colors.container,
        contentColor = colors.content,
        modifier = if (dashedBorderColor != null) {
            modifier.dashedOutline(color = dashedBorderColor, shape = shape)
        } else {
            modifier
        },
        maxWidth = maxWidth,
        border = solidBorder,
        trailingIcon = trailingGlyph(isRepeating = isRepeating, hasAlarm = hasAlarm),
        // Tabular figures: without them "9:05" and "18:00" are different widths and the chip
        // breathes on every tick.
        labelStyle = AppTextStyles.monoTime,
        // Clip the word, not the hour. A due label is "Tomorrow 09:00"; a trailing ellipsis would
        // throw away the half the user actually needs. ru runs 30–50% longer than en, so this is
        // the common case in that locale rather than an edge case.
        labelOverflow = TextOverflow.MiddleEllipsis,
    )
}

/** Tuning constants for [AppDueChip]'s outlines. */
object AppDueChipDefaults {

    /** Outline width, solid and dashed alike. */
    val BorderWidth: Dp = 1.dp

    /** Length of one dash in the [GistiScheduleState.Someday] outline. */
    val DashLength: Dp = 3.dp

    /** Gap between dashes. Equal to [DashLength] — an even dash reads as "provisional". */
    val DashGap: Dp = 3.dp
}

/** The icon that identifies each state. Chosen so the four are distinguishable without colour. */
private fun GistiScheduleState.icon(): ImageVector = when (this) {
    GistiScheduleState.Later -> Icons.Outlined.Event
    GistiScheduleState.Someday -> Icons.Outlined.Bedtime
    GistiScheduleState.Active -> Icons.Filled.Today
    GistiScheduleState.Overdue -> Icons.Filled.EventBusy
}

/**
 * The single trailing glyph, if any.
 *
 * Only one is ever drawn, and repeat outranks the alarm: a repeating task almost always has an alarm
 * too, so showing both would put a second glyph on most repeating rows to say something the first
 * one already implied. "It comes back" is the rarer and more surprising fact, so it wins.
 */
private fun trailingGlyph(isRepeating: Boolean, hasAlarm: Boolean): ImageVector? = when {
    isRepeating -> Icons.Outlined.Repeat
    hasAlarm -> Icons.Outlined.NotificationsActive
    else -> null
}

/**
 * Draws a dashed outline on top of the content, following [shape].
 *
 * Compose has no dashed `BorderStroke`: `Modifier.border` takes a brush and a width, with nowhere to
 * put a [PathEffect]. So the outline is stroked as an explicit [Path] — explicit rather than
 * `drawOutline`, because a dash effect applied to a round-rect primitive is not reliably honoured by
 * every canvas backend, while a dashed *path* stroke is.
 *
 * `drawWithCache` builds the path once per size change instead of once per frame, and
 * `onDrawWithContent` puts the stroke **above** the content — drawing it behind would let the chip's
 * opaque fill cover the inner half of every dash.
 */
private fun Modifier.dashedOutline(
    color: Color,
    shape: CornerBasedShape,
    width: Dp = AppDueChipDefaults.BorderWidth,
): Modifier = drawWithCache {
    val strokePx = width.toPx()
    // The stroke is centred on the path, so inset by half of it to keep the outline inside bounds —
    // matching what Modifier.border does.
    val inset = strokePx / 2f
    val radius = (shape.topStart.toPx(size, this) - inset).coerceAtLeast(0f)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
    val stroke = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(
                AppDueChipDefaults.DashLength.toPx(),
                AppDueChipDefaults.DashGap.toPx(),
            ),
        ),
    )
    onDrawWithContent {
        drawContent()
        drawPath(path = path, color = color, style = stroke)
    }
}
