package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * How far away a task's due date is, as far as the *visual language* is concerned.
 *
 * Deliberately coarser than the domain: the UI distinguishes only what changes the chip's appearance.
 * Mapping a concrete instant onto one of these lives in the feature layer, together with the label
 * text — `core:designsystem` never learns about `ChecklistFillItem`.
 *
 * "No due date" is **not** a state here: a task without a date renders **no chip at all**. That is
 * the cheapest possible neutral — zero pixels, zero weight, zero blame — and it is why the enum has
 * four entries rather than five.
 */
enum class GistiScheduleState {
    /** More than a day out. */
    Later,

    /** Parked without a concrete date. Drawn with a dashed outline. */
    Someday,

    /** Today or tomorrow — the only state that gets an accent color. */
    Active,

    /** The moment has passed. Amber, never red. */
    Overdue,
}

/**
 * The three color channels a due chip paints: fill, text/icon, and an optional outline.
 *
 * @property container chip background.
 * @property content label and icon color, contrast-checked against [container].
 * @property border outline color, or `null` when the state draws no outline.
 * @property borderDashed whether [border] is drawn dashed. Shape carries meaning here, so it is a
 *   token rather than a call-site decision.
 */
@Immutable
data class GistiScheduleColors(
    val container: Color,
    val content: Color,
    val border: Color? = null,
    val borderDashed: Boolean = false,
)

/**
 * Due-date semantics — the color half of it.
 *
 * **Color is never the only channel.** Each state also carries its own icon and its own label shape
 * (see the due chip in the components layer), so the meaning survives color blindness, a greyscale
 * screenshot, and the iOS 27 glass-transparency slider. This object owns only what is colour; the
 * icon and the label string belong to the chip, because the label is user-facing copy and must come
 * from a string resource.
 *
 * ## Why overdue is amber, not red
 * A list of red rows reads as "the app is displeased with me", and a user who is met by that captures
 * *less* — which works directly against the point of reminders. Findability is recovered by the
 * outline instead: an outlined chip stands out in a column and stays distinguishable in greyscale.
 *
 * ## Why active is blue, not green
 * Green already means "done" ([GistiColors.success]). Reusing it for "due now" would make the two
 * most common states of a task the same colour.
 *
 * Every accessor reads [LocalIsDarkTheme] — the *user-selected* theme, which may differ from the
 * system one — exactly like [GistiColors].
 */
object GistiSchedule {

    // ── Active: light content + dark container are new; the other two are scheme roles ──
    private val ActiveContentLight = Color(0xFF0B4C93)
    private val ActiveContainerDark = Color(0xFF0F3B66)

    // ── Overdue: amber, all six new ──
    private val OverdueContainerLight = Color(0xFFFBE3D4)
    private val OverdueContentLight = Color(0xFF733609)
    private val OverdueBorderLight = Color(0xFFE8A87C)
    private val OverdueContainerDark = Color(0xFF3A2517)
    private val OverdueContentDark = Color(0xFFF2B183)
    private val OverdueBorderDark = Color(0xFF6B4A32)

    /** Full color bundle for [state]. */
    @Composable
    @ReadOnlyComposable
    fun colors(state: GistiScheduleState): GistiScheduleColors = when (state) {
        GistiScheduleState.Later -> GistiScheduleColors(
            container = neutralContainer(),
            content = neutralContent(),
        )

        GistiScheduleState.Someday -> GistiScheduleColors(
            container = neutralContainer(),
            content = neutralContent(),
            border = MaterialTheme.colorScheme.outline,
            borderDashed = true,
        )

        GistiScheduleState.Active -> GistiScheduleColors(
            container = activeContainer,
            content = activeContent,
        )

        GistiScheduleState.Overdue -> GistiScheduleColors(
            container = overdueContainer,
            content = overdueContent,
            border = if (LocalIsDarkTheme.current) OverdueBorderDark else OverdueBorderLight,
        )
    }

    /** Container for "today / tomorrow". Light reuses `primaryContainer`; dark needs its own tone. */
    val activeContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) {
            ActiveContainerDark
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }

    /** Content for "today / tomorrow". Dark reuses `onPrimaryContainer`; light needs its own tone. */
    val activeContent: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            ActiveContentLight
        }

    /**
     * Amber container for a passed due date.
     *
     * Also the alarm tone for the credits chip on its last action: a warning, not an error. An
     * `error`-red badge in the top bar reads as "the app is broken" rather than "you are running out".
     */
    val overdueContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) OverdueContainerDark else OverdueContainerLight

    /** Content color paired with [overdueContainer]. */
    val overdueContent: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) OverdueContentDark else OverdueContentLight

    /** Neutral chip fill shared by [GistiScheduleState.Later] and [GistiScheduleState.Someday]. */
    @Composable
    @ReadOnlyComposable
    private fun neutralContainer(): Color = AppSurface.recessed()

    /** Neutral chip content shared by [GistiScheduleState.Later] and [GistiScheduleState.Someday]. */
    @Composable
    @ReadOnlyComposable
    private fun neutralContent(): Color = MaterialTheme.colorScheme.onSurfaceVariant
}
