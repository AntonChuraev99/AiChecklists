package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_category_title
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.LocalEmojiFont
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.OnboardingCategory
import org.jetbrains.compose.resources.stringResource

@Composable
fun CategorySelectionStep(
    selectedCategory: OnboardingCategory?,
    onCategorySelected: (OnboardingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .adaptiveContentWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))

        Text(
            text = stringResource(Res.string.onboarding_interactive_category_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_interactive_category_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            OnboardingCategory.entries.forEach { category ->
                CategoryCard(
                    category = category,
                    isSelected = category == selectedCategory,
                    onClick = { onCategorySelected(category) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
    }
}

@Composable
private fun CategoryCard(
    category: OnboardingCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.01f else 1f,
        label = "card_scale"
    )
    val categoryTitle = stringResource(category.titleRes)

    val shape = RoundedCornerShape(AppDimens.SpacingMd)
    val containerColor = if (isSelected) {
        AppCardDefaults.selectedContainerColor()
    } else {
        AppCardDefaults.containerColor()
    }
    val titleColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(vertical = AppDimens.SpacingMd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = category.icon, fontFamily = LocalEmojiFont.current, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
            Text(
                text = categoryTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Selectable card, shared flat style: selected = accent ring + filled primaryContainer,
    // unselected = hairline; no shadow in either state (the ring shows selection).
    Card(
        modifier = modifier
            .clip(shape)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onClick)
            .semantics { contentDescription = categoryTitle },
        shape = shape,
        colors = AppCardDefaults.colors(container = containerColor),
        border = if (isSelected) AppCardDefaults.selectedBorder() else AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation()
    ) { cardContent() }
}
