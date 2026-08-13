package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_save_percent
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * PaywallRoute — wires ViewModel, analytics, navigation and URI handling.
 *
 * The Screen layer is kept pure (no DI); all side-effects live here.
 */
@Composable
fun PaywallRoute(
    sourceOverride: String? = null,
    forceVariant: String? = null,
    onPurchaseSuccess: () -> Unit = {},
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    val uriHandler = LocalUriHandler.current

    // Web target replaces the paywall with an "install the mobile app" CTA:
    // RevenueCat IAP isn't available in the browser. Done before VM injection
    // so we don't spin up a PaywallViewModel + offerings flow we won't use.
    if (isWebPaywallTarget) {
        val navigator: AppNavigator = koinInject()
        // The only paywall_shown emitted outside PaywallViewModel: this branch returns BEFORE the
        // VM is injected, so no VM exists to emit it. Tagged web_install so it can be excluded —
        // it is an impression that can never convert (no products, no billing in the browser),
        // and leaving it untagged in the denominator silently depresses every conversion rate.
        LaunchedEffect(Unit) {
            analyticsTracker.screenView(AnalyticsScreens.PAYWALL_WEB_INSTALL)
            analyticsTracker.event(
                AnalyticsEvents.Paywall.SHOWN,
                mapOf(
                    AnalyticsParams.SOURCE to (sourceOverride ?: "unknown"),
                    AnalyticsParams.SURFACE to AnalyticsEvents.Paywall.SURFACE_WEB_INSTALL,
                ),
            )
        }
        WebInstallAppScreen(
            onClose = {
                analyticsTracker.event(
                    "paywall_web_install_closed",
                    emptyMap(),
                )
                navigator.onBack()
            },
            onInstallAndroidClick = {
                analyticsTracker.event(
                    "paywall_web_install_android_clicked",
                    mapOf("destination" to "google_play"),
                )
                uriHandler.openUri(GISTI_GOOGLE_PLAY_URL)
            },
        )
        return
    }

    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.PAYWALL) }

    val viewModel: PaywallViewModel = koinViewModel(key = "paywall_${sourceOverride}_$forceVariant") { parametersOf(sourceOverride, forceVariant) }
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    // paywall_shown is NOT emitted here — PaywallViewModel.init owns it (next to paywall_opened,
    // from one shared param map). Emitting it from this Route was the funnel bug: the two
    // onboarding paywall hosts drive the SAME ViewModel but never compose this Route, so they
    // produced purchase taps with no matching impression and "paywall → tap" read ~71%.
    // screen_view (above) stays for GA4 screen-tracking.

    // Navigate away on purchase success (handled by ViewModel in Phase 2)
    LaunchedEffect(state.purchaseSuccess) {
        if (state.purchaseSuccess) onPurchaseSuccess()
    }

    // Rapid-tap guard: Close fires navigator.onBack(); two taps within ~50ms
    // would pop twice and escape the backstack past Main into blank.
    var closeConsumed by remember { mutableStateOf(false) }

    // Product matching, price derivation and the "is there actually an offer?" decision all live in
    // buildPaywallUiState — pure, and therefore unit-tested (PaywallUiStateMapperTest). This Route
    // only resolves the one piece the mapper cannot: the localized savings badge, since
    // stringResource is @Composable-only.
    val uiState = buildPaywallUiState(
        state = state,
        savingsLabel = yearlySavingsPercent(state)?.let { pct ->
            stringResource(Res.string.paywall_save_percent, pct)
        },
    )

    PaywallScreen(
        state        = uiState,
        isPurchasing = state.isPurchasing,
        isRestoring  = state.isRestoring,
        onPlanSelected = { viewModel.sendIntent(PaywallIntent.SelectPlan(it)) },
        onStartTrial = { viewModel.sendIntent(PaywallIntent.Purchase) },
        onClose      = {
            if (closeConsumed) return@PaywallScreen
            closeConsumed = true
            analyticsTracker.event(
                AnalyticsEvents.Paywall.CLOSED,
                mapOf(AnalyticsParams.SOURCE to state.source),
            )
            viewModel.sendIntent(PaywallIntent.Close)
        },
        onRestore    = { viewModel.sendIntent(PaywallIntent.RestorePurchases) },
        onTermsClick = {
            analyticsTracker.event(
                AnalyticsEvents.Paywall.TERMS_CLICKED,
                mapOf(AnalyticsParams.SOURCE to state.source),
            )
            uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL)
        },
        onPrivacyClick = {
            analyticsTracker.event(
                AnalyticsEvents.Paywall.PRIVACY_CLICKED,
                mapOf(AnalyticsParams.SOURCE to state.source),
            )
            uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL)
        },
        onSupportClick = {
            analyticsTracker.event(
                AnalyticsEvents.Paywall.SUPPORT_CLICKED,
                mapOf(AnalyticsParams.SOURCE to state.source),
            )
            uriHandler.openUri("mailto:${PaywallConfig.SUPPORT_EMAIL}")
        },
        errorMessage = state.error,
        onErrorDismiss = { viewModel.sendIntent(PaywallIntent.DismissError) },
    )

    // Post-cancel reason picker — shown once per app session after a cancelled purchase, as a
    // sibling overlay (ModalBottomSheet / AlertDialog) on top of the paywall.
    if (state.showCancelReasonSheet) {
        PostCancelReasonSheet(
            stage = state.cancelReasonStage,
            onSelectReason = { viewModel.sendIntent(PaywallIntent.SelectCancelReason(it)) },
            onDismiss = { viewModel.sendIntent(PaywallIntent.DismissCancelReason) },
        )
    }

    // "Payment issue" routes to Support: the VM flags the request (UriHandler-free), the Route
    // opens mailto and clears the flag so it fires exactly once.
    LaunchedEffect(state.openSupportRequested) {
        if (state.openSupportRequested) {
            analyticsTracker.event(
                AnalyticsEvents.Paywall.SUPPORT_CLICKED,
                mapOf(AnalyticsParams.SOURCE to state.source),
            )
            uriHandler.openUri("mailto:${PaywallConfig.SUPPORT_EMAIL}")
            viewModel.sendIntent(PaywallIntent.ConsumeSupportRequest)
        }
    }
}

