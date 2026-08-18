package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Line-height behaviour applied to **every** role below.
 *
 * `Trim.None` keeps the full line box on the first and last lines instead of trimming it to the glyph
 * bounds, and `Alignment.Center` distributes the leftover leading evenly above and below. Devanagari
 * carries matras above *and* below the baseline, so a trimmed first line clips the top matra outright
 * — the same class of clipping the hi locale already hit on fixed-height pills.
 */
private val AppLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * The app's type scale.
 *
 * **No bundled font, by decision.** The UI ships in EN · RU · HI, i.e. Latin + Cyrillic +
 * **Devanagari**; no OFL text face with real character covers all three, Skiko's glyph fallback on
 * wasmJs is unreliable (the project has already shipped tofu once), and Compose Resources fetches a
 * web font *after* the canvas mounts, reflowing the whole Skiko surface on every cold load. The
 * character therefore comes from the scale itself — weight, tracking and optical sizes — not from a
 * typeface.
 *
 * Two things drive the recut:
 * - **Weight replaces size.** Titles go up to Bold and down in size: `titleLarge` at 20sp Bold weighs
 *   as much as 22sp Medium while taking ~9% less width, which is the main lever against a two-line
 *   top bar on ru/hi. (It buys headroom, it does not guarantee one line at `fontScale ≥ 1.3` — that
 *   is expected, and must not be "fixed" by capping lines.)
 * - **Tracking goes to zero on body and titles.** The 0.15–0.5sp letter spacing in the M3 defaults is
 *   Roboto-era compensation; on a modern UI face it just reads as slack. It survives only on the
 *   small sizes (≤12sp), where a little tracking genuinely aids legibility.
 *
 * Leading stays generous — display/headline ≥1.25×, title/body/label ≥1.33× — for the same
 * Devanagari reason as [AppLineHeightStyle].
 *
 * Product roles layered on top of these fifteen slots live in [AppTextStyles].
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        // 57sp overflows ru/hi at fontScale 1.3 before the string is even long.
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.0).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.75).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    headlineSmall = TextStyle(
        // MediumTopAppBar's title; −2sp wins back a line on ru/hi.
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    titleLarge = TextStyle(
        // The main lever against a two-line top bar — see the class doc.
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    bodyLarge = TextStyle(
        // The task text. 0.5sp of tracking here is visible slack; leading is NOT tightened.
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    bodyMedium = TextStyle(
        // ⚠️ fontSize and lineHeight are LOAD-BEARING and must stay 14/20: AppScaffold's
        // subtitleExtraHeight() derives the top bar's height from bodyMedium.lineHeight on every
        // screen that shows a subtitle. Only the tracking is retuned here.
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    bodySmall = TextStyle(
        // Small sizes keep some tracking — it aids legibility rather than loosening the line.
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    labelLarge = TextStyle(
        // W600 is what separates a button label from body text at the same 14sp.
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    labelMedium = TextStyle(
        // 0.5sp at 12sp adds 3–4dp to every chip; 0.3sp keeps the legibility without the bloat.
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        lineHeightStyle = AppLineHeightStyle,
    ),
)
