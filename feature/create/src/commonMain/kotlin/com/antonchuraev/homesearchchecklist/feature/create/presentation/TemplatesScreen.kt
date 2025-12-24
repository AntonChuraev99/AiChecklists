package com.antonchuraev.homesearchchecklist.feature.create.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold

@Composable
fun TemplatesScreen(
    onBackButtonClick: () -> Unit
) {
    AppScaffold(
        title = "Шаблоны",
        onBackButtonClick = onBackButtonClick
    ) {
        Text("TODO")
    }
}

