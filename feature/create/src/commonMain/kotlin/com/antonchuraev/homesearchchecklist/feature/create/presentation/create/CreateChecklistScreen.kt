package com.antonchuraev.homesearchchecklist.feature.create.presentation.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AddItemInputField
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CreateChecklistScreen(
    editChecklistId: Long? = null,
    viewModel: CreateChecklistViewModel = koinViewModel { parametersOf(editChecklistId) }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("create_checklist") }

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    val title = if (screenState.isEditMode) {
        stringResource(Res.string.checklist_edit_title)
    } else {
        stringResource(Res.string.create_title)
    }

    AppScaffold(
        title = title,
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
        LazyColumn(
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPaddingHorizontal,
                end = AppDimens.ScreenPaddingHorizontal,
                top = AppDimens.SpacingLg,
                bottom = AppDimens.SpacingLg
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            // Name section
            item(key = "name_section") {
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
                        errorMessage = screenState.nameError,
                        showClearButton = true
                    )
                }
            }

            // Items section header and input
            item(key = "items_header") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                    modifier = Modifier.padding(top = AppDimens.SpacingMd)
                ) {
                    Text(
                        text = stringResource(Res.string.create_items_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Inline input field for adding items
                    AddItemInputField(
                        text = screenState.newItemText,
                        onTextChange = { viewModel.sendIntent(CreateChecklistIntent.OnNewItemTextChange(it)) },
                        onAdd = { viewModel.sendIntent(CreateChecklistIntent.OnAddItemFromInput) }
                    )
                }
            }

            // Items list (new items appear at top)
            itemsIndexed(
                items = screenState.items,
                key = { _, item -> item.id }
            ) { _, item ->
                AppCard(modifier = Modifier.animateItem()) {
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
}

