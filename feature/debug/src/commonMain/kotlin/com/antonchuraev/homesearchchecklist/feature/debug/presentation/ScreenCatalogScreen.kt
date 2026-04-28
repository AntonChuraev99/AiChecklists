package com.antonchuraev.homesearchchecklist.feature.debug.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ScreenCatalogScreen(
    viewModel: ScreenCatalogViewModel = koinViewModel()
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()

    AppScaffold(
        title = stringResource(Res.string.debug_catalog_title),
        onBackButtonClick = { viewModel.sendIntent(ScreenCatalogIntent.OnBack) }
    ) {
        // Column + verticalScroll (instead of LazyColumn) so every catalog button is
        // composed at once. Compose Testing's performScrollTo() can only locate nodes
        // that are present in the semantic tree — LazyColumn's recycling makes
        // off-screen testTags invisible to the test, which broke the screenshot harness.
        // 24 simple cards is well below any perf threshold.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {

            // ── Setup State ────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_state))

            // Seed summary — test waits on this Text before tapping navigate buttons
            Text(
                text = state.seedSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_seed_summary")
                    .padding(vertical = AppDimens.SpacingXs)
            )

            CatalogItem(
                testTag = "catalog_state_empty",
                title = stringResource(Res.string.debug_catalog_state_empty),
                subtitle = stringResource(Res.string.debug_catalog_state_empty_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.ResetToEmpty) }
            )

            CatalogItem(
                testTag = "catalog_state_with_data",
                title = stringResource(Res.string.debug_catalog_state_with_data),
                subtitle = stringResource(Res.string.debug_catalog_state_with_data_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.SeedWithData) }
            )

            CatalogItem(
                testTag = "catalog_state_free_limit",
                title = stringResource(Res.string.debug_catalog_state_free_limit),
                subtitle = stringResource(Res.string.debug_catalog_state_free_limit_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.SeedWithFreeLimit) }
            )

            CatalogItem(
                testTag = "catalog_state_premium",
                title = stringResource(Res.string.debug_catalog_state_premium),
                subtitle = stringResource(Res.string.debug_catalog_state_premium_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.SeedAsPremium) }
            )

            // ── Lifecycle ─────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_lifecycle))

            CatalogItem(
                testTag = "catalog_onboarding",
                title = stringResource(Res.string.debug_catalog_onboarding),
                subtitle = stringResource(Res.string.debug_catalog_onboarding_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateOnboarding) }
            )

            CatalogItem(
                testTag = "catalog_interactive_onboarding",
                title = stringResource(Res.string.debug_catalog_interactive_onboarding),
                subtitle = stringResource(Res.string.debug_catalog_interactive_onboarding_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateInteractiveOnboarding) }
            )

            CatalogItem(
                testTag = "catalog_main",
                title = stringResource(Res.string.debug_catalog_main),
                subtitle = stringResource(Res.string.debug_catalog_main_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateMain) }
            )

            // ── Create ────────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_create))

            CatalogItem(
                testTag = "catalog_templates",
                title = stringResource(Res.string.debug_catalog_templates),
                subtitle = stringResource(Res.string.debug_catalog_templates_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateTemplates) }
            )

            CatalogItem(
                testTag = "catalog_template_preview",
                title = stringResource(Res.string.debug_catalog_template_preview),
                subtitle = stringResource(Res.string.debug_catalog_template_preview_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateTemplatePreview) }
            )

            CatalogItem(
                testTag = "catalog_create_new",
                title = stringResource(Res.string.debug_catalog_create_new),
                subtitle = stringResource(Res.string.debug_catalog_create_new_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateCreateNew) }
            )

            CatalogItem(
                testTag = "catalog_create_edit",
                title = stringResource(Res.string.debug_catalog_create_edit),
                subtitle = stringResource(Res.string.debug_catalog_create_edit_desc),
                isDisabled = state.isWorking || state.lastSeededChecklistId == null,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateCreateEdit) }
            )

            // ── Detail ────────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_detail))

            CatalogItem(
                testTag = "catalog_checklist_detail",
                title = stringResource(Res.string.debug_catalog_checklist_detail),
                subtitle = stringResource(Res.string.debug_catalog_checklist_detail_desc),
                isDisabled = state.isWorking || state.lastSeededChecklistId == null,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateChecklistDetail) }
            )

            CatalogItem(
                testTag = "catalog_fill_detail",
                title = stringResource(Res.string.debug_catalog_fill_detail),
                subtitle = stringResource(Res.string.debug_catalog_fill_detail_desc),
                isDisabled = state.isWorking || state.lastSeededFillId == null,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateFillDetail) }
            )

            CatalogItem(
                testTag = "catalog_fills_list",
                title = stringResource(Res.string.debug_catalog_fills_list),
                subtitle = stringResource(Res.string.debug_catalog_fills_list_desc),
                isDisabled = state.isWorking || state.lastSeededChecklistId == null,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateFillsList) }
            )

            CatalogItem(
                testTag = "catalog_share_checklist",
                title = stringResource(Res.string.debug_catalog_share_checklist),
                subtitle = stringResource(Res.string.debug_catalog_share_checklist_desc),
                isDisabled = state.isWorking || state.lastSeededChecklistId == null,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateShareChecklist) }
            )

            // ── AI ────────────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_ai))

            CatalogItem(
                testTag = "catalog_analyze_empty",
                title = stringResource(Res.string.debug_catalog_analyze_empty),
                subtitle = stringResource(Res.string.debug_catalog_analyze_empty_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateAnalyzeEmpty) }
            )

            CatalogItem(
                testTag = "catalog_analyze_for_checklist",
                title = stringResource(Res.string.debug_catalog_analyze_for_checklist),
                subtitle = stringResource(Res.string.debug_catalog_analyze_for_checklist_desc),
                isDisabled = state.isWorking || state.lastSeededChecklistId == null,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateAnalyzeForChecklist) }
            )

            CatalogItem(
                testTag = "catalog_analyze_result_preview",
                title = stringResource(Res.string.debug_catalog_analyze_result_preview),
                subtitle = stringResource(Res.string.debug_catalog_analyze_result_preview_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateAnalyzeResultPreview) }
            )

            // ── Monetisation ──────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_monetisation))

            CatalogItem(
                testTag = "catalog_paywall",
                title = stringResource(Res.string.debug_catalog_paywall),
                subtitle = stringResource(Res.string.debug_catalog_paywall_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigatePaywall) }
            )

            CatalogItem(
                testTag = "paywall_variant_timeline",
                title = stringResource(Res.string.debug_catalog_paywall_timeline),
                subtitle = stringResource(Res.string.debug_catalog_paywall_timeline_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigatePaywallTimeline) }
            )

            CatalogItem(
                testTag = "paywall_variant_features",
                title = stringResource(Res.string.debug_catalog_paywall_features),
                subtitle = stringResource(Res.string.debug_catalog_paywall_features_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigatePaywallFeatures) }
            )

            CatalogItem(
                testTag = "paywall_variant_compare",
                title = stringResource(Res.string.debug_catalog_paywall_compare),
                subtitle = stringResource(Res.string.debug_catalog_paywall_compare_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigatePaywallCompare) }
            )

            CatalogItem(
                testTag = "catalog_subscription_success",
                title = stringResource(Res.string.debug_catalog_subscription_success),
                subtitle = stringResource(Res.string.debug_catalog_subscription_success_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateSubscriptionStatusSuccess) }
            )

            CatalogItem(
                testTag = "catalog_subscription_pending",
                title = stringResource(Res.string.debug_catalog_subscription_pending),
                subtitle = stringResource(Res.string.debug_catalog_subscription_pending_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateSubscriptionStatusPending) }
            )

            // ── Other ─────────────────────────────────────────────────────
            SectionHeader(stringResource(Res.string.debug_catalog_section_other))

            CatalogItem(
                testTag = "catalog_settings",
                title = stringResource(Res.string.debug_catalog_settings),
                subtitle = stringResource(Res.string.debug_catalog_settings_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateSettings) }
            )

            CatalogItem(
                testTag = "catalog_update_feed",
                title = stringResource(Res.string.debug_catalog_update_feed),
                subtitle = stringResource(Res.string.debug_catalog_update_feed_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateUpdateFeed) }
            )

            CatalogItem(
                testTag = "catalog_store_screenshot",
                title = stringResource(Res.string.debug_catalog_store_screenshot),
                subtitle = stringResource(Res.string.debug_catalog_store_screenshot_desc),
                isDisabled = state.isWorking,
                onClick = { viewModel.sendIntent(ScreenCatalogIntent.NavigateStoreScreenshot) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.SpacingLg, bottom = AppDimens.SpacingXs)
    )
}

@Composable
private fun CatalogItem(
    testTag: String,
    title: String,
    subtitle: String,
    isDisabled: Boolean,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier
            .testTag(testTag)
            .alpha(if (isDisabled) 0.38f else 1f),
        onClick = if (isDisabled) null else onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
