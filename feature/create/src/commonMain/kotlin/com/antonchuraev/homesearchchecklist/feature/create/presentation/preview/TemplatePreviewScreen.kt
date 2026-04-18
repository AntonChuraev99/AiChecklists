package com.antonchuraev.homesearchchecklist.feature.create.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TemplatePreviewScreen(
    templateId: String,
    viewModel: TemplatePreviewViewModel = koinViewModel { parametersOf(templateId) }
) {
    val state by viewModel.screenState.collectAsState()

    AppScaffold(
        title = state.template?.name ?: stringResource(Res.string.template_preview_title),
        onBackButtonClick = { viewModel.sendIntent(TemplatePreviewScreenIntent.OnBackClick) },
        bottomBar = {
            if (!state.isLoading && state.template != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.ScreenPaddingHorizontal)
                            .padding(vertical = AppDimens.SpacingLg)
                            .navigationBarsPadding()
                    ) {
                        AppButton(
                            text = if (state.isCreating)
                                stringResource(Res.string.template_preview_creating)
                            else
                                stringResource(Res.string.template_preview_create_button, state.editableItems.size),
                            onClick = { viewModel.sendIntent(TemplatePreviewScreenIntent.OnCreateChecklist) },
                            icon = Icons.Filled.Add,
                            enabled = !state.isCreating && state.editableItems.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.template != null -> {
                    TemplatePreviewContent(
                        template = state.template!!,
                        items = state.editableItems,
                        newItemText = state.newItemText,
                        onRemoveItem = { viewModel.sendIntent(TemplatePreviewScreenIntent.OnRemoveItem(it)) },
                        onNewItemTextChange = { viewModel.sendIntent(TemplatePreviewScreenIntent.OnNewItemTextChange(it)) },
                        onAddItem = { viewModel.sendIntent(TemplatePreviewScreenIntent.OnAddItem) }
                    )
                }
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(AppDimens.SpacingLg),
                    action = {
                        TextButton(onClick = { viewModel.sendIntent(TemplatePreviewScreenIntent.OnDismissError) }) {
                            Text(stringResource(Res.string.ok))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun TemplatePreviewContent(
    template: ChecklistTemplate,
    items: List<String>,
    newItemText: String,
    onRemoveItem: (Int) -> Unit,
    onNewItemTextChange: (String) -> Unit,
    onAddItem: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.ScreenPaddingHorizontal,
            vertical = AppDimens.SpacingLg
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        // Description header
        item {
            if (template.description.isNotEmpty()) {
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppDimens.SpacingMd)
                )
            }
        }

        // Items count
        item {
            Text(
                text = stringResource(Res.string.template_preview_items_count, items.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = AppDimens.SpacingSm)
            )
        }

        // Editable items list
        itemsIndexed(items) { index, item ->
            ChecklistItemCard(
                text = item,
                onRemove = { onRemoveItem(index) }
            )
        }

        // Add new item field
        item {
            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
            AddItemField(
                text = newItemText,
                onTextChange = onNewItemTextChange,
                onAdd = onAddItem
            )
        }

        // Bottom padding for button
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ChecklistItemCard(
    text: String,
    onRemove: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val shape = RoundedCornerShape(12.dp)
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox placeholder
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))

            // Item text
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.template_preview_remove_item),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (isDark) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) { cardContent() }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation)
        ) { cardContent() }
    }
}

@Composable
private fun AddItemField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(Res.string.template_preview_add_item_hint)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd() }),
            shape = RoundedCornerShape(12.dp)
        )

        IconButton(
            onClick = onAdd,
            enabled = text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.template_preview_add_item),
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
