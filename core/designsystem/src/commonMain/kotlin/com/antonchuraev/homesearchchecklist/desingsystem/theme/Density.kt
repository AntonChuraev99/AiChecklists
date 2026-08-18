package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Density tokens for the dense task list — row heights, the paddings inside a row, and the rhythm
 * between rows and sections.
 *
 * Separate from [AppDimens] on purpose: [AppDimens] is a generic spacing scale used app-wide, while
 * these values describe **one anatomy** (the task row and the list around it) and move together. A
 * row that grows by 4dp has to grow its meta gap with it; keeping the two scales apart stops a
 * generic `SpacingMd` change from silently re-cutting the list.
 *
 * ⚠️ Every height here is a **minimum**, and call sites must apply it as `heightIn(min = …)`, never
 * `height(…)`. A fixed height clips Devanagari (which carries matras above *and* below the baseline)
 * and clips any text at a large system font scale. At `fontScale ≥ 1.3` a comfortable row genuinely
 * stops being compact — that is correct behaviour and must not be "fixed" by shrinking the font,
 * which is an accessibility violation.
 */
object AppDensity {

    /**
     * Default task row: title plus a meta row underneath.
     *
     * 60dp rather than the usual 56dp — the extra 4dp is the meta row (due chip, source glyph),
     * which lives on a second line inside the row's column rather than in the trailing edge.
     */
    val RowMinHeightComfortable: Dp = 60.dp

    /** "Compact" display setting: single-line rows, no meta row. */
    val RowMinHeightCompact: Dp = 44.dp

    /** Rows on a detail screen, which carry a checkbox plus richer trailing affordances. */
    val RowMinHeightDetail: Dp = 64.dp

    /** Horizontal inset inside a row, from the card edge to the checkbox. */
    val RowPaddingHorizontal: Dp = 12.dp

    /** Vertical inset inside a row, above the title and below the meta row. */
    val RowPaddingVertical: Dp = 8.dp

    /** Gap between the row's own children (checkbox → text column). */
    val RowGap: Dp = 8.dp

    /** Gap between two consecutive rows in the list. */
    val ListItemSpacing: Dp = 6.dp

    /** Gap between the task title and the meta row below it. */
    val MetaRowTopGap: Dp = 4.dp

    /** Gap between two chips inside the meta row. */
    val MetaChipGap: Dp = 4.dp

    /**
     * Space above a section header ("Overdue", "Today") — the visible break between groups.
     *
     * 12dp, not the 20dp the spec first called for. Measured on the rendered screen: at 20dp a
     * 411×914dp phone fits **five** tasks across four sections, and the gaps between groups read as
     * bigger objects than the cards themselves. The break does not need 20dp to be legible — the
     * header is already set apart by weight and colour, and the group below it is a run of white
     * cards on a warm page. Density is the product promise here; air is not.
     */
    val SectionHeaderTop: Dp = 12.dp

    /**
     * Space between a section header and the first row under it.
     *
     * Deliberately much smaller than [SectionHeaderTop]: the header belongs to the group *under* it,
     * so the asymmetry is what makes the grouping readable at a glance. Equal gaps above and below
     * would leave every header floating between two runs, belonging to neither.
     */
    val SectionHeaderBottom: Dp = 4.dp

    /**
     * Width of the priority bar at the row's leading edge.
     *
     * Priority moved off a 16dp trailing star onto a 3dp leading bar: it frees ~20dp of row width for
     * the title, lines the marker up vertically down the list, and leaves the trailing edge free.
     */
    val PriorityBarWidth: Dp = 3.dp
}
