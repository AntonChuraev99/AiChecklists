package com.antonchuraev.homesearchchecklist.feature.sharing.presentation

import androidx.compose.material3.Card
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCard
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppCardDefaults
import com.antonchuraev.homesearchchecklist.desingsystem.containers.AppScaffold
import com.antonchuraev.homesearchchecklist.desingsystem.containers.adaptiveContentWidth
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.feature.sharing.domain.model.ShareFormat
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.share.ShareLauncher
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsScreens
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ShareScreen(
    checklistId: Long,
    viewModel: ShareViewModel = koinViewModel(key = "share_$checklistId") { parametersOf(checklistId) }
) {
    val analyticsTracker: AnalyticsTracker = koinInject()
    LaunchedEffect(Unit) { analyticsTracker.screenView(AnalyticsScreens.SHARE) }

    val state by viewModel.screenState.collectAsStateWithLifecycle()

    ShareScreenContent(
        state = state,
        onIntent = viewModel::sendIntent
    )

    // Share launcher
    if (state.shouldShare) {
        ShareLauncher(
            textContent = state.formattedText,
            pdfFilePath = state.generatedPdfPath,
            onShareComplete = { viewModel.sendIntent(ShareScreenIntent.OnShareComplete) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareScreenContent(
    state: ShareScreenState,
    onIntent: (ShareScreenIntent) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        title = stringResource(Res.string.share_screen_title),
        onBackButtonClick = { onIntent(ShareScreenIntent.OnBackClick) },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                        .padding(top = AppDimens.SpacingMd, bottom = AppDimens.SpacingLg)
                        .navigationBarsPadding()
                ) {
                    AppButton(
                        text = if (state.isGeneratingPdf) {
                            stringResource(Res.string.share_generating_pdf)
                        } else {
                            stringResource(Res.string.share_button)
                        },
                        onClick = { onIntent(ShareScreenIntent.OnShareClick) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.selectedFormat != null && !state.isGeneratingPdf && !state.isLoading
                    )
                }
            }
        }
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.checklist == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .adaptiveContentWidth()
                        .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                        .padding(top = AppDimens.SpacingLg)
                ) {
                    // Checklist preview card
                    state.checklist?.let { checklist ->
                        ChecklistPreviewCard(
                            checklistName = checklist.name,
                            itemCount = state.checklistFill?.items?.size ?: checklist.items.size,
                            checkedCount = state.checklistFill?.items?.count { it.checked } ?: 0
                        )
                    }

                    Spacer(modifier = Modifier.height(AppDimens.SpacingXl))

                    // Format selection
                    Text(
                        text = stringResource(Res.string.share_select_format),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(AppDimens.SpacingMd))

                    // Text format option
                    FormatOptionCard(
                        icon = Icons.AutoMirrored.Outlined.TextSnippet,
                        title = stringResource(Res.string.share_as_text),
                        description = stringResource(Res.string.share_as_text_description),
                        isSelected = state.selectedFormat == ShareFormat.Text,
                        onClick = { onIntent(ShareScreenIntent.OnFormatSelected(ShareFormat.Text)) }
                    )

                    Spacer(modifier = Modifier.height(AppDimens.SpacingSm))

                    // PDF format option
                    FormatOptionCard(
                        icon = Icons.Outlined.PictureAsPdf,
                        title = stringResource(Res.string.share_as_pdf),
                        description = stringResource(Res.string.share_as_pdf_description),
                        isSelected = state.selectedFormat == ShareFormat.Pdf,
                        onClick = { onIntent(ShareScreenIntent.OnFormatSelected(ShareFormat.Pdf)) },
                        isLoading = state.isGeneratingPdf
                    )

                    // Error message
                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(AppDimens.SpacingMd))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistPreviewCard(
    checklistName: String,
    itemCount: Int,
    checkedCount: Int
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = checklistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.share_items_progress, checkedCount, itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    // Selectable card in the shared flat style: selected = accent ring (2dp primary) + filled
    // primaryContainer; unselected = 1dp hairline + resting container. No shadow in either state —
    // the ring, not elevation, signals selection (standard M3 selectable card).
    val isSelectedContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = AppCardDefaults.colors(
            container = if (isSelected) {
                AppCardDefaults.selectedContainerColor()
            } else {
                AppCardDefaults.containerColor()
            }
        ),
        border = if (isSelected) AppCardDefaults.selectedBorder() else AppCardDefaults.border(),
        elevation = AppCardDefaults.flatElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = isSelectedContentColor
                )
            }
            Spacer(modifier = Modifier.width(AppDimens.SpacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = isSelectedContentColor
                )
            }
        }
    }
}
