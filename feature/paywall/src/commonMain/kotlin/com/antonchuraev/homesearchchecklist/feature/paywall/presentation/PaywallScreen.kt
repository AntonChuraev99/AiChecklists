package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentHeight
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.CreateViaAiIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.FillViaAiIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.illustrations.ExportShareIllustration
import com.antonchuraev.homesearchchecklist.desingsystem.sharedUI.TrialTimeline
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel




private data class PaywallPage(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val illustration: @Composable () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel = koinViewModel(),
    onPurchaseSuccess: () -> Unit = {}
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView("paywall") }

    val state by viewModel.screenState.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.purchaseSuccess) {
        if (state.purchaseSuccess) {
            onPurchaseSuccess()
        }
    }

    // Same pages as onboarding
    val pages = listOf(
        PaywallPage(
            titleRes = Res.string.onboarding_page1_title,
            descriptionRes = Res.string.onboarding_page1_description,
            illustration = { CreateViaAiIllustration() }
        ),
        PaywallPage(
            titleRes = Res.string.onboarding_page2_title,
            descriptionRes = Res.string.onboarding_page2_description,
            illustration = { FillViaAiIllustration() }
        ),
        PaywallPage(
            titleRes = Res.string.onboarding_page3_title,
            descriptionRes = Res.string.onboarding_page3_description,
            illustration = { ExportShareIllustration() }
        )
    )

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size }
    )

    // Track page swipes
    var previousPage by remember { mutableStateOf(pagerState.settledPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != previousPage) {
            analyticsTracker.event("paywall_page_swiped", mapOf(
                "source" to state.source,
                "from_page" to previousPage,
                "to_page" to pagerState.settledPage
            ))
            previousPage = pagerState.settledPage
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top section with close button and pager
            // Skip button row (same style as OnboardingScreen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = AppDimens.SpacingSm, end = AppDimens.ScreenPaddingHorizontal),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppDimens.SpacingSm))
                        .clickable { viewModel.sendIntent(PaywallIntent.Close) }
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

            // Pager with illustrations
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1F)
                    .fillMaxWidth()
            ) { pageIndex ->
                PaywallPageContent(
                    page = pages[pageIndex],
                    modifier = Modifier
                )
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppDimens.SpacingLg),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    PageIndicator(
                        isSelected = index == pagerState.currentPage,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Bottom subscription card
            val product = state.products.firstOrNull()
            SubscriptionCard(
                product = product,
                isLoading = state.isLoading,
                isPurchasing = state.isPurchasing,
                onSubscribe = { viewModel.sendIntent(PaywallIntent.Purchase) },
                onRetry = { viewModel.sendIntent(PaywallIntent.LoadProducts) },
                onRestore = { viewModel.sendIntent(PaywallIntent.RestorePurchases) },
                onTermsClick = {
                    analyticsTracker.event("paywall_terms_clicked", mapOf("source" to state.source))
                    uriHandler.openUri(PaywallConfig.TERMS_OF_USE_URL)
                },
                onPrivacyClick = {
                    analyticsTracker.event("paywall_privacy_clicked", mapOf("source" to state.source))
                    uriHandler.openUri(PaywallConfig.PRIVACY_POLICY_URL)
                },
                onSupportClick = {
                    analyticsTracker.event("paywall_support_clicked", mapOf("source" to state.source))
                    uriHandler.openUri("mailto:${PaywallConfig.SUPPORT_EMAIL}")
                }
            )
        }

        // Error snackbar
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AppDimens.SpacingLg)
                    .navigationBarsPadding(),
                action = {
                    TextButton(onClick = {
                        viewModel.sendIntent(PaywallIntent.DismissError)
                        viewModel.sendIntent(PaywallIntent.LoadProducts)
                    }) {
                        Text(stringResource(Res.string.paywall_retry))
                    }
                },
                dismissAction = {
                    TextButton(onClick = { viewModel.sendIntent(PaywallIntent.DismissError) }) {
                        Text(stringResource(Res.string.ok))
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun PaywallPageContent(
    page: PaywallPage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = AppDimens.ScreenPaddingHorizontal),
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

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        // Title
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Description
        Text(
            text = stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))
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
private fun ColumnScope.SubscriptionCard(
    product: PaywallProduct?,
    isLoading: Boolean,
    isPurchasing: Boolean,
    onSubscribe: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onSupportClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(primaryContainerColor.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 8.dp)
            .navigationBarsPadding()
            .wrapContentHeight()
        ,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fixed height container for content to prevent layout jumps
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = primaryColor
                    )
                }
                product != null -> {
                    SubscriptionContent(
                        product = product,
                        isPurchasing = isPurchasing,
                        onSubscribe = onSubscribe,
                        primaryColor = primaryColor
                    )
                }
                else -> {
                    // Products failed to load
                    LoadingErrorContent(
                        onRetry = onRetry,
                        primaryColor = primaryColor
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Footer links - more compact
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
            ,
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onTermsClick,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.height(25.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_terms),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = onPrivacyClick,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.height(25.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = onRestore,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.height(25.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_restore),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }


            TextButton(
                onClick = onSupportClick,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.height(25.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_support),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoadingErrorContent(
    onRetry: () -> Unit,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.paywall_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(Res.string.paywall_retry),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubscriptionContent(
    product: PaywallProduct,
    isPurchasing: Boolean,
    onSubscribe: () -> Unit,
    primaryColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main headline
        Text(
            text = if (product.hasFreeTrial) {
                stringResource(Res.string.paywall_days_free, product.freeTrialDays)
            } else {
                stringResource(Res.string.paywall_go_premium)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(Res.string.paywall_then_price, product.priceString),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Trial Timeline (Blinkist style)
        if (product.hasFreeTrial) {
            TrialTimeline(
                trialDays = product.freeTrialDays,
                priceString = product.priceString,
                primaryColor = primaryColor
            )
        }

        // CTA Button
        Button(
            onClick = onSubscribe,
            enabled = !isPurchasing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Color.White,
                disabledContainerColor = primaryColor.copy(alpha = 0.5f)
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

        Spacer(modifier = Modifier.height(6.dp))

        // Subscription disclosure (required by Google Play policy)
        if (product.hasFreeTrial) {
            Text(
                text = stringResource(
                    Res.string.paywall_trial_terms,
                    product.freeTrialDays,
                    product.priceString
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

