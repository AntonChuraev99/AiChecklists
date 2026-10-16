package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Tonal Palette — Blue seed #2196F3
// Generated following M3 HCT tonal palette algorithm (manually via Material
// Theme Builder rules: light uses tone 40 for primary, dark uses tone 80).
// ---------------------------------------------------------------------------

// Blue tonal palette (Hue ~210°, Chroma ~90)
private val Blue10 = Color(0xFF001C37)
private val Blue20 = Color(0xFF003258)
private val Blue30 = Color(0xFF004880)
private val Blue40 = Color(0xFF1565C0)  // Light primary (tone 40, close to #2196F3 hue)
private val Blue80 = Color(0xFF90CAF9)  // Dark primary  (tone 80) — WCAG AA on dark surface
private val Blue90 = Color(0xFFBBDEFB)  // Dark primaryContainer content
private val Blue95 = Color(0xFFE3F2FD)
private val Blue99 = Color(0xFFF8FEFF)

// Secondary — slightly desaturated blue (tone 40/80)
private val BlueSecondary40 = Color(0xFF4A6572)
private val BlueSecondary80 = Color(0xFFB0C8D4)
private val BlueSecondary90 = Color(0xFFCDE5F2)
private val BlueSecondary10 = Color(0xFF051E2A)
private val BlueSecondary30 = Color(0xFF324C59)

// Tertiary — complementary teal hue (tone 40/80)
private val Teal40 = Color(0xFF006874)
private val Teal80 = Color(0xFF4DD8E8)
private val Teal90 = Color(0xFFB2EBEF)
private val Teal10 = Color(0xFF001F23)
private val Teal30 = Color(0xFF004F57)

// Error — M3 standard (static, does not change with dynamic color)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)
private val Red10 = Color(0xFF410002)
private val Red30 = Color(0xFF8C0009)

// Neutral palette (for surface roles)
private val Neutral4  = Color(0xFF0E0C11)
private val Neutral6  = Color(0xFF141218)  // MD3 dark surface
private val Neutral10 = Color(0xFF1D1B20)
private val Neutral12 = Color(0xFF211F26)
private val Neutral17 = Color(0xFF2B2930)
private val Neutral20 = Color(0xFF322F35)
private val Neutral22 = Color(0xFF36343B)
private val Neutral24 = Color(0xFF3B383E)
private val Neutral87 = Color(0xFFDDD8E1)
private val Neutral90 = Color(0xFFE6E1E5)
private val Neutral92 = Color(0xFFEAE5EE)
private val Neutral94 = Color(0xFFEFEAF4)
private val Neutral96 = Color(0xFFF5EFFA)
private val Neutral98 = Color(0xFFFEF7FF)
private val Neutral95 = Color(0xFFF4EFF4)  // inverseOnSurface in light (text on snackbar)
private val NeutralVariant30 = Color(0xFF49454F)
private val NeutralVariant50 = Color(0xFF79747E)
private val NeutralVariant60 = Color(0xFF938F99)
private val NeutralVariant80 = Color(0xFFCAC4D0)
private val NeutralVariant90 = Color(0xFFE7E0EC)

// ---------------------------------------------------------------------------
// Redesign B "Soft & tactile" (2026-10-16) — sand neutral ramp, light.
// A warm sand page with cream cards one tonal step above it; the hairline
// (`outlineVariant`) carries the card edge. Replaces the "variant D" cream page
// (#FBFAF8) + white card (#FFFFFF). Brand roles (primary/secondary/tertiary/error)
// are untouched. Measured: onSurface on page 14.0:1, onSurfaceVariant ≥ 5.78:1 on
// page / card / recessed / sheet, outline (checkbox ring) 4.15:1 on page and
// 4.67:1 on card, primary on page 5.02:1. Card vs page ΔL* +4.7.
// ---------------------------------------------------------------------------
private val SandSurface          = Color(0xFFF4EFE7) // background / surface / surfaceBright — sand page
private val SandCard             = Color(0xFFFFFDF9) // surfaceContainerLowest — cream card / group
private val SandContainerLow     = Color(0xFFF9F5EF) // sheet / modal
private val SandContainer        = Color(0xFFEFE9DF) // recessed: chips, fills, ring track
private val SandContainerHigh    = Color(0xFFEAE3D8) // track / pressed
private val SandContainerHighest = Color(0xFFE4DCD0)
private val SandSurfaceDim       = Color(0xFFDDD5C8)
private val SandSurfaceVariant   = Color(0xFFECE5DA)
private val SandOutlineVariant   = Color(0xFFE6DED1) // hairlines: card / group edges, dividers
private val SandOutline          = Color(0xFF7A7266) // control borders, checkbox ring
private val SandInk              = Color(0xFF24211C) // onBackground / onSurface
private val SandInkVariant       = Color(0xFF5B554C) // onSurfaceVariant

// Warm darks — the same ramp shifted off the cool blue-black (#121317 family)
// to a warm near-black. Dark ladder only runs UP (card is lighter than page).
// Measured: onSurface on page 15.1:1, onSurfaceVariant ≥ 7.95:1, outline 4.63:1,
// card vs page ΔL* +5.5.
private val WarmDark4    = Color(0xFF100E0C) // surfaceContainerLowest
private val WarmDark6    = Color(0xFF161412) // background / surface / surfaceDim
private val WarmDark10   = Color(0xFF221F1B) // surfaceContainerLow — card in dark
private val WarmDark12   = Color(0xFF26231F) // surfaceContainer
private val WarmDark17   = Color(0xFF2D2924) // surfaceContainerHigh — recessed in dark
private val WarmDark22   = Color(0xFF35302A) // surfaceContainerHighest
private val WarmDark24   = Color(0xFF3A3530) // surfaceBright
private val WarmDarkOutlineVariant = Color(0xFF37322B)
private val WarmDarkOutline        = Color(0xFF8F877C)
private val WarmDarkInk            = Color(0xFFEEE8E0)
private val WarmDarkInkVariant     = Color(0xFFC8BFB3)
private val WarmDarkSurfaceVariant = Color(0xFF4A443C)

// ---------------------------------------------------------------------------
// Legacy named colors (kept for backward compat / raw usage in Color.kt only)
// ---------------------------------------------------------------------------

val Blue50 = Color(0xFFE3F2FD)
val Blue100 = Color(0xFFBBDEFB)
val Blue200 = Color(0xFF90CAF9)
val Blue300 = Color(0xFF64B5F6)
val Blue400 = Color(0xFF42A5F5)
val Blue500 = Color(0xFF2196F3)
val Blue600 = Color(0xFF1E88E5)
val Blue700 = Color(0xFF1976D2)
val Blue800 = Color(0xFF1565C0)
val Blue900 = Color(0xFF0D47A1)

val White = Color(0xFFFFFFFF)
val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFEEEEEE)
val Gray300 = Color(0xFFE0E0E0)
val Gray400 = Color(0xFFBDBDBD)
val Gray500 = Color(0xFF9E9E9E)
val Gray600 = Color(0xFF757575)
val Gray700 = Color(0xFF616161)
val Gray800 = Color(0xFF424242)
val Gray900 = Color(0xFF212121)

val Success = Color(0xFF4CAF50)
val SuccessLight = Color(0xFFE8F5E9)
val Error = Color(0xFFE53935)
val ErrorLight = Color(0xFFFFEBEE)
val Warning = Color(0xFFFF9800)
val WarningLight = Color(0xFFFFF3E0)

// ---------------------------------------------------------------------------
// Light Color Scheme
// Primary (#1565C0 / Blue40) on white surface → contrast ratio ≈ 8.6:1 (WCAG AAA)
// onPrimary (White) on primary → contrast ratio ≈ 8.6:1 (WCAG AAA)
// ---------------------------------------------------------------------------
val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = White,
    primaryContainer = Blue95,
    onPrimaryContainer = Blue10,
    secondary = BlueSecondary40,
    onSecondary = White,
    secondaryContainer = BlueSecondary90,
    onSecondaryContainer = BlueSecondary10,
    tertiary = Teal40,
    onTertiary = White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    error = Red40,
    onError = White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = SandSurface,
    onBackground = SandInk,
    surface = SandSurface,
    onSurface = SandInk,
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = SandInkVariant,
    surfaceContainerLowest = SandCard,
    surfaceContainerLow = SandContainerLow,
    surfaceContainer = SandContainer,
    surfaceContainerHigh = SandContainerHigh,
    surfaceContainerHighest = SandContainerHighest,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = Blue80,
    outline = SandOutline,
    outlineVariant = SandOutlineVariant,
    scrim = Color.Black,
    surfaceBright = SandSurface,
    surfaceDim = SandSurfaceDim
)

// ---------------------------------------------------------------------------
// Dark Color Scheme — M3 Dark (NOT AMOLED true-black: surface = #141218)
//
// Contrast ratios (WCAG AA minimum: 4.5:1 normal text, 3:1 large):
//   primary (#90CAF9) on surface (#141218) → ≈ 9.1:1  ✓ WCAG AAA
//   onSurface (#E6E1E5) on surface (#141218) → ≈ 13.5:1 ✓ WCAG AAA
//   onPrimary (#001C37 / Blue10) on primary (#90CAF9) → ≈ 10.0:1 ✓ WCAG AAA
//   secondary (#B0C8D4) on surface (#141218) → ≈ 8.2:1  ✓ WCAG AAA
// ---------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = BlueSecondary80,
    onSecondary = BlueSecondary10,
    secondaryContainer = BlueSecondary30,
    onSecondaryContainer = BlueSecondary90,
    tertiary = Teal80,
    onTertiary = Teal10,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    error = Red80,
    onError = Red10,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = WarmDark6,
    onBackground = WarmDarkInk,
    surface = WarmDark6,
    onSurface = WarmDarkInk,
    surfaceVariant = WarmDarkSurfaceVariant,
    onSurfaceVariant = WarmDarkInkVariant,
    surfaceContainerLowest = WarmDark4,
    surfaceContainerLow = WarmDark10,
    surfaceContainer = WarmDark12,
    surfaceContainerHigh = WarmDark17,
    surfaceContainerHighest = WarmDark22,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = Blue40,
    outline = WarmDarkOutline,
    outlineVariant = WarmDarkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = WarmDark24,
    surfaceDim = WarmDark6
)

