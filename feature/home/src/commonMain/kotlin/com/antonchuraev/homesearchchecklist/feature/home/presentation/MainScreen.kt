package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
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
        title = stringResource(Res.string.main_title),
        bottomBar = {
            if (screenState is MainScreenState.Success) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.ScreenPaddingHorizontal)
                        .padding(bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
                ) {
                    AppButton(
                        text = stringResource(Res.string.main_create_checklist),
                        onClick = { viewModel.sendIntent(MainScreenIntent.OnAddChecklistClick) },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppButtonSecondary(
                        text = stringResource(Res.string.main_ai_analysis),
                        onClick = { viewModel.sendIntent(MainScreenIntent.OnAiAnalyzeClick) },
                        icon = Icons.Outlined.AutoAwesome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        if (screenState is MainScreenState.Success) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainScreenContent(
                    screenState = screenState as MainScreenState.Success,
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
        }
    }
}

