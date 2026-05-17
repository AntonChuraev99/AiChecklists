package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.chat_credits_label
import aichecklists.core.designsystem.generated.resources.chat_help_action
import aichecklists.core.designsystem.generated.resources.chat_title
import org.jetbrains.compose.resources.stringResource

/**
 * Top app bar for the AI Chat screen.
 *
 * Actions area contains two elements (left to right):
 * 1. An informational [AssistChip] showing credit balance and cost range.
 *    It is NOT interactive (onClick = null) — read-only indicator only.
 * 2. A help [IconButton] that opens the pricing bottom sheet.
 *
 * @param creditBalance  Current AI credit balance (0 in Phase A).
 * @param onHelpClick    Called when the user taps "?" to open pricing info.
 * @param onBackClick    Called when the user taps the navigation back icon.
 * @param onMenuClick    Called when the user taps the hamburger menu icon to open the drawer.
 *                       If null, shows a back arrow instead (default for push-nav destinations).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    creditBalance: Int,
    onHelpClick: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Credit + cost-range chip — informational only, no onClick
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = stringResource(Res.string.chat_credits_label, creditBalance),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = null,
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = stringResource(Res.string.chat_help_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}
