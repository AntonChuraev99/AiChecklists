package com.antonchuraev.homesearchchecklist.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import com.antonchuraev.aichecklists.R
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.antonchuraev.homesearchchecklist.widget.actions.ReconfigureAction

/**
 * Content shown while loading widget data.
 */
@Composable
fun LoadingContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_loading),
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.onBackground
            )
        )
    }
}

/**
 * Content shown when the selected checklist is not found (deleted).
 */
@Composable
fun NotFoundContent(appWidgetId: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_not_found),
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.error,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = GlanceModifier.height(12.dp))

        Button(
            text = LocalContext.current.getString(R.string.widget_select_another),
            onClick = actionRunCallback<ReconfigureAction>()
        )
    }
}

/**
 * Content shown when widget is not configured (no checklist selected).
 */
@Composable
fun NotConfiguredContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp)
            .clickable(actionRunCallback<ReconfigureAction>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_tap_to_configure),
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.onBackground,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        Text(
            text = LocalContext.current.getString(R.string.widget_select_checklist_display),
            style = TextStyle(
                fontSize = 12.sp,
                color = GlanceTheme.colors.outline,
                textAlign = TextAlign.Center
            )
        )
    }
}
