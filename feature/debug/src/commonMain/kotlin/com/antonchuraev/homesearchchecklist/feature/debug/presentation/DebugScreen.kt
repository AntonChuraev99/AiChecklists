package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = koinViewModel()
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    val items = listOf(
        DebugItem(Icons.Default.Info, "Информация о приложении", "Версия, билд и другие данные") {
            viewModel.sendIntent(DebugScreenIntent.ShowInfoDialog)
        },
        DebugItem(Icons.Default.Refresh, "Сбросить онбординг", "Показать экран приветствия снова") {
            viewModel.sendIntent(DebugScreenIntent.ResetOnboarding)
        },
        DebugItem(Icons.Default.Delete, "Очистить данные", "Удалить все локальные данные") {
            viewModel.sendIntent(DebugScreenIntent.ClearData)
        },
        DebugItem(Icons.Default.Add, "Создать тестовые чек-листы", "Добавить демо-данные для тестирования") {
            viewModel.sendIntent(DebugScreenIntent.CreateTestChecklists)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дебаг меню") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.sendIntent(DebugScreenIntent.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Инструменты разработчика",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(items) { item ->
                DebugMenuItem(
                    icon = item.icon,
                    title = item.title,
                    description = item.description,
                    onClick = item.onClick
                )
            }
        }

        if (screenState.showInfoDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.sendIntent(DebugScreenIntent.HideInfoDialog) },
                title = { Text("Информация о приложении") },
                text = {
                    Column {
                        InfoRow("Название", "Home Search Checklist")
                        InfoRow("Версия", "1.0.0")
                        InfoRow("Билд", "Debug")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.sendIntent(DebugScreenIntent.HideInfoDialog) }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private data class DebugItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
private fun DebugMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

