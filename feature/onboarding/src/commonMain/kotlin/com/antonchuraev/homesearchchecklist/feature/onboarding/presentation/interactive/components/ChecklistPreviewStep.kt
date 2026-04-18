package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.collapse
import aichecklists.core.designsystem.generated.resources.completed_count
import aichecklists.core.designsystem.generated.resources.expand
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_preview_button
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_preview_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_preview_title
import aichecklists.core.designsystem.generated.resources.onboarding_preview_all_checked
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.PreviewChecklistItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChecklistPreviewStep(
    checklistName: String,
    previewItems: List<PreviewChecklistItem>,
    originalItemCount: Int,
    separateCompleted: Boolean,
    autoDeleteCompleted: Boolean,
    isCreating: Boolean,
    onItemToggle: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uncheckedItems = remember(previewItems, separateCompleted) {
        if (separateCompleted) previewItems.filter { !it.isChecked }
        else previewItems
    }
    val completedItems = remember(previewItems, separateCompleted) {
        if (separateCompleted) previewItems.filter { it.isChecked }
        else emptyList()
    }
    var completedExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))

        Text(
            text = stringResource(Res.string.onboarding_interactive_preview_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_interactive_preview_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Checklist name
        Text(
            text = checklistName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Interactive items list
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            contentPadding = PaddingValues(bottom = AppDimens.SpacingSm)
        ) {
            // Unchecked items (or all when separateCompleted is off)
            if (uncheckedItems.isEmpty() && completedItems.isEmpty()) {
                item(key = "all_checked") {
                    AllCheckedMessage()
                }
            }

            items(
                items = uncheckedItems,
                key = { it.id }
            ) { item ->
                PreviewItemCard(
                    item = item,
                    onToggle = { onItemToggle(item.id) },
                    modifier = Modifier.animateItem()
                )
            }

            // Completed section
            if (separateCompleted && completedItems.isNotEmpty()) {
                item(key = "completed_header") {
                    CompletedSectionHeader(
                        completedCount = completedItems.size,
                        expanded = completedExpanded,
                        onToggle = { completedExpanded = !completedExpanded },
                        modifier = Modifier.animateItem()
                    )
                }

                if (completedExpanded) {
                    items(
                        items = completedItems,
                        key = { it.id }
                    ) { item ->
                        PreviewItemCard(
                            item = item,
                            onToggle = { onItemToggle(item.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Save button
        AppButton(
            text = stringResource(Res.string.onboarding_interactive_preview_button),
            onClick = onSave,
            enabled = !isCreating,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
    }
}

@Composable
private fun PreviewItemCard(
    item: PreviewChecklistItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // KMP double-toggle bug: onCheckedChange = null, Row handles click
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = null
            )
            Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (isDark) {
        OutlinedCard(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) { cardContent() }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.CardElevation)
        ) { cardContent() }
    }
}

@Composable
private fun CompletedSectionHeader(
    completedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.completed_count, completedCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) {
                stringResource(Res.string.collapse)
            } else {
                stringResource(Res.string.expand)
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllCheckedMessage() {
    Text(
        text = stringResource(Res.string.onboarding_preview_all_checked),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingXl)
    )
}
