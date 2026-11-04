package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_sample_body
import aichecklists.core.designsystem.generated.resources.onboarding_sample_cta
import aichecklists.core.designsystem.generated.resources.onboarding_sample_title
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_error
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_error_sample
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_error_save
import aichecklists.core.designsystem.generated.resources.onboarding_try_ai_signin
import aichecklists.core.designsystem.generated.resources.onboarding_try_call
import aichecklists.core.designsystem.generated.resources.onboarding_try_chat
import aichecklists.core.designsystem.generated.resources.onboarding_try_send_a11y
import aichecklists.core.designsystem.generated.resources.onboarding_try_talk_caption_call
import aichecklists.core.designsystem.generated.resources.onboarding_try_talk_caption_chat
import aichecklists.core.designsystem.generated.resources.onboarding_try_talk_caption_typed
import aichecklists.core.designsystem.generated.resources.onboarding_try_talk_title
import aichecklists.core.designsystem.generated.resources.onboarding_try_type_empty_hint
import aichecklists.core.designsystem.generated.resources.onboarding_try_type_placeholder
import aichecklists.core.designsystem.generated.resources.onboarding_try_type_title
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppTextField
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPresenceOrb
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChip
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPromptChips
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.OrbMode
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.OnboardingAiError
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.WelcomeStarterTemplate
import org.jetbrains.compose.resources.stringResource

// ── A. Sample card ───────────────────────────────────────────────────────────

/** How the sample card arranges thumbnail and text (DESIGN_SPEC §1.4, §9 fontScale ladder). */
internal enum class SampleCardLayout {
    /** Thumbnail beside the text. */
    Row,

    /** fontScale > 1.3: thumbnail above the text. */
    Stacked,

    /** Expanded window: a 300dp well with the large note above the text. */
    Large,
}

/** Block A: the sample note and the screen's ONLY filled button. */
@Composable
internal fun SampleCard(
    layout: SampleCardLayout,
    enabled: Boolean,
    loading: Boolean,
    onTry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.groupSegment(GroupPosition.Single).padding(AppDimens.SpacingLg)) {
        when (layout) {
            SampleCardLayout.Row -> Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SampleThumb()
                SampleText(Modifier.weight(1f))
            }
            SampleCardLayout.Stacked -> {
                SampleThumb()
                Spacer(Modifier.height(AppDimens.SpacingMd))
                SampleText()
            }
            SampleCardLayout.Large -> {
                SampleNoteImage(
                    noteWidth = 220.dp,
                    noteHeight = 260.dp,
                    rotation = THUMB_ROTATION,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                Spacer(Modifier.height(AppDimens.SpacingLg))
                SampleText()
            }
        }
        Spacer(Modifier.height(AppDimens.SpacingMd))
        AppButton(
            text = stringResource(Res.string.onboarding_sample_cta),
            onClick = onTry,
            enabled = enabled,
            loading = loading,
            icon = Icons.Filled.AutoAwesome,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SampleThumb() {
    SampleNoteImage(
        noteWidth = 70.dp,
        noteHeight = 88.dp,
        rotation = THUMB_ROTATION,
        modifier = Modifier.size(ThumbWellWidth, ThumbWellHeight),
    )
}

@Composable
private fun SampleText(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(Res.string.onboarding_sample_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.onboarding_sample_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── C. Type block ────────────────────────────────────────────────────────────

/**
 * Block C: the goal field with its send disc, the AI-failure box, the empty-send hint and the starter
 * chips. [horizontalPadding] is applied to every child EXCEPT the chips, which bleed edge to edge
 * through their own `contentPadding` (GistiPromptChips KDoc, DESIGN_SPEC pitfall 1).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypeBlock(
    text: String,
    onTextChange: (String) -> Unit,
    showEmptyHint: Boolean,
    aiError: OnboardingAiError?,
    enabled: Boolean,
    fieldFocus: FocusRequester,
    horizontalPadding: Dp,
    onSend: () -> Unit,
    onErrorSample: () -> Unit,
    onErrorSave: () -> Unit,
    onChip: (WelcomeStarterTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    Column(modifier) {
        val inner = Modifier.padding(horizontal = horizontalPadding)
        Text(
            text = stringResource(Res.string.onboarding_try_type_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = inner.semantics { heading() },
        )
        Spacer(Modifier.height(AppDimens.SpacingSm))
        AppTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = stringResource(Res.string.onboarding_try_type_placeholder),
            enabled = enabled,
            singleLine = false,
            maxLines = FIELD_MAX_LINES,
            trailingIcon = { SendButton(filled = text.isNotBlank(), enabled = enabled, onClick = onSend) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = inner
                .focusRequester(fieldFocus)
                // Web/desktop: Enter sends, Shift+Enter is a new line (DESIGN_SPEC §9).
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
        )
        if (showEmptyHint) {
            Text(
                text = stringResource(Res.string.onboarding_try_type_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = inner
                    .padding(start = AppDimens.SpacingLg, top = AppDimens.SpacingXs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (aiError != null) {
            Spacer(Modifier.height(AppDimens.SpacingSm))
            AiErrorBox(aiError, enabled, onErrorSample, onErrorSave, inner)
        }
        AnimatedVisibility(
            visible = text.isBlank(),
            enter = expandVertically(if (reduced) snapTween() else AppMotion.spatialFastTween()) +
                fadeIn(if (reduced) reducedTween() else AppMotion.effectsDefaultTween()),
            exit = shrinkVertically(if (reduced) snapTween() else AppMotion.spatialFastTween()) +
                fadeOut(if (reduced) reducedTween() else AppMotion.effectsDefaultTween()),
        ) {
            val chips = WelcomeStarterTemplate.entries.map { template ->
                GistiPromptChip(emoji = template.emoji(), label = stringResource(template.labelRes), action = template)
            }
            GistiPromptChips(
                chips = chips,
                onChipClick = { if (enabled) onChip(it) },
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                modifier = Modifier.padding(top = AppDimens.SpacingXs),
            )
        }
    }
}

private fun <T> snapTween() = androidx.compose.animation.core.snap<T>()

private fun <T> reducedTween() = androidx.compose.animation.core.tween<T>(AppMotion.ReducedMotionMillis)

private fun WelcomeStarterTemplate.emoji(): String = when (this) {
    WelcomeStarterTemplate.GROCERIES -> "🛒"
    WelcomeStarterTemplate.TRIP -> "🧳"
    WelcomeStarterTemplate.WORKOUT -> "🏋️"
    WelcomeStarterTemplate.TODO -> "✅"
}

/**
 * The 36dp send disc in a 48dp target. Filled `primary` with text; recessed and dimmed when empty —
 * but still clickable: an empty tap answers with the hint (rule "every tap answers").
 */
@Composable
private fun SendButton(filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.onboarding_try_send_a11y)
    val disc = if (filled) MaterialTheme.colorScheme.primary else AppSurface.recessed()
    val glyph = if (filled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_GLYPH_ALPHA)
    }
    Box(
        modifier = Modifier
            .size(AppDimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(SendDisc).clip(CircleShape).background(disc),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = glyph, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The AI-failure box: recessed fill (not `errorContainer` — blue text buttons on pink make three
 * palettes in one box), an error-coloured icon and two AI-free ways forward. liveRegion polite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiErrorBox(
    error: OnboardingAiError,
    enabled: Boolean,
    onSample: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(AppSurface.recessed())
            .padding(start = AppDimens.SpacingMd, top = AppDimens.SpacingMd, end = AppDimens.SpacingMd, bottom = AppDimens.SpacingXs)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Icon(
            Icons.Outlined.SyncProblem,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (error) {
                        OnboardingAiError.Unavailable -> Res.string.onboarding_try_ai_error
                        OnboardingAiError.SignInRequired -> Res.string.onboarding_try_ai_signin
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The text buttons carry 16dp of their own inset; pull the row back so their LABELS line
            // up with the message above.
            // `offset` follows the layout direction, so this pulls toward the START edge in RTL too.
            FlowRow(Modifier.padding(top = AppDimens.SpacingXxs).offset(x = -ErrorActionInset)) {
                AppButtonText(stringResource(Res.string.onboarding_try_ai_error_sample), onSample, enabled = enabled)
                AppButtonText(stringResource(Res.string.onboarding_try_ai_error_save), onSave, enabled = enabled)
            }
        }
    }
}

// ── D. Talk card ─────────────────────────────────────────────────────────────

/**
 * Block D: Gisti's presence orb, title, caption and the Chat / Call buttons. Two buttons abreast only
 * when both labels fit ONE line at half width (measured like `SourcePillGrid`), else stacked.
 */
@Composable
internal fun TalkCard(
    isCallAvailable: Boolean,
    hasTypedText: Boolean,
    enabled: Boolean,
    titleFocus: FocusRequester,
    onChat: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.groupSegment(GroupPosition.Single).padding(AppDimens.SpacingLg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)) {
            // The orb node is 52dp (halo); a 40dp layout box with the node overflowing it keeps the
            // DISC aligned to the card padding and to the title's first line.
            Box(Modifier.size(TalkOrb).wrapContentSize(unbounded = true)) {
                GistiPresenceOrb(mode = OrbMode.Speak, level = { 0f }, size = TalkOrb, static = true)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.onboarding_try_talk_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .focusRequester(titleFocus)
                        .focusable()
                        .semantics { heading() },
                )
                Text(
                    text = stringResource(
                        when {
                            hasTypedText -> Res.string.onboarding_try_talk_caption_typed
                            isCallAvailable -> Res.string.onboarding_try_talk_caption_call
                            else -> Res.string.onboarding_try_talk_caption_chat
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(AppDimens.SpacingMd))
        TalkButtons(isCallAvailable, enabled, onChat, onCall)
    }
}

@Composable
private fun TalkButtons(isCallAvailable: Boolean, enabled: Boolean, onChat: () -> Unit, onCall: () -> Unit) {
    val chatLabel = stringResource(Res.string.onboarding_try_chat)
    val callLabel = stringResource(Res.string.onboarding_try_call)
    if (!isCallAvailable) {
        AppButtonSecondary(chatLabel, onChat, Modifier.fillMaxWidth(), enabled, icon = Icons.Outlined.ChatBubbleOutline)
        return
    }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = (maxWidth - AppDimens.SpacingSm) / 2
        val widest = remember(chatLabel, callLabel, style, measurer) {
            maxOf(measurer.measure(chatLabel, style).size.width, measurer.measure(callLabel, style).size.width)
        }
        val needed = with(LocalDensity.current) { widest.toDp() } + ButtonChrome
        if (needed <= half) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                AppButtonSecondary(chatLabel, onChat, Modifier.weight(1f), enabled, icon = Icons.Outlined.ChatBubbleOutline)
                AppButtonSecondary(callLabel, onCall, Modifier.weight(1f), enabled, icon = Icons.Filled.GraphicEq)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                AppButtonSecondary(chatLabel, onChat, Modifier.fillMaxWidth(), enabled, icon = Icons.Outlined.ChatBubbleOutline)
                AppButtonSecondary(callLabel, onCall, Modifier.fillMaxWidth(), enabled, icon = Icons.Filled.GraphicEq)
            }
        }
    }
}

// ── Mode highlight ring ──────────────────────────────────────────────────────

/**
 * A 2dp ring drawn [outset] outside the node in [shape], at [alpha] (read in the draw phase only).
 * Place it BEFORE a clipping modifier (`groupSegment`) so the clip does not cut the ring.
 */
internal fun Modifier.highlightRing(color: Color, shape: Shape, outset: Dp = 0.dp, alpha: () -> Float): Modifier =
    drawWithContent {
        drawContent()
        val a = alpha()
        if (a <= 0f) return@drawWithContent
        val stroke = RingWidth.toPx()
        val o = outset.toPx()
        val outline = shape.createOutline(
            Size(size.width + 2 * o - stroke, size.height + 2 * o - stroke),
            layoutDirection,
            this,
        )
        translate(left = -o + stroke / 2, top = -o + stroke / 2) {
            drawOutline(outline, color = color, alpha = a, style = Stroke(stroke))
        }
    }

// Fixed sizes without a token (DESIGN_SPEC §6).
private val ThumbWellWidth = 88.dp
private val ThumbWellHeight = 104.dp
private const val THUMB_ROTATION = -3f
private val SendDisc = 36.dp
private val TalkOrb = 40.dp
private val RingWidth = 2.dp
private val ErrorActionInset = 16.dp
/** AppButtonSecondary around its label: 2 × 24dp padding + 18dp icon + 8dp gap + 2dp border. */
private val ButtonChrome = 76.dp
private const val FIELD_MAX_LINES = 4
private const val EMPTY_GLYPH_ALPHA = 0.6f
