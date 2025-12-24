package com.antonchuraev.homesearchchecklist.feature.home.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onDebugClick: () -> Unit,
    openCreateNewChecklistScreen: () -> Unit,
    openSelectFromTemplatesScreen: () -> Unit,
    viewModel: MainScreenViewModel = koinViewModel()
) {
    val screenState: MainScreenState by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onDebugClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
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
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
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

