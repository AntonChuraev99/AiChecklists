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
    private val AiFromLight = Color(0xFF2196F3)
    private val AiToLight = Color(0xFF5C6BC0)
    private val AiFromDark = Color(0xFF3E8DF0)
    private val AiToDark = Color(0xFF6E7CE2)

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

    // ── Priority star gold ──
    val star: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFFF5B544) else Color(0xFFF4A923)

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
    private val tintContentLight = listOf(
        Color(0xFF2E9E5B),
        Color(0xFF2196F3),
        Color(0xFFE8833A),
        Color(0xFF7E5BD0),
        Color(0xFFD24B7E),
        Color(0xFFD9A21E),
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

    /** Pure, testable index mapping — same checklist id always maps to the same hue. */
    fun paletteIndex(key: Long, size: Int): Int {
        val mixed = key xor (key ushr 32)
        val mod = (mixed % size).toInt()
        return if (mod < 0) mod + size else mod
    }
}
