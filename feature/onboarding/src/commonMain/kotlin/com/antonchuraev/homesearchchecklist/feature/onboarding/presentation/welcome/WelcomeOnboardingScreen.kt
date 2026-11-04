package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_cta
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_subtitle
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_title_accent
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_title_lead
import aichecklists.core.designsystem.generated.resources.onboarding_try_own_file_title
import aichecklists.core.designsystem.generated.resources.onboarding_try_title
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_create_error
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.AppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.adaptive.rememberAppWindowSizeClass
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRowSection
import com.antonchuraev.homesearchchecklist.desingsystem.components.SourceRowSectionStyle
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegmentShape
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.OnboardingTopBar
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SampleCard
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SampleCardLayout
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.ShowcaseTiles
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.TalkCard
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.TypeBlock
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.highlightRing
import kotlinx.coroutines.coroutineScope
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.awaitFrameMillis
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Route of the `ai_welcome` onboarding. Wires the ViewModel to the stateless [OnboardingContent].
 *
 * @param isCallAvailable the Ask-pill call button's availability, computed once in App.kt
 *   (platform supports calls && RC `voice_call_enabled`) — FEATURE_SPEC §5.4.
 */
@Composable
fun WelcomeOnboardingScreen(
    isCallAvailable: Boolean = false,
    viewModel: WelcomeOnboardingViewModel = koinViewModel { parametersOf(isCallAvailable) },
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.WELCOME_ONBOARDING) }

    val state by viewModel.screenState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar keys are resolved here: the ViewModel never touches Compose Resources for UI text.
    val createErrorMsg = stringResource(Res.string.onboarding_welcome_create_error)
    LaunchedEffect(viewModel) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                // One message today (WelcomeOnboardingViewModel.ERROR_KEY): the create/complete failure.
                is OnboardingSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(createErrorMsg)
            }
        }
    }

    OnboardingContent(
        state = state,
        onIntent = viewModel::sendIntent,
        snackbarHostState = snackbarHostState,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout (DESIGN_SPEC docs/designs/onboarding-refresh-2026-11-04). Stateless over OnboardingUiState;
// previews / screenshot matrix run over OnboardingPreviewFixtures.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The whole onboarding surface, stateless. Picks the surface from [OnboardingUiState.phase]:
 * sample overlay, AI loader, or the two-step chrome. Owns back handling: ← / system back on Try it
 * goes to Showcase; while busy back is swallowed; on Showcase the OS owns back.
 *
 * `Leaving(Sample)` / `Leaving(Typed)` keep their overlay on screen (same call site, so the overlay's
 * animation state survives) — the chrome must not flash back for the frame before navigation.
 */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = state.step == OnboardingStep.TryIt || state.isBusy) {
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
            else -> OnboardingChrome(state, onIntent, window)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
        )
    }
}

/**
 * Window buckets of DESIGN_SPEC §9. Width buckets are [AppWindowSizeClass] (no local width
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
private fun OnboardingChrome(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit, window: OnboardingWindow) {
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        OnboardingTopBar(
            stepIndex = state.step.index,
            stepCount = OnboardingStep.count,
            showBack = state.step == OnboardingStep.TryIt,
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
                    val forward = targetState.index > initialState.index
                    val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                    (slideInHorizontally(AppMotion.spatialDefaultTween()) { sign * it / 4 } + fadeIn(AppMotion.effectsDefaultTween())) togetherWith
                        (slideOutHorizontally(AppMotion.spatialDefaultTween()) { -sign * it / 4 } + fadeOut(AppMotion.effectsDefaultTween()))
                }
            },
            label = "onboardingStep",
        ) { step ->
            when (step) {
                OnboardingStep.Showcase -> ShowcaseStep(state, onIntent, window)
                OnboardingStep.TryIt -> TryItStep(state, onIntent, window)
            }
        }
    }
}

// ── Step 1: Showcase ─────────────────────────────────────────────────────────

@Composable
private fun ShowcaseStep(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit, window: OnboardingWindow) {
    val enabled = !state.isBusy
    val showStages = LocalDensity.current.fontScale <= LADDER_FONT_SCALE && !window.heightCompact
    val cta = stringResource(Res.string.onboarding_showcase_cta)
    val onCta = { onIntent(OnboardingIntent.OnShowcaseCtaClick) }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // heightIn(min = viewport) under an unbounded scroll: the weighted spacers split the
                // FREE space 1 : 2 when the content fits and collapse to 0 when it does not (§1.2).
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
                        if (window.expanded) {
                            // Expanded: the CTA lives in the content, centred, no dock (§9).
                            Spacer(Modifier.height(AppDimens.SpacingXxl))
                            AppButton(
                                text = cta,
                                onClick = onCta,
                                enabled = enabled,
                                modifier = Modifier.align(Alignment.CenterHorizontally).width(ExpandedCtaWidth),
                            )
                        }
                    }
                    Spacer(Modifier.weight(2f))
                }
            }
        }
        if (!window.expanded) {
            // The pinned dock: content scrolls ABOVE it, never under it (fontScale 2.0 RU).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppDimens.SpacingLg, end = AppDimens.SpacingLg, top = AppDimens.SpacingMd, bottom = AppDimens.SpacingLg),
                contentAlignment = Alignment.Center,
            ) {
                AppButton(
                    text = cta,
                    onClick = onCta,
                    enabled = enabled,
                    modifier = Modifier.widthIn(max = window.contentMaxWidth).fillMaxWidth(),
                )
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

// ── Step 2: Try it ───────────────────────────────────────────────────────────

private enum class TryBlock { Sample, OwnFile, Type, Talk }

@Composable
private fun TryItStep(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit, window: OnboardingWindow) {
    val enabled = !state.isBusy
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val intent by rememberUpdatedState(onIntent)
    val scroll = rememberScrollState()
    val fieldFocus = remember { FocusRequester() }
    val talkFocus = remember { FocusRequester() }
    // Layout coordinates of the scroll content and of each block: positions are read on demand
    // (localPositionOf), never stored as numbers that would lag a layout pass behind.
    val anchors = remember { BlockAnchors() }
    // Ring alphas: plain float states, read in the draw phase only.
    val ringSample = remember { mutableFloatStateOf(0f) }
    val ringOwnFile = remember { mutableFloatStateOf(0f) }
    val ringTalk = remember { mutableFloatStateOf(0f) }

    // ── Mode highlight (§2, §7): scroll → focus → ring 1.2 s, then report it consumed. The local
    // copy outlives the ViewModel clearing pendingFocus (reduced motion reports it at once).
    var ringMode by remember { mutableStateOf<OnboardingMode?>(null) }
    LaunchedEffect(state.pendingFocus) {
        when (val mode = state.pendingFocus) {
            null -> Unit
            OnboardingMode.Default -> intent(OnboardingIntent.OnModeFocusConsumed)
            else -> ringMode = mode
        }
    }
    LaunchedEffect(ringMode) {
        val mode = ringMode ?: return@LaunchedEffect
        var consumed = false
        val consume = {
            if (!consumed) {
                consumed = true
                intent(OnboardingIntent.OnModeFocusConsumed)
            }
        }
        try {
            withFrameNanos { } // the step's first layout pass, so the anchors exist
            val margin = with(density) { AppDimens.SpacingLg.roundToPx() }
            val target = when (mode) {
                OnboardingMode.Write -> anchors.top(TryBlock.Type)
                OnboardingMode.Talk -> anchors.bottom(TryBlock.Talk)?.let { it + margin - scroll.viewportSize }
                else -> 0
            }?.coerceIn(0, scroll.maxValue) ?: 0
            scroll.scrollToBlock(target, reduced)
            when (mode) {
                OnboardingMode.Write -> fieldFocus.requestFocus()
                OnboardingMode.Talk -> talkFocus.requestFocus()
                else -> Unit
            }
            if (reduced) consume()
            val rings = when (mode) {
                OnboardingMode.Attach -> listOf(ringSample, ringOwnFile)
                OnboardingMode.Talk -> listOf(ringTalk)
                else -> emptyList() // Write: the field's own focused border is the ring.
            }
            if (rings.isNotEmpty()) {
                coroutineScope {
                    rings.forEach { ring ->
                        launch {
                            ring.fadeTo(1f, if (reduced) null else AppMotion.effectsDefault)
                            awaitFrameMillis(RING_HOLD_MS)
                            ring.fadeTo(0f, if (reduced) null else AppMotion.effectsSlow)
                        }
                    }
                }
            }
        } finally {
            consume()
            ringMode = null
        }
    }

    // AI failure: back on Try it, text kept, scroll at block C (§2).
    LaunchedEffect(state.aiError) {
        if (state.aiError == null) return@LaunchedEffect
        withFrameNanos { }
        anchors.top(TryBlock.Type)?.let { scroll.scrollToBlock(it.coerceIn(0, scroll.maxValue), reduced) }
    }

    // Chip / plain-list write: a spinner only if it takes > 300 ms (§2).
    var showSpinner by remember { mutableStateOf(false) }
    val creating = state.phase is OnboardingPhase.Creating
    LaunchedEffect(creating) {
        showSpinner = false
        if (creating) {
            awaitFrameMillis(SPINNER_DELAY_MS)
            showSpinner = true
        }
    }

    val ringColor = MaterialTheme.colorScheme.primary
    val cardShape: Shape = groupSegmentShape(GroupPosition.Single)
    val onSend = {
        if (state.inputText.isBlank()) fieldFocus.requestFocus()
        onIntent(OnboardingIntent.OnSendClick)
    }
    val pad = AppDimens.ScreenPaddingHorizontal
    val fontScaleLarge = LocalDensity.current.fontScale > LADDER_FONT_SCALE

    val sample = @Composable { layout: SampleCardLayout, mod: Modifier ->
        SampleCard(
            layout = layout,
            enabled = enabled,
            loading = showSpinner,
            onTry = { onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.Card)) },
            modifier = mod
                .anchor(anchors, TryBlock.Sample)
                .highlightRing(ringColor, cardShape) { ringSample.floatValue },
        )
    }
    val ownFile = @Composable { mod: Modifier ->
        SourceRowSection(
            title = stringResource(Res.string.onboarding_try_own_file_title),
            onSelect = { kind -> onIntent(OnboardingIntent.OnOwnSourceClick(kind)) },
            style = SourceRowSectionStyle.Page,
            modifier = mod
                .anchor(anchors, TryBlock.OwnFile)
                .highlightRing(ringColor, AppShapes.medium, outset = AppDimens.SpacingSm) { ringOwnFile.floatValue },
        )
    }
    val type = @Composable { innerPad: Dp ->
        TypeBlock(
            text = state.inputText,
            onTextChange = { onIntent(OnboardingIntent.OnInputChanged(it)) },
            showEmptyHint = state.showEmptyInputHint,
            aiError = state.aiError,
            enabled = enabled,
            fieldFocus = fieldFocus,
            horizontalPadding = innerPad,
            onSend = onSend,
            onErrorSample = { onIntent(OnboardingIntent.OnSampleClick(SampleOrigin.AiError)) },
            onErrorSave = { onIntent(OnboardingIntent.OnSavePlainListClick) },
            onChip = { onIntent(OnboardingIntent.OnStarterChipClick(it)) },
            modifier = Modifier.anchor(anchors, TryBlock.Type),
        )
    }
    val talk = @Composable { mod: Modifier ->
        TalkCard(
            isCallAvailable = state.isCallAvailable,
            hasTypedText = state.inputText.isNotBlank(),
            enabled = enabled,
            titleFocus = talkFocus,
            onChat = { onIntent(OnboardingIntent.OnChatClick) },
            onCall = { onIntent(OnboardingIntent.OnCallClick) },
            modifier = mod
                .anchor(anchors, TryBlock.Talk)
                .highlightRing(ringColor, cardShape) { ringTalk.floatValue },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .onPlaced { anchors.content = it }
            .padding(top = AppDimens.SpacingSm, bottom = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = window.contentMaxWidth).fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.onboarding_try_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = pad).semantics { heading() },
            )
            Spacer(Modifier.height(AppDimens.SpacingLg))
            if (window.expanded) {
                // 5 : 7 grid: the large sample on the left, own file / type / talk stacked right (§9).
                Row(
                    modifier = Modifier.padding(horizontal = pad),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingXl),
                ) {
                    sample(SampleCardLayout.Large, Modifier.weight(SAMPLE_WEIGHT))
                    Column(Modifier.weight(BLOCKS_WEIGHT)) {
                        ownFile(Modifier)
                        Spacer(Modifier.height(AppDimens.SpacingXl))
                        type(0.dp)
                        Spacer(Modifier.height(AppDimens.SpacingXl))
                        talk(Modifier)
                    }
                }
            } else {
                sample(
                    if (fontScaleLarge) SampleCardLayout.Stacked else SampleCardLayout.Row,
                    Modifier.padding(horizontal = pad),
                )
                Spacer(Modifier.height(AppDimens.SpacingXl))
                ownFile(Modifier.padding(horizontal = pad))
                Spacer(Modifier.height(AppDimens.SpacingXl))
                type(pad)
                Spacer(Modifier.height(AppDimens.SpacingXl))
                talk(Modifier.padding(horizontal = pad))
            }
        }
    }
}

/** Where each Try-it block sits inside the scroll content. Plain holder — read only from effects. */
private class BlockAnchors {
    var content: LayoutCoordinates? = null
    val blocks = HashMap<TryBlock, LayoutCoordinates>()

    fun top(block: TryBlock): Int? = offset(block)?.y?.toInt()

    fun bottom(block: TryBlock): Int? {
        val coords = blocks[block] ?: return null
        return offset(block)?.let { (it.y + coords.size.height).toInt() }
    }

    private fun offset(block: TryBlock): Offset? {
        val content = content?.takeIf { it.isAttached } ?: return null
        val coords = blocks[block]?.takeIf { it.isAttached } ?: return null
        return content.localPositionOf(coords, Offset.Zero)
    }
}

private fun Modifier.anchor(anchors: BlockAnchors, block: TryBlock): Modifier =
    onPlaced { anchors.blocks[block] = it }

/**
 * Reduced motion snaps with a raw delta: no scroll mutex to wait on, so nothing suspends after the
 * position is already applied. Otherwise the spatial tween.
 */
private suspend fun ScrollState.scrollToBlock(target: Int, reduced: Boolean) {
    if (reduced) dispatchRawDelta((target - value).toFloat()) else animateScrollTo(target, AppMotion.spatialDefaultTween())
}

/** Frame-driven fade of a ring alpha; `null` spec = snap (reduced motion). */
private suspend fun MutableFloatState.fadeTo(target: Float, spec: AnimationSpec<Float>?) {
    if (spec == null) {
        floatValue = target
    } else {
        animate(initialValue = floatValue, targetValue = target, animationSpec = spec) { v, _ -> floatValue = v }
    }
}

private const val NBSP = ' '

/** fontScale above which the stages become discs and the sample card stacks (§9 ladder). */
private const val LADDER_FONT_SCALE = 1.3f
private const val RING_HOLD_MS = 1200L
private const val SPINNER_DELAY_MS = 300L
private const val SAMPLE_WEIGHT = 5f
private const val BLOCKS_WEIGHT = 7f

private val HeightCompactBreakpoint = 480.dp
private val MediumContentWidth = 560.dp
private val ExpandedContentWidth = 1040.dp
private val ExpandedCtaWidth = 320.dp
