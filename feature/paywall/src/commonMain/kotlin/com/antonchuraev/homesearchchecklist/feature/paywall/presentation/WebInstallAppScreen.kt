package com.antonchuraev.homesearchchecklist.feature.paywall.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.paywall_v1_close_cd
import aichecklists.core.designsystem.generated.resources.paywall_web_install_android_cta
import aichecklists.core.designsystem.generated.resources.paywall_web_install_android_label
import aichecklists.core.designsystem.generated.resources.paywall_web_install_android_status
import aichecklists.core.designsystem.generated.resources.paywall_web_install_description
import aichecklists.core.designsystem.generated.resources.paywall_web_install_ios_hint
import aichecklists.core.designsystem.generated.resources.paywall_web_install_ios_label
import aichecklists.core.designsystem.generated.resources.paywall_web_install_ios_status
import aichecklists.core.designsystem.generated.resources.paywall_web_install_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/** Public Google Play listing for the Android build. */
internal const val GISTI_GOOGLE_PLAY_URL: String =
    "https://play.google.com/store/apps/details?id=com.antonchuraev.aichecklists"

/**
 * Web-only screen shown instead of [PaywallScreen] on the wasmJs target.
 *
 * IAP isn't available in the browser, so this screen funnels web visitors to
 * the mobile app: an active "Get on Google Play" CTA for Android and a
 * disabled "Coming soon" card for iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebInstallAppScreen(
    onClose: () -> Unit,
    onInstallAndroidClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.paywall_v1_close_cd),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = AppDimens.ScreenPaddingHorizontal,
                    vertical = AppDimens.SpacingLg,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(AppDimens.SpacingLg))

                Text(
                    text = stringResource(Res.string.paywall_web_install_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(AppDimens.SpacingMd))

                Text(
                    text = stringResource(Res.string.paywall_web_install_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(AppDimens.SpacingXxl))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingLg),
                ) {
                    PlatformInstallCard(
                        icon = Icons.Filled.Android,
                        platformName = stringResource(Res.string.paywall_web_install_android_label),
                        status = stringResource(Res.string.paywall_web_install_android_status),
                        actionLabel = stringResource(Res.string.paywall_web_install_android_cta),
                        onActionClick = onInstallAndroidClick,
                    )

                    PlatformInstallCard(
                        icon = Icons.Filled.Schedule,
                        platformName = stringResource(Res.string.paywall_web_install_ios_label),
                        status = stringResource(Res.string.paywall_web_install_ios_status),
                        hint = stringResource(Res.string.paywall_web_install_ios_hint),
                        actionLabel = null,
                        onActionClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformInstallCard(
    icon: ImageVector,
    platformName: String,
    status: String,
    actionLabel: String?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val isEnabled = actionLabel != null
    val accentColor = if (isEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(AppDimens.IconSizeLg),
                )
                Spacer(Modifier.size(AppDimens.SpacingMd))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = platformName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hint != null) {
                Spacer(Modifier.height(AppDimens.SpacingSm))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (actionLabel != null) {
                Spacer(Modifier.height(AppDimens.SpacingLg))
                AppButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val MaxContentWidth = 420.dp
