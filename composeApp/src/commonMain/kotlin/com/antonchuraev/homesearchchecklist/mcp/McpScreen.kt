package com.antonchuraev.homesearchchecklist.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.gisti.AppGradientButton
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiColors
import com.antonchuraev.homesearchchecklist.navigation.safeOpenUri
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.mcp_cta_guide
import aichecklists.core.designsystem.generated.resources.mcp_endpoint_copied
import aichecklists.core.designsystem.generated.resources.mcp_endpoint_copy_cd
import aichecklists.core.designsystem.generated.resources.mcp_endpoint_label
import aichecklists.core.designsystem.generated.resources.mcp_hero_body
import aichecklists.core.designsystem.generated.resources.mcp_hero_title
import aichecklists.core.designsystem.generated.resources.mcp_requires_google
import aichecklists.core.designsystem.generated.resources.mcp_title
import aichecklists.core.designsystem.generated.resources.mcp_value_create
import aichecklists.core.designsystem.generated.resources.mcp_value_edit
import aichecklists.core.designsystem.generated.resources.mcp_value_read
import aichecklists.core.designsystem.generated.resources.mcp_value_sync
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Gisti MCP info screen. Stateless (no ViewModel / MVI) — pure presentation that explains the
 * remote MCP server, exposes the endpoint URL to copy, and links to the connection guide.
 *
 * Opened from the navigation drawer ("Gisti MCP"). Rendered as a pushed detail screen with a
 * back arrow (NOT a drawer/hamburger destination) — it is a one-shot read-and-return page like
 * ChecklistDetail, so it keeps the parent shell underneath and returns via [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val logger: AppLogger = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-resolve the snackbar copy in Composable scope — stringResource can't run inside the
    // coroutine launched on tap (it is @Composable). Captured into a val, shown from launch { }.
    val copiedMessage = stringResource(Res.string.mcp_endpoint_copied)

    AppScaffold(
        title = stringResource(Res.string.mcp_title),
        onBackButtonClick = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        McpContent(
            onCopyEndpoint = {
                clipboard.setText(AnnotatedString(McpConfig.MCP_ENDPOINT_URL))
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = copiedMessage,
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            // Crash-guarded external open: a device with no browser throws
            // ActivityNotFoundException from openUri (Crashlytics c1aeb170). safeOpenUri
            // degrades to a logged warning instead of crashing.
            onOpenGuide = { safeOpenUri(uriHandler, logger, McpConfig.CONNECTION_GUIDE_URL) },
        )
    }
}

@Composable
private fun McpContent(
    onCopyEndpoint: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    // Outer Column centers the capped content column on wide (tablet/desktop wasmJs) viewports.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .adaptiveContentWidth(600)
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal),
        ) {
            Spacer(Modifier.height(AppDimens.SpacingLg))

            // [1] Hero
            HeroSparkleTile()
            Spacer(Modifier.height(AppDimens.SpacingMd))
            Text(
                text = stringResource(Res.string.mcp_hero_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(AppDimens.SpacingSm))
            Text(
                text = stringResource(Res.string.mcp_hero_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(AppDimens.SpacingXl))

            // [3] Value props (monochrome tiles — hero stays the only gradient moment)
            ValuePropRow(icon = Icons.Outlined.MenuBook, label = stringResource(Res.string.mcp_value_read))
            Spacer(Modifier.height(AppDimens.SpacingLg))
            ValuePropRow(icon = Icons.Outlined.AutoAwesome, label = stringResource(Res.string.mcp_value_create))
            Spacer(Modifier.height(AppDimens.SpacingLg))
            ValuePropRow(icon = Icons.Outlined.TaskAlt, label = stringResource(Res.string.mcp_value_edit))
            Spacer(Modifier.height(AppDimens.SpacingMd))
            SyncCaption(text = stringResource(Res.string.mcp_value_sync))

            Spacer(Modifier.height(AppDimens.SpacingXl))

            // [4] CTA — external connection guide
            AppGradientButton(
                text = stringResource(Res.string.mcp_cta_guide),
                onClick = onOpenGuide,
                icon = Icons.AutoMirrored.Filled.OpenInNew,
            )

            Spacer(Modifier.height(AppDimens.SpacingLg))

            // [5] Endpoint card — tap to copy
            EndpointCard(onCopy = onCopyEndpoint)

            Spacer(Modifier.height(AppDimens.SpacingMd))

            // [6] Requires-Google note
            RequiresGoogleNote()

            Spacer(Modifier.height(AppDimens.SpacingXl))
        }
    }
}

/** 56dp rounded-square hero tile with the AI gradient background + white sparkle. */
@Composable
private fun HeroSparkleTile() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GistiColors.aiGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Icon-tile + label row. Tile is monochrome (primaryContainer), icon decorative. */
@Composable
private fun ValuePropRow(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
}

/** Small cloud-synced caption under the value props. */
@Composable
private fun SyncCaption(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Flat filled+hairline card showing the MCP endpoint URL. The whole card is the copy hit-target
 * (the copy icon is decorative); a merged-descendants semantics node gives screen readers a single
 * "Copy MCP server URL" button instead of reading the raw URL.
 */
@Composable
private fun EndpointCard(onCopy: () -> Unit) {
    val copyCd = stringResource(Res.string.mcp_endpoint_copy_cd)
    AppCard(
        onClick = onCopy,
        contentPadding = PaddingValues(AppDimens.SpacingLg),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = copyCd
            role = Role.Button
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingXxs),
            ) {
                Text(
                    text = stringResource(Res.string.mcp_endpoint_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = McpConfig.MCP_ENDPOINT_URL,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** "Requires signing in with Google" footnote. */
@Composable
private fun RequiresGoogleNote() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.mcp_requires_google),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
