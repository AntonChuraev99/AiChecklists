package com.antonchuraev.homesearchchecklist.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.viewmodels.FutureTabViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Заглушка для будущего функционала
 */
@Composable
fun FutureTabScreen(
    viewModel: FutureTabViewModel = koinViewModel()
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Раздел в разработке",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Здесь появится дополнительный функционал для работы с чек-листами",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

