package com.antonchuraev.homesearchchecklist.desingsystem.emoji

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.noto_color_emoji
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun rememberEmojiFont(): FontFamily {
    val emojiFont by preloadFont(Res.font.noto_color_emoji)
    return emojiFont?.let { FontFamily(listOf(it)) } ?: FontFamily.Default
}
