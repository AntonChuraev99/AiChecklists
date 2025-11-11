package com.antonchuraev.homesearchchecklist.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Главный экран с нижней навигацией
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onDebugClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedTab.title) },
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
            NavigationBar {
                BottomNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                BottomNavTab.HOME -> HomeTabScreen()
                BottomNavTab.FUTURE -> FutureTabScreen()
            }
        }
    }
}

/**
 * Табы нижней навигации
 */
private enum class BottomNavTab(
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
}

