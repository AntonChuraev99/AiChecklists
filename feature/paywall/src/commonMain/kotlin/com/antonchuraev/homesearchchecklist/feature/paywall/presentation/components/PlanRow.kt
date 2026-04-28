package com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * PlanRow — selectable subscription plan card with radio + optional badge.
 *
 * A11y: exposes Role.RadioButton + selected state via semantics so TalkBack
 * announces the element correctly.
 */
@Composable
internal fun PlanRow(
    label: String,
    price: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    savings: String? = null,
) {
    val cs = MaterialTheme.colorScheme

    val bg     = if (selected) cs.primaryContainer            else cs.surface
    val border = if (selected) BorderStroke(2.dp, cs.primary) else BorderStroke(1.dp, cs.outlineVariant)
    val onBg   = if (selected) cs.onPrimaryContainer          else cs.onSurface
    val onSub  = if (selected) cs.onPrimaryContainer          else cs.onSurfaceVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick),
        shape  = MaterialTheme.shapes.large,
        color  = bg,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingLg),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Radio dot
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) cs.primary else cs.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = onBg)
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = cs.primary,
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onPrimary,
                                modifier = Modifier.padding(
                                    horizontal = AppDimens.SpacingSm,
                                    vertical = AppDimens.SpacingXxs,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(sub, style = MaterialTheme.typography.bodySmall, color = onSub)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.titleMedium, color = onBg)
                if (savings != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        savings,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.tertiary,
                    )
                }
            }
        }
    }
}
