package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.auto_delete_completed
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_customize_continue
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_customize_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_customize_title
import aichecklists.core.designsystem.generated.resources.onboarding_interactive_settings
import aichecklists.core.designsystem.generated.resources.separate_completed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSwitch
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.CustomizableItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun CustomizeStep(
    items: List<CustomizableItem>,
    checklistName: String,
    separateCompleted: Boolean,
    autoDeleteCompleted: Boolean,
    onToggleItem: (Int) -> Unit,
    onNameChanged: (String) -> Unit,
    onToggleSeparateCompleted: () -> Unit,
    onToggleAutoDeleteCompleted: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledCount = items.count { it.isEnabled }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        Text(
            text = stringResource(Res.string.onboarding_interactive_customize_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.onboarding_interactive_customize_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        // Checklist name
        OutlinedTextField(
            value = checklistName,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            shape = MaterialTheme.shapes.small
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Items list + settings
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { index, _ -> index }
            ) { index, item ->
                ToggleableItemRow(
                    item = item,
                    onToggle = { onToggleItem(index) }
                )
            }

            // Settings section
            item {
                Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

                Text(
                    text = stringResource(Res.string.onboarding_interactive_settings),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AppDimens.SpacingXs)
                )
            }

            item(key = "auto_delete") {
                SettingToggleRow(
                    icon = Icons.Outlined.RemoveDone,
                    text = stringResource(Res.string.auto_delete_completed),
                    checked = autoDeleteCompleted,
                    onToggle = onToggleAutoDeleteCompleted
                )
            }

            item(key = "separate_completed") {
                SettingToggleRow(
                    icon = Icons.Outlined.CheckCircle,
                    text = stringResource(Res.string.separate_completed),
                    checked = separateCompleted,
                    onToggle = onToggleSeparateCompleted
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        // Continue button
        AppButton(
            text = stringResource(Res.string.onboarding_interactive_customize_continue),
            onClick = onContinue,
            enabled = enabledCount > 0,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
    }
}

@Composable
private fun ToggleableItemRow(
    item: CustomizableItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.isEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            textDecoration = if (!item.isEnabled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        AppSwitch(
            checked = item.isEnabled,
            onCheckedChange = null,
            modifier = Modifier.padding(start = AppDimens.SpacingSm)
        )
    }
}

@Composable
private fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = AppDimens.SpacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AppDimens.SpacingMd)
        )
        AppSwitch(
            checked = checked,
            onCheckedChange = null
        )
    }
}
