package com.antonchuraev.homesearchchecklist.desingsystem.containers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String? = null,
    onBackButtonClick: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            if (title != null || onBackButtonClick != null) {
                CenterAlignedTopAppBar(
                    title = {
                        title?.let {
                            Text(
                                text = it
                            )
                        }
                    },
                    navigationIcon = {
                        onBackButtonClick?.let {
                            IconButton(onClick = onBackButtonClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                    },
                    actions = {}
                )
            }
        },
        bottomBar = bottomBar
    ) {
        Box(
            Modifier.padding(it),
            content = {
                content.invoke()
            }
        )
    }
}

