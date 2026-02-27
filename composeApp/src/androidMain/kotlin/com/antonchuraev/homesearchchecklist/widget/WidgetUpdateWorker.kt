package com.antonchuraev.homesearchchecklist.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker to update Glance widgets.
 * WorkManager ensures the update runs in a proper coroutine context.
 */
class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        const val WORK_NAME = "widget_update"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: updating all widgets")
        return try {
            ChecklistWidget().updateAll(context)
            Log.d(TAG, "doWork: widgets updated successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: failed to update widgets", e)
            Result.failure()
        }
    }
}
