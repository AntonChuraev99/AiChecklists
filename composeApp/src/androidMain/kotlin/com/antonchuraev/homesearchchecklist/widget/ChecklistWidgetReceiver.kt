package com.antonchuraev.homesearchchecklist.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.antonchuraev.homesearchchecklist.widget.data.WidgetStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * BroadcastReceiver for the checklist widget.
 * Handles widget lifecycle events (add, update, delete).
 */
class ChecklistWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()

    /**
     * Called when widgets are removed from the home screen.
     * Clean up stored configuration for deleted widgets.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        val stateManager: WidgetStateManager = GlobalContext.get().get()

        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { appWidgetId ->
                stateManager.clearWidget(appWidgetId)
            }
        }
    }
}
