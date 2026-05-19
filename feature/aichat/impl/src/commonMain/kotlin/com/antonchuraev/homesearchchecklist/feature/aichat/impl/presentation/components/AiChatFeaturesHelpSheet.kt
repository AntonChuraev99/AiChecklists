package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_features_example_add
import aichecklists.core.designsystem.generated.resources.chat_features_example_attach
import aichecklists.core.designsystem.generated.resources.chat_features_example_complete
import aichecklists.core.designsystem.generated.resources.chat_features_example_create
import aichecklists.core.designsystem.generated.resources.chat_features_example_delete
import aichecklists.core.designsystem.generated.resources.chat_features_example_find
import aichecklists.core.designsystem.generated.resources.chat_features_example_from_attachment
import aichecklists.core.designsystem.generated.resources.chat_features_example_move
import aichecklists.core.designsystem.generated.resources.chat_features_example_reminder
import aichecklists.core.designsystem.generated.resources.chat_features_examples_title
import aichecklists.core.designsystem.generated.resources.chat_features_got_it
import aichecklists.core.designsystem.generated.resources.chat_features_intro
import aichecklists.core.designsystem.generated.resources.chat_features_sheet_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Features help bottom sheet — explains AI Chat capabilities with concrete examples.
 *
 * Two sections:
 * - **Intro** — short paragraph describing what the assistant can do (natural language,
 *   attachments, voice).
 * - **Examples** — bulleted list of representative commands. Selecting an example does
 *   nothing (read-only); they're prompts for the user to copy mentally.
 *
 * Opens when the user taps the "?" icon in the input row leading position.
 * Dismissed via drag, scrim tap, or "Got it" button.
 *
 * Pattern mirrors [AiChatPricingHelpSheet]; reuse rather than abstract to keep each
 * sheet free to evolve its own structure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatFeaturesHelpSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.SpacingXl, vertical = AppDimens.SpacingXl),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            Text(
                text = stringResource(Res.string.chat_features_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            Text(
                text = stringResource(Res.string.chat_features_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.SpacingLg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Text(
                text = stringResource(Res.string.chat_features_examples_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Representative commands — same lexical patterns the Layer 1 parser
            // recognises in chat input.
            FeatureExample(Res.string.chat_features_example_create)
            FeatureExample(Res.string.chat_features_example_add)
            FeatureExample(Res.string.chat_features_example_complete)
            FeatureExample(Res.string.chat_features_example_delete)
            FeatureExample(Res.string.chat_features_example_reminder)
            FeatureExample(Res.string.chat_features_example_move)
            FeatureExample(Res.string.chat_features_example_find)
            FeatureExample(Res.string.chat_features_example_attach)
            FeatureExample(Res.string.chat_features_example_from_attachment)

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            AppButton(
                text = stringResource(Res.string.chat_features_got_it),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        }
    }
}

@Composable
private fun FeatureExample(
    textRes: StringResource,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingXs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
