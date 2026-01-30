package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.home.presentation.components.PremiumBanner
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainScreenContent(
    screenState: MainScreenState.Success,
    onChecklistClick: (ChecklistWithProgress) -> Unit,
    onAddChecklistClick: () -> Unit,
    onAiAnalyzeClick: () -> Unit,
    onPremiumBannerClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingLg,
            bottom = AppDimens.SpacingXxl
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        item(key = "premium_banner") {
            PremiumBanner(
                isActive = screenState.subscriptionStatus.isActive,
                formattedExpirationDate = screenState.formattedExpirationDate,
                onUpgradeClick = onPremiumBannerClick,
                onSubscriptionClick = onPremiumBannerClick
            )
        }

        if (screenState.checklists.isEmpty()) {
            item(key = "empty_state") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Checklist,
                        title = stringResource(Res.string.main_empty_title),
                        description = stringResource(Res.string.main_empty_description)
                    )
                }
            }
        } else {
            items(
                items = screenState.checklists,
                key = { it.checklist.id }
            ) { checklistWithProgress ->
                ChecklistCard(
                    checklistWithProgress = checklistWithProgress,
                    onClick = { onChecklistClick(checklistWithProgress) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun ChecklistCard(
    checklistWithProgress: ChecklistWithProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)
            ) {
                Text(
                    text = checklistWithProgress.checklist.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (checklistWithProgress.totalItems > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
                    ) {
                        LinearProgressIndicator(
                            progress = { checklistWithProgress.progress },
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "${checklistWithProgress.checkedItems}/${checklistWithProgress.totalItems}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.main_no_items),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

