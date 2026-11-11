package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.voice_call_pane_title
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.VoiceCallDefaults
import org.jetbrains.compose.resources.stringResource

/**
 * The voice call overlay — stateless, one layout per [VoiceCallUiState] (DESIGN_SPEC §1–§9).
 *
 * Drawn edge-to-edge on `AppSurface.ground()` over the whole window (system bars included); the
 * content sits inside `safeDrawingPadding()`. Portrait content is capped at 480dp and centred; a
 * landscape window under 480dp high switches the call to three columns (orb · content · dock rail).
 * The overlay swallows every touch so nothing under it is reachable.
 *
 * Motion: entry fades in and scales 0.92 → 1 from the bottom-end (the entry button) with
 * `spatialSlow`; a state change (call → summary / error) cross-fades with a 16dp rise. Closing is
 * the host's: this composable is gone at [VoiceCallUiState.Closed].
 *
 * @param micLevel / [voiceLevel] audio levels `0..1`, read in the draw phase only.
 */
@Composable
fun VoiceCallScreen(
    uiState: VoiceCallUiState,
    onAction: (VoiceCallAction) -> Unit,
    micLevel: () -> Float,
    voiceLevel: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (uiState is VoiceCallUiState.Closed) return
    PlatformBackHandler(enabled = true) { onAction(VoiceCallAction.BackPressed) }
    val paneTitle = stringResource(Res.string.voice_call_pane_title)
    val reduced = LocalReducedMotion.current
    val inspection = LocalInspectionMode.current
    val entry = remember { Animatable(if (inspection) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (entry.value < 1f) {
            entry.animateTo(1f, if (reduced) tween(AppMotion.ReducedMotionMillis) else AppMotion.spatialSlow)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = entry.value
                alpha = p.coerceIn(0f, 1f)
                if (!reduced) {
                    val s = ENTRY_SCALE + (1f - ENTRY_SCALE) * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 1f)
                }
            }
            .background(AppSurface.ground())
            .clickable(enabled = true, onClick = {}, indication = null, interactionSource = null)
            .semantics { this.paneTitle = paneTitle },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight && maxHeight < LANDSCAPE_MAX_HEIGHT
            val density = LocalDensity.current
            val compactCards = density.fontScale >= COMPACT_FONT_SCALE && maxWidth < COMPACT_MAX_WIDTH
            val risePx = with(density) { RISE.roundToPx() }
            AnimatedContent(
                targetState = uiState,
                contentKey = { it::class },
                transitionSpec = {
                    if (reduced) {
                        fadeIn(tween(AppMotion.ReducedMotionMillis)) togetherWith fadeOut(tween(AppMotion.ReducedMotionMillis))
                    } else {
                        (fadeIn(AppMotion.effectsDefault) + slideInVertically(AppMotion.spatialDefaultAs()) { risePx }) togetherWith
                            fadeOut(AppMotion.effectsDefault)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
                label = "voiceCallState",
            ) { state ->
                // AnimatedContent hands its children the window's FIXED width; wrapContentWidth
                // releases the min so the 480dp cap can bite, and centres the column.
                val capped = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = VoiceCallDefaults.ContentMaxWidth)
                    .fillMaxSize()
                when (state) {
                    is VoiceCallUiState.InCall -> InCallContent(
                        state = state,
                        onAction = onAction,
                        micLevel = micLevel,
                        voiceLevel = voiceLevel,
                        landscape = landscape,
                        compactCards = compactCards,
                        modifier = if (landscape) Modifier.fillMaxSize() else capped,
                    )
                    is VoiceCallUiState.Permission -> Box(capped) { PermissionContent(state, onAction) }
                    is VoiceCallUiState.Failure -> Box(capped) { FailureContent(state.kind, onAction) }
                    is VoiceCallUiState.Summary -> Box(capped) { SummaryContent(state, compactCards, onAction) }
                    is VoiceCallUiState.LimitReached -> Box(capped) { LimitContent(state, compactCards, onAction) }
                    VoiceCallUiState.Closed -> Unit
                }
            }
        }
    }
}

private const val ENTRY_SCALE = 0.92f
private const val COMPACT_FONT_SCALE = 1.5f
private val COMPACT_MAX_WIDTH = 360.dp
private val LANDSCAPE_MAX_HEIGHT = 480.dp

/** Rise of the incoming state on a call → summary / error change. */
private val RISE = 16.dp
