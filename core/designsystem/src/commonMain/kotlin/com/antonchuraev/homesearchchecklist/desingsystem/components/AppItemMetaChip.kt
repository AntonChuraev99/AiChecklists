package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTextStyles

/**
 * Sizes and limits shared by every meta chip, and by [AppDueChip] which wraps one.
 *
 * They live in an object rather than as file-private constants because the due chip needs the same
 * numbers to reason about its own trailing glyph, and two copies of "12dp" would drift.
 */
object AppItemMetaChipDefaults {

    /**
     * Width ceiling for the whole chip, **at `fontScale` 1.0**. Read it through [maxWidth], never
     * directly.
     *
     * Meta chips sit in a row under the task text, and Russian runs 30–50% longer than English, so
     * an uncapped chip pushes its neighbours out of a 60dp row. 140dp is the value the checklist
     * detail screen already used for its reminder chip; it is a default here so that every chip in
     * the product inherits it instead of each call site remembering to cap itself.
     */
    val MaxWidth: Dp = 140.dp

    /**
     * How far the cap is allowed to follow the user's font scale.
     *
     * Past this the chip starts taking width from the task TITLE in the compact layout (there the
     * two share one line and the title is the weighted child, so every dp the chip claims is a dp
     * the title loses). At that point a clipped word is the smaller loss than a title smeared down
     * five lines — so the budget stops growing and truncation resumes, correctly this time, from
     * the front.
     */
    const val MaxWidthFontScaleCeiling = 1.5f

    /**
     * [MaxWidth] for the font scale actually in force.
     *
     * ## Why the cap cannot be a constant
     * The budget is expressed in **dp** and the thing it has to hold is expressed in **sp**, so
     * their ratio is `fontScale` and a fixed cap is only ever right at 1.0. At 1.5 the very same
     * 140dp held less than half a label, and [TextOverflow.MiddleEllipsis] then ate INTO the clock:
     * "Today 14:00" rendered as "Tod…4:00" and "Mon,Wed 18:30" as "Mon…8:30" — not a shortened
     * label but a **different, plausible time**. That is worse than showing nothing, because
     * nothing at least looks like nothing.
     */
    val maxWidth: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(MaxWidth)

    /**
     * [base] adjusted for the current font scale, clamped by [MaxWidthFontScaleCeiling].
     *
     * Public so a call site with its own, tighter budget (the compact task row's trailing chip)
     * scales it the same way instead of re-deriving the rule and drifting.
     */
    @Composable
    @ReadOnlyComposable
    fun scaled(base: Dp): Dp =
        base * LocalDensity.current.fontScale.coerceIn(1f, MaxWidthFontScaleCeiling)

    /** Leading icon — the one that names the chip's kind. */
    val IconSize: Dp = 14.dp

    /**
     * Trailing icon — a modifier on the chip's meaning (repeats, an alarm is armed).
     *
     * Smaller than [IconSize] on purpose: it qualifies the chip rather than identifying it, and at
     * equal size the two glyphs read as two chips crammed together.
     */
    val TrailingIconSize: Dp = 12.dp
}

/**
 * A compact read-only pill chip used in the meta row under a task's text: priority, due date,
 * attachment count, source.
 *
 * Design decisions:
 * - No `onClick` / `clickable` — purely informational. On a task card the row is covered by the
 *   30/70 hit-zone overlay, and a clickable chip would eat part of the 70% zone, so a tap next to
 *   it would stop opening the details sheet. Toggling happens inside `ItemDetailsSheet`.
 * - Height ~22dp: icon 14dp plus 4dp of vertical padding either side.
 * - Shape: `shapes.extraSmall` (6dp) — more rectangular than a full pill so it reads as a "data
 *   tag" rather than an "action chip". This used to be a hardcoded `RoundedCornerShape(6.dp)`; the
 *   shape scale was recut so that the token now *is* 6dp, and the hardcode is gone.
 * - Colors are passed as semantic roles from the caller — no hardcoded `Color` values here.
 *
 * ## Truncation: clip the word, not the hour
 * [labelOverflow] exists because a due label is "Tomorrow 09:00" — two pieces of information of
 * very unequal value. Trailing ellipsis throws away the half the user actually needs, so
 * [AppDueChip] passes [TextOverflow.MiddleEllipsis] and keeps the time visible. The label is also
 * the only flexible child of the row (`Modifier.weight(1f, fill = false)`), so the leading and
 * trailing glyphs are never the thing that gets dropped when space runs out.
 *
 * @param icon           Leading icon (decorative — the label carries the meaning for screen readers).
 * @param label          Short text label, already resolved and localized by the caller.
 * @param containerColor Tonal background. Use `*Container` roles from [MaterialTheme.colorScheme].
 * @param contentColor   Icon + text color. Use the paired `on*Container` role.
 * @param modifier       Optional external modifier. Applied before the built-in width cap, so a
 *                       caller may narrow the chip further but not silently widen it past
 *                       [maxWidth] with a `widthIn` — widening is what [maxWidth] itself is for.
 * @param maxWidth       Width ceiling. Defaults to the font-scale-aware
 *                       [AppItemMetaChipDefaults.maxWidth]. Pass [Dp.Unspecified] where the chip
 *                       sits on a line of its own and the ROW is the real bound — then give it
 *                       `Modifier.weight(1f, fill = false)` so its neighbours are measured first
 *                       and it takes only what is left. A constant cap there is a guess that is
 *                       wrong in both directions: too tight for "Tomorrow 09:00" with an alarm
 *                       glyph even at `fontScale` 1.0, and far short of the space the line
 *                       actually has.
 * @param border         Optional outline. Shape is a channel of meaning here, not decoration: the
 *                       overdue state is findable in a column and in greyscale because it is
 *                       outlined, not because it is coloured.
 * @param trailingIcon   Optional 12dp glyph after the label — "this repeats", "an alarm is armed".
 * @param labelStyle     Overrides [AppTextStyles.metaLabel]. [AppDueChip] uses
 *                       [AppTextStyles.monoTime] so a ticking clock does not resize the chip.
 * @param labelOverflow  How the label truncates once it hits the width cap.
 */
@Composable
fun AppItemMetaChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    maxWidth: Dp = AppItemMetaChipDefaults.maxWidth,
    border: BorderStroke? = null,
    trailingIcon: ImageVector? = null,
    labelStyle: TextStyle? = null,
    labelOverflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        border = border,
        modifier = modifier.widthIn(max = maxWidth),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.SpacingSm,
                vertical = AppDimens.SpacingXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // decorative — label is the semantic content
                modifier = Modifier.size(AppItemMetaChipDefaults.IconSize),
                tint = contentColor,
            )
            Text(
                text = label,
                style = labelStyle ?: AppTextStyles.metaLabel,
                color = contentColor,
                maxLines = 1,
                overflow = labelOverflow,
                // fill = false: the label shrinks only when it has to, and it is the ONLY child
                // that shrinks — the two glyphs are measured first and always survive.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null, // decorative — the label already says what this is
                    modifier = Modifier.size(AppItemMetaChipDefaults.TrailingIconSize),
                    tint = contentColor,
                )
            }
        }
    }
}
