package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.chat_settings_open
import aichecklists.core.designsystem.generated.resources.chat_title
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip
import org.jetbrains.compose.resources.stringResource

/**
 * Top app bar for the AI Chat screen.
 *
 * ## Layout
 * ```
 * [menu/back]      AI Chat      [✨ N | Get More]  [⚙]
 * ```
 *
 * Credit chip stays visible in the bar for at-a-glance balance + Get-More CTA when
 * the user hits zero. The gear icon opens the settings sheet for the Deep Thinking
 * toggle (and richer credit info). Pricing caption (`≈ 0–3 credits per query`) is
 * rendered in [ChatPricingRow] below this bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    creditBalance: Int,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
    isPremium: Boolean = false,
    onCreditsClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // M3 Small TopAppBar — title is LEFT-aligned (sits directly to the right of the
    // navigation icon). The center-aligned variant places the title in the middle of
    // the bar, which doesn't match the requested layout.
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(Res.string.chat_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(Res.string.back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        actions = {
            AppCreditsChip(
                credits = creditBalance,
                isPremium = isPremium,
                onClick = onCreditsClick,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(Res.string.chat_settings_open),
                    // onSurfaceVariant per AI Chat M3 design — lower-emphasis trailing action.
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
