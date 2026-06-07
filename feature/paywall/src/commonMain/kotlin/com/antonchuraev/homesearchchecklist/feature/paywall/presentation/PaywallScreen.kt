package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.PlatformBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_privacy
import aichecklists.core.designsystem.generated.resources.paywall_support
import aichecklists.core.designsystem.generated.resources.paywall_terms
import aichecklists.core.designsystem.generated.resources.paywall_v1_best_value_badge
import aichecklists.core.designsystem.generated.resources.paywall_v1_billed_monthly
import aichecklists.core.designsystem.generated.resources.paywall_v1_billed_annually_subtitle
import aichecklists.core.designsystem.generated.resources.paywall_v1_close_cd
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_feature
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_free
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_headline
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_pro
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_ai_requests
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_checklists
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_fills
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_recurring_reminders
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_value_free_credits
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_value_pro_credits
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_value_unlimited
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_sub_monthly
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_sub_yearly
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_start_trial
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_sub_yearly_no_trial
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_sub_monthly_no_trial
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_headline_no_trial
import aichecklists.core.designsystem.generated.resources.paywall_v1_features_headline_no_trial
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_body_no_trial
import aichecklists.core.designsystem.generated.resources.paywall_subscribe_now
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_ai_runs_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_ai_runs_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_unlimited_fills_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_unlimited_fills_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_unlimited_lists_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_unlimited_lists_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_unlimited_reminders_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_unlimited_reminders_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_features_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_features_headline
import aichecklists.core.designsystem.generated.resources.paywall_v1_how_trial_works
import aichecklists.core.designsystem.generated.resources.paywall_v1_monthly_label
import aichecklists.core.designsystem.generated.resources.paywall_v1_restore_action
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_headline
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_days_free
import aichecklists.core.designsystem.generated.resources.paywall_v1_yearly_label
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.FeatureRow
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.HeroIllustration
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.PaywallTrialTimeline
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.PlanRow
import org.jetbrains.compose.resources.stringResource

/**
 * PaywallScreen — three layout variants behind a single entry point.
 *
 * Layout: Scaffold + scrollable body + sticky bottom CTA.
 * Compliance: ctaSubtext includes "Auto-renews", trial disclosure is visible
 * in body, footer links (Terms / Privacy / Restore / Support) always rendered.
 *
 * @param state         Display state from PaywallUiState (pricing, plan, variant)
 * @param onPlanSelected Called when user taps a plan row
 * @param onStartTrial  Called when user taps the CTA button
 * @param onClose       Called when user taps the X icon
 * @param onRestore     Called when user taps Restore (top bar or footer)
 * @param onTermsClick  Called when user taps Terms of Use footer
 * @param onPrivacyClick Called when user taps Privacy Policy footer
 * @param onSupportClick Called when user taps Support footer
 * @param showHeroIllustration Toggle hero illustration at the top
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallScreen(
    state: PaywallUiState,
    onPlanSelected: (PaywallPlan) -> Unit,
    onStartTrial: () -> Unit,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onSupportClick: () -> Unit,
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
    isPurchasing: Boolean = false,
    isRestoring: Boolean = false,
    showHeroIllustration: Boolean = true,
) {
    // Block navigation while a purchase/restore is in flight: swallow system back +
    // disable the close (X). Prevents leaving mid-transaction (RevenueCat validation +
    // credit-restore Cloud Function still running after the Google Play sheet closes).
    val busy = isPurchasing || isRestoring
    PlatformBackHandler(enabled = busy) { /* swallow back while busy */ }

    // Result feedback (toast): surface restore / purchase / load errors as a Snackbar so
    // the user always sees the outcome. Success is handled by navigating to
    // SubscriptionStatusScreen, which shows its own "🎉 Welcome to Premium" message.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onErrorDismiss()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !busy) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.paywall_v1_close_cd),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRestore, enabled = !busy) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                stringResource(Res.string.paywall_v1_restore_action),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    // Disable M3 tonal-elevation overlay — keep flat surface across scroll states
                    // so the toolbar reads as a direct continuation of the body, not a separate band.
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            val ctaSub = when (state.selectedPlan) {
                PaywallPlan.Yearly  -> if (state.hasFreeTrial)
                    stringResource(Res.string.paywall_v1_cta_sub_yearly, state.yearlyPrice)
                else
                    stringResource(Res.string.paywall_v1_cta_sub_yearly_no_trial, state.yearlyPrice)
                PaywallPlan.Monthly -> if (state.hasFreeTrial)
                    stringResource(Res.string.paywall_v1_cta_sub_monthly, state.monthlyPrice)
                else
                    stringResource(Res.string.paywall_v1_cta_sub_monthly_no_trial, state.monthlyPrice)
            }
            StickyCta(
                ctaLabel = if (state.hasFreeTrial)
                    stringResource(Res.string.paywall_v1_cta_start_trial, state.trialDays)
                else
                    stringResource(Res.string.paywall_subscribe_now),
                sub = ctaSub,
                loading = isPurchasing,
                // Keep the CTA bright (not greyed) while its OWN purchase spins, so the white
                // spinner stays visible; only grey it out when a restore is the active operation.
                enabled = !isRestoring,
                onClick = onStartTrial,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        ) {
            when (state.variant) {
                PaywallVariant.Timeline -> TimelineBody(state, onPlanSelected, showHeroIllustration)
                PaywallVariant.Features -> FeaturesBody(state, onPlanSelected, showHeroIllustration)
                PaywallVariant.Compare  -> CompareBody(state, onPlanSelected, showHeroIllustration)
            }

            // ─── Footer links ──────────────────────────────────────────────
            // Restore omitted here — it's already exposed in the toolbar action,
            // a duplicate footer button was redundant. Keep Terms/Privacy/Support.
            Spacer(Modifier.height(AppDimens.SpacingMd))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                TextButton(
                    onClick = onTermsClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_terms),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onPrivacyClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_privacy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onSupportClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_support),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(AppDimens.SpacingLg))
        }
    }
}

// ─── Sticky bottom CTA ────────────────────────────────────────────────────────

@Composable
private fun StickyCta(
    ctaLabel: String,
    sub: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(
                    start = AppDimens.SpacingLg,
                    end = AppDimens.SpacingLg,
                    top = AppDimens.SpacingMd,
                    bottom = AppDimens.SpacingLg,
                ),
            ) {
                AppButton(
                    text = ctaLabel,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    loading = loading,
                    enabled = enabled,
                )
                Spacer(Modifier.height(AppDimens.SpacingSm))
                // 1-line disclosure with auto-shrink: long localized prices
                // (e.g. "10 990,00 ₸/year") would wrap or clip a fixed-size Text;
                // TextAutoSize steps the font down to 9.sp instead.
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                )
            }
        }
    }
}

// ─── Plans block — reused by all three variants ───────────────────────────────

@Composable
private fun PlansBlock(
    state: PaywallUiState,
    onPlanSelected: (PaywallPlan) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
        PlanRow(
            label    = stringResource(Res.string.paywall_v1_yearly_label),
            price    = state.yearlyPrice,
            sub      = stringResource(
                Res.string.paywall_v1_billed_annually_subtitle,
                state.yearlyMonthly,
            ),
            badge    = stringResource(Res.string.paywall_v1_best_value_badge),
            savings  = state.yearlySavings,
            selected = state.selectedPlan == PaywallPlan.Yearly,
            onClick  = { onPlanSelected(PaywallPlan.Yearly) },
        )
        PlanRow(
            label    = stringResource(Res.string.paywall_v1_monthly_label),
            price    = state.monthlyPrice,
            sub      = stringResource(Res.string.paywall_v1_billed_monthly),
            selected = state.selectedPlan == PaywallPlan.Monthly,
            onClick  = { onPlanSelected(PaywallPlan.Monthly) },
        )
    }
}

// ─── Variant A — Trial timeline focus ─────────────────────────────────────────

@Composable
private fun TimelineBody(
    state: PaywallUiState,
    onPlanSelected: (PaywallPlan) -> Unit,
    showHero: Boolean,
) {
    val cs = MaterialTheme.colorScheme

    if (showHero) HeroIllustration()
    Spacer(Modifier.height(AppDimens.SpacingMd))

    // Trial pill + headline
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.hasFreeTrial) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = cs.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = AppDimens.SpacingMd,
                        vertical = AppDimens.SpacingXs,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = cs.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(Res.string.paywall_v1_timeline_days_free, state.trialDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(AppDimens.SpacingMd))
        }
        Text(
            text = if (state.hasFreeTrial)
                stringResource(Res.string.paywall_v1_timeline_headline)
            else
                stringResource(Res.string.paywall_v1_timeline_headline_no_trial),
            style = MaterialTheme.typography.headlineMedium,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.paywall_v1_timeline_body),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
    Spacer(Modifier.height(AppDimens.SpacingXl))

    if (state.hasFreeTrial) {
        AppCard {
            Column {
                Text(
                    text = stringResource(Res.string.paywall_v1_how_trial_works),
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppDimens.SpacingMd))
                PaywallTrialTimeline()
            }
        }
        Spacer(Modifier.height(AppDimens.SpacingLg))
    }

    PlansBlock(state, onPlanSelected)
}

// ─── Variant B — Feature grid ─────────────────────────────────────────────────

@Composable
private fun FeaturesBody(
    state: PaywallUiState,
    onPlanSelected: (PaywallPlan) -> Unit,
    showHero: Boolean,
) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showHero) HeroIllustration()
        Spacer(Modifier.height(AppDimens.SpacingSm))
        Text(
            text = if (state.hasFreeTrial)
                stringResource(Res.string.paywall_v1_features_headline, state.trialDays)
            else
                stringResource(Res.string.paywall_v1_features_headline_no_trial),
            style = MaterialTheme.typography.headlineMedium,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.paywall_v1_features_body),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
    }
    Spacer(Modifier.height(AppDimens.SpacingLg))

    // Feature rows describe REAL Pro benefits (capacity, not feature exclusivity).
    // Photo/voice/PDF/link inputs are accessible on Free too — server gates by credits,
    // not by input type — so claiming them as Pro-exclusive was a lie.
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
            FeatureRow(
                icon = Icons.Filled.Bolt,
                title = stringResource(Res.string.paywall_v1_feature_ai_runs_title),
                body = stringResource(Res.string.paywall_v1_feature_ai_runs_body),
            )
            FeatureRow(
                icon = Icons.Filled.Checklist,
                title = stringResource(Res.string.paywall_v1_feature_unlimited_lists_title),
                body = stringResource(Res.string.paywall_v1_feature_unlimited_lists_body),
            )
            FeatureRow(
                icon = Icons.Filled.ContentCopy,
                title = stringResource(Res.string.paywall_v1_feature_unlimited_fills_title),
                body = stringResource(Res.string.paywall_v1_feature_unlimited_fills_body),
            )
            FeatureRow(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(Res.string.paywall_v1_feature_unlimited_reminders_title),
                body = stringResource(Res.string.paywall_v1_feature_unlimited_reminders_body),
            )
        }
    }
    Spacer(Modifier.height(AppDimens.SpacingLg))

    PlansBlock(state, onPlanSelected)
}

// ─── Variant C — Comparison table ─────────────────────────────────────────────

private data class CompareRowData(val label: String, val free: Any, val pro: Any)

@Composable
private fun CompareBody(
    state: PaywallUiState,
    onPlanSelected: (PaywallPlan) -> Unit,
    showHero: Boolean,
) {
    val cs = MaterialTheme.colorScheme

    // Only show REAL differences between Free and Pro:
    //   - AI requests: Free gets 100 starter credits ONCE (no daily refill), Pro gets 300/day
    //     (firebase-functions/main.py: DEFAULT_INITIAL_CREDITS=100, refill only for premium).
    //   - Checklists / fills / recurring reminders: hard caps on Free, unlimited on Pro
    //     (RemoteConfig: MAX_CHECKLISTS_FREE=4, MAX_FILLS_FREE=5, MAX_FREE_REPEAT_SCHEDULES=1).
    // Removed lies: photo/voice/PDF/link rows (all accessible on Free) and "best AI model"
    // (server uses gemini-2.5-flash-lite for everyone).
    val unlimited = stringResource(Res.string.paywall_v1_compare_value_unlimited)
    val rows = listOf(
        CompareRowData(
            label = stringResource(Res.string.paywall_v1_compare_row_ai_requests),
            free  = stringResource(Res.string.paywall_v1_compare_value_free_credits),
            pro   = stringResource(Res.string.paywall_v1_compare_value_pro_credits),
        ),
        CompareRowData(
            label = stringResource(Res.string.paywall_v1_compare_row_checklists),
            free  = "4",
            pro   = unlimited,
        ),
        CompareRowData(
            label = stringResource(Res.string.paywall_v1_compare_row_fills),
            free  = "5",
            pro   = unlimited,
        ),
        CompareRowData(
            label = stringResource(Res.string.paywall_v1_compare_row_recurring_reminders),
            free  = "1",
            pro   = unlimited,
        ),
    )

    if (showHero) HeroIllustration()
    Spacer(Modifier.height(AppDimens.SpacingMd))

    Text(
        text = stringResource(Res.string.paywall_v1_compare_headline),
        style = MaterialTheme.typography.headlineMedium,
        color = cs.onSurface,
    )
    Spacer(Modifier.height(AppDimens.SpacingSm))
    Text(
        text = if (state.hasFreeTrial)
            stringResource(Res.string.paywall_v1_compare_body, state.trialDays)
        else
            stringResource(Res.string.paywall_v1_compare_body_no_trial),
        style = MaterialTheme.typography.bodyMedium,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(AppDimens.SpacingLg))

    // zero contentPadding so CompareGrid rows touch the card edges
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(AppDimens.SpacingMd)) {
            // Header row
            CompareGrid(
                feature = {
                    Text(
                        stringResource(Res.string.paywall_v1_compare_feature),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                },
                free = {
                    // fillMaxWidth() is required for TextAlign.Center to actually center
                    // within the 80dp Free column. Without it, Text is sized to the word
                    // "Free" (~28dp) and sits at column start, visually offset from the
                    // centered body cells below.
                    Text(
                        stringResource(Res.string.paywall_v1_compare_free),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                pro = {
                    Surface(shape = RoundedCornerShape(8.dp), color = cs.primary) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = cs.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(Res.string.paywall_v1_compare_pro),
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.onPrimary,
                            )
                        }
                    }
                },
            )
            HorizontalDivider(color = cs.outlineVariant)

            rows.forEachIndexed { i, r ->
                CompareGrid(
                    verticalPad = 12,
                    feature = {
                        Text(r.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    },
                    free = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CompareCell(r.free, isPro = false)
                        }
                    },
                    pro = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompareCell(r.pro, isPro = true)
                        }
                    },
                )
                if (i != rows.lastIndex) HorizontalDivider(color = cs.outlineVariant)
            }
        }
    }
    Spacer(Modifier.height(AppDimens.SpacingLg))

    PlansBlock(state, onPlanSelected)
}

/**
 * 1fr | 80dp | 84dp three-column row used by the comparison table.
 *
 * Column widths tuned for the longest values in the new truthful copy:
 *  - Free column 80dp fits "100 once" (labelLarge medium, ~68dp) with buffer.
 *  - Pro column 84dp fits "Unlimited" (~64dp) with buffer.
 *  - Feature column (weight 1f) gets ~155dp on Pixel-class screens, enough for
 *    "Recurring reminders" without wrap. Old 72/88 widths were tuned for short
 *    boolean cells ("✓"/"✗"/"10"/"300") and broke when text values grew.
 */
@Composable
private fun CompareGrid(
    verticalPad: Int = 8,
    feature: @Composable () -> Unit,
    free: @Composable () -> Unit,
    pro: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPad.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f))    { feature() }
        Box(Modifier.width(80.dp))  { free() }
        Box(Modifier.width(84.dp))  { pro() }
    }
}

@Composable
private fun CompareCell(value: Any, isPro: Boolean) {
    val cs = MaterialTheme.colorScheme
    when (value) {
        is Boolean -> if (value) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isPro) cs.primary else cs.tertiary,
            )
        } else {
            Icon(Icons.Filled.Remove, contentDescription = null, tint = cs.outline)
        }
        is String -> Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPro) cs.primary else cs.onSurfaceVariant,
            fontWeight = if (isPro) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
