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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_privacy
import aichecklists.core.designsystem.generated.resources.paywall_restore
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
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_ai_checklists
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_pdf_link
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_photo
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_priority_ai
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_reminders
import aichecklists.core.designsystem.generated.resources.paywall_v1_compare_row_voice
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_sub_monthly
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_sub_yearly
import aichecklists.core.designsystem.generated.resources.paywall_v1_cta_start_trial
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_ai_runs_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_ai_runs_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_link_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_link_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_pdf_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_pdf_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_photo_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_photo_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_voice_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_feature_voice_title
import aichecklists.core.designsystem.generated.resources.paywall_v1_features_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_features_headline
import aichecklists.core.designsystem.generated.resources.paywall_v1_how_trial_works
import aichecklists.core.designsystem.generated.resources.paywall_v1_monthly_label
import aichecklists.core.designsystem.generated.resources.paywall_v1_restore_action
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_body
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_headline
import aichecklists.core.designsystem.generated.resources.paywall_v1_timeline_days_free
import aichecklists.core.designsystem.generated.resources.paywall_v1_yearly_label
import aichecklists.core.designsystem.generated.resources.paywall_trial_terms_monthly
import aichecklists.core.designsystem.generated.resources.paywall_trial_terms_yearly
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
    showHeroIllustration: Boolean = true,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.paywall_v1_close_cd),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRestore) {
                        Text(
                            stringResource(Res.string.paywall_v1_restore_action),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            val ctaSub = when (state.selectedPlan) {
                PaywallPlan.Yearly  -> stringResource(Res.string.paywall_v1_cta_sub_yearly, state.yearlyPrice)
                PaywallPlan.Monthly -> stringResource(Res.string.paywall_v1_cta_sub_monthly, state.monthlyPrice)
            }
            StickyCta(
                ctaLabel = stringResource(Res.string.paywall_v1_cta_start_trial, state.trialDays),
                sub = ctaSub,
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

            // ─── Compliance disclosure ─────────────────────────────────────
            Spacer(Modifier.height(AppDimens.SpacingMd))
            val trialTerms = when (state.selectedPlan) {
                PaywallPlan.Yearly  -> stringResource(
                    Res.string.paywall_trial_terms_yearly,
                    state.trialDays,
                    state.yearlyPrice,
                )
                PaywallPlan.Monthly -> stringResource(
                    Res.string.paywall_trial_terms_monthly,
                    state.trialDays,
                    state.monthlyPrice,
                )
            }
            Text(
                text = trialTerms,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ─── Footer links ──────────────────────────────────────────────
            Spacer(Modifier.height(AppDimens.SpacingXs))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                TextButton(
                    onClick = onTermsClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 28.dp),
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
                    modifier = Modifier.heightIn(min = 28.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_privacy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onRestore,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 28.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.paywall_restore),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onSupportClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 28.dp),
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
private fun StickyCta(ctaLabel: String, sub: String, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
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
                )
                Spacer(Modifier.height(AppDimens.SpacingSm))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 2,
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
            sub      = stringResource(Res.string.paywall_v1_billed_annually_subtitle),
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
        Text(
            text = stringResource(Res.string.paywall_v1_timeline_headline),
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
            text = stringResource(Res.string.paywall_v1_features_headline, state.trialDays),
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

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
            FeatureRow(
                icon = Icons.Filled.PhotoCamera,
                title = stringResource(Res.string.paywall_v1_feature_photo_title),
                body = stringResource(Res.string.paywall_v1_feature_photo_body),
                accentBg = cs.primaryContainer,
            )
            FeatureRow(
                icon = Icons.Filled.Mic,
                title = stringResource(Res.string.paywall_v1_feature_voice_title),
                body = stringResource(Res.string.paywall_v1_feature_voice_body),
                accentBg = cs.tertiaryContainer,
            )
            FeatureRow(
                icon = Icons.Filled.PictureAsPdf,
                title = stringResource(Res.string.paywall_v1_feature_pdf_title),
                body = stringResource(Res.string.paywall_v1_feature_pdf_body),
                accentBg = cs.secondaryContainer,
            )
            FeatureRow(
                icon = Icons.Filled.Link,
                title = stringResource(Res.string.paywall_v1_feature_link_title),
                body = stringResource(Res.string.paywall_v1_feature_link_body),
                accentBg = cs.primaryContainer,
            )
            FeatureRow(
                icon = Icons.Filled.AllInclusive,
                title = stringResource(Res.string.paywall_v1_feature_ai_runs_title),
                body = stringResource(Res.string.paywall_v1_feature_ai_runs_body),
                accentBg = cs.tertiaryContainer,
            )
            // Sync removed — mobile-only product, no cross-device sync exists
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

    val rows = listOf(
        // Free limit: 10/day, Pro limit: 300/day (per RemoteConfig: AI_DAILY_LIMIT_FREE=10, AI_DAILY_LIMIT_PREMIUM=300)
        CompareRowData(stringResource(Res.string.paywall_v1_compare_row_ai_checklists), "10", "300"),
        CompareRowData(stringResource(Res.string.paywall_v1_compare_row_photo),       true,  true),
        CompareRowData(stringResource(Res.string.paywall_v1_compare_row_voice),       false, true),
        CompareRowData(stringResource(Res.string.paywall_v1_compare_row_pdf_link),    false, true),
        CompareRowData(stringResource(Res.string.paywall_v1_compare_row_reminders),   false, true),
        // Sync row removed — no cross-device sync in product
        CompareRowData(stringResource(Res.string.paywall_v1_compare_row_priority_ai), false, true),
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
        text = stringResource(Res.string.paywall_v1_compare_body, state.trialDays),
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
                    Text(
                        stringResource(Res.string.paywall_v1_compare_free),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
 * 1fr | 72dp | 88dp three-column row used by the comparison table.
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
        Box(Modifier.width(72.dp))  { free() }
        Box(Modifier.width(88.dp))  { pro() }
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
