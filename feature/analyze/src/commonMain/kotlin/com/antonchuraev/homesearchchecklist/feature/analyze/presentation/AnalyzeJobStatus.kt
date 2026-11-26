package com.antonchuraev.homesearchchecklist.feature.analyze.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.analyze_change_input
import aichecklists.core.designsystem.generated.resources.analyze_continue_in_background
import aichecklists.core.designsystem.generated.resources.analyze_failed_title
import aichecklists.core.designsystem.generated.resources.analyze_jobs_in_progress
import aichecklists.core.designsystem.generated.resources.analyze_loading
import aichecklists.core.designsystem.generated.resources.analyze_loading_description
import aichecklists.core.designsystem.generated.resources.analyze_loading_slow
import aichecklists.core.designsystem.generated.resources.analyze_try_again
import aichecklists.core.designsystem.generated.resources.analyze_failed_get_pro
import aichecklists.core.designsystem.generated.resources.analyze_waiting_leave_hint
import aichecklists.core.designsystem.generated.resources.analyze_waiting_network_description
import aichecklists.core.designsystem.generated.resources.analyze_waiting_network_title
import aichecklists.core.designsystem.generated.resources.analyze_waiting_notify_rationale
import aichecklists.core.designsystem.generated.resources.cancel
import aichecklists.core.designsystem.generated.resources.ok
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppPlanNudgeDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppSurface
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Disc behind the phase icon of [JobStatusBody] (the former LoadingContent geometry). */
private val StatusDiscSize = 88.dp
private val StatusIconSize = 44.dp
private val StatusSpinnerStroke = 3.dp
private val InProgressSpinnerStroke = 2.dp
private val InProgressRowMinHeight = 40.dp

/**
 * Centered body of a running or failed analysis: disc + icon, title, text, and a spinner while the
 * job runs. Scrolls instead of sliding under the dock at fontScale 2.0, and still centres when it
 * fits — `heightIn(min = maxHeight)` after `verticalScroll` (a plain fillMaxSize does not centre
 * inside a scroll).
 *
 * [slowHint] swaps the Waiting subtitle for "Large files can take up to a minute" (decided by the
 * caller's 15 s timer).
 */
@Composable
internal fun JobStatusBody(phase: AnalyzeJobPhase, slowHint: Boolean) {
    val failed = phase as? AnalyzeJobPhase.Failed
    val networkWait = (phase as? AnalyzeJobPhase.Waiting)?.waitingForNetwork == true

    val icon: ImageVector
    val iconTint: Color
    val discColor: Color
    val title: String
    val body: String
    when {
        failed != null -> {
            icon = Icons.Outlined.ErrorOutline
            iconTint = MaterialTheme.colorScheme.error
            discColor = AppSurface.recessed()
            title = stringResource(Res.string.analyze_failed_title)
            body = failed.message
        }
        networkWait -> {
            icon = Icons.Outlined.CloudOff
            iconTint = MaterialTheme.colorScheme.primary
            discColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            title = stringResource(Res.string.analyze_waiting_network_title)
            body = stringResource(Res.string.analyze_waiting_network_description)
        }
        else -> {
            icon = Icons.Outlined.AutoAwesome
            iconTint = MaterialTheme.colorScheme.primary
            discColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            title = stringResource(Res.string.analyze_loading)
            body = stringResource(
                if (slowHint) Res.string.analyze_loading_slow else Res.string.analyze_loading_description,
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                // Width cap BEFORE fillMaxWidth: the other way round the cap is ignored at 840dp.
                .adaptiveContentWidth()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal, vertical = AppDimens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(StatusDiscSize)
                    .clip(CircleShape)
                    .background(discColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(StatusIconSize),
                    tint = iconTint,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    // Announced once per phase change; the 15 s subtitle swap is not announced.
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (failed == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimens.IconSizeLg),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = StatusSpinnerStroke,
                )
            }
        }
    }
}

/**
 * Bottom dock shared by every phase: capped at the content width and centred (cap BEFORE
 * fillMaxWidth — otherwise the button stretched to 808dp at 840).
 */
@Composable
internal fun AnalyzeDock(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .adaptiveContentWidth()
                .fillMaxWidth()
                .padding(AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingLg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** Waiting dock: the promise (or the notification rationale), "Continue in background", "Cancel". */
@Composable
internal fun ColumnScope.WaitingDockContent(
    canCancel: Boolean,
    showNotificationRationale: Boolean,
    onContinueInBackground: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = stringResource(
            if (showNotificationRationale) {
                Res.string.analyze_waiting_notify_rationale
            } else {
                Res.string.analyze_waiting_leave_hint
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    AppButtonSecondary(
        text = stringResource(Res.string.analyze_continue_in_background),
        onClick = onContinueInBackground,
        modifier = Modifier.fillMaxWidth().testTag("analyze_continue_in_background"),
    )
    if (canCancel) {
        AppButtonText(
            text = stringResource(Res.string.cancel),
            onClick = onCancel,
            modifier = Modifier.testTag("analyze_cancel_waiting"),
        )
    }
}

/** Failed dock: the primary action of [cta] and, for a retryable failure, "Change input" under it. */
@Composable
internal fun ColumnScope.FailedDockContent(
    cta: FailedCta,
    onRetry: () -> Unit,
    onBackToEditing: () -> Unit,
    onUpgrade: () -> Unit,
) {
    when (cta) {
        FailedCta.Retry -> {
            AppButton(
                text = stringResource(Res.string.analyze_try_again),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag("analyze_retry"),
            )
            AppButtonText(
                text = stringResource(Res.string.analyze_change_input),
                onClick = onBackToEditing,
                modifier = Modifier.testTag("analyze_change_input"),
            )
        }
        FailedCta.ChangeInput -> AppButton(
            text = stringResource(Res.string.analyze_change_input),
            onClick = onBackToEditing,
            modifier = Modifier.fillMaxWidth().testTag("analyze_change_input"),
        )
        FailedCta.Dismiss -> AppButton(
            text = stringResource(Res.string.ok),
            onClick = onBackToEditing,
            modifier = Modifier.fillMaxWidth().testTag("analyze_failed_ok"),
        )
        // Out of credits (Free): the way out is Pro; "OK" returns to the form, laid out like Retry's
        // primary + text pair so the failed dock keeps one rhythm across reasons.
        FailedCta.Upgrade -> {
            AppButton(
                text = stringResource(Res.string.analyze_failed_get_pro),
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth().testTag("analyze_failed_upgrade"),
            )
            AppButtonText(
                text = stringResource(Res.string.ok),
                onClick = onBackToEditing,
                modifier = Modifier.testTag("analyze_failed_ok"),
            )
        }
    }
}

/**
 * "N analyses in progress" — other jobs still running while this form is open. Informational only:
 * not clickable, no live region (it would chatter on every count change).
 */
@Composable
internal fun JobsInProgressRow(count: Int, modifier: Modifier = Modifier) {
    // Keep the last positive count so the fade-out does not flash "0 analyses in progress".
    var shownCount by remember { mutableIntStateOf(count) }
    if (count > 0) shownCount = count

    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = AppPlanNudgeDefaults.Enter,
        exit = AppPlanNudgeDefaults.Exit,
    ) {
        val text = pluralStringResource(Res.plurals.analyze_jobs_in_progress, shownCount, shownCount)
        Surface(
            color = AppSurface.recessed(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = text },
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = InProgressRowMinHeight)
                    .padding(horizontal = AppDimens.SpacingMd, vertical = AppDimens.SpacingSm),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppDimens.IconSizeSm),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = InProgressSpinnerStroke,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
