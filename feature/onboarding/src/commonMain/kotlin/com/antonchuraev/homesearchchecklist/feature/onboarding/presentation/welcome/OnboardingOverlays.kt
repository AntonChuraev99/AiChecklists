package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_sample_checklist_name
import aichecklists.core.designsystem.generated.resources.onboarding_sample_image_a11y
import aichecklists.core.designsystem.generated.resources.onboarding_sample_list_meta
import aichecklists.core.designsystem.generated.resources.onboarding_sample_ready
import aichecklists.core.designsystem.generated.resources.onboarding_sample_reading
import aichecklists.core.designsystem.generated.resources.onboarding_try_generating_prompt
import aichecklists.core.designsystem.generated.resources.onboarding_try_generating_slow
import aichecklists.core.designsystem.generated.resources.onboarding_welcome_generating
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppRoundCheckbox
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPresenceOrb
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.OrbMode
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.NO_SCAN
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SampleNote
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.SampleNoteImage
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components.awaitFrameMillis
import org.jetbrains.compose.resources.stringResource

/**
 * The sample overlay (DESIGN_SPEC §1.5, §7): the note is "read" (scan band + highlighter), then the
 * result card rises. Replaces the whole onboarding chrome.
 *
 * The overlay keeps its own clock: it shows "ready" only once BOTH the ViewModel says [stage] is
 * [SampleStage.Ready] (the write is done) AND the reading animation has played, then reports
 * [onFinished] when its ready animation ends. Reduced motion: note 300ms static → 120ms crossfade →
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
    val noteIn = remember { Animatable(if (reduced) 1f else 0f) }
    val scan = remember { Animatable(0f) }
    val listIn = remember { Animatable(if (reduced) 1f else 0f) }
    var readingDone by remember { mutableStateOf(false) }
    var showReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reduced) {
            awaitFrameMillis(REDUCED_NOTE_MS)
        } else {
            noteIn.animateTo(1f, AppMotion.spatialDefault)
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
        val noteWidth = minOf(NoteMaxWidth, maxWidth - NoteSideRoom)
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
                        SampleNoteImage(
                            noteWidth = noteWidth,
                            noteHeight = noteWidth * NOTE_ASPECT,
                            rotation = OVERLAY_NOTE_ROTATION,
                            well = false,
                            noteModifier = Modifier.graphicsLayer {
                                alpha = noteIn.value
                                val s = NOTE_START_SCALE + (1f - NOTE_START_SCALE) * noteIn.value
                                scaleX = s
                                scaleY = s
                            },
                            scan = {
                                val v = scan.value
                                when {
                                    v <= 0f -> NO_SCAN
                                    v >= 1f -> SCAN_DONE
                                    else -> v
                                }
                            },
                        )
                        Spacer(Modifier.height(AppDimens.SpacingXl))
                        StatusText(stringResource(Res.string.onboarding_sample_reading))
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
 * right after. Rises 24dp + fades in, rows staggered 40ms ([progress] `0..1`, draw phase only).
 */
@Composable
private fun ResultCard(progress: () -> Float) {
    val items = SAMPLE_ITEMS
    val divider = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(Res.string.onboarding_sample_image_a11y)
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
            Box(
                modifier = Modifier
                    .size(44.dp, 52.dp)
                    .clip(AppShapes.small)
                    .background(AppSurface.recessed())
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Image
                    },
                contentAlignment = Alignment.Center,
            ) {
                SampleNote(Modifier.size(34.dp, 42.dp).rotate(-3f))
            }
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
 * The typed-text AI loader (DESIGN_SPEC §2 "Typed → AI generating"): the thinking orb, the user's own
 * words in quotes, the status, and — after 8 s — "Still working on it…". Non-dismissible.
 */
@Composable
internal fun AiGeneratingLoader(
    prompt: String,
    isSlow: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val viewport = maxHeight
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(min = viewport)
                    .widthIn(max = OverlayMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.ScreenPaddingHorizontal, vertical = AppDimens.SpacingXl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GistiPresenceOrb(mode = OrbMode.Think, level = { 0f }, size = LoaderOrb)
                Spacer(Modifier.height(AppDimens.SpacingXl))
                Text(
                    text = stringResource(Res.string.onboarding_try_generating_prompt, prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = PROMPT_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppDimens.SpacingMd))
                Text(
                    text = stringResource(Res.string.onboarding_welcome_generating),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                AnimatedVisibility(visible = isSlow, enter = fadeIn(AppMotion.effectsDefaultTween())) {
                    Text(
                        text = stringResource(Res.string.onboarding_try_generating_slow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = AppDimens.SpacingSm)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

private val NoteMaxWidth = 280.dp
private val NoteSideRoom = 64.dp
private const val NOTE_ASPECT = 1.1f
private const val OVERLAY_NOTE_ROTATION = -2f
private const val NOTE_START_SCALE = 0.92f
/** Past the bottom edge: every line keeps its highlighter, no band is drawn. */
private const val SCAN_DONE = 2f
private val OverlayMaxWidth = 560.dp
private val CardRise = 24.dp
private val RowDividerInset = 52.dp
private val LoaderOrb = 96.dp
private const val PROMPT_MAX_LINES = 3

private const val SCAN_MS = 900
private const val LIST_IN_MS = 400
private const val HOLD_MS = 300L
private const val REDUCED_NOTE_MS = 300L
private const val REDUCED_LIST_MS = 600
private const val CARD_FADE_SHARE = 0.4f
private const val ROW_STAGGER_SHARE = 0.1f
private const val ROW_FADE_SHARE = 0.1f
