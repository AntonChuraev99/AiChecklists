package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SubscriptionStatusScreen(
    showSuccessMessage: Boolean = false,
    viewModel: SubscriptionStatusViewModel = koinViewModel()
) {
    val state by viewModel.screenState.collectAsState()

    // Show success message from nav param and auto-dismiss after 3s
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            viewModel.setShowSuccessMessage(true)
            delay(3000)
            viewModel.sendIntent(SubscriptionStatusIntent.DismissSuccessMessage)
        }
    }

    AppScaffold(
        title = stringResource(Res.string.subscription_status_title),
        onBackButtonClick = { viewModel.sendIntent(SubscriptionStatusIntent.OnBackClick) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                SubscriptionStatusContent(
                    formattedExpirationDate = state.formattedExpirationDate,
                    aiCredits = state.aiCredits,
                    isPremium = state.subscriptionStatus.isActive,
                    onBackClick = { viewModel.sendIntent(SubscriptionStatusIntent.OnBackClick) }
                )
            }

            // Success snackbar
            if (state.showSuccessMessage) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(AppDimens.SpacingLg)
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    action = {
                        TextButton(onClick = { viewModel.sendIntent(SubscriptionStatusIntent.DismissSuccessMessage) }) {
                            Text(stringResource(Res.string.ok))
                        }
                    }
                ) {
                    Text(stringResource(Res.string.subscription_purchase_success))
                }
            }
        }
    }
}

@Composable
private fun SubscriptionStatusContent(
    formattedExpirationDate: String?,
    aiCredits: Int,
    isPremium: Boolean,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Premium icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // Title
        Text(
            text = stringResource(Res.string.subscription_status_active),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingLg))

        // Expiration info
        if (formattedExpirationDate != null) {
            Text(
                text = stringResource(Res.string.subscription_status_valid_until),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            Text(
                text = formattedExpirationDate,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

        // AI Credits section
        Text(
            text = stringResource(Res.string.subscription_status_credits_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

        // Show actual credits for all users
        Text(
            text = stringResource(Res.string.credits_display, aiCredits),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        if (isPremium) {
            Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

            // Refill info for premium users
            Text(
                text = stringResource(Res.string.subscription_status_credits_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(AppDimens.SpacingXs))

            // Daily cap info
            Text(
                text = stringResource(Res.string.subscription_status_daily_cap, 300),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.SpacingXxl))

        // Back to home button
        AppButtonSecondary(
            text = stringResource(Res.string.subscription_status_back),
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
