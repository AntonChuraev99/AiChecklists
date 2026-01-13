package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import homesearchchecklist.core.designsystem.generated.resources.Res
import homesearchchecklist.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateChecklistScreen(
    viewModel: CreateChecklistViewModel = koinViewModel()
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }

    AppScaffold(
        title = stringResource(Res.string.create_title),
        onBackButtonClick = { viewModel.sendIntent(CreateChecklistIntent.OnBackClick) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.ScreenPaddingHorizontal)
                    .padding(bottom = AppDimens.SpacingLg)
                    .navigationBarsPadding()
            ) {
                AppButton(
                    text = stringResource(Res.string.save),
                    onClick = { viewModel.sendIntent(CreateChecklistIntent.OnSaveClick) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
            modifier = Modifier
                .padding(AppDimens.ScreenPaddingHorizontal)
                .padding(top = AppDimens.SpacingLg)
                .verticalScroll(rememberScrollState())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                Text(
                    text = stringResource(Res.string.create_name_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                AppTextField(
                    value = screenState.name,
                    onValueChange = { viewModel.sendIntent(CreateChecklistIntent.OnNameChange(it)) },
                    placeholder = stringResource(Res.string.create_name_placeholder),
                    isError = screenState.nameError != null,
                    errorMessage = screenState.nameError
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                Text(
                    text = stringResource(Res.string.create_items_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                AppButtonSecondary(
                    text = stringResource(Res.string.create_add_item),
                    onClick = { showDialog = true },
                    icon = Icons.Filled.Add
                )

                screenState.items.forEach { item ->
                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.sendIntent(CreateChecklistIntent.OnDeleteItem(item)) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(Res.string.delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    dialogText = ""
                },
                title = {
                    Text(
                        text = stringResource(Res.string.create_add_item_dialog_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    AppTextField(
                        value = dialogText,
                        onValueChange = { dialogText = it },
                        label = stringResource(Res.string.create_item_name_label)
                    )
                },
                confirmButton = {
                    AppButtonText(
                        text = stringResource(Res.string.save),
                        onClick = {
                            if (dialogText.isNotBlank()) {
                                viewModel.sendIntent(CreateChecklistIntent.OnAddItem(dialogText))
                                dialogText = ""
                                showDialog = false
                            }
                        }
                    )
                },
                dismissButton = {
                    AppButtonText(
                        text = stringResource(Res.string.cancel),
                        onClick = {
                            showDialog = false
                            dialogText = ""
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            )
        }
    }
}

