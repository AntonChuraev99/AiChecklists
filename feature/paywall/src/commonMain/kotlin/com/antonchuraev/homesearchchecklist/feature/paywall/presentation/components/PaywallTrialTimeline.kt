package com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_step1_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_step1_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_step3_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_step3_title
import org.jetbrains.compose.resources.stringResource

/**
 * Three-step vertical timeline used in PaywallVariant.Timeline.
 *
 * The design system already exposes a `TrialTimeline` for the simple
 * Today → DueDate variant; this one is the richer Today / Day 5 / Day 7
 * step list specific to the paywall hero. Lives in the feature module to
 * avoid widening the DS surface.
 */
@Composable
internal fun PaywallTrialTimeline(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme

    data class Step(val icon: ImageVector, val title: String, val body: String, val tertiary: Boolean)

    // Two-step timeline: Today → trial-end. Removed the Day-2 "we'll notify you" step
    // because we don't actually send pre-trial-end push notifications — keeping it would
    // be a deceptive promise (Google Play "Deceptive behavior" policy risk).
    val steps = listOf(
        Step(
            icon = Icons.Filled.LockOpen,
            title = stringResource(Res.string.paywall_v1_timeline_step1_title),
            body = stringResource(Res.string.paywall_v1_timeline_step1_body),
            tertiary = false,
        ),
        Step(
            icon = Icons.Filled.WorkspacePremium,
            title = stringResource(Res.string.paywall_v1_timeline_step3_title),
            body = stringResource(Res.string.paywall_v1_timeline_step3_body),
            tertiary = false,
        ),
    )

    Box(modifier = modifier) {
        // Connecting line behind the column
        Box(
            modifier = Modifier
                .padding(start = 19.dp, top = 20.dp, bottom = 20.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(listOf(cs.primary, cs.primary, cs.outlineVariant))
                )
        )

        Column {
            steps.forEachIndexed { i, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(bottom = if (i == steps.lastIndex) 0.dp else 20.dp),
                ) {
                    val dotBg   = if (step.tertiary) cs.tertiaryContainer else cs.primaryContainer
                    val dotTint = if (step.tertiary) cs.tertiary          else cs.onPrimaryContainer
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(dotBg)
                            .border(2.dp, cs.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(step.icon, null, tint = dotTint, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(top = 6.dp)) {
                        Text(step.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(step.body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
