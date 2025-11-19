package com.antonchuraev.homesearchchecklist.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.viewmodels.MainViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Главный экран с нижней навигацией
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onDebugClick: () -> Unit,
    openCreateNewChecklistScreen: () -> Unit,
    openSelectFromTemplatesScreen: () -> Unit,
    viewModel: MainViewModel = koinViewModel()
) {

    val isShowCreateBottomSheet by viewModel.isShowCreateChecklistBottomSheet.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onDebugClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Дебаг меню"
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MainScreenContent()
        }
    }

    // Bottom Sheet для создания нового чеклиста
    if (isShowCreateBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.hideCreateChecklistBottomSheet()
            },
            sheetState = sheetState
        ) {
            CreateChecklistBottomSheetContent(
                onDismiss = {
                    viewModel.hideCreateChecklistBottomSheet()
                },
                onCreateNewChecklistClick = openCreateNewChecklistScreen,
                onSelectFromTemplatesClick = openSelectFromTemplatesScreen
            )
        }
    }
}


/**
 * Содержимое bottom sheet для создания нового чеклиста
 */
@Composable
private fun CreateChecklistBottomSheetContent(
    onDismiss: () -> Unit,
    onCreateNewChecklistClick: () -> Unit,
    onSelectFromTemplatesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Создать новый чеклист",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Кнопка: Создать с нуля
        Button(
            onClick = {
                onCreateNewChecklistClick.invoke()
                //onDismiss()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Создать с нуля",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Начните с пустого чеклиста",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Кнопка: Выбрать из шаблона
        OutlinedButton(
            onClick = {
                onSelectFromTemplatesClick.invoke()
                //onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Выбрать из шаблона",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Используйте готовый шаблон",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Дополнительное пространство снизу для отступа
        Spacer(modifier = Modifier.height(16.dp))
    }
}

