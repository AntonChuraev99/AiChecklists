package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSourceKind
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalReducedMotion

@PreviewLightDark
@Composable
private fun AppSourceIconPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
        ) {
            // Manual draws nothing at all — the row below has four glyphs, not five.
            AppSourceIcon(kind = GistiSourceKind.Manual)
            AppSourceIcon(kind = GistiSourceKind.Ai)
            AppSourceIcon(kind = GistiSourceKind.Email)
            AppSourceIcon(kind = GistiSourceKind.Webhook)
            AppSourceIcon(kind = GistiSourceKind.Messenger)
        }
    }
}

@PreviewLightDark
@Composable
private fun AppSkeletonLinePreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp)
                .width(280.dp),
        ) {
            AppSkeletonLine(widthFraction = 1f)
            AppSkeletonLine(widthFraction = 0.92f)
            AppSkeletonLine(widthFraction = 0.6f)
        }
    }
}

/** Reduced motion: a flat bar, and no infinite transition is created at all. */
@PreviewLightDark
@Composable
private fun AppSkeletonLineReducedMotionPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        CompositionLocalProvider(LocalReducedMotion provides true) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(16.dp)
                    .width(280.dp),
            ) {
                AppSkeletonLine(widthFraction = 1f)
                AppSkeletonLine(widthFraction = 0.55f)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppPlanNudgePreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .width(328.dp),
        ) {
            AppPlanNudge(onClick = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun TokenChipPreviewStatesPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            // Today's call sites: inert, no ripple that leads nowhere, not greyed out.
            TokenChipPreview(label = "Tomorrow 09:00")
            TokenChipPreview(label = "Every day 09:00", isRepeat = true)
            // Wired up: tap to change, cross to clear.
            TokenChipPreview(
                label = "Tomorrow 09:00",
                onClick = {},
                onDismiss = {},
            )
            TokenChipPreview(
                label = "Tomorrow 09:00",
                state = TokenChipState.Confirmed,
                onClick = {},
                onDismiss = {},
            )
        }
    }
}
