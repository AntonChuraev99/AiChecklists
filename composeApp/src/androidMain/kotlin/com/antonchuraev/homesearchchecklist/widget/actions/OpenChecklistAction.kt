package com.antonchuraev.homesearchchecklist.widget.actions

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import org.koin.core.context.GlobalContext

/**
 * ActionCallback to open the main app and navigate to a specific checklist.
 * Used when user taps on the widget header.
 */
class OpenChecklistAction : ActionCallback {

    companion object {
        val CHECKLIST_ID_KEY = ActionParameters.Key<Long>("checklist_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val checklistId = parameters[CHECKLIST_ID_KEY] ?: return

        // Widget-driven return signal. Emitted from this Glance ActionCallback (which starts the
        // Activity directly — no Android-12 receiver trampoline), resolved cold-safe like the toggle.
        runCatching {
            GlobalContext.getOrNull()?.getOrNull<AnalyticsTracker>()?.event(
                AnalyticsEvents.Widget.OPENED,
                mapOf(AnalyticsParams.CHECKLIST_ID to checklistId.toString()),
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("checklist_id", checklistId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        context.startActivity(intent)
    }
}
