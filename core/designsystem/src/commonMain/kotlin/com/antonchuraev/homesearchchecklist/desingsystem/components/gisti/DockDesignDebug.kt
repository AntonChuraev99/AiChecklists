package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Debug-only, in-memory toggle for the chat-dock visual style.
 *
 * Default `false` → the current "Crisp hairline" design ([gistiDockColor] white in light + a top-only
 * hairline). Flip to `true` from the Debug menu to preview the OLD flat-grey dock
 * (`surfaceContainerLow`, no hairline) for a quick visual A/B comparison.
 *
 * In-memory only — resets on process restart, never persisted, and nothing in release flips it (the
 * toggle UI lives in the debug-only Debug screen). It is a [mutableStateOf] so both the dock
 * ([GistiGlassChatDock]) and the Debug screen recompose when it changes. Lives in `designsystem` so
 * both modules can reach it without a new dependency edge.
 */
object DockDesignDebug {
    var useLegacyDock by mutableStateOf(false)
}
