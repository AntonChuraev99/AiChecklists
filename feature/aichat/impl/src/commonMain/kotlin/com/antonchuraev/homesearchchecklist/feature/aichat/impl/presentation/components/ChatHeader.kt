package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.chat_title
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCreditsChip

/**
 * Top app bar for the AI Chat screen.
 *
 * ## Layout
 * ```
 * [menu/back]          AI Chat          [✨ N | Get More]
 * ```
 *
 * Single-line title only. The pricing caption ("≈ 0–3 credits per query" + help icon)
 * has been moved to a dedicated [ChatPricingRow] composable rendered immediately below
 * this bar in [ChatScreen]'s content column. This keeps the TopAppBar uncluttered and
 * follows the standard MD3 "small title bar" pattern.
 *
 * ## Credit chip
 * Uses the shared [AppCreditsChip] from core/designsystem:
 * - `credits > 0` → shows count with AutoAwesome icon (primaryContainer bg)
 * - `credits ≤ 0 && !isPremium` → shows "Get More" CTA (primary bg) — mid-conversation
 *   upsell moment, consistent with MainScreen.
 * - `isPremium` → shows ∞ symbol
 *
 * ## Token mapping
 * - Container: `MaterialTheme.colorScheme.surface` (TopAppBar default)
 * - Title: `MaterialTheme.colorScheme.onSurface` (titleLarge)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    creditBalance: Int,
    onBackClick: () -> Unit,
    isPremium: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    navigateToPaywall: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
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
                onClick = navigateToPaywall,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
