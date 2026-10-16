package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Gisti "variant D" accent tokens that live on top of the Material 3
 * [androidx.compose.material3.ColorScheme] but are intentionally **not** part of
 * the standard Material role set:
 *
 *  - the **AI gradient** (blue → indigo sheen) used for sparkle moments,
 *  - per-checklist **avatar tints** for the list cards,
 *  - the priority **star** gold,
 *  - a **calm completion green** for 100%-done states.
 *
 * Every accessor reads [LocalIsDarkTheme] (the *user-selected* theme, which may
 * differ from the system theme) so tokens follow the app theme correctly.
 *
 * Usage:
 * ```kotlin
 * Box(Modifier.background(GistiColors.aiGradient, RoundedCornerShape(9.dp)))
 * Icon(Icons.Filled.Star, null, tint = GistiColors.star)
 * val tint = GistiColors.avatarTint(checklist.id)
 * ```
 */
object GistiColors {

    // ── AI gradient endpoints (light vs dark per design spec) ──
    // Redesign B (2026-10-16): one step deeper so the WHITE glyph/label on the gradient clears AA.
    // Light: white on start 3.12 → 4.67:1, on end 4.86 → 5.96:1 (was #2196F3 → #5C6BC0).
    // Dark:  white on start 3.35 → 4.84:1, on end 3.72 → 5.34:1 (was #3E8DF0 → #6E7CE2).
    private val AiFromLight = Color(0xFF1A73D9)
    private val AiToLight = Color(0xFF4F5BB8)
    private val AiFromDark = Color(0xFF2A6FD6)
    private val AiToDark = Color(0xFF5562C4)

    /** Diagonal AI sheen for sparkle tiles, "Create/Fill with AI" buttons, the FAB. */
    val aiGradient: Brush
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) {
            Brush.linearGradient(listOf(AiFromDark, AiToDark))
        } else {
            Brush.linearGradient(listOf(AiFromLight, AiToLight))
        }

    /** Solid first AI color — for tints/borders where a gradient is too heavy. */
    val aiStart: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) AiFromDark else AiFromLight

    val aiEnd: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) AiToDark else AiToLight

    // ── Priority star amber ──
    /**
     * Priority star glyph. Redesign B makes the inline star a SOLE-channel indicator on the grouped
     * task row (the priority bar is not drawn there), so the light value is the same darkened amber
     * as [priorityIndicator]: `#A86400` is 4.61:1 on the cream card (the old `#F4A923` was 1.96:1).
     * Dark keeps `#F5B544`.
     */
    val star: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFFF5B544) else Color(0xFFA86400)

    /**
     * Priority indicator when the shape carrying it is the ONLY channel — the 3dp bar along a task
     * row's leading edge, where [star] would fail contrast.
     *
     * [star] is tuned as a *glyph* colour: a star icon reads at 16dp because its silhouette carries
     * the meaning, and the surrounding text says the rest. A 3dp bar has no silhouette and no label —
     * it is a non-text UI element that must clear WCAG 1.4.11 (3:1) on its own, and `#F4A923` gives
     * only ~2:1 against the white card. Redesign B (2026-10-16) takes the light value to `#A86400`
     * (4.61:1 on the cream card, was `#C67C00` at 3.29:1) — the SAME value as [star]: one signal,
     * one colour. It also rings the round checkbox of an important task.
     *
     * Dark keeps [star] unchanged: `#F5B544` on the warm dark card is already far past the bar.
     */
    val priorityIndicator: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFFF5B544) else Color(0xFFA86400)

    // ── Bottom-chrome tone ──
    /**
     * The tone of the app's bottom chrome — the v2 navigation bar, both docks, the full-screen chat
     * overlay and the system-nav strip behind them. Read by `AppSurface.bottomChrome()`, never by a
     * call site.
     *
     * A LITERAL rather than a `surfaceContainer*` role because the whole point of the re-tune is
     * CHROMA: every neutral in this palette at this lightness is a warm grey (`surfaceDim` `#DEDCD6`,
     * L\* 87.8, chroma ~3), which is what the owner rejected on the device ("серый… точно нужно
     * поменять"). This pair keeps the measured lightness relationships of the family it replaces
     * (see `AppSurface.bottomChrome`'s tables) and adds the blue lean the dark page `#121317` — itself
     * a blue-black — already has.
     */
    val chrome: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFF191D25) else Color(0xFFDCE2EC)

    // ── Calm completion green (100% banners, done progress) ──
    val success: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFF5FD08A) else Color(0xFF2E9E5B)

    val successContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFF152A1E) else Color(0xFFE7F4EC)

    val onSuccessContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFF7FD3A2) else Color(0xFF1B5E37)

    // ── Per-checklist avatar tints (deterministic palette) ──
    // The domain model has no emoji/color field, so the list card derives a stable
    // tint + content color from the checklist id. 6 calm hues, light & dark variants.
    private val tintsLight = listOf(
        Color(0xFFE7F4EC), // green
        Color(0xFFE3F2FD), // blue
        Color(0xFFFFF1E6), // peach
        Color(0xFFF0EAFB), // lilac
        Color(0xFFFDEBF0), // pink
        Color(0xFFFFF8E1), // amber
    )
    private val tintsDark = listOf(
        Color(0xFF1B2A20),
        Color(0xFF15304A),
        Color(0xFF2E2317),
        Color(0xFF241E34),
        Color(0xFF34202A),
        Color(0xFF332B16),
    )
    // Redesign B (2026-10-16): darkened so the monogram (which is TEXT) clears AA on its own tint —
    // 5.03–6.10:1, was 2.16–4.16:1. Same six hues in the same order.
    private val tintContentLight = listOf(
        Color(0xFF1D6B40),
        Color(0xFF1565C0),
        Color(0xFFA04A12),
        Color(0xFF6340B5),
        Color(0xFFA52F5C),
        Color(0xFF7E5800),
    )
    private val tintContentDark = listOf(
        Color(0xFF7FD3A2),
        Color(0xFF73B7F5),
        Color(0xFFE3A877),
        Color(0xFFB9A2EC),
        Color(0xFFE69BB6),
        Color(0xFFE6C76E),
    )

    /** Stable pale background tint for a checklist avatar tile, derived from [key]. */
    @Composable
    @ReadOnlyComposable
    fun avatarTint(key: Long): Color {
        val list = if (LocalIsDarkTheme.current) tintsDark else tintsLight
        return list[paletteIndex(key, list.size)]
    }

    /** Stable saturated content color (icon/monogram) matching [avatarTint] for the same [key]. */
    @Composable
    @ReadOnlyComposable
    fun avatarContent(key: Long): Color {
        val list = if (LocalIsDarkTheme.current) tintContentDark else tintContentLight
        return list[paletteIndex(key, list.size)]
    }

    /**
     * Palette tint by POSITION (0 green · 1 blue · 2 peach · 3 lilac · 4 pink · 5 amber), for UI that
     * needs a specific hue rather than one derived from a checklist id — e.g. the Inbox section-header
     * icon tiles. [index] wraps with `mod`, so any Int is safe.
     */
    @Composable
    @ReadOnlyComposable
    fun paletteTint(index: Int): Color {
        val list = if (LocalIsDarkTheme.current) tintsDark else tintsLight
        return list[index.mod(list.size)]
    }

    /** Content colour (icon / monogram) matching [paletteTint] for the same [index]. */
    @Composable
    @ReadOnlyComposable
    fun paletteContent(index: Int): Color {
        val list = if (LocalIsDarkTheme.current) tintContentDark else tintContentLight
        return list[index.mod(list.size)]
    }

    /** Pure, testable index mapping — same checklist id always maps to the same hue. */
    fun paletteIndex(key: Long, size: Int): Int {
        val mixed = key xor (key ushr 32)
        val mod = (mixed % size).toInt()
        return if (mod < 0) mod + size else mod
    }
}
