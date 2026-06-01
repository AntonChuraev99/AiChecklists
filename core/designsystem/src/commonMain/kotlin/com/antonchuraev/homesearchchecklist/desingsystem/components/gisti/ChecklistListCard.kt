package com.antonchuraev.homesearchchecklist.desingsystem.components.gisti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

/**
 * Rich list-card for a checklist entry on the Home screen.
 *
 * The card is **stateless and not clickable internally** — the caller applies click via
 * [modifier] (e.g. `Modifier.clickable { ... }` or wrapping in a clickable parent).
 * This follows the project pattern: whole-card click at the call site, no hit-zone split.
 *
 * Structure (from gisti-screens.jsx ListCard / RxHome):
 * ```
 * Row (padding 13dp vertical, 14dp horizontal)
 *   GistiAvatarTile (48dp)
 *   Column (weight 1f, gap 6dp)
 *     Row: name (titleMedium / 16.5sp / 600) + "🎉" if 100%
 *     if totalItems > 0:
 *       Row: progress bar (weight 1f, 5dp h) + "N of M done" label
 *     else:
 *       Text("No items yet") in bodySmall / onSurfaceVariant
 *     if editedLabel != null:
 *       Text(editedLabel, 11.5sp, faint = onSurfaceVariant 75% alpha)
 * ```
 *
 * Card style matches the existing [AppCard] pattern:
 * - **Light**: elevated [Card] with [AppDimens.CardElevation]
 * - **Dark**: [OutlinedCard] with 1dp `outline` border
 * - Corner radius: `MaterialTheme.shapes.medium` (12dp per AppShapes)
 *
 * Token mapping:
 * - Container: `colorScheme.surfaceContainerLowest` (white card)
 * - Name: `colorScheme.onSurface` / `typography.titleMedium`
 * - Progress accent: [GistiColors.success] at 100%, else `colorScheme.primary`
 * - Progress track: `colorScheme.surfaceContainerHigh`
 * - Done label: [GistiColors.success] at 100%, else `colorScheme.onSurfaceVariant`
 * - Edit label: `colorScheme.onSurfaceVariant` at 75% alpha ("faint")
 *
 * @param name Checklist display name.
 * @param checkedItems Number of completed items.
 * @param totalItems Total item count. 0 = "No items yet" placeholder.
 * @param seed Stable id used for [GistiAvatarTile] color derivation.
 * @param editedLabel Optional relative time label (e.g. "edited 2h ago"). Null hides the row.
 */
@Composable
fun ChecklistListCard(
    name: String,
    checkedItems: Int,
    totalItems: Int,
    seed: Long,
    modifier: Modifier = Modifier,
    editedLabel: String? = null,
) {
    val isDark = LocalIsDarkTheme.current
    val shape = MaterialTheme.shapes.medium
    val isComplete = totalItems > 0 && checkedItems >= totalItems
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GistiAvatarTile(
                seed = seed,
                label = name,
                size = 48.dp,
                cornerRadius = 14.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Name row + optional 🎉 on completion
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isComplete) {
                        Text(
                            text = "🎉",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        )
                    }
                }

                // Progress row or empty placeholder
                if (totalItems > 0) {
                    ProgressRow(
                        checked = checkedItems,
                        total = totalItems,
                        isComplete = isComplete,
                    )
                } else {
                    Text(
                        text = "No items yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Edited label (faint)
                if (editedLabel != null) {
                    Text(
                        text = editedLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }

    if (isDark) {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        ) { cardContent() }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation),
        ) { cardContent() }
    }
}

@Composable
private fun ProgressRow(
    checked: Int,
    total: Int,
    isComplete: Boolean,
) {
    val successColor = GistiColors.success
    val progressColor = if (isComplete) successColor else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = if (isComplete) successColor else MaterialTheme.colorScheme.onSurfaceVariant
    val fraction = if (total > 0) (checked.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val pillShape = RoundedCornerShape(50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Progress bar: track Box + filled Box overlay using fractional fillMaxWidth
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(pillShape)
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .background(progressColor),
            )
        }

        // "N of M done" label
        Text(
            text = "$checked of $total done",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = labelColor,
        )
    }
}
