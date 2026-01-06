package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = koinViewModel()
) {
    AppScaffold(
        title = "Шаблоны",
        onBackButtonClick = { viewModel.sendIntent(TemplatesScreenIntent.OnBackClick) }
    ) {
        Text("TODO")
    }
}

