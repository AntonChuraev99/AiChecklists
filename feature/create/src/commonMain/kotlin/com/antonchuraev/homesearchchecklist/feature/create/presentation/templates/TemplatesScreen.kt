package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import homesearchchecklist.core.designsystem.generated.resources.Res
import homesearchchecklist.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = koinViewModel()
) {
    AppScaffold(
        title = stringResource(Res.string.templates_title),
        onBackButtonClick = { viewModel.sendIntent(TemplatesScreenIntent.OnBackClick) }
    ) {
        EmptyState(
            icon = Icons.Outlined.Description,
            title = stringResource(Res.string.templates_empty_title),
            description = stringResource(Res.string.templates_empty_description)
        )
    }
}

