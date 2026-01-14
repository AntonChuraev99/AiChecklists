package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_best_value
import aichecklists.core.designsystem.generated.resources.paywall_continue
import aichecklists.core.designsystem.generated.resources.paywall_feature_ai
import aichecklists.core.designsystem.generated.resources.paywall_feature_checklists
import aichecklists.core.designsystem.generated.resources.paywall_feature_sync
import aichecklists.core.designsystem.generated.resources.paywall_restore
import aichecklists.core.designsystem.generated.resources.paywall_subtitle
import aichecklists.core.designsystem.generated.resources.paywall_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
        ) {
            // Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.SpacingLg),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { viewModel.sendIntent(PaywallIntent.Close) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Premium icon with gradient background
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                        )
                    )
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Title
            Text(
                text = stringResource(Res.string.paywall_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Subtitle
            Text(
                text = stringResource(Res.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Features list
            FeaturesList()

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Products
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                ProductsList(
                    products = state.products,
                    selectedProductId = state.selectedProductId,
                    onProductSelected = { viewModel.sendIntent(PaywallIntent.SelectProduct(it)) }
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

            // Continue button
            AppButton(
                text = stringResource(Res.string.paywall_continue),
                onClick = { viewModel.sendIntent(PaywallIntent.Purchase) },
                enabled = state.selectedProductId != null && !state.isPurchasing,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isPurchasing) {
                Spacer(modifier = Modifier.height(AppDimens.SpacingSm))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

            // Restore purchases
            TextButton(
                onClick = { viewModel.sendIntent(PaywallIntent.RestorePurchases) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(Res.string.paywall_restore),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))
        }

        // Error snackbar
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AppDimens.SpacingLg),
                action = {
                    TextButton(onClick = { viewModel.sendIntent(PaywallIntent.DismissError) }) {
                        Text("OK")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun FeaturesList() {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        FeatureItem(text = stringResource(Res.string.paywall_feature_checklists))
        FeatureItem(text = stringResource(Res.string.paywall_feature_ai))
        FeatureItem(text = stringResource(Res.string.paywall_feature_sync))
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ProductsList(
    products: List<PaywallProduct>,
    selectedProductId: String?,
    onProductSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)
    ) {
        products.forEach { product ->
            ProductCard(
                product = product,
                isSelected = product.id == selectedProductId,
                onClick = { onProductSelected(product.id) }
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: PaywallProduct,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(AppDimens.SpacingLg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (product.isPopular) {
                        Spacer(modifier = Modifier.width(AppDimens.SpacingSm))
                        PopularBadge()
                    }
                }
                product.periodString?.let { period ->
                    Text(
                        text = period,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = product.priceString,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PopularBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = stringResource(Res.string.paywall_best_value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
