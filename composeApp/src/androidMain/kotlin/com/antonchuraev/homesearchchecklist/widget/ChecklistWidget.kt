package com.antonchuraev.homesearchchecklist.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.antonchuraev.homesearchchecklist.widget.data.WidgetRepository
import com.antonchuraev.homesearchchecklist.widget.data.WidgetStateManager
import com.antonchuraev.homesearchchecklist.widget.ui.ChecklistWidgetContent
import com.antonchuraev.homesearchchecklist.widget.ui.LoadingContent
import com.antonchuraev.homesearchchecklist.widget.ui.NotConfiguredContent
import com.antonchuraev.homesearchchecklist.widget.ui.NotFoundContent
import kotlinx.coroutines.flow.flowOf
import org.koin.core.context.GlobalContext

/**
 * Main Glance widget for displaying a checklist on the home screen.
 * Users can view progress and toggle items directly from the widget.
 *
 * Uses collectAsState() pattern for reactive updates:
 * - Observes selected checklist ID from DataStore
 * - When selection changes, automatically switches to new checklist data
 * - When Room data changes, Flow emits new values and triggers recomposition
 */
class ChecklistWidget : GlanceAppWidget() {

    /**
     * Responsive size mode - widget adapts to different sizes.
     * 4x2 (default), 4x3 (medium), 4x4 (large)
     */
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),  // 4x2 default
            DpSize(250.dp, 180.dp),  // 4x3 medium
            DpSize(250.dp, 280.dp)   // 4x4 large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.getOrNull()

        if (koin == null) {
            provideContent {
                GlanceTheme {
                    NotConfiguredContent()
                }
            }
            return
        }

        val repository: WidgetRepository = koin.get()
        val stateManager: WidgetStateManager = koin.get()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        provideContent {
            // Observe selected checklist ID - reacts to selection changes
            val checklistIdFlow = remember { stateManager.observeSelectedChecklistId(appWidgetId) }
            val checklistId by checklistIdFlow.collectAsState(initial = null)

            // When checklistId changes, automatically switch to observing new checklist data
            val widgetDataFlow = remember(checklistId) {
                if (checklistId != null) {
                    repository.observeChecklistWithDefaultFill(checklistId!!)
                } else {
                    flowOf(null)
                }
            }
            val widgetData by widgetDataFlow.collectAsState(initial = null)

            GlanceTheme {
                when {
                    checklistId == null -> NotConfiguredContent()
                    widgetData == null -> LoadingContent()
                    widgetData!!.notFound -> NotFoundContent(appWidgetId)
                    else -> ChecklistWidgetContent(
                        data = widgetData!!,
                        appWidgetId = appWidgetId
                    )
                }
            }
        }
    }
}
