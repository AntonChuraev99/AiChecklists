package com.antonchuraev.homesearchchecklist.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateChecklistScreen(
    viewModel: CreateChecklistViewModel = koinViewModel(),
    onBackButtonClick: () -> Unit
){
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    var showDialog by remember {
        mutableStateOf(false)
    }

    var dialogText by remember {
        mutableStateOf("")
    }

    AppScaffold(
        title = "Создание",
        onBackButtonClick = onBackButtonClick,
        bottomBar = {
            Button(
                onClick = {
                    viewModel.onSaveClick()
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
                screenState.name,
                onValueChange = {
                    viewModel.onNameChange(it)
                }
            )

            Text(
                "Элементы"
            )

            Button(
                onClick = {
                    showDialog = true
                }
            ){
                Text("Добавить Пункт")
            }

            screenState.items.forEach { item ->
                Text(item.text)
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    dialogText = ""
                },
                title = { Text("Добавить пункт") },
                text = {
                    TextField(
                        value = dialogText,
                        onValueChange = { dialogText = it },
                        label = { Text("Название пункта") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (dialogText.isNotBlank()) {
                                viewModel.onAddItem(dialogText)
                                dialogText = ""
                                showDialog = false
                            }
                        }
                    ) {
                        Text("Сохранить")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDialog = false
                            dialogText = ""
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}