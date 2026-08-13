package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiScheduleState
import com.antonchuraev.homesearchchecklist.desingsystem.theme.GistiSourceKind

@PreviewLightDark
@Composable
private fun AppDueChipStatesPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
        ) {
            AppDueChip(state = GistiScheduleState.Later, label = "Fri 14")
            AppDueChip(state = GistiScheduleState.Someday, label = "Someday")
            AppDueChip(state = GistiScheduleState.Active, label = "Today 18:00")
            AppDueChip(state = GistiScheduleState.Overdue, label = "Yesterday")
        }
    }
}

@PreviewLightDark
@Composable
private fun AppDueChipTrailingGlyphsPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
        ) {
            AppDueChip(state = GistiScheduleState.Active, label = "Today 09:00", hasAlarm = true)
            AppDueChip(state = GistiScheduleState.Active, label = "Today 09:00", isRepeating = true)
            // Repeat outranks the alarm — only one glyph is ever drawn.
            AppDueChip(
                state = GistiScheduleState.Active,
                label = "Today 09:00",
                isRepeating = true,
                hasAlarm = true,
            )
        }
    }
}

/**
 * The overflow case, in the locale that produces it. The word truncates; the hour survives.
 */
@PreviewLightDark
@Composable
private fun AppDueChipOverflowPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
        ) {
            AppDueChip(state = GistiScheduleState.Overdue, label = "Позавчера в 18:30")
            AppDueChip(
                state = GistiScheduleState.Later,
                label = "Следующий понедельник 09:00",
                isRepeating = true,
            )
        }
    }
}

/** The meta row as a task row will assemble it: due chip, source glyph, attachment count. */
@PreviewLightDark
@Composable
private fun AppDueChipInMetaRowPreview() {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(16.dp),
        ) {
            AppDueChip(state = GistiScheduleState.Active, label = "Today 18:00", hasAlarm = true)
            AppSourceIcon(kind = GistiSourceKind.Ai)
        }
    }
}
