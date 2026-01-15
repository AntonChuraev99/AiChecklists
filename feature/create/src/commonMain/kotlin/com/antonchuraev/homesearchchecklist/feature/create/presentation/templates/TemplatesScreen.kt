package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = koinViewModel()
) {
    val state by viewModel.screenState.collectAsState()

    AppScaffold(
        title = stringResource(Res.string.templates_title),
        onBackButtonClick = { viewModel.sendIntent(TemplatesScreenIntent.OnBackClick) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.categories.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = stringResource(Res.string.templates_empty_title),
                        description = stringResource(Res.string.templates_empty_description)
                    )
                }
                else -> {
                    TemplatesContent(
                        categories = state.categories,
                        onTemplateClick = { viewModel.sendIntent(TemplatesScreenIntent.OnTemplateClick(it)) }
                    )
                }
            }

            // Error snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(AppDimens.SpacingLg),
                    action = {
                        TextButton(onClick = { viewModel.sendIntent(TemplatesScreenIntent.OnDismissError) }) {
                            Text(stringResource(Res.string.ok))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }

        // Template preview dialog
        if (state.showPreviewDialog && state.selectedTemplate != null) {
            TemplatePreviewDialog(
                template = state.selectedTemplate!!,
                isCreating = state.isCreating,
                onDismiss = { viewModel.sendIntent(TemplatesScreenIntent.OnDismissPreview) },
                onCreate = { viewModel.sendIntent(TemplatesScreenIntent.OnCreateFromTemplate) }
            )
        }
    }
}

@Composable
private fun TemplatesContent(
    categories: List<TemplateCategory>,
    onTemplateClick: (ChecklistTemplate) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = AppDimens.SpacingLg)
    ) {
        items(categories) { category ->
            CategorySection(
                category = category,
                onTemplateClick = onTemplateClick
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        }
    }
}

@Composable
private fun CategorySection(
    category: TemplateCategory,
    onTemplateClick: (ChecklistTemplate) -> Unit
) {
    Column {
        // Category header
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Horizontal scrolling templates
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppDimens.ScreenPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            items(category.templates) { template ->
                TemplateCard(
                    template = template,
                    onClick = { onTemplateClick(template) }
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ChecklistTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingLg)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForTemplate(template.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // Template name
            Text(
                text = template.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

            // Item count
            Text(
                text = stringResource(Res.string.templates_item_count, template.items.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TemplatePreviewDialog(
    template: ChecklistTemplate,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForTemplate(template.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.templates_preview_items, template.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

                // Show preview items (max 5)
                template.items.take(5).forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            // Empty checkbox
                        }
                        Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Show "and X more" if there are more items
                if (template.items.size > 5) {
                    Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                    Text(
                        text = stringResource(Res.string.templates_and_more, template.items.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
                }
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingXs))
                Text(stringResource(Res.string.templates_use_template))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

/**
 * Maps icon name from Remote Config to Material Icon.
 */
private fun getIconForTemplate(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "apartment" -> Icons.Default.Apartment
        "home" -> Icons.Default.Home
        "luggage" -> Icons.Default.Luggage
        "flight_takeoff" -> Icons.Default.FlightTakeoff
        "shopping_cart" -> Icons.Default.ShoppingCart
        "groups" -> Icons.Default.Groups
        "rocket_launch" -> Icons.Default.RocketLaunch
        "celebration" -> Icons.Default.Celebration
        "local_shipping" -> Icons.Default.LocalShipping
        "medical_services" -> Icons.Default.MedicalServices
        "cleaning_services" -> Icons.Default.CleaningServices
        else -> Icons.Default.Check
    }
}

