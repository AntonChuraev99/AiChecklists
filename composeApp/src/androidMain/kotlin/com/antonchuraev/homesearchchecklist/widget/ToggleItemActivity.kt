package com.antonchuraev.homesearchchecklist.widget

import android.app.Activity
import android.os.Bundle
import androidx.glance.appwidget.updateAll
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.widget.data.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

/**
 * Invisible trampoline-free activity that toggles a checklist item from the widget.
 *
 * Why an Activity instead of [androidx.glance.appwidget.action.actionRunCallback]:
 * inside a Glance `LazyColumn`, both `actionRunCallback` and `actionSendBroadcast` are
 * dispatched through `InvisibleActionTrampolineActivity` + a RemoteViews collection
 * fill-in-intent. When the OS fails to merge that fill-in-intent (killed process /
 * stale RemoteViews cache, seen on Android 11 OEMs) the trampoline crashes with
 * "List adapter activity trampoline invoked without specifying target intent"
 * (FATAL — Crashlytics babbf348). Per the Glance 1.1.1 sources (`ApplyAction.kt`),
 * `StartActivityAction` is the ONLY action whose fill-in-intent is the target intent
 * itself (no trampoline), so [actionStartActivity][androidx.glance.appwidget.action.actionStartActivity]
 * keeps LazyColumn's scroll AND avoids the crash. A lost fill-in-intent degrades to a
 * harmless no-op here, never a FATAL.
 *
 * The activity is fully transparent (Theme.Translucent), suppresses transition
 * animations, and finishes immediately — the user only ever sees the widget update.
 * The actual toggle runs on a process-level [scope] so it survives [finish].
 */
class ToggleItemActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, INVALID).takeIf { it != INVALID }
        val fillId = intent.getLongExtra(EXTRA_FILL_ID, INVALID).takeIf { it != INVALID }
        val itemIndex = intent.getIntExtra(EXTRA_ITEM_INDEX, -1).takeIf { it >= 0 }

        if (checklistId != null && itemIndex != null) {
            val koin = GlobalContext.getOrNull()
            val repository = koin?.getOrNull<WidgetRepository>()
            val analyticsTracker = koin?.getOrNull<AnalyticsTracker>()
            if (repository != null) {
                val appContext = applicationContext
                scope.launch {
                    toggleMutex.withLock {
                        repository.toggleItem(checklistId, fillId, itemIndex)
                        analyticsTracker?.event(
                            AnalyticsEvents.Item.WIDGET_TOGGLED,
                            mapOf("checklist_id" to checklistId.toString())
                        )
                        ChecklistWidget().updateAll(appContext)
                    }
                }
            }
        }

        finish()
    }

    companion object {
        const val EXTRA_CHECKLIST_ID = "checklist_id"
        const val EXTRA_FILL_ID = "fill_id"
        const val EXTRA_ITEM_INDEX = "item_index"

        private const val INVALID = -1L

        // Serialize toggles across rapid taps; process-level so it outlives the activity.
        private val toggleMutex = Mutex()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
