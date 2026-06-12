package com.antonchuraev.homesearchchecklist.feature.home.presentation.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_to_checklist_create_new
import aichecklists.core.designsystem.generated.resources.add_to_checklist_empty_description
import aichecklists.core.designsystem.generated.resources.add_to_checklist_empty_title
import aichecklists.core.designsystem.generated.resources.add_to_checklist_subtitle
import aichecklists.core.designsystem.generated.resources.add_to_checklist_title
import aichecklists.core.designsystem.generated.resources.fill_checklist_picker_empty_description
import aichecklists.core.designsystem.generated.resources.fill_checklist_picker_subtitle
import aichecklists.core.designsystem.generated.resources.fill_checklist_picker_title
import aichecklists.core.designsystem.generated.resources.items_count
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToChecklistPickerScreen(
    text: String,
    purpose: AddToChecklistPurpose = AddToChecklistPurpose.ADD_ITEM,
    viewModel: AddToChecklistPickerViewModel = koinViewModel(
        key = "add_to_checklist_${purpose.name}_${text.hashCode()}"
    ) { parametersOf(text, purpose) },
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val isFill = purpose == AddToChecklistPurpose.FILL_AI
    val titleRes = if (isFill) Res.string.fill_checklist_picker_title else Res.string.add_to_checklist_title
    val subtitleRes =
        if (isFill) Res.string.fill_checklist_picker_subtitle else Res.string.add_to_checklist_subtitle
    val emptyDescriptionRes =
        if (isFill) Res.string.fill_checklist_picker_empty_description
        else Res.string.add_to_checklist_empty_description

    AppScaffold(
        title = stringResource(titleRes),
        onBackButtonClick = { viewModel.sendIntent(AddToChecklistPickerIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
    ) {
        when {
            screenState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            screenState.checklists.isEmpty() -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                    title = stringResource(Res.string.add_to_checklist_empty_title),
                    description = stringResource(emptyDescriptionRes),
                    action = {
                        AppButtonSecondary(
                            text = stringResource(Res.string.add_to_checklist_create_new),
                            icon = Icons.Outlined.Add,
                            onClick = { viewModel.sendIntent(AddToChecklistPickerIntent.OnCreateNewClick) },
                        )
                    },
                )
            }

            else -> {
                AddToChecklistContent(
                    subtitle = stringResource(subtitleRes),
                    checklists = screenState.checklists,
                    onChecklistSelected = {
                        viewModel.sendIntent(AddToChecklistPickerIntent.OnChecklistSelected(it))
                    },
                    onCreateNewClick = {
                        viewModel.sendIntent(AddToChecklistPickerIntent.OnCreateNewClick)
                    },
                )
            }
        }
    }
}

@Composable
private fun AddToChecklistContent(
    subtitle: String,
    checklists: List<Checklist>,
    onChecklistSelected: (Checklist) -> Unit,
    onCreateNewClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.adaptiveContentWidth(),
        contentPadding = PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingLg,
            bottom = AppDimens.SpacingLg,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        item(key = "subtitle") {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AppDimens.SpacingSm),
            )
        }

        items(items = checklists, key = { it.id }) { checklist ->
            AppCard(onClick = { onChecklistSelected(checklist) }) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs)) {
                    Text(
                        text = checklist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(Res.plurals.items_count, checklist.items.size, checklist.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "create_new") {
            AppButtonSecondary(
                text = stringResource(Res.string.add_to_checklist_create_new),
                icon = Icons.Outlined.Add,
                onClick = onCreateNewClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.SpacingMd)
                    .navigationBarsPadding(),
            )
        }
    }
}
