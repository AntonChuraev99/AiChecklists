package com.antonchuraev.homesearchchecklist.feature.voicecall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.voice_call_action_close
import aichecklists.core.designsystem.generated.resources.voice_call_action_open_chat
import aichecklists.core.designsystem.generated.resources.voice_call_action_retry
import aichecklists.core.designsystem.generated.resources.voice_call_err_flag_off_body
import aichecklists.core.designsystem.generated.resources.voice_call_err_flag_off_title
import aichecklists.core.designsystem.generated.resources.voice_call_err_no_recognizer_body
import aichecklists.core.designsystem.generated.resources.voice_call_err_no_recognizer_title
import aichecklists.core.designsystem.generated.resources.voice_call_err_no_voice_action
import aichecklists.core.designsystem.generated.resources.voice_call_err_no_voice_body
import aichecklists.core.designsystem.generated.resources.voice_call_err_no_voice_title
import aichecklists.core.designsystem.generated.resources.voice_call_err_offline_body
import aichecklists.core.designsystem.generated.resources.voice_call_err_offline_title
import aichecklists.core.designsystem.generated.resources.voice_call_err_server_body
import aichecklists.core.designsystem.generated.resources.voice_call_err_server_title
import aichecklists.core.designsystem.generated.resources.voice_call_limit_daily_body
import aichecklists.core.designsystem.generated.resources.voice_call_limit_daily_title
import aichecklists.core.designsystem.generated.resources.voice_call_limit_free_body
import aichecklists.core.designsystem.generated.resources.voice_call_limit_free_title
import aichecklists.core.designsystem.generated.resources.voice_call_limit_try_premium
import aichecklists.core.designsystem.generated.resources.voice_call_not_now
import aichecklists.core.designsystem.generated.resources.voice_call_perm_allow
import aichecklists.core.designsystem.generated.resources.voice_call_perm_blocked_body
import aichecklists.core.designsystem.generated.resources.voice_call_perm_body
import aichecklists.core.designsystem.generated.resources.voice_call_perm_open_settings
import aichecklists.core.designsystem.generated.resources.voice_call_perm_title
import aichecklists.core.designsystem.generated.resources.voice_call_summary_call_again
import aichecklists.core.designsystem.generated.resources.voice_call_summary_call_back
import aichecklists.core.designsystem.generated.resources.voice_call_summary_done
import aichecklists.core.designsystem.generated.resources.voice_call_summary_dropped_title
import aichecklists.core.designsystem.generated.resources.voice_call_summary_free_left
import aichecklists.core.designsystem.generated.resources.voice_call_summary_meta
import aichecklists.core.designsystem.generated.resources.voice_call_summary_nothing
import aichecklists.core.designsystem.generated.resources.voice_call_summary_title
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AppGradientButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import com.antonchuraev.homesearchchecklist.feature.voicecall.presentation.components.VoiceCallDefaults
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen frame (DESIGN_SPEC §1 «Полноэкранные состояния»): a 56dp spacer where the top row
 * was (the title does not jump between states), a scrolling body, and the actions PINNED at the
 * bottom in the thumb zone.
 *
 * @param centered errors and permission: body centred on both axes, text centred.
 */
@Composable
private fun FullStateFrame(
    centered: Boolean,
    actions: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(VoiceCallDefaults.TopRowHeight))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.SpacingLg),
            verticalArrangement = if (centered) {
                Arrangement.spacedBy(AppDimens.SpacingSm, Alignment.CenterVertically)
            } else {
                Arrangement.spacedBy(AppDimens.SpacingSm)
            },
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            content = body,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppDimens.SpacingLg, end = AppDimens.SpacingLg, top = AppDimens.SpacingSm, bottom = AppDimens.SpacingSm),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = actions,
        )
    }
}

@Composable
private fun IconDisc(icon: ImageVector) {
    Box(
        modifier = Modifier
            .padding(bottom = AppDimens.SpacingSm)
            .size(VoiceCallDefaults.IconDisc)
            .background(AppSurface.recessed(), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AppDimens.IconSizeLg),
        )
    }
}

@Composable
private fun FullTitle(text: String, large: Boolean, align: TextAlign) {
    Text(
        text = text,
        style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun FullBody(text: String, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Centred error-like screen: icon disc, title, body. */
@Composable
private fun CenteredState(
    icon: ImageVector,
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    FullStateFrame(centered = true, actions = actions) {
        IconDisc(icon)
        FullTitle(title, large = false, align = TextAlign.Center)
        FullBody(body, TextAlign.Center)
    }
}

@Composable
internal fun PermissionContent(state: VoiceCallUiState.Permission, onAction: (VoiceCallAction) -> Unit) {
    CenteredState(
        icon = if (state.blocked) Icons.Outlined.MicOff else Icons.Outlined.Mic,
        title = stringResource(Res.string.voice_call_perm_title),
        body = stringResource(if (state.blocked) Res.string.voice_call_perm_blocked_body else Res.string.voice_call_perm_body),
    ) {
        if (state.blocked) {
            AppButton(
                text = stringResource(Res.string.voice_call_perm_open_settings),
                onClick = { onAction(VoiceCallAction.OpenAppSettingsClicked) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AppButton(
                text = stringResource(Res.string.voice_call_perm_allow),
                onClick = { onAction(VoiceCallAction.AllowMicClicked) },
                loading = state.requesting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppButtonText(
            text = stringResource(Res.string.voice_call_not_now),
            onClick = { onAction(VoiceCallAction.NotNowClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private class FailureSpec(
    val icon: ImageVector,
    val title: StringResource,
    val body: StringResource,
    val primary: Pair<StringResource, VoiceCallAction>,
    val secondary: Pair<StringResource, VoiceCallAction>,
    /** `AppButtonSecondary` instead of `AppButtonText` (NoVoice: "Ask in chat" is a real alternative). */
    val secondaryOutlined: Boolean = false,
)

private fun failureSpec(kind: VoiceCallFailureUi): FailureSpec {
    val retry = Res.string.voice_call_action_retry to VoiceCallAction.RetryClicked
    val chat = Res.string.voice_call_action_open_chat to VoiceCallAction.OpenChatClicked
    val close = Res.string.voice_call_action_close to VoiceCallAction.CloseClicked
    return when (kind) {
        VoiceCallFailureUi.NoRecognizer -> FailureSpec(
            Icons.Outlined.VoiceOverOff,
            Res.string.voice_call_err_no_recognizer_title,
            Res.string.voice_call_err_no_recognizer_body,
            chat,
            close,
        )
        VoiceCallFailureUi.NoVoice -> FailureSpec(
            Icons.AutoMirrored.Outlined.VolumeOff,
            Res.string.voice_call_err_no_voice_title,
            Res.string.voice_call_err_no_voice_body,
            Res.string.voice_call_err_no_voice_action to VoiceCallAction.OpenVoiceSettingsClicked,
            chat,
            secondaryOutlined = true,
        )
        VoiceCallFailureUi.Offline -> FailureSpec(
            Icons.Outlined.WifiOff,
            Res.string.voice_call_err_offline_title,
            Res.string.voice_call_err_offline_body,
            retry,
            close,
        )
        VoiceCallFailureUi.Server -> FailureSpec(
            Icons.Outlined.CloudOff,
            Res.string.voice_call_err_server_title,
            Res.string.voice_call_err_server_body,
            retry,
            chat,
        )
        VoiceCallFailureUi.FlagOff -> FailureSpec(
            Icons.Outlined.PhoneDisabled,
            Res.string.voice_call_err_flag_off_title,
            Res.string.voice_call_err_flag_off_body,
            chat,
            close,
        )
    }
}

@Composable
internal fun FailureContent(kind: VoiceCallFailureUi, onAction: (VoiceCallAction) -> Unit) {
    val spec = failureSpec(kind)
    CenteredState(icon = spec.icon, title = stringResource(spec.title), body = stringResource(spec.body)) {
        AppButton(
            text = stringResource(spec.primary.first),
            onClick = { onAction(spec.primary.second) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (spec.secondaryOutlined) {
            AppButtonSecondary(
                text = stringResource(spec.secondary.first),
                onClick = { onAction(spec.secondary.second) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AppButtonText(
                text = stringResource(spec.secondary.first),
                onClick = { onAction(spec.secondary.second) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SummaryContent(state: VoiceCallUiState.Summary, compactCards: Boolean, onAction: (VoiceCallAction) -> Unit) {
    val dropped = state.reason == VoiceCallSummaryReason.Dropped
    FullStateFrame(
        centered = false,
        actions = {
            if (dropped && state.callAgainAvailable) {
                AppButton(
                    text = stringResource(Res.string.voice_call_summary_call_back),
                    onClick = { onAction(VoiceCallAction.CallAgainClicked) },
                    icon = Icons.Filled.Call,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButtonSecondary(
                    text = stringResource(Res.string.voice_call_summary_done),
                    onClick = { onAction(VoiceCallAction.CloseClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AppButton(
                    text = stringResource(Res.string.voice_call_summary_done),
                    onClick = { onAction(VoiceCallAction.CloseClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.callAgainAvailable) {
                    AppButtonSecondary(
                        text = stringResource(Res.string.voice_call_summary_call_again),
                        onClick = { onAction(VoiceCallAction.CallAgainClicked) },
                        icon = Icons.Filled.Call,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.freeSecondsLeft?.let {
                Text(
                    text = stringResource(Res.string.voice_call_summary_free_left, formatCallClock(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        FullTitle(
            text = stringResource(
                if (dropped) Res.string.voice_call_summary_dropped_title else Res.string.voice_call_summary_title,
            ),
            large = true,
            align = TextAlign.Start,
        )
        FullBody(
            text = pluralStringResource(
                Res.plurals.voice_call_summary_meta,
                state.actions.size,
                formatCallClock(state.durationSec),
                state.actions.size,
            ),
            align = TextAlign.Start,
        )
        Spacer(Modifier.height(AppDimens.SpacingSm))
        ActionList(state.actions, compactCards, onAction)
    }
}

@Composable
internal fun LimitContent(state: VoiceCallUiState.LimitReached, compactCards: Boolean, onAction: (VoiceCallAction) -> Unit) {
    val free = state.plan == VoicePlanUi.Free
    FullStateFrame(
        centered = false,
        actions = {
            if (free) {
                AppGradientButton(
                    text = stringResource(Res.string.voice_call_limit_try_premium),
                    onClick = { onAction(VoiceCallAction.TryPremiumClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButtonText(
                    text = stringResource(Res.string.voice_call_action_close),
                    onClick = { onAction(VoiceCallAction.CloseClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AppButton(
                    text = stringResource(Res.string.voice_call_summary_done),
                    onClick = { onAction(VoiceCallAction.CloseClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButtonText(
                    text = stringResource(Res.string.voice_call_action_open_chat),
                    onClick = { onAction(VoiceCallAction.OpenChatClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        IconDisc(Icons.Outlined.HourglassBottom)
        FullTitle(
            text = stringResource(if (free) Res.string.voice_call_limit_free_title else Res.string.voice_call_limit_daily_title),
            large = true,
            align = TextAlign.Start,
        )
        val body = when {
            free -> stringResource(Res.string.voice_call_limit_free_body)
            state.resetsAtLocalTime != null -> stringResource(Res.string.voice_call_limit_daily_body, state.resetsAtLocalTime)
            else -> null
        }
        body?.let { FullBody(it, TextAlign.Start) }
        if (state.actions.isNotEmpty()) {
            Spacer(Modifier.height(AppDimens.SpacingSm))
            ActionList(state.actions, compactCards, onAction)
        }
    }
}

@Composable
private fun ActionList(actions: List<VoiceActionUi>, compactCards: Boolean, onAction: (VoiceCallAction) -> Unit) {
    if (actions.isEmpty()) {
        FullBody(stringResource(Res.string.voice_call_summary_nothing), TextAlign.Start)
    } else {
        actions.forEach { ActionCard(action = it, compact = compactCards, onAction = onAction) }
    }
}
