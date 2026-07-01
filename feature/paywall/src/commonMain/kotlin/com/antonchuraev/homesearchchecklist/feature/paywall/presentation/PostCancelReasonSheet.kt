package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonText
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AdaptiveSheetOrDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Neutral, one-tap reason picker shown after the user dismisses the native purchase sheet
 * ([PurchaseResult.Cancelled]). Purely a measurement surface — no re-sell, no dark pattern.
 *
 * Public (not `internal`) and parameterized on primitives (not PaywallState/Intent) so the debug
 * screen can render it standalone without depending on the paywall MVI contract.
 *
 * @param stage [CancelReasonStage.Asking] shows the chips; [CancelReasonStage.Thanks] a short
 *   confirmation (the host auto-dismisses after a delay).
 * @param onSelectReason a chip was tapped.
 * @param onDismiss "Not now" or swipe/outside dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCancelReasonSheet(
    stage: CancelReasonStage,
    onSelectReason: (CancelReason) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // title = null: the heading lives inside the content so it is not duplicated on Expanded
    // (tablet/web) where AdaptiveSheetOrDialog renders an AlertDialog with its own title slot.
    AdaptiveSheetOrDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        title = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) + slideInVertically { it / 4 })
                        .togetherWith(fadeOut(tween(90)))
                        .using(SizeTransform(clip = false))
                },
                label = "cancel_reason_stage",
            ) { currentStage ->
                when (currentStage) {
                    CancelReasonStage.Asking -> CancelReasonAsking(
                        onSelectReason = onSelectReason,
                        onDismiss = onDismiss,
                    )

                    CancelReasonStage.Thanks -> CancelReasonThanks()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelReasonAsking(
    onSelectReason: (CancelReason) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.cancel_reason_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AppDimens.SpacingSm))

        Text(
            text = stringResource(Res.string.cancel_reason_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AppDimens.SpacingMd))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                AppDimens.SpacingSm,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Material3 chips apply minimumInteractiveComponentSize internally → 48dp touch target
            // while the visual height stays 32dp. entries order = the design order.
            CancelReason.entries.forEach { reason ->
                SuggestionChip(
                    onClick = { onSelectReason(reason) },
                    label = {
                        Text(
                            text = stringResource(reason.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = reason.iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            // Payment routes to Support → keep it accented so it reads as actionable;
                            // the other six stay neutral so seven icons don't wall the sheet in blue.
                            tint = if (reason == CancelReason.PAYMENT_ISSUE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(AppDimens.SpacingSm))

        AppButtonText(
            text = stringResource(Res.string.cancel_reason_dismiss),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun CancelReasonThanks() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )

        Spacer(Modifier.height(AppDimens.SpacingMd))

        Text(
            text = stringResource(Res.string.cancel_reason_thanks),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Maps each [CancelReason] to its localized label. Kept in the UI layer (not on the enum) so the
 * enum stays Compose-Resources-free. Adding a reason? Update this `when` too (it is exhaustive, so
 * the compiler forces you) AND strings.xml (EN + values-ru).
 */
private val CancelReason.labelRes: StringResource
    get() = when (this) {
        CancelReason.EXPENSIVE -> Res.string.cancel_reason_expensive
        CancelReason.JUST_LOOKING -> Res.string.cancel_reason_just_looking
        CancelReason.NEED_INFO -> Res.string.cancel_reason_need_info
        CancelReason.SUBSCRIPTIONS -> Res.string.cancel_reason_subscriptions
        CancelReason.MISSING_FEATURE -> Res.string.cancel_reason_missing_feature
        CancelReason.PAYMENT_ISSUE -> Res.string.cancel_reason_payment_issue
        CancelReason.OTHER -> Res.string.cancel_reason_other
    }

/**
 * Leading icon per reason. Vector Material icons (not unicode emoji) so they render identically on
 * Android and wasmJs/Skiko — color-emoji glyphs are unreliable on the web canvas. Exhaustive `when`
 * forces an icon when a reason is added.
 */
private val CancelReason.iconVector: ImageVector
    get() = when (this) {
        CancelReason.EXPENSIVE -> Icons.Outlined.Payments
        CancelReason.JUST_LOOKING -> Icons.Outlined.Visibility
        CancelReason.NEED_INFO -> Icons.Outlined.Info
        CancelReason.SUBSCRIPTIONS -> Icons.Outlined.Autorenew
        CancelReason.MISSING_FEATURE -> Icons.Outlined.Extension
        CancelReason.PAYMENT_ISSUE -> Icons.Outlined.SupportAgent
        CancelReason.OTHER -> Icons.Outlined.MoreHoriz
    }
