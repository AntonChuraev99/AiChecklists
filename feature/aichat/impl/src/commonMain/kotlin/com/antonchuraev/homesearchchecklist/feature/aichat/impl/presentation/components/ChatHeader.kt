package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.back
import aichecklists.core.designsystem.generated.resources.chat_header_subtitle
import aichecklists.core.designsystem.generated.resources.chat_header_title
import aichecklists.core.designsystem.generated.resources.chat_settings_open
import aichecklists.core.designsystem.generated.resources.today_open_menu
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppSoftIconButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.SparkleTile
import org.jetbrains.compose.resources.stringResource

/**
 * Top bar of the full-screen AI Chat — redesign B "Soft & tactile" (§3.3).
 *
 * ```
 * (←)  [✦]  Gisti                  (⚙)
 *           Your checklist helper
 * ```
 * Back (or the drawer menu when [onMenuClick] is set) and settings are [AppSoftIconButton]s. The
 * avatar is the 36dp gradient [SparkleTile] circle — the same mark the assistant replies carry.
 *
 * The credits chip is NOT here any more: it moved into the input row (`ChatInputLeading.Credits`),
 * which opens the pricing sheet → "Get more" → paywall (same paywall source as the old chip).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        // Small TopAppBar = 64dp tall, which is the B header height.
        modifier = modifier,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SparkleTile(size = 36.dp, cornerRadius = 18.dp)
                Column {
                    Text(
                        text = stringResource(Res.string.chat_header_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.chat_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onMenuClick != null) {
                AppSoftIconButton(
                    icon = Icons.Outlined.Menu,
                    contentDescription = stringResource(Res.string.today_open_menu),
                    onClick = onMenuClick,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else {
                AppSoftIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                    onClick = onBackClick,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        },
        actions = {
            AppSoftIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(Res.string.chat_settings_open),
                onClick = onSettingsClick,
                modifier = Modifier.padding(end = 4.dp),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
