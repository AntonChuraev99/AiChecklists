package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_agent_plan_apply
import aichecklists.core.designsystem.generated.resources.chat_agent_plan_header
import aichecklists.core.designsystem.generated.resources.chat_preview_cancel
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.AgentPlan
import org.jetbrains.compose.resources.stringResource

/**
 * Plan-card for the agentic loop (Phase 2d).
 *
 * Shown when the agent returns one or more mutating tool calls that need user confirmation
 * before execution. Mirrors [ChatPreviewCard]'s M3 styling verbatim (same [AppCard],
 * same paddings, same button row) — no novel design decisions made here.
 *
 * @param plan      The batch of proposed actions to display.
 * @param onApply   Called when the user taps "Apply all" — resumes the agent loop and dispatches
 *                  the mutating calls.
 * @param onCancel  Called when the user taps "Cancel" — sends declined results and continues
 *                  the loop so the agent can respond to the cancellation.
 * @param modifier  Optional modifier.
 */
@Composable
fun AgentPlanCard(
    plan: AgentPlan,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            // Header row: icon + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistAddCheck,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.chat_agent_plan_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Numbered list of proposed actions.
            // Destructive items (delete_item) get an error-tinted warning icon to
            // visually separate them from additive/benign actions.
            plan.items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.isDestructive) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimens.IconSizeSm),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Primary action row: Cancel (text, left) | Apply all (filled, right).
            // Mirrors ChatPreviewCard's button layout verbatim.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButtonText(
                    text = stringResource(Res.string.chat_preview_cancel),
                    onClick = onCancel,
                )
                AppButton(
                    text = stringResource(Res.string.chat_agent_plan_apply),
                    onClick = onApply,
                )
            }
        }
    }
}
