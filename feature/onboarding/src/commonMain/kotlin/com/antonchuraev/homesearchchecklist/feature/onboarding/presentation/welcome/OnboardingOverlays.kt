package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_ai_slow_longer
import aichecklists.core.designsystem.generated.resources.onboarding_ai_slow_thanks
import aichecklists.core.designsystem.generated.resources.onboarding_ai_step_ideas
import aichecklists.core.designsystem.generated.resources.onboarding_ai_step_magic
import aichecklists.core.designsystem.generated.resources.onboarding_ai_step_order
import aichecklists.core.designsystem.generated.resources.onboarding_ai_step_read
import aichecklists.core.designsystem.generated.resources.onboarding_sample_checklist_name
import aichecklists.core.designsystem.generated.resources.onboarding_sample_found
import aichecklists.core.designsystem.generated.resources.onboarding_sample_list_meta
import aichecklists.core.designsystem.generated.resources.onboarding_sample_ready
import aichecklists.core.designsystem.generated.resources.onboarding_sample_reading
import aichecklists.core.designsystem.generated.resources.onboarding_try_generating_prompt
import aichecklists.core.designsystem.generated.resources.onboarding_try_generating_slow
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_generating
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppRoundCheckbox
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDensity
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SampleBadge
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SampleCrop
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SamplePhoto
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.TEXT_BLOCK_BOTTOM
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.TEXT_BLOCK_TOP
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.awaitFrameMillis
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.sampleNoteDescription
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.scanBand
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

/**
 * The sample overlay (DESIGN_SPEC 2026-11-17 §2.4, §3.3, §6): the real photo of the note is "read"
 * — four corner brackets snap around it, a band sweeps the text block and a counter ticks "Found N of
 * 6" — then the result card rises. Replaces the whole onboarding chrome.
 *
 * No per-line geometry: line positions differ between the EN / RU / HI photos, the text BLOCK does not.
 *
 * The overlay keeps its own clock: it shows "ready" only once BOTH the ViewModel says [stage] is
 * [SampleStage.Ready] (the write is done) AND the reading animation has played, then reports
 * [onFinished] when its ready animation ends. Reduced motion: photo 300ms static → 120ms crossfade →
 * list 600ms → finished.
 */
@Composable
internal fun SampleAnalyzingOverlay(
    stage: SampleStage,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val finished by rememberUpdatedState(onFinished)
    val haptic = LocalHapticFeedback.current
    val photoIn = remember { Animatable(if (reduced) 1f else 0f) }
    val brackets = remember { Animatable(if (reduced) 1f else 0f) }
    // 0..1 across the text block; reduced motion starts (and stays) at the end: every task "found".
    val scan = remember { Animatable(if (reduced) 1f else 0f) }
    val listIn = remember { Animatable(if (reduced) 1f else 0f) }
    var readingDone by remember { mutableStateOf(false) }
    var showReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reduced) {
            awaitFrameMillis(REDUCED_NOTE_MS)
        } else {
            photoIn.animateTo(1f, AppMotion.spatialDefault)
            brackets.animateTo(1f, AppMotion.spatialFast)
            scan.animateTo(1f, tween(SCAN_MS, easing = LinearEasing))
        }
        readingDone = true
    }
    val ready = stage == SampleStage.Ready && readingDone
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        showReady = true
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (reduced) {
            awaitFrameMillis((AppMotion.ReducedMotionMillis + REDUCED_LIST_MS).toLong())
        } else {
            awaitFrameMillis(AppMotion.EffectsDefaultMillis.toLong())
            listIn.animateTo(1f, tween(LIST_IN_MS, easing = AppMotion.SpatialEasing))
            awaitFrameMillis(HOLD_MS)
        }
        finished()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val viewport = maxHeight
        // §2.4: min(280dp, window − 64dp, (window height − 168dp) × 2/3) — the photo AND its status fit.
        val photoWidth = minOf(PhotoMaxWidth, maxWidth - PhotoSideRoom, (maxHeight - PhotoVerticalRoom) * 2f / 3f)
            .coerceAtLeast(PhotoMinWidth)
        AnimatedContent(
            targetState = showReady,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(AppMotion.ReducedMotionMillis)) togetherWith fadeOut(tween(AppMotion.ReducedMotionMillis))
                } else {
                    fadeIn(AppMotion.effectsDefaultTween()) togetherWith
                        (fadeOut(AppMotion.effectsDefaultTween()) + slideOutVertically(AppMotion.spatialDefaultTween()) { -it / 8 })
                }
            },
            label = "sampleOverlay",
        ) { isReady ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(min = viewport)
                        .widthIn(max = OverlayMaxWidth)
                        .fillMaxWidth()
                        .padding(AppDimens.SpacingLg),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!isReady) {
                        ReadingPhoto(
                            width = photoWidth,
                            photoIn = { photoIn.value },
                            brackets = { brackets.value },
                            scan = { scan.value },
                        )
                        Spacer(Modifier.height(AppDimens.SpacingLg))
                        StatusText(stringResource(Res.string.onboarding_sample_reading))
                        FoundCounter(scan = { scan.value })
                    } else {
                        StatusText(stringResource(Res.string.onboarding_sample_ready))
                        Spacer(Modifier.height(AppDimens.SpacingLg))
                        ResultCard(progress = { listIn.value })
                    }
                }
            }
        }
    }
}

/**
 * The full print with its corner brackets 8dp OUTSIDE it, the "Sample" badge and the scan band over
 * the text block. Light: one soft shadow (a physical print). Dark: a 1dp outlineVariant ring instead.
 */
@Composable
private fun ReadingPhoto(width: Dp, photoIn: () -> Float, brackets: () -> Float, scan: () -> Float) {
    val dark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.outlineVariant
    val description = sampleNoteDescription()
    Box(
        modifier = Modifier
            .width(width + BracketOutset * 2)
            .drawWithContent {
                drawContent()
                val a = brackets()
                if (a <= 0f) return@drawWithContent
                val s = BRACKET_START_SCALE + (1f - BRACKET_START_SCALE) * a
                val stroke = BracketStroke.toPx()
                val arm = BracketArm.toPx()
                val r = BracketRadius.toPx()
                val inset = stroke / 2
                val cx = size.width / 2
                val cy = size.height / 2
                // One corner path (top-start), mirrored into the other three.
                listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (sx, sy) ->
                    val ox = cx - sx * (cx - inset) * s
                    val oy = cy - sy * (cy - inset) * s
                    val path = Path().apply {
                        moveTo(ox, oy + sy * arm)
                        lineTo(ox, oy + sy * r)
                        quadraticTo(ox, oy, ox + sx * r, oy)
                        lineTo(ox + sx * arm, oy)
                    }
                    drawPath(path, primary, alpha = a, style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
            }
            .padding(BracketOutset)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(SampleCrop.Full.aspect)
                .graphicsLayer {
                    val p = photoIn()
                    alpha = p
                    val sc = PHOTO_START_SCALE + (1f - PHOTO_START_SCALE) * p
                    scaleX = sc
                    scaleY = sc
                }
                .then(
                    if (dark) {
                        Modifier.border(1.dp, ring, AppShapes.medium)
                    } else {
                        Modifier.shadow(PhotoShadow, AppShapes.medium, clip = false)
                    },
                )
                .clip(AppShapes.medium),
        ) {
            SamplePhoto(
                crop = SampleCrop.Full,
                modifier = Modifier.fillMaxSize().scanBand(primary) {
                    val v = scan()
                    if (v <= 0f || v >= 1f) -1f else TEXT_BLOCK_TOP + (TEXT_BLOCK_BOTTOM - TEXT_BLOCK_TOP) * v
                },
            )
            SampleBadge(Modifier.align(Alignment.TopStart).padding(AppDimens.SpacingMd))
        }
    }
}

/**
 * "Found N of 6 tasks", N ticking as the band passes each sixth of the text block. Only the final
 * value is a polite live region, so TalkBack announces once, not six times.
 */
@Composable
private fun FoundCounter(scan: () -> Float) {
    val total = SAMPLE_ITEMS.size
    val found by remember { derivedStateOf { ceil(scan() * total).toInt().coerceIn(1, total) } }
    Text(
        text = stringResource(Res.string.onboarding_sample_found, found, total),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = if (found == total) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * A picture of the list being saved, not a live list: navigation lands on the real checklist detail
 * right after. Rises 24dp + fades in, rows staggered ([progress] `0..1`, draw phase only). The header
 * thumbnail is the photo's handwriting crop — a cover crop of the full frame showed the desk.
 */
@Composable
private fun ResultCard(progress: () -> Float) {
    val items = SAMPLE_ITEMS
    val divider = MaterialTheme.colorScheme.outlineVariant
    val description = sampleNoteDescription()
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = progress()
                alpha = (p / CARD_FADE_SHARE).coerceIn(0f, 1f)
                translationY = (1f - p) * CardRise.toPx()
            }
            .groupSegment(GroupPosition.Single),
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            SamplePhoto(
                crop = SampleCrop.Text,
                modifier = Modifier
                    .size(ThumbWidth, ThumbHeight)
                    .clip(AppShapes.small)
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Image
                    },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.onboarding_sample_checklist_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.onboarding_sample_list_meta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items.forEachIndexed { i, item ->
            if (i > 0) {
                Box(
                    Modifier
                        .padding(start = RowDividerInset)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(divider),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val start = CARD_FADE_SHARE + i * ROW_STAGGER_SHARE
                        alpha = ((progress() - start) / ROW_FADE_SHARE).coerceIn(0f, 1f)
                    }
                    .padding(horizontal = AppDimens.SpacingLg, vertical = AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Not interactive: the glyph has no semantics; the row reads as plain text.
                AppRoundCheckbox(checked = false)
                Spacer(Modifier.width(AppDimens.SpacingMd))
                Text(
                    text = stringResource(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(AppDimens.SpacingXs))
    }
}

/**
 * The typed-text AI loader (DESIGN_SPEC 2026-11-17 §L2): the headline, the user's own words in
 * quotes, and a card of four steps on the checklist-row glyphs. The steps tick on a UI clock
 * ([AiLoaderTimeline]); the last one stays in progress until the phase leaves — the server reports
 * no progress, so the card never shows "all done" while we still wait. Slow captions at 8 / 16 / 26 s.
 * Non-dismissible.
 *
 * The clock runs on the frame clock (withFrameMillis), never `delay()`: it is what Robolectric's
 * paused looper and the compose test clock advance. It starts when the loader first composes and
 * keeps running through `Leaving(Typed)` (same call site); a retry composes a fresh loader.
 *
 * Compact mode (only the active row) is chosen by MEASURED fit — the full layout with the tallest
 * caption against the viewport — once per window size, never per frame (§L2.7).
 */
@Composable
internal fun AiGeneratingLoader(
    prompt: String,
    isSlow: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val pinned = LocalAiLoaderElapsedOverride.current
    var clock by remember { mutableLongStateOf(0L) }
    val elapsed = pinned ?: clock
    LaunchedEffect(Unit) {
        if (pinned != null) return@LaunchedEffect // a pinned snapshot: no clock
        val start = withFrameMillis { it }
        for (milestone in AiLoaderTimeline.milestones) {
            while (withFrameMillis { it } - start < milestone) Unit
            clock = milestone
        }
    }
    // Sticky: Leaving(Typed) reports isSlow = false, the caption must not vanish on the last frame.
    var slowSeen by remember { mutableStateOf(false) }
    LaunchedEffect(isSlow) { if (isSlow) slowSeen = true }
    val slow = isSlow || slowSeen
    // Entry (§L2.5): block fade 220ms, rows staggered 40ms. Reduced motion: no entry animation.
    val entry = remember { Animatable(if (reduced) ENTRY_TOTAL_MS.toFloat() else 0f) }
    LaunchedEffect(Unit) { entry.animateTo(ENTRY_TOTAL_MS.toFloat(), tween(ENTRY_TOTAL_MS, easing = LinearEasing)) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val viewport = maxHeight
        val decision = remember(constraints.maxWidth, constraints.maxHeight) { CompactDecision() }
        SubcomposeLayout(Modifier.fillMaxSize()) { c ->
            val compact = decision.compact ?: run {
                val probe = subcompose(LoaderSlot.Probe) {
                    LoaderBlock(prompt, step = 0, caption = 0, compact = false, entry = { ENTRY_TOTAL_MS.toFloat() }, probe = true)
                }.first().measure(Constraints(maxWidth = c.maxWidth))
                (probe.height > c.maxHeight).also { decision.compact = it }
            }
            val main = subcompose(LoaderSlot.Main) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoaderBlock(
                        prompt = prompt,
                        step = AiLoaderTimeline.activeStep(elapsed),
                        caption = AiLoaderTimeline.caption(elapsed, slow),
                        compact = compact,
                        entry = { entry.value },
                        modifier = Modifier.heightIn(min = viewport),
                    )
                }
            }.first().measure(c)
            layout(c.maxWidth, c.maxHeight) { main.place(0, 0) }
        }
    }
}

/**
 * The loader's column. [probe] = the worst case for the fit measure: every label SemiBold, the
 * tallest caption, no infinite animation.
 */
@Composable
private fun LoaderBlock(
    prompt: String,
    step: Int,
    caption: Int,
    compact: Boolean,
    entry: () -> Float,
    modifier: Modifier = Modifier,
    probe: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val labels = AiLoaderSteps.map { stringResource(it) }
    Column(
        modifier = modifier
            .widthIn(max = OverlayMaxWidth)
            .fillMaxWidth()
            .graphicsLayer { alpha = (entry() / ENTRY_FADE_MS).coerceIn(0f, 1f) }
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal, vertical = AppDimens.SpacingXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_welcome_generating),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.onboarding_try_generating_prompt, prompt),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = if (compact || LocalDensity.current.fontScale >= PROMPT_TWO_LINE_FONT_SCALE) 2 else PROMPT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(AppDimens.SpacingXl))
        val activeLabel = labels[step]
        Column(
            modifier = Modifier
                .widthIn(max = LoaderCardMaxWidth)
                .fillMaxWidth()
                .groupSegment(GroupPosition.Single)
                .padding(vertical = AppDimens.SpacingSm)
                // ONE node: "Gathering ideas, in progress". No live region — the ticks are not pushed.
                .clearAndSetSemantics {
                    contentDescription = activeLabel
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
        ) {
            if (compact) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val spec = if (reduced) tween<Float>(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()
                        fadeIn(spec) togetherWith fadeOut(spec)
                    },
                    label = "loaderCompactStep",
                ) { s -> StepRow(labels[s], StepState.Active, animate = !reduced && !probe) }
            } else {
                labels.forEachIndexed { i, label ->
                    val state = when {
                        probe -> StepState.Active
                        i < step -> StepState.Done
                        i == step -> StepState.Active
                        else -> StepState.Pending
                    }
                    StepRow(
                        label = label,
                        state = state,
                        animate = !reduced && !probe,
                        modifier = Modifier.graphicsLayer {
                            alpha = ((entry() - i * ENTRY_STAGGER_MS) / ENTRY_FADE_MS).coerceIn(0f, 1f)
                        },
                    )
                }
            }
        }
        if (probe) {
            // The tallest of the three captions decides the fit.
            Box(Modifier.padding(top = AppDimens.SpacingMd)) {
                AiLoaderCaptions.forEach { SlowCaption(stringResource(it), live = false) }
            }
        } else {
            AnimatedContent(
                targetState = caption,
                transitionSpec = {
                    val spec = if (reduced) tween<Float>(AppMotion.ReducedMotionMillis) else AppMotion.effectsDefaultTween()
                    fadeIn(spec) togetherWith fadeOut(spec)
                },
                label = "loaderCaption",
            ) { level ->
                if (level > 0) {
                    SlowCaption(
                        text = stringResource(AiLoaderCaptions[level - 1]),
                        // Only caption 1 is announced: one every 10 s would be noise (§L2.8).
                        live = level == 1,
                        modifier = Modifier.padding(top = AppDimens.SpacingMd),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlowCaption(text: String, live: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = if (live) modifier.semantics { liveRegion = LiveRegionMode.Polite } else modifier,
    )
}

private enum class StepState { Done, Active, Pending }

/**
 * One step: the glyph in a one-line-high box (so it sits on the FIRST line of a wrapped label), the
 * label beside it. Fixed slots — a tick changes glyph and type, never position.
 */
@Composable
private fun StepRow(label: String, state: StepState, animate: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val style = MaterialTheme.typography.bodyLarge
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimens.MinTouchTarget)
            .padding(horizontal = AppDensity.GroupRowPaddingHorizontal, vertical = AppDimens.SpacingMd),
        verticalAlignment = Alignment.Top,
    ) {
        Crossfade(
            targetState = state,
            animationSpec = if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.effectsFastTween(),
            modifier = Modifier.width(AppDensity.RoundCheckboxBox).height(lineHeight),
            label = "loaderStepGlyph",
        ) { s ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (s) {
                    StepState.Done -> AppRoundCheckbox(checked = true)
                    StepState.Pending -> AppRoundCheckbox(checked = false)
                    StepState.Active -> LoaderStepGlyph(animate)
                }
            }
        }
        Spacer(Modifier.width(AppDensity.GroupRowGap))
        Text(
            text = label,
            style = if (state == StepState.Active) style.copy(fontWeight = FontWeight.SemiBold) else style,
            color = if (state == StepState.Active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The active step: an indeterminate ring the size of the checkbox. [animate] false (reduced motion,
 * the fit probe) composes NO progress indicator at all — a hidden one keeps its infinite transition
 * running — but a static `primary` ring.
 */
@Composable
private fun LoaderStepGlyph(animate: Boolean) {
    if (animate) {
        CircularProgressIndicator(
            modifier = Modifier.size(AppDensity.RoundCheckboxSize),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = LoaderRingStroke,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
        )
    } else {
        Box(
            Modifier
                .size(AppDensity.RoundCheckboxSize)
                .border(LoaderRingStroke, MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

/**
 * Previews and screenshot tests only (DESIGN_SPEC §L2.14 `elapsedMsOverride`): pins the loader's
 * clock to this many ms so a snapshot shows a given step / caption. null in the app = live clock.
 */
internal val LocalAiLoaderElapsedOverride = staticCompositionLocalOf<Long?> { null }

/** Measured once per window size: null until the fit probe has run (§L2.7, pitfall 2). */
private class CompactDecision {
    var compact: Boolean? = null
}

private enum class LoaderSlot { Probe, Main }

private val AiLoaderSteps: List<StringResource> = listOf(
    Res.string.onboarding_ai_step_read,
    Res.string.onboarding_ai_step_ideas,
    Res.string.onboarding_ai_step_order,
    Res.string.onboarding_ai_step_magic,
)

private val AiLoaderCaptions: List<StringResource> = listOf(
    Res.string.onboarding_try_generating_slow,
    Res.string.onboarding_ai_slow_longer,
    Res.string.onboarding_ai_slow_thanks,
)

/**
 * The loader's UI timeline (DESIGN_SPEC §L2.5). Pure: the composable feeds it the elapsed time of
 * its frame clock. The last step never completes on the clock; captions need the ViewModel's slow
 * flag (8 s) and then follow at 16 / 26 s without looping.
 */
internal object AiLoaderTimeline {
    const val STEP_COUNT = 4
    private val stepTicks = longArrayOf(1_200L, 2_600L, 4_200L)
    private const val CAPTION_2_MS = 16_000L
    private const val CAPTION_3_MS = 26_000L

    /** Every time at which the visible state can change, ascending. */
    val milestones: List<Long> = stepTicks.toList() + listOf(CAPTION_2_MS, CAPTION_3_MS)

    /** Index of the step in progress, 0..[STEP_COUNT]−1; capped at the last one. */
    fun activeStep(elapsedMs: Long): Int = stepTicks.count { elapsedMs >= it }.coerceAtMost(STEP_COUNT - 1)

    /** 0 = no caption, 1..3 = the caption shown. */
    fun caption(elapsedMs: Long, slow: Boolean): Int = when {
        !slow -> 0
        elapsedMs >= CAPTION_3_MS -> 3
        elapsedMs >= CAPTION_2_MS -> 2
        else -> 1
    }
}

// Fixed sizes without a token (DESIGN_SPEC §2.4 / §3.3).
private val PhotoMaxWidth = 280.dp
private val PhotoMinWidth = 120.dp
private val PhotoSideRoom = 64.dp
private val PhotoVerticalRoom = 168.dp
private val PhotoShadow = 6.dp
private val BracketOutset = 8.dp
private val BracketStroke = 3.dp
private val BracketArm = 28.dp
private val BracketRadius = 12.dp
private const val BRACKET_START_SCALE = 1.08f
private const val PHOTO_START_SCALE = 0.94f
private val OverlayMaxWidth = 560.dp
private val CardRise = 24.dp
private val RowDividerInset = 52.dp
private val ThumbWidth = 44.dp
private val ThumbHeight = 52.dp
private const val PROMPT_MAX_LINES = 3
private const val PROMPT_TWO_LINE_FONT_SCALE = 1.5f

// AI loader v2 (DESIGN_SPEC §L2.4 no-token values, §L2.5 entry).
private val LoaderCardMaxWidth = 400.dp
private val LoaderRingStroke = 2.dp
private const val ENTRY_FADE_MS = 220f
private const val ENTRY_STAGGER_MS = 40f
private const val ENTRY_TOTAL_MS = 340

private const val SCAN_MS = 1000
private const val LIST_IN_MS = 400
private const val HOLD_MS = 300L
private const val REDUCED_NOTE_MS = 300L
private const val REDUCED_LIST_MS = 600
private const val CARD_FADE_SHARE = 0.4f
private const val ROW_STAGGER_SHARE = 0.1f
private const val ROW_FADE_SHARE = 0.1f

