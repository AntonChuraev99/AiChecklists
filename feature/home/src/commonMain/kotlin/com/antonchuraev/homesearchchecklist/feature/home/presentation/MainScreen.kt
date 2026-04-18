package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    onRateAppClick: () -> Unit = {},
    onLeaveFeedbackClick: () -> Unit = {},
    versionName: String = "",
    viewModel: MainScreenViewModel = koinViewModel(),
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("main") }

    val uriHandler = LocalUriHandler.current
    val screenState: MainScreenState by viewModel.screenState.collectAsStateWithLifecycle()
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    // Use plain `remember` instead of `rememberDrawerState` to avoid the Saver
    // restoring an Open state after back navigation. rememberDrawerState wraps
    // DrawerState in rememberSaveable; the Saver applies AFTER LaunchedEffect runs,
    // winning the race and rendering Main under scrim (visually: "white screen"
    // after returning from UpdateFeed). A fresh Closed state on every composition
    // is correct UX — drawer should never auto-open on navigation return.
    val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
    val scope = rememberCoroutineScope()

    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.onSurface,
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isEditMode,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    // ── Brand header ──────────────────────────────────────
                    DrawerBrandHeader()

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = AppDimens.SpacingLg)
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

                    // ── Section: What's New ───────────────────────────────
                    DrawerSectionLabel(stringResource(Res.string.drawer_section_whats_new))
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.update_feed_menu_item)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Campaign,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                viewModel.sendIntent(MainScreenIntent.OnUpdateFeedClick)
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    // ── Section: Help & Feedback ──────────────────────────
                    Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                    DrawerSectionLabel(stringResource(Res.string.drawer_section_help))
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.main_menu_rate_app)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onRateAppClick()
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.main_menu_leave_feedback)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Feedback,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLeaveFeedbackClick()
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.main_menu_support)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.MailOutline,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            uriHandler.openUri("mailto:${PaywallConfig.SUPPORT_EMAIL}")
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    // ── Section: About ────────────────────────────────────
                    Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                    DrawerSectionLabel(stringResource(Res.string.drawer_section_about))
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.paywall_privacy)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL)
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(Res.string.paywall_terms)) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Article,
                                contentDescription = null
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL)
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    // ── Footer (version) ──────────────────────────────────
                    Spacer(modifier = Modifier.weight(1f))
                    DrawerFooter(versionName)
                }
            }
        }
    ) {
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
                // Edit mode: show "Done" button
                TextButton(onClick = { isEditMode = false }) {
                    Text(
                        text = stringResource(Res.string.done),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Normal mode: credits only (Support moved to navigation drawer)
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
            // Hide bottom bar in edit mode
            if (!isEditMode && screenState is MainScreenState.Success) {
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
                        onEnterEditMode = { isEditMode = true },
                        onExitEditMode = { isEditMode = false },
                        onReorderChecklists = { orderedIds ->
                            viewModel.sendIntent(MainScreenIntent.OnReorderChecklists(orderedIds))
                        }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DrawerBrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.SpacingLg,
                vertical = AppDimens.SpacingLg
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = stringResource(Res.string.drawer_logo_content_description),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(
                text = "Gisti",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.drawer_tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = AppDimens.SpacingLg,
            end = AppDimens.SpacingLg,
            top = AppDimens.SpacingMd,
            bottom = AppDimens.SpacingSm
        )
    )
}

@Composable
private fun DrawerFooter(versionName: String) {
    if (versionName.isNotBlank()) {
        Text(
            text = stringResource(Res.string.drawer_version_label, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd)
                .navigationBarsPadding()
        )
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
