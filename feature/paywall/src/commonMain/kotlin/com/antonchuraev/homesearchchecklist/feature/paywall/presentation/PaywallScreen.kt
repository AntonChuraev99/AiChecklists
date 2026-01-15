package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

// Colors matching the reference design
private val PaywallOrange = Color(0xFFFF9500)
private val PaywallOrangeBackground = Color(0xFFFFF8F0)
private val BackgroundGray = Color(0xFFF8F8F8)
private val PhoneFrameColor = Color(0xFF1C1C1E)

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
            .background(BackgroundGray)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top section with phone mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Close button in top right
                IconButton(
                    onClick = { viewModel.sendIntent(PaywallIntent.Close) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
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
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Phone mockup centered
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp)
                        .padding(top = 48.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PhoneMockup()
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
private fun PhoneMockup() {
    // iPhone-like frame
    Box(
        modifier = Modifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(40.dp),
                spotColor = Color.Black.copy(alpha = 0.25f)
            )
    ) {
        // Phone outer frame (black)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(PhoneFrameColor)
                .padding(8.dp)
        ) {
            // Phone screen (white with content)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .width(260.dp)
            ) {
                // Status bar with notch
                StatusBarWithNotch()

                // App content
                AppContentPreview(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                // Overlay text and pagination at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tagline text
                        Text(
                            text = "All your favorite recipes\nin one place",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.Black,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pagination dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaginationDot(isSelected = false)
                            PaginationDot(isSelected = true)
                            PaginationDot(isSelected = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBarWithNotch() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.White)
    ) {
        // Time on left
        Text(
            text = "9:41",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        )

        // Notch/Dynamic Island in center
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .width(90.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PhoneFrameColor)
        )

        // Signal/battery icons on right (simplified)
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal bars
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((6 + index * 2).dp)
                            .background(Color.Black, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun AppContentPreview(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Recipe title row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Food image placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFE4C4))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Beef Chow Fun",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Section label
        Text(
            text = "For the beef:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Checklist items
        ChecklistPreviewItem(text = "400-500g skirt/flank beef steak", isChecked = true)
        ChecklistPreviewItem(text = "1 tsp light soy sauce", isChecked = true)
        ChecklistPreviewItem(text = "1 tsp white pepper", isChecked = true)
        ChecklistPreviewItem(text = "1 tsp cornstarch", isChecked = true)
        ChecklistPreviewItem(text = "1 tsp sugar", isChecked = false)
        ChecklistPreviewItem(text = "1 tbsp vegetable oil", isChecked = false)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ChecklistPreviewItem(text: String, isChecked: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (isChecked) {
                        Modifier.background(PaywallOrange)
                    } else {
                        Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isChecked) Color.Black else Color.Gray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PaginationDot(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) Color.Black.copy(alpha = 0.8f)
                else Color.Black.copy(alpha = 0.2f)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(PaywallOrangeBackground)
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 12.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = PaywallOrange
            )
            Spacer(modifier = Modifier.height(20.dp))
        } else if (product != null) {
            // Main headline - "7 Days for Free"
            Text(
                text = if (product.hasFreeTrial) {
                    "${product.freeTrialDays} Days for Free"
                } else {
                    "Go Premium"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PaywallOrange,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Price line 1: "then $4.99 / month"
            Text(
                text = "then ${product.priceString} / month",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            // Price line 2: "($59.99 billed annually after trial)"
            if (product.hasFreeTrial) {
                Text(
                    text = "(\$59.99 billed annually after trial)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CTA Button - "Start your FREE week"
            Button(
                onClick = onSubscribe,
                enabled = !isPurchasing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaywallOrange,
                    contentColor = Color.White,
                    disabledContainerColor = PaywallOrange.copy(alpha = 0.5f)
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
                            "Start your FREE week"
                        } else {
                            "Subscribe Now"
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
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "No payment due now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Footer links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { /* Terms */ }) {
                Text(
                    text = "Terms of Use",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onRestore) {
                Text(
                    text = "Restore Purchase",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = { /* Privacy */ }) {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
