package com.antonchuraev.homesearchchecklist.csat

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.csat_chip_ai_quality
import aichecklists.core.designsystem.generated.resources.csat_chip_better_ai
import aichecklists.core.designsystem.generated.resources.csat_chip_better_design
import aichecklists.core.designsystem.generated.resources.csat_chip_buggy
import aichecklists.core.designsystem.generated.resources.csat_chip_easy_export
import aichecklists.core.designsystem.generated.resources.csat_chip_easy_to_use
import aichecklists.core.designsystem.generated.resources.csat_chip_faster
import aichecklists.core.designsystem.generated.resources.csat_chip_hard_to_use
import aichecklists.core.designsystem.generated.resources.csat_chip_inaccurate_ai
import aichecklists.core.designsystem.generated.resources.csat_chip_more_features
import aichecklists.core.designsystem.generated.resources.csat_chip_more_templates
import aichecklists.core.designsystem.generated.resources.csat_chip_nice_design
import aichecklists.core.designsystem.generated.resources.csat_chip_slow
import aichecklists.core.designsystem.generated.resources.csat_chip_templates
import aichecklists.core.designsystem.generated.resources.csat_chip_too_expensive
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
import aichecklists.core.designsystem.generated.resources.csat_what_could_be_better
import aichecklists.core.designsystem.generated.resources.csat_what_do_you_like
import aichecklists.core.designsystem.generated.resources.csat_whats_the_problem
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import org.jetbrains.compose.resources.StringResource
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
                targetState = ContentState.from(state),
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) + slideInVertically { it / 4 })
                        .togetherWith(fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
                label = "csat_content",
            ) { contentState ->
                when (contentState) {
                    ContentState.Empty -> {
                        Spacer(Modifier.height(0.dp))
                    }

                    ContentState.NotGoodFeedback -> {
                        NegativeFeedbackContent(
                            title = stringResource(Res.string.csat_whats_the_problem),
                            chips = FeedbackChip.notGoodChips,
                            selectedChips = state.selectedChips,
                            feedbackText = state.feedbackText,
                            onToggleChip = { onIntent(CsatIntent.ToggleChip(it)) },
                            onTextChange = { onIntent(CsatIntent.UpdateText(it)) },
                            onSubmit = { onIntent(CsatIntent.Submit) },
                        )
                    }

                    ContentState.OkayFeedback -> {
                        NegativeFeedbackContent(
                            title = stringResource(Res.string.csat_what_could_be_better),
                            chips = FeedbackChip.okayChips,
                            selectedChips = state.selectedChips,
                            feedbackText = state.feedbackText,
                            onToggleChip = { onIntent(CsatIntent.ToggleChip(it)) },
                            onTextChange = { onIntent(CsatIntent.UpdateText(it)) },
                            onSubmit = { onIntent(CsatIntent.Submit) },
                        )
                    }

                    ContentState.PositiveFeedback -> {
                        PositiveFeedbackContent(
                            chips = FeedbackChip.positiveChips,
                            selectedChips = state.selectedChips,
                            feedbackText = state.feedbackText,
                            onToggleChip = { onIntent(CsatIntent.ToggleChip(it)) },
                            onTextChange = { onIntent(CsatIntent.UpdateText(it)) },
                            onSubmit = { onIntent(CsatIntent.Submit) },
                        )
                    }

                    ContentState.ThankYou -> {
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

/** Discrete content states for AnimatedContent transitions. */
private enum class ContentState {
    Empty, NotGoodFeedback, OkayFeedback, PositiveFeedback, ThankYou;

    companion object {
        fun from(state: CsatState): ContentState = when {
            state.selectedRating == null -> Empty
            state.selectedRating == CsatRating.LoveIt && state.isSubmitted -> ThankYou
            state.selectedRating == CsatRating.LoveIt -> PositiveFeedback
            state.selectedRating == CsatRating.Okay -> OkayFeedback
            else -> NotGoodFeedback
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackChipsRow(
    title: String,
    chips: List<FeedbackChip>,
    selectedChips: Set<FeedbackChip>,
    onToggleChip: (FeedbackChip) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AppDimens.SpacingSm))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
        ) {
            chips.forEach { chip ->
                val isSelected = chip in selectedChips
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggleChip(chip) },
                    label = {
                        Text(
                            text = stringResource(chip.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NegativeFeedbackContent(
    title: String,
    chips: List<FeedbackChip>,
    selectedChips: Set<FeedbackChip>,
    feedbackText: String,
    onToggleChip: (FeedbackChip) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeedbackChipsRow(
            title = title,
            chips = chips,
            selectedChips = selectedChips,
            onToggleChip = onToggleChip,
        )

        Spacer(Modifier.height(AppDimens.SpacingMd))

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
private fun PositiveFeedbackContent(
    chips: List<FeedbackChip>,
    selectedChips: Set<FeedbackChip>,
    feedbackText: String,
    onToggleChip: (FeedbackChip) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeedbackChipsRow(
            title = stringResource(Res.string.csat_what_do_you_like),
            chips = chips,
            selectedChips = selectedChips,
            onToggleChip = onToggleChip,
        )

        Spacer(Modifier.height(AppDimens.SpacingMd))

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

/** Maps each [FeedbackChip] to its localized string resource. */
private val FeedbackChip.labelRes: StringResource
    get() = when (this) {
        // Not Good
        FeedbackChip.Buggy -> Res.string.csat_chip_buggy
        FeedbackChip.Slow -> Res.string.csat_chip_slow
        FeedbackChip.HardToUse -> Res.string.csat_chip_hard_to_use
        FeedbackChip.InaccurateAi -> Res.string.csat_chip_inaccurate_ai
        FeedbackChip.TooExpensive -> Res.string.csat_chip_too_expensive
        // Okay
        FeedbackChip.MoreFeatures -> Res.string.csat_chip_more_features
        FeedbackChip.BetterDesign -> Res.string.csat_chip_better_design
        FeedbackChip.Faster -> Res.string.csat_chip_faster
        FeedbackChip.MoreTemplates -> Res.string.csat_chip_more_templates
        FeedbackChip.BetterAi -> Res.string.csat_chip_better_ai
        // Positive
        FeedbackChip.AiQuality -> Res.string.csat_chip_ai_quality
        FeedbackChip.Templates -> Res.string.csat_chip_templates
        FeedbackChip.NiceDesign -> Res.string.csat_chip_nice_design
        FeedbackChip.EasyExport -> Res.string.csat_chip_easy_export
        FeedbackChip.EasyToUse -> Res.string.csat_chip_easy_to_use
    }
