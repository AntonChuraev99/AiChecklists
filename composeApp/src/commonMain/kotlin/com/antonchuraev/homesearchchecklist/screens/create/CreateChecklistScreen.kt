package com.antonchuraev.homesearchchecklist.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateChecklistScreen(
    onBackButtonClick: () -> Unit,
    viewModel: CreateChecklistViewModel = koinViewModel()
){

    var name by remember {
        mutableStateOf("")
    }

    val elements: MutableList<String> by remember {
        mutableStateOf(mutableListOf())
    }

    AppScaffold(
        title = "Создание",
        onBackButtonClick = onBackButtonClick,
        bottomBar = {
            Button(
                onClick = {
                    viewModel.onSaveClick(
                        name,
                        elements = elements
                    )
                }
            ){
                Text("Сохранить")
            }
        }
    ){
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Название"
            )
            TextField(
                name,
                onValueChange = {
                    name = it
                }
            )

            Text(
                "Элементы"
            )

            Button(
                onClick = {
                    elements.add("")
                }
            ){
                Text("Добавить Пункт")
            }

            for (index in 0..elements.size){

                val text = elements[index]

                TextField(
                    text,
                    onValueChange = {
                        elements[index] = it
                    }
                )
            }
        }
    }
}