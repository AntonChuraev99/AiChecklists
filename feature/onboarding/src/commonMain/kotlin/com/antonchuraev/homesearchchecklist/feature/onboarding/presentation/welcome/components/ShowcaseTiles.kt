package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.onboarding_demo_talk_reply
import aichecklists.core.designsystem.generated.resources.onboarding_demo_talk_user
import aichecklists.core.designsystem.generated.resources.onboarding_demo_write_item_1
import aichecklists.core.designsystem.generated.resources.onboarding_demo_write_item_2
import aichecklists.core.designsystem.generated.resources.onboarding_demo_write_item_3
import aichecklists.core.designsystem.generated.resources.onboarding_demo_write_prompt
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_attach_caption
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_attach_title
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_cta
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_talk_caption_call
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_talk_caption_chat
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_talk_title
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_write_caption
import aichecklists.core.designsystem.generated.resources.onboarding_showcase_write_title
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonchuraev.homesearchchecklist.desingsystem.components.GroupPosition
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.GistiPresenceOrb
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.OrbMode
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupPositionOf
import com.antonchuraev.homesearchchecklist.desingsystem.components.groupSegment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapeTokens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppShapes
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.welcome.OnboardingMode
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** The three Showcase tiles (DESIGN_SPEC §1.2, §1.3). Each tile is ONE `Role.Button` node. */
@Composable
internal fun ShowcaseTiles(
    isCallAvailable: Boolean,
    showStages: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    onTileClick: (OnboardingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        TileSpec(OnboardingMode.Write, Res.string.onboarding_showcase_write_title, Res.string.onboarding_showcase_write_caption, Icons.Outlined.EditNote, PALETTE_WRITE),
        TileSpec(OnboardingMode.Attach, Res.string.onboarding_showcase_attach_title, Res.string.onboarding_showcase_attach_caption, Icons.Outlined.PhotoCamera, PALETTE_ATTACH),
        TileSpec(
            OnboardingMode.Talk,
            Res.string.onboarding_showcase_talk_title,
            if (isCallAvailable) Res.string.onboarding_showcase_talk_caption_call else Res.string.onboarding_showcase_talk_caption_chat,
            Icons.Outlined.Forum,
            PALETTE_TALK,
        ),
    )
    val clickLabel = stringResource(Res.string.onboarding_showcase_cta)
    if (expanded) {
        // 3 equal columns, stage on top (DESIGN_SPEC §9). IntrinsicSize.Max keeps the three cards the
        // same height when one caption wraps.
        Row(
            modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
        ) {
            tiles.forEach { tile ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .groupSegment(GroupPosition.Single)
                        .clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.Button) { onTileClick(tile.mode) }
                        .padding(AppDimens.SpacingMd),
                ) {
                    if (showStages) {
                        DemoStage(tile, Modifier.fillMaxWidth().scaleStageToWidth())
                        Spacer(Modifier.height(AppDimens.SpacingMd))
                    } else {
                        TileDisc(tile)
                        Spacer(Modifier.height(AppDimens.SpacingMd))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TileText(tile, Modifier.weight(1f))
                        Chevron()
                    }
                }
            }
        }
    } else {
        val inset = if (showStages) StageDividerInset else DiscDividerInset
        Column(modifier.fillMaxWidth()) {
            tiles.forEachIndexed { i, tile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .groupSegment(groupPositionOf(i, tiles.size), dividerInsetStart = inset)
                        .clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.Button) { onTileClick(tile.mode) }
                        .heightIn(min = TileMinHeight)
                        .padding(start = AppDimens.SpacingMd, top = AppDimens.SpacingMd, bottom = AppDimens.SpacingMd, end = AppDimens.SpacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
                ) {
                    if (showStages) DemoStage(tile) else TileDisc(tile)
                    TileText(tile, Modifier.weight(1f))
                    Chevron()
                }
            }
        }
    }
}

private class TileSpec(
    val mode: OnboardingMode,
    val title: StringResource,
    val caption: StringResource,
    val icon: ImageVector,
    val palette: Int,
)

@Composable
private fun TileText(tile: TileSpec, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(tile.title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(tile.caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(AppDimens.IconSizeMd),
    )
}

/** fontScale > 1.3 / height-compact rung: a 40dp palette disc instead of the demo stage. */
@Composable
private fun TileDisc(tile: TileSpec) {
    Box(
        modifier = Modifier
            .size(DiscSize)
            .clip(CircleShape)
            .background(GistiColors.paletteTint(tile.palette))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(tile.icon, contentDescription = null, tint = GistiColors.paletteContent(tile.palette))
    }
}

// ── Demo stages ──────────────────────────────────────────────────────────────

/**
 * A 120×96dp decorative mini-UI. fontScale is locked to 1.0 and semantics are cleared: the tile's
 * title and caption carry the meaning (DESIGN_SPEC §1.3, pitfall 2).
 */
@Composable
private fun DemoStage(tile: TileSpec, modifier: Modifier = Modifier) {
    val progress = rememberDemoClock(
        startOffsetMs = when (tile.mode) {
            OnboardingMode.Attach -> ATTACH_OFFSET_MS
            OnboardingMode.Talk -> TALK_OFFSET_MS
            else -> WRITE_OFFSET_MS
        },
    )
    Box(modifier.clearAndSetSemantics {}) {
        FontScaleLocked {
            Box(
                Modifier
                    .size(StageWidth, StageHeight)
                    .clip(AppShapes.medium)
                    .background(GistiColors.paletteTint(tile.palette)),
            ) {
                when (tile.mode) {
                    OnboardingMode.Attach -> AttachStage(progress)
                    OnboardingMode.Talk -> TalkStage(progress)
                    else -> WriteStage(progress)
                }
            }
        }
    }
}

/**
 * The stage clock, `0..1` over [DEMO_MS]. Plays [DEMO_CYCLES] times on a shared [SET_MS] beat, each
 * stage from its own start offset, then rests on the final frame (1f). Under reduced motion it is 1f
 * from the first frame and no coroutine is started (DESIGN_SPEC §7). The clock dies with the step:
 * Showcase off screen = effect cancelled.
 */
@Composable
private fun rememberDemoClock(startOffsetMs: Int): State<Float> {
    val reduced = LocalReducedMotion.current
    val clock = remember { Animatable(if (reduced) 1f else 0f) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            repeat(DEMO_CYCLES) { cycle ->
                clock.snapTo(0f)
                delay(startOffsetMs.toLong())
                clock.animateTo(1f, tween(DEMO_MS, easing = LinearEasing))
                if (cycle < DEMO_CYCLES - 1) delay((SET_MS - startOffsetMs - DEMO_MS).toLong())
            }
        }
    }
    return clock.asState()
}

/** Local progress of a sub-animation running from [startMs] for [durMs] inside the stage clock. */
private fun seg(p: Float, startMs: Int, durMs: Int): Float = ((p * DEMO_MS - startMs) / durMs).coerceIn(0f, 1f)

private val StageText = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp)

@Composable
private fun BoxScope.WriteStage(progress: State<Float>) {
    val prompt = stringResource(Res.string.onboarding_demo_write_prompt)
    val items = listOf(
        stringResource(Res.string.onboarding_demo_write_item_1),
        stringResource(Res.string.onboarding_demo_write_item_2),
        stringResource(Res.string.onboarding_demo_write_item_3),
    )
    // Typing: fixed per-character steps, linear (recomposes the pill text only while it types).
    val typed by remember(prompt) {
        derivedStateOf { prompt.take((prompt.length * seg(progress.value, 0, TYPE_MS)).roundToInt()) }
    }
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .fillMaxWidth()
            .height(22.dp)
            .clip(AppShapeTokens.Pill)
            .background(AppChatColors.raised())
            .border(1.dp, AppChatColors.controlOutline(), AppShapeTokens.Pill)
            .padding(start = 8.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = typed,
            style = StageText,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    val t = seg(progress.value, TYPE_MS, PULSE_MS)
                    val s = 1f - PULSE_DEPTH * sin(t * PI).toFloat()
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowUpward, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(11.dp))
        }
    }
    MiniListCard(
        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(),
    ) {
        items.forEachIndexed { i, item ->
            StaggerRow(progress, ROWS_START_MS + i * STAGGER_MS, rowHeight = 16.dp) {
                Text(item, style = StageText.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BoxScope.AttachStage(progress: State<Float>) {
    // The real sample photo's handwriting crop (DESIGN_SPEC 2026-11-17 §3.3 `Text`), not a drawn note.
    val band = MaterialTheme.colorScheme.primary
    SamplePhoto(
        crop = SampleCrop.Text,
        modifier = Modifier
            .offset(x = 8.dp, y = 8.dp)
            .graphicsLayer { translationX = -NOTE_SHIFT.toPx() * seg(progress.value, SCAN_MS, SHIFT_MS) }
            .size(46.dp, 62.dp)
            .rotate(-4f)
            .clip(AppShapes.extraSmall)
            .scanBand(band) {
                val t = seg(progress.value, 0, SCAN_MS)
                if (t > 0f && t < 1f) t else -1f
            },
    )
    Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = GistiColors.paletteContent(PALETTE_ATTACH),
        modifier = Modifier.offset(x = 51.dp, y = 38.dp).size(16.dp),
    )
    val bar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = BAR_ALPHA)
    MiniListCard(
        modifier = Modifier.padding(start = 68.dp, end = 6.dp, top = 14.dp).fillMaxWidth(),
    ) {
        listOf(22.dp, 16.dp, 20.dp, 14.dp).forEachIndexed { i, w ->
            StaggerRow(progress, SCAN_MS + SHIFT_MS + i * STAGGER_MS, rowHeight = 15.dp) {
                Box(Modifier.width(w).height(5.dp).clip(AppShapeTokens.Pill).background(bar))
            }
        }
    }
}

@Composable
private fun BoxScope.TalkStage(progress: State<Float>) {
    val bubbleShape = RoundedCornerShape(
        topStart = BubbleCorner,
        topEnd = BubbleCorner,
        bottomStart = BubbleCorner,
        bottomEnd = AppShapeTokens.ChatBubbleTail / 2,
    )
    Text(
        text = stringResource(Res.string.onboarding_demo_talk_user),
        style = StageText,
        color = AppChatColors.onUserBubble(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(start = 16.dp, top = 8.dp, end = 8.dp)
            .graphicsLayer {
                val t = seg(progress.value, 0, ENTER_MS)
                alpha = t
                translationY = (1f - t) * RISE.toPx()
            }
            .clip(bubbleShape)
            .background(AppChatColors.userBubble())
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
    val mode by remember {
        derivedStateOf {
            val ms = progress.value * DEMO_MS
            when {
                ms < ENTER_MS -> OrbMode.Listen0
                ms < REPLY_MS -> OrbMode.Think
                else -> OrbMode.Speak
            }
        }
    }
    val atRest by remember { derivedStateOf { progress.value >= 1f } }
    Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GistiPresenceOrb(
            mode = mode,
            level = {
                val t = seg(progress.value, SPEAK_MS, SPEAK_DUR_MS)
                (sin(t * PI) * (ENVELOPE_BASE + ENVELOPE_WOBBLE * sin(t * PI * ENVELOPE_WAVES))).toFloat()
            },
            size = 18.dp,
            static = atRest,
        )
        val replyShape = RoundedCornerShape(
            topStart = BubbleCorner,
            topEnd = BubbleCorner,
            bottomEnd = BubbleCorner,
            bottomStart = AppShapeTokens.ChatBubbleTail / 2,
        )
        Row(
            modifier = Modifier
                .graphicsLayer {
                    val t = seg(progress.value, REPLY_MS, ENTER_MS)
                    alpha = t
                    translationY = (1f - t) * RISE.toPx()
                }
                .clip(replyShape)
                .background(AppChatColors.raised())
                .border(1.dp, AppChatColors.contentOutline(), replyShape)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_demo_talk_reply),
                style = StageText,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = GistiColors.success, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
private fun MiniListCard(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(AppShapes.small)
            .background(AppSurface.card())
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) { content() }
}

/** One mini-list row: 9dp ring + content, entering with fade + 6dp rise at [startMs]. */
@Composable
private fun StaggerRow(progress: State<Float>, startMs: Int, rowHeight: Dp, content: @Composable () -> Unit) {
    val ring = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .height(rowHeight)
            .graphicsLayer {
                val t = seg(progress.value, startMs, ROW_ENTER_MS)
                alpha = t
                translationY = (1f - t) * RISE.toPx()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).border(1.2.dp, ring, CircleShape))
        Spacer(Modifier.width(5.dp))
        content()
    }
}

/**
 * Expanded layout: the SAME 120×96 composition scaled to the tile width at 5:4 (DESIGN_SPEC §1.3) —
 * one composition, no second layout. Unbounded width (an intrinsic query) keeps the base size.
 */
private fun Modifier.scaleStageToWidth(): Modifier = layout { measurable, constraints ->
    val baseW = StageWidth.roundToPx()
    val baseH = StageHeight.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(baseW, baseH))
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else baseW
    val scale = width.toFloat() / baseW
    val height = (baseH * scale).roundToInt()
    layout(width, height) {
        placeable.placeWithLayer((width - baseW) / 2, (height - baseH) / 2) {
            scaleX = scale
            scaleY = scale
        }
    }
}

// Fixed sizes without a token (DESIGN_SPEC §6): derived from the stage, kept next to it.
private val StageWidth = 120.dp
private val StageHeight = 96.dp
private val DiscSize = 40.dp
private val TileMinHeight = 88.dp
/** 12 padding + 120 stage + 12 gap: the divider starts under the tile's text. */
private val StageDividerInset = 144.dp
/** 12 padding + 40 disc + 12 gap. */
private val DiscDividerInset = 64.dp
private val BubbleCorner = 12.dp
private val RISE = 6.dp
private val NOTE_SHIFT = 6.dp

private const val PALETTE_WRITE = 1
private const val PALETTE_ATTACH = 2
private const val PALETTE_TALK = 3

private const val DEMO_MS = 2400
private const val DEMO_CYCLES = 2
private const val WRITE_OFFSET_MS = 300
private const val ATTACH_OFFSET_MS = 1200
private const val TALK_OFFSET_MS = 2100
/** The whole set: the last stage's offset + one demo + a 3 s hold. */
private const val SET_MS = TALK_OFFSET_MS + DEMO_MS + 3000

private const val TYPE_MS = 800
private const val PULSE_MS = 200
private const val PULSE_DEPTH = 0.1f
private const val ROWS_START_MS = 1000
private const val STAGGER_MS = 120
private const val ROW_ENTER_MS = 300
private const val SCAN_MS = 600
private const val SHIFT_MS = 200
private const val BAR_ALPHA = 0.35f
private const val ENTER_MS = 300
private const val REPLY_MS = 1100
private const val SPEAK_MS = 1400
private const val SPEAK_DUR_MS = 1000
private const val ENVELOPE_BASE = 0.6
private const val ENVELOPE_WOBBLE = 0.4
private const val ENVELOPE_WAVES = 6
