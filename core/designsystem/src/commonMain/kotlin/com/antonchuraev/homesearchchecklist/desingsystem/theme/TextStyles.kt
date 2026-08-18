package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Product roles layered on top of the 15 Material 3 type slots.
 *
 * M3 hands out exactly fifteen slots and there is no sixteenth to add, so roles the product needs by
 * *meaning* rather than by size live here as theme-reading accessors. Every one of them derives from
 * a slot in [AppTypography] rather than declaring metrics from scratch, so the `lineHeightStyle` that
 * keeps Devanagari matras from being clipped is inherited automatically.
 *
 * Tabular figures (`tnum`) appear on the two roles that render numbers which change in place. Without
 * them "9:05" and "18:00" are different widths and the chip around them breathes on every tick.
 */
object AppTextStyles {

    /**
     * The task text itself — currently identical to `bodyLarge`.
     *
     * An alias, not a duplicate: it exists so that tuning the task line later does not drag every
     * other `bodyLarge` in the product along with it.
     */
    val taskTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge

    /**
     * Group heading inside a list — "Overdue", "Today".
     *
     * ⛔ Not uppercased. Devanagari has no letter case, so an uppercase transform is a no-op on hi
     * and the heading loses the very emphasis it was meant to carry.
     */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    /** Chip labels in the meta row. */
    val metaLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall

    /** Clock time on a chip. Tabular figures keep the chip from resizing as the time changes. */
    val monoTime: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium.copy(
            fontFeatureSettings = TabularFigures,
        )

    /** Big counters (Overview) and the paywall price. Tabular figures keep a ticking number steady. */
    val numericDisplay: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-1.0).sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TabularFigures,
        )

    /** OpenType feature tag for fixed-width digits. */
    private const val TabularFigures = "tnum"
}
