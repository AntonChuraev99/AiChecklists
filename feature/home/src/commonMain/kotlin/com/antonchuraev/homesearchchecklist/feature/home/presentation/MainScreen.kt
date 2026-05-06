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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.credits_display
import aichecklists.core.designsystem.generated.resources.credits_get_more
import aichecklists.core.designsystem.generated.resources.done
import aichecklists.core.designsystem.generated.resources.main_create_checklist
import aichecklists.core.designsystem.generated.resources.main_create_checklist_locked
import aichecklists.core.designsystem.generated.resources.main_menu
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main home screen. Drawer lives above this composable in App.kt via
 * ModalNavigationDrawer + AppNavigationDrawerContent. This screen only
 * owns the hamburger affordance and forwards open() to the lifted drawerState.
 *
 * `isEditMode` and `onEditModeChange` are also lifted to the caller so the
 * parent ModalNavigationDrawer can disable swipe gestures while the user is
 * dragging checklist items (gesturesEnabled = !isEditMode).
 */
@Composable
fun MainScreen(
    drawerState: DrawerState,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    viewModel: MainScreenViewModel = koinViewModel(),
    /** When true, the bottom "Create Checklist" bar is suppressed.
     *  Set to true when the caller (App.kt) provides a FAB instead. */
    hideBottomBar: Boolean = false,
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("main") }

    val screenState: MainScreenState by viewModel.screenState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = "",
        navigationIcon = if (!isEditMode) {
            {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(Res.string.main_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        actions = {
            if (isEditMode) {
                TextButton(onClick = { onEditModeChange(false) }) {
                    Text(
                        text = stringResource(Res.string.done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                if (screenState is MainScreenState.Success) {
                    val state = screenState as MainScreenState.Success
                    CreditsChip(
                        credits = state.aiCredits,
                        isPremium = state.subscriptionStatus.isActive,
                        onClick = { viewModel.sendIntent(MainScreenIntent.OnCreditsClick) }
                    )
                }
            }
        },
        bottomBar = {
            // When the caller provides a FAB (hideBottomBar = true), suppress this bar
            // to avoid double bottom UI on the Lists tab.
            if (!hideBottomBar && !isEditMode && screenState is MainScreenState.Success) {
                val state = screenState as MainScreenState.Success
                val canCreateChecklist = state.userLimits?.canCreateChecklist ?: true

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPaddingHorizontal)
                        .padding(bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding()
                ) {
                    AppButton(
                        text = stringResource(
                            if (canCreateChecklist) Res.string.main_create_checklist
                            else Res.string.main_create_checklist_locked
                        ),
                        onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
                        icon = if (canCreateChecklist) Icons.Filled.Add else Icons.Outlined.Lock,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        when (val state = screenState) {
            MainScreenState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is MainScreenState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreenContent(
                        screenState = state,
                        isEditMode = isEditMode,
                        onChecklistClick = { checklistWithProgress ->
                            viewModel.sendIntent(MainScreenIntent.OnChecklistClick(checklistWithProgress))
                        },
                        onAddChecklistClick = {
                            viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick)
                        },
                        onAiAnalyzeClick = {
                            viewModel.sendIntent(MainScreenIntent.OnAiAnalyzeClick)
                        },
                        onPremiumBannerClick = {
                            viewModel.sendIntent(MainScreenIntent.OnPremiumBannerClick)
                        },
                        onEnterEditMode = { onEditModeChange(true) },
                        onExitEditMode = { onEditModeChange(false) },
                        onReorderChecklists = { orderedIds ->
                            viewModel.sendIntent(MainScreenIntent.OnReorderChecklists(orderedIds))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditsChip(
    credits: Int,
    isPremium: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            text = if (showGetMore) stringResource(Res.string.credits_get_more)
                   else stringResource(Res.string.credits_display, credits),
            style = MaterialTheme.typography.labelMedium,
            color = if (showGetMore) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
