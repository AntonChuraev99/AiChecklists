package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

/**
 * Compact tappable card showing the "Today" focus summary row on the home screen.
 *
 * Visual spec (from gisti-screens.jsx RxHome today card):
 *  - Padding: 13dp vertical, 16dp horizontal
 *  - Corner radius: 14dp
 *  - Card style: light = elevated (`shadow`), dark = outlined (1dp `outlineVariant`)
 *  - Left icon tile: 40dp, radius 11dp, background `primaryContainer`, icon [Icons.Outlined.Today] tint `primary`
 *  - Center: title (15.5sp / 600 / `onSurface`) + subtitle (13sp / `onSurfaceVariant`)
 *  - Right: [Icons.AutoMirrored.Filled.KeyboardArrowRight] tint `onSurfaceVariant` 75% alpha ("faint")
 *
 * Token mapping:
 * - Container: `colorScheme.surfaceContainerLowest`
 * - Border (dark): `colorScheme.outlineVariant`
 * - Icon tile: `colorScheme.primaryContainer` / `colorScheme.primary`
 * - Title: `colorScheme.onSurface`
 * - Subtitle: `colorScheme.onSurfaceVariant`
 * - Chevron: `colorScheme.onSurfaceVariant` at 0.75f alpha
 *
 * @param title Primary label (e.g. "Today").
 * @param subtitle Secondary label (e.g. "6 to do · stay on track").
 * @param onClick Called when the card is tapped.
 */
@Composable
fun TodaySummaryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(14.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingLg, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon tile
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(AppDimens.IconSizeMd),
                )
            }

            // Title + subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            // Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (isDark) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        ) { cardContent() }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation),
        ) { cardContent() }
    }
}
