package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.AiAnalysisIllustration
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.ProgressIllustration
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.TasksIllustration
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Background color for paywall
private val BackgroundGray = Color(0xFFF8F8F8)

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
    val state by viewModel.screenState.collectAsState()

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
            illustration = { TasksIllustration() }
        ),
        PaywallPage(
            titleRes = Res.string.onboarding_page2_title,
            descriptionRes = Res.string.onboarding_page2_description,
            illustration = { AiAnalysisIllustration() }
        ),
        PaywallPage(
            titleRes = Res.string.onboarding_page3_title,
            descriptionRes = Res.string.onboarding_page3_description,
            illustration = { ProgressIllustration() }
        )
    )

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top section with close button and pager
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Close button row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { viewModel.sendIntent(PaywallIntent.Close) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.cancel),
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Pager with illustrations
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { pageIndex ->
                        PaywallPageContent(
                            page = pages[pageIndex],
                            modifier = Modifier.fillMaxSize()
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
                }
            }

            // Bottom subscription card
            val product = state.products.firstOrNull()
            SubscriptionCard(
                product = product,
                isLoading = state.isLoading,
                isPurchasing = state.isPurchasing,
                onSubscribe = { viewModel.sendIntent(PaywallIntent.Purchase) },
                onRestore = { viewModel.sendIntent(PaywallIntent.RestorePurchases) }
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
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

        // Description
        Text(
            text = stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppDimens.SpacingLg)
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
private fun SubscriptionCard(
    product: PaywallProduct?,
    isLoading: Boolean,
    isPurchasing: Boolean,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(primaryContainerColor.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 8.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = primaryColor
            )
            Spacer(modifier = Modifier.height(20.dp))
        } else if (product != null) {
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

            // Price line
            Text(
                text = stringResource(Res.string.paywall_then_price, product.priceString),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // "No payment due now" with checkmark
            if (product.hasFreeTrial) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.paywall_no_payment_now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Footer links - more compact
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { /* Terms */ },
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
                onClick = onRestore,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_restore),
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
                onClick = { /* Privacy */ },
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
