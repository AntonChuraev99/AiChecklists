package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_insufficient_credits
import aichecklists.core.designsystem.generated.resources.chat_mic_hold_to_record
import aichecklists.core.designsystem.generated.resources.chat_recording_failed
import aichecklists.core.designsystem.generated.resources.chat_transcribe_empty
import aichecklists.core.designsystem.generated.resources.chat_transcribe_error
import aichecklists.core.designsystem.generated.resources.chat_voice_too_short
import aichecklists.core.designsystem.generated.resources.onboarding_attach_own_title
import aichecklists.core.designsystem.generated.resources.onboarding_attach_title
import aichecklists.core.designsystem.generated.resources.onboarding_mic_denied
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_example
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_title_accent
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_title_lead
import aichecklists.core.designsystem.generated.resources.onboarding_source_link_caption
import aichecklists.core.designsystem.generated.resources.onboarding_source_pdf_caption
import aichecklists.core.designsystem.generated.resources.onboarding_source_photo_caption
import aichecklists.core.designsystem.generated.resources.onboarding_source_voice_caption
import aichecklists.core.designsystem.generated.resources.onboarding_talk_starters_title
import aichecklists.core.designsystem.generated.resources.onboarding_try_talk_title
import aichecklists.core.designsystem.generated.resources.onboarding_try_type_empty_hint
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_create_error
import aichecklists.core.designsystem.generated.resources.onboarding_write_ideas_title
import aichecklists.core.designsystem.generated.resources.onboarding_write_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_write_subtitle_no_voice
import aichecklists.core.designsystem.generated.resources.onboarding_write_title
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeInputKind
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.VoicePressTerminalGate
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.endVoicePress
import com.antonchuraev.homesearchchecklist.core.filepicker.api.recorder.rememberAudioRecorderLauncher
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceCardGrid
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.AiErrorBox
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.GoalComposer
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.GreetingRow
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.OnboardingTopBar
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SamplePhotoCard
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.ShowcaseTiles
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.StarterBubble
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.TalkComposerPlate
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.awaitFrameMillis
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Route of the `ai_welcome` onboarding. Wires the ViewModel to the stateless [OnboardingContent] and
 * owns the platform recorder for the Write / Talk mic (FEATURE_SPEC 2026-11-17 §4): the gesture host
 * is the ONLY caller of the recorder's `start()` (which owns the permission prompt), and
 * [VoicePressTerminalGate] lets one terminal callback per press reach the ViewModel — the chat's
 * exact press contract.
 *
 * @param isCallAvailable the Ask-pill call button's availability, computed once in App.kt
 *   (platform supports calls && RC `voice_call_enabled`) — FEATURE_SPEC §5.4 of 2026-11-04.
 */
@Composable
fun WelcomeOnboardingScreen(
    isCallAvailable: Boolean = false,
    viewModel: WelcomeOnboardingViewModel = koinViewModel { parametersOf(isCallAvailable) },
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    val logger: AppLogger = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.WELCOME_ONBOARDING) }

    val state by viewModel.screenState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar keys are resolved here: the ViewModel never touches Compose Resources for UI text.
    // rememberUpdatedState: resources resolve asynchronously, and the collector below is launched
    // once — a captured map would freeze the first frame's empty strings.
    val messages by rememberUpdatedState(
        mapOf(
            OnboardingSnackbar.CREATE_ERROR to stringResource(Res.string.onboarding_welcome_create_error),
            OnboardingSnackbar.MIC_DENIED to stringResource(Res.string.onboarding_mic_denied),
            OnboardingSnackbar.MIC_HOLD_AGAIN to stringResource(Res.string.chat_mic_hold_to_record),
            OnboardingSnackbar.RECORDING_FAILED to stringResource(Res.string.chat_recording_failed),
            OnboardingSnackbar.TRANSCRIBE_EMPTY to stringResource(Res.string.chat_transcribe_empty),
            OnboardingSnackbar.TRANSCRIBE_ERROR to stringResource(Res.string.chat_transcribe_error),
            OnboardingSnackbar.NO_CREDITS to stringResource(Res.string.chat_insufficient_credits),
        ),
    )
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is OnboardingSideEffect.ShowSnackbar -> {
                    val text = messages[effect.messageKey]
                    if (text.isNullOrEmpty()) {
                        logger.error(TAG, "no text for snackbar key ${effect.messageKey}", IllegalStateException(effect.messageKey))
                    }
                    snackbarHostState.showSnackbar(text?.ifEmpty { null } ?: messages.getValue(OnboardingSnackbar.CREATE_ERROR))
                }
            }
        }
    }

    // ── Mic (Write / Talk): one recorder, one terminal callback per press ─────
    val pressGate = remember { VoicePressTerminalGate() }
    val recorder = rememberAudioRecorderLauncher(
        onResult = { result ->
            if (pressGate.tryDeliver()) {
                viewModel.sendIntent(
                    OnboardingIntent.OnVoiceRecordingStopped(
                        recordingPath = result?.filePath,
                        mimeType = result?.mimeType ?: "audio/m4a",
                    ),
                )
            } else {
                logger.warning(TAG, "Dropped a second recorder result for one press")
            }
        },
        onError = { errorCode ->
            val stop = voiceStopReasonFor(errorCode)
            // The recorder module has no logger: this is where a recorder failure reaches Crashlytics.
            if (stop == OnboardingVoiceStop.RecorderError) {
                logger.error(TAG, "Recorder failed: $errorCode", IllegalStateException(errorCode))
            } else {
                logger.warning(TAG, "Recording ended without audio: $errorCode")
            }
            if (pressGate.tryDeliver()) {
                viewModel.sendIntent(OnboardingIntent.OnVoiceRecordingStopped(recordingPath = null, stop = stop))
            }
        },
    )

    OnboardingContent(
        state = state,
        onIntent = viewModel::sendIntent,
        snackbarHostState = snackbarHostState,
        // Only a user's press starts the mic (and with it the permission prompt) — never screen entry.
        onVoicePressStart = {
            pressGate.onPressStarted()
            viewModel.sendIntent(OnboardingIntent.OnVoiceRecordingStarted)
            recorder.start()
        },
        onVoicePressEnd = { cancel ->
            endVoicePress(pressGate, recorder, cancel) {
                viewModel.sendIntent(OnboardingIntent.OnVoicePressReleasedBeforeStart)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout (DESIGN_SPEC docs/designs/onboarding-steps-2026-11-17, variant A): Showcase → one screen per
// choice. Stateless over OnboardingUiState; the screenshot matrix runs over OnboardingPreviewFixtures.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The whole onboarding surface, stateless. Picks the surface from [OnboardingUiState.phase]:
 * sample overlay, AI loader, or the step chrome. Owns back handling: ← / system back on a branch
 * screen goes to Showcase; while busy back is swallowed; on Showcase the OS owns back.
 *
 * `Leaving(Sample)` / `Leaving(Typed)` keep their overlay on screen (same call site, so the overlay's
 * animation state survives) — the chrome must not flash back for the frame before navigation.
 *
 * @param onVoicePressStart finger down on a composer mic (the Route starts the recorder).
 * @param onVoicePressEnd finger up; `true` = cancelled (dragged off), `false` = stop and transcribe.
 */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onVoicePressStart: () -> Unit = {},
    onVoicePressEnd: (cancel: Boolean) -> Unit = {},
) {
    PlatformBackHandler(enabled = state.step != OnboardingStep.Showcase || state.isBusy) {
        if (!state.isBusy) onIntent(OnboardingIntent.OnBack)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Width bucket comes from the app-wide AppWindowSizeClass (the shell's source of truth) so
        // onboarding and shell never pick different layouts at an in-between width; only the
        // height check — which AppWindowSizeClass does not model — is local.
        val window = OnboardingWindow.of(rememberAppWindowSizeClass(), maxHeight)
        val phase = state.phase
        when {
            phase is OnboardingPhase.SampleRunning || phase == OnboardingPhase.Leaving(OnboardingSeed.Sample) ->
                SampleAnalyzingOverlay(
                    stage = (phase as? OnboardingPhase.SampleRunning)?.stage ?: SampleStage.Ready,
                    onFinished = { onIntent(OnboardingIntent.OnSampleAnimationFinished) },
                )
            phase is OnboardingPhase.AiGenerating || phase == OnboardingPhase.Leaving(OnboardingSeed.Typed) ->
                AiGeneratingLoader(
                    prompt = (phase as? OnboardingPhase.AiGenerating)?.prompt ?: state.inputText,
                    isSlow = (phase as? OnboardingPhase.AiGenerating)?.isSlow ?: false,
                )
            else -> OnboardingChrome(state, onIntent, window, onVoicePressStart, onVoicePressEnd)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
        )
    }
}

/**
 * Window buckets of DESIGN_SPEC §7. Width buckets are [AppWindowSizeClass] (no local width
 * breakpoints); [heightCompact] is onboarding's own short/landscape-window check.
 */
internal data class OnboardingWindow(val expanded: Boolean, val medium: Boolean, val heightCompact: Boolean) {
    /** Max width of the content column. */
    val contentMaxWidth: Dp get() = when {
        expanded -> ExpandedContentWidth
        medium -> MediumContentWidth
        else -> Dp.Infinity
    }

    companion object {
        fun of(sizeClass: AppWindowSizeClass, height: Dp) = OnboardingWindow(
            expanded = sizeClass == AppWindowSizeClass.Expanded,
            medium = sizeClass != AppWindowSizeClass.Compact,
            heightCompact = height < HeightCompactBreakpoint,
        )
    }
}

@Composable
private fun OnboardingChrome(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    window: OnboardingWindow,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: (cancel: Boolean) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The BOTTOM nav-bar inset is left to each step (below): Talk's composer plate paints its
    // bottom-chrome band under the nav bar and pads itself inside it — the system-nav zone reads as
    // one surface with the chat dock. The side (landscape) nav bar is cleared here for everything.
    // imePadding() consumes the keyboard, so a step's own navigationBarsPadding() collapses to
    // max(0, nav − ime) = 0 while it is up: no nav-bar-height gap above the keyboard.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .imePadding(),
    ) {
        OnboardingTopBar(
            stepIndex = state.step.index,
            stepCount = OnboardingStep.count,
            showBack = state.step != OnboardingStep.Showcase,
            enabled = !state.isBusy,
            onBack = { onIntent(OnboardingIntent.OnBack) },
            onSkip = { onIntent(OnboardingIntent.OnSkip) },
        )
        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(AppMotion.ReducedMotionMillis)) togetherWith fadeOut(tween(AppMotion.ReducedMotionMillis))
                } else {
                    // Showcase → branch is forward; branch → Showcase is back (branches never swap).
                    val forward = targetState.index > initialState.index
                    val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                    (slideInHorizontally(AppMotion.spatialDefaultTween()) { sign * it / 4 } + fadeIn(AppMotion.effectsDefaultTween())) togetherWith
                        (slideOutHorizontally(AppMotion.spatialDefaultTween()) { -sign * it / 4 } + fadeOut(AppMotion.effectsDefaultTween()))
                }
            },
            label = "onboardingStep",
        ) { step ->
            val settled = transition.currentState == transition.targetState
            // Talk owns its bottom inset (TalkComposerPlate); every other step clears the nav bar here.
            Box(if (step == OnboardingStep.Talk) Modifier.fillMaxSize() else Modifier.fillMaxSize().navigationBarsPadding()) {
                when (step) {
                    OnboardingStep.Showcase -> ShowcaseStep(state, onIntent, window)
                    OnboardingStep.Write -> WriteStep(
                        state, onIntent, window,
                        settled = settled,
                        onVoicePressStart = onVoicePressStart,
                        onVoicePressEnd = onVoicePressEnd,
                    )
                    OnboardingStep.Attach -> AttachStep(state, onIntent, window)
                    OnboardingStep.Talk -> TalkStep(state, onIntent, window, onVoicePressStart, onVoicePressEnd)
                }
            }
        }
    }
}

// ── Step 1: Showcase ─────────────────────────────────────────────────────────

@Composable
private fun ShowcaseStep(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit, window: OnboardingWindow) {
    val enabled = !state.isBusy
    val showStages = LocalDensity.current.fontScale <= LADDER_FONT_SCALE && !window.heightCompact
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // heightIn(min = viewport) under an unbounded scroll: the weighted spacers split the
            // FREE space 1 : 2 when the content fits and collapse to 0 when it does not.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewport)
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal, vertical = AppDimens.SpacingSm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.widthIn(max = window.contentMaxWidth).fillMaxWidth()) {
                    TwoToneHeadline(
                        lead = stringResource(Res.string.onboarding_showcase_title_lead),
                        accent = stringResource(Res.string.onboarding_showcase_title_accent),
                        style = if (window.expanded) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                    )
                    Spacer(Modifier.height(AppDimens.SpacingSm))
                    Text(
                        text = stringResource(Res.string.onboarding_showcase_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AppDimens.SpacingXl))
                    ShowcaseTiles(
                        isCallAvailable = state.isCallAvailable,
                        showStages = showStages,
                        expanded = window.expanded,
                        enabled = enabled,
                        onTileClick = { onIntent(OnboardingIntent.OnShowcaseTileClick(it)) },
                    )
                    Spacer(Modifier.height(AppDimens.SpacingSm))
                    // The zero-decision path: the sample straight from step 1 (no AI, works offline).
                    // The AutoAwesome icon of DESIGN_SPEC §1 needs an icon slot AppButtonText lacks.
                    AppButtonText(
                        text = stringResource(Res.string.onboarding_showcase_example),
                        onClick = { onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Showcase)) },
                        enabled = enabled,
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = AppDimens.MinTouchTarget),
                    )
                }
                Spacer(Modifier.weight(2f))
            }
        }
    }
}

/**
 * "Turn anything into **a checklist**": ONE text node with `heading()`, the accent a brush span.
 * Spaces inside the accent become NBSP so it wraps as one unit (no orphaned "a").
 */
@Composable
private fun TwoToneHeadline(lead: String, accent: String, style: TextStyle) {
    val brush = GistiColors.aiHeadlineBrush
    val text = remember(lead, accent, brush) {
        buildAnnotatedString {
            append(lead)
            append(' ')
            withStyle(SpanStyle(brush = brush)) { append(accent.replace(' ', NBSP)) }
        }
    }
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

// ── Step 2: Write / Attach / Talk (DESIGN_SPEC 2026-11-17 §2) ─────────────────

/**
 * The scrolling column of a branch screen: top-aligned (the headline sits in the same place on all
 * three), capped at the window's content width and centred. [content] receives no horizontal padding
 * so an edge-bleed row (the idea chips) can reach the screen edges; everything else pads itself.
 */
@Composable
private fun BranchColumn(
    window: OnboardingWindow,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    maxWidth: Dp = window.contentMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = if (window.expanded) AppDimens.SpacingXxl else AppDimens.SpacingSm, bottom = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
    }
}

@Composable
private fun BranchTitle(text: String, window: OnboardingWindow, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = if (window.expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
            .semantics { heading() },
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Write (§2.1): a hero composer card, the AI-error box under it, and the idea chips. Autofocus on
 * entry — only once the step transition has settled ([settled]), so the IME never resizes the window
 * mid-slide, and never on a short (landscape) window where the IME would cover everything.
 */
@Composable
private fun WriteStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    window: OnboardingWindow,
    settled: Boolean,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: (cancel: Boolean) -> Unit,
) {
    val enabled = !state.isBusy
    val fieldFocus = remember { FocusRequester() }
    val pad = AppDimens.ScreenPaddingHorizontal
    val reduced = LocalReducedMotion.current
    val voiceRefocus = rememberVoiceRefocus(state.phase, fieldFocus, allowed = !window.heightCompact)
    var autofocused by remember { mutableStateOf(false) }
    LaunchedEffect(settled) {
        if (settled && !autofocused && !window.heightCompact && state.phase == OnboardingPhase.Idle) {
            autofocused = true
            fieldFocus.requestFocus()
        }
    }
    BranchColumn(window, maxWidth = if (window.expanded) ExpandedWriteWidth else window.contentMaxWidth) {
        BranchTitle(stringResource(Res.string.onboarding_write_title), window)
        Spacer(Modifier.height(AppDimens.SpacingXs))
        Text(
            // No mic (web without Google): the subtitle must not promise one.
            text = stringResource(
                if (state.isVoiceAvailable) Res.string.onboarding_write_subtitle else Res.string.onboarding_write_subtitle_no_voice,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = pad),
        )
        Spacer(Modifier.height(AppDimens.SpacingLg))
        GoalComposer(
            text = state.inputText,
            onTextChange = { onIntent(OnboardingIntent.OnInputChanged(it)) },
            enabled = enabled,
            isRecording = state.phase == OnboardingPhase.Recording(OnboardingStep.Write),
            isTranscribing = state.phase == OnboardingPhase.Transcribing(OnboardingStep.Write),
            showMic = state.isVoiceAvailable,
            focusRequester = fieldFocus,
            onSend = {
                if (state.inputText.isBlank()) fieldFocus.requestFocus()
                onIntent(OnboardingIntent.OnSendClick)
            },
            onVoicePressStart = {
                voiceRefocus.onPressStart()
                onVoicePressStart()
            },
            onVoicePressEnd = onVoicePressEnd,
            onFocusChanged = voiceRefocus::onFocusChanged,
            modifier = Modifier.padding(horizontal = pad),
        )
        ComposerHint(state, Modifier.padding(horizontal = pad))
        state.aiError?.let { error ->
            Spacer(Modifier.height(AppDimens.SpacingSm))
            AiErrorBox(
                error = error,
                enabled = enabled,
                onSample = { onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.AiError)) },
                onSave = { onIntent(OnboardingIntent.OnSavePlainListClick) },
                modifier = Modifier.padding(horizontal = pad),
            )
        }
        // The ideas hide while the field has text and come back with the error box (a chip is a way out).
        AnimatedVisibility(
            visible = state.inputText.isBlank() || state.aiError != null,
            enter = expandVertically(if (reduced) snap() else AppMotion.spatialFastTween()) +
                fadeIn(if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()),
            exit = shrinkVertically(if (reduced) snap() else AppMotion.spatialFastTween()) +
                fadeOut(if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()),
        ) {
            Column {
                Spacer(Modifier.height(AppDimens.SpacingXl))
                SectionTitle(stringResource(Res.string.onboarding_write_ideas_title), Modifier.padding(horizontal = pad))
                Spacer(Modifier.height(AppDimens.SpacingXs))
                val chips = WelcomeStarterTemplate.entries.map { template ->
                    GistiPromptChip(emoji = template.emoji(), label = stringResource(template.labelRes), action = template)
                }
                GistiPromptChips(
                    chips = chips,
                    onChipClick = { if (enabled) onIntent(OnboardingIntent.OnStarterChipClick(it)) },
                    // Edge-bleed row: the padding is the row's own content padding (its KDoc).
                    contentPadding = PaddingValues(horizontal = pad),
                )
            }
        }
    }
}

/** The one-line hints under a composer: empty send, too-short recording. Polite live region. */
@Composable
private fun ComposerHint(state: OnboardingUiState, modifier: Modifier = Modifier) {
    val hint = when {
        state.showVoiceTooShortHint -> stringResource(Res.string.chat_voice_too_short)
        state.showEmptyInputHint -> stringResource(Res.string.onboarding_try_type_empty_hint)
        else -> null
    } ?: return
    Text(
        text = hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = AppDimens.SpacingLg, top = AppDimens.SpacingXs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Attach (§2.2): the real photo sample and the four source cards. Expanded: the photo card left, "Or
 * use your own" and the cards (horizontal shape) right, 5 : 6, top-aligned.
 */
@Composable
private fun AttachStep(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit, window: OnboardingWindow) {
    val enabled = !state.isBusy
    val pad = AppDimens.ScreenPaddingHorizontal
    val spinner = rememberDelayedCreatingSpinner(state)
    val compact = LocalDensity.current.fontScale > LADDER_FONT_SCALE
    val captionPhoto = stringResource(Res.string.onboarding_source_photo_caption)
    val captionPdf = stringResource(Res.string.onboarding_source_pdf_caption)
    val captionLink = stringResource(Res.string.onboarding_source_link_caption)
    val captionVoice = stringResource(Res.string.onboarding_source_voice_caption)
    val captions: (AnalyzeInputKind) -> String = { kind ->
        when (kind) {
            AnalyzeInputKind.PHOTO -> captionPhoto
            AnalyzeInputKind.PDF -> captionPdf
            AnalyzeInputKind.WEB_LINK -> captionLink
            AnalyzeInputKind.VOICE -> captionVoice
            AnalyzeInputKind.RAW_TEXT -> ""
        }
    }
    val photoCard: @Composable (Modifier) -> Unit = { modifier ->
        SamplePhotoCard(
            compact = compact,
            enabled = enabled,
            loading = spinner,
            onTry = { onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card)) },
            modifier = modifier.testTag("onboarding_attach_sample"),
        )
    }
    val ownFiles: @Composable (Modifier, Boolean) -> Unit = { modifier, horizontal ->
        Column(modifier) {
            SectionTitle(stringResource(Res.string.onboarding_attach_own_title))
            Spacer(Modifier.height(AppDimens.SpacingSm))
            SourceCardGrid(
                captions = captions,
                onSelect = { kind -> onIntent(OnboardingIntent.OnOwnSourceClick(kind)) },
                horizontal = horizontal,
                enabled = enabled,
            )
        }
    }
    BranchColumn(window) {
        BranchTitle(stringResource(Res.string.onboarding_attach_title), window)
        Spacer(Modifier.height(AppDimens.SpacingLg))
        // Two panes on expanded AND on a wide-but-short (landscape) window: one column there puts a
        // 400dp-tall photo between the headline and its own CTA.
        if (window.expanded || (window.medium && window.heightCompact)) {
            Row(
                modifier = Modifier.padding(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXl),
                verticalAlignment = Alignment.Top,
            ) {
                photoCard(Modifier.weight(ATTACH_PHOTO_WEIGHT))
                ownFiles(Modifier.weight(ATTACH_FILES_WEIGHT), true)
            }
        } else {
            photoCard(Modifier.padding(horizontal = pad))
            Spacer(Modifier.height(AppDimens.SpacingXl))
            ownFiles(Modifier.padding(horizontal = pad), false)
        }
    }
}

/**
 * Talk (§2.3): Gisti's greeting, the "Try asking" starters, and the chat's own composer docked at
 * the bottom (NOT inside the scroll). With the IME up the feed scrolls to its end, so the starters sit
 * right above the composer.
 */
@Composable
private fun TalkStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    window: OnboardingWindow,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: (cancel: Boolean) -> Unit,
) {
    val enabled = !state.isBusy
    val pad = AppDimens.ScreenPaddingHorizontal
    val reduced = LocalReducedMotion.current
    val scroll = rememberScrollState()
    val fieldFocus = remember { FocusRequester() }
    val voiceRefocus = rememberVoiceRefocus(state.phase, fieldFocus, allowed = !window.heightCompact)
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(scroll, ime, density) {
        // Read in a snapshotFlow, never in composition: the inset changes every IME frame.
        snapshotFlow { ime.getBottom(density) > 0 }
            .distinctUntilChanged()
            .collect { open -> if (open) scroll.animateScrollTo(scroll.maxValue) }
    }
    // Both hint strings promise the mic: without it (web, no Google) the composer speaks for itself.
    val showHint = density.fontScale <= LADDER_FONT_SCALE && state.isVoiceAvailable
    Column(Modifier.fillMaxSize()) {
        BranchColumn(window, modifier = Modifier.weight(1f), scrollState = scroll, maxWidth = MediumContentWidth) {
            BranchTitle(stringResource(Res.string.onboarding_try_talk_title), window)
            Spacer(Modifier.height(AppDimens.SpacingLg))
            GreetingRow(
                isCallAvailable = state.isCallAvailable,
                showHint = showHint,
                modifier = Modifier.padding(horizontal = pad),
            )
            AnimatedVisibility(
                visible = state.inputText.isBlank(),
                enter = expandVertically(if (reduced) snap() else AppMotion.spatialFastTween()) +
                    fadeIn(if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()),
                exit = shrinkVertically(if (reduced) snap() else AppMotion.spatialFastTween()) +
                    fadeOut(if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = pad + TalkStarterIndent, end = pad),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXs),
                ) {
                    Spacer(Modifier.height(AppDimens.SpacingMd))
                    Text(
                        text = stringResource(Res.string.onboarding_talk_starters_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                    OnboardingTalkStarter.entries.forEach { starter ->
                        StarterBubble(
                            text = stringResource(starter.textRes),
                            enabled = enabled,
                            onClick = { onIntent(OnboardingIntent.OnTalkStarterClick(starter)) },
                            modifier = Modifier.testTag("onboarding_talk_starter_${starter.key}"),
                        )
                    }
                }
            }
        }
        TalkComposerPlate(
            text = state.inputText,
            onTextChange = { onIntent(OnboardingIntent.OnInputChanged(it)) },
            enabled = enabled,
            isRecording = state.phase == OnboardingPhase.Recording(OnboardingStep.Talk),
            isTranscribing = state.phase == OnboardingPhase.Transcribing(OnboardingStep.Talk),
            showMic = state.isVoiceAvailable,
            onSend = { onIntent(OnboardingIntent.OnChatClick) },
            onCall = if (state.isCallAvailable) {
                { onIntent(OnboardingIntent.OnCallClick) }
            } else {
                null
            },
            onVoicePressStart = {
                voiceRefocus.onPressStart()
                onVoicePressStart()
            },
            onVoicePressEnd = onVoicePressEnd,
            focusRequester = fieldFocus,
            onFocusChanged = voiceRefocus::onFocusChanged,
            maxPillWidth = if (window.expanded) ExpandedTalkPillWidth else Dp.Infinity,
        )
    }
}

/**
 * Gives the composer its focus back after a dictation (DESIGN_SPEC §4/§5: "mic denied → the field
 * keeps focus, type instead"). Recording disables the field, so the IME closes and focus is lost;
 * when the phase returns to Idle after Recording / Transcribing — permission denied, cancel, too
 * short, recorder error, or a transcript landing in the field — the field is focused again, but only
 * if it HAD focus when the press started. [allowed] false (short landscape window, where Write does
 * not autofocus either) keeps the old behaviour.
 */
private class VoiceRefocus {
    var focused = false
    var refocus = false
    var sawVoicePhase = false

    fun onFocusChanged(isFocused: Boolean) {
        focused = isFocused
    }

    fun onPressStart() {
        refocus = focused
    }
}

@Composable
private fun rememberVoiceRefocus(phase: OnboardingPhase, focusRequester: FocusRequester, allowed: Boolean): VoiceRefocus {
    val holder = remember { VoiceRefocus() }
    LaunchedEffect(phase) {
        when {
            phase is OnboardingPhase.Recording || phase is OnboardingPhase.Transcribing -> holder.sawVoicePhase = true
            phase == OnboardingPhase.Idle && holder.sawVoicePhase -> {
                holder.sawVoicePhase = false
                if (holder.refocus && allowed) focusRequester.requestFocus()
                holder.refocus = false
            }
            // Any other phase (leaving, AI, sample) ends the dictation without a way back to typing.
            phase != OnboardingPhase.Idle -> {
                holder.sawVoicePhase = false
                holder.refocus = false
            }
        }
    }
    return holder
}

private fun WelcomeStarterTemplate.emoji(): String = when (this) {
    WelcomeStarterTemplate.GROCERIES -> "🛒"
    WelcomeStarterTemplate.TRIP -> "🧳"
    WelcomeStarterTemplate.WORKOUT -> "🏋️"
    WelcomeStarterTemplate.TODO -> "✅"
}

/** Chip / plain-list write: a spinner only if it takes > 300 ms (11-04 DESIGN_SPEC §2). */
@Composable
private fun rememberDelayedCreatingSpinner(state: OnboardingUiState): Boolean {
    var showSpinner by remember { mutableStateOf(false) }
    val creating = state.phase is OnboardingPhase.Creating
    LaunchedEffect(creating) {
        showSpinner = false
        if (creating) {
            awaitFrameMillis(SPINNER_DELAY_MS)
            showSpinner = true
        }
    }
    return showSpinner
}

private const val TAG = "WelcomeOnboarding"
private const val NBSP = ' '

/** fontScale above which the stages become discs and the sample card stacks (§9 ladder). */
private const val LADDER_FONT_SCALE = 1.3f
private const val SPINNER_DELAY_MS = 300L

private val HeightCompactBreakpoint = 480.dp
private val MediumContentWidth = 560.dp
private val ExpandedContentWidth = 1040.dp

/** Expanded Write: a narrower column than Attach's two panes (DESIGN_SPEC §7). */
private val ExpandedWriteWidth = 640.dp

/** Expanded Talk: the pill inside the full-width plate (DESIGN_SPEC §7). */
private val ExpandedTalkPillWidth = 640.dp

/** Talk starters start this far in, so they read as the user's side of the conversation. */
private val TalkStarterIndent = 48.dp

private const val ATTACH_PHOTO_WEIGHT = 5f
private const val ATTACH_FILES_WEIGHT = 6f
