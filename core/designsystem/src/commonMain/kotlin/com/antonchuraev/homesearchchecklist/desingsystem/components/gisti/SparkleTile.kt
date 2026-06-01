package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors

/**
 * Rounded square tile with AI gradient background and a centered AutoAwesome icon.
 * Used as the "AI mark" — appears in AskGistiBar, AppGradientButton, SparkleTile rows.
 *
 * The icon size is `size - 11.dp` per the design spec (28dp tile → 17dp icon).
 *
 * Token mapping:
 * - Background: [GistiColors.aiGradient] (blue→indigo, adapts to dark theme)
 * - Icon tint: [Color.White] (always white — gradient ensures contrast)
 */
@Composable
fun SparkleTile(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    cornerRadius: Dp = 9.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(GistiColors.aiGradient),
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null, // decorative — context provides semantics
            tint = Color.White,
            modifier = Modifier.size(size - 11.dp),
        )
    }
}
