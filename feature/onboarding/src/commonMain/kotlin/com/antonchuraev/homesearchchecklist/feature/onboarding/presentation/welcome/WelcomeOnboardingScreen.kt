package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.onboarding_skip
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_capture_lead
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_capture_title_accent
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_capture_title_lead
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_chat_bubble_done
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_chat_bubble_user
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_continue
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_create_cta
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_create_cta_ai
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_create_cta_chip
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_create_error
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_first_hint_empty
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_first_hint_typed
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_first_input_placeholder
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_first_lead
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_first_title_accent
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_first_title_lead
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_more_ways_sub
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_more_ways_title
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_testimonial_author
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_testimonial_quote
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_title_accent
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_title_lead
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_value_lead
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_value_title_accent
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_value_title_lead
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// ---------------------------------------------------------------------------
// Brand colors — hardcoded so Material You dynamic color (API 31+) never shifts
// the AI hero/spark identity (matches FeatureIllustrations.kt). UI chrome (CTA,
// progress, text) stays on MaterialTheme.colorScheme.
// ---------------------------------------------------------------------------
private val BrandBlue = Color(0xFF2196F3)
private val BrandIndigo = Color(0xFF6366F1)
private val BrandPurple = Color(0xFFA855F7)
private val StarGold = Color(0xFFFFC107)

private val BrandGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to BrandBlue, 0.6f to BrandIndigo, 1f to BrandPurple),
)

@Composable
fun WelcomeOnboardingScreen(
    viewModel: WelcomeOnboardingViewModel = koinViewModel(),
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.WELCOME_ONBOARDING) }

    val state by viewModel.screenState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Resolve snackbar string keys in Composable scope (Compose Resources must not be touched in
    // the ViewModel). Currently one message — the create-failure snackbar.
    val createErrorMsg = stringResource(Res.string.onboarding_welcome_create_error)
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is WelcomeOnboardingSideEffect.ShowSnackbar -> {
                    val text = when (effect.messageKey) {
                        WelcomeOnboardingViewModel.ERROR_KEY -> createErrorMsg
                        else -> effect.messageKey
                    }
                    snackbarHostState.showSnackbar(text)
                }
            }
        }
    }

    // Button-driven back (no swipe pager). On the Welcome step there is no previous step, so the
    // handler is disabled there and the OS/browser owns back (exits the app on Android root).
    PlatformBackHandler(enabled = state.currentStep != WelcomeOnboardingStep.Welcome) {
        viewModel.sendIntent(WelcomeOnboardingIntent.OnBack)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            WelcomeTopBar(
                step = state.currentStep,
                onBack = { viewModel.sendIntent(WelcomeOnboardingIntent.OnBack) },
                onSkip = { viewModel.sendIntent(WelcomeOnboardingIntent.OnSkip) },
            )

            WelcomeProgressBar(
                currentStep = state.currentStep.ordinal,
                totalSteps = WelcomeOnboardingStep.entries.size,
                modifier = Modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            )

            AnimatedContent(
                targetState = state.currentStep,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val duration = 300
                    if (forward) {
                        (
                            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(duration, easing = FastOutSlowInEasing))
                        ) togetherWith (
                            slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it } +
                                fadeOut(tween(duration, easing = FastOutSlowInEasing))
                        )
                    } else {
                        (
                            slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it } +
                                fadeIn(tween(duration, easing = FastOutSlowInEasing))
                        ) togetherWith (
                            slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it } +
                                fadeOut(tween(duration, easing = FastOutSlowInEasing))
                        )
                    }
                },
                label = "welcome_onboarding_step",
            ) { step ->
                when (step) {
                    WelcomeOnboardingStep.Welcome -> WelcomeStep()
                    WelcomeOnboardingStep.Capture -> CaptureStep()
                    WelcomeOnboardingStep.Value -> ValueStep()
                    WelcomeOnboardingStep.FirstChecklist -> FirstChecklistStep(
                        inputText = state.inputText,
                        selectedTemplateKey = state.selectedTemplateKey,
                        onInputChanged = {
                            viewModel.sendIntent(WelcomeOnboardingIntent.OnInputChanged(it))
                        },
                        onTemplateSelected = {
                            viewModel.sendIntent(WelcomeOnboardingIntent.OnTemplateSelected(it))
                        },
                        onMoreWays = {
                            viewModel.sendIntent(WelcomeOnboardingIntent.OnMoreWaysToStart)
                        },
                    )
                }
            }

            WelcomeFooter(
                step = state.currentStep,
                isCreating = state.isCreating,
                inputText = state.inputText,
                selectedTemplateKey = state.selectedTemplateKey,
                onPrimary = {
                    if (state.currentStep == WelcomeOnboardingStep.FirstChecklist) {
                        viewModel.sendIntent(WelcomeOnboardingIntent.OnCreateFirstChecklist)
                    } else {
                        viewModel.sendIntent(WelcomeOnboardingIntent.OnNext)
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeTopBar(
    step: WelcomeOnboardingStep,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val showBack = step != WelcomeOnboardingStep.Welcome
    val showSkip = step != WelcomeOnboardingStep.FirstChecklist

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = AppDimens.SpacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        if (showSkip) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.SpacingSm))
                    .clickable(onClick = onSkip)
                    .padding(AppDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_skip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun WelcomeProgressBar(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val activeColor = MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (index <= currentStep) activeColor else inactiveColor),
            )
        }
    }
}

@Composable
private fun WelcomeFooter(
    step: WelcomeOnboardingStep,
    isCreating: Boolean,
    inputText: String,
    selectedTemplateKey: String?,
    onPrimary: () -> Unit,
) {
    // The final-step CTA label adapts to what the user has chosen (chip / typed text / nothing yet);
    // every other step keeps the static "Continue". `.isNotBlank()` mirrors the ViewModel's `.trim()`
    // so the "Create with AI" wording shows exactly when the typed branch will run.
    val ctaState: WelcomeCtaState = when {
        step != WelcomeOnboardingStep.FirstChecklist -> WelcomeCtaState.Continue
        selectedTemplateKey != null -> WelcomeCtaState.CreateChip
        inputText.isNotBlank() -> WelcomeCtaState.CreateAi
        else -> WelcomeCtaState.CreateDefault
    }
    Column(modifier = Modifier.padding(AppDimens.SpacingLg)) {
        // Crossfade the label swap (150ms) so the CTA wording changes smoothly as the user types or
        // picks a chip. `loading` is NOT part of the key — the spinner is owned by AppButton, so an
        // in-flight create never re-triggers the crossfade.
        Crossfade(
            targetState = ctaState,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "welcome_cta_label",
        ) { state ->
            AppButton(
                text = stringResource(state.textRes),
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
                // Spark in the button only on the AI (typed) branch, reinforcing "this goes to AI".
                icon = if (state == WelcomeCtaState.CreateAi) Icons.Filled.AutoAwesome else null,
                // Pill CTA — large shape (16dp) per design spec (default AppButton shape is small/8dp).
                shape = MaterialTheme.shapes.large,
                // Final step CTA stays enabled even with empty input (the default name keeps the
                // mandatory step actionable); only the in-flight spinner swallows taps.
                loading = isCreating,
            )
        }
    }
}

/** The footer CTA's wording variants — drives the [Crossfade] label swap in [WelcomeFooter]. */
private enum class WelcomeCtaState(val textRes: StringResource) {
    Continue(Res.string.onboarding_welcome_continue),
    CreateChip(Res.string.onboarding_welcome_create_cta_chip),
    CreateAi(Res.string.onboarding_welcome_create_cta_ai),
    CreateDefault(Res.string.onboarding_welcome_create_cta),
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeStep() {
    StepScaffold {
        HeroGradientBadge()
        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        TwoToneHeadline(
            leadRes = Res.string.onboarding_welcome_title_lead,
            accentRes = Res.string.onboarding_welcome_title_accent,
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        TestimonialCard()
    }
}

@Composable
private fun CaptureStep() {
    // Reveal the four pieces top-to-bottom, one after another, to draw the eye down the step.
    // Keyed to a fresh remember per composition: AnimatedContent recreates this when re-entering
    // the Capture step, so the stagger replays every time the user lands here. 4 items (user
    // bubble → assistant bubble → headline → lead) — small on purpose to stay calm, not "slop".
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    StepScaffold {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            StaggerItem(visible = revealed, index = 0) {
                ChatBubbleUser()
            }
            StaggerItem(visible = revealed, index = 1) {
                ChatBubbleAssistant()
            }
            StaggerItem(visible = revealed, index = 2) {
                Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
                TwoToneHeadline(
                    leadRes = Res.string.onboarding_welcome_capture_title_lead,
                    accentRes = Res.string.onboarding_welcome_capture_title_accent,
                )
            }
            StaggerItem(visible = revealed, index = 3) {
                Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                LeadText(Res.string.onboarding_welcome_capture_lead)
            }
        }
    }
}

/**
 * One element of a top-down staggered reveal: fades + rises into place after [index] * [stepDelayMs]
 * so siblings appear in sequence. Wraps its [content] in an [AnimatedVisibility]; the parent flips
 * [visible] true once on entry (re-entry recreates the parent, replaying the sequence).
 */
@Composable
private fun StaggerItem(
    visible: Boolean,
    index: Int,
    stepDelayMs: Int = 130,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 280, delayMillis = index * stepDelayMs, easing = FastOutSlowInEasing)) +
            slideInVertically(
                tween(durationMillis = 280, delayMillis = index * stepDelayMs, easing = FastOutSlowInEasing),
            ) { it / 4 },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun ValueStep() {
    StepScaffold {
        GlassIconTile(icon = Icons.Filled.CameraAlt)
        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        TwoToneHeadline(
            leadRes = Res.string.onboarding_welcome_value_title_lead,
            accentRes = Res.string.onboarding_welcome_value_title_accent,
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        LeadText(Res.string.onboarding_welcome_value_lead)
    }
}

@Composable
private fun FirstChecklistStep(
    inputText: String,
    selectedTemplateKey: String?,
    onInputChanged: (String) -> Unit,
    onTemplateSelected: (String) -> Unit,
    onMoreWays: () -> Unit,
) {
    // Scrollable: this step has the text field, so when the IME opens the content must scroll to
    // keep the field + chips reachable above the keyboard (the root Column already imePadding()s).
    StepScaffold(scrollable = true) {
        GlassIconTile(icon = Icons.Filled.AutoAwesome)
        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        TwoToneHeadline(
            leadRes = Res.string.onboarding_welcome_first_title_lead,
            accentRes = Res.string.onboarding_welcome_first_title_accent,
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
        LeadText(Res.string.onboarding_welcome_first_lead)
        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        // The free-text field (and its hint) hide when a starter chip is picked (the chip becomes the
        // unambiguous seed) and reappear when the chip is deselected. Animated height (expand/shrink
        // + fade) so the swap is smooth — no jump in the column's height.
        AnimatedVisibility(
            visible = selectedTemplateKey == null,
            enter = fadeIn(tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                expandVertically(tween(durationMillis = 220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                shrinkVertically(tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        ) {
            // Field + hint + the gap below them animate together so deselecting/selecting never
            // leaves a dangling Spacer above the chips.
            Column(modifier = Modifier.fillMaxWidth()) {
                AppTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.onboarding_welcome_first_input_placeholder),
                    singleLine = true,
                    showClearButton = true,
                    // Spark hints the field feeds AI. Brand tint (not theme) — same AI identity as the
                    // hero/spark marks; AppTextField hardcodes shape=small (8dp), left untouched.
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = BrandIndigo,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                InputHintRow(inputBlank = inputText.isBlank())
                Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
            }
        }
        StarterChipsRow(
            selectedTemplateKey = selectedTemplateKey,
            onTemplateSelected = onTemplateSelected,
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        // Visible in every state (incl. chip-selected): the multimodal entry never depends on the
        // text/chip choice above.
        MultimodalStartCard(onMoreWays = onMoreWays)
    }
}

/**
 * Helper line under the input field. Before the user types it teases concrete example prompts;
 * once they start typing it promises the AI generation. Lives inside the field's [AnimatedVisibility]
 * so it animates in/out together with the field (never a dangling line above the chips).
 */
@Composable
private fun InputHintRow(inputBlank: Boolean) {
    val hintRes = if (inputBlank) {
        Res.string.onboarding_welcome_first_hint_empty
    } else {
        Res.string.onboarding_welcome_first_hint_typed
    }
    Text(
        text = stringResource(hintRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * "More ways to start" affordance — a tappable card that opens the Analyze hub (Photo/PDF/voice/
 * link). The four thumbnails on the left are purely decorative (the whole card is one button); the
 * card is merged into a single Button node for screen readers.
 */
@Composable
private fun MultimodalStartCard(onMoreWays: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onMoreWays)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(AppDimens.SpacingLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        MultimodalThumbnailStack()
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.onboarding_welcome_more_ways_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(AppDimens.SpacingXs))
                // Small spark badge — brand gradient, marks this as the AI-powered entry.
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(BrandGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = stringResource(Res.string.onboarding_welcome_more_ways_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Decorative overlapping stack of four input-type thumbnails (photo / PDF / voice / link). */
@Composable
private fun MultimodalThumbnailStack() {
    val thumbs = listOf(
        Icons.Filled.PhotoCamera to MaterialTheme.colorScheme.primaryContainer,
        Icons.Filled.Description to MaterialTheme.colorScheme.surfaceContainerHighest,
        Icons.Filled.Mic to MaterialTheme.colorScheme.secondaryContainer,
        Icons.Filled.Link to MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    // Negative spacing overlaps each thumb 8dp over its left neighbour (the first sits flush).
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        thumbs.forEach { (icon, bg) ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared step pieces
// ---------------------------------------------------------------------------

/**
 * Common per-step container: centered content, screen padding, top-weighted so headlines sit in
 * the upper third (calm/premium rhythm). The footer CTA lives outside the AnimatedContent.
 *
 * [scrollable] is opt-in (FirstChecklist only): that step carries an input field, so when the IME
 * opens the root Column's [imePadding] shrinks the available height — the content must scroll to
 * keep the field (and chips below it) reachable above the keyboard. The other three steps are short,
 * centered, and never under the keyboard, so they stay non-scrollable to preserve their layout.
 */
@Composable
private fun StepScaffold(
    scrollable: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .padding(top = AppDimens.SpacingXxl)
            // Breathing room below the last element so it isn't flush against the keyboard/footer
            // when the step is scrolled to the bottom.
            .padding(bottom = if (scrollable) AppDimens.SpacingLg else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * Two-tone headline: lead text in onSurface, accent word painted with the AI brand gradient.
 * Gradient is applied as a [SpanStyle.brush] so only the accent run is colored.
 */
@Composable
private fun TwoToneHeadline(
    leadRes: StringResource,
    accentRes: StringResource,
) {
    val lead = stringResource(leadRes)
    val accent = stringResource(accentRes)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
            append(lead)
            if (lead.isNotEmpty() && !lead.endsWith(" ")) append(" ")
        }
        withStyle(SpanStyle(brush = BrandGradient)) {
            append(accent)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LeadText(res: StringResource) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Large rounded hero tile filled with the brand gradient + a soft sparkle mark. */
@Composable
private fun HeroGradientBadge() {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(MaterialTheme.shapes.large)
            .background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        // Soft halo behind the spark
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
    }
}

/** Smaller glassy icon tile (translucent brand wash + hairline) for the Capture/Value/First steps. */
@Composable
private fun GlassIconTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(MaterialTheme.shapes.large)
            .background(BrandBlue.copy(alpha = 0.10f))
            .border(1.dp, BrandIndigo.copy(alpha = 0.20f), MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandIndigo,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** Glassy testimonial card: 5 gold stars + quote + author. Uses theme surface (non-brand). */
@Composable
private fun TestimonialCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = stringResource(Res.string.onboarding_welcome_testimonial_quote),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.onboarding_welcome_testimonial_author),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** User chat bubble (right-aligned, brand fill) — first piece of the Capture preview. */
@Composable
private fun ChatBubbleUser() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp,
                    ),
                )
                .background(BrandBlue)
                .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingSm),
        ) {
            Text(
                text = stringResource(Res.string.onboarding_welcome_chat_bubble_user),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

/** Assistant chat bubble (left-aligned, glassy surface) — second piece of the Capture preview. */
@Composable
private fun ChatBubbleAssistant() {
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 4.dp,
        bottomEnd = 16.dp,
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = BrandIndigo,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(Res.string.onboarding_welcome_chat_bubble_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Tappable starter-template suggestion chips for the final step. */
@Composable
private fun StarterChipsRow(
    selectedTemplateKey: String?,
    onTemplateSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        items(WelcomeStarterTemplate.entries.size) { index ->
            val template = WelcomeStarterTemplate.entries[index]
            val selected = template.key == selectedTemplateKey
            StarterChip(
                label = stringResource(template.labelRes),
                selected = selected,
                onClick = { onTemplateSelected(template.key) },
            )
        }
    }
}

@Composable
private fun StarterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
            )
            .border(1.dp, borderColor, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            // Guarantee a >=48dp touch target (the vertical padding alone can fall short).
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingSm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
