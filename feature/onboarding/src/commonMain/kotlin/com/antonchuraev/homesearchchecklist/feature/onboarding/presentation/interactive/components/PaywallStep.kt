package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.components

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_start_free_trial
import aichecklists.core.designsystem.generated.resources.paywall_subscribe_now
import aichecklists.core.designsystem.generated.resources.paywall_days_free
import aichecklists.core.designsystem.generated.resources.paywall_then_price
import aichecklists.core.designsystem.generated.resources.paywall_price_per_month
import aichecklists.core.designsystem.generated.resources.paywall_auto_renew_notice
import aichecklists.core.designsystem.generated.resources.paywall_terms
import aichecklists.core.designsystem.generated.resources.paywall_privacy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.PremiumBenefitsIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.sharedUI.TrialTimeline
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallIntent
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun PaywallStep(
    paywallViewModel: PaywallViewModel?,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paywallState = paywallViewModel?.screenState?.collectAsState()?.value
    val product = paywallState?.products?.firstOrNull()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        // Illustration
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            PremiumBenefitsIllustration()
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Legal links
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_terms),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "\u2022",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 8.sp,
                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Price info
        if (paywallState?.isLoading == true) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (product != null) {
            if (product.hasFreeTrial) {
                Text(
                    text = stringResource(Res.string.paywall_days_free, product.freeTrialDays),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = if (product.hasFreeTrial)
                    stringResource(Res.string.paywall_then_price, product.priceString)
                else
                    stringResource(Res.string.paywall_price_per_month, product.priceString),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (product.hasFreeTrial) {
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                Text(
                    text = stringResource(Res.string.paywall_auto_renew_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                TrialTimeline(
                    trialDays = product.freeTrialDays,
                    priceString = product.priceString,
                    primaryColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Purchase button
        Button(
            // Stay primary-colored while purchasing so the white spinner stays visible;
            // taps are swallowed by the guard instead of greying the button out.
            onClick = { if (paywallState?.isPurchasing != true) paywallViewModel?.sendIntent(PaywallIntent.Purchase) },
            enabled = product != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.ButtonHeight),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            if (paywallState?.isPurchasing == true) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (product?.hasFreeTrial == true) {
                        stringResource(Res.string.paywall_start_free_trial)
                    } else {
                        stringResource(Res.string.paywall_subscribe_now)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
    }
}
