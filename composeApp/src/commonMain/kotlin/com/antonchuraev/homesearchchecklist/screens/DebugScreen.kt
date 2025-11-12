package com.antonchuraev.homesearchchecklist.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.viewmodels.DebugViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Дебаг меню для разработки
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    viewModel: DebugViewModel = koinViewModel()
) {
    val showDialog by viewModel.showInfoDialog.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дебаг меню") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            
            item {
                DebugMenuItem(
                    icon = Icons.Default.Info,
                    title = "Информация о приложении",
                    description = "Версия, билд и другие данные",
                    onClick = { viewModel.showInfoDialog() }
                )
            }
            
            item {
                DebugMenuItem(
                    icon = Icons.Default.Refresh,
                    title = "Сбросить онбординг",
                    description = "Показать экран приветствия снова",
                    onClick = { viewModel.resetOnboarding() }
                )
            }
            
            item {
                DebugMenuItem(
                    icon = Icons.Default.Delete,
                    title = "Очистить данные",
                    description = "Удалить все локальные данные",
                    onClick = { viewModel.clearData() }
                )
            }
            
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            item {
                Text(
                    text = "Тестовые данные",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                DebugMenuItem(
                    icon = Icons.Default.Add,
                    title = "Создать тестовые чек-листы",
                    description = "Добавить демо-данные для тестирования",
                    onClick = { viewModel.createTestChecklists() }
                )
            }
        }
        
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideInfoDialog() },
                title = { Text("Информация о приложении") },
                text = {
                    Column {
                        InfoRow("Название", "Home Search Checklist")
                        InfoRow("Версия", "1.0.0")
                        InfoRow("Билд", "Debug")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.hideInfoDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

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

