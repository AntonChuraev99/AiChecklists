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
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.CreateViaAiIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.FillViaAiIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.ExportShareIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.PremiumBenefitsIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallIntent
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class OnboardingPage(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val illustration: @Composable () -> Unit
)

// Total pages: 3 feature pages + 1 paywall page
private const val TOTAL_PAGES = 4
private const val PAYWALL_PAGE_INDEX = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = koinViewModel(),
    paywallViewModel: PaywallViewModel = koinViewModel()
) {
    val state by viewModel.screenState.collectAsState()
    val paywallState by paywallViewModel.screenState.collectAsState()
    val uriHandler = LocalUriHandler.current

    val pages = listOf(
        OnboardingPage(
            titleRes = Res.string.onboarding_page1_title,
            descriptionRes = Res.string.onboarding_page1_description,
            illustration = { CreateViaAiIllustration() }
        ),
        OnboardingPage(
            titleRes = Res.string.onboarding_page2_title,
            descriptionRes = Res.string.onboarding_page2_description,
            illustration = { FillViaAiIllustration() }
        ),
        OnboardingPage(
            titleRes = Res.string.onboarding_page3_title,
            descriptionRes = Res.string.onboarding_page3_description,
            illustration = { ExportShareIllustration() }
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
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
    ) {
        // Skip button (hidden on paywall page)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.SpacingSm),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isPaywallPage) {
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
            } else {
                // Spacer to maintain layout
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
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
                // Paywall page
                PaywallPageContent(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom section: different for paywall page
        if (isPaywallPage) {
            PaywallBottomSection(
                product = paywallState.products.firstOrNull(),
                isLoading = paywallState.isLoading,
                isPurchasing = paywallState.isPurchasing,
                onSubscribe = { paywallViewModel.sendIntent(PaywallIntent.Purchase) },
                onSkip = { viewModel.sendIntent(OnboardingIntent.OnSkip) },
                onTermsClick = { uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL) },
                onPrivacyClick = { uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL) }
            )
        } else {
            // Continue button
            AppButton(
                text = stringResource(Res.string.onboarding_continue),
                onClick = { viewModel.sendIntent(OnboardingIntent.OnNextPage) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        }

        // Page indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isPaywallPage) AppDimens.SpacingSm else AppDimens.SpacingXl),
            horizontalArrangement = Arrangement.Center
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
        modifier = modifier.padding(vertical = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Illustration container
        Box(
            modifier = Modifier
                .weight(1f)
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
            .size(if (isSelected) 10.dp else 8.dp)
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = AppDimens.SpacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Illustration with preview items
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

        // Title
        Text(
            text = stringResource(Res.string.paywall_go_premium),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Tagline
        Text(
            text = stringResource(Res.string.paywall_preview_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))
    }
}

@Composable
private fun PaywallBottomSection(
    product: PaywallProduct?,
    isLoading: Boolean,
    isPurchasing: Boolean,
    onSubscribe: () -> Unit,
    onSkip: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
        } else if (product != null) {
            // Price info
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
                text = stringResource(Res.string.paywall_then_price, product.priceString),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

            // Subscribe button
            Button(
                onClick = onSubscribe,
                enabled = !isPurchasing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (product.hasFreeTrial) {
                            stringResource(Res.string.paywall_start_free_trial)
                        } else {
                            stringResource(Res.string.paywall_subscribe_now)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // No payment now hint
            if (product.hasFreeTrial) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.paywall_no_payment_now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
        }

        // Skip link
        TextButton(
            onClick = onSkip,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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

        // Legal links
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
