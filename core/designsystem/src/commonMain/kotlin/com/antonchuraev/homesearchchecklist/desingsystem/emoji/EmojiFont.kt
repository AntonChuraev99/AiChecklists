package com.antonchuraev.homesearchchecklist.desingsystem.emoji

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Returns the emoji FontFamily to provide into [LocalEmojiFont].
 * wasmJs preloads noto_color_emoji.ttf; Android/iOS return [FontFamily.Default] (no-op,
 * system emoji fallback works there, so the 1.47MB font is NOT bundled on those targets).
 */
@Composable
expect fun rememberEmojiFont(): FontFamily
