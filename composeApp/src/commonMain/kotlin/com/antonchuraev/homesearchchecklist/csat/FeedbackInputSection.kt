package com.antonchuraev.homesearchchecklist.csat

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.csat_feedback_placeholder
import aichecklists.core.designsystem.generated.resources.csat_submit
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/** Reusable text input + submit button used in both the full CSAT sheet and the feedback-only sheet. */
@Composable
internal fun FeedbackInputSection(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmitClick: () -> Unit,
    submitEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(Res.string.csat_feedback_placeholder),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
        )

        Spacer(Modifier.height(AppDimens.SpacingLg))

        AppButton(
            text = stringResource(Res.string.csat_submit),
            onClick = onSubmitClick,
            enabled = submitEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
