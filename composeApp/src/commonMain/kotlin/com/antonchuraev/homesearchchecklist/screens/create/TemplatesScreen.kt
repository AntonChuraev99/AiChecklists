package com.antonchuraev.homesearchchecklist.screens.create

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold

@Composable
fun TemplatesScreen(
    onBackButtonClick: () -> Unit
){
    AppScaffold(
        title = "Шаблоны",
        onBackButtonClick = onBackButtonClick
    ){
        Text(
            "TODO"
        )
    }
}