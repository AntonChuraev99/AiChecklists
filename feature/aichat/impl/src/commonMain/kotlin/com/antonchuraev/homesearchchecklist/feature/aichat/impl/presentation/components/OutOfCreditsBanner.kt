package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_cta_pro
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_free_body
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_free_body_no_amount
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_free_title
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_guest_body
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_guest_title
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_low_title
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_not_sent
import aichecklists.core.designsystem.generated.resources.chat_out_of_credits_premium_title
import aichecklists.core.designsystem.generated.resources.credits_refill_info
import aichecklists.core.designsystem.generated.resources.drawer_sign_in
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.NoticeBannerLayout
import com.antonchuraev.homesearchchecklist.desingsystem.components.rememberHeadlineOnOneLineFit
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppChatColors
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatScreenIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.OutOfCreditsBannerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The out-of-credits notice pinned above the AI chat input (DESIGN_SPEC 2026-11-26 §2): a raised
 * card with a hairline, icon · title/body · text button; the button drops under the text when the
 * title would not stay on one line beside it.
 *
 * Stateless: [state] null hides it (animated exit, the last variant stays drawn until the exit
 * ends); [nudgeKey] is a counter the host bumps on every refused send — each change past the value
 * seen at mount flashes the outline, shakes the card (reduced motion: flash only) and prefixes the
 * spoken text with "Not sent." for ~2 s so the live region re-announces.
 *
 * [onCtaClick] fires for [OutOfCreditsBannerState.Free] (paywall) and [OutOfCreditsBannerState.Guest]
 * (sign-in); [OutOfCreditsBannerState.Premium] has no button. [contentPadding] is the gap around the
 * card inside the animated region, so it collapses together with the card.
 */
@Composable
fun OutOfCreditsBanner(
    state: OutOfCreditsBannerState?,
    nudgeKey: Int,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = OutOfCreditsBannerDefaults.ContentPadding,
) {
    val visibleState = remember { MutableTransitionState(initialState = state != null) }
    visibleState.targetState = state != null
    var lastState by remember { mutableStateOf(state) }
    if (state != null) lastState = state

    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = if (reducedMotion) {
            fadeIn(tween(AppMotion.ReducedMotionMillis))
        } else {
            fadeIn(AppMotion.effectsDefaultTween()) +
                expandVertically(AppMotion.spatialFastTween(), expandFrom = Alignment.Bottom)
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(AppMotion.effectsDefaultTween()) +
                shrinkVertically(AppMotion.spatialDefaultTween(), shrinkTowards = Alignment.Bottom)
        },
    ) {
        lastState?.let { shown ->
            NudgingBanner(
                state = shown,
                nudgeKey = nudgeKey,
                onCtaClick = onCtaClick,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

/**
 * What the banner's button sends: Free → paywall, Guest → the host's Google sign-in, Premium has no
 * button. One mapping for every surface that hosts the banner (ChatScreen, the v2 dock, the overlay).
 */
fun OutOfCreditsBannerState.ctaIntent(): ChatScreenIntent? = when (this) {
    is OutOfCreditsBannerState.Free -> ChatScreenIntent.OnPaywallCtaClick
    OutOfCreditsBannerState.Guest -> ChatScreenIntent.OnOutOfCreditsSignInClick
    is OutOfCreditsBannerState.Premium -> null
}

object OutOfCreditsBannerDefaults {
    /** Screen gutter on the sides, 8dp above; the input row's own top padding gives the gap below. */
    val ContentPadding = PaddingValues(
        start = AppDimens.ScreenPaddingHorizontal,
        end = AppDimens.ScreenPaddingHorizontal,
        top = AppDimens.SpacingSm,
    )

    /**
     * [ContentPadding], plus its own 8dp below when nothing sits under the banner in the slot (a
     * pending question hides the input, whose top padding otherwise gives that gap).
     */
    fun contentPadding(inputBelow: Boolean): PaddingValues = if (inputBelow) {
        ContentPadding
    } else {
        PaddingValues(
            start = AppDimens.ScreenPaddingHorizontal,
            end = AppDimens.ScreenPaddingHorizontal,
            top = AppDimens.SpacingSm,
            bottom = AppDimens.SpacingSm,
        )
    }
}

/** Runs the nudge for every [nudgeKey] change after mount and draws the card with its frame. */
@Composable
private fun NudgingBanner(
    state: OutOfCreditsBannerState,
    nudgeKey: Int,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val mountedKey = remember { nudgeKey }
    val flash = remember { Animatable(0f) }
    // Shake progress 0..1, mapped to the 0 → −4 → 4 → −2 → 0 dp path in the draw phase (no relayout).
    val shake = remember { Animatable(1f) }
    var notSent by remember { mutableStateOf(false) }

    LaunchedEffect(nudgeKey) {
        if (nudgeKey == mountedKey) return@LaunchedEffect
        notSent = true
        if (!reducedMotion) {
            launch {
                shake.snapTo(0f)
                shake.animateTo(1f, tween(AppMotion.SpatialFastMillis, easing = LinearEasing))
            }
        }
        val edge = if (reducedMotion) AppMotion.ReducedMotionMillis else AppMotion.EffectsFastMillis
        flash.animateTo(1f, tween(edge, easing = AppMotion.EffectsEasing))
        // The flash holds so it reads as a deliberate signal, not a flicker.
        delay(AppMotion.EffectsSlowMillis.toLong())
        flash.animateTo(0f, tween(edge, easing = AppMotion.EffectsEasing))
        // The spoken "Not sent." prefix outlives the flash so TalkBack finishes the announcement.
        delay(NOT_SENT_A11Y_MILLIS - AppMotion.EffectsSlowMillis - 2L * edge)
        notSent = false
    }

    OutOfCreditsBannerCard(
        state = state,
        flash = { flash.value },
        notSent = notSent,
        onCtaClick = onCtaClick,
        modifier = modifier.graphicsLayer { translationX = shakeOffsetDp(shake.value).dp.toPx() },
    )
}

/** Piecewise-linear shake path over progress [p] 0..1: 0 → −4 → 4 → −2 → 0 dp. */
internal fun shakeOffsetDp(p: Float): Float {
    val points = SHAKE_PATH_DP
    val scaled = p.coerceIn(0f, 1f) * (points.size - 1)
    val i = scaled.toInt().coerceAtMost(points.size - 2)
    val t = scaled - i
    return points[i] + (points[i + 1] - points[i]) * t
}

private val SHAKE_PATH_DP = floatArrayOf(0f, -4f, 4f, -2f, 0f)

/**
 * The card itself, without enter/exit or nudge timing — what previews and screenshots draw.
 *
 * @param flash 0 = resting hairline, 1 = nudge peak (2dp primary outline).
 * @param notSent Prefix the spoken text with "Not sent." (right after a refused send).
 */
@Composable
internal fun OutOfCreditsBannerCard(
    state: OutOfCreditsBannerState,
    flash: () -> Float,
    notSent: Boolean,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = flash()
    val border = BorderStroke(
        width = (1f + f).dp,
        color = lerp(AppChatColors.contentOutline(), MaterialTheme.colorScheme.primary, f),
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(AppMotion.spatialFastAs())
            .testTag("chat_out_of_credits_banner"),
        shape = MaterialTheme.shapes.medium,
        color = AppChatColors.raised(),
        border = border,
    ) {
        AnimatedContent(
            targetState = state,
            // Fade only; the height change is the Surface's animateContentSize, not a SizeTransform.
            transitionSpec = {
                (fadeIn(AppMotion.effectsDefaultTween()) togetherWith fadeOut(AppMotion.effectsDefaultTween()))
                    .using(null)
            },
            label = "outOfCreditsVariant",
        ) { shown ->
            BannerContent(state = shown, notSent = notSent, onCtaClick = onCtaClick)
        }
    }
}

@Composable
private fun BannerContent(
    state: OutOfCreditsBannerState,
    notSent: Boolean,
    onCtaClick: () -> Unit,
) {
    val copy = state.bannerCopy()
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    val showBody = !(largeFont && copy.cta != null)
    val titleStyle = MaterialTheme.typography.titleSmall
    val notSentPrefix = stringResource(Res.string.chat_out_of_credits_not_sent)
    val spoken = buildString {
        if (notSent) append(notSentPrefix).append(' ')
        append(copy.title).append(". ").append(copy.body)
    }

    NoticeBannerLayout(
        modifier = Modifier
            .padding(
                start = AppDimens.SpacingMd,
                end = if (copy.cta != null) AppDimens.SpacingXs else AppDimens.SpacingMd,
                top = AppDimens.SpacingSm,
                bottom = AppDimens.SpacingSm,
            )
            .heightIn(min = AppDimens.MinTouchTarget),
        actionFit = rememberHeadlineOnOneLineFit(copy.title, titleStyle),
        stackedIconLineHeight = titleStyle.lineHeight,
        icon = if (largeFont) {
            null
        } else {
            {
                Icon(
                    imageVector = copy.icon,
                    contentDescription = null,
                    tint = copy.iconTint,
                    modifier = Modifier.size(AppDimens.IconSizeMd),
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(text = copy.title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
                if (showBody) {
                    Text(
                        text = copy.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        action = copy.cta?.let { label ->
            {
                AppButtonText(
                    text = label,
                    onClick = onCtaClick,
                    modifier = Modifier.testTag("chat_out_of_credits_cta"),
                )
            }
        },
    )
}

private class BannerCopy(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val body: String,
    val cta: String?,
)

@Composable
private fun OutOfCreditsBannerState.bannerCopy(): BannerCopy = when (this) {
    is OutOfCreditsBannerState.Free -> BannerCopy(
        icon = Icons.Outlined.AutoAwesome,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(
            if (lowBalance) Res.string.chat_out_of_credits_low_title else Res.string.chat_out_of_credits_free_title,
        ),
        body = premiumDailyCredits
            ?.let { stringResource(Res.string.chat_out_of_credits_free_body, it.toString()) }
            ?: stringResource(Res.string.chat_out_of_credits_free_body_no_amount),
        cta = stringResource(Res.string.chat_out_of_credits_cta_pro),
    )

    is OutOfCreditsBannerState.Premium -> BannerCopy(
        icon = Icons.Outlined.Schedule,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(
            if (lowBalance) Res.string.chat_out_of_credits_low_title else Res.string.chat_out_of_credits_premium_title,
        ),
        body = stringResource(Res.string.credits_refill_info),
        cta = null,
    )

    OutOfCreditsBannerState.Guest -> BannerCopy(
        icon = Icons.Outlined.AccountCircle,
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(Res.string.chat_out_of_credits_guest_title),
        body = stringResource(Res.string.chat_out_of_credits_guest_body),
        cta = stringResource(Res.string.drawer_sign_in),
    )
}

/** DESIGN_SPEC §8: from 1.5× the icon goes, and the body too when a button shares the card. */
private const val LARGE_FONT_SCALE = 1.5f

private const val NOT_SENT_A11Y_MILLIS = 2_000L
