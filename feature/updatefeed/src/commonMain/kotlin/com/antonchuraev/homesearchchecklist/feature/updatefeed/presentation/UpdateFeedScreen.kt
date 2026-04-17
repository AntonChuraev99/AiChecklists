package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.UpdatePostCard
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.update_feed_empty_description
import aichecklists.core.designsystem.generated.resources.update_feed_empty_title
import aichecklists.core.designsystem.generated.resources.update_feed_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpdateFeedScreen(
    viewModel: UpdateFeedViewModel = koinViewModel()
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("update_feed") }

    val state by viewModel.screenState.collectAsStateWithLifecycle()

    AppScaffold(
        title = stringResource(Res.string.update_feed_title),
        onBackButtonClick = { viewModel.sendIntent(UpdateFeedScreenIntent.OnBackClick) }
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
                    items(
                        items = currentState.posts,
                        key = { it.id }
                    ) { post ->
                        UpdatePostCard(
                            post = post,
                            onActionClick = { action ->
                                viewModel.sendIntent(
                                    UpdateFeedScreenIntent.OnActionClick(
                                        postId = post.id,
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
