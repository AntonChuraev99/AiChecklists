package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

/**
 * Full-width gradient CTA button with AI gradient background.
 *
 * Implemented as `Box + Modifier.background(Brush)` rather than a Material [Button]
 * because [Button] does not accept a [Brush] for its container color — it only accepts
 * a solid [Color] via [ButtonDefaults.buttonColors]. Using Box avoids a workaround with
 * a custom [ButtonColors] that still produces wrong ripple colors.
 *
 * Visual spec (from gisti-extra.jsx FilledBtn with gradient=true):
 *  - Height: 56dp, corner radius: 18dp
 *  - Background: [GistiColors.aiGradient] (disabled: `surfaceVariant`)
 *  - Text: white, 16.5sp, weight 700
 *  - Icon: white, 21dp (when provided)
 *  - Shadow: 4dp in light, none in dark
 *  - Disabled: faint background, no click, reduced alpha on content
 *
 * Token mapping:
 * - Enabled background: [GistiColors.aiGradient]
 * - Disabled background: `colorScheme.surfaceVariant`
 * - Content: [Color.White] on enabled, `colorScheme.onSurfaceVariant` on disabled
 *
 * @param text Button label.
 * @param onClick Called when the button is tapped (no-op when [enabled] is false).
 * @param icon Optional leading icon. Defaults to [Icons.Filled.AutoAwesome].
 *             Pass `null` to show no icon.
 * @param enabled Whether the button accepts interaction.
 */
@Composable
fun AppGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.AutoAwesome,
    enabled: Boolean = true,
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(18.dp)

    val backgroundModifier = if (enabled) {
        Modifier.background(GistiColors.aiGradient)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    }

    val contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .then(backgroundModifier)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics { role = Role.Button }
            .padding(horizontal = AppDimens.SpacingLg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(21.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
