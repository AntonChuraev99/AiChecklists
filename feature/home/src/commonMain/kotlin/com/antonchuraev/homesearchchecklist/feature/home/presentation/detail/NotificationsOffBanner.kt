package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.notif_banner_body
import aichecklists.core.designsystem.generated.resources.notif_banner_body_reminder
import aichecklists.core.designsystem.generated.resources.notif_banner_body_settings
import aichecklists.core.designsystem.generated.resources.notif_banner_cta
import aichecklists.core.designsystem.generated.resources.notif_banner_dismiss
import aichecklists.core.designsystem.generated.resources.notif_banner_title
import aichecklists.core.designsystem.generated.resources.notif_banner_title_reminder
import aichecklists.core.designsystem.generated.resources.notif_cta_settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppMotion
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion
import org.jetbrains.compose.resources.stringResource

/**
 * "Notifications are off" banner on checklist detail, with its enter / exit motion.
 *
 * [banner] null = hidden. The last non-null value is kept for the exit animation, so the card does
 * not lose its copy while it shrinks away after the user grants the permission. If the banner is
 * already visible on the first frame (resolved in the same loading phase as the checklist) it
 * starts settled — no pop-in on screen open.
 *
 * Stateless otherwise: [onCta] must call `NotificationPermissionController.request()` and forward
 * the returned action as `OnNotificationsBannerCta`, [onDismiss] sends `OnNotificationsBannerDismiss`.
 */
@Composable
internal fun AnimatedNotificationsOffBanner(
    banner: NotificationsBannerUi?,
    onCta: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember { MutableTransitionState(initialState = banner != null) }
    visibleState.targetState = banner != null
    var lastBanner by remember { mutableStateOf(banner) }
    if (banner != null) lastBanner = banner

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
        lastBanner?.let { shown ->
            NotificationsOffBanner(banner = shown, onCta = onCta, onDismiss = onDismiss)
        }
    }
}

/**
 * The banner card itself. Copy per DESIGN_SPEC state matrix: reminder variant swaps title / body,
 * permanently-denied swaps body + CTA to the settings route.
 */
@Composable
internal fun NotificationsOffBanner(
    banner: NotificationsBannerUi,
    onCta: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (banner.reminderVariant) Res.string.notif_banner_title_reminder else Res.string.notif_banner_title
    val body = when {
        banner.settingsRequired -> Res.string.notif_banner_body_settings
        banner.reminderVariant -> Res.string.notif_banner_body_reminder
        else -> Res.string.notif_banner_body
    }
    val cta = if (banner.settingsRequired) Res.string.notif_cta_settings else Res.string.notif_banner_cta

    InlineNudgeCard(
        icon = Icons.Outlined.NotificationsOff,
        title = stringResource(title),
        body = stringResource(body),
        actionLabel = stringResource(cta),
        onAction = onCta,
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.notif_banner_dismiss),
        rowAlignment = Alignment.Top,
        modifier = modifier,
    )
}
