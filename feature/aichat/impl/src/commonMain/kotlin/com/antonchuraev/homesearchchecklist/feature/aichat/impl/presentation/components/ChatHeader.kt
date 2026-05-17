package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.chat_credits_balance
import aichecklists.core.designsystem.generated.resources.chat_title
import org.jetbrains.compose.resources.stringResource

/**
 * Top app bar for the AI Chat screen.
 *
 * Layout:
 *   [menu] AI Chat ............................. [✨ N]
 *
 * The actions area shows ONLY a compact balance chip: AutoAwesome icon + raw
 * credit number, no surrounding text. Pricing explanation is no longer here —
 * it lives in [ChatPricingCaption] above the input row, which is closer to the
 * point where the cost actually matters.
 *
 * Matches the brand-consistent CreditsChip pattern used on the Main screen
 * (Icons.Outlined.AutoAwesome + primaryContainer background).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    creditBalance: Int,
    onBackClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
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
            // Brand-consistent compact balance chip — icon + number, no text label.
            // Matches MainScreen.CreditsChip pattern (AutoAwesome + primaryContainer).
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.chat_credits_balance, creditBalance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
