package com.antonchuraev.homesearchchecklist.desingsystem.sharedUI

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_timeline_due
import aichecklists.core.designsystem.generated.resources.paywall_timeline_free
import aichecklists.core.designsystem.generated.resources.paywall_timeline_today
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.getTrialEndDateFormatted
import org.jetbrains.compose.resources.stringResource

/**
 * Formats zero price in the same currency as the given price string.
 * Examples: "$1.99" → "$0.00", "€1,99" → "€0,00", "199 ₽" → "0 ₽"
 */
fun formatZeroPrice(priceString: String): String {
    // Find currency symbol (non-digit, non-separator characters)
    val currencySymbol = priceString.filter { !it.isDigit() && it != '.' && it != ',' && !it.isWhitespace() }

    // Determine decimal separator (comma for EU, dot for US)
    val hasComma = priceString.contains(',')
    val zeroAmount = if (hasComma) "0,00" else "0.00"

    // Check if currency symbol is at the end (e.g., "1,99 €")
    val trimmed = priceString.trim()
    return if (trimmed.lastOrNull()?.isDigit() == false && currencySymbol.isNotEmpty()) {
        // Currency at end: "0,00 €"
        "$zeroAmount $currencySymbol"
    } else {
        // Currency at start: "$0.00"
        "$currencySymbol$zeroAmount"
    }
}

@Composable
fun TrialTimeline(
    trialDays: Int,
    priceString: String,
    primaryColor: Color,
    modifier: Modifier = Modifier.Companion
) {
    // Google Play counts today as day 1 of the trial, so billing starts on today + (trialDays - 1)
    val trialEndDate = remember(trialDays) { getTrialEndDateFormatted(trialDays - 1) }
    val zeroPriceFormatted = remember(priceString) { formatZeroPrice(priceString) }
    val lineColor = primaryColor.copy(alpha = 0.3f)
    val textSecondary = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        // Timeline with vertical line and dots
        Box(
            modifier = Modifier.Companion
                .width(24.dp)
                .height(52.dp)
        ) {
            Canvas(modifier = Modifier.Companion.matchParentSize()) {
                val dotRadius = 5.dp.toPx()
                val lineWidth = 2.dp.toPx()
                val centerX = size.width / 2

                // Vertical line
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, dotRadius),
                    end = Offset(centerX, size.height - dotRadius),
                    strokeWidth = lineWidth
                )

                // Top dot (Today)
                drawCircle(
                    color = primaryColor,
                    radius = dotRadius,
                    center = Offset(centerX, dotRadius)
                )

                // Bottom dot (Due date)
                drawCircle(
                    color = lineColor,
                    radius = dotRadius,
                    center = Offset(centerX, size.height - dotRadius)
                )
            }
        }

        // Text content
        Column(
            modifier = Modifier.Companion.weight(1f)
        ) {
            // Today row
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.paywall_timeline_today),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Companion.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row {
                    Text(
                        text = stringResource(Res.string.paywall_timeline_free),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Companion.SemiBold,
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.Companion.width(8.dp))
                    Text(
                        text = zeroPriceFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.Companion.height(12.dp))

            // Due date row
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.paywall_timeline_due, trialEndDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
                Text(
                    text = priceString,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
            }
        }
    }
}