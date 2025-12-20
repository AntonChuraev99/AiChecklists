package com.antonchuraev.homesearchchecklist.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.feature.checklist.MainScreenState
import com.antonchuraev.homesearchchecklist.feature.checklist.MainViewModel
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
    val screenState: MainScreenState by viewModel.state.collectAsStateWithLifecycle()

    // Удалены isShowCreateBottomSheet и sheetState - пока не нужны
    
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
        bottomBar = {
            if (screenState is MainScreenState.Success && (screenState as MainScreenState.Success).checklists.isNotEmpty()) {
                FilledTonalButton(onClick = openCreateNewChecklistScreen) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать чек-лист")
                }
            }
        },
        modifier = Modifier.navigationBarsPadding()
    ) { paddingValues ->
        if (screenState is MainScreenState.Success) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                MainScreenContent(
                    screenState = screenState as MainScreenState.Success,
                    onAddChecklistClick = openCreateNewChecklistScreen
                )
            }
        }
    }
}