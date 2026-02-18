package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DebugScreen(
    viewModel: DebugViewModel = koinViewModel(),
    onShowCsat: () -> Unit = {},
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    val items = listOf(
        DebugItem(Icons.Default.Info, stringResource(Res.string.debug_app_info), stringResource(Res.string.debug_app_info_description)) {
            viewModel.sendIntent(DebugScreenIntent.ShowInfoDialog)
        },
        DebugItem(Icons.Default.Refresh, stringResource(Res.string.debug_reset_onboarding), stringResource(Res.string.debug_reset_onboarding_description)) {
            viewModel.sendIntent(DebugScreenIntent.ResetOnboarding)
        },
        DebugItem(Icons.Default.Delete, stringResource(Res.string.debug_clear_data), stringResource(Res.string.debug_clear_data_description)) {
            viewModel.sendIntent(DebugScreenIntent.ClearData)
        },
        DebugItem(Icons.Default.Add, stringResource(Res.string.debug_create_test), stringResource(Res.string.debug_create_test_description)) {
            viewModel.sendIntent(DebugScreenIntent.CreateTestChecklists)
        },
        DebugItem(Icons.Outlined.Screenshot, "Store Screenshots", "Preview screens for App Store/Play Store") {
            viewModel.sendIntent(DebugScreenIntent.OpenStoreScreenshot)
        },
        DebugItem(
            Icons.Default.Star,
            stringResource(Res.string.debug_test_restore_credits),
            stringResource(Res.string.debug_test_restore_credits_description)
        ) {
            viewModel.sendIntent(DebugScreenIntent.TestRestoreCredits)
        },
        DebugItem(
            Icons.Default.ThumbUp,
            "Show CSAT Survey",
            "Force show CSAT bottom sheet for testing"
        ) {
            onShowCsat()
        }
    )

    AppScaffold(
        title = stringResource(Res.string.debug_title),
        onBackButtonClick = { viewModel.sendIntent(DebugScreenIntent.OnBackClick) }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(AppDimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            item {
                Text(
                    text = stringResource(Res.string.debug_developer_tools),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = AppDimens.SpacingSm)
                )
            }

            items(items) { item ->
                DebugMenuItem(
                    icon = item.icon,
                    title = item.title,
                    description = item.description,
                    onClick = item.onClick
                )
            }
        }

        if (screenState.showInfoDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.sendIntent(DebugScreenIntent.HideInfoDialog) },
                title = {
                    Text(
                        text = stringResource(Res.string.debug_app_info_dialog_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column {
                        InfoRow(stringResource(Res.string.debug_label_name), stringResource(Res.string.app_name))
                        InfoRow(stringResource(Res.string.debug_label_version), "1.0.0")
                        InfoRow(stringResource(Res.string.debug_label_build), "Debug")
                    }
                },
                confirmButton = {
                    AppButtonText(
                        text = stringResource(Res.string.ok),
                        onClick = { viewModel.sendIntent(DebugScreenIntent.HideInfoDialog) }
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            )
        }

        screenState.restoreCreditsResult?.let { result ->
            AlertDialog(
                onDismissRequest = { viewModel.sendIntent(DebugScreenIntent.DismissRestoreCreditsResult) },
                title = {
                    Text(
                        text = stringResource(Res.string.debug_test_restore_credits),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Text(
                        text = when (result) {
                            is RestoreCreditsResult.Success -> stringResource(
                                Res.string.debug_restore_credits_success,
                                result.credits
                            )
                            is RestoreCreditsResult.Error -> stringResource(
                                Res.string.debug_restore_credits_error
                            ) + ": ${result.message}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (result) {
                            is RestoreCreditsResult.Success -> MaterialTheme.colorScheme.onSurface
                            is RestoreCreditsResult.Error -> MaterialTheme.colorScheme.error
                        }
                    )
                },
                confirmButton = {
                    AppButtonText(
                        text = stringResource(Res.string.ok),
                        onClick = { viewModel.sendIntent(DebugScreenIntent.DismissRestoreCreditsResult) }
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            )
        }
    }
}

private data class DebugItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
private fun DebugMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingXs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

