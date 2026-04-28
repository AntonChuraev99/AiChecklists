package com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * FeatureRow — icon tile + title + body. Used in PaywallVariant.Features.
 *
 * Icon container is fixed Material Blue 50 (#E3F2FD); icon tinted with the
 * theme's primary blue so the row reads as a single brand-color unit across
 * all features instead of cycling through M3 container hues.
 */
private val FeatureIconBg = Color(0xFFE3F2FD)

@Composable
internal fun FeatureRow(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingSm),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(FeatureIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                // Fixed black tint (not theme-derived) — gives the M3 Filled
                // icon set maximum visual weight against the light-blue tile,
                // so even icons with semi-outline geometry (Checklist,
                // ContentCopy) read as solid shapes.
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
