package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_style_title
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.antonchuraev.homesearchchecklist.desingsystem.emoji.LocalEmojiFont
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.OrganizingStyle
import org.jetbrains.compose.resources.stringResource

@Composable
fun StyleSelectionStep(
    selectedStyle: OrganizingStyle?,
    onStyleSelected: (OrganizingStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))

        Text(
            text = stringResource(Res.string.onboarding_interactive_style_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_interactive_style_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))

        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            OrganizingStyle.entries.forEach { style ->
                StyleCard(
                    style = style,
                    isSelected = style == selectedStyle,
                    onClick = { onStyleSelected(style) }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
    }
}

@Composable
private fun StyleCard(
    style: OrganizingStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        label = "style_scale"
    )
    val title = stringResource(style.titleRes)
    val description = stringResource(style.descriptionRes)

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
    val descriptionColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = style.emoji, fontFamily = LocalEmojiFont.current, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = descriptionColor
            )
        }
    }

    // Selectable card, shared flat style: selected = accent ring + filled primaryContainer,
    // unselected = hairline; no shadow in either state (the ring shows selection).
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(shape)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onClick)
            .semantics { contentDescription = title },
        shape = shape,
        colors = AppCardDefaults.colors(container = containerColor),
        border = if (isSelected) AppCardDefaults.selectedBorder() else AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation()
    ) { cardContent() }
}
