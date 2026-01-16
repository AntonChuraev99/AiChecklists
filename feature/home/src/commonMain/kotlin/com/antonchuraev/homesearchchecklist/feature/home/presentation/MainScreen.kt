package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = koinViewModel(),
) {
    val screenState: MainScreenState by viewModel.screenState.collectAsStateWithLifecycle()

    AppScaffold(
        title = "",
        actions = {
            if (screenState is MainScreenState.Success) {
                val state = screenState as MainScreenState.Success
                CreditsChip(
                    credits = state.aiCredits,
                    isPremium = state.subscriptionStatus.isActive,
                    onClick = { viewModel.sendIntent(MainScreenIntent.OnCreditsClick) }
                )
            }
        },
        bottomBar = {
            if (screenState is MainScreenState.Success) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPaddingHorizontal)
                        .padding(bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding()
                ) {
                    AppButton(
                        text = stringResource(Res.string.main_create_checklist),
                        onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        if (screenState is MainScreenState.Success) {
            val state = screenState as MainScreenState.Success
            Box(modifier = Modifier.fillMaxSize()) {
                MainScreenContent(
                    screenState = state,
                    onChecklistClick = { checklist ->
                        viewModel.sendIntent(MainScreenIntent.OnChecklistClick(checklist))
                    },
                    onAddChecklistClick = {
                        viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick)
                    },
                    onAiAnalyzeClick = {
                        viewModel.sendIntent(MainScreenIntent.OnAiAnalyzeClick)
                    },
                    onPremiumBannerClick = {
                        viewModel.sendIntent(MainScreenIntent.OnPremiumBannerClick)
                    }
                )
            }

            if (state.showLimitReachedDialog && state.userLimits != null) {
                LimitReachedDialog(
                    maxChecklists = state.userLimits.maxChecklists,
                    onDismiss = { viewModel.sendIntent(MainScreenIntent.OnDismissLimitDialog) },
                    onUpgrade = { viewModel.sendIntent(MainScreenIntent.OnUpgradeToPremiumClick) }
                )
            }
        }
    }
}

@Composable
private fun LimitReachedDialog(
    maxChecklists: Int,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.limit_reached_title))
        },
        text = {
            Text(stringResource(Res.string.limit_reached_message, maxChecklists))
        },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text(stringResource(Res.string.limit_upgrade))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun CreditsChip(
    credits: Int,
    isPremium: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // All users (including premium) now see their actual credits
    val displayText = stringResource(Res.string.credits_display, credits)

    // Show "Get More" when credits = 0
    // For premium users, clicking opens subscription status (shows refill info)
    // For non-premium users, clicking opens paywall
    val showGetMore = credits == 0

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (showGetMore) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (showGetMore) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (showGetMore) stringResource(Res.string.credits_get_more) else displayText,
            style = MaterialTheme.typography.labelMedium,
            color = if (showGetMore) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
