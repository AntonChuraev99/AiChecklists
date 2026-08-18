package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Exposes the currently active app theme (not system theme). Prefer this over
 * `isSystemInDarkTheme()` when a component needs to adapt its appearance —
 * user-selected theme may differ from system setting.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Gisti application theme.
 *
 * @param darkTheme Whether to apply the dark color scheme. Default is false (light).
 *   The caller is responsible for reading the user's theme preference from DataStore
 *   and passing the correct value. Call-sites that omit this parameter keep light theme
 *   behavior unchanged — no breaking change.
 * @param dynamicColor Whether to prefer the platform-provided Material You palette
 *   over the static [LightColorScheme] / [DarkColorScheme]. Has effect only on
 *   platforms where [supportsDynamicColor] returns `true` (Android 12+). On any
 *   other platform the flag is silently ignored and the static scheme is used.
 *   Default is `false` to keep existing call-sites unchanged.
 * @param content The composable content to theme.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dynamicScheme = if (dynamicColor) rememberDynamicColorScheme(darkTheme) else null
    val colorScheme = dynamicScheme
        ?: if (darkTheme) DarkColorScheme else LightColorScheme

    // TODO(ux-overhaul): resolve from the platform once `isReducedMotionEnabled()` exists — Android
    //  reads Settings.Global.ANIMATOR_DURATION_SCALE, wasmJs reads the
    //  `(prefers-reduced-motion: reduce)` media query. The expect/actual is owned by @kmp-expert and
    //  the platform specialists; `false` here preserves today's behaviour exactly. See
    //  [LocalReducedMotion] for the contract call sites must honour.
    val reducedMotion = false

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
        ) {
            // Provided INSIDE MaterialTheme's content lambda on purpose: MaterialTheme installs its
            // own values around the content it wraps, so a provider placed outside it is overwritten
            // and the change silently does nothing.
            //
            // BLOCKED(ux-overhaul): `LocalMotionScheme provides MotionScheme.expressive()` belongs
            //  here — it would hand the app's spring language to stock M3 components (sheets, FAB,
            //  Switch) in one line. It does NOT compile on Compose Multiplatform 1.11.0: the symbols
            //  ship inside material3-1.11.0-alpha07 but are `internal`, so they exist in the klib yet
            //  are unusable from our code:
            //      Cannot access 'interface MotionScheme : Any': it is internal in file.
            //      Cannot access 'fun expressive(): MotionScheme': it is internal in
            //          'androidx.compose.material3.MotionScheme.Companion'.
            //      Cannot access 'annotation class ExperimentalMaterial3ExpressiveApi': internal.
            //      Unresolved reference 'LocalMotionScheme'.
            //  Not worked around: there is no public substitute, and reaching past `internal` is not
            //  one. Stock M3 components keep their default motion until CMP repackages androidx
            //  material3 1.4.x with the expressive API public; app-authored motion is unaffected and
            //  uses AppMotion directly.
            CompositionLocalProvider(
                LocalReducedMotion provides reducedMotion,
                content = content,
            )
        }
    }
}
