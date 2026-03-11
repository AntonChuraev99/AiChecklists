package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_skip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components.CategorySelectionStep
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components.ChecklistPreviewStep
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components.WelcomeStep
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallIntent
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InteractiveOnboardingScreen(
    viewModel: InteractiveOnboardingViewModel = koinViewModel()
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("interactive_onboarding") }

    val state by viewModel.screenState.collectAsState()

    // Lazy PaywallViewModel — only instantiated at paywall step
    val paywallViewModel: PaywallViewModel? =
        if (state.currentStep == InteractiveOnboardingStep.Paywall) {
            koinViewModel(
                key = "onboarding_paywall",
                parameters = { parametersOf("onboarding_trial") }
            )
        } else null

    val paywallState = paywallViewModel?.screenState?.collectAsState()?.value

    // Handle successful purchase
    LaunchedEffect(paywallState?.purchaseSuccess) {
        if (paywallState?.purchaseSuccess == true) {
            viewModel.completeOnboarding()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Skip button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.SpacingSm),
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.SpacingSm))
                    .clickable { viewModel.sendIntent(InteractiveOnboardingIntent.OnSkip) }
                    .padding(AppDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_skip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress bar
        OnboardingProgressBar(
            currentStep = state.currentStep.ordinal,
            totalSteps = InteractiveOnboardingStep.entries.size,
            modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal)
        )

        // Step content with animated transitions
        AnimatedContent(
            targetState = state.currentStep,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                if (targetState == InteractiveOnboardingStep.ChecklistPreview) {
                    // Vertical slide-up for aha moment reveal
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                } else if (targetState.ordinal > initialState.ordinal) {
                    // Forward: slide in from right
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    // Back: slide in from left
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "onboarding_step"
        ) { step ->
            when (step) {
                InteractiveOnboardingStep.Welcome -> WelcomeStep(
                    onGetStarted = { viewModel.sendIntent(InteractiveOnboardingIntent.OnGetStarted) }
                )
                InteractiveOnboardingStep.CategorySelection -> CategorySelectionStep(
                    selectedCategory = state.selectedCategory,
                    onCategorySelected = {
                        viewModel.sendIntent(InteractiveOnboardingIntent.OnCategorySelected(it))
                    }
                )
                InteractiveOnboardingStep.ChecklistPreview -> ChecklistPreviewStep(
                    template = state.matchedTemplate,
                    isCreating = state.isCreatingChecklist,
                    onSave = { viewModel.sendIntent(InteractiveOnboardingIntent.OnSaveChecklist) }
                )
                InteractiveOnboardingStep.Paywall -> {
                    com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components.PaywallStep(
                        paywallViewModel = paywallViewModel,
                        onComplete = { viewModel.completeOnboarding() }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val progress = (currentStep + 1).toFloat() / totalSteps.toFloat()

    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}
