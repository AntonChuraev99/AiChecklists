package com.antonchuraev.homesearchchecklist.csat

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.csat_feedback_placeholder
import aichecklists.core.designsystem.generated.resources.csat_love_it
import aichecklists.core.designsystem.generated.resources.csat_maybe_later
import aichecklists.core.designsystem.generated.resources.csat_not_good
import aichecklists.core.designsystem.generated.resources.csat_okay
import aichecklists.core.designsystem.generated.resources.csat_rate_button
import aichecklists.core.designsystem.generated.resources.csat_submit
import aichecklists.core.designsystem.generated.resources.csat_thank_you_description
import aichecklists.core.designsystem.generated.resources.csat_thank_you_title
import aichecklists.core.designsystem.generated.resources.csat_title
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsatBottomSheet(
    state: CsatState,
    onIntent: (CsatIntent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(CsatIntent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = stringResource(Res.string.csat_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AppDimens.SpacingXl))

            // Emoji row
            EmojiRow(
                selectedRating = state.selectedRating,
                onSelect = { onIntent(CsatIntent.SelectRating(it)) },
            )

            Spacer(Modifier.height(AppDimens.SpacingLg))

            // Animated state-dependent content
            AnimatedContent(
                targetState = state.selectedRating,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) + slideInVertically { it / 4 })
                        .togetherWith(fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
                label = "csat_content",
            ) { rating ->
                when (rating) {
                    null -> {
                        // Empty — waiting for emoji selection
                        Spacer(Modifier.height(0.dp))
                    }

                    CsatRating.NotGood, CsatRating.Okay -> {
                        FeedbackForm(
                            feedbackText = state.feedbackText,
                            onTextChange = { onIntent(CsatIntent.UpdateText(it)) },
                            onSubmit = { onIntent(CsatIntent.Submit) },
                        )
                    }

                    CsatRating.LoveIt -> {
                        ThankYouContent(
                            onRate = { onIntent(CsatIntent.LaunchReview) },
                            onSkip = { onIntent(CsatIntent.SkipReview) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiRow(
    selectedRating: CsatRating?,
    onSelect: (CsatRating) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        EmojiItem(
            emoji = "\uD83D\uDE1E", // 😞
            label = stringResource(Res.string.csat_not_good),
            isSelected = selectedRating == CsatRating.NotGood,
            onClick = { onSelect(CsatRating.NotGood) },
        )
        EmojiItem(
            emoji = "\uD83D\uDE10", // 😐
            label = stringResource(Res.string.csat_okay),
            isSelected = selectedRating == CsatRating.Okay,
            onClick = { onSelect(CsatRating.Okay) },
        )
        EmojiItem(
            emoji = "\uD83D\uDE0A", // 😊
            label = stringResource(Res.string.csat_love_it),
            isSelected = selectedRating == CsatRating.LoveIt,
            onClick = { onSelect(CsatRating.LoveIt) },
        )
    }
}

@Composable
private fun EmojiItem(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.SpacingMd))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = emoji,
            fontSize = 40.sp,
        )
        Spacer(Modifier.height(AppDimens.SpacingXs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun FeedbackForm(
    feedbackText: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTextField(
            value = feedbackText,
            onValueChange = onTextChange,
            placeholder = stringResource(Res.string.csat_feedback_placeholder),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
        )

        Spacer(Modifier.height(AppDimens.SpacingLg))

        AppButton(
            text = stringResource(Res.string.csat_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThankYouContent(
    onRate: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "✅",
            fontSize = 40.sp,
        )

        Spacer(Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.csat_thank_you_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.csat_thank_you_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AppDimens.SpacingXl))

        AppButton(
            text = stringResource(Res.string.csat_rate_button),
            onClick = onRate,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AppDimens.SpacingSm))

        AppButtonText(
            text = stringResource(Res.string.csat_maybe_later),
            onClick = onSkip,
        )
    }
}
