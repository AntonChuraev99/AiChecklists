package com.antonchuraev.homesearchchecklist.widget.actions

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.widget.ChecklistWidget
import com.antonchuraev.homesearchchecklist.widget.data.WidgetRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

/**
 * ActionCallback for toggling checklist item checked state.
 * Called when user taps on an item in the widget.
 *
 * With collectAsState() pattern in ChecklistWidget:
 * 1. Update data in Room
 * 2. Call update() to trigger provideGlance()
 * 3. Flow emits new data, collectAsState() triggers recomposition
 */
class ToggleItemAction : ActionCallback {

    companion object {
        val CHECKLIST_ID_KEY = ActionParameters.Key<Long>("checklist_id")
        val FILL_ID_KEY = ActionParameters.Key<Long>("fill_id")
        val ITEM_INDEX_KEY = ActionParameters.Key<Int>("item_index")

        // Mutex to serialize toggle operations and prevent race conditions
        private val toggleMutex = Mutex()
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val checklistId = parameters[CHECKLIST_ID_KEY] ?: return
        val fillId = parameters[FILL_ID_KEY]?.takeIf { it != -1L }
        val itemIndex = parameters[ITEM_INDEX_KEY] ?: return

        val koin = GlobalContext.getOrNull() ?: return
        val repository: WidgetRepository = koin.get()
        val analyticsTracker: AnalyticsTracker = koin.get()

        // Serialize all toggle operations to prevent race conditions on rapid tapping
        toggleMutex.withLock {
            // 1. Update data in Room database
            repository.toggleItem(checklistId, fillId, itemIndex)

            analyticsTracker.event("widget_item_toggled", mapOf(
                "checklist_id" to checklistId.toString()
            ))

            // 2. Trigger widget update - provideGlance() will be called,
            // and collectAsState() will receive new data from Flow
            ChecklistWidget().update(context, glanceId)
        }
    }
}
