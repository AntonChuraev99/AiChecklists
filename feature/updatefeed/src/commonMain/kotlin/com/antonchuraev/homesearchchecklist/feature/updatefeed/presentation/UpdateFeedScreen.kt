package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.components.PremiumBanner
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.ReleaseCard
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.main_menu
import aichecklists.core.designsystem.generated.resources.update_feed_empty_description
import aichecklists.core.designsystem.generated.resources.update_feed_empty_title
import aichecklists.core.designsystem.generated.resources.update_feed_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Update Feed screen. Reachable from the app drawer (item "Updates").
 * When `drawerState` is provided, a hamburger affordance replaces the
 * back-arrow in the TopAppBar — satisfies MD3 "Drawer Affordance Scope"
 * rule (every in-app destination reachable from a drawer must expose
 * the drawer). When called without a drawer (e.g. deep link), falls back
 * to the standard back-arrow.
 */
@Composable
fun UpdateFeedScreen(
    onBackClick: () -> Unit = {},
    drawerState: DrawerState? = null,
) {
    UpdateFeedScreenContent(onBackClick = onBackClick, drawerState = drawerState)
}

@Composable
private fun UpdateFeedScreenContent(
    onBackClick: () -> Unit,
    drawerState: DrawerState?,
    viewModel: UpdateFeedViewModel = koinViewModel(),
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("update_feed") }

    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Double-back guard: rapid double-tap на back иногда выполняет popBackStack
    // дважды — второй pop выскакивает за Main в destination, которого уже нет
    // в стеке (визуально "обход стека"). Флаг сворачивает серию тапов в одну
    // навигацию. При повторном заходе на экран composable создаётся заново —
    // новый remember даёт чистый consumed=false.
    var consumed by remember { mutableStateOf(false) }

    AppScaffold(
        title = stringResource(Res.string.update_feed_title),
        navigationIcon = if (drawerState != null) {
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
        onBackButtonClick = if (drawerState == null) {
            {
                if (!consumed) {
                    consumed = true
                    onBackClick()
                }
            }
        } else null
    ) {
        when (val currentState = state) {
            UpdateFeedScreenState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            UpdateFeedScreenState.Empty -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = stringResource(Res.string.update_feed_empty_title),
                    description = stringResource(Res.string.update_feed_empty_description)
                )
            }

            is UpdateFeedScreenState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                    contentPadding = PaddingValues(
                        horizontal = AppDimens.ScreenPaddingHorizontal,
                        vertical = AppDimens.SpacingLg
                    )
                ) {
                    item(key = "premium_banner") {
                        PremiumBanner(
                            isActive = currentState.isPremium,
                            formattedExpirationDate = currentState.formattedExpirationDate,
                            onUpgradeClick = {
                                viewModel.sendIntent(UpdateFeedScreenIntent.OnPremiumBannerClick)
                            },
                            onSubscriptionClick = {
                                viewModel.sendIntent(UpdateFeedScreenIntent.OnPremiumBannerClick)
                            }
                        )
                    }
                    items(
                        items = currentState.releases,
                        key = { it.version }
                    ) { release ->
                        ReleaseCard(
                            release = release,
                            onActionClick = { postId, action ->
                                viewModel.sendIntent(
                                    UpdateFeedScreenIntent.OnActionClick(
                                        postId = postId,
                                        action = action
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
