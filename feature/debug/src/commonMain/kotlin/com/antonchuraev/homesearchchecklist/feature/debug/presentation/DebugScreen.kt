package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.DockDesignDebug
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.CancelReason
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.CancelReasonStage
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PostCancelReasonSheet
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = koinViewModel(),
    onShowCsat: () -> Unit = {},
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    var showRepeatRulePreview by rememberSaveable { mutableStateOf(false) }

    val items = listOf(
        DebugItem(Icons.Default.GridView, stringResource(Res.string.debug_catalog_title), stringResource(Res.string.debug_catalog_entry_description)) {
            viewModel.sendIntent(DebugScreenIntent.OpenScreenCatalog)
        },
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
        DebugItem(Icons.Outlined.Screenshot, stringResource(Res.string.debug_store_screenshots), stringResource(Res.string.debug_store_screenshots_description)) {
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
            Icons.Default.Notifications,
            stringResource(Res.string.debug_repeat_rule_presets),
            stringResource(Res.string.debug_repeat_rule_presets_description)
        ) {
            showRepeatRulePreview = true
        },
        DebugItem(
            Icons.Default.ThumbUp,
            stringResource(Res.string.debug_show_csat),
            stringResource(Res.string.debug_show_csat_description)
        ) {
            onShowCsat()
        },
        DebugItem(
            Icons.Outlined.CheckCircle,
            stringResource(Res.string.debug_interactive_onboarding),
            stringResource(Res.string.debug_interactive_onboarding_description)
        ) {
            viewModel.sendIntent(DebugScreenIntent.OpenInteractiveOnboarding)
        },
        DebugItem(
            Icons.Default.PlayCircleFilled,
            stringResource(Res.string.debug_onboardings_title),
            stringResource(Res.string.debug_onboardings_description)
        ) {
            viewModel.sendIntent(DebugScreenIntent.OpenOnboardings)
        },
        DebugItem(
            Icons.Default.Cancel,
            stringResource(Res.string.debug_cancel_reason_title),
            stringResource(Res.string.debug_cancel_reason_description)
        ) {
            viewModel.sendIntent(DebugScreenIntent.ShowCancelReasonSheet)
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

            item {
                DebugToggleItem(
                    icon = Icons.Default.Brush,
                    title = stringResource(Res.string.debug_dock_legacy_title),
                    description = stringResource(Res.string.debug_dock_legacy_description),
                    checked = DockDesignDebug.useLegacyDock,
                    onCheckedChange = { DockDesignDebug.useLegacyDock = it }
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

        if (showRepeatRulePreview) {
            RepeatPresetPreviewSheet(onDismiss = { showRepeatRulePreview = false })
        }

        // Debug preview of the paywall post-cancel reason sheet. Bypasses the session cap (always
        // shows). Stage is driven locally here — `remember` re-initializes to Asking each time the
        // sheet re-enters composition, so reopening always starts fresh.
        if (screenState.showCancelReasonSheet) {
            var debugStage by remember { mutableStateOf(CancelReasonStage.Asking) }
            PostCancelReasonSheet(
                stage = debugStage,
                onSelectReason = { reason ->
                    if (reason == CancelReason.PAYMENT_ISSUE) {
                        viewModel.sendIntent(DebugScreenIntent.HideCancelReasonSheet)
                    } else {
                        debugStage = CancelReasonStage.Thanks
                    }
                },
                onDismiss = { viewModel.sendIntent(DebugScreenIntent.HideCancelReasonSheet) },
            )
            LaunchedEffect(debugStage) {
                if (debugStage == CancelReasonStage.Thanks) {
                    delay(1000)
                    viewModel.sendIntent(DebugScreenIntent.HideCancelReasonSheet)
                }
            }
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
private fun DebugToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppCard(onClick = { onCheckedChange(!checked) }) {
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

            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatPresetPreviewSheet(onDismiss: () -> Unit) {
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var customExpanded by rememberSaveable { mutableStateOf(false) }

    val presets = listOf(
        stringResource(Res.string.reminder_repeat_daily) to "DAILY, interval=1",
        stringResource(Res.string.reminder_repeat_weekdays) to "WEEKLY, interval=1, weekDays={1,2,3,4,5}",
        stringResource(Res.string.reminder_repeat_weekly) to "WEEKLY, interval=1",
        stringResource(Res.string.reminder_repeat_biweekly) to "WEEKLY, interval=2",
        stringResource(Res.string.reminder_repeat_monthly) to "MONTHLY, interval=1",
        stringResource(Res.string.reminder_repeat_quarterly) to "MONTHLY, interval=3",
        stringResource(Res.string.reminder_repeat_yearly) to "YEARLY, interval=1",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            Text(
                text = stringResource(Res.string.reminder_repeat_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
            )

            presets.forEachIndexed { index, (label, config) ->
                val selected = selectedIndex == index && !customExpanded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            selectedIndex = index
                            customExpanded = false
                        }
                        .padding(vertical = AppDimens.SpacingSm, horizontal = AppDimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                        )
                        if (selected) {
                            Text(
                                text = config,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Custom option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { customExpanded = !customExpanded }
                    .padding(vertical = AppDimens.SpacingSm, horizontal = AppDimens.SpacingXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
            ) {
                Icon(
                    imageVector = if (customExpanded) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (customExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(Res.string.reminder_repeat_custom),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (customExpanded) FontWeight.Medium else FontWeight.Normal
                )
            }

            // Custom section preview
            AnimatedVisibility(visible = customExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(AppDimens.SpacingLg),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
                    ) {
                        // Type chips row
                        Text(
                            text = "Every [N] [type]",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                            listOf("days", "weeks", "months", "years").forEachIndexed { i, label ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (i == 0) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (i == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(
                                            horizontal = AppDimens.SpacingMd,
                                            vertical = AppDimens.SpacingXs
                                        )
                                    )
                                }
                            }
                        }

                        // Weekday circles preview
                        Text(
                            text = "Weekday chips (WEEKLY type only):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, label ->
                                val isSelected = i < 5 // Mon-Fri selected
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            Text(
                text = "This is a debug preview. Tap presets to see their config.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

