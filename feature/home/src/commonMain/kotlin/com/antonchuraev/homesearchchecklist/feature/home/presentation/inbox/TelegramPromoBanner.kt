package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.telegram_connect_cta
import aichecklists.core.designsystem.generated.resources.telegram_promo_body
import aichecklists.core.designsystem.generated.resources.telegram_promo_body_web
import aichecklists.core.designsystem.generated.resources.telegram_promo_cta_short
import aichecklists.core.designsystem.generated.resources.telegram_promo_dismiss_cd
import aichecklists.core.designsystem.generated.resources.telegram_promo_title
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.TelegramLogo
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.InlineNudgeCard
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NudgeActionStyle
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.TILE_HIDE_FONT_SCALE
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import org.jetbrains.compose.resources.stringResource

/** `getPlatformName()` value of the wasmJs build. */
private const val PLATFORM_WEB = "web"

/** The logo inside the card's 36dp leading slot — a full-colour disc reads heavier than a glyph tile. */
private val PromoLogoSize = 32.dp

/**
 * The Inbox Telegram promo with its enter / exit motion — the `AnimatedNotificationsOffBanner`
 * recipe (spec `docs/designs/telegram-channel-banner-2026-11-21/spec.md` §4.2): visible on the first
 * frame → starts settled; reduced motion → no transition.
 */
@Composable
internal fun AnimatedTelegramPromoBanner(
    visible: Boolean,
    onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isWeb: Boolean = remember { getPlatformName() == PLATFORM_WEB },
) {
    val visibleState = remember { MutableTransitionState(initialState = visible) }
    visibleState.targetState = visible
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(AppMotion.effectsDefaultTween()) + expandVertically(AppMotion.spatialFastTween())
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(AppMotion.effectsDefaultTween()) + shrinkVertically(AppMotion.spatialDefaultTween())
        },
    ) {
        TelegramPromoBanner(isWeb = isWeb, onOpenTelegram = onOpenTelegram, onDismiss = onDismiss)
    }
}

/**
 * A suggestion, not a warning, so a quieter anatomy than the notifications banner: the whole card
 * is the tap target with the action as a link, and at fontScale ≥ 1.5 the body goes and the link
 * shortens to "Connect" (the title already names Telegram) — at 320dp × 2.0 RU the card stays
 * ≈ 190dp, so the first task is above the fold. The AI chat lives in the TITLE for that reason: the
 * body is exactly what large-font users do not get. No Stars / payment / premium wording.
 */
@Composable
internal fun TelegramPromoBanner(
    isWeb: Boolean,
    onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeFont = LocalDensity.current.fontScale >= TILE_HIDE_FONT_SCALE
    InlineNudgeCard(
        // Brand logo, untinted and without the tinted tile (spec: the real Telegram mark).
        logo = { TelegramLogo(size = PromoLogoSize) },
        title = stringResource(Res.string.telegram_promo_title),
        body = stringResource(if (isWeb) Res.string.telegram_promo_body_web else Res.string.telegram_promo_body),
        actionLabel = stringResource(
            if (largeFont) Res.string.telegram_promo_cta_short else Res.string.telegram_connect_cta,
        ),
        onAction = onOpenTelegram,
        onClick = onOpenTelegram,
        actionStyle = NudgeActionStyle.Link,
        hideBodyAtLargeFont = true,
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.telegram_promo_dismiss_cd),
        dismissTestTag = "inbox_telegram_promo_dismiss",
        rowAlignment = Alignment.Top,
        modifier = modifier.testTag("inbox_telegram_promo"),
    )
}
