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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.foundation.text.TextAutoSize
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = koinViewModel()
) {
    val state by viewModel.screenState.collectAsState()

    AppScaffold(
        title = stringResource(Res.string.create_title),
        onBackButtonClick = { viewModel.sendIntent(TemplatesScreenIntent.OnBackClick) }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main content area (scrollable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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

            // Bottom action buttons
            BottomActionButtons(
                onCreateManually = { viewModel.sendIntent(TemplatesScreenIntent.OnCreateManuallyClick) },
                onCreateWithAi = { viewModel.sendIntent(TemplatesScreenIntent.OnCreateWithAiClick) }
            )
        }
    }
}

@Composable
private fun BottomActionButtons(
    onCreateManually: () -> Unit,
    onCreateWithAi: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingLg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
        ) {
            // Create manually button
            AppButton(
                text = stringResource(Res.string.templates_create_manually),
                onClick = onCreateManually,
                icon = Icons.Default.Edit,
                modifier = Modifier.fillMaxWidth()
            )

            // Create with AI button
            AppButtonSecondary(
                text = stringResource(Res.string.templates_create_with_ai),
                onClick = onCreateWithAi,
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
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
        // Section header
        item {
            Text(
                text = stringResource(Res.string.templates_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingSm
                )
            )
        }

        items(categories) { category ->
            CategorySection(
                category = category,
                onTemplateClick = onTemplateClick
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }

        // Bottom spacing for buttons
        item {
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
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = AppDimens.ScreenPaddingHorizontal,
                vertical = AppDimens.SpacingXs
            )
        )

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
            .width(200.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.SpacingMd)
        ) {
            // Header with icon and item count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(getIconBackgroundColor(template.category)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForTemplate(template.icon),
                        contentDescription = null,
                        tint = getIconColor(template.category),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Item count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${template.items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Template name
            Text(
                text = template.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

            // Description with auto-fit
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 10.sp,
                    maxFontSize = 14.sp,
                    stepSize = 1.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // "Use" indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.templates_tap_to_use),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
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

/**
 * Get background color for icon based on category.
 */
@Composable
private fun getIconBackgroundColor(category: String): Color {
    return when (category.lowercase()) {
        "real_estate" -> Color(0xFFE3F2FD) // Light blue
        "travel" -> Color(0xFFFFF3E0) // Light orange
        "shopping" -> Color(0xFFE8F5E9) // Light green
        "work" -> Color(0xFFF3E5F5) // Light purple
        "events" -> Color(0xFFFFEBEE) // Light red
        "health" -> Color(0xFFE0F7FA) // Light cyan
        "home" -> Color(0xFFFFFDE7) // Light yellow
        else -> MaterialTheme.colorScheme.primaryContainer
    }
}

/**
 * Get icon color based on category.
 */
@Composable
private fun getIconColor(category: String): Color {
    return when (category.lowercase()) {
        "real_estate" -> Color(0xFF1976D2) // Blue
        "travel" -> Color(0xFFE65100) // Orange
        "shopping" -> Color(0xFF388E3C) // Green
        "work" -> Color(0xFF7B1FA2) // Purple
        "events" -> Color(0xFFC62828) // Red
        "health" -> Color(0xFF00838F) // Cyan
        "home" -> Color(0xFFF9A825) // Yellow/Amber
        else -> MaterialTheme.colorScheme.primary
    }
}
