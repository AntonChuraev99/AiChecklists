package com.antonchuraev.homesearchchecklist.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    viewModel: MainViewModel = koinViewModel()
) {

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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MainScreenContent()
        }
    }
}


/*private enum class BottomNavTab(
    val title: String,
    val icon: ImageVector
) {
    HOME(
        title = "Главная",
        icon = Icons.Default.Home
    ),
    FUTURE(
        title = "Будущее",
        icon = Icons.Default.Star
    )
}*/

