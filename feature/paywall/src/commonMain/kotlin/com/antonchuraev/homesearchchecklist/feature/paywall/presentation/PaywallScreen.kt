package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallProduct
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Colors for the paywall
private val PaywallOrange = Color(0xFFFF9500)
private val PaywallOrangeLight = Color(0xFFFFF3E0)
private val PaywallOrangeDark = Color(0xFFE68600)

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
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top section with close button and preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Close button
                IconButton(
                    onClick = { viewModel.sendIntent(PaywallIntent.Close) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppDimens.SpacingMd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }

                // App preview mockup
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .padding(top = 60.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppPreviewMockup()
                }
            }

            // Bottom card with subscription info
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
private fun AppPreviewMockup() {
    // Phone-like container with app preview
    Box(
        modifier = Modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // App header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sample checklist items
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SampleChecklistItem(text = stringResource(Res.string.paywall_preview_item1), checked = true)
                SampleChecklistItem(text = stringResource(Res.string.paywall_preview_item2), checked = true)
                SampleChecklistItem(text = stringResource(Res.string.paywall_preview_item3), checked = false)
                SampleChecklistItem(text = stringResource(Res.string.paywall_preview_item4), checked = false)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tagline
            Text(
                text = stringResource(Res.string.paywall_preview_tagline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SampleChecklistItem(text: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.Check else Icons.Outlined.Checklist,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) Color.Black else Color.Gray
        )
    }
}

@Composable
private fun SubscriptionCard(
    product: PaywallProduct?,
    isLoading: Boolean,
    isPurchasing: Boolean,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(PaywallOrangeLight)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = PaywallOrange
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else if (product != null) {
            // Trial headline
            if (product.hasFreeTrial) {
                Text(
                    text = stringResource(Res.string.paywall_days_free, product.freeTrialDays),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(Res.string.paywall_go_premium),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price info
            Text(
                text = stringResource(Res.string.paywall_then_price, product.priceString),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // CTA Button
            Button(
                onClick = onSubscribe,
                enabled = !isPurchasing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaywallOrange,
                    contentColor = Color.White,
                    disabledContainerColor = PaywallOrange.copy(alpha = 0.5f)
                )
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

            // No payment due now
            if (product.hasFreeTrial) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.paywall_no_payment_now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Footer links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { /* Terms */ }) {
                Text(
                    text = stringResource(Res.string.paywall_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = onRestore) {
                Text(
                    text = stringResource(Res.string.paywall_restore),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = { /* Privacy */ }) {
                Text(
                    text = stringResource(Res.string.paywall_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
