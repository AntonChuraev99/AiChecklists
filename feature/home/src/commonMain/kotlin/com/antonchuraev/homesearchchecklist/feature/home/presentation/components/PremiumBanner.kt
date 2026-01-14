package com.antonchuraev.homesearchchecklist.feature.home.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.premium_banner_active_title
import aichecklists.core.designsystem.generated.resources.premium_banner_description
import aichecklists.core.designsystem.generated.resources.premium_banner_title
import aichecklists.core.designsystem.generated.resources.premium_banner_upgrade
import aichecklists.core.designsystem.generated.resources.premium_banner_valid_until
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

// Premium gradient colors
private val PremiumGradientStart = Color(0xFF667EEA)
private val PremiumGradientEnd = Color(0xFF764BA2)

@Composable
fun PremiumBanner(
    isActive: Boolean,
    formattedExpirationDate: String?,
    onUpgradeClick: () -> Unit,
    onSubscriptionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isActive) {
        ActivePremiumBanner(
            formattedExpirationDate = formattedExpirationDate,
            onClick = onSubscriptionClick,
            modifier = modifier
        )
    } else {
        InactivePremiumBanner(
            onClick = onUpgradeClick,
            modifier = modifier
        )
    }
}

@Composable
private fun InactivePremiumBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(PremiumGradientStart, PremiumGradientEnd)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(gradientBrush)
            .clickable(onClick = onClick)
            .padding(AppDimens.SpacingLg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
                ) {
                    Text(
                        text = stringResource(Res.string.premium_banner_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(Res.string.premium_banner_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
            ) {
                Text(
                    text = stringResource(Res.string.premium_banner_upgrade),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ActivePremiumBanner(
    formattedExpirationDate: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(AppDimens.SpacingLg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
                ) {
                    Text(
                        text = stringResource(Res.string.premium_banner_active_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (formattedExpirationDate != null) {
                        Text(
                            text = stringResource(Res.string.premium_banner_valid_until, formattedExpirationDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
