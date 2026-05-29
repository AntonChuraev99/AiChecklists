package com.antonchuraev.homesearchchecklist.feature.onboarding.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.foundation.layout.fillMaxHeight
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.AiChatHeroIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.CreateFromAnythingIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.RemindersCalendarIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.WorksEverywhereIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallIntent
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallPlan
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallState
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallViewModel
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.components.PlanRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class OnboardingPage(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val illustration: @Composable () -> Unit
)

// Total pages: 4 feature pages + 1 paywall page
private const val TOTAL_PAGES = 5
private const val PAYWALL_PAGE_INDEX = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    paywallViewModel: PaywallViewModel = koinViewModel(
        key = "onboarding_paywall",
        parameters = { parametersOf("onboarding_trial") }
    )
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("onboarding") }

    val state by viewModel.screenState.collectAsState()
    val paywallState by paywallViewModel.screenState.collectAsState()

    val pages = listOf(
        OnboardingPage(
            titleRes = Res.string.onboarding_page1_title,
            descriptionRes = Res.string.onboarding_page1_description,
            illustration = { AiChatHeroIllustration() }
        ),
        OnboardingPage(
            titleRes = Res.string.onboarding_page2_title,
            descriptionRes = Res.string.onboarding_page2_description,
            illustration = { CreateFromAnythingIllustration() }
        ),
        OnboardingPage(
            titleRes = Res.string.onboarding_page3_title,
            descriptionRes = Res.string.onboarding_page3_description,
            illustration = { RemindersCalendarIllustration() }
        ),
        OnboardingPage(
            titleRes = Res.string.onboarding_page4_title,
            descriptionRes = Res.string.onboarding_page4_description,
            illustration = { WorksEverywhereIllustration() }
        )
    )

    val pagerState = rememberPagerState(
        initialPage = state.currentPage,
        pageCount = { TOTAL_PAGES }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.sendIntent(OnboardingIntent.OnPageSelected(page))
        }
    }

    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }

    // Handle successful purchase
    LaunchedEffect(paywallState.purchaseSuccess) {
        if (paywallState.purchaseSuccess) {
            viewModel.sendIntent(OnboardingIntent.OnSkip)
        }
    }

    val isPaywallPage = state.currentPage == PAYWALL_PAGE_INDEX

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Skip button (shown on all pages including paywall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.SpacingSm),
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimens.SpacingSm))
                    .clickable { viewModel.sendIntent(OnboardingIntent.OnSkip) }
                    .padding(AppDimens.SpacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_skip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = AppDimens.ScreenPaddingHorizontal),
            pageSpacing = AppDimens.ScreenPaddingHorizontal,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            if (pageIndex < pages.size) {
                OnboardingPageContent(
                    page = pages[pageIndex],
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PaywallPageContent(
                    product = paywallState.products.firstOrNull(),
                    paywallState = paywallState,
                    onPlanSelected = { paywallViewModel.sendIntent(PaywallIntent.SelectPlan(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            if (isPaywallPage) {
                val product = paywallState.products.firstOrNull()
                Button(
                    onClick = { paywallViewModel.sendIntent(PaywallIntent.Purchase) },
                    enabled = !paywallState.isPurchasing && product != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimens.ButtonHeight),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    if (paywallState.isPurchasing) {
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
            } else {
                AppButton(
                    text = stringResource(Res.string.onboarding_continue),
                    onClick = { viewModel.sendIntent(OnboardingIntent.OnNextPage) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
        }

        // Page indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppDimens.SpacingLg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(TOTAL_PAGES) { index ->
                PageIndicator(
                    isSelected = index == state.currentPage,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Illustration container
        Box(
            modifier = Modifier
                .fillMaxHeight(fraction = 0.7F)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            page.illustration()
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Title
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        // Description
        Text(
            text = stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
    }
}

@Composable
private fun PageIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                }
            )
    )
}

@Composable
private fun PaywallPageContent(
    modifier: Modifier = Modifier,
    product: PaywallProduct?,
    paywallState: PaywallState,
    onPlanSelected: (PaywallPlan) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val products = paywallState.products
    val yearlyProduct = products.find {
        it.id.contains("year", ignoreCase = true) || it.id.contains("annual", ignoreCase = true)
    }
    val monthlyProduct = products.find {
        it.id.contains("month", ignoreCase = true)
    }

    Column(
        modifier = modifier
            .padding(vertical = AppDimens.SpacingMd)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Headline
        Text(
            text = stringResource(Res.string.paywall_v1_features_headline, product?.freeTrialDays ?: 3),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        Text(
            text = stringResource(Res.string.paywall_v1_features_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Plan rows
        if (!paywallState.isLoading && products.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)) {
                if (yearlyProduct != null) {
                    PlanRow(
                        label = stringResource(Res.string.paywall_v1_yearly_label),
                        price = yearlyProduct.priceString,
                        sub = stringResource(Res.string.paywall_v1_billed_annually_subtitle,
                            formatMonthlyFromYearly(yearlyProduct.priceAmount, yearlyProduct.priceString)),
                        badge = stringResource(Res.string.paywall_v1_best_value_badge),
                        savings = computeSavings(yearlyProduct, monthlyProduct),
                        selected = paywallState.selectedPlan == PaywallPlan.Yearly,
                        onClick = { onPlanSelected(PaywallPlan.Yearly) },
                    )
                }
                if (monthlyProduct != null) {
                    PlanRow(
                        label = stringResource(Res.string.paywall_v1_monthly_label),
                        price = monthlyProduct.priceString,
                        sub = stringResource(Res.string.paywall_v1_billed_monthly),
                        badge = if (monthlyProduct.hasFreeTrial) stringResource(
                            Res.string.paywall_v1_timeline_days_free, monthlyProduct.freeTrialDays
                        ) else null,
                        selected = paywallState.selectedPlan == PaywallPlan.Monthly,
                        onClick = { onPlanSelected(PaywallPlan.Monthly) },
                    )
                }
            }
        } else if (paywallState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Benefit list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
        ) {
            BenefitCheckRow(stringResource(Res.string.ob_benefit_unlimited_ai))
            BenefitCheckRow(stringResource(Res.string.ob_benefit_calendar))
            BenefitCheckRow(stringResource(Res.string.ob_benefit_reminders))
            BenefitCheckRow(stringResource(Res.string.ob_benefit_credits))
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Legal links
        PaywallLegalLinks(
            onTermsClick = { uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL) },
            onPrivacyClick = { uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL) }
        )
    }
}

@Composable
private fun BenefitCheckRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMonthlyFromYearly(yearlyAmount: Double, yearlyPriceString: String): String {
    if (yearlyAmount <= 0) return yearlyPriceString
    val monthly = yearlyAmount / 12
    // KMP-safe: String.format is JVM-only and breaks wasmJs/iOS compilation.
    val formatted = kotlin.math.round(monthly).toLong().toString()
    val currencySymbol = yearlyPriceString.filter { !it.isDigit() && it != '.' && it != ',' && it != ' ' }.trim()
    return if (currencySymbol.isNotEmpty()) "$formatted $currencySymbol" else formatted
}

private fun computeSavings(yearly: PaywallProduct?, monthly: PaywallProduct?): String? {
    if (yearly == null || monthly == null) return null
    if (yearly.priceAmount <= 0 || monthly.priceAmount <= 0) return null
    val yearlyMonthly = yearly.priceAmount / 12
    val savingsPercent = ((1 - yearlyMonthly / monthly.priceAmount) * 100).toInt()
    return if (savingsPercent > 0) "-$savingsPercent%" else null
}


@Composable
private fun PaywallLegalLinks(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onTermsClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(Res.string.paywall_terms),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "•",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = onPrivacyClick,
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
}
