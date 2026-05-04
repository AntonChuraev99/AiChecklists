package com.antonchuraev.homesearchchecklist.feature.create.presentation.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Countertops
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RealEstateAgent
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.EmptyState
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.ChecklistTemplate
import com.antonchuraev.homesearchchecklist.feature.create.domain.model.TemplateCategory
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = koinViewModel()
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("templates") }

    val state by viewModel.screenState.collectAsState()

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    AppScaffold(
        title = stringResource(Res.string.create_title),
        onBackButtonClick = { viewModel.sendIntent(TemplatesScreenIntent.OnBackClick) },
        actions = {
            IconButton(
                onClick = { viewModel.sendIntent(TemplatesScreenIntent.OnToggleSearch) }
            ) {
                Icon(
                    imageVector = if (state.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (state.isSearchActive) {
                        stringResource(Res.string.cancel)
                    } else {
                        stringResource(Res.string.templates_search_placeholder)
                    },
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Expandable search field
            AnimatedVisibility(
                visible = state.isSearchActive,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                AppTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.sendIntent(TemplatesScreenIntent.OnSearchQueryChange(it)) },
                    placeholder = stringResource(Res.string.templates_search_placeholder),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    showClearButton = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                        .padding(vertical = AppDimens.SpacingMd)
                        .focusRequester(searchFocusRequester)
                )
            }

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
                    state.filteredCategories.isEmpty() && state.searchQuery.isNotEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = stringResource(Res.string.templates_no_results),
                            description = stringResource(Res.string.templates_no_results_description)
                        )
                    }
                    else -> {
                        TemplatesContent(
                            categories = state.filteredCategories,
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
                onCreateWithAi = { viewModel.sendIntent(TemplatesScreenIntent.OnCreateWithAiClick) },
                onCreateWeekly = { viewModel.sendIntent(TemplatesScreenIntent.OnCreateWeeklyClick) },
            )
        }
    }
}

@Composable
private fun BottomActionButtons(
    onCreateManually: () -> Unit,
    onCreateWithAi: () -> Unit,
    onCreateWeekly: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingLg),
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

        // My Week button
        AppButtonSecondary(
            text = stringResource(Res.string.templates_create_weekly),
            onClick = onCreateWeekly,
            icon = Icons.Outlined.CalendarMonth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Height of bottom buttons section (2 buttons + spacing + padding)
private val BottomButtonsSectionHeight = 160.dp

@Composable
private fun TemplatesContent(
    categories: List<TemplateCategory>,
    onTemplateClick: (ChecklistTemplate) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = AppDimens.SpacingLg,
            bottom = BottomButtonsSectionHeight
        )
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
    Surface(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpacingMd)
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getIconBackgroundColor(template.category)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForTemplate(template.icon),
                        contentDescription = null,
                        tint = getIconColor(template.category),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Item count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${template.items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // Template name
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

            // Description
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // "Use" indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.templates_tap_to_use),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Maps icon name from template JSON to Material Icon.
 */
private fun getIconForTemplate(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        // Real Estate
        "apartment" -> Icons.Default.Apartment
        "home" -> Icons.Default.Home
        "description" -> Icons.Outlined.Description
        "real_estate_agent" -> Icons.Default.RealEstateAgent
        // Travel
        "luggage" -> Icons.Default.Luggage
        "flight_takeoff" -> Icons.Default.FlightTakeoff
        "directions_car" -> Icons.Default.DirectionsCar
        "business_center" -> Icons.Default.BusinessCenter
        "forest" -> Icons.Default.Forest
        // Shopping
        "shopping_cart" -> Icons.Default.ShoppingCart
        "checkroom" -> Icons.Default.Checkroom
        "devices" -> Icons.Default.Devices
        "card_giftcard" -> Icons.Default.CardGiftcard
        // Work
        "groups" -> Icons.Default.Groups
        "rocket_launch" -> Icons.Default.RocketLaunch
        "work" -> Icons.Default.Work
        "badge" -> Icons.Default.Badge
        "event_note" -> Icons.AutoMirrored.Filled.EventNote
        // Events
        "celebration" -> Icons.Default.Celebration
        "local_shipping" -> Icons.Default.LocalShipping
        "favorite" -> Icons.Default.Favorite
        "cake" -> Icons.Default.Cake
        "holiday_village" -> Icons.Default.HolidayVillage
        // Health
        "medical_services" -> Icons.Default.MedicalServices
        "wb_sunny" -> Icons.Default.WbSunny
        "medication" -> Icons.Default.Medication
        "dentistry" -> Icons.Default.HealthAndSafety
        // Education
        "school" -> Icons.Default.School
        "quiz" -> Icons.Default.Quiz
        "edit_document" -> Icons.Default.EditNote
        "computer" -> Icons.Default.Computer
        // Home
        "cleaning_services" -> Icons.Default.CleaningServices
        "mop" -> Icons.Default.CleaningServices
        "handyman" -> Icons.Default.Handyman
        "home_work" -> Icons.Default.HomeWork
        // Fitness
        "fitness_center" -> Icons.Default.FitnessCenter
        "directions_run" -> Icons.AutoMirrored.Filled.DirectionsRun
        "self_improvement" -> Icons.Default.SelfImprovement
        "exercise" -> Icons.Default.FitnessCenter
        // Cooking
        "restaurant" -> Icons.Default.Restaurant
        "kitchen" -> Icons.Default.Kitchen
        "bakery_dining" -> Icons.Default.BakeryDining
        "countertops" -> Icons.Default.Countertops
        // Finance
        "account_balance_wallet" -> Icons.Default.AccountBalanceWallet
        "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
        "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
        "savings" -> Icons.Default.Savings
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
        "education" -> Color(0xFFE8EAF6) // Light indigo
        "fitness" -> Color(0xFFFCE4EC) // Light pink
        "cooking" -> Color(0xFFFFF8E1) // Light amber
        "finance" -> Color(0xFFE0F2F1) // Light teal
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
        "education" -> Color(0xFF303F9F) // Indigo
        "fitness" -> Color(0xFFC2185B) // Pink
        "cooking" -> Color(0xFFFF8F00) // Amber
        "finance" -> Color(0xFF00695C) // Teal
        else -> MaterialTheme.colorScheme.primary
    }
}
