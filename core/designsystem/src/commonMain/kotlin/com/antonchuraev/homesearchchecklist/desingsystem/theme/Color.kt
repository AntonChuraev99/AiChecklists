package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Primary Colors (Blue)
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

// Neutral Colors
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

// Semantic Colors
val Success = Color(0xFF4CAF50)
val SuccessLight = Color(0xFFE8F5E9)
val Error = Color(0xFFE53935)
val ErrorLight = Color(0xFFFFEBEE)
val Warning = Color(0xFFFF9800)
val WarningLight = Color(0xFFFFF3E0)

val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Blue700,
    onSecondary = White,
    secondaryContainer = Blue50,
    onSecondaryContainer = Blue900,
    tertiary = Blue600,
    onTertiary = White,
    background = White,
    onBackground = Gray900,
    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,
    outline = Gray300,
    outlineVariant = Gray200,
    error = Error,
    onError = White,
    errorContainer = ErrorLight,
    onErrorContainer = Error
)
