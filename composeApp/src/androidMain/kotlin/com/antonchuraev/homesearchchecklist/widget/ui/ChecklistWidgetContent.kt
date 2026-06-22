package com.antonchuraev.homesearchchecklist.widget.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import com.antonchuraev.aichecklists.R
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.widget.ToggleItemActivity
import com.antonchuraev.homesearchchecklist.widget.actions.OpenChecklistAction
import com.antonchuraev.homesearchchecklist.widget.data.ChecklistWidgetData

/**
 * Main content composable for the checklist widget.
 * Displays checklist name, progress, and a scrollable list of items.
 */
@Composable
fun ChecklistWidgetContent(
    data: ChecklistWidgetData,
    appWidgetId: Int
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(top = 8.dp)
            .padding(horizontal = 8.dp)
    ) {
        // Header with title and progress
        WidgetHeader(
            name = data.name,
            progressText = data.progressText,
            checklistId = data.checklistId
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Scrollable items list. Each row's tap toggles the item via actionStartActivity
        // → ToggleItemActivity (an invisible, no-animation activity). This is deliberate:
        // inside a Glance LazyColumn, actionRunCallback / actionSendBroadcast dispatch
        // through InvisibleActionTrampolineActivity + a collection fill-in-intent, which
        // crashes with "List adapter activity trampoline invoked without specifying target
        // intent" when the OS drops the fill-in-intent (Android 11 OEMs — Crashlytics
        // babbf348). StartActivityAction is the only action Glance maps to a direct
        // fill-in-intent in a lazy collection (no trampoline), so we keep the scroll AND
        // avoid the FATAL. See ToggleItemActivity for the full rationale.
        if (data.items.isEmpty()) {
            EmptyItemsContent()
        } else {
            // defaultWeight() (not fillMaxSize): inside the outer Column the list must take
            // the remaining height BELOW the header to get a bounded, scrollable viewport.
            // fillMaxSize() here fights the Column for the full height and the LazyColumn
            // ends up non-scrollable (the original bug, masked until the DI fix let the
            // widget render at all).
            LazyColumn(
                modifier = GlanceModifier.defaultWeight()
            ) {
                itemsIndexed(data.items) { index, item ->
                    ChecklistItemRow(
                        item = item,
                        checklistId = data.checklistId,
                        fillId = data.fillId,
                        itemIndex = index
                    )
                }

                item {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Widget header with checklist name and progress.
 * Tapping opens the app to ChecklistDetail.
 */
@Composable
private fun WidgetHeader(
    name: String,
    progressText: String,
    checklistId: Long
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(
                actionRunCallback<OpenChecklistAction>(
                    actionParametersOf(
                        OpenChecklistAction.CHECKLIST_ID_KEY to checklistId
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onBackground
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = progressText,
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.secondary
            )
        )
    }
}

/**
 * Single checklist item row with checkbox indicator and text.
 * Tapping anywhere on the row toggles the checked state.
 *
 * Uses custom CheckboxIndicator instead of Glance CheckBox because:
 * - Glance CheckBox with onCheckedChange causes double-toggle (internal state conflict)
 * - Glance CheckBox with onCheckedChange=null doesn't render visual state correctly
 */
@Composable
private fun ChecklistItemRow(
    item: ChecklistFillItem,
    checklistId: Long,
    fillId: Long?,
    itemIndex: Int
) {
    val context = LocalContext.current
    val toggleAction = actionStartActivity(
        Intent(context, ToggleItemActivity::class.java).apply {
            // No animation: the activity is invisible, so any transition would only flicker.
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(ToggleItemActivity.EXTRA_CHECKLIST_ID, checklistId)
            putExtra(ToggleItemActivity.EXTRA_FILL_ID, fillId ?: -1L)
            putExtra(ToggleItemActivity.EXTRA_ITEM_INDEX, itemIndex)
        }
    )

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(toggleAction),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Custom checkbox indicator - more reliable than Glance CheckBox
        CheckboxIndicator(checked = item.checked)

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Text styling based on checked state
        Text(
            text = item.text,
            style = TextStyle(
                fontSize = 14.sp,
                color = if (item.checked) {
                    GlanceTheme.colors.outline
                } else {
                    GlanceTheme.colors.onBackground
                },
                textDecoration = if (item.checked) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                }
            ),
            maxLines = 2,
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

/**
 * Custom checkbox indicator using drawable resources.
 * More reliable than Glance CheckBox which has state management issues.
 */
@Composable
private fun CheckboxIndicator(checked: Boolean) {
    val iconRes = if (checked) {
        R.drawable.ic_checkbox_checked
    } else {
        R.drawable.ic_checkbox_unchecked
    }

    val tintColor = if (checked) {
        GlanceTheme.colors.primary
    } else {
        GlanceTheme.colors.outline
    }

    val context = LocalContext.current
    Image(
        provider = ImageProvider(iconRes),
        contentDescription = if (checked) context.getString(R.string.widget_checked) else context.getString(R.string.widget_unchecked),
        modifier = GlanceModifier.size(24.dp),
        colorFilter = ColorFilter.tint(tintColor)
    )
}

/**
 * Content shown when checklist has no items.
 */
@Composable
private fun EmptyItemsContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = LocalContext.current.getString(R.string.widget_no_items),
            style = TextStyle(
                fontSize = 14.sp,
                color = GlanceTheme.colors.outline
            )
        )
    }
}
