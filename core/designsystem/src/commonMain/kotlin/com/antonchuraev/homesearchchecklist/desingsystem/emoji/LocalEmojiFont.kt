package com.antonchuraev.homesearchchecklist.desingsystem.emoji

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * Loaded emoji FontFamily — Twemoji/NotoColor on wasmJs, [FontFamily.Default] on Android/iOS
 * (their system fonts already cover emoji). Provided once at the App root via
 * CompositionLocalProvider; read by any composable that renders emoji Text. On wasmJs Skiko
 * has NO system emoji fallback, so emoji-bearing Text MUST set fontFamily = LocalEmojiFont.current
 * (or use [rememberEmojiAwareText]).
 */
val LocalEmojiFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
