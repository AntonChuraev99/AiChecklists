package com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components

// Shared between feature/onboarding DiscoverMoreStep and feature/updatefeed.
// Strings live in core/designsystem composeResources with `onboarding_discover_widget_*` prefix.

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_discover_widget_done
import aichecklists.core.designsystem.generated.resources.onboarding_discover_widget_sheet_title
import aichecklists.core.designsystem.generated.resources.onboarding_discover_widget_step1
import aichecklists.core.designsystem.generated.resources.onboarding_discover_widget_step2
import aichecklists.core.designsystem.generated.resources.onboarding_discover_widget_step3
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

private val Blue50 = Color(0xFFE3F2FD)

@Composable
fun WidgetInstructionOverlay(
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(bottom = AppDimens.SpacingXl)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_discover_widget_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        WidgetStep(number = 1, text = stringResource(Res.string.onboarding_discover_widget_step1))
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        WidgetStep(number = 2, text = stringResource(Res.string.onboarding_discover_widget_step2))
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        WidgetStep(number = 3, text = stringResource(Res.string.onboarding_discover_widget_step3))

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        AppButton(
            text = stringResource(Res.string.onboarding_discover_widget_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun WidgetStep(
    number: Int,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Blue50, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
